package org.aquilagraph.source.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.api.SyntaxValue.ExecutableDeclaration.ExecutableKind;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationApplication;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationTypeLocation;
import org.aquilagraph.source.api.SyntaxSemanticTarget.Declaration;
import org.aquilagraph.source.api.SyntaxSemanticTarget.SourceOccurrence;

/** Package-local validator for one captured source AST. */
final class JavaAstAggregateBuilder {
  private static final Comparator<SyntaxStructuralEdge> CHILD_ORDER = Comparator
      .comparing((SyntaxStructuralEdge edge) -> edge.role().stableId()).thenComparingInt(SyntaxStructuralEdge::position)
      .thenComparing(SyntaxStructuralEdge::childKey);
  private static final Comparator<SyntaxSemanticEdge> SEMANTIC_ORDER = Comparator.comparing(SyntaxSemanticEdge::sourceKey)
      .thenComparing(edge -> edge.relation().stableId()).thenComparing(JavaAstAggregateBuilder::compareTargets);

  private final InputSnapshot snapshot;
  private final Map<String, SourceFile> sources = new HashMap<>();
  private final Map<String, SyntaxSourceContribution> contributions = new HashMap<>();
  private final Map<String, SourceExecutionFacts> executionScopes = new HashMap<>();
  private final Map<String, ExecutionDecisionRegistration> executionDecisions = new HashMap<>();
  private final Map<String, DeclarationMemberOrder> declarationMemberOrders = new HashMap<>();
  private final Map<String, WrittenSourceAssociation> writtenSourceAssociations = new HashMap<>();
  private final Map<String, DeclarationTypeEvidence> declarationTypes = new HashMap<>();
  private boolean sealed;

  JavaAstAggregateBuilder(InputSnapshot inputSnapshot) {
    snapshot = Objects.requireNonNull(inputSnapshot, "inputSnapshot");
    for (var source : snapshot.sourceFiles()) {
      sources.put(source.sourceFileKey(), source);
    }
  }

  JavaAstAggregateBuilder add(SyntaxSourceContribution contribution) {
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

  JavaAstAggregateBuilder addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin) {
    return addMethodBody(methodKey, root, origin, List.of(), List.of(), List.of(), List.of());
  }

  JavaAstAggregateBuilder addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin, List<ChildUseEvidence> uses,
      List<InvocationEvidence> invocations, List<StaticMemberEvidence> staticAccesses, List<StringExpressionEvidence> concatenations) {
    return addMethodBody(methodKey, root, origin, uses, invocations, staticAccesses, concatenations, List.of());
  }

  JavaAstAggregateBuilder addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin, List<ChildUseEvidence> uses,
      List<InvocationEvidence> invocations, List<StaticMemberEvidence> staticAccesses, List<StringExpressionEvidence> concatenations,
      List<ConstructorEvidence> constructors) {
    return addMethodBody(methodKey, root, origin, uses, invocations, staticAccesses, concatenations, constructors, List.of());
  }

  JavaAstAggregateBuilder addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin, List<ChildUseEvidence> uses,
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
    root.descendantsAndSelf();
    var decisions = new ExecutionDecisionRegistration(uses, invocations, staticAccesses, concatenations, constructors);
    var scope = new ExecutionScope.Method(methodKey);
    validateAttributions(scope, root, attributedOccurrences);
    if (executionScopes.putIfAbsent(methodKey, new SourceExecutionFacts(scope, root, Optional.of(origin), attributedOccurrences)) != null) {
      throw new IllegalArgumentException("Duplicate method body identity: " + methodKey);
    }
    executionDecisions.put(methodKey, decisions);
    return this;
  }

  JavaAstAggregateBuilder addDeclarationMemberOrder(DeclarationMemberOrder order) {
    requireOpen();
    Objects.requireNonNull(order, "order");
    if (!sources.containsKey(order.sourceFileKey()) || declarationMemberOrders.putIfAbsent(order.ownerTypeKey(), order) != null) {
      throw new IllegalArgumentException("Declaration member order requires a captured source and unique type owner");
    }
    return this;
  }

  JavaAstAggregateBuilder addDeclarationScope(ExecutionScope scope, SyntaxNode root, ExecutionDecisionRegistration decisions,
      List<AttributedBodyOccurrence> attributions) {
    requireOpen();
    if (scope instanceof ExecutionScope.Method) {
      throw new IllegalArgumentException("A declaration registration cannot replace a method body");
    }
    validateAttributions(scope, root, attributions);
    if (!decisions.constructors().isEmpty()) {
      throw new IllegalArgumentException("Declaration initialization cannot own incoming constructor contexts");
    }
    if (executionScopes.putIfAbsent(scope.key(), new SourceExecutionFacts(scope, root, Optional.empty(), attributions)) != null) {
      throw new IllegalArgumentException("Duplicate execution scope identity: " + scope.key());
    }
    executionDecisions.put(scope.key(), decisions);
    return this;
  }

  private static void validateAttributions(ExecutionScope scope, SyntaxNode root, List<AttributedBodyOccurrence> attributedOccurrences) {
    var nodes = root.descendantsAndSelf().stream().collect(Collectors.toMap(SyntaxNode::nodeId, node -> node));
    var retainedKeys = new HashSet<String>();
    var attributedKeys = new HashSet<SyntaxNodeKey>();
    for (var occurrence : attributedOccurrences) {
      var node = nodes.get(occurrence.nodeKey());
      if (!scope.key().equals(occurrence.scopeKey()) || node == null || !node.sourceFileKey().equals(occurrence.sourceFileKey())
          || node.sourceKind() != occurrence.sourceKind() || !retainedKeys.add(occurrence.nodeKey())
          || !attributedKeys.add(SyntaxNodeKey.from(occurrence.sourceFileKey(), occurrence.compilerPath(), occurrence.sourceKind()))) {
        throw new IllegalArgumentException("Attributed body addresses require unique exact owned occurrences and source kinds");
      }
    }
  }

  JavaAstAggregateBuilder addWrittenSourceAssociation(WrittenSourceAssociation association) {
    requireOpen();
    Objects.requireNonNull(association, "association");
    if (!sources.containsKey(association.sourceFileKey())
        || writtenSourceAssociations.putIfAbsent(association.nodeKey(), association) != null) {
      throw new IllegalArgumentException("Written source associations require a captured source and unique actual node");
    }
    return this;
  }

  JavaAstAggregateBuilder addDeclarationTypeEvidence(DeclarationTypeEvidence evidence) {
    requireOpen();
    Objects.requireNonNull(evidence, "evidence");
    if (!sources.containsKey(evidence.sourceFileKey()) || declarationTypes.putIfAbsent(evidence.ownerTypeKey(), evidence) != null) {
      throw new IllegalArgumentException("Declaration type evidence requires a captured source and unique actual owner");
    }
    return this;
  }

  JavaAstAggregate seal() {
    var validated = validate();
    return new JavaAstAggregate(validated.snapshot(), validated.roots(), validated.nodes(), validated.ownedNodes(),
        validated.structuralEdges(), validated.semanticEdges(), validated.executionScopes(), validated.declarationMemberOrders(),
        validated.executionDecisions(), validated.writtenSourceAssociations(), validated.declarationTypes());
  }

  ValidatedAggregate validate() {
    requireOpen();
    var nodes = new HashMap<String, SyntaxNode>();
    var roots = new ArrayList<SyntaxNode>();
    var structural = new ArrayList<SyntaxStructuralEdge>();
    var semantic = new ArrayList<SyntaxSemanticEdge>();
    for (var source : snapshot.sourceFiles()) {
      var contribution = contributions.get(source.sourceFileKey());
      if (contribution == null) {
        throw new IllegalArgumentException("Missing AST source root for captured source: " + source.sourceFileKey());
      }
      validateRoot(contribution);
      roots.add(contribution.root());
      for (var node : contribution.nodes()) {
        validateNode(node, contribution.sourceFileKey(), source);
        if (nodes.putIfAbsent(node.nodeId(), node) != null) {
          throw new IllegalArgumentException("Duplicate AST node key: " + node.nodeId());
        }
      }
      structural.addAll(contribution.structuralEdges());
      semantic.addAll(contribution.semanticEdges());
    }
    validateContributionEdgeOwners(nodes);
    validateWrittenSourceAssociations(nodes);
    validateRoots(roots, nodes);
    var outgoing = validateStructuralEdges(structural, nodes, roots);
    var orderedNodes = orderNodes(roots, nodes, outgoing);
    validateEmbeddedChildren(nodes, outgoing);
    validateRestrictedOperands(nodes);
    validateEmptyDeclarationOwners(nodes);
    validateAnnotationRanges(orderedNodes);
    validateSemanticEdges(semantic, nodes);
    validateMethodBodies(nodes);
    validateAttributionOwnership(nodes);
    validateDeclarationMemberOrders(orderedNodes);
    validateDeclarationTypes(orderedNodes);
    var decisionIndexes = new HashMap<String, ExecutionDecisionIndex>();
    for (var body : executionScopes.entrySet()) {
      decisionIndexes.put(body.getKey(),
          ExecutionDecisionValidation.validate(body.getValue().scope(), body.getValue().root(),
              Objects.requireNonNull(executionDecisions.get(body.getKey()), "method decision registration"), nodes,
              body.getValue().attributedOccurrences(), declarationMemberOrders));
    }
    var orderedStructural = new ArrayList<SyntaxStructuralEdge>();
    for (var node : orderedNodes) {
      orderedStructural.addAll(outgoing.getOrDefault(node.nodeId(), List.of()));
    }
    semantic.sort(SEMANTIC_ORDER);
    sealed = true;
    return new ValidatedAggregate(snapshot, roots, orderedNodes, nodes, orderedStructural, semantic, executionScopes,
        declarationMemberOrders, decisionIndexes, writtenSourceAssociations, declarationTypes);
  }

  private void validateAttributionOwnership(Map<String, SyntaxNode> nodes) {
    var declarations = new AttributedDeclarationPaths(List.copyOf(declarationMemberOrders.values()), nodes, writtenSourceAssociations);
    var addresses = new HashMap<SyntaxNodeKey, AttributedBodyOccurrence>();
    for (var facts : executionScopes.values()) {
      for (var occurrence : facts.attributedOccurrences()) {
        var key = SyntaxNodeKey.from(occurrence.sourceFileKey(), occurrence.compilerPath(), occurrence.sourceKind());
        if (addresses.putIfAbsent(key, occurrence) != null) {
          throw new IllegalArgumentException("An attributed compiler address cannot belong to multiple execution occurrences");
        }
      }
    }
    var validatedRegions = new HashSet<String>();
    executionScopes.values().stream()
        .sorted(Comparator.comparingInt(facts -> Optional.ofNullable(writtenSourceAssociations.get(facts.root().nodeId()))
            .map(association -> association.ownerPath().size()).orElse(0)))
        .forEach(facts -> validateExecutionAttributionPaths(facts, nodes, declarations, validatedRegions));
    var regions = new HashSet<SyntaxNodeKey>();
    var sourceRegions = new HashSet<String>();
    for (var association : writtenSourceAssociations.values()) {
      var normalized = association.normalizedExpression().filter(value -> value.occurrencePath().isEmpty());
      if (normalized.isEmpty()) {
        continue;
      }
      var relation = normalized.orElseThrow();
      if (!sourceRegions.add(association.sourceFileKey() + ':' + relation.region().compilerOwner())) {
        throw new IllegalArgumentException("A written normalized region requires one unique captured source owner");
      }
      if (relation.compilerAnchor() instanceof CompilerExpressionAnchor.Execution execution) {
        if (!validatedRegions.contains(association.nodeKey())
            || !regions.add(SyntaxNodeKey.from(association.sourceFileKey(), execution.path(), relation.compilerKind()))) {
          throw new IllegalArgumentException("A normalized region requires its unique independently correlated compiler anchor");
        }
      } else {
        var root = Objects.requireNonNull(nodes.get(association.nodeKey()), "annotation expression root");
        if (root.structuralPath().steps().stream()
            .noneMatch(step -> step.role() == SyntaxRole.ANNOTATION_ARGUMENT || step.role() == SyntaxRole.DEFAULT_VALUE)) {
          throw new IllegalArgumentException("An annotation source anchor requires its actual annotation-value or default context");
        }
      }
    }
  }

  private void validateExecutionAttributionPaths(SourceExecutionFacts facts, Map<String, SyntaxNode> nodes,
      AttributedDeclarationPaths declarations, Set<String> validatedRegions) {
    var occurrences = facts.attributedOccurrences();
    if (occurrences.isEmpty()) {
      return;
    }
    var root = facts.root();
    var addresses = occurrences.stream().collect(Collectors.toMap(AttributedBodyOccurrence::nodeKey, occurrence -> occurrence));
    var compilerRoot = Optional.ofNullable(addresses.get(root.nodeId()))
        .orElseThrow(() -> new IllegalArgumentException("Execution attributions require their actual scope-root address")).compilerPath();
    var constructor = nodes.values().stream()
        .anyMatch(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration declaration
            && declaration.declarationKey().equals(facts.scope().key())
            && (declaration.kind() == ExecutableKind.CONSTRUCTOR || declaration.kind() == ExecutableKind.COMPACT_CONSTRUCTOR));
    var method = facts.scope() instanceof ExecutionScope.Method;
    if (!method || facts.methodOrigin().orElseThrow() == MethodBodyOrigin.SOURCE_AST) {
      var association = writtenSourceAssociations.get(root.nodeId());
      var expected = association == null
          ? Optional.<SyntaxStructuralPath>empty()
          : declarations.compilerPath(root.sourceFileKey(), association.ownerPath());
      var required = !method || association == null || declarations.requiresExactPath(root.sourceFileKey(), association.ownerPath());
      if ((required && expected.isEmpty()) || (expected.isPresent() && !expected.orElseThrow().equals(compilerRoot))) {
        throw new IllegalArgumentException(
            "Execution attribution root must match its independently copied declaration owner and member order");
      }
    }
    var statementOffset = method ? methodStatementOffset(root, compilerRoot, addresses) : 0;
    var normalized = SourceExecutionTraversal.nodes(root).stream().map(SyntaxNode::nodeId).map(writtenSourceAssociations::get)
        .filter(Objects::nonNull).flatMap(association -> association.normalizedExpression().stream())
        .anyMatch(value -> value.compilerAnchor() instanceof CompilerExpressionAnchor.Execution);
    var writtenDelegation = root.children(SyntaxRole.STATEMENT).stream()
        .filter(statement -> statement.syntaxKind() == SyntaxKind.EXPRESSION_STATEMENT)
        .map(statement -> statement.requiredChild(SyntaxRole.EXPRESSION))
        .anyMatch(node -> node.value() instanceof SyntaxValue.Invocation invocation
            && (invocation.form() == InvocationForm.SUPER_CONSTRUCTOR || invocation.form() == InvocationForm.THIS_CONSTRUCTOR));
    if (!method) {
      if (compilerRoot.steps().getLast().role() != SyntaxRole.MEMBER) {
        throw new IllegalArgumentException("Declaration attributions require an actual compiler member-root address");
      }
    } else if (facts.methodOrigin().orElseThrow() != MethodBodyOrigin.SOURCE_AST) {
      if (statementOffset != 0 || !compilerRoot.equals(root.structuralPath())) {
        throw new IllegalArgumentException("Auxiliary method attribution paths cannot be rebased");
      }
    } else if (statementOffset < 0 || statementOffset > 1 || (statementOffset != 0 && (!constructor || writtenDelegation))
        || !compilerRoot.steps().getLast().equals(new SyntaxStructuralPath.Step(SyntaxRole.BODY, 0))) {
      throw new IllegalArgumentException("Source method attribution paths require their body and constructor correspondence");
    }
    if (normalized) {
      NormalizedExecutionPaths.validate(facts, addresses, writtenSourceAssociations, statementOffset, validatedRegions);
    } else {
      for (var node : root.descendantsAndSelf()) {
        var occurrence = addresses.get(node.nodeId());
        if (occurrence == null) {
          continue;
        }
        var relative = node.structuralPath().steps().subList(root.structuralPath().steps().size(), node.structuralPath().steps().size());
        var expected = compilerRoot;
        if (facts.scope() instanceof ExecutionScope.EnumConstant && !relative.isEmpty()
            && (relative.getFirst().role() == SyntaxRole.ARGUMENT || relative.getFirst().role() == SyntaxRole.CLASS_BODY)) {
          // Javac places constant arguments and bodies under an implicit creation wrapper.
          expected = expected.child(SyntaxRole.INITIALIZER, 0);
        }
        for (var index = 0; index < relative.size(); index++) {
          var step = relative.get(index);
          expected = expected.child(step.role(),
              step.position() + (index == 0 && step.role() == SyntaxRole.STATEMENT ? statementOffset : 0));
        }
        if (!expected.equals(occurrence.compilerPath())) {
          throw new IllegalArgumentException("Execution attribution paths must preserve exact scope-relative occurrences");
        }
      }
    }
    if (method && facts.methodOrigin().orElseThrow() == MethodBodyOrigin.SOURCE_AST) {
      var association = Objects.requireNonNull(writtenSourceAssociations.get(root.nodeId()), "parsed method body");
      declarations.retainStatementOffset(root.sourceFileKey(), association.ownerPath(), statementOffset);
    }
  }

  private static int methodStatementOffset(SyntaxNode root, SyntaxStructuralPath compilerRoot,
      Map<String, AttributedBodyOccurrence> addresses) {
    var rootDepth = root.structuralPath().steps().size();
    for (var node : root.descendantsAndSelf()) {
      var address = addresses.get(node.nodeId());
      if (address != null && node.structuralPath().steps().size() > rootDepth
          && address.compilerPath().steps().size() > compilerRoot.steps().size()) {
        var retained = node.structuralPath().steps().get(rootDepth);
        var step = address.compilerPath().steps().get(compilerRoot.steps().size());
        if (retained.role() == SyntaxRole.STATEMENT && step.role() == SyntaxRole.STATEMENT) {
          return step.position() - retained.position();
        }
      }
    }
    return 0;
  }

  private void validateDeclarationTypes(List<SyntaxNode> nodes) {
    for (var evidence : declarationTypes.values()) {
      var actual = nodes.stream().filter(node -> node.sourceFileKey().equals(evidence.sourceFileKey()))
          .anyMatch(node -> (node.value() instanceof SyntaxValue.TypeDeclaration declaration
              && declaration.declarationKey().equals(evidence.ownerTypeKey()))
              || (node.value() instanceof SyntaxValue.CompilationUnit unit && unit.form() == SyntaxValue.CompilationUnitForm.COMPACT
                  && node.children(SyntaxRole.COMPACT_MEMBER).stream()
                      .anyMatch(member -> member.value() instanceof SyntaxValue.ExecutableDeclaration declaration
                          && declaration.ownerKey().equals(evidence.ownerTypeKey()))));
      if (!actual) {
        throw new IllegalArgumentException("Declaration type evidence requires an actual source-owned canonical declaration");
      }
    }
  }

  private void validateWrittenSourceAssociations(Map<String, SyntaxNode> nodes) {
    for (var node : nodes.values()) {
      if (node.value() instanceof SyntaxValue.CompilationUnit unit && unit.range() == SyntaxValue.CompilationUnitRange.CAPTURED_SOURCE
          && !writtenSourceAssociations.containsKey(node.nodeId())) {
        throw new IllegalArgumentException("A captured source envelope requires its original compilation unit association");
      }
    }
    for (var association : writtenSourceAssociations.values()) {
      var node = nodes.get(association.nodeKey());
      if (node == null || !node.sourceFileKey().equals(association.sourceFileKey()) || !associationRangeMatches(node, association)
          || (node.sourceKind() != association.sourceKind()
              && !(node.value() instanceof SyntaxValue.Annotation && association.sourceKind() == JavaSourceKind.TYPE_ANNOTATION)
              && !normalizedAnnotationArgument(node, association))) {
        throw new IllegalArgumentException("Written source associations must bind exact actual source nodes, kinds, and ranges");
      }
      if (association.normalizedExpression().isPresent()) {
        WrittenExpressionSemantics.validate(node);
        WrittenExpressionSemantics.validateCorrespondence(node, association.normalizedExpression().orElseThrow(),
            writtenSourceAssociations);
        validateDerivedLiteral(node, association.normalizedExpression().orElseThrow());
      }
      if (association.normalizedExpression().filter(value -> value.occurrencePath().isEmpty()).isPresent()) {
        var normalized = association.normalizedExpression().orElseThrow();
        if (!normalized.region().matches(node) || !node.type().equals(normalized.compilerType())) {
          throw new IllegalArgumentException("A normalized region requires complete written topology and corroborated result type");
        }
      }
    }
  }

  private void validateDerivedLiteral(SyntaxNode node, NormalizedExpressionCorrespondence correspondence) {
    if (!(node.value() instanceof SyntaxValue.Literal literal)) {
      return;
    }
    var annotation = node.structuralPath().isAnnotationValue();
    var origin = node.origin().orElseThrow();
    var source = Objects.requireNonNull(sources.get(node.sourceFileKey()), "derived literal source");
    var expected = annotation
        ? source.content().substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive()))
        : literal.restrictedDecimal().map(DecimalMinimumOperand::magnitude)
            .orElseGet(() -> correspondence.derivedConstant().flatMap(WrittenExpressionConstants::text).orElse("null"));
    if (!literal.text().equals(expected)) {
      throw new IllegalArgumentException("A derived literal must retain its exact annotation spelling or executable scalar value");
    }
  }

  private boolean normalizedAnnotationArgument(SyntaxNode node, WrittenSourceAssociation association) {
    if (!(node.value() instanceof SyntaxValue.AnnotationArgument argument) || argument.explicitName().isPresent()) {
      return false;
    }
    var value = node.requiredChild(SyntaxRole.VALUE);
    return Optional.ofNullable(writtenSourceAssociations.get(value.nodeId())).flatMap(WrittenSourceAssociation::normalizedExpression)
        .filter(correspondence -> correspondence.occurrencePath().isEmpty()
            && correspondence.compilerAnchor() instanceof CompilerExpressionAnchor.AnnotationSource
            && correspondence.compilerKind() == association.sourceKind()
            && correspondence.region().compilerOwner().equals(association.ownerPath()) && value.sourceKind() == node.sourceKind()
            && value.origin().equals(association.origin()))
        .isPresent();
  }

  private void validateRestrictedOperands(Map<String, SyntaxNode> nodes) {
    var parents = new HashMap<String, SyntaxNode>();
    nodes.values().forEach(parent -> parent.children().forEach(child -> parents.put(child.node().nodeId(), parent)));
    for (var node : nodes.values()) {
      if (node.value() instanceof SyntaxValue.Literal literal && literal.restrictedDecimal().isPresent()) {
        var association = writtenSourceAssociations.get(node.nodeId());
        if (association == null || association.normalizedExpression().isEmpty()) {
          throw new IllegalArgumentException("A restricted decimal operand requires its explicit grammar-derived correspondence");
        }
        WrittenExpressionSemantics.validateRestrictedParent(node, Optional.ofNullable(parents.get(node.nodeId())));
      }
    }
  }

  private void validateEmptyDeclarationOwners(Map<String, SyntaxNode> nodes) {
    for (var root : nodes.values()) {
      if (!(root.value() instanceof SyntaxValue.CompilationUnit unit)) {
        continue;
      }
      var members = root
          .children(unit.form() == SyntaxValue.CompilationUnitForm.COMPACT ? SyntaxRole.COMPACT_MEMBER : SyntaxRole.TYPE_DECLARATION);
      var emptyOrdinal = 0;
      for (var member : members) {
        if (member.syntaxKind() != SyntaxKind.EMPTY) {
          continue;
        }
        var ordinal = emptyOrdinal;
        emptyOrdinal++;
        var association = writtenSourceAssociations.get(member.nodeId());
        if (unit.range() != SyntaxValue.CompilationUnitRange.CAPTURED_SOURCE || association == null
            || !association.ownerPath().parent().equals(Optional.of(SourceOwnerPath.root()))
            || association.ownerPath().lastStep()
                .filter(step -> (step.role() == SourceOwnerPath.Role.EMPTY_DECLARATION && step.ordinal() == ordinal)
                    || (step.role() == SourceOwnerPath.Role.TYPE_DECLARATION && step.ordinal() == members.indexOf(member)))
                .isEmpty()) {
          throw new IllegalArgumentException("An empty unit declaration requires its exact parsed or captured unit owner and ordinal");
        }
      }
    }
    for (var node : nodes.values()) {
      if (node.syntaxKind() != SyntaxKind.EMPTY || node.structuralPath().steps().getLast().role() != SyntaxRole.MEMBER) {
        continue;
      }
      var association = writtenSourceAssociations.get(node.nodeId());
      var parent = nodes.values().stream().filter(owner -> owner.syntaxKind() == SyntaxKind.TYPE_DECLARATION
          && owner.children(SyntaxRole.MEMBER).stream().anyMatch(member -> member.nodeId().equals(node.nodeId()))).findFirst();
      if (parent.isEmpty()) {
        throw new IllegalArgumentException("An empty body declaration requires a source type parent");
      }
      var owner = writtenSourceAssociations.get(parent.orElseThrow().nodeId());
      var ordinal = parent.orElseThrow().children(SyntaxRole.MEMBER).stream().filter(member -> member.syntaxKind() == SyntaxKind.EMPTY)
          .toList().indexOf(node);
      if (association == null || owner == null || !association.ownerPath().parent().equals(Optional.of(owner.ownerPath()))
          || association.ownerPath().lastStep()
              .filter(step -> step.role() == SourceOwnerPath.Role.EMPTY_DECLARATION && step.ordinal() == ordinal).isEmpty()) {
        throw new IllegalArgumentException(
            "An empty body declaration requires its exact parsed type owner association and role-local ordinal");
      }
    }
  }

  private boolean associationRangeMatches(SyntaxNode node, WrittenSourceAssociation association) {
    var source = sources.get(node.sourceFileKey());
    if (node.value() instanceof SyntaxValue.CompilationUnit unit && unit.range() == SyntaxValue.CompilationUnitRange.CAPTURED_SOURCE) {
      return source != null && node.sourceKind() == JavaSourceKind.COMPILATION_UNIT
          && association.sourceKind() == JavaSourceKind.COMPILATION_UNIT && association.ownerPath().isEmpty()
          && node.origin().filter(span -> span.startOffset() == 0 && span.endOffsetExclusive() == source.content().length()).isPresent()
          && association.origin().map(span -> span.endOffsetExclusive() <= source.content().length()).orElse(true);
    }
    if (node.origin().equals(association.origin())) {
      return true;
    }
    return source != null && node.value() instanceof SyntaxValue.CompilationUnit unit
        && unit.form() == SyntaxValue.CompilationUnitForm.COMPACT && association.sourceKind() == JavaSourceKind.COMPILATION_UNIT
        && association.ownerPath().isEmpty()
        && node.origin().filter(span -> span.startOffset() == 0 && span.endOffsetExclusive() == source.content().length()).isPresent()
        && association.origin().map(span -> span.startOffset() >= 0 && span.endOffsetExclusive() <= source.content().length()).orElse(true);
  }

  private void validateDeclarationMemberOrders(List<SyntaxNode> nodes) {
    for (var order : declarationMemberOrders.values()) {
      if (nodes.stream().noneMatch(node -> sourceMemberRole(node, order.sourceFileKey(), order.ownerTypeKey()).isPresent())) {
        throw new IllegalArgumentException("Declaration member order requires an actual source-owned declaration");
      }
    }
  }

  private static Optional<SyntaxRole> sourceMemberRole(SyntaxNode node, String sourceFileKey, String ownerKey) {
    if (!node.sourceFileKey().equals(sourceFileKey)) {
      return Optional.empty();
    }
    if (node.value() instanceof SyntaxValue.TypeDeclaration declaration && declaration.declarationKey().equals(ownerKey)) {
      return Optional.of(SyntaxRole.MEMBER);
    }
    if (node.value() instanceof SyntaxValue.CompilationUnit unit && unit.form() == SyntaxValue.CompilationUnitForm.COMPACT
        && node.children(SyntaxRole.COMPACT_MEMBER).stream().anyMatch(
            member -> member.value() instanceof SyntaxValue.ExecutableDeclaration declaration && declaration.ownerKey().equals(ownerKey))) {
      return Optional.of(SyntaxRole.COMPACT_MEMBER);
    }
    return Optional.empty();
  }

  private void validateMethodBodies(Map<String, SyntaxNode> nodes) {
    var sourceInstances = ownedInstances(nodes);
    var sourceExecutables = nodes.values().stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration)
        .collect(Collectors.groupingBy(node -> node.value(SyntaxValue.ExecutableDeclaration.class).declarationKey()));
    var sourceBodyOwners = new IdentityHashMap<SyntaxNode, String>();
    sourceExecutables.forEach((methodKey, declarations) -> declarations
        .forEach(declaration -> declaration.children(SyntaxRole.BODY).forEach(body -> sourceBodyOwners.put(body, methodKey))));
    var rootOwners = new IdentityHashMap<SyntaxNode, String>();
    var auxiliaryOwners = new IdentityHashMap<SyntaxNode, String>();
    for (var entry : executionScopes.entrySet()) {
      var methodKey = entry.getKey();
      var registration = entry.getValue();
      var root = registration.root();
      if (!(registration.scope() instanceof ExecutionScope.Method)) {
        validateDeclarationScope(registration, nodes);
        continue;
      }
      LocalBindingDeclarations.validate(methodKey, root, List.copyOf(nodes.values()));
      if (!sources.containsKey(root.sourceFileKey())) {
        throw new IllegalArgumentException("Method body source is not in the captured snapshot: " + methodKey);
      }
      declarationMemberOrders.values().stream()
          .filter(order -> order.members().stream().anyMatch(member -> member.declarationKey().equals(methodKey))).forEach(order -> {
            if (!root.sourceFileKey().equals(order.sourceFileKey())) {
              throw new IllegalArgumentException("Method body source must match its declared member owner: " + methodKey);
            }
          });
      var previousRootOwner = rootOwners.putIfAbsent(root, methodKey);
      if (previousRootOwner != null) {
        throw new IllegalArgumentException(
            "Method body root is shared by executable declarations: " + previousRootOwner + " and " + methodKey);
      }
      if (registration.methodOrigin().orElseThrow() == MethodBodyOrigin.SOURCE_AST) {
        if (!sourceInstances.contains(root)) {
          throw new IllegalArgumentException("Source method body must be the identical aggregate occurrence: " + methodKey);
        }
        var declarations = sourceExecutables.getOrDefault(methodKey, List.of());
        if (declarations.size() != 1 || declarations.getFirst().children(SyntaxRole.BODY).size() != 1
            || !methodKey.equals(sourceBodyOwners.get(root))) {
          throw new IllegalArgumentException("Source method body must belong to its unique executable declaration: " + methodKey);
        }
      }
      for (var node : root.descendantsAndSelf()) {
        if (registration.methodOrigin().orElseThrow() != MethodBodyOrigin.SOURCE_AST) {
          var previousOwner = auxiliaryOwners.putIfAbsent(node, methodKey);
          if (previousOwner != null) {
            throw new IllegalArgumentException(
                "Auxiliary method body occurrence is shared by executable declarations: " + previousOwner + " and " + methodKey);
          }
          if (sourceInstances.contains(node)) {
            throw new IllegalArgumentException("Auxiliary execution body cannot also be a source AST occurrence: " + methodKey);
          }
          validateAuxiliaryBodyNode(node, root.sourceFileKey(), methodKey);
        }
      }
      if (registration.methodOrigin().orElseThrow() != MethodBodyOrigin.SOURCE_AST && sourceExecutables.getOrDefault(methodKey, List.of())
          .stream().anyMatch(declaration -> !declaration.children(SyntaxRole.BODY).isEmpty())) {
        throw new IllegalArgumentException("A published written body must retain its source occurrence provenance: " + methodKey);
      }
    }
  }

  private void validateDeclarationScope(SourceExecutionFacts facts, Map<String, SyntaxNode> nodes) {
    var root = facts.root();
    if (!ownedInstances(nodes).contains(root)) {
      throw new IllegalArgumentException("A declaration scope requires its identical canonical source anchor");
    }
    String ownerKey;
    if (facts.scope() instanceof ExecutionScope.Field field) {
      if (root.syntaxKind() != SyntaxKind.FIELD_DECLARATION
          || !root.value(SyntaxValue.VariableDeclaration.class).declarationKey().equals(field.key())
          || !root.value(SyntaxValue.VariableDeclaration.class).ownerKey().equals(field.ownerKey())) {
        throw new IllegalArgumentException("A field scope requires its exact source declaration and owner");
      }
      ownerKey = field.ownerKey();
    } else if (facts.scope() instanceof ExecutionScope.EnumConstant constant) {
      var declaration = root.value(SyntaxValue.VariableDeclaration.class);
      if (root.syntaxKind() != SyntaxKind.ENUM_CONSTANT_DECLARATION || !declaration.declarationKey().equals(constant.key())
          || !declaration.ownerKey().equals(constant.ownerKey())) {
        throw new IllegalArgumentException("An enum scope requires its exact constant declaration and enum owner");
      }
      constant.selectedConstructor().flatMap(SemanticReference::symbolKey).ifPresent(selected -> {
        var members = declarationMemberOrders.get(constant.ownerKey());
        if (members != null && members.members().stream().noneMatch(member -> member.declarationKey().equals(selected))) {
          throw new IllegalArgumentException("An enum constant must select an actual constructor in its declared enum");
        }
      });
      ownerKey = constant.ownerKey();
    } else if (facts.scope() instanceof ExecutionScope.Initializer initializer) {
      if (!root.origin().equals(Optional.of(initializer.origin())) || root.syntaxKind() != initializer.kind()) {
        throw new IllegalArgumentException("An initializer scope requires its exact written block occurrence");
      }
      ownerKey = initializer.ownerKey();
    } else {
      throw new IllegalArgumentException("A declaration scope requires field, enum constant or initializer ownership");
    }
    var ownedMember = nodes.values().stream()
        .anyMatch(node -> sourceMemberRole(node, root.sourceFileKey(), ownerKey)
            .filter(role -> role == SyntaxRole.MEMBER || facts.scope() instanceof ExecutionScope.Field)
            .filter(role -> node.children(role).stream().anyMatch(member -> member.nodeId().equals(root.nodeId()))).isPresent());
    if (!ownedMember) {
      throw new IllegalArgumentException("A declaration execution anchor must belong directly to its actual type owner");
    }
    LocalBindingDeclarations.validate(facts.scope().key(), root, List.copyOf(nodes.values()));
  }

  private void validateAuxiliaryBodyNode(SyntaxNode node, String sourceFileKey, String methodKey) {
    if (!node.sourceFileKey().equals(sourceFileKey)) {
      throw new IllegalArgumentException("Auxiliary method body descendants must retain their root source: " + methodKey);
    }
    var source = Objects.requireNonNull(sources.get(sourceFileKey), "captured body source");
    node.origin().ifPresent(origin -> {
      if (!origin.sourceFileKey().equals(sourceFileKey) || origin.endOffsetExclusive() > source.content().length()) {
        throw new IllegalArgumentException("Auxiliary method body origin is outside its captured source: " + methodKey);
      }
    });
    for (var child : node.children()) {
      if (!child.node().structuralPath().equals(node.structuralPath().child(child.role(), child.position()))) {
        throw new IllegalArgumentException("Auxiliary method body child path does not match its attachment: " + methodKey);
      }
    }
  }

  private void validateContributionEdgeOwners(Map<String, SyntaxNode> nodes) {
    for (var capturedSource : snapshot.sourceFiles()) {
      var contribution = Objects.requireNonNull(contributions.get(capturedSource.sourceFileKey()), "source contribution");
      for (var edge : contribution.structuralEdges()) {
        var parent = nodes.get(edge.parentKey());
        if (parent != null && !parent.sourceFileKey().equals(contribution.sourceFileKey())) {
          throw new IllegalArgumentException("AST structural parent " + edge.parentKey() + " belongs to source " + parent.sourceFileKey()
              + ", not contribution " + contribution.sourceFileKey());
        }
      }
      for (var edge : contribution.semanticEdges()) {
        var source = nodes.get(edge.sourceKey());
        if (source != null && !source.sourceFileKey().equals(contribution.sourceFileKey())) {
          throw new IllegalArgumentException("AST semantic source " + edge.sourceKey() + " belongs to source " + source.sourceFileKey()
              + ", not contribution " + contribution.sourceFileKey());
        }
      }
    }
  }

  private static void validateRoot(SyntaxSourceContribution contribution) {
    var root = contribution.root();
    if (!root.sourceFileKey().equals(contribution.sourceFileKey()) || root.sourceKind() != JavaSourceKind.COMPILATION_UNIT
        || root.syntaxKind() != SyntaxKind.COMPILATION_UNIT || !(root.value() instanceof SyntaxValue.CompilationUnit)
        || !root.structuralPath().equals(SyntaxStructuralPath.sourceFileRoot())) {
      throw new IllegalArgumentException(
          "AST source root must be a compilation unit for source " + contribution.sourceFileKey() + ": " + root.nodeId());
    }
  }

  private static void validateNode(SyntaxNode node, String contributionSource, SourceFile source) {
    Objects.requireNonNull(node, "node");
    if (!node.sourceFileKey().equals(contributionSource) || !node.sourceFileKey().equals(source.sourceFileKey())) {
      throw new IllegalArgumentException("AST node source is not owned by contribution " + contributionSource + ": " + node.nodeId()
          + " belongs to " + node.sourceFileKey());
    }
    var range = node.origin().orElseThrow(() -> new IllegalArgumentException("AST node lacks a required source range: " + node.nodeId()));
    if (node.value() instanceof SyntaxValue.CompilationUnit unit && unit.range() == SyntaxValue.CompilationUnitRange.CAPTURED_SOURCE
        && (range.startOffset() != 0 || range.endOffsetExclusive() != source.content().length())) {
      throw new IllegalArgumentException("A captured source envelope must cover the exact complete source file");
    }
    if (!range.sourceFileKey().equals(source.sourceFileKey()) || range.startOffset() < 0 || range.endOffsetExclusive() < range.startOffset()
        || range.endOffsetExclusive() > source.content().length()) {
      throw new IllegalArgumentException("AST source range is outside captured source " + source.sourceFileKey() + " for node "
          + node.nodeId() + ": [" + range.startOffset() + ", " + range.endOffsetExclusive() + ") of " + source.content().length());
    }
  }

  private static void validateAnnotationRanges(List<SyntaxNode> nodes) {
    var writtenOrigins = new HashSet<SourceSpan>();
    for (var parent : nodes) {
      var parentRange = parent.origin().orElseThrow();
      var previousEnd = parentRange.startOffset();
      for (var child : parent.children()) {
        if (child.role() != SyntaxRole.ANNOTATION || !(child.node().value() instanceof SyntaxValue.Annotation)) {
          continue;
        }
        var annotation = child.node();
        var range = annotation.origin().orElseThrow();
        if (!writtenOrigins.add(range)) {
          throw new IllegalArgumentException("Duplicate AST written annotation range: " + annotation.nodeId());
        }
        if (range.length() == 0 || !range.sourceFileKey().equals(parentRange.sourceFileKey()) || range.startOffset() < previousEnd
            || range.endOffsetExclusive() > parentRange.endOffsetExclusive()) {
          throw new IllegalArgumentException("AST annotation range must be nonempty, contained, and ordered: " + annotation.nodeId());
        }
        previousEnd = range.endOffsetExclusive();
      }
    }
  }

  private static void validateRoots(List<SyntaxNode> roots, Map<String, SyntaxNode> nodes) {
    var ownedInstances = ownedInstances(nodes);
    for (var root : roots) {
      if (!ownedInstances.contains(root)) {
        throw new IllegalArgumentException("AST source root is not an owned node: " + root.nodeId());
      }
    }
  }

  private static Map<String, List<SyntaxStructuralEdge>> validateStructuralEdges(List<SyntaxStructuralEdge> edges,
      Map<String, SyntaxNode> nodes, List<SyntaxNode> roots) {
    var outgoing = new HashMap<String, List<SyntaxStructuralEdge>>();
    var parentCount = new HashMap<String, Integer>();
    var seenEdges = new HashSet<SyntaxStructuralEdge>();
    Map<String, Map<SyntaxRole, Set<Integer>>> rolePositions = new HashMap<>();
    for (var edge : edges) {
      Objects.requireNonNull(edge, "structuralEdge");
      if (!seenEdges.add(edge)) {
        throw new IllegalArgumentException("Duplicate AST structural edge tuple: " + edge);
      }
      var parent = nodes.get(edge.parentKey());
      var child = nodes.get(edge.childKey());
      if (parent == null) {
        throw new IllegalArgumentException("AST edge endpoint is not owned by the aggregate: structural parent " + edge.parentKey());
      }
      if (child == null) {
        throw new IllegalArgumentException("AST edge endpoint is not owned by the aggregate: structural child " + edge.childKey());
      }
      if (!parent.sourceFileKey().equals(child.sourceFileKey())) {
        throw new IllegalArgumentException(
            "AST structural edge crosses source owners " + parent.sourceFileKey() + " and " + child.sourceFileKey() + ": " + edge);
      }
      if (!positionsFor(rolePositions, edge.parentKey(), edge.role()).add(edge.position())) {
        throw new IllegalArgumentException(
            "Duplicate AST role position for parent " + edge.parentKey() + " and role " + edge.role() + ": " + edge.position());
      }
      outgoingFor(outgoing, edge.parentKey()).add(edge);
      parentCount.merge(edge.childKey(), 1, Integer::sum);
    }
    for (var parentEntry : rolePositions.entrySet()) {
      for (var roleEntry : parentEntry.getValue().entrySet()) {
        var positions = roleEntry.getValue();
        for (var expected = 0; expected < positions.size(); expected++) {
          if (!positions.contains(expected)) {
            throw new IllegalArgumentException("AST role positions are not contiguous for parent " + parentEntry.getKey() + " and role "
                + roleEntry.getKey() + ": expected " + expected);
          }
        }
      }
    }
    outgoing.values().forEach(children -> children.sort(CHILD_ORDER));
    detectCycles(nodes, outgoing);
    var rootKeys = new HashSet<String>();
    for (var root : roots) {
      rootKeys.add(root.nodeId());
      var count = parentCount.getOrDefault(root.nodeId(), 0);
      if (count != 0) {
        throw new IllegalArgumentException("AST source root has a structural parent: " + root.nodeId() + " count " + count);
      }
    }
    for (var node : nodes.values()) {
      var count = parentCount.getOrDefault(node.nodeId(), 0);
      if (!rootKeys.contains(node.nodeId()) && count != 1) {
        throw new IllegalArgumentException("AST non-root requires exactly one structural parent: " + node.nodeId() + " count " + count);
      }
    }
    for (var edge : edges) {
      var parent = Objects.requireNonNull(nodes.get(edge.parentKey()), "structural parent");
      var child = Objects.requireNonNull(nodes.get(edge.childKey()), "structural child");
      if (!child.structuralPath().equals(parent.structuralPath().child(edge.role(), edge.position()))) {
        throw new IllegalArgumentException("AST structural path disagrees with parent edge for node " + child.nodeId());
      }
    }
    return outgoing;
  }

  private static void detectCycles(Map<String, SyntaxNode> nodes, Map<String, List<SyntaxStructuralEdge>> outgoing) {
    var completed = new HashSet<String>();
    var active = new HashSet<String>();
    for (var key : nodes.keySet()) {
      visitCycle(key, outgoing, completed, active);
    }
  }

  private static void visitCycle(String key, Map<String, List<SyntaxStructuralEdge>> outgoing, Set<String> completed, Set<String> active) {
    if (completed.contains(key)) {
      return;
    }
    if (!active.add(key)) {
      throw new IllegalArgumentException("AST structural cycle involves node " + key);
    }
    for (var edge : outgoing.getOrDefault(key, List.of())) {
      visitCycle(edge.childKey(), outgoing, completed, active);
    }
    active.remove(key);
    completed.add(key);
  }

  private static List<SyntaxNode> orderNodes(List<SyntaxNode> roots, Map<String, SyntaxNode> nodes,
      Map<String, List<SyntaxStructuralEdge>> outgoing) {
    var ordered = new ArrayList<SyntaxNode>();
    var reached = new HashSet<String>();
    for (var root : roots) {
      visitOrdered(root.nodeId(), nodes, outgoing, reached, ordered);
    }
    if (reached.size() != nodes.size()) {
      var missing = nodes.keySet().stream().filter(key -> !reached.contains(key)).sorted().findFirst().orElseThrow();
      throw new IllegalArgumentException("AST node is not reachable from a source root: " + missing);
    }
    return ordered;
  }

  private static void visitOrdered(String key, Map<String, SyntaxNode> nodes, Map<String, List<SyntaxStructuralEdge>> outgoing,
      Set<String> reached, List<SyntaxNode> ordered) {
    if (!reached.add(key)) {
      throw new IllegalArgumentException("AST node has multiple structural paths: " + key);
    }
    ordered.add(nodes.get(key));
    for (var edge : outgoing.getOrDefault(key, List.of())) {
      visitOrdered(edge.childKey(), nodes, outgoing, reached, ordered);
    }
  }

  private static void validateEmbeddedChildren(Map<String, SyntaxNode> nodes, Map<String, List<SyntaxStructuralEdge>> outgoing) {
    var ownedInstances = ownedInstances(nodes);
    for (var node : nodes.values()) {
      var expected = node.structuralEdges().stream().sorted(CHILD_ORDER).toList();
      var actual = outgoing.getOrDefault(node.nodeId(), List.of());
      if (!expected.equals(actual)) {
        throw new IllegalArgumentException("AST structural edges disagree with owned node children: " + node.nodeId());
      }
      for (var child : node.children()) {
        if (!ownedInstances.contains(child.node())) {
          throw new IllegalArgumentException("AST child is not the identical owned node: " + child.node().nodeId());
        }
      }
    }
  }

  private static void validateSemanticEdges(List<SyntaxSemanticEdge> edges, Map<String, SyntaxNode> nodes) {
    var seen = new HashSet<SyntaxSemanticEdge>();
    var declarationEdges = new HashMap<String, List<SyntaxSemanticEdge>>();
    var annotationTargets = new HashMap<String, List<SyntaxSemanticEdge>>();
    var annotationParents = new HashMap<String, SyntaxNode>();
    var recordTypeOwners = new HashMap<String, SyntaxNode>();
    var ownedExecutables = nodes.values().stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration)
        .collect(Collectors.groupingBy(node -> node.value(SyntaxValue.ExecutableDeclaration.class).declarationKey()));
    var fieldGroups = SharedVariableDeclarations.index(List.copyOf(nodes.values()));
    for (var parent : nodes.values()) {
      parent.children().stream().filter(child -> child.role() == SyntaxRole.ANNOTATION)
          .forEach(child -> annotationParents.put(child.node().nodeId(), parent));
      if (parent.syntaxKind() == SyntaxKind.RECORD_COMPONENT_DECLARATION) {
        parent.children(SyntaxRole.DECLARED_TYPE).forEach(type -> collectRecordTypeOwners(type, parent, recordTypeOwners));
      }
    }
    for (var edge : edges) {
      Objects.requireNonNull(edge, "semanticEdge");
      if (!seen.add(edge)) {
        throw new IllegalArgumentException("Duplicate AST semantic edge tuple: " + edge);
      }
      if (!nodes.containsKey(edge.sourceKey())) {
        throw new IllegalArgumentException("AST edge endpoint is not owned by the aggregate: semantic source " + edge.sourceKey());
      }
      if (edge.target() instanceof SourceOccurrence occurrence && !nodes.containsKey(occurrence.nodeKey())) {
        throw new IllegalArgumentException("AST edge endpoint is not owned by the aggregate: semantic target " + occurrence.nodeKey());
      }
      if (edge.target() instanceof SourceOccurrence occurrence
          && !isDeclaration(Objects.requireNonNull(nodes.get(occurrence.nodeKey()), "owned semantic target"))) {
        throw new IllegalArgumentException("AST semantic target must identify a declaration occurrence: " + occurrence.nodeKey());
      }
      if (edge.relation() == SyntaxSemanticRelation.DECLARATION_REFERENCE) {
        appendEdge(declarationEdges, edge);
      } else if (edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET) {
        appendEdge(annotationTargets, edge);
      }
    }
    for (var node : nodes.values()) {
      if (!seen.containsAll(node.semanticEdges())) {
        throw new IllegalArgumentException("AST semantic edges omit an owned node reference: " + node.nodeId());
      }
      validateDeclarationEdge(node, declarationEdges.getOrDefault(node.nodeId(), List.of()), nodes);
      validateMemberReferenceSelection(node, ownedExecutables, nodes);
      var targets = annotationTargets.getOrDefault(node.nodeId(), List.of());
      if (node.sourceKind() == JavaSourceKind.ANNOTATION && node.value() instanceof SyntaxValue.Annotation annotation
          && annotation.location() != SyntaxValue.AnnotationLocation.UNRESOLVED
          && annotation.location() != SyntaxValue.AnnotationLocation.VALUE) {
        if (node.semanticReference().orElseThrow().status() != ResolutionStatus.RESOLVED) {
          throw new IllegalArgumentException("AST applied annotation requires a resolved annotation type: " + node.nodeId());
        }
        if (targets.size() != 1 || !(targets.getFirst().target() instanceof AnnotationApplication application)) {
          throw new IllegalArgumentException("AST annotation requires exactly one typed application: " + node.nodeId());
        }
        var parent = annotationParents.get(node.nodeId());
        if (parent == null || !parent.sourceFileKey().equals(node.sourceFileKey())
            || !application.equals(AnnotationApplication.forAttachment(parent, node,
                Optional.ofNullable(recordTypeOwners.get(parent.nodeId())), fieldGroups.getOrDefault(parent.nodeId(), List.of())))) {
          throw new IllegalArgumentException("AST annotation application does not match its exact source location: " + node.nodeId());
        }
      } else if (!targets.isEmpty()) {
        throw new IllegalArgumentException("AST annotation target relation has a nonannotation source: " + node.nodeId());
      }
    }
  }

  private static void collectRecordTypeOwners(SyntaxNode type, SyntaxNode component, Map<String, SyntaxNode> owners) {
    owners.put(type.nodeId(), component);
    type.children().stream().map(SyntaxChild::node).filter(child -> child.syntaxKind() == SyntaxKind.TYPE_USE)
        .forEach(child -> collectRecordTypeOwners(child, component, owners));
  }

  private static void validateMemberReferenceSelection(SyntaxNode node, Map<String, List<SyntaxNode>> ownedExecutables,
      Map<String, SyntaxNode> nodes) {
    if (!(node.value() instanceof SyntaxValue.MemberReference reference) || node.semanticReference().isEmpty()
        || node.requiredReference().symbolKey().isEmpty()) {
      return;
    }
    for (var declaration : ownedExecutables.getOrDefault(node.requiredReference().symbolKey().orElseThrow(), List.of())) {
      var executable = declaration.value(SyntaxValue.ExecutableDeclaration.class);
      if (node.requiredReference().referenceKind() != executableReferenceKind(executable.kind())) {
        throw new IllegalArgumentException("A member reference must agree with its owned executable kind: " + node.nodeId());
      }
      CanonicalMethodCorrespondence.validateSelection(reference.selectedType(), declaration, nodes);
      if (node.requiredReference().referenceKind() != ReferenceKind.METHOD) {
        validateConstructedTypeSelection(node.requiredChild(SyntaxRole.QUALIFIER), executable.ownerKey());
        continue;
      }
      if (!reference.name().equals(executable.name())) {
        throw new IllegalArgumentException("A member reference must agree with its owned executable name: " + node.nodeId());
      }
      if (reference.staticMember() != executable.effectiveModifiers().contains(JavaDeclarationModifier.STATIC)) {
        throw new IllegalArgumentException("A member reference must agree with its owned executable static modifier: " + node.nodeId());
      }
    }
  }

  private static void validateConstructedTypeSelection(SyntaxNode qualifier, String ownerKey) {
    if (qualifier.semanticReference().filter(reference -> reference.status() == ResolutionStatus.RESOLVED
        && reference.referenceKind() == ReferenceKind.TYPE && !reference.symbolKey().equals(Optional.of(ownerKey))).isPresent()) {
      throw new IllegalArgumentException("A member reference must agree with its owned constructor type: " + qualifier.nodeId());
    }
    if (qualifier.sourceKind() == JavaSourceKind.ANNOTATED_TYPE) {
      validateConstructedTypeSelection(qualifier.requiredChild(SyntaxRole.UNDERLYING_TYPE), ownerKey);
    } else if (qualifier.sourceKind() == JavaSourceKind.PARAMETERIZED_TYPE) {
      validateConstructedTypeSelection(qualifier.requiredChild(SyntaxRole.TYPE_NAME), ownerKey);
    }
  }

  private static void appendEdge(Map<String, List<SyntaxSemanticEdge>> indexedEdges, SyntaxSemanticEdge edge) {
    indexedEdges.computeIfAbsent(edge.sourceKey(), ignored -> new ArrayList<>()).add(edge);
  }

  private static boolean isDeclaration(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case TYPE_DECLARATION, TYPE_PARAMETER_DECLARATION, METHOD_DECLARATION, FIELD_DECLARATION, ENUM_CONSTANT_DECLARATION,
          RECORD_COMPONENT_DECLARATION, PARAMETER_DECLARATION, LOCAL_DECLARATION ->
        true;
      default -> false;
    };
  }

  private static void validateDeclarationEdge(SyntaxNode node, List<SyntaxSemanticEdge> declarationEdges, Map<String, SyntaxNode> nodes) {
    if (switch (node.syntaxKind()) {
      case INVOCATION, OBJECT_CREATION, MEMBER_REFERENCE, VARIABLE_ACCESS, FIELD_ACCESS, LOCAL_DECLARATION -> true;
      default -> false;
    }) {
      var selected = declarationEdges.stream().filter(edge -> edge.target() instanceof Declaration).toList();
      if (!selected.equals(node.semanticEdges())
          || declarationEdges.stream().anyMatch(edge -> edge.target() instanceof SourceOccurrence occurrence
              && !sameSelectedDeclaration(node, Objects.requireNonNull(nodes.get(occurrence.nodeKey()), "owned semantic target")))) {
        throw new IllegalArgumentException("AST executable declaration reference must match its selected target: " + node.nodeId());
      }
    }
    if (node.value() instanceof SyntaxValue.Annotation && !declarationEdges.equals(node.semanticEdges())) {
      throw new IllegalArgumentException("AST annotation type reference must match its selected target: " + node.nodeId());
    }
    if ((node.syntaxKind() == SyntaxKind.TYPE_USE || node.syntaxKind() == SyntaxKind.MODULE_TYPE_REFERENCE)
        && !declarationEdges.equals(node.semanticEdges())) {
      throw new IllegalArgumentException("AST type-use declaration reference must match its selected target: " + node.nodeId());
    }
    if (switch (node.syntaxKind()) {
      case ANNOTATION_ARGUMENT, ANNOTATION_ELEMENT_NAME, ANNOTATION_NAME_REFERENCE, ANNOTATION_CLASS_LITERAL, ANNOTATION_CAST -> true;
      default -> false;
    } && !declarationEdges.equals(node.semanticEdges())) {
      throw new IllegalArgumentException("AST annotation value reference must match its selected target: " + node.nodeId());
    }
    var declaration = declaredTarget(node);
    if (declaration.isEmpty()) {
      return;
    }
    if (declarationEdges.size() != 1 || !declarationEdges.getFirst().target().equals(declaration.orElseThrow())) {
      throw new IllegalArgumentException("AST declaration reference must match its source declaration: " + node.nodeId());
    }
  }

  private static Optional<Declaration> declaredTarget(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case TYPE_DECLARATION ->
        Optional.of(declarationTarget(ReferenceKind.TYPE, node.value(SyntaxValue.TypeDeclaration.class).declarationKey()));
      case TYPE_PARAMETER_DECLARATION -> Optional
          .of(declarationTarget(ReferenceKind.TYPE_PARAMETER, node.value(SyntaxValue.TypeParameterDeclaration.class).declarationKey()));
      case METHOD_DECLARATION -> {
        var value = node.value(SyntaxValue.ExecutableDeclaration.class);
        yield Optional.of(declarationTarget(executableReferenceKind(value.kind()), value.declarationKey()));
      }
      case FIELD_DECLARATION ->
        Optional.of(declarationTarget(ReferenceKind.FIELD, node.value(SyntaxValue.VariableDeclaration.class).declarationKey()));
      case ENUM_CONSTANT_DECLARATION ->
        Optional.of(declarationTarget(ReferenceKind.ENUM_CONSTANT, node.value(SyntaxValue.VariableDeclaration.class).declarationKey()));
      case RECORD_COMPONENT_DECLARATION -> Optional
          .of(declarationTarget(ReferenceKind.RECORD_COMPONENT, node.value(SyntaxValue.RecordComponentDeclaration.class).declarationKey()));
      case PARAMETER_DECLARATION ->
        Optional.of(declarationTarget(ReferenceKind.PARAMETER, node.value(SyntaxValue.ParameterDeclaration.class).declarationKey()));
      default -> Optional.<Declaration>empty();
    };
  }

  private static ReferenceKind executableReferenceKind(ExecutableKind kind) {
    return switch (kind) {
      case CONSTRUCTOR, COMPACT_CONSTRUCTOR -> ReferenceKind.CONSTRUCTOR;
      case METHOD, ANNOTATION_ELEMENT -> ReferenceKind.METHOD;
    };
  }

  private static boolean sameSelectedDeclaration(SyntaxNode source, SyntaxNode target) {
    return source.semanticReference()
        .filter(reference -> declaredTarget(target).map(Declaration::reference).or(target::semanticReference)
            .filter(declaration -> declaration.referenceKind() == reference.referenceKind()
                && declaration.symbolKey().equals(reference.symbolKey()))
            .isPresent())
        .isPresent();
  }

  private static Declaration declarationTarget(ReferenceKind kind, String key) {
    return new Declaration(
        new SemanticReference(kind, ResolutionStatus.RESOLVED, Optional.of(key), Optional.empty(), List.of(), List.of()));
  }

  private static int compareTargets(SyntaxSemanticEdge left, SyntaxSemanticEdge right) {
    var first = left.target();
    var second = right.target();
    if (first instanceof SourceOccurrence source) {
      if (second instanceof SourceOccurrence other) {
        return source.nodeKey().compareTo(other.nodeKey());
      }
      return -1;
    }
    if (second instanceof SourceOccurrence) {
      return 1;
    }
    if (first instanceof Declaration leftDeclaration) {
      return second instanceof Declaration rightDeclaration
          ? compareReferences(leftDeclaration.reference(), rightDeclaration.reference())
          : -1;
    }
    if (second instanceof Declaration) {
      return 1;
    }
    var leftApplication = (AnnotationApplication) first;
    var rightApplication = (AnnotationApplication) second;
    var result = leftApplication.ownerNodeKey().compareTo(rightApplication.ownerNodeKey());
    if (result == 0) {
      result = compareOptional(leftApplication.declarationNodeKey(), rightApplication.declarationNodeKey());
    }
    if (result == 0) {
      result = compareTypeLocation(leftApplication.typeLocation(), rightApplication.typeLocation());
    }
    for (var index = 0; result == 0
        && index < Math.min(leftApplication.propagatedCounterparts().size(), rightApplication.propagatedCounterparts().size()); index++) {
      var leftCounterpart = leftApplication.propagatedCounterparts().get(index);
      var rightCounterpart = rightApplication.propagatedCounterparts().get(index);
      result = compareReferences(leftCounterpart.reference(), rightCounterpart.reference());
      if (result == 0) {
        result = Boolean.compare(leftCounterpart.declarationApplicable(), rightCounterpart.declarationApplicable());
      }
      if (result == 0) {
        result = compareTypeLocation(leftCounterpart.typeLocation(), rightCounterpart.typeLocation());
      }
    }
    if (result == 0) {
      result = Integer.compare(leftApplication.propagatedCounterparts().size(), rightApplication.propagatedCounterparts().size());
    }
    for (var index = 0; result == 0
        && index < Math.min(leftApplication.fieldDeclarators().size(), rightApplication.fieldDeclarators().size()); index++) {
      var leftField = leftApplication.fieldDeclarators().get(index);
      var rightField = rightApplication.fieldDeclarators().get(index);
      result = leftField.fieldNodeKey().compareTo(rightField.fieldNodeKey());
      if (result == 0) {
        result = Boolean.compare(leftField.declarationApplicable(), rightField.declarationApplicable());
      }
      if (result == 0) {
        result = compareTypeLocation(leftField.typeLocation(), rightField.typeLocation());
      }
    }
    return result == 0 ? Integer.compare(leftApplication.fieldDeclarators().size(), rightApplication.fieldDeclarators().size()) : result;
  }

  private static int compareTypeLocation(Optional<AnnotationTypeLocation> left, Optional<AnnotationTypeLocation> right) {
    if (left.isEmpty()) {
      return right.isEmpty() ? 0 : -1;
    }
    if (right.isEmpty()) {
      return 1;
    }
    if (left.orElseThrow() instanceof AnnotationTypeLocation.Written written) {
      return right.orElseThrow() instanceof AnnotationTypeLocation.Written other ? written.nodeKey().compareTo(other.nodeKey()) : -1;
    }
    if (right.orElseThrow() instanceof AnnotationTypeLocation.Written) {
      return 1;
    }
    if (left.orElseThrow() instanceof AnnotationTypeLocation.ArrayDimension dimension) {
      if (!(right.orElseThrow() instanceof AnnotationTypeLocation.ArrayDimension other)) {
        return -1;
      }
      var result = dimension.nodeKey().compareTo(other.nodeKey());
      return result == 0 ? Integer.compare(dimension.dimension(), other.dimension()) : result;
    }
    if (right.orElseThrow() instanceof AnnotationTypeLocation.ArrayDimension) {
      return 1;
    }
    return ((AnnotationTypeLocation.Constructed) left.orElseThrow()).declarationKey()
        .compareTo(((AnnotationTypeLocation.Constructed) right.orElseThrow()).declarationKey());
  }

  private static int compareReferences(SemanticReference left, SemanticReference right) {
    var result = left.referenceKind().name().compareTo(right.referenceKind().name());
    if (result == 0) {
      result = left.status().name().compareTo(right.status().name());
    }
    if (result == 0) {
      result = compareOptional(left.symbolKey(), right.symbolKey());
    }
    if (result == 0) {
      result = compareOptional(left.typeName(), right.typeName());
    }
    if (result == 0) {
      result = compareStrings(left.typeAlternativeNames(), right.typeAlternativeNames());
    }
    if (result == 0) {
      result = compareStrings(left.assignableToTypeNames(), right.assignableToTypeNames());
    }
    if (result != 0 || (left.declarationIdentity().isEmpty() && right.declarationIdentity().isEmpty())) {
      return result;
    }
    if (left.declarationIdentity().isEmpty() || right.declarationIdentity().isEmpty()) {
      return left.declarationIdentity().isEmpty() ? -1 : 1;
    }
    var leftIdentity = left.declarationIdentity().orElseThrow();
    var rightIdentity = right.declarationIdentity().orElseThrow();
    result = compareOptional(leftIdentity.moduleName(), rightIdentity.moduleName());
    return result == 0 ? leftIdentity.binaryName().compareTo(rightIdentity.binaryName()) : result;
  }

  private static int compareOptional(Optional<String> left, Optional<String> right) {
    if (left.isEmpty()) {
      return right.isEmpty() ? 0 : -1;
    }
    return right.isEmpty() ? 1 : left.orElseThrow().compareTo(right.orElseThrow());
  }

  private static int compareStrings(List<String> left, List<String> right) {
    for (var index = 0; index < Math.min(left.size(), right.size()); index++) {
      var result = left.get(index).compareTo(right.get(index));
      if (result != 0) {
        return result;
      }
    }
    return Integer.compare(left.size(), right.size());
  }

  private void requireOpen() {
    if (sealed) {
      throw new IllegalStateException("AST aggregate construction is already sealed");
    }
  }

  private static Set<SyntaxNode> ownedInstances(Map<String, SyntaxNode> nodes) {
    var instances = Collections.newSetFromMap(new IdentityHashMap<SyntaxNode, Boolean>());
    instances.addAll(nodes.values());
    return instances;
  }

  private static Set<Integer> positionsFor(Map<String, Map<SyntaxRole, Set<Integer>>> rolePositions, String parentKey, SyntaxRole role) {
    return rolePositions.computeIfAbsent(parentKey, ignored -> new EnumMap<>(SyntaxRole.class)).computeIfAbsent(role,
        ignored -> new HashSet<>());
  }

  private static List<SyntaxStructuralEdge> outgoingFor(Map<String, List<SyntaxStructuralEdge>> outgoing, String parentKey) {
    return outgoing.computeIfAbsent(parentKey, ignored -> new ArrayList<>());
  }

  record ValidatedAggregate(InputSnapshot snapshot, List<SyntaxNode> roots, List<SyntaxNode> nodes, Map<String, SyntaxNode> ownedNodes,
      List<SyntaxStructuralEdge> structuralEdges, List<SyntaxSemanticEdge> semanticEdges, Map<String, SourceExecutionFacts> executionScopes,
      Map<String, DeclarationMemberOrder> declarationMemberOrders, Map<String, ExecutionDecisionIndex> executionDecisions,
      Map<String, WrittenSourceAssociation> writtenSourceAssociations, Map<String, DeclarationTypeEvidence> declarationTypes) {
  }

}
