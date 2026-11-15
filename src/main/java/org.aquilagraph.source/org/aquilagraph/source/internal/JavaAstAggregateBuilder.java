package org.aquilagraph.source.internal;

import org.aquilagraph.source.api.AttributedBodyOccurrence;
import org.aquilagraph.source.api.ChildUseEvidence;
import org.aquilagraph.source.api.ConstructorEvidence;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.DeclarationTypeEvidence;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.StaticMemberEvidence;
import org.aquilagraph.source.api.StringExpressionEvidence;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;

/** Collects a single submission of copied source and execution values. */
public final class JavaAstAggregateBuilder {
  private final InputSnapshot snapshot;
  private final Map<String, SourceFile> sources = new HashMap<>();
  private final Map<String, SyntaxSourceContribution> contributions = new HashMap<>();
  private final Map<String, ExecutionRegistration> executions = new HashMap<>();
  private final Map<String, DeclarationMemberOrder> declarationMemberOrders = new HashMap<>();
  private final Map<String, WrittenSourceAssociation> writtenSourceAssociations = new HashMap<>();
  private final Map<String, DeclarationTypeEvidence> declarationTypes = new HashMap<>();
  private boolean sealed;

  /**
   * Starts aggregate admission for one captured source inventory.
   *
   * @param inputSnapshot
   *          input snapshot
   */
  public JavaAstAggregateBuilder(InputSnapshot inputSnapshot) {
    snapshot = Objects.requireNonNull(inputSnapshot, "inputSnapshot");
    for (var source : snapshot.sourceFiles()) {
      sources.put(source.sourceFileKey(), source);
    }
  }

  /**
   * Registers a copied source contribution for aggregate admission.
   *
   * @param contribution
   *          contribution
   * @return the resulting java ast aggregate builder
   */
  public JavaAstAggregateBuilder add(SyntaxSourceContribution contribution) {
    requireOpen();
    Objects.requireNonNull(contribution, "contribution");
    if (!sources.containsKey(contribution.sourceFileKey())) {
      throw new IllegalArgumentException("AST contribution source is not in the captured snapshot: " + contribution.sourceFileKey()
          + " for root node " + contribution.root().nodeId());
    }
    if (contributions.putIfAbsent(contribution.sourceFileKey(), contribution) != null) {
      throw new IllegalArgumentException("Duplicate AST source contribution: " + contribution.sourceFileKey());
    }
    return this;
  }

  /**
   * Registers a copied method body with its origin and available execution evidence.
   *
   * @param methodKey
   *          method key
   * @param root
   *          root
   * @param origin
   *          origin
   * @return the resulting java ast aggregate builder
   */
  public JavaAstAggregateBuilder addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin) {
    return addMethodBody(methodKey, root, origin, List.of(), List.of(), List.of(), List.of());
  }

  /**
   * Registers a copied method body with its origin and available execution evidence.
   *
   * @param methodKey
   *          method key
   * @param root
   *          root
   * @param origin
   *          origin
   * @param uses
   *          uses
   * @param invocations
   *          invocations
   * @param staticAccesses
   *          static accesses
   * @param concatenations
   *          concatenations
   * @return the resulting java ast aggregate builder
   */
  public JavaAstAggregateBuilder addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin, List<ChildUseEvidence> uses,
      List<InvocationEvidence> invocations, List<StaticMemberEvidence> staticAccesses, List<StringExpressionEvidence> concatenations) {
    return addMethodBody(methodKey, root, origin, uses, invocations, staticAccesses, concatenations, List.of());
  }

  /**
   * Registers a copied method body with its origin and available execution evidence.
   *
   * @param methodKey
   *          method key
   * @param root
   *          root
   * @param origin
   *          origin
   * @param uses
   *          uses
   * @param invocations
   *          invocations
   * @param staticAccesses
   *          static accesses
   * @param concatenations
   *          concatenations
   * @param constructors
   *          constructors
   * @return the resulting java ast aggregate builder
   */
  public JavaAstAggregateBuilder addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin, List<ChildUseEvidence> uses,
      List<InvocationEvidence> invocations, List<StaticMemberEvidence> staticAccesses, List<StringExpressionEvidence> concatenations,
      List<ConstructorEvidence> constructors) {
    return addMethodBody(methodKey, root, origin, uses, invocations, staticAccesses, concatenations, constructors, List.of());
  }

  /**
   * Registers a copied method body with its origin and available execution evidence.
   *
   * @param methodKey
   *          method key
   * @param root
   *          root
   * @param origin
   *          origin
   * @param uses
   *          uses
   * @param invocations
   *          invocations
   * @param staticAccesses
   *          static accesses
   * @param concatenations
   *          concatenations
   * @param constructors
   *          constructors
   * @param attributedOccurrences
   *          attributed occurrences
   * @return the resulting java ast aggregate builder
   */
  public JavaAstAggregateBuilder addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin, List<ChildUseEvidence> uses,
      List<InvocationEvidence> invocations, List<StaticMemberEvidence> staticAccesses, List<StringExpressionEvidence> concatenations,
      List<ConstructorEvidence> constructors, List<AttributedBodyOccurrence> attributedOccurrences) {
    requireOpen();
    Objects.requireNonNull(methodKey, "methodKey");
    if (methodKey.isBlank()) {
      throw new IllegalArgumentException("Method body identity cannot be blank");
    }
    Objects.requireNonNull(root, "root");
    Objects.requireNonNull(origin, "origin");
    if (root.syntaxKind() != SyntaxKind.BLOCK) {
      throw new IllegalArgumentException("A method body root must be a block");
    }
    var decisions = new ExecutionDecisionRegistration(uses, invocations, staticAccesses, concatenations, constructors);
    var facts = new SourceExecutionFacts(new ExecutionScope.Method(methodKey), root, Optional.of(origin), attributedOccurrences);
    if (executions.putIfAbsent(methodKey, new ExecutionRegistration(facts, decisions)) != null) {
      throw new IllegalArgumentException("Duplicate method body identity: " + methodKey);
    }
    return this;
  }

  /**
   * Registers the retained written member order of one declaration.
   *
   * @param order
   *          order
   * @return the resulting java ast aggregate builder
   */
  public JavaAstAggregateBuilder addDeclarationMemberOrder(DeclarationMemberOrder order) {
    requireOpen();
    Objects.requireNonNull(order, "order");
    if (!sources.containsKey(order.sourceFileKey()) || declarationMemberOrders.putIfAbsent(order.ownerTypeKey(), order) != null) {
      throw new IllegalArgumentException("Declaration member order requires a captured source and unique type owner");
    }
    return this;
  }

  /**
   * Registers a declaration execution scope and its copied decision evidence.
   *
   * @param scope
   *          scope
   * @param root
   *          root
   * @param decisions
   *          decisions
   * @param attributions
   *          attributions
   * @return the resulting java ast aggregate builder
   */
  public JavaAstAggregateBuilder addDeclarationScope(ExecutionScope scope, SyntaxNode root, ExecutionDecisionRegistration decisions,
      List<AttributedBodyOccurrence> attributions) {
    requireOpen();
    if (scope instanceof ExecutionScope.Method) {
      throw new IllegalArgumentException("A declaration registration cannot replace a method body");
    }
    if (!decisions.constructors().isEmpty()) {
      throw new IllegalArgumentException("Declaration initialization cannot own incoming constructor contexts");
    }
    var facts = new SourceExecutionFacts(scope, root, Optional.empty(), attributions);
    if (executions.putIfAbsent(scope.key(), new ExecutionRegistration(facts, decisions)) != null) {
      throw new IllegalArgumentException("Duplicate execution scope identity: " + scope.key());
    }
    return this;
  }

  /**
   * Registers a copied written-source association.
   *
   * @param association
   *          association
   * @return the resulting java ast aggregate builder
   */
  public JavaAstAggregateBuilder addWrittenSourceAssociation(WrittenSourceAssociation association) {
    requireOpen();
    Objects.requireNonNull(association, "association");
    if (!sources.containsKey(association.sourceFileKey())
        || writtenSourceAssociations.putIfAbsent(association.nodeKey(), association) != null) {
      throw new IllegalArgumentException("Written source associations require a captured source and unique actual node");
    }
    return this;
  }

  /**
   * Registers independently copied declaration type evidence.
   *
   * @param evidence
   *          evidence
   * @return the resulting java ast aggregate builder
   */
  public JavaAstAggregateBuilder addDeclarationTypeEvidence(DeclarationTypeEvidence evidence) {
    requireOpen();
    Objects.requireNonNull(evidence, "evidence");
    if (!sources.containsKey(evidence.sourceFileKey()) || declarationTypes.putIfAbsent(evidence.ownerTypeKey(), evidence) != null) {
      throw new IllegalArgumentException("Declaration type evidence requires a captured source and unique actual owner");
    }
    return this;
  }

  /**
   * Closes registration and validates a single submission before publication.
   *
   * @return immutable admitted aggregate
   */
  public JavaAstAggregate seal() {
    requireOpen();
    sealed = true;
    return AggregateAdmission.seal(snapshot, Map.copyOf(sources), Map.copyOf(contributions), Map.copyOf(executions),
        Map.copyOf(declarationMemberOrders), Map.copyOf(writtenSourceAssociations), Map.copyOf(declarationTypes));
  }

  private void requireOpen() {
    if (sealed) {
      throw new IllegalStateException("AST aggregate construction is already sealed");
    }
  }

}
