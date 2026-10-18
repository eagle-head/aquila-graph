package org.aquilagraph.frontend;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.DeconstructionPatternTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.VariableElement;
import org.aquilagraph.frontend.JavaAnnotationProjector.DeclarationSite;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxValue;

/** Projects ordered written attachments without owning source-copy correspondence or publication. */
final class JavaWrittenAttachmentProjector {
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory treeInventory;
  private final JavaWrittenSourceAssociations sourceAssociations;
  private final Function<String, JavaSharedAnnotations> localAnnotations;
  private final JavaAnnotationProjector annotationProjector;
  private final JavaTypeUseTranslator typeUseTranslator;

  JavaWrittenAttachmentProjector(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory,
      JavaWrittenSourceAssociations sourceAssociations, Function<String, JavaSharedAnnotations> localAnnotations) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.treeInventory = Objects.requireNonNull(treeInventory, "treeInventory");
    this.sourceAssociations = Objects.requireNonNull(sourceAssociations, "sourceAssociations");
    this.localAnnotations = Objects.requireNonNull(localAnnotations, "localAnnotations");
    annotationProjector = new JavaAnnotationProjector(semantics, sourceEvidence, treeInventory, sourceAssociations);
    typeUseTranslator = new JavaTypeUseTranslator(semantics, sourceEvidence, treeInventory, Optional.empty(), sourceAssociations);
  }

  Optional<Attachments> project(SyntaxNode original, TreePath creatingPath, SyntaxStructuralPath path, List<SyntaxChild> copiedChildren,
      CompilationUnitTree unit, SourceFile sourceFile, DiagnosticEmissionCollector diagnostics) {
    var context = new ProjectionContext(unit, sourceFile, diagnostics);
    var children = new ArrayList<>(copiedChildren);
    if (!appendAttachments(original, creatingPath, path, children, context)) {
      return Optional.empty();
    }
    var binding = original.value() instanceof SyntaxValue.LocalDeclaration && creatingPath.getLeaf() instanceof VariableTree variable
        ? Optional.of(bindingAnnotations(variable, children, context))
        : Optional.<BindingAnnotations>empty();
    return Optional.of(new Attachments(children, binding));
  }

  private BindingAnnotations bindingAnnotations(VariableTree variable, List<SyntaxChild> children, ProjectionContext context) {
    var owners = localAnnotations.apply(localHeaderKey(context, variable));
    var sharedKeys = variable.getModifiers().getAnnotations().stream()
        .flatMap(annotation -> sourceEvidence.origin(context.unit(), context.sourceFile(), annotation).stream()).map(owners::find)
        .filter(Objects::nonNull).filter(key -> children.stream().noneMatch(child -> child.node().nodeId().equals(key))).toList();
    var count = Math.toIntExact(children.stream().filter(child -> child.role() == SyntaxRole.ANNOTATION).count());
    return new BindingAnnotations(count, sharedKeys);
  }

  record Attachments(List<SyntaxChild> children, Optional<BindingAnnotations> bindingAnnotations) {
    Attachments {
      children = List.copyOf(children);
      Objects.requireNonNull(bindingAnnotations, "bindingAnnotations");
    }
  }

  record BindingAnnotations(int ownedCount, List<String> sharedKeys) {
    BindingAnnotations {
      sharedKeys = List.copyOf(sharedKeys);
    }
  }

  private boolean appendAttachments(SyntaxNode original, TreePath creatingPath, SyntaxStructuralPath path, List<SyntaxChild> children,
      ProjectionContext context) {
    var complete = true;
    switch (creatingPath.getLeaf()) {
      case NewArrayTree array -> {
        if (array.getType() != null) {
          complete = appendType(array.getType(), SyntaxRole.TYPE_OPERAND, 0, path, children, context);
        }
        var annotations = new ArrayList<AnnotationTree>();
        annotations.addAll(array.getAnnotations());
        array.getDimAnnotations().forEach(annotations::addAll);
        var projector = new JavaAnnotationProjector(semantics, sourceEvidence, treeInventory, sourceAssociations);
        for (var ordinal = 0; ordinal < annotations.size(); ordinal++) {
          var annotationPath = path.child(SyntaxRole.ANNOTATION, ordinal);
          var annotation = projector.typeUse(context.unit(), context.sourceFile(), annotations.get(ordinal), annotationPath, annotationPath,
              context.diagnostics()).value();
          if (annotation.isPresent()) {
            children.add(new SyntaxChild(SyntaxRole.ANNOTATION, ordinal, annotation.orElseThrow()));
          }
          complete &= annotation.isPresent();
        }
      }
      case NewClassTree creation -> {
        complete = appendType(creation.getIdentifier(), SyntaxRole.TYPE_OPERAND, 0, path, children, context);
        for (var index = 0; index < creation.getTypeArguments().size(); index++) {
          complete &= appendType(creation.getTypeArguments().get(index), SyntaxRole.TYPE_ARGUMENT, index, path, children, context);
        }
      }
      case MemberReferenceTree reference -> {
        var form = original.value(SyntaxValue.MemberReference.class).qualifier();
        if (form == SyntaxValue.ReferenceQualifier.TYPE || form == SyntaxValue.ReferenceQualifier.ARRAY_TYPE) {
          complete = appendType(reference.getQualifierExpression(), SyntaxRole.QUALIFIER, 0, path, children, context);
        }
        var arguments = reference.getTypeArguments();
        if (arguments != null) {
          for (var index = 0; index < arguments.size(); index++) {
            complete &= appendType(arguments.get(index), SyntaxRole.TYPE_ARGUMENT, index, path, children, context);
          }
        }
        children.sort(Comparator.comparingInt(child -> child.role() == SyntaxRole.QUALIFIER ? 0 : 1));
      }
      case MethodInvocationTree invocation -> {
        for (var index = 0; index < invocation.getTypeArguments().size(); index++) {
          complete &= appendType(invocation.getTypeArguments().get(index), SyntaxRole.TYPE_ARGUMENT, index, path, children, context);
        }
        if (original.value(SyntaxValue.Invocation.class).form() == InvocationForm.STATIC_TYPE
            && invocation.getMethodSelect() instanceof MemberSelectTree selection) {
          complete &= appendType(selection.getExpression(), SyntaxRole.QUALIFIER, 0, path, children, context);
        }
      }
      case MemberSelectTree selection -> {
        if (original.syntaxKind() == SyntaxKind.CLASS_LITERAL) {
          complete = appendType(selection.getExpression(), SyntaxRole.TYPE_OPERAND, 0, path, children, context);
        } else if ((original.syntaxKind() == SyntaxKind.FIELD_ACCESS
            && original.value(SyntaxValue.FieldAccess.class).receiver() == SyntaxValue.FieldReceiver.STATIC_TYPE)
            || original.syntaxKind() == SyntaxKind.RECEIVER) {
          complete = appendType(selection.getExpression(), SyntaxRole.QUALIFIER, 0, path, children, context);
        }
      }
      case TypeCastTree cast -> complete = appendType(cast.getType(), SyntaxRole.TYPE_OPERAND, 0, path, children, context);
      case InstanceOfTree test -> {
        if (test.getPattern() == null) {
          complete = appendType(test.getType(), SyntaxRole.TYPE_OPERAND, 0, path, children, context);
        }
      }
      case DeconstructionPatternTree pattern -> {
        complete = appendType(pattern.getDeconstructor(), SyntaxRole.TYPE_OPERAND, 0, path, children, context);
        children.sort(Comparator.comparingInt(child -> child.role() == SyntaxRole.TYPE_OPERAND ? 0 : 1));
      }
      case VariableTree variable -> {
        complete = appendLocal(variable, creatingPath, path, children, context);
      }
      default -> {
      }
    }
    return complete;
  }

  private String localHeaderKey(ProjectionContext context, VariableTree variable) {
    return context.sourceFile().sourceFileKey() + ':' + sourceEvidence.rawStart(context.unit(), variable);
  }

  private boolean appendLocal(VariableTree variable, TreePath creatingPath, SyntaxStructuralPath path, List<SyntaxChild> children,
      ProjectionContext context) {
    var owners = localAnnotations.apply(localHeaderKey(context, variable));
    var annotations = treeInventory.variableAnnotations(variable).orElse(List.of());
    var ownAnnotations = annotations.stream()
        .filter(annotation -> sourceEvidence.origin(context.unit(), context.sourceFile(), annotation).filter(owners::contains).isEmpty())
        .toList();
    var site = semantics.element(creatingPath) instanceof VariableElement element
        && (element.getKind() == ElementKind.EXCEPTION_PARAMETER || element.getKind() == ElementKind.PARAMETER)
            ? DeclarationSite.PARAMETER
            : DeclarationSite.LOCAL_VARIABLE;
    var complete = true;
    for (var index = 0; index < ownAnnotations.size(); index++) {
      var attachment = path.child(SyntaxRole.ANNOTATION, index);
      var annotation = annotationProjector
          .declaration(context.unit(), context.sourceFile(), ownAnnotations.get(index), attachment, attachment, site, context.diagnostics())
          .value();
      if (annotation.isPresent()) {
        children.add(new SyntaxChild(SyntaxRole.ANNOTATION, index, annotation.orElseThrow()));
      }
      complete &= annotation.isPresent();
    }
    children.stream().filter(child -> child.role() == SyntaxRole.ANNOTATION)
        .forEach(child -> owners.retain(child.node().origin().orElseThrow(), child.node().nodeId()));
    if (variable.getType() != null && !treeInventory.hasInferredVariableType(variable)) {
      var typePath = path.child(SyntaxRole.DECLARED_TYPE, 0);
      var translated = new JavaTypeUseTranslator(semantics, sourceEvidence, treeInventory, Optional.of(owners), sourceAssociations)
          .translateDeclaration(context.unit(), context.sourceFile(), variable.getType(), new TreePath(creatingPath, variable.getType()),
              typePath, typePath, semantics.type(creatingPath), context.diagnostics())
          .value();
      translated.ifPresent(node -> children.add(new SyntaxChild(SyntaxRole.DECLARED_TYPE, 0, node)));
      complete &= translated.isPresent();
    }
    children.sort(Comparator.comparingInt(child -> switch (child.role()) {
      case ANNOTATION -> 0;
      case DECLARED_TYPE -> 1;
      default -> 2;
    }));
    return complete;
  }

  private boolean appendType(Tree tree, SyntaxRole role, int position, SyntaxStructuralPath parentPath, List<SyntaxChild> children,
      ProjectionContext context) {
    var path = parentPath.child(role, position);
    var translated = typeUseTranslator.translate(context.unit(), context.sourceFile(), tree, path, path, context.diagnostics()).value();
    translated.ifPresent(node -> children.add(new SyntaxChild(role, position, node)));
    return translated.isPresent();
  }

  private record ProjectionContext(CompilationUnitTree unit, SourceFile sourceFile, DiagnosticEmissionCollector diagnostics) {
  }
}
