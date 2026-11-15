package org.aquilagraph.source.internal;

import org.aquilagraph.source.api.CompilerExpressionAnchor;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxNodeKey;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.api.SyntaxValue.ExecutableDeclaration.ExecutableKind;
import java.util.Set;

/** Correlation of exact retained execution occurrences with copied compiler addresses. */
final class ExecutionAttributionValidation {
  private ExecutionAttributionValidation() {
  }

  static void validate(List<ExecutionRegion> regions, Map<String, SyntaxNode> nodes, OccurrenceIndex<SyntaxNode> parents,
      SourceDeclarations declarations, OccurrenceIndex<WrittenSourceAssociation> writtenOccurrences,
      Collection<WrittenSourceAssociation> associations, AttributedDeclarationPaths declarationPaths) {
    var validatedRegions = new HashSet<String>();
    regions.stream()
        .sorted(Comparator.comparingInt(region -> Optional.ofNullable(writtenOccurrences.get(region.facts().root()))
            .map(association -> association.ownerPath().size()).orElse(0)))
        .forEach(region -> validatePaths(region, parents, declarations, writtenOccurrences, declarationPaths, validatedRegions));
    var compilerRegions = new HashSet<SyntaxNodeKey>();
    var sourceRegions = new HashSet<String>();
    for (var association : associations) {
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
            || !compilerRegions.add(SyntaxNodeKey.from(association.sourceFileKey(), execution.path(), relation.compilerKind()))) {
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

  private static void validatePaths(ExecutionRegion region, OccurrenceIndex<SyntaxNode> parents, SourceDeclarations declarations,
      OccurrenceIndex<WrittenSourceAssociation> writtenOccurrences, AttributedDeclarationPaths declarationPaths,
      Set<String> validatedRegions) {
    var facts = region.facts();
    var addresses = region.addresses();
    if (addresses.isEmpty()) {
      return;
    }
    var root = facts.root();
    var compilerRoot = Optional.ofNullable(addresses.get(root.nodeId()))
        .orElseThrow(() -> new IllegalArgumentException("Execution attributions require their actual scope-root address")).compilerPath();
    var constructor = declarations.executables(facts.scope().key()).stream()
        .filter(node -> node.sourceFileKey().equals(root.sourceFileKey()))
        .map(node -> node.value(SyntaxValue.ExecutableDeclaration.class).kind())
        .anyMatch(kind -> kind == ExecutableKind.CONSTRUCTOR || kind == ExecutableKind.COMPACT_CONSTRUCTOR);
    var method = facts.scope() instanceof ExecutionScope.Method;
    if (!method || facts.methodOrigin().orElseThrow() == MethodBodyOrigin.SOURCE_AST) {
      var association = writtenOccurrences.get(root);
      var expected = association == null
          ? Optional.<SyntaxStructuralPath>empty()
          : declarationPaths.compilerPath(root.sourceFileKey(), association.ownerPath());
      var required = !method || association == null || declarationPaths.requiresExactPath(root.sourceFileKey(), association.ownerPath());
      if ((required && expected.isEmpty()) || (expected.isPresent() && !expected.orElseThrow().equals(compilerRoot))) {
        throw new IllegalArgumentException(
            "Execution attribution root must match its independently copied declaration owner and member order");
      }
    }
    var statementOffset = method ? methodStatementOffset(region, compilerRoot) : 0;
    var normalized = region.nodes().values().stream().map(writtenOccurrences::get).filter(Objects::nonNull)
        .flatMap(association -> association.normalizedExpression().stream())
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
      NormalizedExecutionPaths.validate(region, parents, writtenOccurrences, statementOffset, validatedRegions);
    } else {
      for (var node : region.descendants()) {
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
      var association = Optional.ofNullable(writtenOccurrences.get(root))
          .orElseThrow(() -> new IllegalArgumentException("Written method attribution requires its parsed body association"));
      declarationPaths.retainStatementOffset(root.sourceFileKey(), association.ownerPath(), statementOffset);
    }
  }

  private static int methodStatementOffset(ExecutionRegion region, SyntaxStructuralPath compilerRoot) {
    var rootDepth = region.facts().root().structuralPath().steps().size();
    for (var node : region.descendants()) {
      var address = region.addresses().get(node.nodeId());
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

}
