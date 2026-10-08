package org.aquilagraph.source.api;

import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.aquilagraph.source.api.SyntaxValue.ExecutableDeclaration;
import org.aquilagraph.source.api.SyntaxValue.ExecutableDeclaration.ExecutableKind;
import org.aquilagraph.source.api.SyntaxValue.ParameterDeclaration;

/** Correlates canonical declaration values with independently retained written headers. */
final class CanonicalMethodCorrespondence {
  private static final String OWNED_ERASURE_CONFLICT = "A selected erasure must match its available owned type-variable bounds";
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
    var methods = program.methods().stream().collect(Collectors.toUnmodifiableMap(JavaMethod::methodKey, method -> method));
    for (var node : aggregate.nodes()) {
      if (node.value() instanceof SyntaxValue.MemberReference reference && reference.selectedType() instanceof SyntaxType.Resolved selected
          && selected.structure().orElse(null) instanceof JavaTypeStructure.Executable signature) {
        node.semanticReference().flatMap(SemanticReference::symbolKey).map(methods::get).ifPresent(method -> {
          if (method.parameters().size() != signature.erasure().parameters().size()) {
            throw new IllegalArgumentException("A selected erasure must match its canonical parameter count");
          }
          for (var index = 0; index < method.parameters().size(); index++) {
            JavaExecutableErasure.validateIdentity(method.parameters().get(index).staticType(),
                signature.erasure().parameters().get(index));
          }
        });
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
    var parameters = parameters(owner, method, node, declaration);
    var signature = parameters
        .map(values -> name + "(" + values.stream().map(value -> value.staticType().erasedName()).collect(Collectors.joining(",")) + ")");
    var expected = new JavaMethod(declaration.declarationKey(), declaration.ownerKey(), name, signature.orElse(method.signature()),
        returnType.orElse(method.returnType()), parameters.orElse(method.parameters()), modifiers, constructor, true,
        declaration.shape().body(), node.origin().stream().toList());
    if (!method.equals(expected)) {
      throw new IllegalArgumentException("Canonical method declaration contradicts its retained written header: " + method.methodKey());
    }
  }

  private static Optional<List<JavaParameter>> parameters(JavaType owner, JavaMethod method, SyntaxNode node,
      ExecutableDeclaration declaration) {
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
    if (nodes.size() != method.parameters().size()) {
      throw new IllegalArgumentException("Canonical method parameter count contradicts its retained written header");
    }
    return Optional.of(nodes.stream().map(value -> {
      var parameter = (ParameterDeclaration) value.value();
      return new JavaParameter(parameter.declarationKey(), parameter.name(),
          type(value.requiredChild(SyntaxRole.DECLARED_TYPE)).orElse(method.parameters().get(nodes.indexOf(value)).staticType()), true,
          value.origin().stream().toList());
    }).toList());
  }

  private static Optional<JavaTypeIdentity> type(SyntaxNode node) {
    return node.type().knownIdentity();
  }

  static void validateSelection(SyntaxType selectedType, SyntaxNode declaration, Map<String, SyntaxNode> nodes) {
    if (!(selectedType instanceof SyntaxType.Resolved selected)
        || !(selected.structure().orElse(null) instanceof JavaTypeStructure.Executable signature)) {
      return;
    }
    var executable = declaration.value(ExecutableDeclaration.class);
    var typeParameters = nodes.values().stream().filter(node -> node.value() instanceof SyntaxValue.TypeParameterDeclaration).collect(
        Collectors.toUnmodifiableMap(node -> node.value(SyntaxValue.TypeParameterDeclaration.class).declarationKey(), node -> node));
    var bounds = typeParameters.values().stream().map(node -> node.value(SyntaxValue.TypeParameterDeclaration.class)).collect(Collectors
        .toUnmodifiableMap(SyntaxValue.TypeParameterDeclaration::declarationKey, SyntaxValue.TypeParameterDeclaration::upperBound));
    var variables = declaration.children(SyntaxRole.TYPE_PARAMETER).stream()
        .map(node -> new JavaTypeStructure.Variable(node.value(SyntaxValue.TypeParameterDeclaration.class).declarationKey())).toList();
    if (!variables.equals(signature.variables())) {
      throw new IllegalArgumentException("A selected executable signature must match its owned type variables");
    }
    var parameters = executable.kind() == ExecutableKind.COMPACT_CONSTRUCTOR
        ? nodes.values().stream()
            .filter(
                node -> node.value() instanceof SyntaxValue.TypeDeclaration owner && owner.declarationKey().equals(executable.ownerKey()))
            .flatMap(node -> node.children(SyntaxRole.RECORD_COMPONENT).stream()).map(node -> node.requiredChild(SyntaxRole.DECLARED_TYPE))
            .toList()
        : declaration.children(SyntaxRole.PARAMETER).stream().map(node -> node.requiredChild(SyntaxRole.DECLARED_TYPE)).toList();
    if (parameters.size() != signature.parameters().size()) {
      throw new IllegalArgumentException("A selected executable signature must match its owned parameter count");
    }
    for (var index = 0; index < parameters.size(); index++) {
      SyntaxNode.validateKnownTypeUseType(parameters.get(index), signature.parameters().get(index));
      SyntaxNode.validateKnownTypeUseErasure(parameters.get(index), signature.erasure().parameters().get(index));
      validateOwnedErasure(parameters.get(index), signature.parameters().get(index), signature.erasure().parameters().get(index), bounds,
          typeParameters);
    }
    declaration.optionalChild(SyntaxRole.RETURN_TYPE).ifPresent(node -> {
      SyntaxNode.validateKnownTypeUseType(node, signature.returnType());
      SyntaxNode.validateKnownTypeUseErasure(node, signature.erasure().returnType());
      validateOwnedErasure(node, signature.returnType(), signature.erasure().returnType(), bounds, typeParameters);
    });
    var thrownTypes = executable.thrownTypes();
    if (thrownTypes.size() != signature.thrownTypes().size()) {
      throw new IllegalArgumentException("A selected executable signature must match its owned thrown type count");
    }
    var writtenThrownTypes = declaration.children(SyntaxRole.THROWN_TYPE);
    for (var index = 0; index < thrownTypes.size(); index++) {
      validateKnownStructure(thrownTypes.get(index), signature.thrownTypes().get(index));
      SyntaxNode.validateKnownTypeUseType(writtenThrownTypes.get(index), signature.thrownTypes().get(index));
    }
    var receiver = executable.attributedHeader().receiverType();
    if ((receiver != SyntaxType.NotApplicable.INSTANCE) != signature.receiver().isPresent()) {
      throw new IllegalArgumentException("A selected executable signature must match its owned receiver presence");
    }
    signature.receiver().ifPresent(value -> validateKnownStructure(receiver, value));
  }

  private static void validateOwnedErasure(SyntaxNode written, SyntaxType semantic, SyntaxType erased,
      Map<String, JavaTypeStructure> bounds, Map<String, SyntaxNode> typeParameters) {
    var erasedName = erased.knownIdentity().orElseThrow().erasedName();
    if (writtenVariableErasureName(written, typeParameters).filter(name -> !name.equals(erasedName)).isPresent()) {
      throw new IllegalArgumentException(OWNED_ERASURE_CONFLICT);
    }
    if (semantic instanceof SyntaxType.Resolved selected && selected.structure().isPresent()
        && ownedVariableErasureName(selected.structure().orElseThrow(), typeParameters).filter(name -> !name.equals(erasedName))
            .isPresent()) {
      throw new IllegalArgumentException(OWNED_ERASURE_CONFLICT);
    }
    if (erased instanceof SyntaxType.Resolved raw && raw.structure().isPresent()) {
      var structure = raw.structure().orElseThrow();
      if (semantic instanceof SyntaxType.Resolved resolved && resolved.structure().isPresent()
          && !JavaExecutableErasure.matchesOwnedErasure(resolved.structure().orElseThrow(), structure, bounds, new HashSet<>())) {
        throw new IllegalArgumentException(OWNED_ERASURE_CONFLICT);
      }
      validateWrittenVariableErasure(written, structure, bounds);
    }
  }

  private static void validateWrittenVariableErasure(SyntaxNode written, JavaTypeStructure erased, Map<String, JavaTypeStructure> bounds) {
    if (written.sourceKind() == JavaSourceKind.ANNOTATED_TYPE) {
      validateWrittenVariableErasure(written.requiredChild(SyntaxRole.UNDERLYING_TYPE), erased, bounds);
    } else if (written.sourceKind() == JavaSourceKind.ARRAY_TYPE && erased instanceof JavaTypeStructure.Array array) {
      validateWrittenVariableErasure(written.requiredChild(SyntaxRole.COMPONENT_TYPE), array.component(), bounds);
    } else {
      written.semanticReference()
          .filter(reference -> reference.status() == ResolutionStatus.RESOLVED && reference.referenceKind() == ReferenceKind.TYPE_PARAMETER)
          .flatMap(SemanticReference::symbolKey).ifPresent(key -> {
            if (!JavaExecutableErasure.matchesOwnedErasure(new JavaTypeStructure.Variable(key), erased, bounds, new HashSet<>())) {
              throw new IllegalArgumentException(OWNED_ERASURE_CONFLICT);
            }
          });
    }
  }

  private static Optional<String> writtenVariableErasureName(SyntaxNode written, Map<String, SyntaxNode> typeParameters) {
    return switch (written.sourceKind()) {
      case ANNOTATED_TYPE -> writtenVariableErasureName(written.requiredChild(SyntaxRole.UNDERLYING_TYPE), typeParameters);
      case ARRAY_TYPE ->
        writtenVariableErasureName(written.requiredChild(SyntaxRole.COMPONENT_TYPE), typeParameters).map(name -> name + "[]");
      default -> written.semanticReference()
          .filter(reference -> reference.status() == ResolutionStatus.RESOLVED && reference.referenceKind() == ReferenceKind.TYPE_PARAMETER)
          .flatMap(SemanticReference::symbolKey).flatMap(key -> typeParameterErasureName(key, typeParameters));
    };
  }

  private static Optional<String> ownedVariableErasureName(JavaTypeStructure selected, Map<String, SyntaxNode> typeParameters) {
    return switch (selected) {
      case JavaTypeStructure.Variable variable -> typeParameterErasureName(variable.parameterKey(), typeParameters);
      case JavaTypeStructure.Array array -> ownedVariableErasureName(array.component(), typeParameters).map(name -> name + "[]");
      default -> Optional.empty();
    };
  }

  private static Optional<String> typeParameterErasureName(String key, Map<String, SyntaxNode> typeParameters) {
    return Optional.ofNullable(typeParameters.get(key)).flatMap(node -> {
      var writtenBounds = node.children(SyntaxRole.TYPE_BOUND);
      if (writtenBounds.isEmpty()) {
        return Optional.of("java.lang.Object");
      }
      return node.type().knownIdentity().or(() -> writtenBounds.getFirst().type().knownIdentity()).map(JavaTypeIdentity::erasedName);
    });
  }

  private static void validateKnownStructure(SyntaxType written, SyntaxType selected) {
    if (written.knownIdentity()
        .filter(identity -> selected.knownIdentity()
            .filter(value -> identity.category() != value.category() || !identity.erasedName().equals(value.erasedName())).isPresent())
        .isPresent()
        || (written instanceof SyntaxType.Resolved resolved && selected instanceof SyntaxType.Resolved selectedResolved
            && resolved.structure().isPresent() && selectedResolved.structure().isPresent()
            && !resolved.structure().equals(selectedResolved.structure()))) {
      throw new IllegalArgumentException("A selected executable signature must match its owned attributed header components");
    }
  }
}
