package org.aquilagraph.source.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import org.aquilagraph.identity.api.SourceSpan;

/** Intrinsic binding ownership and referability within an admitted executable body. */
final class LocalBindingDeclarations {
  private LocalBindingDeclarations() {
  }

  static void validate(String methodKey, SyntaxNode root) {
    var bindings = new HashMap<String, SyntaxNode>();
    var nodes = root.descendantsAndSelf();
    var parents = new HashMap<String, SyntaxNode>();
    for (var node : nodes) {
      node.children().forEach(child -> parents.put(child.node().nodeId(), node));
      if (node.value() instanceof SyntaxValue.LocalDeclaration local) {
        local.bindingEvidence().ifPresent(evidence -> validateContext(node, evidence.context(), parents));
        var copiedKey = local.bindingEvidence().flatMap(LocalBindingEvidence::declarationKey);
        var key = copiedKey.or(() -> node.semanticReference().flatMap(SemanticReference::symbolKey));
        key.ifPresent(identity -> {
          if (!identity.startsWith(methodKey + ':') || bindings.putIfAbsent(identity, node) != null) {
            throw new IllegalArgumentException("A local binding requires a unique identity in its declared executable owner");
          }
        });
      }
    }
    for (var node : nodes) {
      if (node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS) {
        node.semanticReference()
            .filter(reference -> reference.referenceKind() == ReferenceKind.LOCAL && reference.status() == ResolutionStatus.RESOLVED)
            .flatMap(SemanticReference::symbolKey).ifPresent(key -> validateAccess(methodKey, node, key, bindings, parents));
      }
    }
    validatePatternScopes(nodes);
  }

  private static void validatePatternScopes(List<SyntaxNode> nodes) {
    for (var pattern : nodes) {
      if (pattern.syntaxKind() != SyntaxKind.BINDING_PATTERN) {
        continue;
      }
      var binding = pattern.requiredChild(SyntaxRole.PARAMETER).value(SyntaxValue.LocalDeclaration.class);
      var key = binding.bindingEvidence().flatMap(LocalBindingEvidence::declarationKey);
      pattern.value(SyntaxValue.PatternBinding.class).resolvedAccesses().ifPresent(accesses -> {
        for (var origin : accesses) {
          var matches = nodes.stream()
              .anyMatch(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS && node.origin().equals(Optional.of(origin))
                  && node.semanticReference().flatMap(SemanticReference::symbolKey).equals(key)
                  && binding.name().equals(node.value(SyntaxValue.SourceSymbol.class).name()));
          if (!matches && nodes.stream().noneMatch(node -> node.syntaxKind() == SyntaxKind.UNSUPPORTED && contains(node, origin))) {
            throw new IllegalArgumentException("Copied pattern scope must select actual accesses to its exact named binding");
          }
        }
      });
    }
  }

  private static boolean contains(SyntaxNode node, SourceSpan access) {
    return node.origin().filter(origin -> origin.sourceFileKey().equals(access.sourceFileKey())
        && origin.startOffset() <= access.startOffset() && origin.endOffsetExclusive() >= access.endOffsetExclusive()).isPresent();
  }

  private static void validateContext(SyntaxNode declaration, LocalBindingEvidence.Context context, Map<String, SyntaxNode> parents) {
    var parent = Optional.ofNullable(parents.get(declaration.nodeId())).orElseThrow();
    var role = declaration.structuralPath().steps().getLast().role();
    var expected = switch (parent.sourceKind()) {
      case CATCH -> role == SyntaxRole.PARAMETER ? LocalBindingEvidence.Context.CATCH : LocalBindingEvidence.Context.LOCAL;
      case ENHANCED_FOR_LOOP ->
        role == SyntaxRole.PARAMETER ? LocalBindingEvidence.Context.ENHANCED_FOR : LocalBindingEvidence.Context.LOCAL;
      case TRY -> role == SyntaxRole.RESOURCE ? LocalBindingEvidence.Context.RESOURCE : LocalBindingEvidence.Context.LOCAL;
      case LAMBDA_EXPRESSION -> role == SyntaxRole.PARAMETER ? LocalBindingEvidence.Context.LAMBDA : LocalBindingEvidence.Context.LOCAL;
      case BINDING_PATTERN -> LocalBindingEvidence.Context.PATTERN;
      default -> LocalBindingEvidence.Context.LOCAL;
    };
    if (context != expected) {
      throw new IllegalArgumentException("A local binding context must agree with its source attachment");
    }
  }

  private static void validateAccess(String methodKey, SyntaxNode access, String key, Map<String, SyntaxNode> bindings,
      Map<String, SyntaxNode> parents) {
    var declaration = bindings.get(key);
    if (declaration == null && key.startsWith(methodKey + ':') && hasUnsupportedOwner(access, parents)) {
      return;
    }
    if (declaration == null || "_".equals(declaration.value(SyntaxValue.LocalDeclaration.class).name())
        || !declaration.value(SyntaxValue.LocalDeclaration.class).name().equals(access.value(SyntaxValue.SourceSymbol.class).name())) {
      throw new IllegalArgumentException("A local access must select its owned named binding");
    }
    var parent = Optional.ofNullable(parents.get(declaration.nodeId())).orElseThrow();
    if (parent.syntaxKind() == SyntaxKind.BINDING_PATTERN) {
      var scope = parent.value(SyntaxValue.PatternBinding.class).resolvedAccesses();
      if (scope.isPresent() && !scope.orElseThrow().contains(access.origin().orElseThrow())) {
        throw new IllegalArgumentException("A pattern access must agree with its copied compiler-established flow scope");
      }
      return;
    }
    var scopePath = parent.structuralPath().steps();
    var accessPath = access.structuralPath().steps();
    if (accessPath.size() <= scopePath.size() || !accessPath.subList(0, scopePath.size()).equals(scopePath)) {
      throw new IllegalArgumentException("A local access must remain within its binding's lexical scope");
    }
    var declarationStep = declaration.structuralPath().steps().getLast();
    var accessStep = accessPath.get(scopePath.size());
    if (parent.syntaxKind() == SyntaxKind.BLOCK && declarationStep.role() == SyntaxRole.STATEMENT
        && (accessStep.role() != SyntaxRole.STATEMENT || accessStep.position() < declarationStep.position())) {
      throw new IllegalArgumentException("A local access cannot precede its binding declaration");
    }
    if (parent.syntaxKind() == SyntaxKind.CATCH && accessStep.role() != SyntaxRole.BODY) {
      throw new IllegalArgumentException("An exception binding is visible only within its catch body");
    }
    if (parent.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION && accessStep.role() != SyntaxRole.BODY) {
      throw new IllegalArgumentException("A lambda binding is visible only within its lambda body");
    }
    if (parent.syntaxKind() == SyntaxKind.ENHANCED_FOR_LOOP && accessStep.role() != SyntaxRole.BODY) {
      throw new IllegalArgumentException("An enhanced-for binding is visible only within its loop body");
    }
    if (parent.syntaxKind() == SyntaxKind.FOR_LOOP && accessStep.role() == SyntaxRole.INITIALIZER
        && accessStep.position() < declarationStep.position()) {
      throw new IllegalArgumentException("A basic-for initializer cannot refer to a later binding");
    }
  }

  private static boolean hasUnsupportedOwner(SyntaxNode node, Map<String, SyntaxNode> parents) {
    var parent = parents.get(node.nodeId());
    while (parent != null) {
      if (parent.syntaxKind() == SyntaxKind.UNSUPPORTED) {
        return true;
      }
      parent = parents.get(parent.nodeId());
    }
    return false;
  }
}
