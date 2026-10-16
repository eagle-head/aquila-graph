package org.aquilagraph.frontend;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import org.aquilagraph.frontend.JavaExecutableSyntaxProjector.NodeProjection;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.SyntaxNode;

final class JavaMethodBodyTranslator {
  private static final String DIAGNOSTIC_PRODUCER = "JavaMethodBodyTranslator";
  private final SemanticResolver semantics;
  private final JavaExecutableSyntaxProjector syntax;
  private final JavaExecutableProjectionContext context;

  JavaMethodBodyTranslator(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory,
      JavaExecutableProjectionContext context) {
    this.context = Objects.requireNonNull(context, "context");
    context.requireMethodBody();
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    syntax = new JavaExecutableSyntaxProjector(semantics, sourceEvidence, treeInventory, context, DIAGNOSTIC_PRODUCER, "method body");
  }

  TranslationResult<TranslatedMethodBody> translate(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, BlockTree body,
      TreePath bodyPath, boolean constructor, boolean implicitDeclaration) {
    context.requireProjection();
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(methodKey, "methodKey");
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(bodyPath, "bodyPath");
    var methodPath = bodyPath.getParentPath();
    if (!JavaProjectionIdentity.sameInstance(bodyPath.getCompilationUnit(), unit)
        || !JavaProjectionIdentity.sameInstance(bodyPath.getLeaf(), body) || methodPath == null
        || !(methodPath.getLeaf() instanceof MethodTree declaration) || !JavaProjectionIdentity.sameInstance(declaration.getBody(), body)
        || !(semantics.element(methodPath) instanceof ExecutableElement executable)
        || !semantics.declarationKey(executable).equals(Optional.of(methodKey))) {
      throw new IllegalArgumentException("A method body projection requires its attributed executable and exact body path");
    }
    var projection = syntax.project(unit, sourceFile, methodKey, body, bodyPath, JavaSyntaxStructuralPath.executionRoot(bodyPath),
        constructor, implicitDeclaration);
    var translation = projection.translation();
    var nodeProjections = projection.nodes();
    var decisions = new JavaBodyDecisionProducer(semantics).produce(methodKey, translation.value(), nodeProjections,
        translation.diagnostics());
    var bodyProjection = new BodyProjection(declaration, bodyPath, translation.value(), nodeProjections, decisions);
    return new TranslationResult<>(new TranslatedMethodBody(methodKey, translation.value(), bodyProjection), translation.emissions(),
        translation.featureOccurrences(), translation.featureObservations());
  }

  static final class BodyProjection {
    private final MethodTree capturedDeclaration;
    private final TreePath capturedBodyPath;
    private final SyntaxNode createdRoot;
    private final List<NodeProjection> retainedNodeProjections;
    private final JavaBodyDecisions selectedDecisions;

    private BodyProjection(MethodTree declaration, TreePath bodyPath, SyntaxNode producedRoot, List<NodeProjection> nodeProjections,
        JavaBodyDecisions decisions) {
      this.capturedDeclaration = declaration;
      this.capturedBodyPath = bodyPath;
      this.createdRoot = producedRoot;
      this.retainedNodeProjections = List.copyOf(nodeProjections);
      this.selectedDecisions = decisions;
    }

    MethodTree declaration() {
      return capturedDeclaration;
    }

    TreePath bodyPath() {
      return capturedBodyPath;
    }

    SyntaxNode producedRoot() {
      return createdRoot;
    }

    List<NodeProjection> nodeProjections() {
      return retainedNodeProjections;
    }

    JavaBodyDecisions decisions() {
      return selectedDecisions;
    }
  }

}
