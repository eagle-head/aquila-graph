package org.aquilagraph.frontend.internal;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.aquilagraph.source.api.ConstructorEvidence;
import org.aquilagraph.source.api.ConstructorContext;
import org.aquilagraph.source.api.CompilerInsertedInvocation;
import org.aquilagraph.source.api.InvocationApplicability;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.StaticMemberEvidence;
import org.aquilagraph.source.api.StringExpressionEvidence;
import org.aquilagraph.source.api.ConversionEvidence;
import org.aquilagraph.source.api.ChildUseEvidence;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.EvidenceOrigin;
import org.aquilagraph.source.api.EvidenceValue;

/** Copied selected facts for exactly one independently correlated body. */
record JavaBodyDecisions(List<ChildUseEvidence> uses, List<InvocationEvidence> invocations, List<StaticMemberEvidence> staticAccesses,
    List<StringExpressionEvidence> concatenations, List<ConstructorEvidence> constructors) {
  JavaBodyDecisions(List<ChildUseEvidence> uses, List<InvocationEvidence> invocations, List<StaticMemberEvidence> staticAccesses,
      List<StringExpressionEvidence> concatenations) {
    this(uses, invocations, staticAccesses, concatenations, List.of());
  }

  JavaBodyDecisions {
    uses = List.copyOf(uses);
    invocations = List.copyOf(invocations);
    staticAccesses = List.copyOf(staticAccesses);
    concatenations = List.copyOf(concatenations);
    constructors = List.copyOf(constructors);
  }

  JavaBodyDecisions rebind(Map<String, SyntaxNode> copiedNodes, SyntaxNode root) {
    return new JavaBodyDecisions(
        uses.stream().filter(value -> copiedNodes.containsKey(value.consumerNodeId()))
            .map(value -> rebindUse(value, copied(copiedNodes, value.consumerNodeId()))).toList(),
        invocations.stream().filter(value -> copiedNodes.containsKey(value.consumerNodeId()))
            .map(value -> rebindInvocation(value, copied(copiedNodes, value.consumerNodeId()))).toList(),
        staticAccesses.stream().filter(value -> copiedNodes.containsKey(value.accessNodeId()))
            .map(value -> rebindStatic(value, copied(copiedNodes, value.accessNodeId()))).toList(),
        concatenations.stream().filter(value -> copiedNodes.containsKey(value.binaryNodeId()))
            .map(value -> rebindString(value, copied(copiedNodes, value.binaryNodeId()))).toList(),
        constructors.stream().map(value -> rebindConstructor(value, copiedNodes.getOrDefault(value.invocationNodeId(), root))).toList());
  }

  private static InvocationEvidence rebindInvocation(InvocationEvidence value, SyntaxNode copied) {
    var evidence = value.applicability();
    var origin = evidence.origin();
    var known = evidence.knownValue()
        .map(classification -> new InvocationApplicability(classification.scopeKey(), copied.nodeId(), classification.mode(),
            classification.formalCount(), classification.varargArrayType(), classification.erasedArrayType(),
            classification.elementType()));
    return new InvocationEvidence(
        new EvidenceValue<>(evidence.state(), known, new EvidenceOrigin(origin.scopeKey(), copied.nodeId(), origin.slot(), copied.origin()),
            evidence.explanation(), evidence.diagnostic()));
  }

  private static ConstructorEvidence rebindConstructor(ConstructorEvidence value, SyntaxNode copied) {
    var evidence = value.corroboration();
    var known = evidence.knownValue()
        .map(context -> new ConstructorContext(context.superclassParameterTypes(),
            context.insertedInvocation().map(inserted -> new CompilerInsertedInvocation(inserted.sourceFileKey(), inserted.compilerPath(),
                inserted.reference(), inserted.semantics(), rebindInvocation(inserted.applicability(), copied)))));
    return new ConstructorEvidence(value.scopeKey(), copied.nodeId(), value.ownerTypeKey(), value.selectedConstructorKey(), value.form(),
        new EvidenceValue<>(evidence.state(), known,
            new EvidenceOrigin(value.scopeKey(), copied.nodeId(), ConstructorEvidence.SLOT, copied.origin()), evidence.explanation(),
            evidence.diagnostic()));
  }

  private static ChildUseEvidence rebindUse(ChildUseEvidence value, SyntaxNode copied) {
    var classification = value.conversion();
    var known = classification.knownValue()
        .map(fact -> new ConversionEvidence(fact.scopeKey(), copied.nodeId(), fact.role(), fact.position(), fact.sourceType(),
            fact.selectedTarget(), fact.checkedReferenceTarget(), fact.unboxedType(), fact.effectivePrimitiveType(),
            fact.preBoxPrimitiveType(), fact.boxedType()));
    return new ChildUseEvidence(value.scopeKey(), copied.nodeId(), value.role(), value.position(), value.sourceType(),
        value.selectedTarget(),
        new EvidenceValue<>(classification.state(), known,
            new EvidenceOrigin(value.scopeKey(), copied.nodeId(), classification.origin().slot(), copied.origin()),
            classification.explanation(), classification.diagnostic()),
        rebindStringSlot(value.referenceCheck(), copied));
  }

  private static StaticMemberEvidence rebindStatic(StaticMemberEvidence value, SyntaxNode copied) {
    var evidence = value.constantVariable();
    var owner = value.declaringOwner();
    return new StaticMemberEvidence(value.scopeKey(), copied.nodeId(), value.selectedMemberKey(), value.kind(),
        new EvidenceValue<>(owner.state(), owner.knownValue(),
            new EvidenceOrigin(value.scopeKey(), copied.nodeId(), StaticMemberEvidence.OWNER_SLOT, copied.origin()), owner.explanation(),
            owner.diagnostic()),
        new EvidenceValue<>(evidence.state(), evidence.knownValue(),
            new EvidenceOrigin(value.scopeKey(), copied.nodeId(), StaticMemberEvidence.CONSTANT_SLOT, copied.origin()),
            evidence.explanation(), evidence.diagnostic()));
  }

  private static StringExpressionEvidence rebindString(StringExpressionEvidence value, SyntaxNode copied) {
    return new StringExpressionEvidence(value.scopeKey(), copied.nodeId(), value.stringType(),
        rebindStringSlot(value.leftIsString(), copied), rebindStringSlot(value.rightIsString(), copied),
        rebindStringSlot(value.compileTimeConstant(), copied));
  }

  private static EvidenceValue<Boolean> rebindStringSlot(EvidenceValue<Boolean> evidence, SyntaxNode copied) {
    var origin = evidence.origin();
    return new EvidenceValue<>(evidence.state(), evidence.knownValue(),
        new EvidenceOrigin(origin.scopeKey(), copied.nodeId(), origin.slot(), copied.origin()), evidence.explanation(),
        evidence.diagnostic());
  }

  private static SyntaxNode copied(Map<String, SyntaxNode> copiedNodes, String originalKey) {
    return Objects.requireNonNull(copiedNodes.get(originalKey), "exact copied compiler occurrence");
  }
}
