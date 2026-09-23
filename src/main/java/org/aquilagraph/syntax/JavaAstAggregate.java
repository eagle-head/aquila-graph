package org.aquilagraph.syntax;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SourceSpan;

/** Immutable, compiler-neutral source AST for one captured input snapshot. */
public final class JavaAstAggregate {
  private final InputSnapshot capturedInput;
  private final List<SyntaxNode> sourceRoots;
  private final List<SyntaxNode> orderedOccurrences;
  private final Map<String, SyntaxNode> occurrencesByKey;
  private final List<SyntaxNodeTuple> orderedIdentities;
  private final List<SyntaxRangeTuple> orderedRanges;
  private final List<SyntaxStructuralEdge> orderedStructuralRelations;
  private final List<SyntaxSemanticEdge> orderedSemanticRelations;
  private final List<NodeFacts> orderedFacts;

  private JavaAstAggregate(InputSnapshot inputSnapshot, List<SyntaxNode> sourceRoots, List<SyntaxNode> orderedNodes,
      Map<String, SyntaxNode> ownedNodes, List<SyntaxStructuralEdge> orderedStructuralEdges,
      List<SyntaxSemanticEdge> orderedSemanticEdges) {
    capturedInput = Objects.requireNonNull(inputSnapshot, "inputSnapshot");
    this.sourceRoots = List.copyOf(sourceRoots);
    orderedOccurrences = List.copyOf(orderedNodes);
    occurrencesByKey = Map.copyOf(ownedNodes);
    orderedStructuralRelations = List.copyOf(orderedStructuralEdges);
    orderedSemanticRelations = List.copyOf(orderedSemanticEdges);
    orderedIdentities = List.copyOf(orderedOccurrences.stream().map(SyntaxNode::nodeTuple).toList());
    orderedRanges = List.copyOf(orderedOccurrences.stream().map(node -> node.rangeTuple().orElseThrow()).toList());
    orderedFacts = orderedOccurrences.stream().map(NodeFacts::from).toList();
  }

  /**
   * Starts one controlled construction for the captured source set.
   *
   * @param snapshot
   *          immutable captured input
   * @return mutable construction boundary
   */
  public static Builder builder(InputSnapshot snapshot) {
    return new Builder(snapshot);
  }

  /** The only public construction boundary for one captured source set. */
  public static final class Builder {
    private final JavaAstAggregateBuilder validator;

    private Builder(InputSnapshot snapshot) {
      validator = new JavaAstAggregateBuilder(snapshot);
    }

    /**
     * Adds one captured source contribution. Arrival order has no effect on the sealed value.
     *
     * @param contribution
     *          source occurrences and typed relations
     * @return this builder
     */
    public Builder add(SyntaxSourceContribution contribution) {
      validator.add(contribution);
      return this;
    }

    /**
     * Validates all source, graph, identity, and ordering invariants before publication.
     *
     * @return immutable aggregate with canonical queries
     * @throws IllegalArgumentException
     *           if any contribution violates an aggregate invariant
     */
    public JavaAstAggregate seal() {
      var validated = validator.validate();
      return new JavaAstAggregate(validated.snapshot(), validated.roots(), validated.nodes(), validated.ownedNodes(),
          validated.structuralEdges(), validated.semanticEdges());
    }
  }

  /**
   * Returns the captured input that defines source membership and root order.
   *
   * @return captured input snapshot
   */
  public InputSnapshot snapshot() {
    return capturedInput;
  }

  /**
   * Returns source roots in captured snapshot order.
   *
   * @return immutable ordered source roots
   */
  public List<SyntaxNode> roots() {
    return sourceRoots;
  }

  /**
   * Returns occurrences in source-root order and canonical structural preorder.
   *
   * @return immutable ordered occurrences
   */
  public List<SyntaxNode> nodes() {
    return orderedOccurrences;
  }

  /**
   * Returns exact occurrence keys in the same order as {@link #nodes()}.
   *
   * @return immutable ordered keys
   */
  public List<String> orderedNodeKeys() {
    return orderedIdentities.stream().map(SyntaxNodeTuple::nodeKey).toList();
  }

  /**
   * Returns canonical occurrence identity tuples.
   *
   * @return immutable ordered identity tuples
   */
  public List<SyntaxNodeTuple> nodeTuples() {
    return orderedIdentities;
  }

  /**
   * Returns one exact source range per occurrence in canonical node order.
   *
   * @return immutable ordered range tuples
   */
  public List<SyntaxRangeTuple> rangeTuples() {
    return orderedRanges;
  }

  /**
   * Returns structural edges in canonical parent-preorder, role, and position order.
   *
   * @return immutable ordered structural tuples
   */
  public List<SyntaxStructuralEdge> structuralEdges() {
    return orderedStructuralRelations;
  }

  /**
   * Returns semantic edges in total source, relation, and target order.
   *
   * @return immutable ordered semantic tuples
   */
  public List<SyntaxSemanticEdge> semanticEdges() {
    return orderedSemanticRelations;
  }

  /**
   * Returns the identical owned occurrence, or empty when the key is absent.
   *
   * @param key
   *          occurrence key
   * @return the aggregate-owned occurrence when present
   */
  public Optional<SyntaxNode> node(String key) {
    return Optional.ofNullable(occurrencesByKey.get(Objects.requireNonNull(key, "key")));
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof JavaAstAggregate that && capturedInput.equals(that.capturedInput)
        && orderedFacts.equals(that.orderedFacts) && orderedStructuralRelations.equals(that.orderedStructuralRelations)
        && orderedSemanticRelations.equals(that.orderedSemanticRelations));
  }

  @Override
  public int hashCode() {
    return Objects.hash(capturedInput, orderedFacts, orderedStructuralRelations, orderedSemanticRelations);
  }

  private record NodeFacts(SyntaxNodeTuple identity, SourceSpan range, SyntaxKind syntaxKind, SyntaxType type, SyntaxValue value,
      Optional<SemanticReference> reference) {
    private static NodeFacts from(SyntaxNode node) {
      return new NodeFacts(node.nodeTuple(), node.origin().orElseThrow(), node.syntaxKind(), node.type(), node.value(),
          node.semanticReference());
    }
  }
}
