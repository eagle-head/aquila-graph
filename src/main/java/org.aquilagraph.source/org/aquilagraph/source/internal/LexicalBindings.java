package org.aquilagraph.source.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath.Step;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxValue.ExecutableDeclaration.ExecutableKind;
import org.jspecify.annotations.Nullable;

/** Exact local and formal binding selection over admitted source attachments. */
final class LexicalBindings {
  private static final String FORMAL_KEY_COMPONENT = ":parameter:";
  private static final String OWNER_REQUIRED = "A local binding requires a unique identity in its declared executable owner";
  private static final String NAMED_BINDING_REQUIRED = "A local access must select its owned named binding";
  private static final String LEXICAL_SCOPE_REQUIRED = "A local access must remain within its binding's lexical scope";
  private static final String PATTERN_SCOPE_REQUIRED = "A pattern access must agree with its copied compiler-established flow scope";
  private static final String SWITCH_DECLARATION_ORDER = "A switch-block local access cannot precede its declaration";

  private final List<SyntaxNode> nodes;
  private final OccurrenceIndex<SyntaxNode> parents;
  private final OccurrenceIndex<String> executionOwners;
  private final SourceDeclarations sourceDeclarations;
  private final SourceMemberVisibility members;
  private final Map<String, List<Binding>> bindings;
  private final Map<BindingName, List<Binding>> namedBindings;

  private LexicalBindings(Collection<SyntaxNode> nodes, OccurrenceIndex<SyntaxNode> parents, OccurrenceIndex<String> executionOwners,
      SourceDeclarations sourceDeclarations) {
    this.nodes = List.copyOf(nodes);
    this.parents = parents;
    this.executionOwners = executionOwners;
    this.sourceDeclarations = sourceDeclarations;
    members = new SourceMemberVisibility(sourceDeclarations, parents);
    var declarations = new HashMap<String, List<Binding>>();
    this.nodes.forEach(node -> indexDeclaration(declarations, node));
    declarations.replaceAll((key, values) -> List.copyOf(values));
    bindings = Map.copyOf(declarations);
    var byName = new HashMap<BindingName, List<Binding>>();
    bindings.values().forEach(candidates -> candidates.forEach(binding -> indexName(byName, binding)));
    byName.replaceAll((key, values) -> List.copyOf(values));
    namedBindings = Map.copyOf(byName);
  }

  static LexicalBindings forAdmission(Collection<SyntaxNode> nodes, OccurrenceIndex<SyntaxNode> parents,
      OccurrenceIndex<String> executionOwners, SourceDeclarations sourceDeclarations) {
    return new LexicalBindings(nodes, parents, executionOwners, sourceDeclarations);
  }

  private void indexDeclaration(Map<String, List<Binding>> declarations, SyntaxNode node) {
    if (node.value() instanceof SyntaxValue.ExecutableDeclaration method && method.kind() == ExecutableKind.COMPACT_CONSTRUCTOR) {
      indexCompactParameters(node, method.declarationKey(), declarations);
    } else if (node.value() instanceof SyntaxValue.ParameterDeclaration parameter) {
      indexBinding(declarations, parameter.declarationKey(),
          new Binding(node, Optional.of(parameter.name()), ReferenceKind.PARAMETER, false, declaredType(node)));
    } else if (node.value() instanceof SyntaxValue.LocalDeclaration local) {
      localKey(node, local).ifPresent(key -> indexLocal(declarations, node, key, Optional.of(local.name())));
    } else if (isUnsupportedLocalDeclaration(node)) {
      bindingReference(node).filter(reference -> reference.referenceKind() == ReferenceKind.LOCAL).flatMap(SemanticReference::symbolKey)
          .ifPresent(key -> indexLocal(declarations, node, key, Optional.empty()));
    }
  }

  private static void indexName(Map<BindingName, List<Binding>> names, Binding binding) {
    binding.name().ifPresent(name -> names
        .computeIfAbsent(new BindingName(binding.declaration().sourceFileKey(), name), ignored -> new ArrayList<>()).add(binding));
  }

  private boolean isUnsupportedLocalDeclaration(SyntaxNode node) {
    if (node.syntaxKind() != SyntaxKind.UNSUPPORTED || node.sourceKind() != JavaSourceKind.VARIABLE) {
      return false;
    }
    var parent = parents.get(node);
    if (parent == null) {
      return false;
    }
    var kind = parent.sourceKind();
    return switch (step(node).role()) {
      case STATEMENT -> kind == JavaSourceKind.BLOCK || kind == JavaSourceKind.CASE;
      case INITIALIZER -> kind == JavaSourceKind.FOR_LOOP;
      case RESOURCE -> kind == JavaSourceKind.TRY;
      case PARAMETER -> switch (kind) {
        case CATCH, ENHANCED_FOR_LOOP, LAMBDA_EXPRESSION, BINDING_PATTERN -> true;
        default -> false;
      };
      default -> false;
    };
  }

  void validate() {
    for (var node : nodes) {
      if (node.value() instanceof SyntaxValue.LocalDeclaration local) {
        local.bindingEvidence().ifPresent(evidence -> {
          if (evidence.context() != localContext(node)) {
            throw new IllegalArgumentException("A local binding context must agree with its source attachment");
          }
        });
      } else if (node.syntaxKind() == SyntaxKind.BINDING_PATTERN) {
        validatePatternScope(node);
      } else if (isBindingAccess(node)) {
        selectedBinding(node).ifPresent(binding -> validateAccess(node, binding));
      } else if (bindingReference(node).isPresent() && !isBindingDeclaration(node)) {
        throw new IllegalArgumentException("An unsupported occurrence cannot carry a resolved local or formal access");
      }
    }
  }

  void validateSelectedOccurrence(SyntaxNode source, SyntaxNode target) {
    if (isBindingAccess(source) && bindingReference(source).isPresent()) {
      var selected = selectedBinding(source).filter(binding -> !binding.implicit());
      if (selected.isEmpty() || !OccurrenceIndex.same(selected.orElseThrow().declaration(), target)) {
        throw new IllegalArgumentException("A binding occurrence edge must identify its exact lexical declaration");
      }
    } else if (isBindingDeclaration(target) && (!isBindingDeclaration(source) || !OccurrenceIndex.same(source, target))) {
      throw new IllegalArgumentException("A binding occurrence edge must identify its exact lexical declaration");
    }
  }

  boolean isBindingDeclaration(SyntaxNode node) {
    return node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION || node.syntaxKind() == SyntaxKind.PARAMETER_DECLARATION
        || (isUnsupportedLocalDeclaration(node)
            && bindingReference(node).filter(reference -> reference.referenceKind() == ReferenceKind.LOCAL).isPresent());
  }

  private static Optional<JavaTypeIdentity> declaredType(SyntaxNode node) {
    return node.optionalChild(SyntaxRole.DECLARED_TYPE).flatMap(type -> type.type().knownIdentity());
  }

  private void indexLocal(Map<String, List<Binding>> declarations, SyntaxNode node, String key, Optional<String> name) {
    var owner = executionOwners.get(node);
    if (owner == null) {
      owner = executionKey(enclosingExecution(node));
    }
    // The stored key spells its registered owner. Reachability is proved separately by attachments.
    if (owner == null || !key.startsWith(owner + ':')) {
      throw new IllegalArgumentException(OWNER_REQUIRED);
    }
    indexBinding(declarations, key, new Binding(node, name, ReferenceKind.LOCAL, false, node.type().knownIdentity()));
  }

  private void indexCompactParameters(SyntaxNode method, String methodKey, Map<String, List<Binding>> declarations) {
    var record = parents.get(method);
    if (record == null) {
      return;
    }
    var components = record.children(SyntaxRole.RECORD_COMPONENT);
    for (var index = 0; index < components.size(); index++) {
      indexComponentParameter(declarations, method, methodKey, index, components.get(index));
    }
  }

  private static void indexComponentParameter(Map<String, List<Binding>> declarations, SyntaxNode method, String methodKey, int ordinal,
      SyntaxNode component) {
    var name = component.value(SyntaxValue.RecordComponentDeclaration.class).name();
    indexBinding(declarations, methodKey + FORMAL_KEY_COMPONENT + ordinal,
        new Binding(method, Optional.of(name), ReferenceKind.PARAMETER, true, declaredType(component)));
  }

  private static void indexBinding(Map<String, List<Binding>> declarations, String key, Binding binding) {
    var candidates = declarations.computeIfAbsent(key, ignored -> new ArrayList<>());
    if (candidates.stream().anyMatch(candidate -> candidate.kind() == binding.kind()
        && candidate.declaration().sourceFileKey().equals(binding.declaration().sourceFileKey()))) {
      throw new IllegalArgumentException(OWNER_REQUIRED);
    }
    candidates.add(binding);
  }

  private static Optional<String> localKey(SyntaxNode declaration, SyntaxValue.LocalDeclaration local) {
    return local.bindingEvidence().flatMap(LocalBindingEvidence::declarationKey)
        .or(() -> declaration.semanticReference().flatMap(SemanticReference::symbolKey));
  }

  private static boolean isBindingAccess(SyntaxNode node) {
    return node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS || node.syntaxKind() == SyntaxKind.ANNOTATION_NAME_REFERENCE;
  }

  private static Optional<SemanticReference> bindingReference(SyntaxNode node) {
    return node.semanticReference().filter(reference -> reference.status() == ResolutionStatus.RESOLVED
        && (reference.referenceKind() == ReferenceKind.LOCAL || reference.referenceKind() == ReferenceKind.PARAMETER));
  }

  private Optional<Binding> selectedBinding(SyntaxNode access) {
    return bindingReference(access).flatMap(reference -> {
      if (access.sourceKind() != JavaSourceKind.IDENTIFIER) {
        throw new IllegalArgumentException("A local or formal access requires an unqualified name");
      }
      if (members.hasSelectorEnumConstant(access)) {
        throw new IllegalArgumentException("A bare enum case label must select its selector's enum constant");
      }
      var key = reference.symbolKey().orElseThrow();
      var candidates = bindings.getOrDefault(key, List.of()).stream().filter(binding -> binding.kind() == reference.referenceKind())
          .toList();
      var selected = candidates.stream().filter(binding -> binding.declaration().sourceFileKey().equals(access.sourceFileKey()))
          .findFirst();
      if (selected.isEmpty()) {
        if (!candidates.isEmpty()) {
          throw new IllegalArgumentException("A local access must remain in its binding's captured source");
        }
        if (unrepresentedBinding(access, key, reference.referenceKind())) {
          return Optional.empty();
        }
        throw new IllegalArgumentException(NAMED_BINDING_REQUIRED);
      }
      var name = access.value(SyntaxValue.SourceSymbol.class).name();
      if ("_".equals(name) || selected.orElseThrow().name().filter(declared -> !name.equals(declared)).isPresent()) {
        throw new IllegalArgumentException(NAMED_BINDING_REQUIRED);
      }
      return selected;
    });
  }

  private boolean unrepresentedBinding(SyntaxNode access, String key, ReferenceKind kind) {
    var owner = executionOwners.get(access);
    return kind == ReferenceKind.PARAMETER && owner != null && hasUnrepresentedFormal(access, key, owner);
  }

  private boolean hasUnrepresentedFormal(SyntaxNode access, String key, String owner) {
    // A body whose declaration was withheld may still carry independently attributed formal references.
    var prefix = owner + FORMAL_KEY_COMPONENT;
    var region = enclosingExecution(access);
    if (!key.startsWith(prefix)
        || sourceDeclarations.executables(owner).stream().anyMatch(node -> node.sourceFileKey().equals(access.sourceFileKey()))
        || parents.get(region) != null || region.syntaxKind() != SyntaxKind.BLOCK) {
      return false;
    }
    var ordinal = key.substring(prefix.length());
    return !ordinal.isEmpty() && (ordinal.length() == 1 || ordinal.charAt(0) != '0')
        && ordinal.chars().allMatch(character -> character >= '0' && character <= '9');
  }

  private LocalBindingEvidence.Context localContext(SyntaxNode declaration) {
    var role = step(declaration).role();
    return switch (requiredParent(declaration).sourceKind()) {
      case CATCH -> role == SyntaxRole.PARAMETER ? LocalBindingEvidence.Context.CATCH : LocalBindingEvidence.Context.LOCAL;
      case ENHANCED_FOR_LOOP ->
        role == SyntaxRole.PARAMETER ? LocalBindingEvidence.Context.ENHANCED_FOR : LocalBindingEvidence.Context.LOCAL;
      case TRY -> role == SyntaxRole.RESOURCE ? LocalBindingEvidence.Context.RESOURCE : LocalBindingEvidence.Context.LOCAL;
      case LAMBDA_EXPRESSION -> role == SyntaxRole.PARAMETER ? LocalBindingEvidence.Context.LAMBDA : LocalBindingEvidence.Context.LOCAL;
      case BINDING_PATTERN -> LocalBindingEvidence.Context.PATTERN;
      default -> LocalBindingEvidence.Context.LOCAL;
    };
  }

  private void validateAccess(SyntaxNode access, Binding binding) {
    var violation = accessViolation(access, binding);
    if (violation != null) {
      throw new IllegalArgumentException(violation);
    }
    var outside = enclosingExecution(binding.declaration());
    var accessName = access.value(SyntaxValue.SourceSymbol.class).name();
    if (members.shadowsCapture(access, outside, accessName)) {
      throw new IllegalArgumentException("A local access cannot select an outer variable where a source member shadows its binding");
    }
    var name = new BindingName(access.sourceFileKey(), accessName);
    for (var closer : namedBindings.getOrDefault(name, List.of())) {
      var inside = enclosingExecution(closer.declaration());
      if (crossesNestedType(inside, outside) && visiblyCompetes(access, closer)) {
        throw new IllegalArgumentException("A local access must select its nearer binding in the nested type");
      }
    }
    if (binding.declaredType().filter(type -> BindingTypeCorrespondence.contradicts(access, type)).isPresent()) {
      throw new IllegalArgumentException("A binding access type must agree with its selected declaration");
    }
  }

  private boolean visiblyCompetes(SyntaxNode access, Binding binding) {
    if (accessViolation(access, binding) != null) {
      return false;
    }
    var parent = parents.get(binding.declaration());
    // Missing flow facts permit a selected binding but cannot prove that it shadows another one.
    return binding.kind() != ReferenceKind.LOCAL || parent == null || parent.sourceKind() != JavaSourceKind.BINDING_PATTERN
        || (parent.value() instanceof SyntaxValue.PatternBinding pattern && pattern.resolvedAccesses().isPresent());
  }

  private boolean crossesNestedType(SyntaxNode inside, SyntaxNode outside) {
    var nestedType = false;
    for (var parent = parents.get(inside); parent != null; parent = parents.get(parent)) {
      if (OccurrenceIndex.same(parent, outside)) {
        return nestedType;
      }
      nestedType |= parent.syntaxKind() == SyntaxKind.TYPE_DECLARATION;
    }
    return false;
  }

  private @Nullable String accessViolation(SyntaxNode access, Binding binding) {
    if (binding.kind() == ReferenceKind.PARAMETER) {
      return formalViolation(access, binding);
    }
    return localViolation(access, binding.declaration());
  }

  private @Nullable String formalViolation(SyntaxNode access, Binding binding) {
    var declaration = binding.declaration();
    var executable = binding.implicit() ? declaration : requiredParent(declaration);
    var branch = branchWithin(access, executable);
    if (branch.isPresent() && step(branch.orElseThrow()).role() == SyntaxRole.BODY) {
      return null;
    }
    if (branch.isEmpty() && parents.get(enclosingExecution(access)) == null
        && executable.value(SyntaxValue.ExecutableDeclaration.class).declarationKey().equals(executionOwners.get(access))) {
      return null;
    }
    return "A formal parameter access must remain within its declaration's executable body";
  }

  private @Nullable String localViolation(SyntaxNode access, SyntaxNode declaration) {
    var parent = requiredParent(declaration);
    if (parent.sourceKind() == JavaSourceKind.BINDING_PATTERN) {
      return patternViolation(access, parent);
    }
    if (parent.sourceKind() == JavaSourceKind.CASE && step(declaration).role() == SyntaxRole.STATEMENT) {
      return switchViolation(access, declaration, parent);
    }
    var branch = branchWithin(access, parent).orElse(null);
    if (branch == null || outsideOwnInitializer(access, declaration, branch)) {
      return LEXICAL_SCOPE_REQUIRED;
    }
    return localRegionViolation(step(declaration), step(branch), parent.sourceKind());
  }

  private @Nullable String switchViolation(SyntaxNode access, SyntaxNode declaration, SyntaxNode declarationCase) {
    var accessCase = branchWithin(access, requiredParent(declarationCase)).filter(node -> step(node).role() == SyntaxRole.CASE)
        .orElse(null);
    if (accessCase == null) {
      return LEXICAL_SCOPE_REQUIRED;
    }
    // A later case is in the remaining switch block, including its labels and guard.
    var caseOrder = Integer.compare(step(accessCase).position(), step(declarationCase).position());
    if (caseOrder != 0) {
      return caseOrder > 0 ? null : SWITCH_DECLARATION_ORDER;
    }
    var statement = branchWithin(access, accessCase).orElse(null);
    if (statement == null || step(statement).role() != SyntaxRole.STATEMENT || step(statement).position() < step(declaration).position()) {
      return SWITCH_DECLARATION_ORDER;
    }
    return outsideOwnInitializer(access, declaration, statement) ? LEXICAL_SCOPE_REQUIRED : null;
  }

  private static @Nullable String localRegionViolation(Step declaration, Step reached, JavaSourceKind parentKind) {
    var outsideParameterBody = declaration.role() == SyntaxRole.PARAMETER && reached.role() != SyntaxRole.BODY;
    return switch (parentKind) {
      case BLOCK -> reached.role() != SyntaxRole.STATEMENT || reached.position() < declaration.position()
          ? "A local access cannot precede its binding declaration"
          : null;
      case CATCH -> outsideParameterBody ? "An exception binding is visible only within its catch body" : null;
      case LAMBDA_EXPRESSION -> outsideParameterBody ? "A lambda binding is visible only within its lambda body" : null;
      case ENHANCED_FOR_LOOP -> outsideParameterBody ? "An enhanced-for binding is visible only within its loop body" : null;
      case TRY ->
        reached.role() != SyntaxRole.TRY_BLOCK && (reached.role() != SyntaxRole.RESOURCE || reached.position() < declaration.position())
            ? "A resource binding is visible only in its following resources and try body"
            : null;
      case FOR_LOOP -> declaration.role() == SyntaxRole.INITIALIZER && reached.role() == SyntaxRole.INITIALIZER
          && reached.position() < declaration.position() ? "A basic-for initializer cannot refer to a later binding" : null;
      default -> null; // Exact ancestry is the available constraint for partial projections.
    };
  }

  private boolean outsideOwnInitializer(SyntaxNode access, SyntaxNode declaration, SyntaxNode branch) {
    return OccurrenceIndex.same(branch, declaration)
        && branchWithin(access, declaration).filter(node -> step(node).role() == SyntaxRole.INITIALIZER).isEmpty();
  }

  private @Nullable String patternViolation(SyntaxNode access, SyntaxNode pattern) {
    if (!withinPatternRegion(access, access.origin(), pattern)) {
      return LEXICAL_SCOPE_REQUIRED;
    }
    if (pattern.value() instanceof SyntaxValue.PatternBinding binding && binding.resolvedAccesses().isPresent()
        && access.origin().filter(binding.resolvedAccesses().orElseThrow()::contains).isEmpty()) {
      return PATTERN_SCOPE_REQUIRED;
    }
    return null;
  }

  private void validatePatternScope(SyntaxNode pattern) {
    var declaration = pattern.requiredChild(SyntaxRole.PARAMETER);
    var local = declaration.value(SyntaxValue.LocalDeclaration.class);
    var key = localKey(declaration, local);
    pattern.value(SyntaxValue.PatternBinding.class).resolvedAccesses().ifPresent(accesses -> {
      for (var origin : accesses) {
        if (nodes.stream().noneMatch(node -> matchesPatternAccess(node, origin, declaration, key, pattern))) {
          throw new IllegalArgumentException("Copied pattern scope must select actual accesses to its exact named binding");
        }
      }
    });
  }

  private boolean matchesPatternAccess(SyntaxNode node, SourceSpan origin, SyntaxNode declaration, Optional<String> key,
      SyntaxNode pattern) {
    if (!node.sourceFileKey().equals(origin.sourceFileKey()) || !withinPatternRegion(node, Optional.of(origin), pattern)) {
      return false;
    }
    return node.syntaxKind() == SyntaxKind.UNSUPPORTED
        ? contains(node, origin)
        : node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS && node.origin().equals(Optional.of(origin))
            && bindingReference(node).flatMap(SemanticReference::symbolKey).equals(key) && key.isPresent()
            && selectedBinding(node).filter(binding -> OccurrenceIndex.same(binding.declaration(), declaration)).isPresent();
  }

  private boolean withinPatternRegion(SyntaxNode access, Optional<SourceSpan> origin, SyntaxNode pattern) {
    var region = enclosingPatternRegion(pattern);
    var reached = branchWithin(access, region);
    if (reached.isEmpty()
        || pattern.origin().filter(declared -> origin.filter(use -> use.startOffset() < declared.startOffset()).isPresent()).isPresent()) {
      return false;
    }
    var declaration = branchWithin(pattern, region);
    return declaration.isEmpty() || step(declaration.orElseThrow()).role() != SyntaxRole.STATEMENT
        || step(reached.orElseThrow()).role() != SyntaxRole.STATEMENT
        || step(reached.orElseThrow()).position() >= step(declaration.orElseThrow()).position();
  }

  private static boolean contains(SyntaxNode node, SourceSpan access) {
    return node.origin()
        .filter(origin -> origin.startOffset() <= access.startOffset() && origin.endOffsetExclusive() >= access.endOffsetExclusive())
        .isPresent();
  }

  private SyntaxNode enclosingExecution(SyntaxNode node) {
    return enclosingRegion(node, candidate -> executionKey(candidate) != null);
  }

  private SyntaxNode enclosingPatternRegion(SyntaxNode pattern) {
    return enclosingRegion(pattern, candidate -> switch (candidate.sourceKind()) {
      case BLOCK, CASE, LAMBDA_EXPRESSION -> true;
      default -> executionKey(candidate) != null;
    });
  }

  private SyntaxNode enclosingRegion(SyntaxNode node, Predicate<SyntaxNode> boundary) {
    var current = node;
    for (var parent = parents.get(current); parent != null; parent = parents.get(current)) {
      if (boundary.test(current)) {
        return current;
      }
      current = parent;
    }
    return current;
  }

  private @Nullable String executionKey(SyntaxNode node) {
    return switch (node.value()) {
      case SyntaxValue.ExecutableDeclaration method -> method.declarationKey();
      case SyntaxValue.VariableDeclaration member -> member.declarationKey();
      default -> initializerKey(node);
    };
  }

  private @Nullable String initializerKey(SyntaxNode node) {
    if (node.syntaxKind() != SyntaxKind.STATIC_INITIALIZER_BLOCK && node.syntaxKind() != SyntaxKind.INSTANCE_INITIALIZER_BLOCK) {
      return null;
    }
    var owner = parents.get(node);
    return owner != null && owner.value() instanceof SyntaxValue.TypeDeclaration type
        ? node.origin().map(range -> type.declarationKey() + ":initializer:" + range.startOffset() + ':' + range.endOffsetExclusive())
            .orElse(null)
        : null;
  }

  private Optional<SyntaxNode> branchWithin(SyntaxNode node, SyntaxNode region) {
    var branch = node;
    for (var parent = parents.get(branch); parent != null; parent = parents.get(branch)) {
      if (OccurrenceIndex.same(parent, region)) {
        return Optional.of(branch);
      }
      branch = parent;
    }
    return Optional.empty();
  }

  private SyntaxNode requiredParent(SyntaxNode node) {
    return Optional.ofNullable(parents.get(node)).orElseThrow(() -> new IllegalArgumentException(LEXICAL_SCOPE_REQUIRED));
  }

  private static Step step(SyntaxNode node) {
    return node.structuralPath().steps().getLast();
  }

  private record Binding(SyntaxNode declaration, Optional<String> name, ReferenceKind kind, boolean implicit,
      Optional<JavaTypeIdentity> declaredType) {
  }

  private record BindingName(String sourceFileKey, String name) {
  }
}
