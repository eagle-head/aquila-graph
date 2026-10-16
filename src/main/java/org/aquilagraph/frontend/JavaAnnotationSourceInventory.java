package org.aquilagraph.frontend;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.WildcardTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxOperator;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;

/** Immutable written obligations captured before attribution, independent of translation and coverage output. */
record JavaAnnotationSourceInventory(List<Occurrence> annotations, List<Declaration> declarations) {
  JavaAnnotationSourceInventory {
    annotations = List.copyOf(annotations);
    declarations = List.copyOf(declarations);
  }

  static JavaAnnotationSourceInventory capture(ParsedJavaSource parsed, SnapshotSourceCatalog sources) {
    var annotations = new ArrayList<Occurrence>();
    var declarations = new ArrayList<Declaration>();
    var physicalAnnotations = new HashSet<SourceSpan>();
    for (var unit : parsed.units()) {
      captureUnit(parsed, sources, unit, annotations, declarations, physicalAnnotations);
    }
    return new JavaAnnotationSourceInventory(annotations, declarations);
  }

  private static void captureUnit(ParsedJavaSource parsed, SnapshotSourceCatalog sources, CompilationUnitTree unit,
      List<Occurrence> annotations, List<Declaration> declarations, Set<SourceSpan> physicalAnnotations) {
    var source = sources.find(unit).orElseThrow();
    var capture = new Capture(source, parsed.sourceEvidence(), parsed.treeInventory());
    new TreePathScanner<Void, Void>() {
      private int declarationOrdinal;
      @Override
      public Void visitAnnotation(AnnotationTree annotation, Void unused) {
        var path = getCurrentPath();
        var written = capture.annotation(path, annotation);
        if (written.range().isEmpty() || physicalAnnotations.add(written.range().orElseThrow())) {
          var owner = path.getParentPath();
          while (owner != null && owner.getLeaf() instanceof ModifiersTree) {
            owner = owner.getParentPath();
          }
          var ownerPath = Optional.ofNullable(owner);
          annotations.add(new Occurrence(source, written, ownerPath.flatMap(capture::range), capture.ancestors(ownerPath),
              ownerPath.map(parent -> parsed.treeInventory().ownerAnnotations(parent.getLeaf()).indexOf(annotation)).orElse(0)));
        }
        return super.visitAnnotation(annotation, null);
      }

      @Override
      public Void visitClass(ClassTree tree, Void unused) {
        if (header(getCurrentPath())) {
          declarations.add(new Declaration(source, getCurrentPath(), capture.range(getCurrentPath()),
              List.of(JavaDeclarationPropertyCoverage.TYPE_ELEMENT_ANNOTATION_MIRRORS), Optional.empty(), declarationOrdinal));
          declarationOrdinal++;
        }
        return super.visitClass(tree, null);
      }

      @Override
      public Void visitMethod(MethodTree tree, Void unused) {
        if (header(getCurrentPath())) {
          var defaultTree = Optional.ofNullable(tree.getDefaultValue()).map(value -> capture.value(new TreePath(getCurrentPath(), value)));
          declarations.add(new Declaration(source, getCurrentPath(), capture.range(getCurrentPath()),
              List.of(JavaDeclarationPropertyCoverage.EXECUTABLE_ELEMENT_ANNOTATION_MIRRORS,
                  JavaDeclarationPropertyCoverage.METHOD_DEFAULT_VALUE, JavaDeclarationPropertyCoverage.EXECUTABLE_ELEMENT_DEFAULT_VALUE),
              defaultTree, declarationOrdinal));
          declarationOrdinal++;
        }
        return super.visitMethod(tree, null);
      }
    }.scan(unit, null);
  }

  private static boolean header(TreePath path) {
    for (var parent = path.getParentPath(); parent != null; parent = parent.getParentPath()) {
      if (parent.getLeaf() instanceof BlockTree || parent.getLeaf() instanceof NewClassTree) {
        return false;
      }
    }
    return true;
  }

  record Occurrence(SourceFile source, WrittenTree tree, Optional<SourceSpan> ownerRange, List<Ancestor> ancestors, int ordinal) {
    Occurrence {
      ancestors = List.copyOf(ancestors);
    }
  }

  record Ancestor(JavaSourceKind kind, Optional<SourceSpan> range) {
  }

  record Declaration(SourceFile source, TreePath path, Optional<SourceSpan> range, List<String> properties,
      Optional<WrittenTree> defaultTree, int ordinal) {
    Declaration {
      properties = List.copyOf(properties);
    }
  }

  record Branch(SyntaxRole role, int ordinal, WrittenTree tree) {
  }

  record WrittenTree(TreePath path, JavaSourceKind sourceKind, SyntaxKind syntaxKind, Optional<SourceSpan> range,
      JavaSourceEvidence.Position position, SyntaxValue value, List<Branch> children) {
    WrittenTree {
      children = List.copyOf(children);
    }
  }

  private record Capture(SourceFile source, JavaSourceEvidence evidence, ParsedTreeInventory inventory) {
    List<Ancestor> ancestors(Optional<TreePath> owner) {
      var result = new ArrayList<Ancestor>();
      for (var path = owner.orElse(null); path != null; path = path.getParentPath()) {
        var parent = path.getParentPath();
        var methodSelection = path.getLeaf() instanceof MemberSelectTree && parent != null
            && parent.getLeaf() instanceof MethodInvocationTree invocation
            && JavaProjectionIdentity.sameInstance(invocation.getMethodSelect(), path.getLeaf());
        var implicitEnvelope = JavaEnumConstantSyntax.isImplicitEnvelope(path, evidence);
        if (!(path.getLeaf() instanceof ModifiersTree) && !(path.getLeaf() instanceof CompilationUnitTree) && !methodSelection
            && !implicitEnvelope) {
          var kind = JavaSourceKind.valueOf(path.getLeaf().getKind().name());
          result.add(new Ancestor(kind == JavaSourceKind.TYPE_ANNOTATION ? JavaSourceKind.ANNOTATION : kind, range(path)));
        }
      }
      return List.copyOf(result);
    }
    Optional<SourceSpan> range(TreePath path) {
      return evidence.origin(path.getCompilationUnit(), source, path.getLeaf());
    }

    WrittenTree tree(TreePath path, SyntaxKind kind, SyntaxValue value, List<Branch> children) {
      return new WrittenTree(path, JavaSourceKind.valueOf(path.getLeaf().getKind().name()), kind, range(path),
          evidence.position(path.getCompilationUnit(), source, path.getLeaf()), value, children);
    }

    WrittenTree annotation(TreePath path, AnnotationTree annotation) {
      var children = new ArrayList<Branch>();
      var arguments = inventory.annotationArguments(annotation).orElseThrow();
      for (var ordinal = 0; ordinal < arguments.size(); ordinal++) {
        var argument = arguments.get(ordinal);
        children.add(new Branch(SyntaxRole.ANNOTATION_ARGUMENT, ordinal, argument(path, argument)));
      }
      return tree(path, SyntaxKind.ANNOTATION,
          inventory.annotation(annotation).<SyntaxValue>map(value -> value).orElse(SyntaxValue.None.INSTANCE), children);
    }

    private WrittenTree argument(TreePath path, ParsedTreeInventory.WrittenAnnotationArgument written) {
      var argumentPath = new TreePath(path, written.argument());
      var children = new ArrayList<Branch>();
      written.name().ifPresent(name -> children.add(new Branch(SyntaxRole.ANNOTATION_ELEMENT_NAME, 0, tree(new TreePath(argumentPath, name),
          SyntaxKind.ANNOTATION_ELEMENT_NAME, new SyntaxValue.SourceSymbol(name.getName().toString()), List.of()))));
      children.add(new Branch(SyntaxRole.VALUE, 0, value(written.valuePath())));
      return tree(argumentPath, SyntaxKind.ANNOTATION_ARGUMENT, SyntaxValue.None.INSTANCE, children);
    }

    WrittenTree value(TreePath path) {
      var children = new ArrayList<Branch>();
      if (path.getLeaf() instanceof AnnotationTree annotation) {
        return annotation(path, annotation);
      }
      var kind = switch (path.getLeaf()) {
        case NewArrayTree array -> {
          var items = array.getInitializers();
          if (items != null) {
            for (var ordinal = 0; ordinal < items.size(); ordinal++) {
              children.add(new Branch(SyntaxRole.VALUE, ordinal, value(new TreePath(path, items.get(ordinal)))));
            }
          }
          yield SyntaxKind.ANNOTATION_ARRAY;
        }
        case LiteralTree _ -> SyntaxKind.LITERAL;
        case IdentifierTree _ -> SyntaxKind.ANNOTATION_NAME_REFERENCE;
        case MemberSelectTree selection -> {
          var literal = selection.getIdentifier().contentEquals("class");
          children.add(
              new Branch(literal ? SyntaxRole.TYPE_OPERAND : SyntaxRole.QUALIFIER, 0, type(new TreePath(path, selection.getExpression()))));
          yield literal ? SyntaxKind.ANNOTATION_CLASS_LITERAL : SyntaxKind.ANNOTATION_NAME_REFERENCE;
        }
        case ParenthesizedTree parentheses -> {
          children.add(new Branch(SyntaxRole.EXPRESSION, 0, value(new TreePath(path, parentheses.getExpression()))));
          yield SyntaxKind.PARENTHESIZED;
        }
        case TypeCastTree cast -> {
          children.add(new Branch(SyntaxRole.TYPE_OPERAND, 0, type(new TreePath(path, cast.getType()))));
          children.add(new Branch(SyntaxRole.EXPRESSION, 0, value(new TreePath(path, cast.getExpression()))));
          yield SyntaxKind.ANNOTATION_CAST;
        }
        case UnaryTree unary -> {
          children.add(new Branch(SyntaxRole.OPERAND, 0, value(new TreePath(path, unary.getExpression()))));
          yield SyntaxKind.UNARY;
        }
        case BinaryTree binary -> {
          children.add(new Branch(SyntaxRole.LEFT_OPERAND, 0, value(new TreePath(path, binary.getLeftOperand()))));
          children.add(new Branch(SyntaxRole.RIGHT_OPERAND, 0, value(new TreePath(path, binary.getRightOperand()))));
          yield switch (binary.getKind()) {
            case CONDITIONAL_AND -> SyntaxKind.CONDITIONAL_AND;
            case CONDITIONAL_OR -> SyntaxKind.CONDITIONAL_OR;
            default -> SyntaxKind.BINARY;
          };
        }
        case ConditionalExpressionTree conditional -> {
          children.add(new Branch(SyntaxRole.CONDITION, 0, value(new TreePath(path, conditional.getCondition()))));
          children.add(new Branch(SyntaxRole.TRUE_EXPRESSION, 0, value(new TreePath(path, conditional.getTrueExpression()))));
          children.add(new Branch(SyntaxRole.FALSE_EXPRESSION, 0, value(new TreePath(path, conditional.getFalseExpression()))));
          yield SyntaxKind.CONDITIONAL_EXPRESSION;
        }
        default -> SyntaxKind.UNSUPPORTED;
      };
      return tree(path, kind, sourceValue(path), children);
    }

    private SyntaxValue sourceValue(TreePath path) {
      return switch (path.getLeaf()) {
        case IdentifierTree identifier -> new SyntaxValue.SourceSymbol(identifier.getName().toString());
        case MemberSelectTree selection when !selection.getIdentifier().contentEquals("class") ->
          new SyntaxValue.SourceSymbol(selection.getIdentifier().toString());
        case LiteralTree _ -> range(path)
            .<SyntaxValue>map(span -> new SyntaxValue.Literal(
                source.content().substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive()))))
            .orElse(SyntaxValue.None.INSTANCE);
        case UnaryTree _,BinaryTree _ -> new SyntaxValue.Operator(SyntaxOperator.valueOf(path.getLeaf().getKind().name()));
        default -> SyntaxValue.None.INSTANCE;
      };
    }

    WrittenTree type(TreePath path) {
      var children = new ArrayList<Branch>();
      switch (path.getLeaf()) {
        case MemberSelectTree selection ->
          children.add(new Branch(SyntaxRole.TYPE_QUALIFIER, 0, type(new TreePath(path, selection.getExpression()))));
        case ArrayTypeTree array -> children.add(new Branch(SyntaxRole.COMPONENT_TYPE, 0, type(new TreePath(path, array.getType()))));
        case ParameterizedTypeTree parameterized -> {
          children.add(new Branch(SyntaxRole.TYPE_NAME, 0, type(new TreePath(path, parameterized.getType()))));
          for (var ordinal = 0; ordinal < parameterized.getTypeArguments().size(); ordinal++) {
            children.add(
                new Branch(SyntaxRole.TYPE_ARGUMENT, ordinal, type(new TreePath(path, parameterized.getTypeArguments().get(ordinal)))));
          }
        }
        case AnnotatedTypeTree annotated -> {
          for (var ordinal = 0; ordinal < annotated.getAnnotations().size(); ordinal++) {
            var annotation = annotated.getAnnotations().get(ordinal);
            children.add(new Branch(SyntaxRole.ANNOTATION, ordinal, annotation(new TreePath(path, annotation), annotation)));
          }
          children.add(new Branch(SyntaxRole.UNDERLYING_TYPE, 0, type(new TreePath(path, annotated.getUnderlyingType()))));
        }
        case WildcardTree wildcard when wildcard.getBound() != null ->
          children.add(new Branch(wildcard.getKind() == Tree.Kind.EXTENDS_WILDCARD ? SyntaxRole.UPPER_BOUND : SyntaxRole.LOWER_BOUND, 0,
              type(new TreePath(path, wildcard.getBound()))));
        default -> {
          // Leaf type syntax has no structural obligation.
        }
      }
      var value = path.getLeaf() instanceof PrimitiveTypeTree primitive
          ? new SyntaxValue.PrimitiveType(JavaTypeCategory.valueOf(primitive.getPrimitiveTypeKind().name()))
          : sourceValue(path);
      return tree(path, SyntaxKind.TYPE_USE, value, children);
    }
  }
}
