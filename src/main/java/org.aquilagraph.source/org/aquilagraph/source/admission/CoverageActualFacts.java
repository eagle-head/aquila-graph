package org.aquilagraph.source.admission;

import static org.aquilagraph.source.internal.CoverageWrittenAttachments.Cardinality.LIST;
import static org.aquilagraph.source.internal.CoverageWrittenAttachments.Cardinality.OPTIONAL_VALUE;

import org.aquilagraph.source.internal.CoverageWrittenAttachments;

import org.aquilagraph.source.internal.CoverageActualFactUniverse;
import org.aquilagraph.source.api.AttributedCoverageExpectations;
import org.aquilagraph.source.internal.CoverageAnnotationFacts;
import org.aquilagraph.source.internal.CoverageArrayFacts;
import org.aquilagraph.source.api.CoverageEvidenceValue;
import org.aquilagraph.source.internal.CoverageExecutableFacts;
import org.aquilagraph.source.api.CoverageExecutableType;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoverageFactValue;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.internal.CoverageSourceProjection;
import org.aquilagraph.source.api.CoverageSyntaxFactValue;
import org.aquilagraph.source.api.CoverageTypeStructure;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.DeclarationTypeEvidence;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeNesting;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.CoverageFactReference.Kind;
import org.aquilagraph.source.api.CoveragePropertyValue.State;

/** Enumerates actual retained facts without consulting either expected inventory or producer encounter list. */
public final class CoverageActualFacts {
  private static final int EXPECTED_EMPTY_DECLARATION_OWNERS = 1;
  /** Captured component attribution at a match-all occurrence. */
  public static final String MATCH_ALL_TYPE_ROLE = "match-all-type";

  /**
   * Copies the required component identity independently of source syntax production.
   *
   * @param type
   *          copied compiler type
   * @return exact attributed identity, or unavailable required attribution
   */
  public static CoveragePropertyValue patternTypeValue(SyntaxType type) {
    return type instanceof SyntaxType.Resolved resolved
        ? new CoveragePropertyValue(State.SCALAR, List.of(typeItem(resolved.identity(), 0)))
        : new CoveragePropertyValue(State.UNAVAILABLE, List.of());
  }

  /**
   * Reconciles a match-all component type with its exact independently captured owner.
   *
   * @param context
   *          captured occurrence context
   * @param expected
   *          independently attributed component identity
   * @param facts
   *          actual retained facts
   * @return whether the occurrence retains the exact applicable type
   */
  public static boolean supportsPatternType(SourceContext context, CoveragePropertyValue expected, List<CoverageFactReference> facts) {
    if (facts.size() != 1 || !(facts.getFirst().value() instanceof CoverageFactValue.Source source))
      return false;
    var fact = facts.getFirst();
    var syntax = source.value();
    return fact.sourceFileKey().equals(context.address().sourceFileKey()) && fact.origin().stream().toList().equals(context.origins())
        && syntax.syntaxKind().equals(SyntaxKind.ANY_PATTERN.name())
        && syntax.sourceAssociation().filter(association -> association.ownerPath().equals(context.address().ownerPath())).isPresent()
        && syntax.type().tag() == CoverageEvidenceValue.Tag.TYPE_RESOLVED
        && syntax.type().fields().stream().filter(field -> "identity".equals(field.name())).anyMatch(
            field -> field.value() instanceof CoverageEvidenceValue.TypeIdentity identity && scalarType(expected, identity.value()));
  }

  private static final int EXPECTED_COMPACT_SOURCE_ROOTS = 1;
  private static final int EXPECTED_DECLARATION_OWNERS = 1;
  private static final String ANNOTATION_KIND = "ANNOTATION";
  private static final String ANNOTATION_TYPE_KIND = "ANNOTATION_TYPE";
  private static final String EXECUTABLE_ELEMENT_SURFACE = "ExecutableElement";
  private static final String TYPE_ELEMENT_SURFACE = "TypeElement";
  private static final String VOID_TYPE_NAME = "void";
  private static final String OBJECT_TYPE_NAME = "java.lang.Object";
  private CoverageActualFacts() {
  }

  /**
   * Reads actual retained declarations and source values, retaining independently correlated typed views.
   *
   * @param program
   *          actual retained declarations
   * @param aggregate
   *          actual retained source AST
   * @return immutable actual references and syntax-local views
   */
  public static CoverageActualFactView capture(JavaProgram program, Optional<JavaAstAggregate> aggregate) {
    var result = new ArrayList<CoverageFactReference>();
    var sourceValues = new HashMap<String, CoverageSyntaxFactValue>();
    for (var type : program.types()) {
      addTypeFacts(type, aggregate, result);
    }
    aggregate.ifPresent(value -> value.nodes().forEach(node -> {
      validatePackageQualifier(node, value.snapshot());
      var view = new CoverageSyntaxFactValue(
          node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), node.value(), node.children().stream()
              .map(child -> new CoverageSyntaxFactValue.Child(child.role(), child.position(), child.node().nodeId())).toList(),
          node.semanticReference(), value.writtenSourceAssociation(node.nodeId()));
      if (sourceValues.putIfAbsent(node.nodeId(), view) != null) {
        throw new IllegalArgumentException("Actual source occurrences require unique retained identities");
      }
      result.add(new CoverageFactReference(Kind.SYNTAX, node.nodeId(), node.sourceFileKey(),
          new CoverageFactValue.Source(CoverageSourceProjection.copy(view)), node.origin()));
    }));
    return new CoverageActualFactUniverse(result, sourceValues, aggregate.map(value -> value.snapshot().sourceFiles()).orElseGet(List::of));
  }

  private static void validatePackageQualifier(SyntaxNode node, InputSnapshot snapshot) {
    if (!(node.value() instanceof SyntaxValue.PackageDeclaration declaration) || declaration.qualifier().isEmpty()) {
      return;
    }
    var origin = declaration.qualifier().orElseThrow().origin();
    var source = snapshot.sourceFiles().stream().filter(file -> file.sourceFileKey().equals(node.sourceFileKey())).findFirst();
    var declarationOrigin = node.origin();
    if (!origin.sourceFileKey().equals(node.sourceFileKey()) || declarationOrigin.isEmpty() || source.isEmpty()
        || origin.startOffset() < declarationOrigin.orElseThrow().startOffset()
        || origin.endOffsetExclusive() > declarationOrigin.orElseThrow().endOffsetExclusive()
        || origin.endOffsetExclusive() > source.orElseThrow().content().length()) {
      throw new IllegalArgumentException("Package qualifier evidence must belong to its actual declaration and captured source");
    }
  }

  private static void addTypeFacts(JavaType type, Optional<JavaAstAggregate> aggregate, List<CoverageFactReference> result) {
    var origin = type.origins().stream().findFirst();
    var source = sourceFileKey(type, aggregate);
    result.add(new CoverageFactReference(Kind.TYPE, type.typeKey(), source, new CoverageFactValue.Type(type), origin));
    aggregate.flatMap(value -> value.declarationMemberOrder(type.typeKey()))
        .ifPresent(order -> result.add(new CoverageFactReference(Kind.DECLARATION_MEMBER_ORDER, type.typeKey() + "#enclosed-order",
            order.sourceFileKey(), new CoverageFactValue.MemberOrder(order), origin)));
    aggregate.flatMap(value -> value.declarationTypeEvidence(type.typeKey()))
        .ifPresent(evidence -> result.add(new CoverageFactReference(Kind.DECLARATION_TYPE, type.typeKey() + "#declared-type",
            evidence.sourceFileKey(), new CoverageFactValue.DeclarationType(evidence), origin)));
    type.fields().forEach(field -> result.add(new CoverageFactReference(Kind.FIELD, field.fieldKey(), source,
        new CoverageFactValue.Field(field), field.origins().stream().findFirst())));
    type.recordComponents().forEach(component -> result.add(new CoverageFactReference(Kind.RECORD_COMPONENT, component.componentKey(),
        source, new CoverageFactValue.RecordComponent(component), component.origins().stream().findFirst())));
    type.synthesizedDeclarations().forEach(declaration -> result.add(new CoverageFactReference(Kind.SYNTHESIZED_DECLARATION,
        declaration.declarationKey(), source, new CoverageFactValue.SynthesizedDeclaration(declaration), Optional.empty())));
    for (var method : type.methods()) {
      addMethodFacts(method, source, result);
    }
  }

  private static void addMethodFacts(JavaMethod method, String source, List<CoverageFactReference> result) {
    result.add(new CoverageFactReference(Kind.METHOD, method.methodKey(), source, new CoverageFactValue.Method(method),
        method.origins().stream().findFirst()));
    method.parameters().forEach(parameter -> result.add(new CoverageFactReference(Kind.PARAMETER, parameter.parameterKey(), source,
        new CoverageFactValue.Parameter(parameter), parameter.origins().stream().findFirst())));
  }

  /**
   * Returns the captured source file that owns the requested syntax occurrence.
   *
   * @param type
   *          required value type
   * @param aggregate
   *          aggregate
   * @return the resulting string
   */
  static String sourceFileKey(JavaType type, Optional<JavaAstAggregate> aggregate) {
    return type.origins().stream().findFirst().map(SourceSpan::sourceFileKey).orElseGet(() -> compactSource(type.typeKey(), aggregate));
  }

  private static String compactSource(String typeKey, Optional<JavaAstAggregate> aggregate) {
    var roots = aggregate.stream().flatMap(value -> value.roots().stream())
        .filter(root -> root.value() instanceof SyntaxValue.CompilationUnit unit && unit.form() == SyntaxValue.CompilationUnitForm.COMPACT)
        .filter(root -> root.children(SyntaxRole.COMPACT_MEMBER).stream()
            .anyMatch(member -> (member.value() instanceof SyntaxValue.ExecutableDeclaration method && method.ownerKey().equals(typeKey))
                || (member.value() instanceof SyntaxValue.VariableDeclaration field && field.ownerKey().equals(typeKey))))
        .toList();
    if (roots.size() != EXPECTED_COMPACT_SOURCE_ROOTS) {
      throw new IllegalArgumentException("A retained type requires a captured source owner");
    }
    return roots.getFirst().sourceFileKey();
  }

  /**
   * Validates copied direct-member orders against independently retained canonical declarations and source attachments.
   *
   * @param program
   *          actual program
   * @param aggregate
   *          actual aggregate
   */
  static void validateDeclarationMemberOrders(JavaProgram program, JavaAstAggregate aggregate) {
    for (var evidence : aggregate.declarationTypeEvidence()) {
      validateDeclarationType(evidence, program, aggregate);
    }
    for (var order : aggregate.declarationMemberOrders()) {
      validateMemberOrder(order, program, aggregate);
    }
  }

  private static void validateDeclarationType(DeclarationTypeEvidence evidence, JavaProgram program, JavaAstAggregate aggregate) {
    var actual = program.types().stream().filter(type -> type.typeKey().equals(evidence.ownerTypeKey())).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Declaration type evidence requires its actual canonical program owner"));
    var selected = (CoverageTypeStructure.Declared) evidence.structure();
    var source = sourceFileKey(actual, Optional.of(aggregate));
    var variables = actual.typeParameters().stream().map(parameter -> new CoverageTypeStructure.Variable(parameter.typeParameterKey()))
        .toList();
    if (!source.equals(evidence.sourceFileKey()) || !selected.arguments().equals(variables)
        || !selected.lexicalOwnerKey().equals(actual.nesting().lexicalOwnerKey()) || !evidence.identity().equals(actual.staticType())) {
      throw new IllegalArgumentException("Declaration type evidence must retain exact canonical parameters, lexical owner, and source");
    }
  }

  private static void validateMemberOrder(DeclarationMemberOrder order, JavaProgram program, JavaAstAggregate aggregate) {
    var type = program.types().stream().filter(value -> value.typeKey().equals(order.ownerTypeKey())).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Retained member order requires its actual canonical program owner"));
    var source = sourceFileKey(type, Optional.of(aggregate));
    if (!source.equals(order.sourceFileKey())) {
      throw new IllegalArgumentException("Retained member order source must match its actual program owner");
    }
    var kinds = new HashMap<String, String>();
    type.fields().forEach(field -> kinds.put(field.fieldKey(), field.enumConstant() ? "ENUM_CONSTANT" : "FIELD"));
    type.methods().forEach(method -> kinds.put(method.methodKey(), method.constructor() ? "CONSTRUCTOR" : "METHOD"));
    type.recordComponents().forEach(component -> kinds.put(component.componentKey(), "RECORD_COMPONENT"));
    type.synthesizedDeclarations().forEach(declaration -> kinds.put(declaration.declarationKey(), declaration.kind().name()));
    program.types().stream().filter(value -> value.nesting().kind() == JavaTypeNesting.Kind.MEMBER)
        .filter(value -> value.enclosingType().flatMap(reference -> reference.declarationKey()).filter(type.typeKey()::equals).isPresent())
        .forEach(
            value -> kinds.put(value.typeKey(), ANNOTATION_KIND.equals(value.kind().name()) ? ANNOTATION_TYPE_KIND : value.kind().name()));
    if (!new HashSet<>(order.members().stream().map(DeclarationMemberOrder.Member::declarationKey).toList()).equals(kinds.keySet())
        || order.members().stream().anyMatch(member -> !member.kind().equals(kinds.get(member.declarationKey())))) {
      throw new IllegalArgumentException("Retained member order must exactly match canonical direct-member identities and kinds");
    }
    if (type.methods().stream().anyMatch(method -> method.sourceDeclared() != !method.origins().isEmpty())) {
      throw new IllegalArgumentException("Canonical method source declaration state must match its retained source origins");
    }
    if (!familyOrder(order, type.fields().stream().map(field -> field.fieldKey()).toList())
        || !familyOrder(order, type.methods().stream().map(method -> method.methodKey()).toList())
        || !familyOrder(order, type.recordComponents().stream().map(component -> component.componentKey()).toList())
        || !order.members().stream().map(DeclarationMemberOrder.Member::declarationKey)
            .filter(key -> type.synthesizedDeclarations().stream().anyMatch(declaration -> declaration.declarationKey().equals(key)))
            .sorted().toList().equals(type.synthesizedDeclarations().stream().map(declaration -> declaration.declarationKey()).toList())) {
      throw new IllegalArgumentException("Retained member order must preserve actual program order within each declaration family: "
          + order.members().stream().map(DeclarationMemberOrder.Member::declarationKey).toList() + " actual "
          + type.fields().stream().map(field -> field.fieldKey()).toList() + ' '
          + type.methods().stream().map(method -> method.methodKey()).toList() + ' '
          + type.recordComponents().stream().map(component -> component.componentKey()).toList() + ' '
          + type.synthesizedDeclarations().stream().map(declaration -> declaration.declarationKey()).toList());
    }
    var owner = aggregate.nodes().stream()
        .filter(
            node -> node.value() instanceof SyntaxValue.TypeDeclaration declaration && declaration.declarationKey().equals(type.typeKey()))
        .findFirst();
    var writtenMembers = owner.map(node -> node.children(SyntaxRole.MEMBER)).orElseGet(() -> aggregate.roots().stream()
        .filter(root -> root.sourceFileKey().equals(order.sourceFileKey())).findFirst().orElseThrow().children(SyntaxRole.COMPACT_MEMBER));
    var written = new HashMap<String, Integer>();
    for (var index = 0; index < writtenMembers.size(); index++) {
      var key = declarationKey(writtenMembers.get(index).value());
      if (key.isPresent()) {
        written.put(key.orElseThrow(), index);
      }
    }
    if (order.members().stream()
        .anyMatch(member -> !member.writtenOrdinal().equals(Optional.ofNullable(written.get(member.declarationKey())))
            || (written.containsKey(member.declarationKey()) && member.provenance() != CoveragePropertyValue.Provenance.WRITTEN))) {
      throw new IllegalArgumentException("Retained member order must preserve exact written source attachment order and provenance");
    }
  }

  private static boolean familyOrder(DeclarationMemberOrder order, List<String> members) {
    return order.members().stream().map(DeclarationMemberOrder.Member::declarationKey).filter(members::contains).toList().equals(members);
  }

  private static boolean memberDisplayMatches(DeclarationMemberOrder.Member member, String sourceFileKey, CoverageActualFactView facts) {
    return facts.withIdentity(sourceFileKey, member.declarationKey()).stream().findFirst().map(fact -> switch (fact.value()) {
      case CoverageFactValue.Field field ->
        member.displayValue().equals((field.value().enumConstant() ? "ENUM_CONSTANT:" : "FIELD:") + field.value().name());
      case CoverageFactValue.RecordComponent component -> member.displayValue().equals("RECORD_COMPONENT:" + component.value().name());
      case CoverageFactValue.Type type -> member.displayValue()
          .equals((ANNOTATION_KIND.equals(type.value().kind().name()) ? ANNOTATION_TYPE_KIND : type.value().kind().name()) + ':'
              + type.value().qualifiedName());
      case CoverageFactValue.Method _,CoverageFactValue.SynthesizedDeclaration _ -> true;
      default -> false;
    }).orElse(false);
  }

  private static Optional<String> declarationKey(SyntaxValue value) {
    return switch (value) {
      case SyntaxValue.TypeDeclaration type -> Optional.of(type.declarationKey());
      case SyntaxValue.ExecutableDeclaration method -> Optional.of(method.declarationKey());
      case SyntaxValue.VariableDeclaration field -> Optional.of(field.declarationKey());
      default -> Optional.empty();
    };
  }

  /**
   * Checks an original compiler kind against its explicitly copied normalized-region observation.
   *
   * @param context
   *          original captured compiler occurrence
   * @param syntax
   *          actual written-region root fact
   * @return whether a real normalization observation corroborates that occurrence
   */
  public static boolean supportsNormalizedCompilerOccurrence(SourceContext context, CoverageSyntaxFactValue syntax) {
    return syntax.sourceAssociation().flatMap(association -> association.normalizedExpression())
        .filter(value -> value.occurrencePath().isEmpty() && value.region().occurrences().stream()
            .noneMatch(occurrence -> occurrence.compilerOperand().filter(context.address().ownerPath()::equals).isPresent()))
        .stream().flatMap(value -> value.observations().stream())
        .anyMatch(property -> property.ownerPath().equals(context.address().ownerPath())
            && property.origin().stream().toList().equals(context.origins())
            && context.feature().map(feature -> property.sourceKind().name().equals(feature.identifier())).orElse(false));
  }

  /**
   * Resolves a canonical declaration's captured containing type without guessing from missing source positions.
   *
   * @param fact
   *          actual retained declaration fact
   * @param inventory
   *          independently captured written owners
   * @param attributed
   *          independently captured attributed owner descriptors
   * @param facts
   *          actual retained declaration and source values
   * @return the unique exact captured declaring owner, or empty when ownership is unavailable or ambiguous
   */
  public static Optional<SourceContext> capturedDeclarationOwner(CoverageFactReference fact, SourceCoverageInventory inventory,
      AttributedCoverageExpectations attributed, CoverageActualFactView facts) {
    Objects.requireNonNull(fact, "fact");
    Objects.requireNonNull(inventory, "inventory");
    Objects.requireNonNull(attributed, "attributed");
    Objects.requireNonNull(facts, "facts");
    var keys = declaringTypeKeys(fact, facts);
    var types = facts.stream().filter(candidate -> candidate.sourceFileKey().equals(fact.sourceFileKey())
        && candidate.value() instanceof CoverageFactValue.Type type && keys.contains(type.value().typeKey())).toList();
    if (types.size() != EXPECTED_DECLARATION_OWNERS) {
      return Optional.empty();
    }
    var typeFact = types.getFirst();
    var type = ((CoverageFactValue.Type) typeFact.value()).value();
    var owners = type.sourceDeclared()
        ? writtenTypeOwners(typeFact, type, inventory, attributed, facts)
        : compactTypeOwners(typeFact, type, inventory, facts);
    return owners.size() == EXPECTED_DECLARATION_OWNERS ? Optional.of(owners.getFirst()) : Optional.empty();
  }

  private static List<String> declaringTypeKeys(CoverageFactReference fact, CoverageActualFactView facts) {
    return switch (fact.value()) {
      case CoverageFactValue.Type type -> List.of(type.value().typeKey());
      case CoverageFactValue.Method method -> List.of(method.value().ownerTypeKey());
      case CoverageFactValue.Field field -> List.of(field.value().ownerTypeKey());
      case CoverageFactValue.RecordComponent component -> List.of(component.value().ownerTypeKey());
      case CoverageFactValue.SynthesizedDeclaration declaration -> List.of(declaration.value().ownerTypeKey());
      case CoverageFactValue.MemberOrder order -> List.of(order.value().ownerTypeKey());
      case CoverageFactValue.DeclarationType type -> List.of(type.value().ownerTypeKey());
      case CoverageFactValue.Parameter parameter -> {
        var methods = facts.stream().filter(candidate -> candidate.sourceFileKey().equals(fact.sourceFileKey()))
            .map(CoverageFactReference::value).filter(CoverageFactValue.Method.class::isInstance).map(CoverageFactValue.Method.class::cast)
            .filter(method -> method.value().parameters().contains(parameter.value())).toList();
        yield methods.size() == EXPECTED_DECLARATION_OWNERS ? List.of(methods.getFirst().value().ownerTypeKey()) : List.of();
      }
      case CoverageFactValue.Source _ -> List.of();
    };
  }

  private static List<SourceContext> writtenTypeOwners(CoverageFactReference typeFact, JavaType type, SourceCoverageInventory inventory,
      AttributedCoverageExpectations attributed, CoverageActualFactView facts) {
    var sourceOwners = facts.stream().filter(candidate -> candidate.sourceFileKey().equals(typeFact.sourceFileKey()))
        .flatMap(facts::syntaxValues).filter(syntax -> syntax.value() instanceof SyntaxValue.TypeDeclaration declaration
            && declaration.declarationKey().equals(type.typeKey()))
        .flatMap(syntax -> syntax.sourceAssociation().stream()).toList();
    var kind = ANNOTATION_KIND.equals(type.kind().name()) ? ANNOTATION_TYPE_KIND : type.kind().name();
    return inventory.contexts().stream()
        .filter(context -> context.address().partition() == SourceContext.Partition.TREE
            && context.address().sourceFileKey().equals(typeFact.sourceFileKey())
            && context.feature().filter(feature -> feature.identifier().equals(kind)).isPresent()
            && context.origins().equals(typeFact.origin().stream().toList())
            && attributed.projections().stream()
                .anyMatch(projection -> projection.address().sourceFileKey().equals(context.address().sourceFileKey())
                    && projection.address().ownerPath().equals(context.address().ownerPath())
                    && projection.ownerIdentity().filter(type.typeKey()::equals).isPresent())
            && (sourceOwners.isEmpty() || (sourceOwners.size() == EXPECTED_DECLARATION_OWNERS
                && sourceOwners.getFirst().ownerPath().equals(context.address().ownerPath())
                && sourceOwners.getFirst().origin().stream().toList().equals(context.origins()))))
        .toList();
  }

  private static List<SourceContext> compactTypeOwners(CoverageFactReference typeFact, JavaType type, SourceCoverageInventory inventory,
      CoverageActualFactView facts) {
    var roots = facts.stream().filter(candidate -> candidate.sourceFileKey().equals(typeFact.sourceFileKey()))
        .filter(root -> compactOwns(root, type.typeKey(), facts)).flatMap(root -> facts.requiredSyntax(root).sourceAssociation().stream())
        .toList();
    if (roots.size() != EXPECTED_COMPACT_SOURCE_ROOTS) {
      return List.of();
    }
    var root = roots.getFirst();
    return inventory.contexts().stream()
        .filter(context -> context.address().partition() == SourceContext.Partition.TREE
            && context.address().sourceFileKey().equals(typeFact.sourceFileKey())
            && context.feature().filter(feature -> "COMPILATION_UNIT".equals(feature.identifier())).isPresent()
            && context.address().ownerPath().equals(root.ownerPath()) && context.origins().equals(root.origin().stream().toList()))
        .toList();
  }

  private static boolean compactOwns(CoverageFactReference root, String typeKey, CoverageActualFactView facts) {
    return facts.isSource(root,
        syntax -> syntax.value() instanceof SyntaxValue.CompilationUnit unit && unit.form() == SyntaxValue.CompilationUnitForm.COMPACT)
        && facts.children(root, List.of(SyntaxRole.COMPACT_MEMBER)).stream().map(facts::requiredSyntax)
            .anyMatch(syntax -> (syntax.value() instanceof SyntaxValue.ExecutableDeclaration method && method.ownerKey().equals(typeKey))
                || (syntax.value() instanceof SyntaxValue.VariableDeclaration field && field.ownerKey().equals(typeKey)));
  }

  /**
   * Checks copied accessor presence, scalar values and ordered item boundaries against actual owners.
   *
   * @param context
   *          captured owner/accessor
   * @param expected
   *          independent accessor projection
   * @param owned
   *          actual owner facts
   * @param actualFacts
   *          independently enumerated actual references and syntax views
   * @return whether the represented accessor is supported by actual facts
   */
  public static boolean supportsProperty(SourceContext context, CoveragePropertyValue expected, List<CoverageFactReference> owned,
      CoverageActualFactView actualFacts) {
    if (context.address().partition() == SourceContext.Partition.MODULE_ENDPOINT) {
      return ModuleServiceFacts.supports(context, expected, owned, actualFacts);
    }
    if (context.address().partition() == SourceContext.Partition.TYPE && MATCH_ALL_TYPE_ROLE.equals(context.address().role())) {
      return supportsPatternType(context, expected, owned);
    }
    if (owned.isEmpty() || expected.state() == State.UNAVAILABLE) {
      return false;
    }
    var role = context.address().role();
    var method = role.substring(role.indexOf('#') + 1, role.indexOf('('));
    var sourceOwner = owned.stream().filter(fact -> actualFacts.isSource(fact, value -> matchingOwner(role, value))).findFirst();
    var sourceSyntax = sourceOwner.map(actualFacts::requiredSyntax);
    if (CoverageExecutableFacts.isSurface(role)) {
      return sourceOwner.map(owner -> CoverageExecutableFacts.supports(method, expected, owner, actualFacts)).orElse(false);
    }
    if (role.startsWith("NewArrayTree")) {
      return sourceOwner.map(owner -> CoverageArrayFacts.supports(method, expected, owner, actualFacts)).orElse(false);
    }
    var type = owned.stream().map(CoverageFactReference::value).filter(CoverageFactValue.Type.class::isInstance)
        .map(CoverageFactValue.Type.class::cast).map(CoverageFactValue.Type::value).findFirst();
    var executable = role.startsWith(TYPE_ELEMENT_SURFACE)
        ? Optional.<JavaMethod>empty()
        : owned.stream().map(CoverageFactReference::value).filter(CoverageFactValue.Method.class::isInstance)
            .map(CoverageFactValue.Method.class::cast).map(CoverageFactValue.Method::value).findFirst();
    return switch (method) {
      case "getName", "getSimpleName" -> executable
          .map(value -> scalarText(expected, value.constructor() ? "<init>" : value.name(), role.startsWith(EXECUTABLE_ELEMENT_SURFACE)))
          .orElseGet(() -> type
              .map(value -> scalarText(expected,
                  sourceSyntax.filter(syntax -> syntax.value() instanceof SyntaxValue.TypeDeclaration)
                      .map(syntax -> ((SyntaxValue.TypeDeclaration) syntax.value()).name())
                      .orElseGet(() -> value.qualifiedName().substring(value.qualifiedName().lastIndexOf('.') + 1)),
                  role.startsWith(TYPE_ELEMENT_SURFACE)))
              .orElseGet(() -> sourceSyntax.map(value -> name(value.value()).filter(text -> scalarText(expected, text, false)).isPresent())
                  .orElse(false)));
      case "getQualifiedName" -> type.map(value -> scalarText(expected, value.qualifiedName(), true)).orElse(false);
      case "getBody" ->
        sourceOwner.map(owner -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.BODY), actualFacts, OPTIONAL_VALUE))
            .orElse(false);
      case "getDefaultValue" -> sourceOwner
          .map(owner -> actualFacts.requiredSyntax(owner).value() instanceof SyntaxValue.ExecutableDeclaration declaration
              && (role.startsWith(EXECUTABLE_ELEMENT_SURFACE)
                  ? CoverageAnnotationFacts.defaultValue(expected, declaration,
                      actualFacts.children(owner, List.of(SyntaxRole.DEFAULT_VALUE)), actualFacts)
                  : CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.DEFAULT_VALUE), actualFacts, OPTIONAL_VALUE)))
          .orElse(false);
      case "getReceiverParameter",
          "getNameExpression" ->
        sourceOwner.map(owner -> CoverageWrittenAttachments.matches(expected, owner,
            List.of("getReceiverParameter".equals(method) ? SyntaxRole.RECEIVER_PARAMETER : SyntaxRole.QUALIFIER), actualFacts,
            OPTIONAL_VALUE)).orElse(false);
      case "getReturnType" -> role.startsWith(EXECUTABLE_ELEMENT_SURFACE)
          ? sourceOwner.map(owner -> actualFacts.requiredSyntax(owner).value() instanceof SyntaxValue.ExecutableDeclaration declaration
              && ((declaration.kind() == SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR
                  || declaration.kind() == SyntaxValue.ExecutableDeclaration.ExecutableKind.COMPACT_CONSTRUCTOR)
                      ? scalarType(expected, new JavaTypeIdentity(JavaTypeCategory.VOID, VOID_TYPE_NAME, VOID_TYPE_NAME))
                      : actualFacts.children(owner, List.of(SyntaxRole.RETURN_TYPE)).stream().map(actualFacts::requiredSyntax)
                          .flatMap(syntax -> resolvedIdentity(syntax.type()).stream())
                          .anyMatch(identity -> scalarType(expected, identity))))
              .orElse(false)
          : sourceOwner.map(
              owner -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.RETURN_TYPE), actualFacts, OPTIONAL_VALUE))
              .orElse(false);
      case "getInitializer" ->
        sourceOwner
            .map(owner -> actualFacts.requiredSyntax(owner).syntaxKind() == SyntaxKind.ENUM_CONSTANT_DECLARATION
                ? CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.ARGUMENT, SyntaxRole.CLASS_BODY), actualFacts,
                    LIST)
                : CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.INITIALIZER), actualFacts, OPTIONAL_VALUE))
            .orElse(false);
      case "getPackage" -> sourceOwner.map(owner -> CoverageWrittenAttachments.matches(expected, owner,
          List.of(SyntaxRole.PACKAGE_DECLARATION), actualFacts, OPTIONAL_VALUE)).orElse(false);
      case "getPackageName" -> sourceOwner.map(owner -> {
        var declarations = actualFacts.children(owner, List.of(SyntaxRole.PACKAGE_DECLARATION));
        return expected.state() == State.ABSENT
            ? declarations.isEmpty()
            : declarations.size() == 1
                && actualFacts.requiredSyntax(declarations.getFirst()).value() instanceof SyntaxValue.PackageDeclaration declaration
                && writtenPackageName(expected, declarations.getFirst(), declaration, actualFacts);
      }).orElse(false);
      case "getModule" -> sourceOwner.map(
          owner -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.MODULE_DECLARATION), actualFacts, OPTIONAL_VALUE))
          .orElse(false);
      case "getImports" -> sourceOwner
          .map(owner -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.IMPORT_DECLARATION), actualFacts, LIST))
          .orElse(false);
      case "getTypeDecls" -> sourceOwner
          .map(owner -> (actualFacts.requiredSyntax(owner).value() instanceof SyntaxValue.CompilationUnit unit
              && unit.form() == SyntaxValue.CompilationUnitForm.COMPACT)
                  ? expected.equals(
                      elements(List.of(new CoveragePropertyValue.Item("CLASS:0", "CLASS", CoveragePropertyValue.Provenance.WRITTEN))))
                      && type.filter(declaration -> !declaration.sourceDeclared()).isPresent()
                  : CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.TYPE_DECLARATION), actualFacts, LIST))
          .orElse(false);
      case "getMembers" -> sourceOwner.map(owner -> CoverageWrittenAttachments.matches(expected, owner,
          List.of(SyntaxRole.RECORD_COMPONENT, SyntaxRole.MEMBER, SyntaxRole.COMPACT_MEMBER), actualFacts, LIST)).orElse(false);
      case "getParameters" -> role.startsWith(EXECUTABLE_ELEMENT_SURFACE)
          ? executable.map(value -> expected.equals(elements(value.parameters().stream()
              .map(parameter -> new CoveragePropertyValue.Item(parameter.parameterKey(), "PARAMETER:" + parameter.name(),
                  provenance(parameter.sourceDeclared())))
              .toList()))).orElse(false)
          : sourceOwner.map(owner -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.PARAMETER), actualFacts, LIST))
              .orElse(false);
      case "getTypeParameters" ->
        role.startsWith(
            TYPE_ELEMENT_SURFACE)
                ? type
                    .map(
                        value -> expected.equals(elements(value.typeParameters().stream()
                            .map(parameter -> new CoveragePropertyValue.Item(parameter.typeParameterKey(),
                                "TYPE_PARAMETER:" + parameter.name(), provenance(parameter.sourceDeclared())))
                            .toList())))
                    .orElse(false)
                : role.startsWith(EXECUTABLE_ELEMENT_SURFACE)
                    ? sourceOwner
                        .map(owner -> expected.equals(elements(
                            actualFacts.children(owner, List.of(SyntaxRole.TYPE_PARAMETER)).stream().map(actualFacts::requiredSyntax)
                                .map(CoverageSyntaxFactValue::value).map(SyntaxValue.TypeParameterDeclaration.class::cast)
                                .map(parameter -> new CoveragePropertyValue.Item(parameter.declarationKey(),
                                    "TYPE_PARAMETER:" + parameter.name(), CoveragePropertyValue.Provenance.WRITTEN))
                                .toList())))
                        .orElse(false)
                    : sourceOwner.map(
                        owner -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.TYPE_PARAMETER), actualFacts, LIST))
                        .orElse(false);
      case "getThrows" ->
        sourceOwner.map(owner -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.THROWN_TYPE), actualFacts, LIST))
            .orElse(false);
      case "getThrownTypes" -> sourceSyntax.map(value -> value.value() instanceof SyntaxValue.ExecutableDeclaration declaration
          && typeSequence(expected, declaration.thrownTypes().stream().flatMap(thrown -> resolvedIdentity(thrown).stream()).toList())
          && cardinality(expected, declaration.thrownTypes().size())).orElse(false);
      case "getImplementsClause" ->
        sourceOwner.map(owner -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.INTERFACE), actualFacts, LIST))
            .orElse(false);
      case "getInterfaces" ->
        type.map(value -> typeSequence(expected, value.directInterfaces().stream().map(reference -> reference.identity()).toList()))
            .orElse(false);
      case "getPermitsClause" -> sourceOwner
          .map(owner -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.PERMITTED_SUBCLASS), actualFacts, LIST))
          .orElse(false);
      case "getPermittedSubclasses" ->
        type.map(value -> typeSequence(expected, value.permittedSubclasses().stream().map(reference -> reference.identity()).toList()))
            .orElse(false);
      case "getExtendsClause" -> sourceOwner
          .map(owner -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.SUPERCLASS), actualFacts, OPTIONAL_VALUE))
          .orElse(false);
      case "getRecordComponents" -> type
          .map(
              value -> expected
                  .equals(
                      elements(
                          value.recordComponents().stream()
                              .map(component -> new CoveragePropertyValue.Item(component.componentKey(),
                                  "RECORD_COMPONENT:" + component.name(), CoveragePropertyValue.Provenance.WRITTEN))
                              .toList())))
          .orElse(false);
      case "isDefault" ->
        executable.map(value -> scalarText(expected, Boolean.toString(value.modifiers().contains("default")), true)).orElse(false);
      case "isVarArgs" -> sourceSyntax.map(value -> value.value() instanceof SyntaxValue.ExecutableDeclaration declaration
          && scalarText(expected, Boolean.toString(declaration.shape().varArgs()), true)).orElse(false);
      case "getAnnotationMirrors" -> sourceOwner
          .map(owner -> CoverageAnnotationFacts.mirrors(expected, actualFacts.children(owner, List.of(SyntaxRole.ANNOTATION)), actualFacts))
          .orElse(false);
      case "getPackageAnnotations" ->
        sourceOwner.map(owner -> actualFacts.children(owner, List.of(SyntaxRole.PACKAGE_DECLARATION)).stream().findFirst()
            .map(
                declaration -> CoverageWrittenAttachments.matches(expected, declaration, List.of(SyntaxRole.ANNOTATION), actualFacts, LIST))
            .orElse(expected.state() == State.EMPTY)).orElse(false);
      case "getEnclosedElements" -> owned.stream().map(CoverageFactReference::value).filter(CoverageFactValue.MemberOrder.class::isInstance)
          .map(CoverageFactValue.MemberOrder.class::cast).map(CoverageFactValue.MemberOrder::value).findFirst()
          .map(order -> expected.items().equals(order.members().stream()
              .map(member -> new CoveragePropertyValue.Item(member.declarationKey(), member.displayValue(), member.provenance())).toList())
              && cardinality(expected, order.members().size())
              && order.members().stream().allMatch(member -> memberDisplayMatches(member, order.sourceFileKey(), actualFacts)))
          .orElse(false);
      case "getModifiers" -> role.startsWith(TYPE_ELEMENT_SURFACE)
          ? type
              .map(value -> semanticModifiers(expected,
                  value.modifiers().stream().map(modifier -> modifier.name().toLowerCase(Locale.ROOT).replace('_', '-')).sorted().toList()))
              .orElse(false)
          : role.startsWith(EXECUTABLE_ELEMENT_SURFACE)
              ? executable.map(value -> semanticModifiers(expected, value.modifiers().stream().sorted().toList())).orElse(false)
              : sourceSyntax.map(value -> writtenModifiers(expected, value.value(), actualFacts.references(), type)).orElse(false);
      case "getKind" -> role.startsWith(EXECUTABLE_ELEMENT_SURFACE)
          ? executable.map(value -> scalarItem(expected, value.constructor() ? "CONSTRUCTOR" : "METHOD", true)).orElse(false)
          : role.startsWith(TYPE_ELEMENT_SURFACE)
              ? type.map(value -> scalarItem(expected,
                  ANNOTATION_KIND.equals(value.kind().name()) ? ANNOTATION_TYPE_KIND : value.kind().name(), true)).orElse(false)
              : (role.startsWith("ClassTree") && type.filter(value -> !value.sourceDeclared()).isPresent())
                  ? scalarItem(expected, "CLASS", false)
                  : sourceSyntax.map(value -> scalarItem(expected, value.sourceKind().name(), false)).orElse(false);
      case "getEnclosingElement" ->
        executable.map(value -> enclosingElement(expected, context.address().sourceFileKey(), value.ownerTypeKey(), actualFacts))
            .orElseGet(() -> type.map(value -> value.nesting().lexicalOwnerKey().map(key -> value.nesting().ownerDetails()
                .map(owner -> expected.equals(new CoveragePropertyValue(State.SCALAR,
                    List.of(new CoveragePropertyValue.Item(key, owner.kind() + ':' + owner.displayName(), provenance(owner.written()))))))
                .orElseGet(() -> enclosingElement(expected, context.address().sourceFileKey(), key, actualFacts)))
                .orElseGet(() -> packageOwner(expected, context.address().sourceFileKey(), actualFacts))).orElse(false));
      case "getNestingKind" -> type.map(value -> scalarItem(expected, value.nesting().kind().name(), true)).orElse(false);
      case "getSuperclass" -> type.map(value -> value.directSuperclass().map(reference -> scalarType(expected, reference.identity()))
          .orElse(expected.state() == State.ABSENT)).orElse(false);
      case "getReceiverType" -> sourceSyntax.map(value -> value.value() instanceof SyntaxValue.ExecutableDeclaration declaration
          && (declaration.receiverType() instanceof SyntaxType.Resolved resolved
              ? scalarType(expected, resolved.identity())
              : expected.state() == State.ABSENT))
          .orElse(false);
      case "asType" -> type
          .map(value -> owned.stream().map(CoverageFactReference::value).filter(CoverageFactValue.DeclarationType.class::isInstance)
              .map(CoverageFactValue.DeclarationType.class::cast).map(CoverageFactValue.DeclarationType::value)
              .filter(evidence -> evidence.ownerTypeKey().equals(value.typeKey()))
              .anyMatch(evidence -> expected.equals(new CoveragePropertyValue(State.SCALAR,
                  List.of(new CoveragePropertyValue.Item("DECLARED:0", evidence.identity().displayName(),
                      CoveragePropertyValue.Provenance.COMPILER_DERIVED, Optional.empty(), Optional.of(evidence.identity()),
                      Optional.empty(), Optional.empty(), Optional.of(evidence.structure())))))))
          .orElseGet(() -> executable
              .map(value -> sourceOwner.filter(owner -> supportsExecutableType(expected, owner, value, actualFacts)).isPresent())
              .orElse(false));
      case "getType" -> sourceOwner
          .map(owner -> actualFacts.requiredSyntax(owner).value() instanceof SyntaxValue.LocalDeclaration local
              ? writtenLocalType(expected, owner, local, actualFacts)
              : CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.DECLARED_TYPE), actualFacts, OPTIONAL_VALUE))
          .orElse(false);
      default -> false;
    };
  }

  private static boolean supportsExecutableType(CoveragePropertyValue expected, CoverageFactReference owner, JavaMethod method,
      CoverageActualFactView actualFacts) {
    return actualExecutableType(owner, method, actualFacts)
        .filter(actual -> expected.equals(new CoveragePropertyValue(State.SCALAR, List.of(new CoveragePropertyValue.Item("EXECUTABLE:0",
            actual.canonicalText(), CoveragePropertyValue.Provenance.COMPILER_DERIVED, Optional.of(actual))))))
        .isPresent();
  }

  private static Optional<CoverageExecutableType> actualExecutableType(CoverageFactReference owner, JavaMethod method,
      CoverageActualFactView actualFacts) {
    if (!(actualFacts.requiredSyntax(owner).value() instanceof SyntaxValue.ExecutableDeclaration declaration)
        || !declaration.declarationKey().equals(method.methodKey())) {
      return Optional.empty();
    }
    var variables = actualFacts.children(owner, List.of(SyntaxRole.TYPE_PARAMETER));
    var copiedVariables = new ArrayList<CoverageExecutableType.Variable>();
    for (var ordinal = 0; ordinal < variables.size(); ordinal++) {
      var variable = actualExecutableVariable(variables.get(ordinal), ordinal, method.methodKey(), actualFacts);
      if (variable.isEmpty()) {
        return Optional.empty();
      }
      copiedVariables.add(variable.orElseThrow());
    }
    var returnTypes = actualFacts.children(owner, List.of(SyntaxRole.RETURN_TYPE)).stream().map(actualFacts::requiredSyntax)
        .map(CoverageSyntaxFactValue::type).flatMap(value -> resolvedIdentity(value).stream()).toList();
    var returnType = method.constructor()
        ? Optional.of(new JavaTypeIdentity(JavaTypeCategory.VOID, VOID_TYPE_NAME, VOID_TYPE_NAME))
        : returnTypes.size() == 1 ? Optional.of(returnTypes.getFirst()) : Optional.<JavaTypeIdentity>empty();
    var thrownTypes = declaration.thrownTypes().stream().flatMap(value -> resolvedIdentity(value).stream()).toList();
    if (returnType.isEmpty() || thrownTypes.size() != declaration.thrownTypes().size()
        || declaration.receiverType() instanceof SyntaxType.Unresolved) {
      return Optional.empty();
    }
    return Optional
        .of(new CoverageExecutableType(copiedVariables, method.parameters().stream().map(parameter -> parameter.staticType()).toList(),
            returnType.orElseThrow(), thrownTypes, resolvedIdentity(declaration.receiverType())));
  }

  private static Optional<CoverageExecutableType.Variable> actualExecutableVariable(CoverageFactReference source, int ordinal,
      String ownerKey, CoverageActualFactView actualFacts) {
    if (!(actualFacts.requiredSyntax(source).value() instanceof SyntaxValue.TypeParameterDeclaration variable)
        || variable.ordinal() != ordinal || !variable.ownerKey().equals(ownerKey)) {
      return Optional.empty();
    }
    return typeParameterErasure(source, variable, actualFacts).map(erasure -> new CoverageExecutableType.Variable(variable.declarationKey(),
        new JavaTypeIdentity(JavaTypeCategory.TYPE_VARIABLE, variable.name(), erasure)));
  }

  private static Optional<String> typeParameterErasure(CoverageFactReference source, SyntaxValue.TypeParameterDeclaration variable,
      CoverageActualFactView actualFacts) {
    var bounds = actualFacts.children(source, List.of(SyntaxRole.TYPE_BOUND));
    if (bounds.size() != variable.bounds()) {
      return Optional.empty();
    }
    if (bounds.isEmpty()) {
      return JavaTypeDeclarationIdentity.isPlatformType(variable.upperBound(), "java.lang.Object")
          ? Optional.of(OBJECT_TYPE_NAME)
          : Optional.empty();
    }
    var first = actualFacts.requiredSyntax(bounds.getFirst()).type();
    if (!(first instanceof SyntaxType.Resolved resolved)
        || (resolved.identity().category() != JavaTypeCategory.DECLARED && resolved.identity().category() != JavaTypeCategory.TYPE_VARIABLE)
        || resolved.structure().isEmpty()) {
      return Optional.empty();
    }
    var upper = variable.upperBound() instanceof JavaTypeStructure.Intersection intersection
        ? intersection.bounds().getFirst()
        : variable.upperBound();
    return resolved.structure().filter(upper::equals).map(ignored -> resolved.identity().erasedName());
  }

  private static Optional<JavaTypeIdentity> resolvedIdentity(SyntaxType value) {
    return value instanceof SyntaxType.Resolved resolved ? Optional.of(resolved.identity()) : Optional.empty();
  }

  private static CoveragePropertyValue.Provenance provenance(boolean written) {
    return written ? CoveragePropertyValue.Provenance.WRITTEN : CoveragePropertyValue.Provenance.COMPILER_DERIVED;
  }

  private static CoveragePropertyValue elements(List<CoveragePropertyValue.Item> items) {
    return new CoveragePropertyValue(items.isEmpty() ? State.EMPTY : State.ITEMS, items);
  }

  private static boolean semanticModifiers(CoveragePropertyValue expected, List<String> flags) {
    return expected.equals(elements(flags.stream()
        .map(flag -> new CoveragePropertyValue.Item(flag, flag, CoveragePropertyValue.Provenance.COMPILER_DERIVED)).toList()));
  }

  private static boolean writtenModifiers(CoveragePropertyValue expected, SyntaxValue value, List<CoverageFactReference> facts,
      Optional<JavaType> type) {
    var flags = switch (value) {
      case SyntaxValue.TypeDeclaration declaration -> declaration.writtenModifiers().stream()
          .map(modifier -> modifier.name().toLowerCase(Locale.ROOT).replace('_', '-')).sorted().toList();
      case SyntaxValue.ExecutableDeclaration declaration -> declaration.writtenModifiers().stream().map(Object::toString)
          .map(flag -> flag.toLowerCase(Locale.ROOT).replace('_', '-')).sorted().toList();
      case SyntaxValue.VariableDeclaration declaration -> declaration.writtenModifiers().stream().map(Object::toString)
          .map(flag -> flag.toLowerCase(Locale.ROOT).replace('_', '-')).sorted().toList();
      case SyntaxValue.RecordComponentDeclaration declaration -> facts.stream().map(CoverageFactReference::value)
          .filter(CoverageFactValue.Field.class::isInstance).map(CoverageFactValue.Field.class::cast).map(CoverageFactValue.Field::value)
          .filter(field -> field.fieldKey().equals(declaration.counterparts().backingFieldKey())).findFirst()
          .map(field -> field.modifiers().stream().sorted().toList()).orElse(null);
      case SyntaxValue.ParameterDeclaration declaration -> declaration.writtenModifiers().stream().map(Object::toString)
          .map(flag -> flag.toLowerCase(Locale.ROOT).replace('_', '-')).sorted().toList();
      case SyntaxValue.ReceiverParameter declaration -> declaration.writtenModifiers().stream().map(Object::toString)
          .map(flag -> flag.toLowerCase(Locale.ROOT).replace('_', '-')).sorted().toList();
      case SyntaxValue.LocalDeclaration declaration -> declaration.writtenModifiers();
      case SyntaxValue.CompilationUnit unit -> unit.form() == SyntaxValue.CompilationUnitForm.COMPACT
          ? type.filter(declaration -> !declaration.sourceDeclared())
              .map(declaration -> declaration.modifiers().stream()
                  .map(modifier -> modifier.name().toLowerCase(Locale.ROOT).replace('_', '-')).sorted().toList())
              .orElse(null)
          : List.<String>of();
      default -> null;
    };
    return flags != null && expected.equals(new CoveragePropertyValue(State.SCALAR,
        List.of(new CoveragePropertyValue.Item("MODIFIERS:0", CoveragePropertyValue.canonicalFlags(flags),
            CoveragePropertyValue.Provenance.WRITTEN, Optional.empty(), Optional.empty(), Optional.of(flags)))));
  }

  private static boolean typeSequence(CoveragePropertyValue expected, List<JavaTypeIdentity> values) {
    var items = new ArrayList<CoveragePropertyValue.Item>();
    for (var ordinal = 0; ordinal < values.size(); ordinal++) {
      var type = values.get(ordinal);
      items.add(typeItem(type, ordinal));
    }
    return expected.equals(elements(items));
  }

  private static CoveragePropertyValue.Item typeItem(JavaTypeIdentity type, int ordinal) {
    return new CoveragePropertyValue.Item(typeKind(type) + ':' + ordinal, type.displayName(),
        CoveragePropertyValue.Provenance.COMPILER_DERIVED, Optional.empty(), Optional.of(type), Optional.empty());
  }

  private static String typeKind(JavaTypeIdentity type) {
    return type.category() == JavaTypeCategory.TYPE_VARIABLE ? "TYPEVAR" : type.category().name();
  }

  private static boolean scalarType(CoveragePropertyValue expected, JavaTypeIdentity type) {
    return expected.equals(new CoveragePropertyValue(State.SCALAR, List.of(new CoveragePropertyValue.Item(typeKind(type) + ":0",
        type.displayName(), CoveragePropertyValue.Provenance.COMPILER_DERIVED, Optional.empty(), Optional.of(type), Optional.empty()))));
  }

  private static boolean scalarItem(CoveragePropertyValue expected, String value, boolean semantic) {
    return expected
        .equals(new CoveragePropertyValue(State.SCALAR, List.of(new CoveragePropertyValue.Item(value, value, provenance(!semantic)))));
  }

  private static boolean scalarText(CoveragePropertyValue expected, String value, boolean semantic) {
    return expected
        .equals(new CoveragePropertyValue(State.SCALAR, List.of(new CoveragePropertyValue.Item("item:0", value, provenance(!semantic)))));
  }

  private static boolean enclosingElement(CoveragePropertyValue expected, String source, String key, CoverageActualFactView facts) {
    var field = facts.withIdentity(source, key).stream().map(CoverageFactReference::value).filter(CoverageFactValue.Field.class::isInstance)
        .map(CoverageFactValue.Field.class::cast).map(CoverageFactValue.Field::value).filter(value -> value.fieldKey().equals(key))
        .findFirst();
    if (field.isPresent()) {
      var value = field.orElseThrow();
      return expected.equals(new CoveragePropertyValue(State.SCALAR, List.of(new CoveragePropertyValue.Item(key,
          (value.enumConstant() ? "ENUM_CONSTANT:" : "FIELD:") + value.name(), provenance(value.sourceDeclared())))));
    }
    var method = facts.withIdentity(source, key).stream().map(CoverageFactReference::value)
        .filter(CoverageFactValue.Method.class::isInstance).map(CoverageFactValue.Method.class::cast).map(CoverageFactValue.Method::value)
        .filter(value -> value.methodKey().equals(key)).findFirst();
    if (method.isPresent()) {
      var value = method.orElseThrow();
      var kind = value.constructor() ? "CONSTRUCTOR" : "METHOD";
      return expected.state() == State.SCALAR && expected.items().size() == 1 && expected.items().getFirst().identity().equals(key)
          && expected.items().getFirst().value().startsWith(kind + ':');
    }
    return facts.withIdentity(source, key).stream().map(CoverageFactReference::value).filter(CoverageFactValue.Type.class::isInstance)
        .map(CoverageFactValue.Type.class::cast).map(CoverageFactValue.Type::value).filter(type -> type.typeKey().equals(key))
        .anyMatch(type -> expected.equals(new CoveragePropertyValue(State.SCALAR, List.of(new CoveragePropertyValue.Item(key,
            (ANNOTATION_KIND.equals(type.kind().name()) ? ANNOTATION_TYPE_KIND : type.kind().name()) + ':' + type.displayName(), provenance(
                type.sourceDeclared() || facts.inSource(source).stream().anyMatch(root -> compactOwns(root, type.typeKey(), facts))))))));
  }

  private static boolean writtenPackageName(CoveragePropertyValue expected, CoverageFactReference owner,
      SyntaxValue.PackageDeclaration declaration, CoverageActualFactView facts) {
    return declaration.qualifier()
        .map(qualifier -> expected
            .equals(new CoveragePropertyValue(State.SCALAR, List.of(new CoveragePropertyValue.Item(qualifier.sourceKind().name() + ":0",
                facts.capturedText(owner, qualifier.origin()), CoveragePropertyValue.Provenance.WRITTEN)))))
        .orElse(false);
  }

  private static boolean packageOwner(CoveragePropertyValue expected, String sourceKey, CoverageActualFactView facts) {
    var packageName = facts.inSource(sourceKey).stream().flatMap(facts::syntaxValues).map(CoverageSyntaxFactValue::value)
        .filter(SyntaxValue.PackageDeclaration.class::isInstance).map(SyntaxValue.PackageDeclaration.class::cast)
        .map(SyntaxValue.PackageDeclaration::name).findFirst().orElse("");
    return expected.equals(new CoveragePropertyValue(State.SCALAR, List
        .of(new CoveragePropertyValue.Item("package:" + packageName, "PACKAGE:" + packageName, CoveragePropertyValue.Provenance.WRITTEN))));
  }

  private static boolean writtenLocalType(CoveragePropertyValue expected, CoverageFactReference owner, SyntaxValue.LocalDeclaration local,
      CoverageActualFactView facts) {
    if (expected.state() == State.ABSENT
        && local.bindingEvidence().filter(binding -> binding.typeForm() != LocalBindingEvidence.TypeForm.DECLARED).isPresent()) {
      return local.writtenType().isEmpty() && facts.children(owner, List.of(SyntaxRole.DECLARED_TYPE)).isEmpty();
    }
    if (expected.state() != State.SCALAR || local.writtenType().isEmpty()) {
      return false;
    }
    var written = local.writtenType().orElseThrow();
    var type = facts.requiredSyntax(owner).type();
    var sameType = (type instanceof SyntaxType.Resolved resolved && written.type() instanceof SyntaxType.Resolved copied)
        ? resolved.identity().category() == copied.identity().category()
            && resolved.identity().erasedName().equals(copied.identity().erasedName()) && resolved.structure().equals(copied.structure())
        : type.equals(written.type());
    var text = facts.capturedText(owner, written.origin());
    return sameType && expected.equals(new CoveragePropertyValue(State.SCALAR,
        List.of(new CoveragePropertyValue.Item(written.sourceKind().name() + ":0", text, CoveragePropertyValue.Provenance.WRITTEN))));
  }

  /**
   * Matches a written body semicolon to its exact copied occurrence and enclosing type.
   *
   * @param context
   *          independently captured source obligation
   * @param fact
   *          candidate copied syntax fact
   * @param facts
   *          complete actual source view
   * @return whether the exact source ownership and member order agree
   */
  public static boolean matchesEmptyDeclaration(SourceContext context, CoverageFactReference fact, CoverageActualFactView facts) {
    if (fact.kind() != Kind.SYNTAX || !fact.sourceFileKey().equals(context.address().sourceFileKey())
        || !fact.origin().stream().toList().equals(context.origins())) {
      return false;
    }
    var node = facts.requiredSyntax(fact);
    if (node.syntaxKind() != SyntaxKind.EMPTY || node.sourceKind() != JavaSourceKind.EMPTY_STATEMENT
        || node.sourceAssociation().filter(value -> value.ownerPath().equals(context.address().ownerPath())).isEmpty()) {
      return false;
    }
    var parentPath = context.address().ownerPath().parent().orElseThrow();
    var unitOwned = context.address().partition() == SourceContext.Partition.EMPTY_UNIT_DECLARATION;
    var parents = facts.stream().filter(parent -> facts.isSource(parent,
        syntax -> (unitOwned ? syntax.value() instanceof SyntaxValue.CompilationUnit : syntax.syntaxKind() == SyntaxKind.TYPE_DECLARATION)
            && syntax.sourceAssociation()
                .filter(value -> value.sourceFileKey().equals(fact.sourceFileKey()) && value.ownerPath().equals(parentPath)).isPresent()))
        .toList();
    var roles = unitOwned ? List.of(SyntaxRole.TYPE_DECLARATION, SyntaxRole.COMPACT_MEMBER) : List.of(SyntaxRole.MEMBER);
    return parents.size() == EXPECTED_EMPTY_DECLARATION_OWNERS && facts.children(parents.getFirst(), roles).contains(fact);
  }

  private static Optional<String> name(SyntaxValue value) {
    if (value instanceof SyntaxValue.Symbol symbol) {
      return Optional.of(symbol.name());
    }
    if (value instanceof SyntaxValue.LocalDeclaration local) {
      return Optional.of("_".equals(local.name()) ? "" : local.name());
    }
    return Optional.empty();
  }

  private static boolean matchingOwner(String role, CoverageSyntaxFactValue value) {
    if (CoverageExecutableFacts.isSurface(role)) {
      return CoverageExecutableFacts.matches(role, value);
    }
    if (role.startsWith("NewArrayTree")) {
      return value.sourceKind() == JavaSourceKind.NEW_ARRAY
          && (value.syntaxKind() == SyntaxKind.ARRAY_CREATION || value.syntaxKind() == SyntaxKind.ANNOTATION_ARRAY);
    }
    if (role.startsWith("CompilationUnitTree")) {
      return value.value() instanceof SyntaxValue.CompilationUnit;
    }
    if (role.startsWith("ClassTree") || role.startsWith(TYPE_ELEMENT_SURFACE)) {
      return value.value() instanceof SyntaxValue.TypeDeclaration
          || (value.value() instanceof SyntaxValue.CompilationUnit unit && unit.form() == SyntaxValue.CompilationUnitForm.COMPACT);
    }
    if (role.startsWith("MethodTree") || role.startsWith(EXECUTABLE_ELEMENT_SURFACE)) {
      return value.value() instanceof SyntaxValue.ExecutableDeclaration;
    }
    return value.sourceKind() == JavaSourceKind.VARIABLE;
  }

  private static boolean cardinality(CoveragePropertyValue expected, int count) {
    return expected.state() == State.EMPTY ? count == 0 : expected.state() == State.ITEMS && expected.items().size() == count;
  }

}
