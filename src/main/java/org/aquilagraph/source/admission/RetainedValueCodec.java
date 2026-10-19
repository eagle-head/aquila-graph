package org.aquilagraph.source.admission;

import java.math.BigDecimal;
import java.lang.annotation.ElementType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.aquilagraph.identity.api.CanonicalValueReader;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.DependencySnapshot;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.CoverageValidationEvidence;
import org.aquilagraph.source.api.CoverageEvidenceValue;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaTypeNesting;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeReference;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.CoverageSourceFact;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeatureClassification;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.SyntaxOperator;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.WrittenTypeEvidence;
import org.jspecify.annotations.Nullable;

/** Closed source value encoding; no class name is resolved from retained data. */
final class RetainedValueCodec {
  private static final Set<String> BINDING_TAGS = Set.of("syntax-value/local-binding", "local-binding-evidence", "local-binding-type-form",
      "local-binding-context");
  private final boolean canonical;
  private final String format;
  private final int formatVersion;
  private final Map<String, JavaTypeIdentity> historicalTypeIdentities = new HashMap<>();
  private final Function<SyntaxNode, @Nullable Integer> nodeOrdinal;
  private final ToIntFunction<SyntaxNode> registerNode;
  private final List<@Nullable SyntaxNode> readNodes = new ArrayList<>();

  RetainedValueCodec(boolean canonical) {
    this(canonical, AstResult.SOURCE_MODEL_VERSION);
  }

  RetainedValueCodec(boolean canonical, String format) {
    this.canonical = canonical;
    if (!AstResult.supportsSourceModelVersion(format)) {
      throw new IllegalArgumentException("Unsupported retained AST format: " + format);
    }
    this.format = format;
    formatVersion = Integer.parseInt(format.substring(format.indexOf('/') + 1));
    var nodes = new IdentityHashMap<SyntaxNode, Integer>();
    nodeOrdinal = nodes::get;
    registerNode = node -> {
      var ordinal = nodes.size();
      nodes.put(node, ordinal);
      return ordinal;
    };
  }

  void write(CanonicalValueWriter writer, Object value) {
    switch (value) {
      case String text -> {
        writer.text("text");
        writer.text(text);
      }
      case Boolean flag -> {
        writer.text("flag");
        writer.flag(flag);
      }
      case Integer number -> {
        writer.text("int32");
        writer.integer(number);
      }
      case Long number -> {
        writer.text("int64");
        writer.longInteger(number);
      }
      case Float number -> {
        writer.text("float32");
        writer.floating(number);
      }
      case Double number -> {
        writer.text("float64");
        writer.floating(number);
      }
      case Byte number -> {
        writer.text("int8");
        writer.integer(number);
      }
      case Short number -> {
        writer.text("int16");
        writer.integer(number);
      }
      case Character character -> {
        writer.text("char16");
        writer.integer(character);
      }
      case BigDecimal number -> {
        writer.text("decimal");
        writer.text(number.toString());
      }
      case Path path -> {
        writer.text("path");
        writer.text(path.toString());
      }
      case Optional<?> optional -> {
        writer.text("optional");
        writer.flag(optional.isPresent());
        optional.ifPresent(component -> write(writer, component));
      }
      case Set<?> set -> {
        writer.text("set");
        var ordered = new ArrayList<>(set);
        ordered.sort((left, right) -> Arrays.compareUnsigned(orderingBytes(left), orderingBytes(right)));
        writeSequence(writer, ordered);
      }
      case List<?> list -> {
        writer.text("list");
        writeSequence(writer, list);
      }
      case SyntaxNode node -> writeNode(writer, node);
      case CoverageSourceFact fact when canonical -> writeRegistered(writer, canonicalSourceFact(fact));
      case JavaAstAggregate aggregate -> {
        writer.text("source-aggregate");
        write(writer, CopiedAstValues.capture(aggregate));
      }
      case LanguageFeatureCoverage coverage -> {
        writer.text("source-coverage-contract");
        write(writer, coverage.release());
        write(writer, coverage.classifications());
      }
      case LanguageFeatureCoverageResult coverage -> {
        writer.text("source-coverage-result");
        write(writer, coverage.coverage());
        write(writer, coverage.validationEvidence());
      }
      case SyntaxValue.Annotation annotation -> {
        writer.text("syntax-value/annotation");
        writeSequence(writer, List.of(annotation.form(), annotation.arguments(), annotation.location(), annotation.applicableTargets(),
            annotation.closestTypeAdmissible(), annotation.argumentFailureCode(), annotation.repeatableContainerKey()));
      }
      case SyntaxValue.LocalDeclaration local when local.bindingEvidence().isEmpty() -> {
        validateLegacyLocalName(local.name());
        writer.text("syntax-value/local-declaration");
        writer.count(3);
        writer.text("name");
        write(writer, local.name());
        writer.text("writtenType");
        write(writer, local.writtenType());
        writer.text("writtenModifiers");
        write(writer, local.writtenModifiers());
      }
      default -> writeRegistered(writer, value);
    }
  }

  Object read(CanonicalValueReader reader) {
    var tag = reader.text();
    return switch (tag) {
      case "text" -> reader.text();
      case "flag" -> reader.flag();
      case "int32" -> reader.integer();
      case "int64" -> reader.longInteger();
      case "float32" -> reader.floating();
      case "float64" -> reader.doubleFloating();
      case "int8" -> boundedInteger(reader, Byte.MIN_VALUE, Byte.MAX_VALUE).byteValue();
      case "int16" -> boundedInteger(reader, Short.MIN_VALUE, Short.MAX_VALUE).shortValue();
      case "char16" -> (char) boundedInteger(reader, Character.MIN_VALUE, Character.MAX_VALUE).intValue();
      case "decimal" -> new BigDecimal(reader.text());
      case "path" -> Path.of(reader.text());
      case "optional" -> reader.flag() ? Optional.of(read(reader)) : Optional.empty();
      case "list" -> readSequence(reader);
      case "set" -> readSet(reader);
      case "source-node" -> readNode(reader);
      case "source-node-reference" -> readNodeReference(reader);
      case "source-aggregate" -> AstValueAdmission.admit(required(read(reader), CopiedAstValues.class));
      case "source-coverage-contract" -> LanguageFeatureCoverage.forRelease(required(read(reader), JavaRelease.class),
          sequence(read(reader), LanguageFeatureClassification.class));
      case "source-coverage-result" -> readCoverage(reader);
      case "syntax-value/annotation" -> readAnnotation(reader);
      case "syntax-value/local-declaration" -> readLegacyLocal(reader);
      default -> readRegistered(reader, tag);
    };
  }

  private SyntaxValue.LocalDeclaration readLegacyLocal(CanonicalValueReader reader) {
    if (reader.count() != 3 || !"name".equals(reader.text())) {
      throw new IllegalArgumentException("Invalid retained local declaration shape");
    }
    var name = required(read(reader), String.class);
    validateLegacyLocalName(name);
    if (!"writtenType".equals(reader.text())) {
      throw new IllegalArgumentException("Invalid retained local declaration type field");
    }
    var type = optional(read(reader), WrittenTypeEvidence.class);
    if (!"writtenModifiers".equals(reader.text())) {
      throw new IllegalArgumentException("Invalid retained local declaration modifier field");
    }
    return new SyntaxValue.LocalDeclaration(name, type, sequence(read(reader), String.class));
  }

  private void validateLegacyLocalName(String name) {
    if (AstResult.LEGACY_SOURCE_MODEL_VERSION.equals(format) && "_".equals(name)) {
      throw new IllegalArgumentException("An unnamed binding requires the current retained AST format");
    }
  }

  private void writeRegistered(CanonicalValueWriter writer, Object value) {
    var shape = AstValueSchema.type(value.getClass());
    validateFormatTag(shape.tag());
    if (value == CoverageEvidenceValue.Tag.LOCAL_BINDING && AstResult.LEGACY_SOURCE_MODEL_VERSION.equals(format)) {
      throw new IllegalArgumentException("Copied binding coverage requires the current retained AST format");
    }
    writer.text(shape.tag());
    if (value instanceof Enum<?> enumeration) {
      validateFormatEnum(shape.type(), enumeration.name());
      writer.text(enumeration.name());
      return;
    }
    var components = shape.retainedComponents(value, formatVersion);
    var fields = shape.retainedFields(formatVersion);
    var excluded = canonical && (value instanceof InputSnapshot || value instanceof DependencySnapshot);
    writer.count(components.size() - (excluded ? 1 : 0));
    for (var index = excluded ? 1 : 0; index < components.size(); index++) {
      writer.text(fields.get(index));
      write(writer, components.get(index));
    }
  }

  private static CoverageSourceFact canonicalSourceFact(CoverageSourceFact fact) {
    var children = fact.children().stream()
        .sorted(Comparator.comparing((CoverageSourceFact.Child child) -> SyntaxRole.valueOf(child.role()).stableId())
            .thenComparingInt(CoverageSourceFact.Child::ordinal))
        .toList();
    return new CoverageSourceFact(fact.structuralPath(), fact.sourceKind(), fact.syntaxKind(), fact.type(), fact.value(), children,
        fact.semanticReference(), fact.sourceAssociation());
  }

  private Object readRegistered(CanonicalValueReader reader, String tag) {
    validateFormatTag(tag);
    var shape = AstValueSchema.tag(tag);
    if (shape.type().isEnum()) {
      var name = reader.text();
      validateFormatEnum(shape.type(), name);
      if (shape.type() == CoverageEvidenceValue.Tag.class && "LOCAL_BINDING".equals(name)
          && AstResult.LEGACY_SOURCE_MODEL_VERSION.equals(format)) {
        throw new IllegalArgumentException("Copied binding coverage requires the current retained AST format");
      }
      return Arrays.stream(Objects.requireNonNull(shape.type().getEnumConstants(), "registered enum constants"))
          .filter(value -> value instanceof Enum<?> enumeration && enumeration.name().equals(name)).findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Unknown retained enum value: " + tag + "/" + name));
    }
    var fields = shape.retainedFields(formatVersion);
    var historicalScopes = formatVersion < 14 && "copied-ast-values".equals(tag);
    if (reader.count() != fields.size()) {
      throw new IllegalArgumentException("Wrong retained field count: " + tag);
    }
    var components = new ArrayList<Object>(shape.fields().size());
    for (var field : fields) {
      if (!field.equals(reader.text())) {
        throw new IllegalArgumentException("Wrong retained field order: " + tag + "/" + field);
      }
      components.add(read(reader));
    }
    if (formatVersion < 19 && ("written-source-association".equals(tag) || "source-coverage-inventory".equals(tag)
        || "syntax-value/literal".equals(tag) || "coverage-source-fact/association".equals(tag))) {
      components.add(Optional.empty());
    }
    if (historicalScopes) {
      components.add(List.of());
    }
    if (formatVersion < 18 && "syntax-value/compilation-unit".equals(tag)) {
      components.add(SyntaxValue.CompilationUnitRange.LEGACY_PROFILE);
    }
    if (formatVersion < 14 && "declaration-member-order".equals(tag)) {
      components.add(Optional.empty());
    }
    if (formatVersion < 15 && "java-type".equals(tag)) {
      var owner = ((Optional<?>) components.get(4)).map(JavaTypeReference.class::cast).flatMap(JavaTypeReference::declarationKey);
      components.add(new JavaTypeNesting(owner.isEmpty() ? JavaTypeNesting.Kind.TOP_LEVEL : JavaTypeNesting.Kind.MEMBER, owner));
      var name = (String) components.get(1);
      components.add(historicalTypeIdentities.getOrDefault((String) components.getFirst(),
          new JavaTypeIdentity(JavaTypeCategory.DECLARED, name, name)));
      components.add(name);
    }
    if (formatVersion < 15 && "declaration-type-evidence".equals(tag)) {
      historicalTypeIdentities.put((String) components.getFirst(), (JavaTypeIdentity) components.get(2));
    }
    if (formatVersion < 15 && "java-program".equals(tag)) {
      components.set(0, historicalTypes(((List<?>) components.getFirst()).stream().map(JavaType.class::cast).toList(), Optional.empty()));
    }
    if (formatVersion < 15 && "syntax-analysis-input".equals(tag)) {
      var program = (JavaProgram) components.get(2);
      var aggregate = ((Optional<?>) components.get(3)).map(JavaAstAggregate.class::cast);
      components.set(2, new JavaProgram(historicalTypes(program.types(), aggregate)));
    }
    return shape.construct(components);
  }

  private static List<JavaType> historicalTypes(List<JavaType> types, Optional<JavaAstAggregate> aggregate) {
    return types.stream().map(type -> {
      var identity = aggregate.flatMap(value -> value.declarationTypeEvidence(type.typeKey())).map(value -> value.identity())
          .orElseGet(() -> types.stream().flatMap(value -> value.enclosingType().stream())
              .filter(reference -> reference.declarationKey().filter(type.typeKey()::equals).isPresent()).map(JavaTypeReference::identity)
              .findFirst().orElse(type.staticType()));
      return new JavaType(type.typeKey(), type.qualifiedName(), type.kind(), type.modifiers(), type.enclosingType(),
          type.directSuperclass(), type.directInterfaces(), type.permittedSubclasses(), type.typeParameters(), type.sourceDeclared(),
          type.fields(), type.recordComponents(), type.methods(), type.synthesizedDeclarations(), type.origins(), type.nesting(), identity,
          type.displayName());
    }).toList();
  }

  private void validateFormatTag(String tag) {
    if (formatVersion < 19 && (tag.startsWith("written-expression-region") || tag.startsWith("normalized-expression-correspondence")
        || tag.startsWith("compiler-expression-anchor/") || "decimal-minimum-operand".equals(tag))) {
      throw new IllegalArgumentException("Written expression evidence requires retained AST format java-source/19");
    }
    if (formatVersion < 18 && "syntax-value/compilation-unit-range".equals(tag)) {
      throw new IllegalArgumentException("Compilation unit range provenance requires retained AST format java-source/18");
    }
    if (formatVersion < 16 && "execution-scope/enum-constant".equals(tag)) {
      throw new IllegalArgumentException("Enum initialization scopes require retained AST format java-source/16");
    }
    if (formatVersion < 15 && tag.startsWith("java-type-nesting")) {
      throw new IllegalArgumentException("Nested declaration metadata requires retained AST format java-source/15");
    }
    if (formatVersion < 14 && (tag.startsWith("execution-scope/") || "copied-ast-values/scope".equals(tag)
        || "declaration-member-order/tree-order".equals(tag))) {
      throw new IllegalArgumentException("Declaration execution scopes require retained AST format java-source/14");
    }
    if (formatVersion < 11 && "syntax-value/switch-label/pattern".equals(tag)) {
      throw new IllegalArgumentException("Pattern switch labels require retained AST format java-source/11");
    }
    if (formatVersion < 10 && Set.of("syntax-value/pattern-binding", "syntax-value/inferred-record-type").contains(tag)) {
      throw new IllegalArgumentException("Pattern binding scope evidence requires retained AST format java-source/10");
    }
    if (formatVersion < 9 && Set.of("syntax-value/member-reference", "reference-mode", "reference-qualifier", "reference-selection",
        "java-type-structure/executable", "java-executable-erasure").contains(tag)) {
      throw new IllegalArgumentException("Member reference syntax requires retained AST format java-source/9");
    }
    if (formatVersion < 8 && Set.of("syntax-value/lambda", "lambda-parameter", "lambda-body-kind").contains(tag)) {
      throw new IllegalArgumentException("Lambda syntax requires retained AST format java-source/8");
    }
    if (formatVersion < 6 && "syntax-value/statement-label".equals(tag)) {
      throw new IllegalArgumentException("Iteration statements require retained AST format java-source/6");
    }
    if (formatVersion < 5
        && ("syntax-value/array-creation".equals(tag) || "syntax-semantic-target/annotation-type-location/array-dimension".equals(tag))) {
      throw new IllegalArgumentException("Executable arrays require retained AST format java-source/5");
    }
    if (AstResult.LEGACY_SOURCE_MODEL_VERSION.equals(format) && BINDING_TAGS.contains(tag)) {
      throw new IllegalArgumentException("Copied binding values require the current retained AST format: " + tag);
    }
  }

  private void validateFormatEnum(Class<?> type, String name) {
    if (formatVersion < 19 && ((type == CoverageEvidenceValue.Tag.class && "DECIMAL_MINIMUM_OPERAND".equals(name))
        || (type == SourceContext.Partition.class && "WRITTEN_EXPRESSION".equals(name))
        || (type == SourceOwnerPath.Role.class && "WRITTEN_EXPRESSION".equals(name)))) {
      throw new IllegalArgumentException("Written expression provenance requires retained AST format java-source/19");
    }

    if (formatVersion < 18 && type == SourceContext.Partition.class && "EMPTY_UNIT_DECLARATION".equals(name)) {
      throw new IllegalArgumentException("Empty unit declarations require retained AST format java-source/18");
    }
    if (formatVersion < 17 && ((type == SourceContext.Partition.class && "EMPTY_BODY_DECLARATION".equals(name))
        || (type == SourceOwnerPath.Role.class && "EMPTY_DECLARATION".equals(name)))) {
      throw new IllegalArgumentException("Empty body declarations require retained AST format java-source/17");
    }
    if (formatVersion < 15 && type == SyntaxRole.class && "CLASS_BODY".equals(name)) {
      throw new IllegalArgumentException("Anonymous declaration bodies require retained AST format java-source/15");
    }
    if (formatVersion < 13 && type == SyntaxKind.class && "CLASS_LITERAL".equals(name)) {
      throw new IllegalArgumentException("Executable class literals require retained AST format java-source/13");
    }
    if (formatVersion < 11 && ((type == SyntaxKind.class && "SWITCH_STATEMENT".equals(name))
        || (type == CoverageEvidenceValue.Tag.class && "PATTERN_LABEL".equals(name)))) {
      throw new IllegalArgumentException("Expanded switch syntax requires retained AST format java-source/11");
    }
    if (formatVersion < 10
        && ((type == SyntaxKind.class && Set.of("INSTANCE_OF", "BINDING_PATTERN", "DECONSTRUCTION_PATTERN", "ANY_PATTERN").contains(name))
            || (type == SyntaxRole.class && Set.of("PATTERN", "PATTERN_COMPONENT").contains(name))
            || (type == CoverageEvidenceValue.Tag.class && Set.of("PATTERN_BINDING", "INFERRED_RECORD_TYPE").contains(name)))) {
      throw new IllegalArgumentException("Instance tests and patterns require retained AST format java-source/10");
    }
    if (formatVersion < 9 && ((type == SyntaxKind.class && "MEMBER_REFERENCE".equals(name)) || (type == CoverageEvidenceValue.Tag.class
        && Set.of("MEMBER_REFERENCE", "TYPE_EXECUTABLE", "TYPE_EXECUTABLE_ERASURE").contains(name)))) {
      throw new IllegalArgumentException("Member reference syntax requires retained AST format java-source/9");
    }
    if (formatVersion < 8 && ((type == SyntaxKind.class && "LAMBDA_EXPRESSION".equals(name))
        || (type == CoverageEvidenceValue.Tag.class && Set.of("LAMBDA", "LAMBDA_PARAMETER").contains(name)))) {
      throw new IllegalArgumentException("Lambda syntax requires retained AST format java-source/8");
    }
    if (formatVersion < 7 && ((type == SyntaxKind.class && Set.of("ASSERT", "SYNCHRONIZED").contains(name))
        || (type == SyntaxRole.class && Set.of("DETAIL", "MONITOR").contains(name)))) {
      throw new IllegalArgumentException("Assertion and monitor syntax require retained AST format java-source/7");
    }
    if (formatVersion < 6 && ((type == SyntaxKind.class
        && Set.of("WHILE_LOOP", "DO_WHILE_LOOP", "FOR_LOOP", "ENHANCED_FOR_LOOP", "LABELED_STATEMENT", "BREAK", "CONTINUE").contains(name))
        || (type == SyntaxRole.class && Set.of("UPDATE", "ITERABLE").contains(name))
        || (type == CoverageEvidenceValue.Tag.class && "STATEMENT_LABEL".equals(name))
        || (type == LocalBindingEvidence.Context.class && "ENHANCED_FOR".equals(name)))) {
      throw new IllegalArgumentException("Iteration statements require retained AST format java-source/6");
    }
    if (formatVersion < 4 && ((type == SyntaxKind.class && ("UPDATE".equals(name) || "COMPOUND_ASSIGNMENT".equals(name)))
        || (type == SyntaxOperator.class && switch (name) {
          case "MULTIPLY_ASSIGNMENT", "DIVIDE_ASSIGNMENT", "REMAINDER_ASSIGNMENT", "PLUS_ASSIGNMENT", "MINUS_ASSIGNMENT",
              "LEFT_SHIFT_ASSIGNMENT", "RIGHT_SHIFT_ASSIGNMENT", "UNSIGNED_RIGHT_SHIFT_ASSIGNMENT", "AND_ASSIGNMENT", "XOR_ASSIGNMENT",
              "OR_ASSIGNMENT", "PREFIX_INCREMENT", "PREFIX_DECREMENT", "POSTFIX_INCREMENT", "POSTFIX_DECREMENT" ->
            true;
          default -> false;
        }))) {
      throw new IllegalArgumentException("Update expressions require the current retained AST format");
    }
    if (formatVersion < 5 && ((type == SyntaxKind.class && "ARRAY_CREATION".equals(name))
        || (type == SyntaxRole.class && ("DIMENSION".equals(name) || "ARRAY_ELEMENT".equals(name)))
        || (type == CoverageEvidenceValue.Tag.class && "ARRAY_CREATION".equals(name)))) {
      throw new IllegalArgumentException("Executable arrays require retained AST format java-source/5");
    }
  }

  private void validateSourceFormat(SyntaxNode node) {
    if (formatVersion < 17 && node.syntaxKind() == SyntaxKind.EMPTY
        && node.structuralPath().steps().getLast().role() == SyntaxRole.MEMBER) {
      throw new IllegalArgumentException("Empty body declarations require retained AST format java-source/17");
    }
    if (formatVersion < 13 && node.syntaxKind() == SyntaxKind.CLASS_LITERAL) {
      throw new IllegalArgumentException("Executable class literals require retained AST format java-source/13");
    }
    if (formatVersion < 12 && ((node.syntaxKind() == SyntaxKind.TRY
        && (!node.children(SyntaxRole.RESOURCE).isEmpty() || !node.children(SyntaxRole.FINALLY_BLOCK).isEmpty()))
        || node.sourceKind() == JavaSourceKind.UNION_TYPE)) {
      throw new IllegalArgumentException("Expanded exception syntax requires retained AST format java-source/12");
    }
    if (formatVersion < 11 && node.syntaxKind() == SyntaxKind.CASE && !SourceSwitchRequirements.isBoundedCase(node)) {
      throw new IllegalArgumentException("Expanded switch cases require retained AST format java-source/11");
    }
  }

  private void writeNode(CanonicalValueWriter writer, SyntaxNode node) {
    validateSourceFormat(node);
    var previous = nodeOrdinal.apply(node);
    if (previous != null) {
      writer.text("source-node-reference");
      writer.integer(previous);
      return;
    }
    var ordinal = registerNode.applyAsInt(node);
    writer.text("source-node");
    writer.integer(ordinal);
    var children = canonical
        ? node.children().stream()
            .sorted(Comparator.comparing((SyntaxChild child) -> child.role().stableId()).thenComparingInt(SyntaxChild::position)).toList()
        : node.children();
    writeSequence(writer, List.of(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(),
        node.value(), children, node.semanticReference(), node.origin()));
  }

  private SyntaxNode readNode(CanonicalValueReader reader) {
    var ordinal = reader.integer();
    if (ordinal != readNodes.size()) {
      throw new IllegalArgumentException("Source occurrence slots must be contiguous");
    }
    readNodes.add(null);
    var values = readSequence(reader);
    requireSize(values, 9, "source-node");
    var source = required(values.get(0), String.class);
    var path = required(values.get(1), SyntaxStructuralPath.class);
    var sourceKind = required(values.get(2), JavaSourceKind.class);
    var kind = required(values.get(3), SyntaxKind.class);
    var type = required(values.get(4), SyntaxType.class);
    var value = required(values.get(5), SyntaxValue.class);
    var children = sequence(values.get(6), SyntaxChild.class);
    var reference = optional(values.get(7), SemanticReference.class);
    var origin = optional(values.get(8), SourceSpan.class);
    var node = origin.isPresent()
        ? SyntaxNode.source(source, path, sourceKind, kind, type, value, children, reference, origin.orElseThrow())
        : SyntaxNode.withoutDirectSource(source, path, sourceKind, kind, type, value, children, reference);
    validateSourceFormat(node);
    readNodes.set(ordinal, node);
    return node;
  }

  private SyntaxNode readNodeReference(CanonicalValueReader reader) {
    var ordinal = reader.integer();
    if (ordinal < 0 || ordinal >= readNodes.size() || readNodes.get(ordinal) == null) {
      throw new IllegalArgumentException("Source occurrence reference requires an earlier complete occurrence");
    }
    return Objects.requireNonNull(readNodes.get(ordinal), "owned source occurrence");
  }

  private LanguageFeatureCoverageResult readCoverage(CanonicalValueReader reader) {
    var coverage = required(read(reader), LanguageFeatureCoverage.class);
    var evidence = required(read(reader), CoverageValidationEvidence.class);
    return LanguageFeatureCoverageResult.fromEvidence(coverage, evidence.writtenInventory(), evidence.attributedExpectations(),
        evidence.outcomes(), evidence.unmatchedFacts(), evidence.diagnosticEmissions());
  }

  private SyntaxValue.Annotation readAnnotation(CanonicalValueReader reader) {
    var values = readSequence(reader);
    requireSize(values, 7, "syntax-value/annotation");
    var form = required(values.get(0), SyntaxValue.AnnotationForm.class);
    var arguments = sequence(values.get(1), SyntaxValue.AnnotationArgumentHeader.class);
    var location = required(values.get(2), SyntaxValue.AnnotationLocation.class);
    var targets = set(values.get(3), ElementType.class);
    var admissible = required(values.get(4), Boolean.class);
    var failure = optional(values.get(5), String.class);
    var container = optional(values.get(6), String.class);
    var annotation = failure.isPresent()
        ? SyntaxValue.Annotation.withArgumentFailure(form, arguments, location, targets, admissible, failure.orElseThrow())
        : new SyntaxValue.Annotation(form, arguments, location, targets, admissible);
    return annotation.withRepeatableContainer(container);
  }

  private void writeSequence(CanonicalValueWriter writer, List<?> values) {
    writer.count(values.size());
    values.forEach(value -> write(writer, value));
  }

  private List<Object> readSequence(CanonicalValueReader reader) {
    var count = reader.count();
    if (count > reader.remaining() / Integer.BYTES) {
      throw new IllegalArgumentException("Retained sequence count exceeds the remaining payload");
    }
    var values = new ArrayList<Object>(count);
    for (var index = 0; index < count; index++) {
      values.add(read(reader));
    }
    return List.copyOf(values);
  }

  private Set<Object> readSet(CanonicalValueReader reader) {
    var values = readSequence(reader);
    var result = new LinkedHashSet<>(values);
    if (result.size() != values.size()) {
      throw new IllegalArgumentException("Retained set cannot contain repeated entries");
    }
    return Set.copyOf(result);
  }

  private byte[] orderingBytes(Object value) {
    var writer = new CanonicalValueWriter("source-value/1");
    new RetainedValueCodec(canonical, format).write(writer, value);
    return writer.toByteArray();
  }

  private static Integer boundedInteger(CanonicalValueReader reader, int minimum, int maximum) {
    var value = reader.integer();
    if (value < minimum || value > maximum) {
      throw new IllegalArgumentException("Retained integer is outside its tagged width");
    }
    return value;
  }

  static <T> T required(Object value, Class<T> type) {
    if (!type.isInstance(value)) {
      throw new IllegalArgumentException("Unexpected retained value for " + type.getSimpleName());
    }
    return Objects.requireNonNull(type.cast(value), "retained value");
  }

  private static <T> List<T> sequence(Object value, Class<T> type) {
    if (!(value instanceof List<?> list)) {
      throw new IllegalArgumentException("Expected an ordered retained sequence");
    }
    return list.stream().map(component -> required(component, type)).toList();
  }

  private static <T> Set<T> set(Object value, Class<T> type) {
    if (!(value instanceof Set<?> values)) {
      throw new IllegalArgumentException("Expected a retained set");
    }
    var result = new LinkedHashSet<T>();
    values.forEach(component -> result.add(required(component, type)));
    return Set.copyOf(result);
  }

  private static <T> Optional<T> optional(Object value, Class<T> type) {
    if (!(value instanceof Optional<?> optional)) {
      throw new IllegalArgumentException("Expected explicit retained presence");
    }
    return optional.map(component -> required(component, type));
  }

  private static void requireSize(List<?> values, int size, String tag) {
    if (values.size() != size) {
      throw new IllegalArgumentException("Wrong retained field count: " + tag);
    }
  }

}
