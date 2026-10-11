package org.aquilagraph.source.admission;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.source.admission.AstValueProjection.Link;
import org.aquilagraph.source.admission.AstValueProjection.Node;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxValue;

/** Materializes all fields using the same closed shapes as the retained decoder. */
final class AstValueProjector {
  private final List<Node> nodes = new ArrayList<>();
  private final List<Link> links = new ArrayList<>();
  private final Function<SyntaxNode, Optional<Integer>> sourceOrdinal;
  private final BiConsumer<SyntaxNode, Integer> rememberSource;
  private final String format;
  private final int formatVersion;

  AstValueProjector(String format) {
    this.format = format;
    formatVersion = Integer.parseInt(format.substring(format.indexOf('/') + 1));
    var occurrences = new IdentityHashMap<SyntaxNode, Integer>();
    sourceOrdinal = node -> Optional.ofNullable(occurrences.get(node));
    rememberSource = occurrences::put;
  }

  AstValueProjection project(Object value) {
    append(value);
    return new AstValueProjection(nodes, links);
  }

  private int append(Object value) {
    if (value instanceof SyntaxNode source && sourceOrdinal.apply(source).isPresent()) {
      return sourceOrdinal.apply(source).orElseThrow();
    }
    var ordinal = nodes.size();
    var shape = shape(value);
    var sourceKey = value instanceof SyntaxNode source ? Optional.of(source.nodeId()) : Optional.<String>empty();
    nodes.add(new Node(ordinal, shape.tag(), shape.scalar() ? Optional.of(encoded(value)) : Optional.empty(), sourceKey));
    if (value instanceof SyntaxNode source) {
      rememberSource.accept(source, ordinal);
    }
    for (var position = 0; position < shape.components().size(); position++) {
      appendComponent(ordinal, shape.fields().get(position), position, shape.components().get(position));
    }
    return ordinal;
  }

  private void appendComponent(int owner, String field, int position, Object value) {
    var target = append(value);
    links.add(new Link(owner, target, field, position));
  }

  private Shape shape(Object value) {
    return switch (value) {
      case String _ -> scalar("text");
      case Boolean _ -> scalar("flag");
      case Integer _ -> scalar("int32");
      case Long _ -> scalar("int64");
      case Float _ -> scalar("float32");
      case Double _ -> scalar("float64");
      case Byte _ -> scalar("int8");
      case Short _ -> scalar("int16");
      case Character _ -> scalar("char16");
      case BigDecimal _ -> scalar("decimal");
      case Path _ -> scalar("path");
      case Optional<?> optional -> sequence(optional.isPresent() ? "optional/present" : "optional/absent", optional.stream().toList());
      case Set<?> set -> sequence("set", ordered(set));
      case List<?> list -> sequence("list", list);
      case SyntaxNode node -> new Shape("source-node", false,
          List.of("sourceFileKey", "structuralPath", "sourceKind", "syntaxKind", "type", "value", "children", "semanticReference",
              "origin"),
          List.of(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), node.value(),
              node.children(), node.semanticReference(), node.origin()));
      case JavaAstAggregate aggregate -> fields("source-aggregate", List.of("copiedValues"), List.of(CopiedAstValues.capture(aggregate)));
      case LanguageFeatureCoverage coverage ->
        fields("source-coverage-contract", List.of("release", "classifications"), List.of(coverage.release(), coverage.classifications()));
      case LanguageFeatureCoverageResult coverage -> fields("source-coverage-result", List.of("coverage", "validationEvidence"),
          List.of(coverage.coverage(), coverage.validationEvidence()));
      case SyntaxValue.Annotation annotation -> fields("syntax-value/annotation",
          List.of("form", "arguments", "location", "applicableTargets", "closestTypeAdmissible", "argumentFailureCode",
              "repeatableContainerKey"),
          List.of(annotation.form(), annotation.arguments(), annotation.location(), annotation.applicableTargets(),
              annotation.closestTypeAdmissible(), annotation.argumentFailureCode(), annotation.repeatableContainerKey()));
      case SyntaxValue.LocalDeclaration local when local.bindingEvidence().isEmpty() -> fields("syntax-value/local-declaration",
          List.of("name", "writtenType", "writtenModifiers"), List.of(local.name(), local.writtenType(), local.writtenModifiers()));
      default -> registered(value);
    };
  }

  private Shape registered(Object value) {
    var registered = AstValueSchema.type(value.getClass());
    return value instanceof Enum<?>
        ? scalar(registered.tag())
        : fields(registered.tag(), registered.retainedFields(formatVersion), registered.retainedComponents(value, formatVersion));
  }

  private static Shape scalar(String tag) {
    return new Shape(tag, true, List.of(), List.of());
  }

  private static Shape fields(String tag, List<String> names, List<?> components) {
    return new Shape(tag, false, names, components);
  }

  private static Shape sequence(String tag, List<?> components) {
    return fields(tag, components.stream().map(ignored -> "item").toList(), components);
  }

  private static List<?> ordered(Set<?> values) {
    var ordered = new ArrayList<>(values);
    ordered.sort((left, right) -> Arrays.compareUnsigned(bytes(left), bytes(right)));
    return ordered;
  }

  private String encoded(Object value) {
    var writer = new CanonicalValueWriter("source-value/1");
    new RetainedValueCodec(false, format).write(writer, value);
    return Base64.getEncoder().encodeToString(writer.toByteArray());
  }

  private static byte[] bytes(Object value) {
    var writer = new CanonicalValueWriter("source-value/1");
    new RetainedValueCodec(false).write(writer, value);
    return writer.toByteArray();
  }

  private record Shape(String tag, boolean scalar, List<String> fields, List<?> components) {
  }
}
