package org.aquilagraph.source.internal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;

/** Checks indirect enclosing views against the source facts available in one aggregate. */
final class EnclosingTypeAdmission {
  private final Map<String, SyntaxNode> declarations = new HashMap<>();
  private final Map<String, JavaTypeStructure> bounds = new HashMap<>();

  private EnclosingTypeAdmission(List<SyntaxNode> nodes) {
    for (var node : nodes) {
      if (node.value() instanceof SyntaxValue.TypeDeclaration declaration) {
        declarations.put(declaration.declarationKey(), node);
      } else if (node.value() instanceof SyntaxValue.TypeParameterDeclaration parameter) {
        bounds.put(parameter.declarationKey(), parameter.upperBound());
      }
    }
  }

  static void validate(List<SyntaxNode> nodes) {
    var admission = new EnclosingTypeAdmission(nodes);
    for (var node : nodes) {
      structure(node.type()).ifPresent(admission::validate);
      if (node.value() instanceof SyntaxValue.TypeParameterDeclaration parameter) {
        admission.validate(parameter.upperBound());
      }
      if (node.syntaxKind() == SyntaxKind.OBJECT_CREATION
          && structure(node.type()).orElse(null) instanceof JavaTypeStructure.Declared declared
          && declared.enclosingOwnerType().isPresent()) {
        node.optionalChild(SyntaxRole.ENCLOSING_INSTANCE).flatMap(qualifier -> structure(qualifier.type())).ifPresent(actual -> {
          if (!declared.enclosingType().equals(Optional.of(actual))) {
            throw new IllegalArgumentException("An indirect enclosing instance must match its written qualifier type");
          }
        });
      }
    }
  }

  private void validate(JavaTypeStructure type) {
    validate(type, new EnclosingCaptureScope(type));
  }

  private void validate(JavaTypeStructure type, EnclosingCaptureScope captures) {
    switch (type) {
      case JavaTypeStructure.Declared declared -> {
        declared.enclosingType().ifPresent(value -> validate(value, captures));
        declared.arguments().forEach(value -> validate(value, captures));
        declared.enclosingOwnerType().ifPresent(view -> {
          var expected = owner(declared.enclosingType().orElseThrow(), declared.lexicalOwnerKey().orElseThrow(), new OwnerPath(), captures);
          if ((expected.complete() && expected.view().isEmpty())
              || expected.view().filter(value -> !captures.normalize(value).equals(view)).isPresent()) {
            throw new IllegalArgumentException("An enclosing owner view conflicts with available source bounds");
          }
          validate(view);
        });
      }
      case JavaTypeStructure.LocalDeclared local -> {
        local.enclosingType().ifPresent(value -> validate(value, captures));
        local.arguments().forEach(value -> validate(value, captures));
      }
      case JavaTypeStructure.Captured captured -> {
        validate(captured.upperBound(), captures);
        captured.lowerBound().ifPresent(value -> validate(value, captures));
      }
      case JavaTypeStructure.Array array -> validate(array.component(), captures);
      case JavaTypeStructure.Wildcard wildcard -> wildcard.bound().ifPresent(value -> validate(value, captures));
      case JavaTypeStructure.Intersection intersection -> intersection.bounds().forEach(value -> validate(value, captures));
      case JavaTypeStructure.Union union -> union.alternatives().forEach(value -> validate(value, captures));
      case JavaTypeStructure.Executable executable -> {
        executable.variables().forEach(this::validate);
        executable.parameters().forEach(value -> structure(value).ifPresent(this::validate));
        structure(executable.returnType()).ifPresent(this::validate);
        executable.receiver().flatMap(EnclosingTypeAdmission::structure).ifPresent(this::validate);
        executable.thrownTypes().forEach(value -> structure(value).ifPresent(this::validate));
      }
      case JavaTypeStructure.Variable _,JavaTypeStructure.CaptureReference _,JavaTypeStructure.Primitive _ -> {
      }
    }
  }

  private OwnerSearch owner(JavaTypeStructure instance, String ownerKey, OwnerPath visited, EnclosingCaptureScope captures) {
    return switch (instance) {
      case JavaTypeStructure.Variable variable -> visited.declarations.add(variable.parameterKey())
          ? Optional.ofNullable(bounds.get(variable.parameterKey())).map(bound -> owner(bound, ownerKey, visited, captures))
              .orElseGet(OwnerSearch::unknown)
          : OwnerSearch.unknown();
      case JavaTypeStructure.Captured captured -> visited.captures.add(captured.ordinal())
          ? owner(captured.upperBound(), ownerKey, visited, captures)
          : new OwnerSearch(Optional.empty(), true);
      case JavaTypeStructure.CaptureReference reference ->
        captures.resolve(reference).map(value -> owner(value, ownerKey, visited, captures)).orElseGet(OwnerSearch::unknown);
      case JavaTypeStructure.Intersection intersection -> search(intersection.bounds(), ownerKey, visited, captures);
      case JavaTypeStructure.Declared declared -> declared.declarationKey().equals(ownerKey)
          ? new OwnerSearch(Optional.of(declared), true)
          : inheritedOwner(declared.declarationKey(), declared.enclosingType(), declared.arguments(), ownerKey, visited, captures);
      case JavaTypeStructure.LocalDeclared local -> local.declarationKey().equals(ownerKey)
          ? new OwnerSearch(Optional.of(local), true)
          : inheritedOwner(local.declarationKey(), local.enclosingType(), local.arguments(), ownerKey, visited, captures);
      default -> OwnerSearch.unknown();
    };
  }

  private OwnerSearch inheritedOwner(String declarationKey, Optional<JavaTypeStructure> enclosing, List<JavaTypeStructure> arguments,
      String ownerKey, OwnerPath visited, EnclosingCaptureScope captures) {
    var declaration = declarations.get(declarationKey);
    if (declaration == null || !visited.declarations.add(declarationKey)) {
      return OwnerSearch.unknown();
    }
    var substitutions = new HashMap<String, JavaTypeStructure>();
    if (!bind(declarationKey, enclosing, arguments, substitutions)) {
      return OwnerSearch.unknown();
    }
    var parents = declaration.children().stream()
        .filter(child -> child.role() == SyntaxRole.SUPERCLASS || child.role() == SyntaxRole.INTERFACE).toList();
    if (parents.stream().anyMatch(child -> structure(child.node().type()).isEmpty())) {
      return OwnerSearch.unknown();
    }
    return search(parents.stream().map(child -> substitute(structure(child.node().type()).orElseThrow(), substitutions)).toList(), ownerKey,
        visited, captures);
  }

  private OwnerSearch search(List<JavaTypeStructure> parents, String ownerKey, OwnerPath visited, EnclosingCaptureScope captures) {
    var complete = true;
    for (var parent : parents) {
      var found = owner(parent, ownerKey, visited.fork(), captures);
      if (found.view().isPresent()) {
        return found;
      }
      complete &= found.complete();
    }
    return new OwnerSearch(Optional.empty(), complete);
  }

  private record OwnerSearch(Optional<JavaTypeStructure> view, boolean complete) {
    static OwnerSearch unknown() {
      return new OwnerSearch(Optional.empty(), false);
    }
  }

  private static final class OwnerPath {
    private final Set<String> declarations = new HashSet<>();
    private final Set<Integer> captures = new HashSet<>();

    private OwnerPath fork() {
      var fork = new OwnerPath();
      fork.declarations.addAll(declarations);
      fork.captures.addAll(captures);
      return fork;
    }
  }

  private boolean bind(String declarationKey, Optional<JavaTypeStructure> enclosing, List<JavaTypeStructure> arguments,
      Map<String, JavaTypeStructure> substitutions) {
    var declaration = declarations.get(declarationKey);
    if (declaration == null) {
      return false;
    }
    var parameters = declaration.children(SyntaxRole.TYPE_PARAMETER);
    if (parameters.size() != arguments.size()) {
      return false;
    }
    for (var index = 0; index < parameters.size(); index++) {
      substitutions.put(parameters.get(index).value(SyntaxValue.TypeParameterDeclaration.class).declarationKey(), arguments.get(index));
    }
    return switch (enclosing.orElse(null)) {
      case JavaTypeStructure.Declared outer -> bind(outer.declarationKey(), outer.enclosingType(), outer.arguments(), substitutions);
      case JavaTypeStructure.LocalDeclared outer -> bind(outer.declarationKey(), outer.enclosingType(), outer.arguments(), substitutions);
      case null, default -> true;
    };
  }

  private static JavaTypeStructure substitute(JavaTypeStructure type, Map<String, JavaTypeStructure> substitutions) {
    return switch (type) {
      case JavaTypeStructure.Variable variable -> substitutions.getOrDefault(variable.parameterKey(), variable);
      case JavaTypeStructure.Declared declared -> new JavaTypeStructure.Declared(declared.declarationKey(), declared.lexicalOwnerKey(),
          declared.enclosingType().map(value -> substitute(value, substitutions)),
          declared.arguments().stream().map(value -> substitute(value, substitutions)).toList(),
          declared.enclosingOwnerType().map(value -> substitute(value, substitutions)));
      case JavaTypeStructure.LocalDeclared local -> new JavaTypeStructure.LocalDeclared(local.declarationKey(), local.lexicalOwnerKey(),
          local.kind(), local.enclosingType().map(value -> substitute(value, substitutions)),
          local.arguments().stream().map(value -> substitute(value, substitutions)).toList());
      case JavaTypeStructure.Captured captured -> new JavaTypeStructure.Captured(captured.ordinal(),
          substitute(captured.upperBound(), substitutions), captured.lowerBound().map(value -> substitute(value, substitutions)));
      case JavaTypeStructure.Array array -> new JavaTypeStructure.Array(substitute(array.component(), substitutions));
      case JavaTypeStructure.Wildcard wildcard ->
        new JavaTypeStructure.Wildcard(wildcard.kind(), wildcard.bound().map(value -> substitute(value, substitutions)));
      case JavaTypeStructure.Intersection intersection ->
        new JavaTypeStructure.Intersection(intersection.bounds().stream().map(value -> substitute(value, substitutions)).toList());
      default -> type;
    };
  }

  private static Optional<JavaTypeStructure> structure(SyntaxType type) {
    return type instanceof SyntaxType.Resolved resolved ? resolved.structure() : Optional.empty();
  }
}
