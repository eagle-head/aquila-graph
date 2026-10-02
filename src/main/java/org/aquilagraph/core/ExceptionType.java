package org.aquilagraph.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxRole;

record ExceptionType(String typeName, List<String> assignableToTypeNames, ExceptionDomainKind domainKind, boolean mayBeNull) {
  static final String NULL_POINTER_EXCEPTION = "java.lang.NullPointerException";

  ExceptionType {
    requireName(typeName);
    assignableToTypeNames = List.copyOf(Objects.requireNonNull(assignableToTypeNames, "assignableToTypeNames"));
    assignableToTypeNames.forEach(ExceptionType::requireName);
    Objects.requireNonNull(domainKind, "domainKind");
    if (domainKind == ExceptionDomainKind.UNKNOWN && !assignableToTypeNames.isEmpty()) {
      throw new IllegalArgumentException("An unknown exception cannot declare an assignability hierarchy");
    }
  }

  static ExceptionType resolved(String typeName, List<String> assignableToTypeNames) {
    var hierarchy = new LinkedHashSet<String>();
    hierarchy.add(Objects.requireNonNull(typeName, "typeName"));
    hierarchy.addAll(Objects.requireNonNull(assignableToTypeNames, "assignableToTypeNames"));
    return new ExceptionType(typeName, List.copyOf(hierarchy), ExceptionDomainKind.EXACT, false);
  }

  static ExceptionType subtypeBound(String typeName, List<String> assignableToTypeNames, boolean mayBeNull) {
    var exact = resolved(typeName, assignableToTypeNames);
    return new ExceptionType(typeName, exact.assignableToTypeNames(), ExceptionDomainKind.SUBTYPE_BOUND, mayBeNull);
  }

  static ExceptionType runtime(String typeName) {
    var ancestors = switch (typeName) {
      case "java.lang.ArithmeticException", "java.lang.ArrayStoreException", "java.lang.ClassCastException",
          "java.lang.NullPointerException", "java.lang.MatchException" ->
        List.of("java.lang.RuntimeException", "java.lang.Exception", "java.lang.Throwable", "java.lang.Object");
      case "java.lang.ArrayIndexOutOfBoundsException" -> List.of("java.lang.IndexOutOfBoundsException", "java.lang.RuntimeException",
          "java.lang.Exception", "java.lang.Throwable", "java.lang.Object");
      case "java.lang.OutOfMemoryError" ->
        List.of("java.lang.VirtualMachineError", "java.lang.Error", "java.lang.Throwable", "java.lang.Object");
      default -> List.<String>of();
    };
    return resolved(typeName, ancestors);
  }

  static ExceptionType fromSyntax(SyntaxNode node, String typeName) {
    Objects.requireNonNull(node, "node");
    var type = node.resolvedType().orElseThrow(() -> new IllegalStateException("An admitted throw requires a resolved value type"));
    if (type.category() == JavaTypeCategory.NULL) {
      return runtime(NULL_POINTER_EXCEPTION);
    }
    if (!type.erasedName().equals(typeName) || !referenceType(type.category())) {
      throw new IllegalStateException("An admitted throw requires its qualified reference-type bound");
    }
    node.semanticReference().ifPresent(reference -> {
      if (reference.status() != ResolutionStatus.RESOLVED) {
        throw new IllegalStateException("An admitted throw cannot carry unresolved declaration evidence");
      }
    });
    if (provenNull(node)) {
      return runtime(NULL_POINTER_EXCEPTION);
    }
    if (node.syntaxKind() == SyntaxKind.PARENTHESIZED) {
      return fromSyntax(node.requiredChild(SyntaxRole.EXPRESSION), typeName);
    }
    if (node.syntaxKind() == SyntaxKind.TYPE_CAST) {
      var source = node.requiredChild(SyntaxRole.EXPRESSION);
      var sourceType = source.resolvedType()
          .orElseThrow(() -> new IllegalStateException("An admitted cast requires an attributed operand type"));
      if (referenceType(sourceType.category())) {
        var sourceDomain = fromSyntax(source, sourceType.erasedName());
        if (sourceDomain.domainKind() == ExceptionDomainKind.EXACT && sourceDomain.assignableToTypeNames().contains(typeName)) {
          return sourceDomain;
        }
        var hierarchy = node.semanticReference().map(reference -> reference.assignableToTypeNames()).orElseGet(List::of);
        return subtypeBound(typeName, hierarchy, sourceDomain.mayBeNull());
      }
    }
    var hierarchy = node.semanticReference().map(reference -> reference.assignableToTypeNames()).orElseGet(List::of);
    return node.syntaxKind() == SyntaxKind.OBJECT_CREATION ? resolved(typeName, hierarchy) : subtypeBound(typeName, hierarchy, true);
  }

  static ExceptionType unknown(String category) {
    return new ExceptionType(category, List.of(), ExceptionDomainKind.UNKNOWN, false);
  }

  Optional<String> qualifiedTypeName() {
    return unknown() ? Optional.empty() : Optional.of(typeName);
  }

  boolean unknown() {
    return domainKind == ExceptionDomainKind.UNKNOWN;
  }

  List<ExceptionType> outcomes() {
    if (!mayBeNull) {
      return List.of(this);
    }
    return List.of(new ExceptionType(typeName, assignableToTypeNames, domainKind, false), runtime(NULL_POINTER_EXCEPTION));
  }

  private static boolean referenceType(JavaTypeCategory category) {
    return switch (category) {
      case DECLARED, TYPE_VARIABLE, INTERSECTION, UNION -> true;
      default -> false;
    };
  }

  private static boolean provenNull(SyntaxNode node) {
    var type = node.resolvedType().orElseThrow(() -> new IllegalStateException("An admitted throw requires attributed null provenance"));
    if (type.category() == JavaTypeCategory.NULL) {
      return true;
    }
    return switch (node.syntaxKind()) {
      case PARENTHESIZED -> provenNull(node.requiredChild(SyntaxRole.EXPRESSION));
      case TYPE_CAST -> (referenceType(type.category()) || type.category() == JavaTypeCategory.ARRAY)
          && provenNull(node.requiredChild(SyntaxRole.EXPRESSION));
      default -> false;
    };
  }

  private static void requireName(String name) {
    if (Objects.requireNonNull(name, "type name").isBlank()) {
      throw new IllegalArgumentException("An exception category or qualified type cannot be blank");
    }
  }
}
