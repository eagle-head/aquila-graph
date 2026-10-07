package org.aquilagraph.source.api;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.aquilagraph.source.api.SyntaxValue.ExecutableDeclaration;
import org.aquilagraph.source.api.SyntaxValue.ExecutableDeclaration.ExecutableKind;
import org.aquilagraph.source.api.SyntaxValue.ParameterDeclaration;

/** Correlates canonical declaration values with independently retained written headers. */
final class CanonicalMethodCorrespondence {
  private CanonicalMethodCorrespondence() {
  }

  static void validate(JavaProgram program, JavaAstAggregate aggregate) {
    var declarations = aggregate.nodes().stream().filter(node -> node.value() instanceof ExecutableDeclaration)
        .collect(Collectors.toUnmodifiableMap(node -> ((ExecutableDeclaration) node.value()).declarationKey(), node -> node));
    for (var owner : program.types()) {
      for (var method : owner.methods()) {
        var node = declarations.get(method.methodKey());
        if (node != null) {
          validate(owner, method, node, (ExecutableDeclaration) node.value());
        }
      }
    }
  }

  private static void validate(JavaType owner, JavaMethod method, SyntaxNode node, ExecutableDeclaration declaration) {
    var constructor = declaration.kind() == ExecutableKind.CONSTRUCTOR || declaration.kind() == ExecutableKind.COMPACT_CONSTRUCTOR;
    var name = constructor ? "<init>" : declaration.name();
    var modifiers = declaration.effectiveModifiers().stream().map(value -> value.name().toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
    var returnType = constructor
        ? Optional.of("void")
        : node.optionalChild(SyntaxRole.RETURN_TYPE).flatMap(CanonicalMethodCorrespondence::type).map(JavaTypeIdentity::displayName);
    var parameters = parameters(owner, node, declaration);
    var signature = parameters
        .map(values -> name + "(" + values.stream().map(value -> value.staticType().erasedName()).collect(Collectors.joining(",")) + ")");
    var expected = new JavaMethod(declaration.declarationKey(), declaration.ownerKey(), name, signature.orElse(method.signature()),
        returnType.orElse(method.returnType()), parameters.orElse(method.parameters()), modifiers, constructor, true,
        declaration.shape().body(), node.origin().stream().toList());
    if (!method.equals(expected)) {
      throw new IllegalArgumentException("Canonical method declaration contradicts its retained written header: " + method.methodKey());
    }
  }

  private static Optional<List<JavaParameter>> parameters(JavaType owner, SyntaxNode node, ExecutableDeclaration declaration) {
    if (declaration.kind() == ExecutableKind.COMPACT_CONSTRUCTOR) {
      var components = owner.recordComponents();
      if (components.stream().anyMatch(value -> value.canonicalParameterKey().isEmpty()
          || value.staticType().category() == JavaTypeCategory.ERROR || value.staticType().category() == JavaTypeCategory.OTHER)) {
        return Optional.empty();
      }
      return Optional.of(components.stream()
          .map(value -> new JavaParameter(value.canonicalParameterKey().orElseThrow(), value.name(), value.staticType(), false, List.of()))
          .toList());
    }
    var nodes = node.children(SyntaxRole.PARAMETER);
    if (nodes.stream().anyMatch(value -> !(value.value() instanceof ParameterDeclaration)
        || value.optionalChild(SyntaxRole.DECLARED_TYPE).flatMap(CanonicalMethodCorrespondence::type).isEmpty())) {
      return Optional.empty();
    }
    return Optional.of(nodes.stream().map(value -> {
      var parameter = (ParameterDeclaration) value.value();
      return new JavaParameter(parameter.declarationKey(), parameter.name(),
          type(value.requiredChild(SyntaxRole.DECLARED_TYPE)).orElseThrow(), true, value.origin().stream().toList());
    }).toList());
  }

  private static Optional<JavaTypeIdentity> type(SyntaxNode node) {
    return node.type() instanceof SyntaxType.Resolved resolved ? Optional.of(resolved.identity()) : Optional.empty();
  }
}
