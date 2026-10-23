package org.aquilagraph.frontend.internal;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.SourceVersion;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import org.aquilagraph.frontend.internal.JavaAnnotationProjector.DeclarationSite;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.JavaRecordComponent;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.AnnotationElementValue;
import org.aquilagraph.source.api.JavaDeclarationModifier;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;

import java.util.function.Function;

/** Projects qualified written headers without owning bodies or lexical publication. */
final class JavaDeclarationHeaderProjector {
  private static final String DIAGNOSTIC_PRODUCER = "JavaAstContributionTranslator";
  private static final String RECEIVER_NAME = "this";
  private static final int REQUIRED_COMPONENT_SOURCE_COUNT = 1;
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory treeInventory;
  private final JavaTranslationPolicy policy;
  private final RecordAnnotationMirrorCorrelation recordMirrorCorrelation;
  private final JavaWrittenSourceAssociations sourceAssociations;
  private final Function<String, JavaSharedAnnotations> fieldAnnotations;
  private final JavaAnnotationProjector annotationProjector;
  private final JavaTypeUseTranslator typeUseTranslator;

  JavaDeclarationHeaderProjector(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory,
      RecordAnnotationMirrorCorrelation recordMirrorCorrelation, JavaTranslationPolicy policy,
      JavaWrittenSourceAssociations sourceAssociations, Function<String, JavaSharedAnnotations> fieldAnnotations) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.treeInventory = Objects.requireNonNull(treeInventory, "treeInventory");
    this.recordMirrorCorrelation = Objects.requireNonNull(recordMirrorCorrelation, "recordMirrorCorrelation");
    this.policy = Objects.requireNonNull(policy, "policy");
    this.sourceAssociations = Objects.requireNonNull(sourceAssociations, "sourceAssociations");
    this.fieldAnnotations = Objects.requireNonNull(fieldAnnotations, "fieldAnnotations");
    annotationProjector = new JavaAnnotationProjector(semantics, sourceEvidence, treeInventory, sourceAssociations);
    typeUseTranslator = new JavaTypeUseTranslator(semantics, sourceEvidence, treeInventory, Optional.empty(), sourceAssociations);
  }

  JavaDeclarationHeaderProjector child(JavaWrittenSourceAssociations associations, Function<String, JavaSharedAnnotations> annotations) {
    return new JavaDeclarationHeaderProjector(semantics, sourceEvidence, treeInventory, recordMirrorCorrelation, policy, associations,
        annotations);
  }

  record Contributions(List<SyntaxChild> children, List<JavaDeclarationReference> references, boolean complete) {
    Contributions {
      children = List.copyOf(children);
      references = List.copyOf(references);
    }
  }
  record TypeIdentity(TypeElement element, String name, boolean anonymous, SourceSpan range) {
  }
  record TypeHeader(TypeIdentity identity, Contributions contributions, SyntaxValue.TypeDeclaration.Shape shape) {
  }
  record ExecutableIdentity(ExecutableElement element, String ownerKey, String key, String name,
      SyntaxValue.ExecutableDeclaration.ExecutableKind kind, boolean constructor, boolean compactConstructor, SourceSpan range) {
  }
  record AttributedSignature(SyntaxType receiverType, Optional<String> receiverOwnerKey, Optional<String> receiverSimpleName,
      Optional<String> enclosingTypeKey, List<SyntaxType> thrownTypes) {
    AttributedSignature {
      thrownTypes = List.copyOf(thrownTypes);
    }
  }
  record ExecutableHeader(ExecutableIdentity identity, Contributions contributions, SyntaxValue.ExecutableDeclaration.HeaderShape shape,
      AttributedSignature attributed, Set<JavaDeclarationModifier> writtenModifiers, Set<JavaDeclarationModifier> effectiveModifiers) {
    ExecutableHeader {
      writtenModifiers = Set.copyOf(writtenModifiers);
      effectiveModifiers = Set.copyOf(effectiveModifiers);
    }
  }
  record DefaultValue(SyntaxNode node, AnnotationElementValue value) {
  }
  record DefaultCompletion(Optional<DefaultValue> value, boolean complete) {
  }
  record FieldHeader(SyntaxValue.VariableDeclaration value, List<SyntaxChild> children, SourceSpan range) {
    FieldHeader {
      children = List.copyOf(children);
    }
  }
  private Optional<SyntaxNode> annotationAttachment(CompilationUnitTree unit, SourceFile sourceFile, AnnotationTree annotation,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, DeclarationSite site, DiagnosticEmissionCollector diagnostics) {
    return annotationProjector.declaration(unit, sourceFile, annotation, path, occurrencePath, site, diagnostics).value();
  }

  private static Optional<String> receiverName(Tree tree) {
    return switch (tree) {
      case IdentifierTree identifier when identifier.getName().contentEquals(RECEIVER_NAME) -> Optional.of(RECEIVER_NAME);
      case MemberSelectTree selection when selection.getIdentifier().contentEquals(RECEIVER_NAME)
          && selection.getExpression() instanceof IdentifierTree identifier
          && SourceVersion.isIdentifier(identifier.getName().toString()) ->
        Optional.of(identifier.getName() + "." + RECEIVER_NAME);
      default -> Optional.empty();
    };
  }

  Optional<TypeIdentity> identifyType(CompilationUnitTree unit, SourceFile sourceFile, TreePath classPath, ClassTree declaration,
      SyntaxStructuralPath occurrencePath, DiagnosticEmissionCollector diagnostics) {
    if (!treeInventory.isWritten(declaration)) {
      return Optional.empty();
    }
    var range = requiredRange(unit, sourceFile, declaration, occurrencePath, diagnostics);
    if (range.isEmpty()) {
      return Optional.empty();
    }
    var element = semantics.element(classPath);
    if (!(element instanceof TypeElement typeElement) || semantics.declarationKey(typeElement).isEmpty()
        || !typeElement.getKind().name().equals(declaration.getKind().name()) || semantics.isImplicitDeclaration(classPath)) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.unavailable-source-type-declaration",
              "A written type has no matching explicit declaration element and stable key", range));
      return Optional.empty();
    }
    var anonymous = typeElement.getNestingKind() == NestingKind.ANONYMOUS;
    var name = declaration.getSimpleName().toString();
    if (!SourceVersion.isIdentifier(name)
        && !(policy.supportsNestedDeclarations() && name.isEmpty() && classPath.getParentPath().getLeaf() instanceof NewClassTree)) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.missing-type-name",
          "A written type declaration has no valid simple name", range));
      return Optional.empty();
    }
    return Optional.of(new TypeIdentity(typeElement, name, anonymous, range.orElseThrow()));
  }
  TypeHeader typeHeader(CompilationUnitTree unit, SourceFile sourceFile, TreePath classPath, ClassTree declaration,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, TypeIdentity identity, DiagnosticEmissionCollector diagnostics,
      Map<String, JavaType> typesByKey) {
    var typeElement = identity.element();
    var anonymous = identity.anonymous();
    var range = Optional.of(identity.range());
    var children = new ArrayList<SyntaxChild>();
    var stagedEdges = new ArrayList<JavaDeclarationReference>();
    var complete = true;
    var annotations = declaration.getModifiers().getAnnotations();
    for (var ordinal = 0; ordinal < annotations.size(); ordinal++) {
      var position = ordinal;
      var role = SyntaxRole.ANNOTATION;
      var annotation = annotations.get(ordinal);
      var attachment = annotationAttachment(unit, sourceFile, annotation, path.child(role, position), occurrencePath.child(role, ordinal),
          declaration.getKind() == Tree.Kind.ANNOTATION_TYPE ? DeclarationSite.ANNOTATION_TYPE : DeclarationSite.TYPE, diagnostics);
      attachment.ifPresent(node -> children.add(new SyntaxChild(role, position, node)));
      complete &= attachment.isPresent();
    }
    for (var ordinal = 0; ordinal < declaration.getTypeParameters().size(); ordinal++) {
      var parameter = declaration.getTypeParameters().get(ordinal);
      var parameterPath = path.child(SyntaxRole.TYPE_PARAMETER, ordinal);
      var parameterOccurrencePath = occurrencePath.child(SyntaxRole.TYPE_PARAMETER, ordinal);
      var translated = typeParameter(unit, sourceFile, TreePath.getPath(classPath, parameter), parameter, parameterPath,
          parameterOccurrencePath, semantics.declarationKey(typeElement).orElseThrow(), ordinal, stagedEdges, diagnostics);
      if (translated.isPresent()) {
        children.add(new SyntaxChild(SyntaxRole.TYPE_PARAMETER, ordinal, translated.orElseThrow()));
      }
      complete &= translated.isPresent();
    }
    if (!anonymous && declaration.getExtendsClause() != null) {
      complete &= appendTypeHeaderAttachment(unit, sourceFile, declaration.getExtendsClause(), path, occurrencePath, SyntaxRole.SUPERCLASS,
          0, typeElement.getSuperclass(), children, diagnostics);
    }
    if ((!declaration.getImplementsClause().isEmpty() && declaration.getImplementsClause().size() != typeElement.getInterfaces().size())
        || (!declaration.getPermitsClause().isEmpty()
            && declaration.getPermitsClause().size() != typeElement.getPermittedSubclasses().size())) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.inconsistent-type-header", "Written hierarchy attachments do not match compiler-attributed declaration order", range));
      complete = false;
    }
    for (var ordinal = 0; !anonymous && ordinal < declaration.getImplementsClause().size(); ordinal++) {
      if (ordinal < typeElement.getInterfaces().size()) {
        complete &= appendTypeHeaderAttachment(unit, sourceFile, declaration.getImplementsClause().get(ordinal), path, occurrencePath,
            SyntaxRole.INTERFACE, ordinal, typeElement.getInterfaces().get(ordinal), children, diagnostics);
      }
    }
    for (var ordinal = 0; ordinal < declaration.getPermitsClause().size(); ordinal++) {
      if (ordinal < typeElement.getPermittedSubclasses().size()) {
        complete &= appendTypeHeaderAttachment(unit, sourceFile, declaration.getPermitsClause().get(ordinal), path, occurrencePath,
            SyntaxRole.PERMITTED_SUBCLASS, ordinal, typeElement.getPermittedSubclasses().get(ordinal), children, diagnostics);
      }
    }
    var modeledType = typesByKey.get(semantics.declarationKey(typeElement).orElseThrow());
    if (modeledType == null || modeledType.recordComponents().size() != typeElement.getRecordComponents().size()) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.unavailable-record-header", "A written type has no complete correlated record-component inventory", range));
      complete = false;
    } else {
      for (var component : modeledType.recordComponents()) {
        var attributedComponent = typeElement.getRecordComponents().get(component.ordinal());
        var componentNode = recordComponent(unit, sourceFile, declaration, typeElement, component, attributedComponent.asType(), path,
            occurrencePath, stagedEdges, diagnostics);
        componentNode.ifPresent(node -> children.add(new SyntaxChild(SyntaxRole.RECORD_COMPONENT, component.ordinal(), node)));
        complete &= componentNode.isPresent();
      }
    }
    var shape = new SyntaxValue.TypeDeclaration.Shape(annotations.size(), declaration.getTypeParameters().size(),
        !anonymous && declaration.getExtendsClause() != null, anonymous ? 0 : declaration.getImplementsClause().size(),
        declaration.getPermitsClause().size(), modeledType == null ? 0 : modeledType.recordComponents().size(), 0);
    return new TypeHeader(identity, new Contributions(children, stagedEdges, complete), shape);
  }

  SyntaxValue.TypeDeclaration completeType(ClassTree declaration, TypeHeader header, int memberCount) {
    var shape = header.shape();
    return new SyntaxValue.TypeDeclaration(header.identity().name(), semantics.declarationKey(header.identity().element()).orElseThrow(),
        JavaTypeTranslator.typeModifiers(treeInventory.writtenModifiers(declaration.getModifiers())),
        JavaTypeTranslator.typeModifiers(header.identity().element().getModifiers()),
        new SyntaxValue.TypeDeclaration.Shape(shape.annotations(), shape.typeParameters(), shape.superclass(), shape.interfaces(),
            shape.permittedSubclasses(), shape.recordComponents(), memberCount));
  }
  private boolean appendTypeHeaderAttachment(CompilationUnitTree unit, SourceFile sourceFile, Tree tree, SyntaxStructuralPath parentPath,
      SyntaxStructuralPath parentOccurrencePath, SyntaxRole role, int ordinal, TypeMirror declarationType, List<SyntaxChild> children,
      DiagnosticEmissionCollector diagnostics) {
    var path = parentPath.child(role, ordinal);
    var attributed = semantics.declarationType(declarationType);
    var node = role == SyntaxRole.DECLARED_TYPE || role == SyntaxRole.RETURN_TYPE
        ? typeUseTranslator
            .translateDeclaration(unit, sourceFile, tree, path, parentOccurrencePath.child(role, ordinal), attributed, diagnostics).value()
        : typeUseTranslator.translate(unit, sourceFile, tree, path, parentOccurrencePath.child(role, ordinal), diagnostics).value();
    if (node.isEmpty()) {
      return false;
    }
    var written = node.orElseThrow().type();
    if (written instanceof SyntaxType.Resolved writtenType
        && (!(attributed instanceof SyntaxType.Resolved declaredType) || !matchingDeclarationType(role, writtenType, declaredType))) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.inconsistent-declaration-type",
              "A written type attachment conflicts with its compiler-attributed declaration type", node.orElseThrow().origin()));
      return false;
    }
    children.add(new SyntaxChild(role, ordinal, node.orElseThrow()));
    return true;
  }

  private static boolean matchingDeclarationType(SyntaxRole role, SyntaxType.Resolved written, SyntaxType.Resolved attributed) {
    if (role == SyntaxRole.PERMITTED_SUBCLASS && written.structure().orElseThrow() instanceof JavaTypeStructure.Declared source
        && attributed.structure().orElseThrow() instanceof JavaTypeStructure.Declared selected) {
      return source.declarationKey().equals(selected.declarationKey());
    }
    return written.structure().equals(attributed.structure());
  }

  private Optional<SyntaxNode> recordComponent(CompilationUnitTree unit, SourceFile sourceFile, ClassTree declaration,
      TypeElement typeElement, JavaRecordComponent component, TypeMirror attributedType, SyntaxStructuralPath typePath,
      SyntaxStructuralPath typeOccurrencePath, List<JavaDeclarationReference> semanticEdges, DiagnosticEmissionCollector diagnostics) {
    var candidates = declaration.getMembers().stream().filter(VariableTree.class::isInstance).map(VariableTree.class::cast)
        .filter(tree -> treeInventory.isWritten(tree) && tree.getName().contentEquals(component.name())
            && sourceEvidence.origin(unit, sourceFile, tree).filter(component.origin()::equals).isPresent())
        .toList();
    var path = typePath.child(SyntaxRole.RECORD_COMPONENT, component.ordinal());
    var occurrencePath = typeOccurrencePath.child(SyntaxRole.RECORD_COMPONENT, component.ordinal());
    if (candidates.size() != REQUIRED_COMPONENT_SOURCE_COUNT) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(component.componentKey(), "frontend.unavailable-record-component-source",
              "A record component has no unique written source occurrence", Optional.of(component.origin())));
      return Optional.empty();
    }
    var tree = candidates.getFirst();
    var annotations = treeInventory.variableAnnotations(tree);
    if (annotations.isEmpty()) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(component.componentKey(), "frontend.missing-record-component-annotations",
              "A written record component has no pre-attribution annotation inventory", Optional.of(component.origin())));
      return Optional.empty();
    }
    var children = new ArrayList<SyntaxChild>();
    var complete = appendAnnotations(unit, sourceFile, annotations.orElseThrow(), path, occurrencePath, DeclarationSite.RECORD_COMPONENT,
        children, diagnostics);
    complete &= appendTypeHeaderAttachment(unit, sourceFile, tree.getType(), path, occurrencePath, SyntaxRole.DECLARED_TYPE, 0,
        attributedType, children, diagnostics);
    if (!complete) {
      return Optional.empty();
    }
    var effectiveModifiers = component.effectiveModifiers().stream().map(modifier -> JavaDeclarationModifier.valueOf(modifier.name()))
        .collect(Collectors.toUnmodifiableSet());
    var counterparts = recordCounterparts(typeElement, component);
    if (counterparts.isEmpty()) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(component.componentKey(), "frontend.unavailable-record-counterpart",
              "A record component has no uniquely correlated accessor or canonical parameter provenance", Optional.of(component.origin())));
      return Optional.empty();
    }
    if (!recordAnnotationMirrors(children, counterparts.orElseThrow())) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(component.componentKey(), "frontend.unavailable-record-annotation-propagation",
              "A record component annotation has no matching compiler-propagated counterpart mirror", Optional.of(component.origin())));
      return Optional.empty();
    }
    var value = new SyntaxValue.RecordComponentDeclaration(component.name(), component.ownerTypeKey(), component.ordinal(), Set.of(),
        effectiveModifiers, component.staticType(), annotations.orElseThrow().size(), component.varArgs(),
        counterparts.orElseThrow().counterparts());
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.VARIABLE, SyntaxKind.RECORD_COMPONENT_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE, value, children, Optional.empty(), component.origin());
    sourceAssociations.created(node, tree);
    var reference = new SemanticReference(ReferenceKind.RECORD_COMPONENT, ResolutionStatus.RESOLVED, Optional.of(component.componentKey()),
        Optional.empty(), List.of(), List.of());
    addDeclarationEdge(node, reference, semanticEdges);
    return Optional.of(node);
  }

  private Optional<RecordCounterpartEvidence> recordCounterparts(TypeElement owner, JavaRecordComponent component) {
    var recordComponent = owner.getRecordComponents().get(component.ordinal());
    var accessor = recordComponent.getAccessor();
    if (accessor == null || semantics.declarationKey(accessor).filter(component.accessorKey().orElseThrow()::equals).isEmpty()) {
      return Optional.empty();
    }
    var canonical = owner.getEnclosedElements().stream().filter(ExecutableElement.class::isInstance).map(ExecutableElement.class::cast)
        .filter(executable -> executable.getKind() == ElementKind.CONSTRUCTOR)
        .filter(executable -> executable.getParameters().size() > component.ordinal())
        .filter(executable -> semantics.declarationKey(executable.getParameters().get(component.ordinal()))
            .filter(component.canonicalParameterKey().orElseThrow()::equals).isPresent())
        .toList();
    if (canonical.size() != REQUIRED_COMPONENT_SOURCE_COUNT) {
      return Optional.empty();
    }
    var fields = owner.getEnclosedElements().stream().filter(VariableElement.class::isInstance).map(VariableElement.class::cast)
        .filter(field -> field.getKind() == ElementKind.FIELD)
        .filter(field -> semantics.declarationKey(field).filter(component.backingFieldKey().orElseThrow()::equals).isPresent()).toList();
    if (fields.size() != REQUIRED_COMPONENT_SOURCE_COUNT) {
      return Optional.empty();
    }
    var constructor = canonical.getFirst();
    var accessorImplicit = semantics.sourceTree(accessor) == null;
    var parameterDerived = semantics.declarationOrigin(constructor) == Elements.Origin.MANDATED
        || semantics.isCompactConstructor(constructor);
    var facts = new SyntaxValue.RecordComponentDeclaration.Counterparts(component.backingFieldKey().orElseThrow(),
        component.accessorKey().orElseThrow(), component.canonicalParameterKey().orElseThrow(), accessorImplicit, parameterDerived);
    return Optional.of(new RecordCounterpartEvidence(facts, recordComponent.asType(), fields.getFirst(), accessor,
        constructor.getParameters().get(component.ordinal())));
  }

  private record RecordCounterpartEvidence(SyntaxValue.RecordComponentDeclaration.Counterparts counterparts, TypeMirror componentType,
      VariableElement field, ExecutableElement accessor, VariableElement parameter) {
  }

  private boolean recordAnnotationMirrors(List<SyntaxChild> children, RecordCounterpartEvidence evidence) {
    var writtenType = children.stream().filter(child -> child.role() == SyntaxRole.DECLARED_TYPE).map(SyntaxChild::node).findFirst()
        .orElseThrow();
    var correlation = recordMirrorCorrelation;
    var fieldValues = new ArrayList<SyntaxNode>();
    var accessorValues = new ArrayList<SyntaxNode>();
    var parameterValues = new ArrayList<SyntaxNode>();
    var typeValues = new ArrayList<SyntaxNode>();
    for (var expected : children.stream().filter(child -> child.role() == SyntaxRole.ANNOTATION).map(SyntaxChild::node).toList()) {
      var annotation = expected.value(SyntaxValue.Annotation.class);
      var targets = annotation.applicableTargets();
      var counterparts = evidence.counterparts();
      if (targets.contains(ElementType.FIELD)) {
        fieldValues.add(expected);
      }
      if (annotation.location() == SyntaxValue.AnnotationLocation.TYPE_USE
          || annotation.location() == SyntaxValue.AnnotationLocation.DUAL) {
        typeValues.add(expected);
      }
      if (counterparts.accessorImplicit() && targets.contains(ElementType.METHOD)) {
        accessorValues.add(expected);
      }
      if (counterparts.canonicalParameterDerived() && targets.contains(ElementType.PARAMETER)) {
        parameterValues.add(expected);
      }
    }
    var sourceTypes = new JavaWrittenTypeAnnotationCorrelation(semantics);
    return sourceTypes.matches(writtenType, typeValues, evidence.componentType())
        && sourceTypes.matches(writtenType, typeValues, evidence.field().asType())
        && (!evidence.counterparts().accessorImplicit()
            || sourceTypes.matches(writtenType, typeValues, evidence.accessor().getReturnType()))
        && (!evidence.counterparts().canonicalParameterDerived()
            || sourceTypes.matches(writtenType, typeValues, evidence.parameter().asType()))
        && correlation.containsAll(evidence.field().getAnnotationMirrors(), fieldValues)
        && correlation.containsAll(evidence.accessor().getAnnotationMirrors(), accessorValues)
        && correlation.containsAll(evidence.parameter().getAnnotationMirrors(), parameterValues)
        && correlation.sameAnnotatedType(evidence.componentType(), evidence.field().asType())
        && (!evidence.counterparts().accessorImplicit()
            || correlation.sameAnnotatedType(evidence.componentType(), evidence.accessor().getReturnType()))
        && (!evidence.counterparts().canonicalParameterDerived()
            || correlation.sameAnnotatedType(evidence.componentType(), evidence.parameter().asType()));
  }

  private Optional<SyntaxNode> typeParameter(CompilationUnitTree unit, SourceFile sourceFile, TreePath treePath,
      TypeParameterTree declaration, SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, String ownerKey, int ordinal,
      List<JavaDeclarationReference> semanticEdges, DiagnosticEmissionCollector diagnostics) {
    var range = requiredRange(unit, sourceFile, declaration, occurrencePath, diagnostics);
    var expectedKey = ownerKey + "#type-parameter:" + ordinal;
    var element = treePath == null ? null : semantics.element(treePath);
    if (!treeInventory.isWritten(declaration) || treePath == null || semantics.isImplicitDeclaration(treePath)
        || !(element instanceof TypeParameterElement parameterElement)
        || semantics.declarationKey(parameterElement).filter(expectedKey::equals).isEmpty()
        || !SourceVersion.isIdentifier(declaration.getName())) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(expectedKey, "frontend.unavailable-type-parameter-declaration",
              "A written type parameter has no matching attributed declaration and canonical key", range));
      return Optional.empty();
    }
    if (range.isEmpty()) {
      return Optional.empty();
    }
    var children = new ArrayList<SyntaxChild>();
    var complete = appendAnnotations(unit, sourceFile, declaration.getAnnotations(), path, occurrencePath, DeclarationSite.TYPE_PARAMETER,
        children, diagnostics);
    if (declaration.getBounds().size() != parameterElement.getBounds().size() && !declaration.getBounds().isEmpty()) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(expectedKey, "frontend.inconsistent-type-parameter-bounds",
          "Written and attributed type-parameter bounds have different cardinality", range));
      complete = false;
    }
    for (var index = 0; index < declaration.getBounds().size(); index++) {
      if (index < parameterElement.getBounds().size()) {
        complete &= appendTypeHeaderAttachment(unit, sourceFile, declaration.getBounds().get(index), path, occurrencePath,
            SyntaxRole.TYPE_BOUND, index, parameterElement.getBounds().get(index), children, diagnostics);
      }
    }
    if (!complete) {
      return Optional.empty();
    }
    var upperBound = semantics.typeParameterUpperBound(parameterElement);
    if (upperBound.isEmpty()) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(expectedKey, "frontend.unavailable-type-parameter-bound",
          "The attributed type parameter has no complete upper-bound structure", range));
      return Optional.empty();
    }
    var value = new SyntaxValue.TypeParameterDeclaration(declaration.getName().toString(), ownerKey, ordinal,
        declaration.getAnnotations().size(), declaration.getBounds().size(), upperBound.orElseThrow());
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.TYPE_PARAMETER, SyntaxKind.TYPE_PARAMETER_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE, value, children, Optional.empty(), range.orElseThrow());
    sourceAssociations.created(node, treePath);
    declarationEdge(treePath, node, semanticEdges);
    return Optional.of(node);
  }

  private boolean appendAnnotations(CompilationUnitTree unit, SourceFile sourceFile, List<? extends AnnotationTree> annotations,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, DeclarationSite site, List<SyntaxChild> children,
      DiagnosticEmissionCollector diagnostics) {
    var complete = true;
    for (var index = 0; index < annotations.size(); index++) {
      var annotation = annotationAttachment(unit, sourceFile, annotations.get(index), path.child(SyntaxRole.ANNOTATION, index),
          occurrencePath.child(SyntaxRole.ANNOTATION, index), site, diagnostics);
      if (annotation.isPresent()) {
        children.add(new SyntaxChild(SyntaxRole.ANNOTATION, index, annotation.orElseThrow()));
      }
      complete &= annotation.isPresent();
    }
    return complete;
  }

  private static Set<JavaDeclarationModifier> declarationModifiers(Set<Modifier> modifiers) {
    return modifiers.stream().map(modifier -> JavaDeclarationModifier.valueOf(modifier.name())).collect(Collectors.toUnmodifiableSet());
  }

  Optional<ExecutableIdentity> identifyExecutable(CompilationUnitTree unit, SourceFile sourceFile, TreePath methodPath,
      MethodTree declaration, Optional<SourceSpan> range, DiagnosticEmissionCollector diagnostics) {
    var element = semantics.element(methodPath);
    if (!(element instanceof ExecutableElement executable) || semantics.declarationKey(executable).isEmpty()) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.unavailable-executable-declaration", "A written executable has no attributed declaration and canonical key", range));
      return Optional.empty();
    }
    var owner = methodPath.getParentPath().getLeaf();
    if (!(owner instanceof ClassTree ownerType)) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.unavailable-executable-owner", "A written executable has no source type owner", range));
      return Optional.empty();
    }
    var sourceOwner = semantics.element(methodPath.getParentPath());
    var sourceOwnerKey = sourceOwner instanceof TypeElement ? semantics.declarationKey(sourceOwner) : Optional.<String>empty();
    var attributedOwner = executable.getEnclosingElement();
    var attributedOwnerKey = attributedOwner instanceof TypeElement ? semantics.declarationKey(attributedOwner) : Optional.<String>empty();
    if (sourceOwnerKey.isEmpty() || !sourceOwnerKey.equals(attributedOwnerKey)) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.unavailable-executable-owner", "A written executable has no matching source and attributed type owner", range));
      return Optional.empty();
    }
    var constructor = executable.getKind() == ElementKind.CONSTRUCTOR;
    var compactConstructor = constructor && semantics.isCompactConstructor(executable);
    var kind = constructor
        ? compactConstructor
            ? SyntaxValue.ExecutableDeclaration.ExecutableKind.COMPACT_CONSTRUCTOR
            : SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR
        : ownerType.getKind() == Tree.Kind.ANNOTATION_TYPE
            ? SyntaxValue.ExecutableDeclaration.ExecutableKind.ANNOTATION_ELEMENT
            : SyntaxValue.ExecutableDeclaration.ExecutableKind.METHOD;
    var name = constructor ? ownerType.getSimpleName().toString() : declaration.getName().toString();
    if (!SourceVersion.isIdentifier(name) || constructor != (declaration.getReturnType() == null)) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.invalid-executable-header", "A written executable has an inconsistent source name or return-type form", range));
      return Optional.empty();
    }
    var methodKey = semantics.declarationKey(executable).orElseThrow();
    return Optional.of(new ExecutableIdentity(executable, sourceOwnerKey.orElseThrow(), methodKey, name, kind, constructor,
        compactConstructor, range.orElseThrow()));
  }
  Optional<ExecutableHeader> signature(CompilationUnitTree unit, SourceFile sourceFile, TreePath methodPath, MethodTree declaration,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, ExecutableIdentity identity,
      DiagnosticEmissionCollector diagnostics) {
    var executable = identity.element();
    var methodKey = identity.key();
    var constructor = identity.constructor();
    var compactConstructor = identity.compactConstructor();
    var kind = identity.kind();
    var attributedOwner = executable.getEnclosingElement();
    var range = Optional.of(identity.range());
    var stagedEdges = new ArrayList<JavaDeclarationReference>();
    var children = new ArrayList<SyntaxChild>();
    var site = constructor
        ? DeclarationSite.CONSTRUCTOR
        : declaration.getReturnType() instanceof PrimitiveTypeTree primitive && primitive.getPrimitiveTypeKind() == TypeKind.VOID
            ? DeclarationSite.VOID_METHOD
            : DeclarationSite.METHOD;
    var complete = appendAnnotations(unit, sourceFile, declaration.getModifiers().getAnnotations(), path, occurrencePath, site, children,
        diagnostics);
    for (var index = 0; index < declaration.getTypeParameters().size(); index++) {
      var parameter = declaration.getTypeParameters().get(index);
      var translated = typeParameter(unit, sourceFile, TreePath.getPath(methodPath, parameter), parameter,
          path.child(SyntaxRole.TYPE_PARAMETER, index), occurrencePath.child(SyntaxRole.TYPE_PARAMETER, index), methodKey, index,
          stagedEdges, diagnostics);
      if (translated.isPresent()) {
        children.add(new SyntaxChild(SyntaxRole.TYPE_PARAMETER, index, translated.orElseThrow()));
      }
      complete &= translated.isPresent();
    }
    var receiverTree = declaration.getReceiverParameter();
    var receiverType = semantics.receiverType(executable);
    var receiverOwnerKey = semantics.receiverOwnerKey(executable);
    var receiverSimpleName = semantics.receiverSimpleName(executable);
    var enclosingTypeKey = constructor && attributedOwner instanceof TypeElement ownerTypeElement
        ? semantics.enclosingTypeKey(ownerTypeElement)
        : Optional.<String>empty();
    if (receiverType == SyntaxType.Erroneous.INSTANCE) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.erroneousCompilerState(methodKey, "frontend.error-receiver-type",
          "javac reported an error type for the executable receiver", range));
      complete = false;
    } else if (receiverType instanceof SyntaxType.NoType noType) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(methodKey, "frontend.invalid-receiver-no-type",
          "The executable receiver resolved to compiler pseudo-type " + noType.kind(), range));
      complete = false;
    } else if (receiverType == SyntaxType.Unsupported.INSTANCE) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(methodKey, "frontend.unsupported-receiver-type",
          "The executable receiver has an implementation-reserved compiler type", range));
      complete = false;
    } else if (receiverType == SyntaxType.Unresolved.INSTANCE || receiverType instanceof SyntaxType.StructureUnavailable
        || (receiverTree != null && receiverType == SyntaxType.NotApplicable.INSTANCE)) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(methodKey, "frontend.unavailable-receiver-type",
          "The attributed receiver type is unavailable for the executable", range));
      complete = false;
    }
    if (receiverType instanceof SyntaxType.Resolved && (receiverOwnerKey.isEmpty() || receiverSimpleName.isEmpty())) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(methodKey, "frontend.unavailable-receiver-owner",
          "The attributed receiver has no canonical type owner", range));
      complete = false;
    }
    if (receiverTree != null) {
      var receiverNameExpression = receiverTree.getNameExpression();
      var receiverFormValid = switch (kind) {
        case METHOD -> receiverNameExpression instanceof IdentifierTree;
        case CONSTRUCTOR -> receiverNameExpression instanceof MemberSelectTree;
        case COMPACT_CONSTRUCTOR, ANNOTATION_ELEMENT -> false;
      };
      if (receiverNameExpression != null && !receiverFormValid) {
        diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
            "frontend.invalid-executable-header", "An explicit receiver has a name form incompatible with its executable", range));
        return Optional.empty();
      }
      var receiver = parameter(unit, sourceFile, methodPath, receiverTree, path.child(SyntaxRole.RECEIVER_PARAMETER, 0),
          occurrencePath.child(SyntaxRole.RECEIVER_PARAMETER, 0), methodKey, executable.getReceiverType(), true, false, stagedEdges,
          diagnostics);
      receiver.ifPresent(node -> children.add(new SyntaxChild(SyntaxRole.RECEIVER_PARAMETER, 0, node)));
      complete &= receiver.isPresent();
    }
    var writtenParameters = compactConstructor ? List.<VariableTree>of() : declaration.getParameters();
    if (executable.getParameters().size() != declaration.getParameters().size()
        || (executable.isVarArgs() && declaration.getParameters().isEmpty())) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(methodKey, "frontend.inconsistent-formal-parameters",
          "The attributed formal-parameter list does not match written source parameters", range));
      complete = false;
    }
    for (var index = 0; index < writtenParameters.size(); index++) {
      var formal = writtenParameters.get(index);
      var parameterKey = methodKey + ":parameter:" + index;
      var translated = index < executable.getParameters().size()
          ? parameter(unit, sourceFile, methodPath, formal, path.child(SyntaxRole.PARAMETER, index),
              occurrencePath.child(SyntaxRole.PARAMETER, index), parameterKey, executable.getParameters().get(index).asType(), false,
              executable.isVarArgs() && index == writtenParameters.size() - 1, stagedEdges, diagnostics)
          : Optional.<SyntaxNode>empty();
      if (translated.isPresent()) {
        children.add(new SyntaxChild(SyntaxRole.PARAMETER, index, translated.orElseThrow()));
      }
      complete &= translated.isPresent();
    }
    if (declaration.getReturnType() != null) {
      complete &= appendTypeHeaderAttachment(unit, sourceFile, declaration.getReturnType(), path, occurrencePath, SyntaxRole.RETURN_TYPE, 0,
          executable.getReturnType(), children, diagnostics);
    }
    var thrownTypes = semantics.thrownTypes(executable);
    if (thrownTypes.size() != declaration.getThrows().size()) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(methodKey, "frontend.inconsistent-thrown-types",
          "The attributed thrown-type list does not match written source attachments", range));
      complete = false;
    }
    for (var index = 0; index < declaration.getThrows().size(); index++) {
      if (index < executable.getThrownTypes().size()) {
        complete &= appendTypeHeaderAttachment(unit, sourceFile, declaration.getThrows().get(index), path, occurrencePath,
            SyntaxRole.THROWN_TYPE, index, executable.getThrownTypes().get(index), children, diagnostics);
      }
      if (index < thrownTypes.size() && !(thrownTypes.get(index) instanceof SyntaxType.Resolved)) {
        var thrownType = thrownTypes.get(index);
        var code = thrownType instanceof SyntaxType.NoType
            ? "frontend.invalid-thrown-no-type"
            : thrownType == SyntaxType.Unsupported.INSTANCE ? "frontend.unsupported-thrown-type" : "frontend.unavailable-thrown-type";
        diagnostics.emit(DIAGNOSTIC_PRODUCER,
            FrontendDiagnosticFactory.otherError(methodKey, code, "A written thrown type has no resolved attributed outcome",
                sourceEvidence.origin(unit, sourceFile, declaration.getThrows().get(index))));
        complete = false;
      }
    }
    var shape = new SyntaxValue.ExecutableDeclaration.HeaderShape(declaration.getModifiers().getAnnotations().size(),
        declaration.getTypeParameters().size(), receiverTree != null, writtenParameters.size(), declaration.getReturnType() != null,
        declaration.getThrows().size(), declaration.getBody() != null, executable.isVarArgs());
    return Optional.of(new ExecutableHeader(identity, new Contributions(children, stagedEdges, complete), shape,
        new AttributedSignature(receiverType, receiverOwnerKey, receiverSimpleName, enclosingTypeKey, thrownTypes),
        declarationModifiers(treeInventory.writtenModifiers(declaration.getModifiers())), declarationModifiers(executable.getModifiers())));
  }

  Optional<SyntaxValue.ExecutableDeclaration> completeExecutable(SourceFile sourceFile, ExecutableHeader header,
      Optional<AnnotationElementValue> defaultValue, DiagnosticEmissionCollector diagnostics) {
    var identity = header.identity();
    var attributed = header.attributed();
    try {
      return Optional.of(new SyntaxValue.ExecutableDeclaration(identity.name(), identity.ownerKey(), identity.key(), identity.kind(),
          header.writtenModifiers(), header.effectiveModifiers(), header.shape(),
          new SyntaxValue.ExecutableDeclaration.AttributedHeader(attributed.receiverType(), attributed.receiverOwnerKey(),
              attributed.receiverSimpleName(), attributed.enclosingTypeKey(), attributed.thrownTypes(), defaultValue)));
    } catch (IllegalArgumentException exception) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.invalid-executable-header",
              Objects.requireNonNullElse(exception.getMessage(), "The executable header is inconsistent"), Optional.of(identity.range())));
      return Optional.empty();
    }
  }
  DefaultCompletion completeDefault(CompilationUnitTree unit, SourceFile sourceFile, MethodTree declaration, ExecutableElement executable,
      SyntaxStructuralPath path, DiagnosticEmissionCollector diagnostics) {
    if (declaration.getDefaultValue() == null && executable.getDefaultValue() == null) {
      return new DefaultCompletion(Optional.empty(), true);
    }
    var projected = defaultValue(unit, sourceFile, declaration, executable, path, diagnostics);
    return new DefaultCompletion(projected, projected.isPresent());
  }

  private Optional<DefaultValue> defaultValue(CompilationUnitTree unit, SourceFile sourceFile, MethodTree declaration,
      ExecutableElement executable, SyntaxStructuralPath path, DiagnosticEmissionCollector diagnostics) {
    return annotationProjector.defaultValue(unit, sourceFile, declaration, executable, path.child(SyntaxRole.DEFAULT_VALUE, 0), diagnostics)
        .value().map(result -> new DefaultValue(result.node(), result.value()));
  }
  private Optional<SyntaxNode> parameter(CompilationUnitTree unit, SourceFile sourceFile, TreePath methodPath, VariableTree declaration,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, String identityKey, TypeMirror attributedType, boolean receiver,
      boolean varArgs, List<JavaDeclarationReference> semanticEdges, DiagnosticEmissionCollector diagnostics) {
    var range = requiredRange(unit, sourceFile, declaration, occurrencePath, diagnostics);
    var parameterPath = TreePath.getPath(methodPath, declaration);
    var element = parameterPath == null ? null : semantics.element(parameterPath);
    if (!treeInventory.isWritten(declaration) || parameterPath == null || semantics.isImplicitDeclaration(parameterPath)) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(identityKey, "frontend.unavailable-written-parameter",
          "An attributed parameter is absent from the parsed source declaration", range));
      return Optional.empty();
    }
    if (!receiver
        && (!(element instanceof VariableElement variable) || semantics.declarationKey(variable).filter(identityKey::equals).isEmpty())) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(identityKey, "frontend.unavailable-formal-parameter",
          "A written formal parameter has no matching attributed declaration and canonical key", range));
      return Optional.empty();
    }
    var nameExpression = declaration.getNameExpression();
    if (range.isEmpty() || declaration.getType() == null || declaration.getInitializer() != null || (!receiver && nameExpression != null)) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(identityKey, "frontend.invalid-parameter-header",
          "A written parameter has an unavailable type or invalid source form", range));
      return Optional.empty();
    }
    var name = receiver ? RECEIVER_NAME : declaration.getName().toString();
    if (!receiver && !SourceVersion.isIdentifier(name)) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(identityKey, "frontend.invalid-parameter-name",
          "A written formal parameter has no valid simple name", range));
      return Optional.empty();
    }
    if (receiver && nameExpression == null) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(identityKey, "frontend.unavailable-receiver-name",
          "An explicit receiver has no structural name expression", range));
      return Optional.empty();
    }
    var children = new ArrayList<SyntaxChild>();
    var complete = appendAnnotations(unit, sourceFile, declaration.getModifiers().getAnnotations(), path, occurrencePath,
        receiver ? DeclarationSite.RECEIVER : DeclarationSite.PARAMETER, children, diagnostics);
    complete &= appendTypeHeaderAttachment(unit, sourceFile, declaration.getType(), path, occurrencePath, SyntaxRole.DECLARED_TYPE, 0,
        attributedType, children, diagnostics);
    if (receiver) {
      var qualified = receiverName(nameExpression);
      var qualifierRange = requiredRange(unit, sourceFile, nameExpression, occurrencePath.child(SyntaxRole.QUALIFIER, 0), diagnostics);
      if (qualified.isPresent() && qualifierRange.isPresent()) {
        var sourceKind = JavaSourceKind.valueOf(nameExpression.getKind().name());
        var qualifier = SyntaxNode.source(sourceFile.sourceFileKey(), path.child(SyntaxRole.QUALIFIER, 0), sourceKind,
            SyntaxKind.RECEIVER_NAME, SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.SourceSymbol(qualified.orElseThrow()), List.of(),
            Optional.empty(), qualifierRange.orElseThrow());
        sourceAssociations.created(qualifier, new TreePath(parameterPath, nameExpression));
        children.add(new SyntaxChild(SyntaxRole.QUALIFIER, 0, qualifier));
      } else {
        diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(identityKey, "frontend.unavailable-receiver-name",
            "An explicit receiver has no structural name expression and source range", range));
        complete = false;
      }
    }
    if (!complete) {
      return Optional.empty();
    }
    var effective = element instanceof VariableElement variable ? variable.getModifiers() : declaration.getModifiers().getFlags();
    SyntaxValue.ParameterHeader value = receiver
        ? new SyntaxValue.ReceiverParameter(identityKey, declarationModifiers(treeInventory.writtenModifiers(declaration.getModifiers())),
            declarationModifiers(effective), declaration.getModifiers().getAnnotations().size())
        : new SyntaxValue.ParameterDeclaration(identityKey, name,
            declarationModifiers(treeInventory.writtenModifiers(declaration.getModifiers())), declarationModifiers(effective),
            declaration.getModifiers().getAnnotations().size(), true, varArgs);
    var syntaxKind = receiver ? SyntaxKind.RECEIVER_PARAMETER : SyntaxKind.PARAMETER_DECLARATION;
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.VARIABLE, syntaxKind, SyntaxType.NotApplicable.INSTANCE,
        value, children, Optional.empty(), range.orElseThrow());
    sourceAssociations.created(node, parameterPath);
    if (!receiver) {
      declarationEdge(parameterPath, node, semanticEdges);
    }
    return Optional.of(node);
  }

  Optional<FieldHeader> field(CompilationUnitTree unit, SourceFile sourceFile, TreePath fieldPath, VariableTree declaration,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, DiagnosticEmissionCollector diagnostics) {
    var range = requiredRange(unit, sourceFile, declaration, occurrencePath, diagnostics);
    if (range.isEmpty()) {
      return Optional.empty();
    }
    var element = semantics.element(fieldPath);
    var owner = semantics.element(fieldPath.getParentPath());
    var type = semantics.type(fieldPath);
    if (!(element instanceof VariableElement variable) || !(owner instanceof TypeElement ownerType)
        || !(type instanceof SyntaxType.Resolved resolved) || resolved.identity().category() == JavaTypeCategory.ERROR
        || resolved.identity().category() == JavaTypeCategory.OTHER || semantics.declarationKey(variable).isEmpty()
        || semantics.declarationKey(ownerType).isEmpty() || !Objects.equals(variable.getEnclosingElement(), ownerType)
        || (variable.getKind() != ElementKind.FIELD && variable.getKind() != ElementKind.ENUM_CONSTANT)) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.unavailable-field-declaration",
              "A written field or enum constant has no matching attributed declaration, owner, key, or type", range));
      return Optional.empty();
    }
    var enumConstant = variable.getKind() == ElementKind.ENUM_CONSTANT;
    var initializer = declaration.getInitializer();
    if (enumConstant && !(initializer instanceof NewClassTree)) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.unavailable-enum-constant-shape",
              "An enum constant has no attributed constructor-argument and body source shape", range));
      return Optional.empty();
    }
    var children = new ArrayList<SyntaxChild>();
    var sharedKeys = enumConstant ? List.<String>of() : sharedFieldKeys(unit, fieldPath, declaration);
    var annotationOwners = sharedKeys.isEmpty()
        ? Optional.<JavaSharedAnnotations>empty()
        : Optional.of(fieldAnnotations.apply(sharedKeys.getFirst()));
    var ownsModifiers = sharedKeys.isEmpty() || sharedKeys.getFirst().equals(semantics.declarationKey(variable).orElseThrow());
    var modifierAnnotations = ownsModifiers ? declaration.getModifiers().getAnnotations() : List.<AnnotationTree>of();
    var complete = appendAnnotations(unit, sourceFile, modifierAnnotations, path, occurrencePath,
        enumConstant ? DeclarationSite.ENUM_CONSTANT : DeclarationSite.FIELD, children, diagnostics);
    if (complete && annotationOwners.isPresent()) {
      children.forEach(child -> annotationOwners.orElseThrow().retain(child.node().origin().orElseThrow(), child.node().nodeId()));
    }
    if (!enumConstant) {
      if (declaration.getType() == null) {
        diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
            "frontend.unavailable-field-type-attachment", "A written field has no source type attachment", range));
        return Optional.empty();
      }
      var typePath = path.child(SyntaxRole.DECLARED_TYPE, 0);
      var typeNode = new JavaTypeUseTranslator(semantics, sourceEvidence, treeInventory, annotationOwners, sourceAssociations)
          .translateDeclaration(unit, sourceFile, declaration.getType(), new TreePath(fieldPath, declaration.getType()), typePath,
              occurrencePath.child(SyntaxRole.DECLARED_TYPE, 0), semantics.declarationType(variable.asType()), diagnostics)
          .value();
      typeNode.ifPresent(node -> children.add(new SyntaxChild(SyntaxRole.DECLARED_TYPE, 0, node)));
      complete &= typeNode.isPresent();
    }
    if (!complete) {
      return Optional.empty();
    }
    var writtenModifiers = enumConstant
        ? Set.<JavaDeclarationModifier>of()
        : declarationModifiers(treeInventory.writtenModifiers(declaration.getModifiers()));
    var kind = enumConstant ? SyntaxValue.VariableDeclaration.Kind.ENUM_CONSTANT : SyntaxValue.VariableDeclaration.Kind.FIELD;
    var creation = enumConstant && initializer instanceof NewClassTree newClass ? newClass : null;
    var argumentList = creation == null
        ? Optional.of(false)
        : creation.getArguments().isEmpty()
            ? JavaEnumConstantSourceEvidence.argumentList(sourceFile, range.orElseThrow(),
                sourceEvidence.rawStart(unit, creation.getIdentifier()))
            : Optional.of(true);
    if (argumentList.isEmpty()) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.unavailable-enum-argument-list", "A written enum constant has no verifiable argument-list form", range));
      return Optional.empty();
    }
    var value = new SyntaxValue.VariableDeclaration(declaration.getName().toString(), semantics.declarationKey(ownerType).orElseThrow(),
        semantics.declarationKey(variable).orElseThrow(), kind, writtenModifiers, declarationModifiers(variable.getModifiers()),
        resolved.identity(), modifierAnnotations.size(), !enumConstant, initializer != null, argumentList.orElseThrow(),
        creation == null ? 0 : creation.getArguments().size(), creation != null && creation.getClassBody() != null, sharedKeys);
    return Optional.of(new FieldHeader(value, children, range.orElseThrow()));
  }
  private List<String> sharedFieldKeys(CompilationUnitTree unit, TreePath fieldPath, VariableTree declaration) {
    if (!(fieldPath.getParentPath().getLeaf() instanceof ClassTree owner)) {
      return List.of();
    }
    var start = sourceEvidence.rawStart(unit, declaration);
    var keys = owner.getMembers().stream().filter(treeInventory::isWritten).filter(VariableTree.class::isInstance)
        .map(VariableTree.class::cast).filter(candidate -> sourceEvidence.rawStart(unit, candidate) == start)
        .map(candidate -> semantics.element(new TreePath(fieldPath.getParentPath(), candidate)))
        .filter(element -> element != null && element.getKind() == ElementKind.FIELD).map(semantics::declarationKey)
        .flatMap(Optional::stream).toList();
    return keys.size() > 1 ? keys : List.of();
  }

  private Optional<SourceSpan> requiredRange(CompilationUnitTree unit, SourceFile sourceFile, Tree tree, SyntaxStructuralPath path,
      DiagnosticEmissionCollector diagnostics) {
    return sourceEvidence.requiredRange(unit, sourceFile, tree, path, diagnostics).value();
  }

  private void declarationEdge(TreePath path, SyntaxNode node, List<JavaDeclarationReference> edges) {
    JavaDeclarationReference.attributed(semantics, path, node).ifPresent(edges::add);
  }

  private void addDeclarationEdge(SyntaxNode node, SemanticReference reference, List<JavaDeclarationReference> edges) {
    edges.add(new JavaDeclarationReference(node, reference));
  }

}
