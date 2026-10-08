package org.aquilagraph.frontend;

import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import org.aquilagraph.frontend.JavaConstructorSelection.SelectedConstructorFacts;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.InvocationMode;
import org.aquilagraph.source.api.InvocationSemantics;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeReference;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.jspecify.annotations.Nullable;

final class SemanticResolver {
  private static final String PATH_ARGUMENT = "path";
  private static final String ELEMENT_ARGUMENT = "element";
  private static final String TYPE_ARGUMENT = "type";
  private static final String EXECUTABLE_ARGUMENT = "executable";
  private final Trees trees;
  private final Types types;
  private final Elements elements;
  private final DeclarationIdentityRegistry identities;
  private final Optional<JavaConstructorSelection> constructorSelection;

  SemanticResolver(Trees trees, Types types, Elements elements, DeclarationIdentityRegistry identities) {
    this(trees, types, elements, identities, Optional.empty());
  }

  SemanticResolver(Trees trees, Types types, Elements elements, DeclarationIdentityRegistry identities,
      Optional<JavaConstructorSelection> constructorSelection) {
    this.trees = Objects.requireNonNull(trees, "trees");
    this.types = Objects.requireNonNull(types, "types");
    this.elements = Objects.requireNonNull(elements, "elements");
    this.identities = Objects.requireNonNull(identities, "identities");
    this.constructorSelection = Objects.requireNonNull(constructorSelection, "constructorSelection");
  }

  Optional<SelectedConstructorFacts> selectedConstructor(TreePath path, ExecutableElement selected) {
    return constructorSelection.flatMap(selection -> selection.select(path, selected));
  }

  Element element(TreePath path) {
    var element = trees.getElement(Objects.requireNonNull(path, PATH_ARGUMENT));
    if (element != null && path.getLeaf() instanceof VariableTree) {
      identities.registerSourceDeclaration(element, path);
    }
    return element;
  }

  boolean isLambdaParameter(Element element) {
    return identities.isLambdaParameter(element);
  }

  Optional<TypeMirror> functionalReturnType(TreePath path) {
    return attributedMirror(path).flatMap(this::functionalReturnType);
  }

  private Optional<TypeMirror> functionalReturnType(TypeMirror type) {
    var methods = functionalMethods(type);
    var candidates = methods.stream().filter(candidate -> candidate.type().getTypeVariables().isEmpty() && methods.stream()
        .allMatch(other -> candidate.name().equals(other.name()) && types.isSubsignature(candidate.type(), other.type()))).toList();
    var covariant = candidates.stream().filter(candidate -> methods.stream()
        .allMatch(other -> sameOrSubtypeReturn(candidate.type().getReturnType(), other.type().getReturnType()))).findFirst();
    var descriptor = covariant.or(() -> candidates.stream()
        .filter(candidate -> methods.stream().allMatch(other -> substitutableReturn(candidate.type(), other.type()))).findFirst());
    return descriptor.map(method -> method.type().getReturnType());
  }

  private List<FunctionalMethod> functionalMethods(TypeMirror type) {
    if (type instanceof IntersectionType intersection) {
      return intersection.getBounds().stream().flatMap(bound -> functionalMethods(bound).stream()).toList();
    }
    if (!(type instanceof DeclaredType declared) || !(declared.asElement() instanceof TypeElement owner)
        || !elements.isFunctionalInterface(owner)) {
      return List.of();
    }
    var object = elements.getTypeElement("java.lang.Object");
    return elements.getAllMembers(owner).stream().filter(ExecutableElement.class::isInstance).map(ExecutableElement.class::cast)
        .filter(method -> method.getKind() == ElementKind.METHOD && method.getModifiers().contains(Modifier.ABSTRACT)
            && !method.getModifiers().contains(Modifier.STATIC))
        .filter(method -> object.getEnclosedElements().stream().filter(ExecutableElement.class::isInstance)
            .map(ExecutableElement.class::cast).filter(candidate -> candidate.getModifiers().contains(Modifier.PUBLIC))
            .noneMatch(candidate -> method.getSimpleName().contentEquals(candidate.getSimpleName())
                && types.isSubsignature((ExecutableType) method.asType(), (ExecutableType) candidate.asType())))
        .map(method -> new FunctionalMethod(method.getSimpleName().toString(), (ExecutableType) types.asMemberOf(declared, method)))
        .toList();
  }

  private boolean sameOrSubtypeReturn(TypeMirror candidate, TypeMirror other) {
    return types.isSameType(candidate, other)
        || (!candidate.getKind().isPrimitive() && candidate.getKind() != TypeKind.VOID && types.isSubtype(candidate, other));
  }

  private boolean substitutableReturn(ExecutableType candidate, ExecutableType other) {
    var result = candidate.getReturnType();
    var target = other.getReturnType();
    return sameOrSubtypeReturn(result, target) || (!result.getKind().isPrimitive() && result.getKind() != TypeKind.VOID
        && !target.getKind().isPrimitive() && target.getKind() != TypeKind.VOID && (types.isAssignable(result, target)
            || (!types.isSubsignature(other, candidate) && types.isSameType(result, types.erasure(target)))));
  }

  private record FunctionalMethod(String name, ExecutableType type) {
  }

  boolean isImplicitDeclaration(TreePath path) {
    var declaration = element(path);
    return declaration != null && elements.getOrigin(declaration) != Elements.Origin.EXPLICIT;
  }

  @Nullable
  Tree sourceTree(Element element) {
    return trees.getTree(Objects.requireNonNull(element, ELEMENT_ARGUMENT));
  }

  @Nullable
  Tree annotationSourceTree(Element owner, AnnotationMirror mirror) {
    return trees.getTree(Objects.requireNonNull(owner, ELEMENT_ARGUMENT), Objects.requireNonNull(mirror, "mirror"));
  }

  Elements.Origin declarationOrigin(Element element) {
    return elements.getOrigin(Objects.requireNonNull(element, ELEMENT_ARGUMENT));
  }

  Elements.Origin annotationOrigin(Element owner, AnnotationMirror mirror) {
    return elements.getOrigin(Objects.requireNonNull(owner, ELEMENT_ARGUMENT), Objects.requireNonNull(mirror, "mirror"));
  }

  boolean sameType(TypeMirror first, TypeMirror second) {
    return types.isSameType(Objects.requireNonNull(first, "first"), Objects.requireNonNull(second, "second"));
  }

  boolean assignable(TypeMirror source, TypeMirror target) {
    return types.isAssignable(Objects.requireNonNull(source, "source"), Objects.requireNonNull(target, "target"));
  }

  TypeMirror erasedType(TypeMirror type) {
    return types.erasure(Objects.requireNonNull(type, TYPE_ARGUMENT));
  }

  PrimitiveType primitiveType(TypeKind kind) {
    return types.getPrimitiveType(Objects.requireNonNull(kind, "kind"));
  }

  TypeMirror boxedType(PrimitiveType type) {
    return types.boxedClass(Objects.requireNonNull(type, TYPE_ARGUMENT)).asType();
  }

  Optional<PrimitiveType> unboxedType(TypeMirror type) {
    Objects.requireNonNull(type, TYPE_ARGUMENT);
    try {
      return Optional.of(types.unboxedType(type));
    } catch (IllegalArgumentException noUnboxingConversion) {
      return Optional.empty();
    }
  }

  TypeMirror memberType(DeclaredType owner, Element member) {
    return types.asMemberOf(Objects.requireNonNull(owner, "owner"), Objects.requireNonNull(member, "member"));
  }

  boolean isCompactConstructor(ExecutableElement element) {
    return elements.isCompactConstructor(Objects.requireNonNull(element, ELEMENT_ARGUMENT));
  }

  Optional<String> declarationKey(Element element) {
    return identities.keyOf(Objects.requireNonNull(element, ELEMENT_ARGUMENT));
  }

  Optional<JavaTypeStructure> typeParameterUpperBound(TypeParameterElement parameter) {
    var mirror = Objects.requireNonNull(parameter, "parameter").asType();
    return mirror instanceof TypeVariable variable ? structuredType(variable.getUpperBound()) : Optional.empty();
  }

  String executableSignature(ExecutableElement element) {
    return identities.signatureOf(Objects.requireNonNull(element, ELEMENT_ARGUMENT));
  }

  Optional<SemanticReference> reference(TreePath path, ReferenceKind expectedKind) {
    Objects.requireNonNull(path, PATH_ARGUMENT);
    Objects.requireNonNull(expectedKind, "expectedKind");
    var element = element(path);
    if (element == null) {
      return Optional.of(unresolvedReference(expectedKind));
    }
    var actualKind = isLambdaParameter(element) ? Optional.of(ReferenceKind.LOCAL) : referenceKind(element.getKind());
    if (actualKind.isEmpty() || actualKind.orElseThrow() != expectedKind) {
      return Optional.of(unresolvedReference(expectedKind));
    }
    var type = compilerType(path);
    var symbolKey = identities.keyOf(element);
    var status = type != null && type.getKind() == TypeKind.ERROR
        ? ResolutionStatus.ERROR
        : symbolKey.isPresent() ? ResolutionStatus.RESOLVED : ResolutionStatus.UNRESOLVED;
    return Optional
        .of(new SemanticReference(expectedKind, status, symbolKey, typeName(type), typeAlternativeNames(type), assignableTypeNames(type)));
  }

  SyntaxType type(TreePath path) {
    Objects.requireNonNull(path, PATH_ARGUMENT);
    if (!(path.getLeaf() instanceof ExpressionTree) && !(path.getLeaf() instanceof VariableTree)) {
      return SyntaxType.NotApplicable.INSTANCE;
    }
    return projectType(compilerType(path), true);
  }

  Optional<JavaTypeIdentity> resolvedType(TreePath path) {
    var syntaxType = type(path);
    return syntaxType instanceof SyntaxType.Resolved resolved ? Optional.of(resolved.identity()) : Optional.empty();
  }

  SyntaxType explicitType(TreePath path) {
    Objects.requireNonNull(path, PATH_ARGUMENT);
    return projectType(compilerType(path), true);
  }

  List<? extends AnnotationMirror> typeAnnotationMirrors(TreePath path) {
    var type = compilerType(Objects.requireNonNull(path, PATH_ARGUMENT));
    return type == null ? List.of() : type.getAnnotationMirrors();
  }

  Optional<TypeMirror> attributedMirror(TreePath path) {
    return Optional.ofNullable(compilerType(Objects.requireNonNull(path, PATH_ARGUMENT)));
  }

  SyntaxType declarationType(TypeMirror type) {
    return projectType(Objects.requireNonNull(type, TYPE_ARGUMENT), true);
  }

  private SyntaxType projectType(@Nullable TypeMirror type, boolean includeStructure) {
    if (type == null) {
      return SyntaxType.Unresolved.INSTANCE;
    }
    return switch (type.getKind()) {
      case NONE -> new SyntaxType.NoType(SyntaxType.NoType.Kind.NONE);
      case PACKAGE -> new SyntaxType.NoType(SyntaxType.NoType.Kind.PACKAGE);
      case MODULE -> new SyntaxType.NoType(SyntaxType.NoType.Kind.MODULE);
      case ERROR -> SyntaxType.Erroneous.INSTANCE;
      case OTHER -> SyntaxType.Unsupported.INSTANCE;
      case NULL, EXECUTABLE -> new SyntaxType.Resolved(typeIdentity(type));
      default -> includeStructure
          ? structuredType(type).<SyntaxType>map(value -> new SyntaxType.Resolved(typeIdentity(type), value))
              .orElseGet(() -> new SyntaxType.StructureUnavailable(typeIdentity(type)))
          : new SyntaxType.Resolved(typeIdentity(type));
    };
  }

  private Optional<JavaTypeStructure> structuredType(TypeMirror type) {
    return new JavaTypeStructureProjection(identities).project(type);
  }

  SyntaxType receiverType(ExecutableElement executable) {
    var type = Objects.requireNonNull(executable, EXECUTABLE_ARGUMENT).getReceiverType();
    if (type == null) {
      return SyntaxType.Unresolved.INSTANCE;
    }
    return switch (type.getKind()) {
      case NONE -> SyntaxType.NotApplicable.INSTANCE;
      case ERROR -> SyntaxType.Erroneous.INSTANCE;
      case DECLARED -> projectType(type, true);
      case PACKAGE -> new SyntaxType.NoType(SyntaxType.NoType.Kind.PACKAGE);
      case MODULE -> new SyntaxType.NoType(SyntaxType.NoType.Kind.MODULE);
      case OTHER -> SyntaxType.Unsupported.INSTANCE;
      default -> SyntaxType.Unresolved.INSTANCE;
    };
  }

  Optional<String> receiverOwnerKey(ExecutableElement executable) {
    var type = Objects.requireNonNull(executable, EXECUTABLE_ARGUMENT).getReceiverType();
    return type instanceof DeclaredType declared && declared.asElement() instanceof TypeElement owner
        ? identities.keyOf(owner)
        : Optional.empty();
  }

  Optional<String> receiverSimpleName(ExecutableElement executable) {
    var type = Objects.requireNonNull(executable, EXECUTABLE_ARGUMENT).getReceiverType();
    return type instanceof DeclaredType declared && declared.asElement() instanceof TypeElement owner
        ? Optional.of(owner.getSimpleName().toString())
        : Optional.empty();
  }

  Optional<String> enclosingTypeKey(TypeElement type) {
    for (var owner = Objects.requireNonNull(type, TYPE_ARGUMENT).getEnclosingElement(); owner != null; owner = owner
        .getEnclosingElement()) {
      if (owner instanceof TypeElement enclosing) {
        return identities.keyOf(enclosing);
      }
    }
    return Optional.empty();
  }

  List<SyntaxType> thrownTypes(ExecutableElement executable) {
    return Objects.requireNonNull(executable, EXECUTABLE_ARGUMENT).getThrownTypes().stream().map(type -> projectType(type, true)).toList();
  }

  Optional<SemanticReference> typeReference(TreePath path) {
    Objects.requireNonNull(path, PATH_ARGUMENT);
    var element = trees.getElement(path);
    return element == null
        ? Optional.empty()
        : reference(path, element.getKind() == ElementKind.TYPE_PARAMETER ? ReferenceKind.TYPE_PARAMETER : ReferenceKind.TYPE);
  }

  Optional<SemanticReference> typeUseReference(TreePath path) {
    var type = compilerType(Objects.requireNonNull(path, PATH_ARGUMENT));
    if (type == null || (type.getKind() != TypeKind.DECLARED && type.getKind() != TypeKind.TYPEVAR)) {
      return Optional.empty();
    }
    var element = types.asElement(type);
    if (element == null) {
      return Optional.empty();
    }
    var kind = element.getKind() == ElementKind.TYPE_PARAMETER ? ReferenceKind.TYPE_PARAMETER : ReferenceKind.TYPE;
    return identities.keyOf(element)
        .map(key -> new SemanticReference(kind, ResolutionStatus.RESOLVED, Optional.of(key), Optional.empty(), List.of(), List.of()));
  }

  boolean selectsTypeMember(TreePath memberPath, TreePath qualifierPath) {
    Objects.requireNonNull(memberPath, "memberPath");
    Objects.requireNonNull(qualifierPath, "qualifierPath");
    var selectedType = compilerType(memberPath);
    var selected = trees.getElement(memberPath);
    if (!(selectedType instanceof DeclaredType) || !(selected instanceof TypeElement member)
        || !selected.equals(types.asElement(selectedType))) {
      return false;
    }
    var qualifierType = compilerType(qualifierPath);
    return qualifierType != null && isTypeMemberOf(qualifierType, member);
  }

  private boolean isTypeMemberOf(TypeMirror qualifierType, TypeElement member) {
    return switch (qualifierType) {
      case DeclaredType declared -> {
        try {
          yield member.equals(types.asElement(types.asMemberOf(declared, member)));
        } catch (IllegalArgumentException invalidMember) {
          yield false;
        }
      }
      case TypeVariable variable -> isTypeMemberOf(variable.getUpperBound(), member);
      case IntersectionType intersection -> intersection.getBounds().stream().anyMatch(bound -> isTypeMemberOf(bound, member));
      default -> false;
    };
  }

  Optional<JavaTypeReference> typeReference(TypeMirror type) {
    return projectTypeReference(type, false);
  }

  JavaTypeAttribution attributedType(TreePath path) {
    var type = compilerType(Objects.requireNonNull(path, PATH_ARGUMENT));
    return JavaTypeAttribution.fromKind(type == null ? null : type.getKind());
  }

  Optional<InvocationSemantics> invocationSemantics(TreePath invocationPath, ExecutableElement executable, InvocationForm form) {
    Objects.requireNonNull(invocationPath, "invocationPath");
    Objects.requireNonNull(executable, EXECUTABLE_ARGUMENT);
    Objects.requireNonNull(form, "form");
    var qualification = qualifyingType(invocationPath, executable, form);
    if (qualification.isEmpty()) {
      return Optional.empty();
    }
    var mode = invocationMode(executable, form, qualification.orElseThrow().interfaceType());
    return Optional.of(new InvocationSemantics(mode, qualification.orElseThrow().reference(), form));
  }

  boolean isStatic(Element element) {
    return Objects.requireNonNull(element, ELEMENT_ARGUMENT).getModifiers().contains(Modifier.STATIC);
  }

  boolean isTypeOrPackage(TreePath path) {
    var element = trees.getElement(Objects.requireNonNull(path, PATH_ARGUMENT));
    return element != null && switch (element.getKind()) {
      case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE, PACKAGE -> true;
      default -> false;
    };
  }

  boolean isEnumConstant(TreePath path) {
    var element = trees.getElement(Objects.requireNonNull(path, PATH_ARGUMENT));
    return element != null && element.getKind() == ElementKind.ENUM_CONSTANT;
  }

  boolean isArrayLength(MemberSelectTree tree, TreePath path) {
    Objects.requireNonNull(tree, "tree");
    Objects.requireNonNull(path, PATH_ARGUMENT);
    if (!tree.getIdentifier().contentEquals("length")) {
      return false;
    }
    var expressionType = trees.getTypeMirror(new TreePath(path, tree.getExpression()));
    return expressionType != null && expressionType.getKind() == TypeKind.ARRAY;
  }

  private Optional<QualifyingType> qualifyingType(TreePath invocationPath, ExecutableElement executable, InvocationForm form) {
    if (isDeclaredByObject(executable) && executable.getEnclosingElement() instanceof TypeElement objectType) {
      return qualifyingType(objectType.asType());
    }
    return switch (form) {
      case STATIC_UNQUALIFIED, INSTANCE_IMPLICIT -> simpleMethodQualifyingType(invocationPath, executable);
      case STATIC_TYPE, STATIC_EXPRESSION, INSTANCE_EXPRESSION, INSTANCE_THIS, INSTANCE_QUALIFIED_THIS ->
        invocationQualifierType(invocationPath);
      case SUPER, SUPER_CONSTRUCTOR, QUALIFIED_SUPER_CONSTRUCTOR -> directSuperclassType(invocationPath);
      case QUALIFIED_SUPER -> qualifiedSuperType(invocationPath);
      case THIS_CONSTRUCTOR -> enclosingType(invocationPath).flatMap(type -> qualifyingType(type.asType()));
      case OBJECT_CREATION, QUALIFIED_OBJECT_CREATION ->
        executable.getEnclosingElement() instanceof TypeElement owner ? qualifyingType(owner.asType()) : Optional.empty();
    };
  }

  private Optional<QualifyingType> simpleMethodQualifyingType(TreePath invocationPath, ExecutableElement executable) {
    for (var path = invocationPath; path != null; path = path.getParentPath()) {
      if (!(path.getLeaf() instanceof ClassTree)) {
        continue;
      }
      var element = trees.getElement(path);
      if (element instanceof TypeElement type && elements.getAllMembers(type).stream().anyMatch(executable::equals)) {
        return qualifyingType(type.asType());
      }
    }
    return executable.getEnclosingElement() instanceof TypeElement owner ? qualifyingType(owner.asType()) : Optional.empty();
  }

  private Optional<QualifyingType> invocationQualifierType(TreePath invocationPath) {
    if (!(invocationPath.getLeaf() instanceof MethodInvocationTree invocation)
        || !(invocation.getMethodSelect() instanceof MemberSelectTree selection)) {
      return Optional.empty();
    }
    var selectorPath = new TreePath(invocationPath, selection);
    var qualifierPath = new TreePath(selectorPath, selection.getExpression());
    var type = compilerType(qualifierPath);
    if (type instanceof IntersectionType intersection) {
      type = intersection.getBounds().getFirst();
    }
    return qualifyingType(type);
  }

  private Optional<QualifyingType> directSuperclassType(TreePath invocationPath) {
    return enclosingType(invocationPath).flatMap(type -> qualifyingType(type.getSuperclass()));
  }

  private Optional<QualifyingType> qualifiedSuperType(TreePath invocationPath) {
    if (!(invocationPath.getLeaf() instanceof MethodInvocationTree invocation)
        || !(invocation.getMethodSelect() instanceof MemberSelectTree selection)
        || !(selection.getExpression() instanceof MemberSelectTree qualifiedSuper)) {
      return Optional.empty();
    }
    var selectorPath = new TreePath(invocationPath, selection);
    var qualifiedSuperPath = new TreePath(selectorPath, qualifiedSuper);
    var typeNamePath = new TreePath(qualifiedSuperPath, qualifiedSuper.getExpression());
    var element = trees.getElement(typeNamePath);
    if (!(element instanceof TypeElement type)) {
      return Optional.empty();
    }
    return isInterface(type.asType()) ? qualifyingType(type.asType()) : qualifyingType(type.getSuperclass());
  }

  private Optional<TypeElement> enclosingType(TreePath path) {
    for (var candidate = path; candidate != null; candidate = candidate.getParentPath()) {
      if (candidate.getLeaf() instanceof ClassTree && trees.getElement(candidate) instanceof TypeElement type) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }

  private Optional<QualifyingType> qualifyingType(TypeMirror type) {
    return projectTypeReference(type, true).map(reference -> new QualifyingType(reference, isInterface(types.erasure(type))));
  }

  private Optional<JavaTypeReference> projectTypeReference(TypeMirror type, boolean erase) {
    if (type == null || type.getKind() == TypeKind.NONE || type.getKind() == TypeKind.PACKAGE || type.getKind() == TypeKind.MODULE
        || type.getKind() == TypeKind.OTHER) {
      return Optional.empty();
    }
    var identityType = erase ? types.erasure(type) : type;
    var declaration = types.asElement(type);
    var declarationKey = declaration == null ? Optional.<String>empty() : identities.keyOf(declaration);
    return Optional.of(new JavaTypeReference(typeIdentity(identityType), declarationKey));
  }

  private static InvocationMode invocationMode(ExecutableElement executable, InvocationForm form, boolean interfaceType) {
    if (executable.getModifiers().contains(Modifier.STATIC)) {
      return InvocationMode.STATIC;
    }
    if (form == InvocationForm.SUPER || form == InvocationForm.QUALIFIED_SUPER) {
      return InvocationMode.SUPER;
    }
    if (executable.getKind() == ElementKind.CONSTRUCTOR) {
      return InvocationMode.CONSTRUCTOR;
    }
    return interfaceType ? InvocationMode.INTERFACE : InvocationMode.VIRTUAL;
  }

  private static boolean isDeclaredByObject(ExecutableElement executable) {
    return executable.getKind() == ElementKind.METHOD && executable.getEnclosingElement() instanceof TypeElement owner
        && owner.getQualifiedName().contentEquals("java.lang.Object");
  }

  private boolean isInterface(TypeMirror type) {
    var element = types.asElement(type);
    return element != null && (element.getKind() == ElementKind.INTERFACE || element.getKind() == ElementKind.ANNOTATION_TYPE);
  }

  private List<String> assignableTypeNames(TypeMirror type) {
    if (type == null) {
      return List.of();
    }
    var names = new LinkedHashSet<String>();
    var pending = new ArrayDeque<TypeMirror>();
    pending.add(type);
    while (!pending.isEmpty()) {
      var candidate = pending.removeFirst();
      if (names.add(types.erasure(candidate).toString())) {
        switch (candidate.getKind()) {
          case ARRAY, DECLARED, ERROR, TYPEVAR, INTERSECTION -> pending.addAll(types.directSupertypes(candidate));
          default -> {
          }
        }
      }
    }
    return List.copyOf(names);
  }

  JavaTypeIdentity typeIdentity(TypeMirror type) {
    return new JavaTypeIdentity(typeKind(type.getKind()), type.toString(), types.erasure(type).toString());
  }

  private static JavaTypeCategory typeKind(TypeKind kind) {
    return switch (kind) {
      case BOOLEAN -> JavaTypeCategory.BOOLEAN;
      case BYTE -> JavaTypeCategory.BYTE;
      case SHORT -> JavaTypeCategory.SHORT;
      case INT -> JavaTypeCategory.INT;
      case LONG -> JavaTypeCategory.LONG;
      case CHAR -> JavaTypeCategory.CHAR;
      case FLOAT -> JavaTypeCategory.FLOAT;
      case DOUBLE -> JavaTypeCategory.DOUBLE;
      case VOID -> JavaTypeCategory.VOID;
      case NULL -> JavaTypeCategory.NULL;
      case ARRAY -> JavaTypeCategory.ARRAY;
      case DECLARED -> JavaTypeCategory.DECLARED;
      case ERROR -> JavaTypeCategory.ERROR;
      case TYPEVAR -> JavaTypeCategory.TYPE_VARIABLE;
      case WILDCARD -> JavaTypeCategory.WILDCARD;
      case EXECUTABLE -> JavaTypeCategory.EXECUTABLE;
      case UNION -> JavaTypeCategory.UNION;
      case INTERSECTION -> JavaTypeCategory.INTERSECTION;
      case NONE, PACKAGE, MODULE, OTHER -> throw new IllegalArgumentException("A non-type compiler outcome has no type identity: " + kind);
    };
  }

  private TypeMirror compilerType(TreePath path) {
    if (path.getLeaf() instanceof VariableTree variable && path.getParentPath() != null
        && path.getParentPath().getLeaf() instanceof CatchTree) {
      return trees.getTypeMirror(new TreePath(path, variable.getType()));
    }
    return trees.getTypeMirror(path);
  }

  private List<String> typeAlternativeNames(TypeMirror type) {
    if (type == null) {
      return List.of();
    }
    if (type instanceof UnionType unionType) {
      return unionType.getAlternatives().stream().map(types::erasure).map(Object::toString).toList();
    }
    return List.of(types.erasure(type).toString());
  }

  private static Optional<String> typeName(TypeMirror type) {
    return type == null ? Optional.empty() : Optional.of(type.toString());
  }

  private static SemanticReference unresolvedReference(ReferenceKind kind) {
    return new SemanticReference(kind, ResolutionStatus.UNRESOLVED, Optional.empty(), Optional.empty(), List.of(), List.of());
  }

  private static Optional<ReferenceKind> referenceKind(ElementKind kind) {
    return switch (kind) {
      case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> Optional.of(ReferenceKind.TYPE);
      case TYPE_PARAMETER -> Optional.of(ReferenceKind.TYPE_PARAMETER);
      case CONSTRUCTOR -> Optional.of(ReferenceKind.CONSTRUCTOR);
      case METHOD -> Optional.of(ReferenceKind.METHOD);
      case FIELD -> Optional.of(ReferenceKind.FIELD);
      case PARAMETER -> Optional.of(ReferenceKind.PARAMETER);
      case LOCAL_VARIABLE, EXCEPTION_PARAMETER, RESOURCE_VARIABLE, BINDING_VARIABLE -> Optional.of(ReferenceKind.LOCAL);
      case ENUM_CONSTANT -> Optional.of(ReferenceKind.ENUM_CONSTANT);
      case RECORD_COMPONENT -> Optional.of(ReferenceKind.RECORD_COMPONENT);
      case PACKAGE -> Optional.of(ReferenceKind.PACKAGE);
      default -> Optional.empty();
    };
  }

  private record QualifyingType(JavaTypeReference reference, boolean interfaceType) {
    private QualifyingType {
      Objects.requireNonNull(reference, "reference");
    }
  }
}
