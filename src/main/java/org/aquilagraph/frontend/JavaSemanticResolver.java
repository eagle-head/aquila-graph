package org.aquilagraph.frontend;

import com.sun.source.tree.CatchTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
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
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.UnionType;
import javax.lang.model.util.Types;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SemanticReference;

final class JavaSemanticResolver {
  private final Trees trees;
  private final Types types;

  JavaSemanticResolver(Trees trees, Types types) {
    this.trees = Objects.requireNonNull(trees, "trees");
    this.types = Objects.requireNonNull(types, "types");
  }

  Element element(TreePath path) {
    return trees.getElement(Objects.requireNonNull(path, "path"));
  }

  List<SemanticReference> references(TreePath path) {
    Objects.requireNonNull(path, "path");
    var element = trees.getElement(path);
    if (element == null) {
      return unresolvedReferenceKind(path.getLeaf())
          .map(kind -> List
              .of(new SemanticReference(kind, ResolutionStatus.UNRESOLVED, Optional.empty(), Optional.empty(), List.of(), List.of())))
          .orElseGet(List::of);
    }
    var type = compilerType(path);
    var status = type != null && type.getKind() == TypeKind.ERROR ? ResolutionStatus.ERROR : ResolutionStatus.RESOLVED;
    return List.of(new SemanticReference(referenceKind(element.getKind()), status, Optional.of(symbolKey(element)),
        type == null ? Optional.empty() : Optional.of(type.toString()), type == null ? List.of() : typeAlternativeNames(type),
        type == null ? List.of() : assignableTypeNames(type)));
  }

  boolean isArrayLength(MemberSelectTree tree, TreePath path) {
    Objects.requireNonNull(tree, "tree");
    Objects.requireNonNull(path, "path");
    if (!tree.getIdentifier().contentEquals("length")) {
      return false;
    }
    var expressionType = trees.getTypeMirror(new TreePath(path, tree.getExpression()));
    return expressionType != null && expressionType.getKind() == TypeKind.ARRAY;
  }

  private List<String> assignableTypeNames(TypeMirror type) {
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

  private TypeMirror compilerType(TreePath path) {
    if (path.getLeaf() instanceof VariableTree variable && path.getParentPath() != null
        && path.getParentPath().getLeaf() instanceof CatchTree) {
      return trees.getTypeMirror(new TreePath(path, variable.getType()));
    }
    return trees.getTypeMirror(path);
  }

  private List<String> typeAlternativeNames(TypeMirror type) {
    if (type instanceof UnionType unionType) {
      return unionType.getAlternatives().stream().map(types::erasure).map(Object::toString).toList();
    }
    return List.of(types.erasure(type).toString());
  }

  private static Optional<ReferenceKind> unresolvedReferenceKind(Tree tree) {
    return switch (tree.getKind()) {
      case METHOD_INVOCATION -> Optional.of(ReferenceKind.METHOD);
      case NEW_CLASS -> Optional.of(ReferenceKind.CONSTRUCTOR);
      case MEMBER_SELECT -> Optional.of(ReferenceKind.FIELD);
      case IDENTIFIER -> Optional.of(ReferenceKind.LOCAL);
      case TYPE_CAST -> Optional.of(ReferenceKind.TYPE);
      default -> Optional.empty();
    };
  }

  private static ReferenceKind referenceKind(ElementKind kind) {
    return switch (kind) {
      case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> ReferenceKind.TYPE;
      case CONSTRUCTOR -> ReferenceKind.CONSTRUCTOR;
      case METHOD -> ReferenceKind.METHOD;
      case FIELD -> ReferenceKind.FIELD;
      case PARAMETER -> ReferenceKind.PARAMETER;
      case LOCAL_VARIABLE, EXCEPTION_PARAMETER, RESOURCE_VARIABLE, BINDING_VARIABLE -> ReferenceKind.LOCAL;
      case ENUM_CONSTANT -> ReferenceKind.ENUM_CONSTANT;
      case PACKAGE -> ReferenceKind.PACKAGE;
      default -> ReferenceKind.TYPE;
    };
  }

  private static String symbolKey(Element element) {
    var owner = element.getEnclosingElement();
    var ownerName = owner instanceof TypeElement typeElement
        ? typeElement.getQualifiedName().toString()
        : owner == null ? "" : owner.toString();
    return switch (element.getKind()) {
      case CONSTRUCTOR, METHOD -> ownerName + "#" + element.getSimpleName() + element.asType();
      default -> ownerName + "#" + element.getSimpleName() + ":" + element.asType();
    };
  }
}
