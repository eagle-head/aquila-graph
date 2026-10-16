package org.aquilagraph.frontend;

import com.sun.source.tree.ClassTree;
import com.sun.source.util.TreePath;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxStructuralPath;

/** Fixes an executable operation's exact policy and permitted lexical collaboration. */
final class JavaExecutableProjectionContext {
  private final JavaTranslationPolicy selectedPolicy;
  private final Operation operation;

  private JavaExecutableProjectionContext(JavaTranslationPolicy policy, Operation operation) {
    selectedPolicy = Objects.requireNonNull(policy, "policy");
    this.operation = Objects.requireNonNull(operation, "operation");
  }

  static JavaExecutableProjectionContext metadata(JavaTranslationPolicy policy) {
    return new JavaExecutableProjectionContext(policy, EarlyBody.INSTANCE);
  }

  static JavaExecutableProjectionContext canonicalMethod(JavaTranslationPolicy policy, DeclarationProjection declarations) {
    requireNested(policy);
    return new JavaExecutableProjectionContext(policy, new CanonicalSource(CanonicalPurpose.METHOD, declarations));
  }

  static JavaExecutableProjectionContext memberInitialization(JavaTranslationPolicy policy) {
    requireInitialization(policy);
    if (policy.supportsNestedDeclarations()) {
      throw new IllegalArgumentException("Canonical nested initialization requires its lexical declaration operation");
    }
    return new JavaExecutableProjectionContext(policy, MemberInitialization.INSTANCE);
  }

  static JavaExecutableProjectionContext memberInitialization(JavaTranslationPolicy policy, DeclarationProjection declarations) {
    requireInitialization(policy);
    requireNested(policy);
    return new JavaExecutableProjectionContext(policy, new CanonicalSource(CanonicalPurpose.MEMBER_INITIALIZATION, declarations));
  }

  static JavaExecutableProjectionContext enumInitialization(JavaTranslationPolicy policy, DeclarationProjection declarations) {
    requireNested(policy);
    if (!policy.supportsEnumInitialization()) {
      throw new IllegalArgumentException("The selected profile does not project enum initialization");
    }
    return new JavaExecutableProjectionContext(policy, new CanonicalSource(CanonicalPurpose.ENUM_INITIALIZATION, declarations));
  }

  static JavaExecutableProjectionContext remainingAttributedBody(JavaTranslationPolicy policy) {
    requireNested(policy);
    return new JavaExecutableProjectionContext(policy, AttributedBody.INSTANCE);
  }

  JavaTranslationPolicy policy() {
    return selectedPolicy;
  }

  Optional<DeclarationProjection> declarations() {
    return operation instanceof CanonicalSource source ? Optional.of(source.declarations()) : Optional.empty();
  }

  boolean supportsNestedDeclarations() {
    return selectedPolicy.supportsNestedDeclarations() && operation instanceof CanonicalSource;
  }

  void requireProjection() {
    if (operation == EarlyBody.INSTANCE && selectedPolicy.supportsNestedDeclarations()) {
      throw new IllegalStateException("Nested-capable declaration metadata defers executable projection to canonical source");
    }
  }

  void requireMethodBody() {
    if (operation != EarlyBody.INSTANCE && operation != AttributedBody.INSTANCE
        && !(operation instanceof CanonicalSource source && source.purpose() == CanonicalPurpose.METHOD)) {
      throw new IllegalArgumentException("A method body requires a metadata, canonical method or remaining attributed operation");
    }
  }

  private static void requireNested(JavaTranslationPolicy policy) {
    if (!Objects.requireNonNull(policy, "policy").supportsNestedDeclarations()) {
      throw new IllegalArgumentException("The selected profile does not project nested declarations");
    }
  }

  private static void requireInitialization(JavaTranslationPolicy policy) {
    if (!Objects.requireNonNull(policy, "policy").supportsMemberInitialization()) {
      throw new IllegalArgumentException("The selected profile does not project member initialization");
    }
  }

  @FunctionalInterface
  interface DeclarationProjection {
    TranslationResult<SyntaxNode> project(ClassTree tree, TreePath path, SyntaxStructuralPath structuralPath);
  }

  private sealed interface Operation permits EarlyBody, AttributedBody, MemberInitialization, CanonicalSource {
  }

  private enum EarlyBody implements Operation {
    INSTANCE
  }

  private enum AttributedBody implements Operation {
    INSTANCE
  }

  private enum MemberInitialization implements Operation {
    INSTANCE
  }

  private enum CanonicalPurpose {
    METHOD, MEMBER_INITIALIZATION, ENUM_INITIALIZATION
  }

  private record CanonicalSource(CanonicalPurpose purpose, DeclarationProjection declarations) implements Operation {
    private CanonicalSource {
      Objects.requireNonNull(purpose, "purpose");
      Objects.requireNonNull(declarations, "declarations");
    }
  }
}
