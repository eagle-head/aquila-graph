package org.aquilagraph.frontend;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.NewArrayTree;
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
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.syntax.AnnotationScalar;
import org.aquilagraph.syntax.AnnotationElementValue;
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

/** Projects written annotation values and checks available compiler attribution independently. */
final class JavaAnnotationValueProjector {
  private static final String VALUE_ELEMENT_NAME = "value";
  private static final int UNIQUE_CANDIDATE_COUNT = 1;
  private final SemanticResolver semantics;
  private final JavaSourceEvidence evidence;
  private final ParsedTreeInventory inventory;
  private final JavaTypeUseTranslator typeUses;
  private final JavaAnnotationValues values;
  private final JavaAnnotationMirrorCorrelation correlation;

  JavaAnnotationValueProjector(SemanticResolver semantics, JavaSourceEvidence evidence, ParsedTreeInventory inventory) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.evidence = Objects.requireNonNull(evidence, "evidence");
    this.inventory = Objects.requireNonNull(inventory, "inventory");
    typeUses = new JavaTypeUseTranslator(semantics, evidence, inventory);
    values = new JavaAnnotationValues(semantics);
    correlation = new JavaAnnotationMirrorCorrelation(semantics);
  }

  List<SyntaxChild> arguments(CompilationUnitTree unit, SourceFile source, AnnotationTree annotation, TypeElement annotationType,
      SyntaxStructuralPath parentPath, List<AnalysisDiagnostic> diagnostics) {
    if (inventory.annotationTypePath(annotation).map(semantics::element).filter(annotationType::equals).isEmpty()) {
      report(source, parentPath, "frontend.unavailable-annotation-attribution",
          "The written annotation does not resolve to the selected annotation type", evidence.origin(unit, source, annotation),
          diagnostics);
      return List.of();
    }
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
    var children = writtenArguments(unit, source, annotation, annotationType, parentPath, diagnostics);
    var mirror = mirror(annotation, annotationType);
    if (children.size() == written.size() && mirror.isPresent()
        && !correlation.matchesArguments(mirror.orElseThrow(), children.stream().map(SyntaxChild::node).toList())) {
      report(source, parentPath, "frontend.inconsistent-annotation-value",
          "The evaluated written arguments disagree with the compiler annotation values", evidence.origin(unit, source, annotation),
          diagnostics);
      return List.of();
    }
    return children;
  }

  private List<SyntaxChild> writtenArguments(CompilationUnitTree unit, SourceFile source, AnnotationTree annotation,
      TypeElement annotationType, SyntaxStructuralPath parentPath, List<AnalysisDiagnostic> diagnostics) {
    var written = inventory.annotationArguments(annotation).orElseThrow();
    var children = new ArrayList<SyntaxChild>();
    for (var ordinal = 0; ordinal < written.size(); ordinal++) {
      var argument = written.get(ordinal);
      var path = parentPath.child(SyntaxRole.ANNOTATION_ARGUMENT, ordinal);
      var projected = argument(unit, source, annotationType, argument, path, diagnostics);
      if (projected.isPresent()) {
        children.add(new SyntaxChild(SyntaxRole.ANNOTATION_ARGUMENT, ordinal, projected.orElseThrow()));
      }
    }
    return children.size() == written.size() ? List.copyOf(children) : List.of();
  }

  Optional<ProjectedValue> defaultValue(CompilationUnitTree unit, SourceFile source, MethodTree method, ExecutableElement element,
      SyntaxStructuralPath path, List<AnalysisDiagnostic> diagnostics) {
    var written = method.getDefaultValue();
    var attributed = element.getDefaultValue();
    if (!(written instanceof ExpressionTree expression) || attributed == null) {
      report(source, path, "frontend.unavailable-annotation-default", "The written annotation default has no matching attributed value",
          evidence.origin(unit, source, written == null ? method : written), diagnostics);
      return Optional.empty();
    }
    var treePath = TreePath.getPath(unit, expression);
    var selectedType = semantics.declarationType(element.getReturnType());
    var range = evidence.requiredRange(unit, source, expression, path, diagnostics);
    if (treePath == null || range.isEmpty() || !(selectedType instanceof SyntaxType.Resolved resolved) || resolved.structure().isEmpty()) {
      report(source, path, "frontend.unavailable-annotation-default",
          "The written annotation default has no source path, range, or structural element type", range, diagnostics);
      return Optional.empty();
    }
    var projected = value(unit, source, expression, treePath, element.getReturnType(), path, diagnostics);
    if (projected.isEmpty()) {
      return Optional.empty();
    }
    var result = projected.orElseThrow();
    if (!valueMatchesElement(result.value(), resolved.structure().orElseThrow())) {
      report(source, path, "frontend.incompatible-annotation-default",
          "The attributed annotation default conflicts with its declaring element type", range, diagnostics);
      return Optional.empty();
    }
    if (!correlation.matchesValue(attributed, result.value(), result.node())) {
      report(source, path, "frontend.inconsistent-annotation-default",
          "The evaluated written default disagrees with the compiler default value", range, diagnostics);
      return Optional.empty();
    }
    return Optional.of(result);
  }

  private Optional<AnnotationMirror> mirror(AnnotationTree annotation, TypeElement annotationType) {
    var path = inventory.annotationPath(annotation);
    if (path.isEmpty() || path.orElseThrow().getParentPath() == null) {
      return Optional.empty();
    }
    var ownerPath = path.orElseThrow().getParentPath();
    while (ownerPath != null && ownerPath.getLeaf() instanceof ModifiersTree) {
      ownerPath = ownerPath.getParentPath();
    }
    if (ownerPath == null) {
      return Optional.empty();
    }
    var owner = annotationOwner(ownerPath);
    var attributed = new ArrayList<AnnotationMirror>();
    if (owner != null && !(ownerPath.getLeaf() instanceof AnnotatedTypeTree)) {
      attributed.addAll(owner.getAnnotationMirrors());
      var annotatedType = owner instanceof ExecutableElement executable ? executable.getReturnType() : owner.asType();
      for (var mirror : closestType(annotatedType).getAnnotationMirrors()) {
        if (!attributed.contains(mirror)) {
          attributed.add(mirror);
        }
      }
      for (var mirror : attributed) {
        if (annotation.equals(semantics.annotationSourceTree(owner, mirror)) && sameAnnotationType(mirror, annotationType)) {
          return Optional.of(mirror);
        }
      }
    }
    for (var mirror : ownerPath.getLeaf() instanceof AnnotatedTypeTree
        ? semantics.typeAnnotationMirrors(ownerPath)
        : List.<AnnotationMirror>of()) {
      if (!attributed.contains(mirror)) {
        attributed.add(mirror);
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
    var direct = attributed.stream().filter(candidate -> sameAnnotationType(candidate, annotationType)).toList();
    if (sourceOrdinal < direct.size()) {
      return Optional.of(direct.get(sourceOrdinal));
    }
    var containingType = repeatableContainingType(annotationType);
    if (containingType.isEmpty()) {
      return Optional.empty();
    }
    var nested = new ArrayList<AnnotationMirror>();
    for (var container : attributed) {
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

  private static TypeMirror closestType(TypeMirror type) {
    var current = type;
    while (current instanceof ArrayType array) {
      current = array.getComponentType();
    }
    while (current instanceof DeclaredType declared && declared.getEnclosingType().getKind() != TypeKind.NONE) {
      current = declared.getEnclosingType();
    }
    return current;
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

  private Optional<SyntaxNode> argument(CompilationUnitTree unit, SourceFile source, TypeElement annotationType,
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
    var key = semantics.declarationKey(selected);
    if (key.isEmpty()) {
      report(source, path, "frontend.unavailable-annotation-attribution", "The selected annotation element has no resolved identity", range,
          diagnostics);
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
    var projected = value(unit, source, written.value(), written.valuePath(), selected.getReturnType(), path.child(SyntaxRole.VALUE, 0),
        diagnostics);
    if (projected.isEmpty()) {
      return Optional.empty();
    }
    var result = projected.orElseThrow();
    children.add(new SyntaxChild(SyntaxRole.VALUE, 0, result.node()));
    if (range.isEmpty()) {
      return Optional.empty();
    }
    if (!valueMatchesElement(result.value(), resolvedType.structure().orElseThrow())) {
      report(source, path, "frontend.incompatible-annotation-value",
          "The attributed annotation value conflicts with its selected element type", range, diagnostics);
      return Optional.empty();
    }
    var reference = new SemanticReference(ReferenceKind.METHOD, ResolutionStatus.RESOLVED, key, Optional.empty(), List.of(), List.of());
    return Optional.of(SyntaxNode.source(source.sourceFileKey(), path, JavaSourceKind.valueOf(written.argument().getKind().name()),
        SyntaxKind.ANNOTATION_ARGUMENT, selectedType, new SyntaxValue.AnnotationArgument(name, key.orElseThrow(), result.value()), children,
        Optional.of(reference), range.orElseThrow()));
  }

  private Optional<ProjectedValue> value(CompilationUnitTree unit, SourceFile source, ExpressionTree tree, TreePath treePath,
      TypeMirror expected, SyntaxStructuralPath path, List<AnalysisDiagnostic> diagnostics) {
    var range = evidence.requiredRange(unit, source, tree, path, diagnostics);
    if (range.isEmpty()) {
      return Optional.empty();
    }
    if (expected instanceof ArrayType arrayType) {
      var values = new ArrayList<AnnotationElementValue>();
      if (tree instanceof NewArrayTree array) {
        var initializers = array.getInitializers();
        if (initializers == null) {
          report(source, path, "frontend.inconsistent-annotation-array", "The written annotation array has no initializer inventory", range,
              diagnostics);
          return Optional.empty();
        }
        var children = new ArrayList<SyntaxChild>();
        for (var index = 0; index < initializers.size(); index++) {
          var childTree = initializers.get(index);
          var childPath = path.child(SyntaxRole.VALUE, index);
          var child = arrayItem(unit, source, childTree, treePath, arrayType.getComponentType(), childPath, diagnostics);
          if (child.isEmpty()) {
            return Optional.empty();
          }
          values.add(child.orElseThrow().value());
          children.add(new SyntaxChild(SyntaxRole.VALUE, index, child.orElseThrow().node()));
        }
        var node = SyntaxNode.source(source.sourceFileKey(), path, JavaSourceKind.NEW_ARRAY, SyntaxKind.ANNOTATION_ARRAY,
            SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, children, Optional.empty(), range.orElseThrow());
        return Optional.of(new ProjectedValue(new AnnotationElementValue.Array(values), node));
      }
      var child = value(unit, source, tree, treePath, arrayType.getComponentType(), path, diagnostics);
      return child.map(result -> new ProjectedValue(new AnnotationElementValue.Array(List.of(result.value())), result.node()));
    }
    if (expected instanceof DeclaredType declared && declared.asElement() instanceof TypeElement annotationType
        && annotationType.getKind() == ElementKind.ANNOTATION_TYPE) {
      if (!(tree instanceof AnnotationTree nested)) {
        report(source, path, "frontend.inconsistent-nested-annotation",
            "An attributed nested annotation has no written annotation occurrence", range, diagnostics);
        return Optional.empty();
      }
      return nested(unit, source, nested, annotationType, path, diagnostics);
    }
    var scalar = values.scalar(treePath, expected);
    if (scalar.isEmpty()) {
      report(source, path, "frontend.unavailable-annotation-attribution",
          "The written annotation value cannot be evaluated from resolved compiler types and constants", range, diagnostics);
      return Optional.empty();
    }
    return expression(unit, source, tree, treePath, path, diagnostics).map(node -> new ProjectedValue(scalar.orElseThrow(), node));
  }

  private Optional<ProjectedValue> arrayItem(CompilationUnitTree unit, SourceFile source, ExpressionTree tree, TreePath parentPath,
      TypeMirror expected, SyntaxStructuralPath path, List<AnalysisDiagnostic> diagnostics) {
    return value(unit, source, tree, new TreePath(parentPath, tree), expected, path, diagnostics);
  }

  private Optional<ProjectedValue> nested(CompilationUnitTree unit, SourceFile source, AnnotationTree tree, TypeElement annotationType,
      SyntaxStructuralPath path, List<AnalysisDiagnostic> diagnostics) {
    var parsed = inventory.annotation(tree);
    var typePath = inventory.annotationTypePath(tree);
    var range = evidence.requiredRange(unit, source, tree, path, diagnostics);
    if (parsed.isEmpty() || typePath.isEmpty() || range.isEmpty() || !annotationType.equals(semantics.element(typePath.orElseThrow()))) {
      report(source, path, "frontend.unavailable-nested-annotation",
          "The nested annotation has no matching written form and attributed type", range, diagnostics);
      return Optional.empty();
    }
    var reference = semantics.reference(typePath.orElseThrow(), ReferenceKind.TYPE);
    var key = semantics.declarationKey(annotationType);
    if (reference.isEmpty() || reference.orElseThrow().status() != ResolutionStatus.RESOLVED || key.isEmpty()) {
      report(source, path, "frontend.unresolved-nested-annotation", "The nested annotation type cannot be resolved", range, diagnostics);
      return Optional.empty();
    }
    var names = inventory.annotationArguments(tree).orElseThrow().stream()
        .map(argument -> argument.name().map(identifier -> identifier.getName().toString()).orElse(VALUE_ELEMENT_NAME)).toList();
    if (new HashSet<>(names).size() != names.size()) {
      report(source, path, "frontend.duplicate-annotation-element", "The nested annotation writes the same element more than once", range,
          diagnostics);
      return Optional.empty();
    }
    if (annotationType.getEnclosedElements().stream().filter(ExecutableElement.class::isInstance).map(ExecutableElement.class::cast)
        .anyMatch(element -> element.getDefaultValue() == null && !names.contains(element.getSimpleName().toString()))) {
      report(source, path, "frontend.incomplete-nested-annotation", "The nested annotation omits a required element value", range,
          diagnostics);
      return Optional.empty();
    }
    var written = parsed.orElseThrow();
    var children = written.arguments().isEmpty()
        ? List.<SyntaxChild>of()
        : writtenArguments(unit, source, tree, annotationType, path, diagnostics);
    if (children.size() != written.arguments().size()) {
      return Optional.empty();
    }
    var node = SyntaxNode.source(source.sourceFileKey(), path, JavaSourceKind.ANNOTATION, SyntaxKind.ANNOTATION,
        SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.Annotation(written.form(), written.arguments(), SyntaxValue.AnnotationLocation.VALUE), children, reference,
        range.orElseThrow());
    return Optional.of(new ProjectedValue(new AnnotationElementValue.Nested(key.orElseThrow()), node));
  }

  private static boolean valueMatchesElement(AnnotationElementValue value, JavaTypeStructure type) {
    return switch (value) {
      case AnnotationScalar.BooleanValue ignored ->
        type instanceof JavaTypeStructure.Primitive primitive && primitive.kind() == JavaTypeCategory.BOOLEAN;
      case AnnotationScalar.ByteValue ignored ->
        type instanceof JavaTypeStructure.Primitive primitive && primitive.kind() == JavaTypeCategory.BYTE;
      case AnnotationScalar.ShortValue ignored ->
        type instanceof JavaTypeStructure.Primitive primitive && primitive.kind() == JavaTypeCategory.SHORT;
      case AnnotationScalar.CharValue ignored ->
        type instanceof JavaTypeStructure.Primitive primitive && primitive.kind() == JavaTypeCategory.CHAR;
      case AnnotationScalar.IntValue ignored ->
        type instanceof JavaTypeStructure.Primitive primitive && primitive.kind() == JavaTypeCategory.INT;
      case AnnotationScalar.LongValue ignored ->
        type instanceof JavaTypeStructure.Primitive primitive && primitive.kind() == JavaTypeCategory.LONG;
      case AnnotationScalar.FloatValue ignored ->
        type instanceof JavaTypeStructure.Primitive primitive && primitive.kind() == JavaTypeCategory.FLOAT;
      case AnnotationScalar.DoubleValue ignored ->
        type instanceof JavaTypeStructure.Primitive primitive && primitive.kind() == JavaTypeCategory.DOUBLE;
      case AnnotationScalar.StringValue ignored ->
        type instanceof JavaTypeStructure.Declared declared && "type:java.lang.String".equals(declared.declarationKey());
      case AnnotationScalar.EnumConstant constant ->
        type instanceof JavaTypeStructure.Declared declared && declared.declarationKey().equals(constant.enumTypeKey());
      case AnnotationScalar.ClassLiteral ignored ->
        type instanceof JavaTypeStructure.Declared declared && "type:java.lang.Class".equals(declared.declarationKey());
      case AnnotationElementValue.Nested nested ->
        type instanceof JavaTypeStructure.Declared declared && declared.declarationKey().equals(nested.annotationTypeKey());
      case AnnotationElementValue.Array array ->
        type instanceof JavaTypeStructure.Array component && !(component.component() instanceof JavaTypeStructure.Array)
            && array.elements().stream().allMatch(element -> valueMatchesElement(element, component.component()));
    };
  }

  record ProjectedValue(AnnotationElementValue value, SyntaxNode node) {
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
