package org.aquilagraph.frontend;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import org.aquilagraph.identity.api.SourceFile;
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

  void requireOwner(SemanticResolver semantics, JavaSourceEvidence evidence, SourceFile source, String scopeKey, TreePath path,
      boolean constructor, boolean compilerGenerated) {
    var parent = path.getParentPath();
    if (parent == null) {
      throw new IllegalArgumentException("Executable projection requires its enclosing declaration");
    }
    if (operation == EarlyBody.INSTANCE || operation == AttributedBody.INSTANCE
        || (operation instanceof CanonicalSource canonical && canonical.purpose() == CanonicalPurpose.METHOD)) {
      if (!(parent.getLeaf() instanceof MethodTree method) || !JavaProjectionIdentity.sameInstance(method.getBody(), path.getLeaf())
          || !semantics.declarationKey(semantics.element(parent)).equals(Optional.of(scopeKey))
          || constructor != (method.getReturnType() == null)) {
        throw new IllegalArgumentException("Executable method projection requires its exact attributed method owner");
      }
      return;
    }
    if (constructor || compilerGenerated) {
      throw new IllegalArgumentException("Initialization projection cannot use method generation flags");
    }
    if (operation instanceof CanonicalSource canonical && canonical.purpose() == CanonicalPurpose.ENUM_INITIALIZATION) {
      var fieldPath = parent.getParentPath();
      if (!(parent.getLeaf() instanceof NewClassTree creation) || fieldPath == null || !(fieldPath.getLeaf() instanceof VariableTree field)
          || !JavaProjectionIdentity.sameInstance(field.getInitializer(), creation)
          || creation.getArguments().stream().noneMatch(argument -> JavaProjectionIdentity.sameInstance(argument, path.getLeaf()))
          || semantics.element(fieldPath) == null || semantics.element(fieldPath).getKind() != ElementKind.ENUM_CONSTANT
          || !semantics.declarationKey(semantics.element(fieldPath)).equals(Optional.of(scopeKey))) {
        throw new IllegalArgumentException("Enum argument projection requires its exact enum constant owner");
      }
      return;
    }
    if (path.getLeaf() instanceof BlockTree block && parent.getLeaf() instanceof ClassTree) {
      var owner = semantics.declarationKey(semantics.element(parent));
      var range = evidence.origin(path.getCompilationUnit(), source, block);
      if (owner.isPresent() && range.isPresent()) {
        var origin = range.orElseThrow();
        var expected = owner.orElseThrow() + ":initializer:" + origin.startOffset() + ':' + origin.endOffsetExclusive();
        if (expected.equals(scopeKey)) {
          return;
        }
      }
    } else if (parent.getLeaf() instanceof VariableTree field && parent.getParentPath() != null
        && parent.getParentPath().getLeaf() instanceof ClassTree
        && JavaProjectionIdentity.sameInstance(field.getInitializer(), path.getLeaf())
        && semantics.declarationKey(semantics.element(parent)).equals(Optional.of(scopeKey)) && semantics.element(parent) != null
        && semantics.element(parent).getKind() == ElementKind.FIELD) {
      return;
    }
    throw new IllegalArgumentException("Member initialization projection requires its exact field or initializer scope");
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
