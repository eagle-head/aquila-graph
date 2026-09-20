package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;

final class JavaTreeSemantics {
  private final Trees trees;
  private final SourcePositions positions;

  JavaTreeSemantics(Trees trees) {
    this.trees = Objects.requireNonNull(trees, "trees");
    positions = trees.getSourcePositions();
  }

  Element element(TreePath path) {
    return trees.getElement(path);
  }

  List<SourceSpan> sourceOrigins(CompilationUnitTree unit, SourceFile sourceFile, Tree tree) {
    var start = positions.getStartPosition(unit, tree);
    var end = positions.getEndPosition(unit, tree);
    if (start == Diagnostic.NOPOS || end == Diagnostic.NOPOS || end < start) {
      return List.of();
    }
    return List.of(new SourceSpan(sourceFile.sourceFileKey(), start, end));
  }

  List<SemanticReference> semanticReferences(TreePath path) {
    var element = trees.getElement(path);
    if (element == null) {
      return unresolvedReferenceKind(path.getLeaf())
          .map(kind -> List.of(new SemanticReference(kind, ResolutionStatus.UNRESOLVED, Optional.empty(), Optional.empty())))
          .orElseGet(List::of);
    }
    var type = trees.getTypeMirror(path);
    var status = type != null && type.getKind() == TypeKind.ERROR ? ResolutionStatus.ERROR : ResolutionStatus.RESOLVED;
    return List.of(new SemanticReference(referenceKind(element.getKind()), status, Optional.of(symbolKey(element)),
        type == null ? Optional.empty() : Optional.of(type.toString())));
  }

  boolean isArrayLength(MemberSelectTree tree, TreePath path) {
    if (!tree.getIdentifier().contentEquals("length")) {
      return false;
    }
    var expressionType = trees.getTypeMirror(new TreePath(path, tree.getExpression()));
    return expressionType != null && expressionType.getKind() == TypeKind.ARRAY;
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
