package org.aquilagraph.frontend.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.frontend.internal.JavaExecutableSyntaxProjector.Projection;
import org.aquilagraph.source.api.ChildUseEvidence;
import org.aquilagraph.source.api.CompilerInsertedInvocation;
import org.aquilagraph.source.api.ConstructorContext;
import org.aquilagraph.source.api.ConstructorEvidence;
import org.aquilagraph.source.api.EvidenceOrigin;
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.InvocationApplicability;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.SourceExecutionTraversal;
import org.aquilagraph.source.api.StaticMemberEvidence;
import org.aquilagraph.source.api.StringExpressionEvidence;
import org.aquilagraph.source.api.SyntaxKind;

/** Composes independent semantic evidence in executable traversal order. */
final class JavaBodyDecisionProducer {
  private final SemanticResolver semantics;
  private final JavaConversionEvidenceProducer conversions;
  private final JavaInvocationEvidenceProducer invocationEvidence;
  private final JavaStaticMemberEvidenceProducer staticMembers;
  private final JavaStringExpressionEvidenceProducer strings;
  private final JavaConstructorEvidenceProducer constructors;

  JavaBodyDecisionProducer(SemanticResolver semantics) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    conversions = new JavaConversionEvidenceProducer(semantics);
    invocationEvidence = new JavaInvocationEvidenceProducer(semantics, conversions);
    staticMembers = new JavaStaticMemberEvidenceProducer(semantics);
    strings = new JavaStringExpressionEvidenceProducer(semantics);
    constructors = new JavaConstructorEvidenceProducer(semantics);
  }

  TranslationResult<JavaBodyDecisions> produce(Projection projection) {
    var bindings = JavaExecutableProjectionProof.corroborate(projection, semantics);
    var scopeKey = projection.scopeKey();
    var diagnostics = new DiagnosticEmissionCollector("JavaBodyDecisionProducer", scopeKey, DiagnosticSubject.None.INSTANCE);
    var root = projection.translation().value();
    var uses = new ArrayList<ChildUseEvidence>();
    var invocations = new ArrayList<InvocationApplicability>();
    var staticAccesses = new ArrayList<StaticMemberEvidence>();
    var concatenations = new ArrayList<StringExpressionEvidence>();
    var constructorContexts = new ArrayList<ConstructorEvidence>();
    for (var consumer : SourceExecutionTraversal.nodes(root)) {
      var binding = bindings.binding(consumer);
      if (consumer.syntaxKind() == SyntaxKind.TYPE_DECLARATION) {
        continue;
      }
      if (consumer.syntaxKind() == SyntaxKind.INVOCATION || consumer.syntaxKind() == SyntaxKind.OBJECT_CREATION) {
        var context = constructors.produce(scopeKey, root, binding);
        context.ifPresent(constructorContexts::add);
        invocationEvidence.produce(scopeKey, binding, bindings, diagnostics).ifPresent(selected -> {
          uses.addAll(selected.uses());
          invocations.add(selected.applicability());
        });
      } else {
        for (var child : consumer.children()) {
          conversions.childUse(scopeKey, binding, child, bindings).ifPresent(uses::add);
        }
      }
      staticMembers.produce(scopeKey, binding).ifPresent(staticAccesses::add);
      strings.produce(scopeKey, binding, bindings).ifPresent(concatenations::add);
    }
    var selectedInvocations = new HashMap<String, InvocationApplicability>();
    invocations.forEach(value -> selectedInvocations.put(value.consumerNodeId(), value));
    var retainedInvocations = SourceExecutionTraversal.nodes(root).stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION || node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
        .map(
            node -> Optional.ofNullable(selectedInvocations.get(node.nodeId())).map(value -> InvocationEvidence.known(value, node.origin()))
                .orElseGet(() -> new InvocationEvidence(
                    EvidenceValue.missing(new EvidenceOrigin(scopeKey, node.nodeId(), InvocationEvidence.SLOT, node.origin()),
                        "Compiler applicability could not be established"))))
        .toList();
    var retainedConstructors = constructorContexts.stream().map(value -> {
      var evidence = value.corroboration();
      var known = evidence.knownValue()
          .map(
              context -> new ConstructorContext(context.superclassParameterTypes(),
                  context.insertedInvocation().map(inserted -> new CompilerInsertedInvocation(inserted.sourceFileKey(),
                      inserted.compilerPath(), inserted.reference(), inserted.semantics(), retainedInvocations.stream()
                          .filter(invocation -> invocation.consumerNodeId().equals(value.invocationNodeId())).findFirst().orElseThrow()))));
      return new ConstructorEvidence(value.scopeKey(), value.invocationNodeId(), value.ownerTypeKey(), value.selectedConstructorKey(),
          value.form(), new EvidenceValue<>(evidence.state(), known, evidence.origin(), evidence.explanation(), evidence.diagnostic()));
    }).toList();
    return new TranslationResult<>(new JavaBodyDecisions(uses, retainedInvocations, staticAccesses, concatenations, retainedConstructors),
        diagnostics.snapshot());
  }
}
