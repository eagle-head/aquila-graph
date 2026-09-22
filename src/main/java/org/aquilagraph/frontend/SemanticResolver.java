package org.aquilagraph.frontend;

import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.UnionType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.aquilagraph.model.InvocationForm;
import org.aquilagraph.model.InvocationMode;
import org.aquilagraph.model.InvocationSemantics;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.JavaTypeReference;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.syntax.SyntaxType;

final class SemanticResolver {
  private static final String PATH_ARGUMENT = "path";
  private static final String ELEMENT_ARGUMENT = "element";
  private final Trees trees;
  private final Types types;
  private final Elements elements;
  private final DeclarationIdentityRegistry identities;

  SemanticResolver(Trees trees, Types types, Elements elements, DeclarationIdentityRegistry identities) {
    this.trees = Objects.requireNonNull(trees, "trees");
    this.types = Objects.requireNonNull(types, "types");
    this.elements = Objects.requireNonNull(elements, "elements");
    this.identities = Objects.requireNonNull(identities, "identities");
  }

  Element element(TreePath path) {
    return trees.getElement(Objects.requireNonNull(path, PATH_ARGUMENT));
  }

  Optional<String> declarationKey(Element element) {
    return identities.keyOf(Objects.requireNonNull(element, ELEMENT_ARGUMENT));
  }

  String executableSignature(ExecutableElement element) {
    return identities.signatureOf(Objects.requireNonNull(element, ELEMENT_ARGUMENT));
  }

  Optional<SemanticReference> reference(TreePath path, ReferenceKind expectedKind) {
    Objects.requireNonNull(path, PATH_ARGUMENT);
    Objects.requireNonNull(expectedKind, "expectedKind");
    var element = trees.getElement(path);
    if (element == null) {
      return Optional.of(unresolvedReference(expectedKind));
    }
    var actualKind = referenceKind(element.getKind());
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
    var type = compilerType(path);
    if (type == null || type.getKind() == TypeKind.NONE || type.getKind() == TypeKind.PACKAGE || type.getKind() == TypeKind.MODULE) {
      return SyntaxType.Unresolved.INSTANCE;
    }
    if (type.getKind() == TypeKind.ERROR) {
      return SyntaxType.Erroneous.INSTANCE;
    }
    return new SyntaxType.Resolved(typeIdentity(type));
  }

  Optional<JavaTypeIdentity> resolvedType(TreePath path) {
    var syntaxType = type(path);
    return syntaxType instanceof SyntaxType.Resolved resolved ? Optional.of(resolved.identity()) : Optional.empty();
  }

  SyntaxType explicitType(TreePath path) {
    Objects.requireNonNull(path, PATH_ARGUMENT);
    var type = compilerType(path);
    if (type == null || type.getKind() == TypeKind.NONE || type.getKind() == TypeKind.PACKAGE || type.getKind() == TypeKind.MODULE) {
      return SyntaxType.Unresolved.INSTANCE;
    }
    if (type.getKind() == TypeKind.ERROR) {
      return SyntaxType.Erroneous.INSTANCE;
    }
    return new SyntaxType.Resolved(typeIdentity(type));
  }

  Optional<SemanticReference> typeReference(TreePath path) {
    Objects.requireNonNull(path, PATH_ARGUMENT);
    var element = trees.getElement(path);
    return element == null ? Optional.empty() : reference(path, ReferenceKind.TYPE);
  }

  Optional<JavaTypeReference> typeReference(TypeMirror type) {
    return projectTypeReference(type, false);
  }

  Optional<InvocationSemantics> invocationSemantics(TreePath invocationPath, ExecutableElement executable, InvocationForm form) {
    Objects.requireNonNull(invocationPath, "invocationPath");
    Objects.requireNonNull(executable, "executable");
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
    if (type == null || type.getKind() == TypeKind.NONE || type.getKind() == TypeKind.PACKAGE || type.getKind() == TypeKind.MODULE) {
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

  private JavaTypeIdentity typeIdentity(TypeMirror type) {
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
      case NONE, PACKAGE, MODULE, OTHER -> JavaTypeCategory.OTHER;
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
      case CONSTRUCTOR -> Optional.of(ReferenceKind.CONSTRUCTOR);
      case METHOD -> Optional.of(ReferenceKind.METHOD);
      case FIELD -> Optional.of(ReferenceKind.FIELD);
      case PARAMETER -> Optional.of(ReferenceKind.PARAMETER);
      case LOCAL_VARIABLE, EXCEPTION_PARAMETER, RESOURCE_VARIABLE, BINDING_VARIABLE -> Optional.of(ReferenceKind.LOCAL);
      case ENUM_CONSTANT -> Optional.of(ReferenceKind.ENUM_CONSTANT);
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
