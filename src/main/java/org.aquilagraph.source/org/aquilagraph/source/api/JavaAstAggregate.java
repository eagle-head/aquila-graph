package org.aquilagraph.source.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.identity.api.InputSnapshot;

import org.aquilagraph.source.internal.AdmittedJavaAstAggregate;
/** Immutable, compiler-neutral source AST for one captured input snapshot. */
public sealed interface JavaAstAggregate permits AdmittedJavaAstAggregate {

  /**
   * Returns the captured input that defines source membership and root order.
   *
   * @return captured input snapshot
   */
  InputSnapshot snapshot();

  /**
   * Returns source roots in captured snapshot order.
   *
   * @return immutable ordered source roots
   */
  List<SyntaxNode> roots();

  /**
   * Returns occurrences in source-root order and canonical structural preorder.
   *
   * @return immutable ordered occurrences
   */
  List<SyntaxNode> nodes();

  /**
   * Returns exact occurrence keys in the same order as {@link #nodes()}.
   *
   * @return immutable ordered keys
   */
  List<String> orderedNodeKeys();

  /**
   * Returns canonical occurrence identity tuples.
   *
   * @return immutable ordered identity tuples
   */
  List<SyntaxNodeTuple> nodeTuples();

  /**
   * Returns one exact source range per occurrence in canonical node order.
   *
   * @return immutable ordered range tuples
   */
  List<SyntaxRangeTuple> rangeTuples();

  /**
   * Returns structural edges in canonical parent-preorder, role, and position order.
   *
   * @return immutable ordered structural tuples
   */
  List<SyntaxStructuralEdge> structuralEdges();

  /**
   * Returns semantic edges in total source, relation, and target order.
   *
   * @return immutable ordered semantic tuples
   */
  List<SyntaxSemanticEdge> semanticEdges();

  /**
   * Returns the identical owned occurrence, or empty when the key is absent.
   *
   * @param key
   *          occurrence key
   * @return the aggregate-owned occurrence when present
   */
  Optional<SyntaxNode> node(String key);

  /**
   * Returns a view over a body owned by this aggregate.
   *
   * @param methodKey
   *          canonical method identity
   * @return the core-facing body view, when registered
   */
  Optional<MethodSyntax> methodSyntax(String methodKey);

  /**
   * Returns how a retained executable body relates to written source.
   *
   * @param methodKey
   *          canonical method identity
   * @return source occurrence or auxiliary execution classification
   */
  Optional<MethodBodyOrigin> methodBodyOrigin(String methodKey);

  /**
   * Returns the exact method identities for retained executable bodies.
   *
   * @return immutable method identity set
   */
  Set<String> methodBodyKeys();

  /**
   * Returns every actual execution scope in canonical identity order.
   *
   * @return immutable method, field and initializer scopes
   */
  List<ExecutionScope> executionScopes();

  /**
   * Finds executable syntax and copied facts for an actual source owner.
   *
   * @param scopeKey
   *          canonical source scope identity
   * @return immutable view when registered
   */
  Optional<ExecutionSyntax> executionSyntax(String scopeKey);

  /**
   * Returns a copied compiler member order owned by the aggregate.
   *
   * @param ownerTypeKey
   *          canonical type identity
   * @return actual translated order when retained
   */
  Optional<DeclarationMemberOrder> declarationMemberOrder(String ownerTypeKey);

  /**
   * Returns actual translated member orders sorted by source and canonical owner.
   *
   * @return immutable ordered values
   */
  List<DeclarationMemberOrder> declarationMemberOrders();

  /**
   * Returns original parsed ownership of an actual source occurrence.
   *
   * @param nodeKey
   *          exact source node identity
   * @return the retained association, when supplied
   */
  Optional<WrittenSourceAssociation> writtenSourceAssociation(String nodeKey);

  /**
   * Returns executable literal runtime text without replacing written source topology. Annotation arguments, defaults and their descendants
   * are outside this operation; their literal payloads retain written spelling. A restored executable expression qualifies only at an
   * actual compiler literal anchor with its copied scalar value. Other constant-expression anchors and restricted decimal operands do not
   * independently denote compiler literals.
   *
   * @param nodeKey
   *          exact owned source occurrence
   * @return runtime text, absent outside executable literal interpretation
   */
  Optional<String> executableLiteralText(String nodeKey);

  /**
   * Returns original parsed associations in actual structural node order.
   *
   * @return immutable ordered associations
   */
  List<WrittenSourceAssociation> writtenSourceAssociations();

  /**
   * Finds copied public type components for an actual canonical declaration.
   *
   * @param ownerTypeKey
   *          canonical type identity
   * @return actual components when supplied
   */
  Optional<DeclarationTypeEvidence> declarationTypeEvidence(String ownerTypeKey);

  /**
   * Returns copied public declaration types in source and owner order.
   *
   * @return immutable ordered declaration type values
   */
  List<DeclarationTypeEvidence> declarationTypeEvidence();

  /**
   * Returns actual copied attributed addresses for one owned body.
   *
   * @param methodKey
   *          canonical containing method
   * @return immutable addresses, or empty for an unknown body or unavailable address evidence
   */
  List<AttributedBodyOccurrence> bodyAttributions(String methodKey);

}
