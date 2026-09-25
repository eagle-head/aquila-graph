package org.aquilagraph.frontend;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.DeclaredType;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.syntax.AnnotationScalar;
import org.aquilagraph.syntax.JavaTypeStructure;
import org.aquilagraph.syntax.JavaSourceKind;
import org.aquilagraph.syntax.SyntaxChild;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxOperator;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxStructuralPath;
import org.aquilagraph.syntax.SyntaxType;
import org.aquilagraph.syntax.SyntaxValue;

/** Correlates written annotation arguments with attributed scalar values. */
final class JavaAnnotationValueProjector {
  private static final String VALUE_ELEMENT_NAME = "value";
  private static final int UNIQUE_CANDIDATE_COUNT = 1;
  private final SemanticResolver semantics;
  private final JavaSourceEvidence evidence;
  private final ParsedTreeInventory inventory;
  private final JavaTypeUseTranslator typeUses;

  JavaAnnotationValueProjector(SemanticResolver semantics, JavaSourceEvidence evidence, ParsedTreeInventory inventory) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.evidence = Objects.requireNonNull(evidence, "evidence");
    this.inventory = Objects.requireNonNull(inventory, "inventory");
    typeUses = new JavaTypeUseTranslator(semantics, evidence);
  }

  List<SyntaxChild> arguments(CompilationUnitTree unit, SourceFile source, AnnotationTree annotation, TypeElement annotationType,
      SyntaxStructuralPath parentPath, List<AnalysisDiagnostic> diagnostics) {
    var captured = inventory.annotationArguments(annotation);
    if (captured.isEmpty()) {
      report(source, parentPath, "frontend.missing-annotation-argument-evidence",
          "The written argument order was not captured before attribution", evidence.origin(unit, source, annotation), diagnostics);
      return List.of();
    }
    var written = captured.orElseThrow();
    if (written.isEmpty()) {
      return List.of();
    }
    var names = new HashSet<String>();
    for (var argument : written) {
      var name = argument.name().map(identifier -> identifier.getName().toString()).orElse(VALUE_ELEMENT_NAME);
      if (!names.add(name)) {
        report(source, parentPath, "frontend.duplicate-annotation-element", "The annotation writes the same element more than once",
            evidence.origin(unit, source, argument.argument()), diagnostics);
        return List.of();
      }
    }
    var mirror = mirror(annotation, annotationType);
    if (mirror.isEmpty()) {
      report(source, parentPath, "frontend.unavailable-annotation-attribution",
          "No attributed annotation mirror matches the written occurrence", evidence.origin(unit, source, annotation), diagnostics);
      return List.of();
    }
    var children = new ArrayList<SyntaxChild>();
    for (var ordinal = 0; ordinal < written.size(); ordinal++) {
      var argument = written.get(ordinal);
      var path = parentPath.child(SyntaxRole.ANNOTATION_ARGUMENT, ordinal);
      var projected = argument(unit, source, annotationType, mirror.orElseThrow(), argument, path, diagnostics);
      if (projected.isPresent()) {
        children.add(new SyntaxChild(SyntaxRole.ANNOTATION_ARGUMENT, ordinal, projected.orElseThrow()));
      }
    }
    return children.size() == written.size() ? List.copyOf(children) : List.of();
  }

  private Optional<AnnotationMirror> mirror(AnnotationTree annotation, TypeElement annotationType) {
    var path = inventory.annotationPath(annotation);
    if (path.isEmpty() || path.orElseThrow().getParentPath() == null) {
      return Optional.empty();
    }
    var ownerPath = path.orElseThrow().getParentPath();
    while (ownerPath != null && ownerPath.getLeaf().getKind() == Tree.Kind.MODIFIERS) {
      ownerPath = ownerPath.getParentPath();
    }
    if (ownerPath == null) {
      return Optional.empty();
    }
    var owner = annotationOwner(ownerPath);
    if (owner == null) {
      return Optional.empty();
    }
    for (var mirror : owner.getAnnotationMirrors()) {
      if (annotation.equals(semantics.annotationSourceTree(owner, mirror)) && sameAnnotationType(mirror, annotationType)) {
        return Optional.of(mirror);
      }
    }
    var sourceOrdinal = 0;
    for (var candidate : inventory.ownerAnnotations(ownerPath.getLeaf())) {
      if (annotation.equals(candidate)) {
        break;
      }
      if (inventory.annotationTypePath(candidate).map(semantics::element).filter(annotationType::equals).isPresent()) {
        sourceOrdinal++;
      }
    }
    var direct = owner.getAnnotationMirrors().stream().filter(candidate -> sameAnnotationType(candidate, annotationType)).toList();
    if (direct.size() == UNIQUE_CANDIDATE_COUNT && sourceOrdinal == 0) {
      return Optional.of(direct.getFirst());
    }
    var containingType = repeatableContainingType(annotationType);
    if (containingType.isEmpty()) {
      return Optional.empty();
    }
    var nested = new ArrayList<AnnotationMirror>();
    for (var container : owner.getAnnotationMirrors()) {
      if (!sameAnnotationType(container, containingType.orElseThrow())) {
        continue;
      }
      for (var entry : container.getElementValues().entrySet()) {
        if (!entry.getKey().getSimpleName().contentEquals(VALUE_ELEMENT_NAME) || !(entry.getValue().getValue() instanceof List<?> values)) {
          continue;
        }
        for (var value : values) {
          if (value instanceof AnnotationValue annotationValue && annotationValue.getValue() instanceof AnnotationMirror candidate
              && sameAnnotationType(candidate, annotationType)) {
            nested.add(candidate);
          }
        }
      }
    }
    return sourceOrdinal < nested.size() ? Optional.of(nested.get(sourceOrdinal)) : Optional.empty();
  }

  private Element annotationOwner(TreePath ownerPath) {
    if (ownerPath.getLeaf() instanceof VariableTree variable) {
      var parent = ownerPath.getParentPath();
      while (parent != null) {
        if (semantics.element(parent) instanceof TypeElement type && type.getKind() == ElementKind.RECORD) {
          var components = type.getRecordComponents().stream()
              .filter(component -> component.getSimpleName().contentEquals(variable.getName())).filter(component -> {
                var componentTree = semantics.sourceTree(component);
                return componentTree != null && evidence.rawStart(ownerPath.getCompilationUnit(), componentTree) >= 0 && evidence
                    .rawStart(ownerPath.getCompilationUnit(), componentTree) == evidence.rawStart(ownerPath.getCompilationUnit(), variable);
              }).toList();
          if (components.size() == UNIQUE_CANDIDATE_COUNT) {
            return components.getFirst();
          }
          break;
        }
        parent = parent.getParentPath();
      }
    }
    return semantics.element(ownerPath);
  }

  private static Optional<TypeElement> repeatableContainingType(TypeElement annotationType) {
    for (var mirror : annotationType.getAnnotationMirrors()) {
      if (!(mirror.getAnnotationType().asElement() instanceof TypeElement metaType)
          || !metaType.getQualifiedName().contentEquals("java.lang.annotation.Repeatable")) {
        continue;
      }
      for (var entry : mirror.getElementValues().entrySet()) {
        if (entry.getKey().getSimpleName().contentEquals(VALUE_ELEMENT_NAME) && entry.getValue().getValue() instanceof DeclaredType declared
            && declared.asElement() instanceof TypeElement containingType) {
          return Optional.of(containingType);
        }
      }
    }
    return Optional.empty();
  }

  private static boolean sameAnnotationType(AnnotationMirror mirror, TypeElement annotationType) {
    return mirror.getAnnotationType().asElement().equals(annotationType);
  }

  private Optional<SyntaxNode> argument(CompilationUnitTree unit, SourceFile source, TypeElement annotationType, AnnotationMirror mirror,
      ParsedTreeInventory.WrittenAnnotationArgument written, SyntaxStructuralPath path, List<AnalysisDiagnostic> diagnostics) {
    var range = evidence.requiredRange(unit, source, written.argument(), path, diagnostics);
    var name = written.name().map(identifier -> identifier.getName().toString());
    var selectedName = name.orElse(VALUE_ELEMENT_NAME);
    var element = annotationType.getEnclosedElements().stream()
        .filter(candidate -> candidate instanceof ExecutableElement && candidate.getSimpleName().contentEquals(selectedName))
        .map(ExecutableElement.class::cast).findFirst();
    if (element.isEmpty()) {
      report(source, path, "frontend.unknown-annotation-element", "The written argument does not select an annotation element", range,
          diagnostics);
      return Optional.empty();
    }
    var selected = element.orElseThrow();
    var selectedType = semantics.declarationType(selected.getReturnType());
    if (!(selectedType instanceof SyntaxType.Resolved resolvedType) || resolvedType.structure().isEmpty()) {
      report(source, path, "frontend.unavailable-annotation-element-type",
          "The selected annotation element has no resolved structural type", range, diagnostics);
      return Optional.empty();
    }
    var value = mirror.getElementValues().get(selected);
    var key = semantics.declarationKey(selected);
    if (value == null || key.isEmpty()) {
      report(source, path, "frontend.unavailable-annotation-attribution",
          "The selected annotation element has no explicit attributed value or identity", range, diagnostics);
      return Optional.empty();
    }
    var children = new ArrayList<SyntaxChild>();
    if (written.name().isPresent()) {
      var namePath = path.child(SyntaxRole.ANNOTATION_ELEMENT_NAME, 0);
      var nameRange = evidence.requiredRange(unit, source, written.name().orElseThrow(), namePath, diagnostics);
      if (nameRange.isEmpty()) {
        return Optional.empty();
      }
      children.add(new SyntaxChild(SyntaxRole.ANNOTATION_ELEMENT_NAME, 0,
          SyntaxNode.source(source.sourceFileKey(), namePath, JavaSourceKind.IDENTIFIER, SyntaxKind.ANNOTATION_ELEMENT_NAME,
              SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.SourceSymbol(selectedName), List.of(), Optional.empty(),
              nameRange.orElseThrow())));
    }
    var expressionPath = path.child(SyntaxRole.VALUE, 0);
    var expression = expression(unit, source, written.value(), written.valuePath(), expressionPath, diagnostics);
    if (expression.isEmpty()) {
      return Optional.empty();
    }
    children.add(new SyntaxChild(SyntaxRole.VALUE, 0, expression.orElseThrow()));
    var scalar = scalar(value, source, path, range, diagnostics);
    if (scalar.isEmpty() || range.isEmpty()) {
      return Optional.empty();
    }
    if (!scalarMatchesElement(scalar.orElseThrow(), resolvedType)) {
      report(source, path, "frontend.incompatible-annotation-scalar",
          "The attributed annotation value conflicts with its selected element type", range, diagnostics);
      return Optional.empty();
    }
    var reference = new SemanticReference(ReferenceKind.METHOD, ResolutionStatus.RESOLVED, key, Optional.empty(), List.of(), List.of());
    return Optional.of(SyntaxNode.source(source.sourceFileKey(), path, JavaSourceKind.valueOf(written.argument().getKind().name()),
        SyntaxKind.ANNOTATION_ARGUMENT, selectedType, new SyntaxValue.AnnotationArgument(name, key.orElseThrow(), scalar.orElseThrow()),
        children, Optional.of(reference), range.orElseThrow()));
  }

  private Optional<AnnotationScalar> scalar(AnnotationValue value, SourceFile source, SyntaxStructuralPath path, Optional<SourceSpan> range,
      List<AnalysisDiagnostic> diagnostics) {
    var attributed = value.getValue();
    AnnotationScalar result = switch (attributed) {
      case Boolean number -> new AnnotationScalar.BooleanValue(number);
      case Byte number -> new AnnotationScalar.ByteValue(number);
      case Short number -> new AnnotationScalar.ShortValue(number);
      case Character character -> new AnnotationScalar.CharValue(character);
      case Integer number -> new AnnotationScalar.IntValue(number);
      case Long number -> new AnnotationScalar.LongValue(number);
      case Float number -> new AnnotationScalar.FloatValue(number);
      case Double number -> new AnnotationScalar.DoubleValue(number);
      case String string -> new AnnotationScalar.StringValue(string);
      case VariableElement constant when constant.getKind() == ElementKind.ENUM_CONSTANT -> {
        var constantKey = semantics.declarationKey(constant);
        var enumKey = semantics.declarationKey(constant.getEnclosingElement());
        yield constantKey.isPresent() && enumKey.isPresent()
            ? new AnnotationScalar.EnumConstant(constantKey.orElseThrow(), enumKey.orElseThrow())
            : null;
      }
      case TypeMirror type -> {
        var projected = semantics.declarationType(type);
        yield projected instanceof SyntaxType.Resolved resolved && resolved.structure().isPresent()
            ? new AnnotationScalar.ClassLiteral(resolved.structure().orElseThrow())
            : null;
      }
      default -> null;
    };
    if (result == null) {
      report(source, path, "frontend.unsupported-annotation-value", "The written argument has no supported attributed scalar value", range,
          diagnostics);
    }
    return Optional.ofNullable(result);
  }

  private static boolean scalarMatchesElement(AnnotationScalar scalar, SyntaxType.Resolved elementType) {
    var category = elementType.identity().category();
    return switch (scalar) {
      case AnnotationScalar.BooleanValue ignored -> category == JavaTypeCategory.BOOLEAN;
      case AnnotationScalar.ByteValue ignored -> category == JavaTypeCategory.BYTE;
      case AnnotationScalar.ShortValue ignored -> category == JavaTypeCategory.SHORT;
      case AnnotationScalar.CharValue ignored -> category == JavaTypeCategory.CHAR;
      case AnnotationScalar.IntValue ignored -> category == JavaTypeCategory.INT;
      case AnnotationScalar.LongValue ignored -> category == JavaTypeCategory.LONG;
      case AnnotationScalar.FloatValue ignored -> category == JavaTypeCategory.FLOAT;
      case AnnotationScalar.DoubleValue ignored -> category == JavaTypeCategory.DOUBLE;
      case AnnotationScalar.StringValue ignored -> "java.lang.String".equals(elementType.identity().erasedName());
      case AnnotationScalar.EnumConstant constant -> elementType.structure().orElseThrow() instanceof JavaTypeStructure.Declared declared
          && declared.declarationKey().equals(constant.enumTypeKey());
      case AnnotationScalar.ClassLiteral ignored -> "java.lang.Class".equals(elementType.identity().erasedName());
    };
  }

  private Optional<SyntaxNode> expression(CompilationUnitTree unit, SourceFile source, ExpressionTree tree, TreePath treePath,
      SyntaxStructuralPath path, List<AnalysisDiagnostic> diagnostics) {
    var range = evidence.requiredRange(unit, source, tree, path, diagnostics);
    var type = semantics.type(treePath);
    if (range.isEmpty()) {
      return Optional.empty();
    }
    if (!(type instanceof SyntaxType.Resolved resolved) || resolved.structure().isEmpty()) {
      report(source, path, "frontend.unavailable-annotation-expression-type", "The annotation expression has no resolved structural type",
          range, diagnostics);
      return Optional.empty();
    }
    var sourceKind = JavaSourceKind.valueOf(tree.getKind().name());
    var children = new ArrayList<SyntaxChild>();
    SyntaxKind kind;
    SyntaxValue value = SyntaxValue.None.INSTANCE;
    Optional<SemanticReference> reference = Optional.empty();
    var complete = true;
    switch (tree) {
      case LiteralTree literal -> {
        if (literal.getValue() == null) {
          return unsupported(source, path, range, diagnostics);
        }
        kind = SyntaxKind.LITERAL;
        var span = range.orElseThrow();
        value = new SyntaxValue.Literal(
            source.content().substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive())));
      }
      case IdentifierTree identifier -> {
        kind = SyntaxKind.ANNOTATION_NAME_REFERENCE;
        value = new SyntaxValue.SourceSymbol(identifier.getName().toString());
        reference = constantReference(treePath);
        complete = reference.isPresent();
      }
      case MemberSelectTree selection when selection.getIdentifier().contentEquals("class") -> {
        kind = SyntaxKind.ANNOTATION_CLASS_LITERAL;
        complete = appendType(unit, source, selection.getExpression(), treePath, path, SyntaxRole.TYPE_OPERAND, children, diagnostics);
      }
      case MemberSelectTree selection -> {
        kind = SyntaxKind.ANNOTATION_NAME_REFERENCE;
        value = new SyntaxValue.SourceSymbol(selection.getIdentifier().toString());
        reference = constantReference(treePath);
        complete = reference.isPresent()
            && appendType(unit, source, selection.getExpression(), treePath, path, SyntaxRole.QUALIFIER, children, diagnostics);
      }
      case ParenthesizedTree parentheses -> {
        kind = SyntaxKind.PARENTHESIZED;
        complete = appendExpression(unit, source, parentheses.getExpression(), treePath, path, SyntaxRole.EXPRESSION, children,
            diagnostics);
      }
      case TypeCastTree cast -> {
        kind = SyntaxKind.ANNOTATION_CAST;
        complete = appendType(unit, source, cast.getType(), treePath, path, SyntaxRole.TYPE_OPERAND, children, diagnostics);
        complete &= appendExpression(unit, source, cast.getExpression(), treePath, path, SyntaxRole.EXPRESSION, children, diagnostics);
      }
      case UnaryTree unary when unaryOperator(unary.getKind()) -> {
        kind = SyntaxKind.UNARY;
        value = new SyntaxValue.Operator(SyntaxOperator.valueOf(unary.getKind().name()));
        complete = appendExpression(unit, source, unary.getExpression(), treePath, path, SyntaxRole.OPERAND, children, diagnostics);
      }
      case BinaryTree binary when binaryOperator(binary.getKind()) -> {
        kind = switch (binary.getKind()) {
          case CONDITIONAL_AND -> SyntaxKind.CONDITIONAL_AND;
          case CONDITIONAL_OR -> SyntaxKind.CONDITIONAL_OR;
          default -> SyntaxKind.BINARY;
        };
        value = new SyntaxValue.Operator(SyntaxOperator.valueOf(binary.getKind().name()));
        complete = appendExpression(unit, source, binary.getLeftOperand(), treePath, path, SyntaxRole.LEFT_OPERAND, children, diagnostics);
        complete &= appendExpression(unit, source, binary.getRightOperand(), treePath, path, SyntaxRole.RIGHT_OPERAND, children,
            diagnostics);
      }
      case ConditionalExpressionTree conditional -> {
        kind = SyntaxKind.CONDITIONAL_EXPRESSION;
        complete = appendExpression(unit, source, conditional.getCondition(), treePath, path, SyntaxRole.CONDITION, children, diagnostics);
        complete &= appendExpression(unit, source, conditional.getTrueExpression(), treePath, path, SyntaxRole.TRUE_EXPRESSION, children,
            diagnostics);
        complete &= appendExpression(unit, source, conditional.getFalseExpression(), treePath, path, SyntaxRole.FALSE_EXPRESSION, children,
            diagnostics);
      }
      default -> {
        return unsupported(source, path, range, diagnostics);
      }
    }
    if (!complete) {
      if (reference.isEmpty() && kind == SyntaxKind.ANNOTATION_NAME_REFERENCE) {
        report(source, path, "frontend.nonconstant-annotation-name",
            "The annotation name does not resolve to a constant variable or enum constant", range, diagnostics);
      }
      return Optional.empty();
    }
    return Optional
        .of(SyntaxNode.source(source.sourceFileKey(), path, sourceKind, kind, type, value, children, reference, range.orElseThrow()));
  }

  private Optional<SemanticReference> constantReference(TreePath path) {
    var element = semantics.element(path);
    if (!(element instanceof VariableElement variable)) {
      return Optional.empty();
    }
    var kind = switch (variable.getKind()) {
      case ENUM_CONSTANT -> ReferenceKind.ENUM_CONSTANT;
      case FIELD -> ReferenceKind.FIELD;
      case LOCAL_VARIABLE -> ReferenceKind.LOCAL;
      default -> null;
    };
    if (kind == null || (kind != ReferenceKind.ENUM_CONSTANT && variable.getConstantValue() == null)) {
      return Optional.empty();
    }
    return semantics.reference(path, kind).filter(reference -> reference.status() == ResolutionStatus.RESOLVED);
  }

  private boolean appendExpression(CompilationUnitTree unit, SourceFile source, ExpressionTree tree, TreePath parent,
      SyntaxStructuralPath parentPath, SyntaxRole role, List<SyntaxChild> children, List<AnalysisDiagnostic> diagnostics) {
    var path = parentPath.child(role, 0);
    var result = expression(unit, source, tree, new TreePath(parent, tree), path, diagnostics);
    result.ifPresent(node -> children.add(new SyntaxChild(role, 0, node)));
    return result.isPresent();
  }

  private boolean appendType(CompilationUnitTree unit, SourceFile source, Tree tree, TreePath parent, SyntaxStructuralPath parentPath,
      SyntaxRole role, List<SyntaxChild> children, List<AnalysisDiagnostic> diagnostics) {
    var path = parentPath.child(role, 0);
    var treePath = new TreePath(parent, tree);
    var result = typeUses.translateAttributed(unit, source, tree, treePath, path, path, semantics.explicitType(treePath), diagnostics);
    result.ifPresent(node -> children.add(new SyntaxChild(role, 0, node)));
    return result.isPresent();
  }

  private static boolean unaryOperator(Tree.Kind kind) {
    return switch (kind) {
      case UNARY_PLUS, UNARY_MINUS, BITWISE_COMPLEMENT, LOGICAL_COMPLEMENT -> true;
      default -> false;
    };
  }

  private static boolean binaryOperator(Tree.Kind kind) {
    return switch (kind) {
      case MULTIPLY, DIVIDE, REMAINDER, PLUS, MINUS, LEFT_SHIFT, RIGHT_SHIFT, UNSIGNED_RIGHT_SHIFT, LESS_THAN, GREATER_THAN,
          LESS_THAN_EQUAL, GREATER_THAN_EQUAL, EQUAL_TO, NOT_EQUAL_TO, AND, XOR, OR, CONDITIONAL_AND, CONDITIONAL_OR ->
        true;
      default -> false;
    };
  }

  private static Optional<SyntaxNode> unsupported(SourceFile source, SyntaxStructuralPath path, Optional<SourceSpan> range,
      List<AnalysisDiagnostic> diagnostics) {
    report(source, path, "frontend.nonconstant-annotation-expression",
        "The written annotation expression is outside the Java constant-expression profile", range, diagnostics);
    return Optional.empty();
  }

  private static void report(SourceFile source, SyntaxStructuralPath path, String code, String message, Optional<SourceSpan> range,
      List<AnalysisDiagnostic> diagnostics) {
    diagnostics.add(FrontendDiagnosticFactory.capabilityError(source.sourceFileKey() + ':' + path, code, message, range));
  }
}
