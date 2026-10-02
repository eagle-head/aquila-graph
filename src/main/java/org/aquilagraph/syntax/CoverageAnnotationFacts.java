package org.aquilagraph.syntax;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.model.CoverageAnnotationValue;
import org.aquilagraph.model.CoverageAnnotationValue.Scalar;
import org.aquilagraph.model.CoverageAnnotationValue.TypeOperand;
import org.aquilagraph.model.CoveragePropertyValue;
import org.aquilagraph.model.JavaTypeCategory;

/** Compares typed public annotation projections with actual retained source values. */
final class CoverageAnnotationFacts {
  private static final int SINGLE_VALUE_COUNT = 1;
  private CoverageAnnotationFacts() {
  }

  static boolean mirrors(CoveragePropertyValue expected, List<CoverageSyntaxFactValue> annotations, CoverageActualFactView facts) {
    var source = annotations.stream()
        .filter(value -> value.value() instanceof SyntaxValue.Annotation annotation
            && (annotation.location() == SyntaxValue.AnnotationLocation.DECLARATION
                || annotation.location() == SyntaxValue.AnnotationLocation.DUAL))
        .toList();
    if (expected.state() == CoveragePropertyValue.State.EMPTY) {
      return source.isEmpty();
    }
    if (expected.state() != CoveragePropertyValue.State.ITEMS) {
      return false;
    }
    var expanded = new ArrayList<CoverageAnnotationValue.Annotation>();
    for (var ordinal = 0; ordinal < expected.items().size(); ordinal++) {
      var item = expected.items().get(ordinal);
      if (!validCompilerItem(item, ordinal)
          || !(item.annotationValue().orElse(null) instanceof CoverageAnnotationValue.Annotation annotation)) {
        return false;
      }
      if (annotation.provenance() == CoveragePropertyValue.Provenance.WRITTEN) {
        expanded.add(annotation);
      } else {
        if (annotation.explicitValues().size() != SINGLE_VALUE_COUNT
            || !(annotation.explicitValues().getFirst().value() instanceof CoverageAnnotationValue.Array array)
            || array.elements().isEmpty()) {
          return false;
        }
        for (var nested : array.elements()) {
          if (!(nested instanceof CoverageAnnotationValue.Annotation written) || source.stream()
              .noneMatch(value -> value.value() instanceof SyntaxValue.Annotation actual
                  && actual.repeatableContainerKey().filter(annotation.typeKey()::equals).isPresent()
                  && value.semanticReference().flatMap(reference -> reference.symbolKey()).filter(written.typeKey()::equals).isPresent())) {
            return false;
          }
          expanded.add(written);
        }
      }
    }
    if (expanded.size() != source.size()) {
      return false;
    }
    for (var ordinal = 0; ordinal < expanded.size(); ordinal++) {
      if (!annotation(source.get(ordinal), facts).filter(expanded.get(ordinal)::equals).isPresent()) {
        return false;
      }
    }
    return true;
  }

  static boolean defaultValue(CoveragePropertyValue expected, SyntaxValue.ExecutableDeclaration declaration,
      List<CoverageSyntaxFactValue> expressions, CoverageActualFactView facts) {
    if (expected.state() == CoveragePropertyValue.State.ABSENT) {
      return declaration.defaultValue().isEmpty() && expressions.isEmpty();
    }
    return expected.state() == CoveragePropertyValue.State.SCALAR && expressions.size() == SINGLE_VALUE_COUNT
        && validCompilerItem(expected.items().getFirst(), 0) && expected.items().getFirst().annotationValue().isPresent()
        && declaration.defaultValue().flatMap(value -> value(value, expressions.getFirst(), facts))
            .equals(expected.items().getFirst().annotationValue());
  }

  private static boolean validCompilerItem(CoveragePropertyValue.Item item, int ordinal) {
    return item.identity().equals("item:" + ordinal) && item.provenance() == CoveragePropertyValue.Provenance.COMPILER_DERIVED
        && item.executableType().isEmpty() && item.typeIdentity().isEmpty() && item.modifierFlags().isEmpty()
        && item.typeStructure().isEmpty()
        && item.annotationValue().map(CoverageAnnotationValue::canonicalText).filter(item.value()::equals).isPresent();
  }

  private static Optional<CoverageAnnotationValue.Annotation> annotation(CoverageSyntaxFactValue expression, CoverageActualFactView facts) {
    if (!(expression.value() instanceof SyntaxValue.Annotation annotation) || annotation.argumentFailureCode().isPresent()) {
      return Optional.empty();
    }
    var key = expression.semanticReference().flatMap(reference -> reference.symbolKey());
    if (key.isEmpty()) {
      return Optional.empty();
    }
    var entries = new ArrayList<CoverageAnnotationValue.Entry>();
    for (var argument : children(expression, SyntaxRole.ANNOTATION_ARGUMENT, facts)) {
      if (!(argument.value() instanceof SyntaxValue.AnnotationArgument pair)) {
        return Optional.empty();
      }
      var values = children(argument, SyntaxRole.VALUE, facts);
      if (values.size() != SINGLE_VALUE_COUNT) {
        return Optional.empty();
      }
      var copied = value(pair.value(), values.getFirst(), facts);
      if (copied.isEmpty()) {
        return Optional.empty();
      }
      entries.add(new CoverageAnnotationValue.Entry(pair.selectedElementKey(), copied.orElseThrow()));
    }
    return Optional.of(new CoverageAnnotationValue.Annotation(key.orElseThrow(), entries, CoveragePropertyValue.Provenance.WRITTEN));
  }

  private static Optional<CoverageAnnotationValue> value(AnnotationElementValue value, CoverageSyntaxFactValue expression,
      CoverageActualFactView facts) {
    return switch (value) {
      case AnnotationScalar.BooleanValue scalar -> Optional.of(new Scalar(Scalar.Kind.BOOLEAN, Boolean.toString(scalar.value())));
      case AnnotationScalar.ByteValue scalar -> Optional.of(new Scalar(Scalar.Kind.BYTE, Byte.toString(scalar.value())));
      case AnnotationScalar.ShortValue scalar -> Optional.of(new Scalar(Scalar.Kind.SHORT, Short.toString(scalar.value())));
      case AnnotationScalar.CharValue scalar -> Optional.of(new Scalar(Scalar.Kind.CHAR, Integer.toString(scalar.value())));
      case AnnotationScalar.IntValue scalar -> Optional.of(new Scalar(Scalar.Kind.INT, Integer.toString(scalar.value())));
      case AnnotationScalar.LongValue scalar -> Optional.of(new Scalar(Scalar.Kind.LONG, Long.toString(scalar.value())));
      case AnnotationScalar.FloatValue scalar -> Optional.of(new Scalar(Scalar.Kind.FLOAT, Float.toString(scalar.value())));
      case AnnotationScalar.DoubleValue scalar -> Optional.of(new Scalar(Scalar.Kind.DOUBLE, Double.toString(scalar.value())));
      case AnnotationScalar.StringValue scalar -> Optional.of(new Scalar(Scalar.Kind.STRING, scalar.value()));
      case AnnotationScalar.EnumConstant scalar ->
        Optional.of(new CoverageAnnotationValue.EnumConstant(scalar.declarationKey(), scalar.enumTypeKey()));
      case AnnotationScalar.ClassLiteral scalar -> Optional.of(new CoverageAnnotationValue.ClassLiteral(operand(scalar.operandType())));
      case AnnotationElementValue.Nested nested -> annotation(expression, facts)
          .filter(annotation -> annotation.typeKey().equals(nested.annotationTypeKey())).map(CoverageAnnotationValue.class::cast);
      case AnnotationElementValue.Array array -> {
        var expressions = expression.syntaxKind() == SyntaxKind.ANNOTATION_ARRAY
            ? children(expression, SyntaxRole.VALUE, facts)
            : List.of(expression);
        if (expressions.size() != array.elements().size()) {
          yield Optional.empty();
        }
        var components = new ArrayList<CoverageAnnotationValue>();
        for (var ordinal = 0; ordinal < expressions.size(); ordinal++) {
          var copied = value(array.elements().get(ordinal), expressions.get(ordinal), facts);
          if (copied.isEmpty()) {
            yield Optional.empty();
          }
          components.add(copied.orElseThrow());
        }
        yield Optional.of(new CoverageAnnotationValue.Array(components));
      }
    };
  }

  private static TypeOperand operand(JavaTypeStructure value) {
    return switch (value) {
      case JavaTypeStructure.Primitive primitive ->
        new TypeOperand(primitive.kind(), Optional.empty(), Optional.empty(), Optional.empty(), List.of());
      case JavaTypeStructure.Array array ->
        new TypeOperand(JavaTypeCategory.ARRAY, Optional.empty(), Optional.empty(), Optional.empty(), List.of(operand(array.component())));
      case JavaTypeStructure.Declared declared -> new TypeOperand(JavaTypeCategory.DECLARED, Optional.of(declared.declarationKey()),
          declared.lexicalOwnerKey(), declared.enclosingType().map(CoverageAnnotationFacts::operand),
          declared.arguments().stream().map(CoverageAnnotationFacts::operand).toList());
      case JavaTypeStructure.LocalDeclared declared -> new TypeOperand(JavaTypeCategory.DECLARED, Optional.of(declared.declarationKey()),
          Optional.of(declared.lexicalOwnerKey()), declared.enclosingType().map(CoverageAnnotationFacts::operand),
          declared.arguments().stream().map(CoverageAnnotationFacts::operand).toList());
      default -> throw new IllegalArgumentException("An annotation class literal must retain an admissible operand type");
    };
  }

  private static List<CoverageSyntaxFactValue> children(CoverageSyntaxFactValue owner, SyntaxRole role, CoverageActualFactView facts) {
    return owner.children().stream().filter(child -> child.role() == role).map(child -> facts.stream()
        .filter(fact -> fact.identity().equals(child.nodeKey())).flatMap(facts::syntaxValues).findFirst().orElseThrow()).toList();
  }
}
