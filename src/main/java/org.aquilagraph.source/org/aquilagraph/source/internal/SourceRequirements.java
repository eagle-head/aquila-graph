package org.aquilagraph.source.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.SourceQualification;
import org.aquilagraph.source.api.SourceQualificationReason;
import org.aquilagraph.source.api.SourceQualificationReason.Category;
import org.aquilagraph.source.api.SourceQualificationReason.EvidenceRole;
import org.aquilagraph.source.api.SourceQualificationReason.Subject;
import org.aquilagraph.source.api.SourceQualificationReason.Capture;
import org.aquilagraph.source.api.SourceQualificationReason.Node;
import org.aquilagraph.source.api.SourceQualificationReason.Declaration;
import org.aquilagraph.source.api.SourceQualificationReason.Context;
import org.aquilagraph.source.api.SourceQualificationReason.Fact;
import org.aquilagraph.source.api.SourceQualificationReason.DiagnosticReference;
import org.aquilagraph.source.api.SourceQualificationReason.DiagnosticEvidence;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.EvidenceState;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SourceExecutionTraversal;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.ExecutionSyntax;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.NormalizedExpressionCorrespondence;
import org.aquilagraph.source.api.SourceContext;

/** Source-only qualification over intrinsically admitted copied facts. */
public final class SourceRequirements {
  private static final String UNNAMED_LOCAL = "_";
  private SourceRequirements() {
  }

  /**
   * Establishes completion and all blocking causes from the same admitted facts.
   *
   * @param input
   *          admitted source input
   * @param profile
   *          selected original source profile
   * @param semanticRevision
   *          original production revision
   * @return immutable source assessment in rule and source occurrence order
   */
  public static SourceQualification assess(SyntaxAnalysisInput input, AstProfile profile, String semanticRevision) {
    if (!profile.isSupported()) {
      throw new IllegalArgumentException("Unsupported source profile: " + profile.version());
    }
    var reasons = new ArrayList<SourceQualificationReason>();
    if (input.snapshot().sourceFiles().isEmpty()) {
      reasons.add(reason(Category.EMPTY_SOURCE, EvidenceRole.SOURCE_CAPTURE, Capture.INSTANCE, List.of()));
    }
    if (input.aggregate().isEmpty()) {
      reasons.add(reason(Category.UNAVAILABLE_ROOTS, EvidenceRole.SOURCE_ROOTS, Capture.INSTANCE, List.of()));
    }
    if (input.aggregate().isPresent()) {
      var source = input.aggregate().orElseThrow();
      if (source.roots().stream()
          .anyMatch(root -> root.value(SyntaxValue.CompilationUnit.class)
              .range() != (profile.supportsSourceEnvelope()
                  ? SyntaxValue.CompilationUnitRange.CAPTURED_SOURCE
                  : SyntaxValue.CompilationUnitRange.LEGACY_PROFILE))) {
        throw new IllegalArgumentException("Compilation unit range policy must agree with its source profile");
      }
      if (!profile.supportsSourceEnvelope() && input.program().types().isEmpty()) {
        reasons.add(reason(Category.PROFILE_RESTRICTION, EvidenceRole.DECLARATION_ENVELOPE, Capture.INSTANCE, List.of()));
      }
      if (profile.supportsModuleServices()) {
        collectModuleServiceGaps(input, reasons);
      }
      if (profile.supportsWrittenExpressions()
          && input.languageFeatureCoverageResult().validationEvidence().writtenInventory().writtenExpressions().isEmpty()) {
        reasons.add(reason(Category.MISSING_REQUIRED_EVIDENCE, EvidenceRole.WRITTEN_EXPRESSIONS, Capture.INSTANCE, List.of()));
      }
      for (var type : input.program().types()) {
        if (type.staticType().category() != JavaTypeCategory.DECLARED) {
          reasons.add(reason(Category.ERRONEOUS_EVIDENCE, EvidenceRole.DECLARED_TYPE, new Declaration(type.typeKey()), type.origins()));
        }
      }
      if (profile.supportsNestedDeclarations()) {
        for (var type : input.program().types()) {
          if (type.nesting().lexicalOwnerKey().isPresent() && type.nesting().ownerDetails().isEmpty()) {
            reasons.add(
                reason(Category.MISSING_REQUIRED_EVIDENCE, EvidenceRole.LEXICAL_OWNER, new Declaration(type.typeKey()), type.origins()));
          }
          if (type.sourceDeclared()
              && source.declarationMemberOrder(type.typeKey()).flatMap(order -> order.attributedTreeOrder()).isEmpty()) {
            reasons.add(
                reason(Category.MISSING_REQUIRED_EVIDENCE, EvidenceRole.MEMBER_ORDER, new Declaration(type.typeKey()), type.origins()));
          }
        }
      }
    }
    var evidence = input.languageFeatureCoverageResult().validationEvidence();
    for (var event : evidence.diagnosticEmissions()) {
      if (event.diagnostic().isError()) {
        reasons.add(new SourceQualificationReason(
            event.diagnostic().outcomeKind() == AnalysisOutcomeKind.COMPILER_ERROR ? Category.COMPILER_ERROR : Category.SOURCE_ERROR,
            EvidenceRole.DIAGNOSTIC, Capture.INSTANCE, event.diagnostic().origins(), List.of(reference(input, event)), List.of()));
      }
    }
    for (var outcome : evidence.outcomes()) {
      if (outcome.support().mode() == FeatureContextOutcome.Mode.FAILED) {
        var context = evidence.writtenInventory().contexts().stream().filter(value -> value.address().equals(outcome.address())).findFirst()
            .orElseThrow();
        reasons
            .add(new SourceQualificationReason(Category.STRUCTURAL_COVERAGE_GAP, EvidenceRole.COVERAGE_SUPPORT,
                new Context(outcome.address()), context.origins(), evidence.diagnosticEmissions().stream()
                    .filter(event -> outcome.emissions().contains(event.address())).map(event -> reference(input, event)).toList(),
                List.of()));
      }
    }
    for (var unmatched : evidence.unmatchedFacts()) {
      var context = evidence.writtenInventory().contexts().stream().filter(value -> value.address().equals(unmatched.owner())).findFirst()
          .orElseThrow();
      var event = evidence.diagnosticEmissions().stream().filter(value -> value.address().equals(unmatched.emission())).findFirst()
          .orElseThrow();
      reasons.add(new SourceQualificationReason(Category.STRUCTURAL_COVERAGE_GAP, EvidenceRole.UNMATCHED_FACT, new Fact(unmatched.fact()),
          context.origins(), List.of(reference(input, event)), List.of()));
    }
    input.aggregate().ifPresent(source -> {
      source.nodes().forEach(node -> collectSourceGaps(node, profile, reasons));
      source.nodes().forEach(node -> collectProfileGaps(source, node, profile, reasons));
      source.nodes().forEach(node -> collectDeclarationScopeGaps(source, node, profile, reasons));
      for (var node : source.nodes()) {
        if (source.writtenSourceAssociation(node.nodeId()).flatMap(association -> association.normalizedExpression())
            .filter(SourceRequirements::hasWrittenExpressionGap).isPresent()) {
          reasons.add(nodeReason(Category.MISSING_REQUIRED_EVIDENCE, EvidenceRole.CONSTANT_EXPRESSION, node));
        }
      }
      source.executionScopes()
          .forEach(scope -> source.executionSyntax(scope.key()).ifPresent(execution -> collectExecutionGaps(execution, profile, reasons)));
      for (var method : input.program().methods()) {
        if (method.hasBody() && source.methodBodyOrigin(method.methodKey())
            .map(origin -> origin == MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION).orElse(true)) {
          reasons.add(
              reason(Category.MISSING_REQUIRED_EVIDENCE, EvidenceRole.METHOD_BODY, new Declaration(method.methodKey()), method.origins()));
        }
      }
    });
    var unavailable = input.snapshot().sourceFiles().isEmpty() || input.aggregate().isEmpty()
        || (!profile.supportsSourceEnvelope() && input.program().types().isEmpty());
    reasons.sort(Comparator.comparing(SourceQualificationReason::role).thenComparingInt(reason -> fileOrder(input, reason))
        .thenComparingLong(reason -> occurrenceOrder(input, reason)));
    return new SourceQualification(
        unavailable ? AstCompletion.UNAVAILABLE : reasons.isEmpty() ? AstCompletion.COMPLETE : AstCompletion.INCOMPLETE, profile,
        Optional.of(semanticRevision), reasons);
  }

  /**
   * Qualifies an empty capture without manufacturing a production revision.
   *
   * @param profile
   *          selected source profile
   * @return unavailable assessment with truthful semantic absence
   */
  public static SourceQualification empty(AstProfile profile) {
    return new SourceQualification(AstCompletion.UNAVAILABLE, profile, Optional.empty(),
        List.of(reason(Category.EMPTY_SOURCE, EvidenceRole.SOURCE_CAPTURE, Capture.INSTANCE, List.of())));
  }

  private static int fileOrder(SyntaxAnalysisInput input, SourceQualificationReason reason) {
    var key = switch (reason.subject()) {
      case Node node -> Optional.of(node.sourceFileKey());
      case Context context -> Optional.of(context.address().sourceFileKey());
      case Fact fact -> Optional.of(fact.reference().sourceFileKey());
      case Declaration declaration -> declarationNode(input, declaration).map(SyntaxNode::sourceFileKey)
          .or(() -> input.aggregate().flatMap(source -> source.methodSyntax(declaration.key())).map(body -> body.root().sourceFileKey()))
          .or(() -> reason.origins().stream().findFirst().map(SourceSpan::sourceFileKey));
      default -> reason.origins().stream().findFirst().map(SourceSpan::sourceFileKey);
    };
    for (var index = 0; index < input.snapshot().sourceFiles().size(); index++) {
      if (key.filter(input.snapshot().sourceFiles().get(index).sourceFileKey()::equals).isPresent()) {
        return index;
      }
    }
    return Integer.MAX_VALUE;
  }

  private static long occurrenceOrder(SyntaxAnalysisInput input, SourceQualificationReason reason) {
    var nodeKey = switch (reason.subject()) {
      case Node node -> Optional.of(node.nodeKey());
      case Declaration declaration -> declarationNode(input, declaration).map(SyntaxNode::nodeId);
      default -> Optional.<String>empty();
    };
    if (nodeKey.isPresent()) {
      var nodes = input.aggregate().orElseThrow().nodes();
      for (var index = 0; index < nodes.size(); index++) {
        if (nodes.get(index).nodeId().equals(nodeKey.orElseThrow())) {
          return index;
        }
      }
    }
    return reason.origins().stream().findFirst().map(SourceSpan::startOffset).orElse(Long.MAX_VALUE);
  }

  private static Optional<SyntaxNode> declarationNode(SyntaxAnalysisInput input, Declaration declaration) {
    var source = input.aggregate().orElseThrow();
    var direct = source.nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration type && type.declarationKey().equals(declaration.key()))
        .findFirst();
    if (direct.isPresent()) {
      return direct;
    }
    var owner = input.program().methods().stream().filter(method -> method.methodKey().equals(declaration.key()))
        .map(method -> method.ownerTypeKey()).findFirst();
    return owner.flatMap(key -> source.nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration type && type.declarationKey().equals(key)).findFirst());
  }

  private static DiagnosticReference reference(SyntaxAnalysisInput input, DiagnosticEmission event) {
    return new DiagnosticReference(input.diagnostics(), event.slot(), Optional.of(event.address()));
  }

  private static SourceQualificationReason reason(Category category, EvidenceRole role, Subject subject, List<SourceSpan> origins) {
    return new SourceQualificationReason(category, role, subject, origins, List.of(), List.of());
  }

  private static SourceQualificationReason nodeReason(Category category, EvidenceRole role, SyntaxNode node) {
    return reason(category, role, new Node(node.nodeId(), node.sourceFileKey()), node.origin().stream().toList());
  }

  private static void collectSourceGaps(SyntaxNode node, AstProfile profile, List<SourceQualificationReason> reasons) {
    if (node.value() instanceof SyntaxValue.PackageQualifier) {
      return;
    }
    if (!SourceFormRequirements.supports(node, profile)) {
      reasons.add(nodeReason(Category.STRUCTURAL_COVERAGE_GAP, EvidenceRole.SOURCE_FORM, node));
    }
    if (node.syntaxKind().requiresType() && (!(node.type() instanceof SyntaxType.Resolved resolved)
        || resolved.identity().category() == JavaTypeCategory.ERROR || resolved.identity().category() == JavaTypeCategory.OTHER)) {
      var erroneous = node.type() == SyntaxType.Erroneous.INSTANCE
          || node.type().knownIdentity().filter(type -> type.category() == JavaTypeCategory.ERROR).isPresent();
      reasons.add(nodeReason(erroneous ? Category.ERRONEOUS_EVIDENCE : Category.MISSING_REQUIRED_EVIDENCE, EvidenceRole.TYPE, node));
    }
    if (node.semanticReference().filter(reference -> reference.status() != ResolutionStatus.RESOLVED).isPresent()) {
      reasons.add(nodeReason(node.semanticReference().orElseThrow().status() == ResolutionStatus.ERROR
          ? Category.ERRONEOUS_EVIDENCE
          : Category.MISSING_REQUIRED_EVIDENCE, EvidenceRole.SEMANTIC_REFERENCE, node));
    }
    switch (node.syntaxKind()) {
      case INVOCATION, OBJECT_CREATION -> {
        if (node.value(SyntaxValue.Invocation.class).semantics().isEmpty()) {
          reasons.add(nodeReason(Category.MISSING_REQUIRED_EVIDENCE, EvidenceRole.INVOCATION_SEMANTICS, node));
        }
      }
      default -> {
      }
    }
  }

  private static void collectExecutionGaps(ExecutionSyntax execution, AstProfile profile, List<SourceQualificationReason> reasons) {
    if (profile.supportsMemberInitialization() && !(execution.scope() instanceof ExecutionScope.Method)
        && execution.attributedOccurrences().stream().noneMatch(address -> address.nodeKey().equals(execution.root().nodeId()))) {
      reasons.add(nodeReason(Category.MISSING_REQUIRED_EVIDENCE, EvidenceRole.EXECUTION_ROOT, execution.root()));
    }
    SourcePatternRequirements.unsupportedNodes(execution.root())
        .forEach(node -> reasons.add(nodeReason(Category.STRUCTURAL_COVERAGE_GAP, EvidenceRole.PATTERN_PLACEMENT, node)));
    SourceSwitchRequirements.unsupportedNodes(execution.root(), false)
        .forEach(node -> reasons.add(nodeReason(Category.STRUCTURAL_COVERAGE_GAP, EvidenceRole.SWITCH_STRUCTURE, node)));
    for (var node : SourceExecutionTraversal.nodes(execution.root())) {
      if (isStaticMember(node) && execution.staticAccess(node.nodeId()).flatMap(value -> value.declaringOwner().knownValue())
          .flatMap(owner -> owner.declarationKey()).isEmpty()) {
        var value = execution.staticAccess(node.nodeId()).map(item -> item.declaringOwner());
        var diagnostic = value.flatMap(item -> item.diagnostic());
        reasons.add(new SourceQualificationReason(
            value.filter(item -> item.state() == EvidenceState.ERRONEOUS).isPresent()
                ? Category.ERRONEOUS_EVIDENCE
                : Category.MISSING_REQUIRED_EVIDENCE,
            EvidenceRole.STATIC_OWNER, new Node(node.nodeId(), node.sourceFileKey()), node.origin().stream().toList(), List.of(),
            diagnostic.stream().map(item -> new DiagnosticEvidence(EvidenceRole.STATIC_OWNER, item)).toList()));
      }
      if (node.syntaxKind() == SyntaxKind.INVOCATION || node.syntaxKind() == SyntaxKind.OBJECT_CREATION
          || node.syntaxKind() == SyntaxKind.ENUM_CONSTANT_DECLARATION) {
        for (var child : node.children()) {
          if (child.role() == SyntaxRole.ARGUMENT && execution.useEvidence(node.nodeId(), child.role(), child.position()).isEmpty()) {
            reasons.add(nodeReason(Category.MISSING_REQUIRED_EVIDENCE, EvidenceRole.ARGUMENT_TARGET, child.node()));
          }
        }
      }
      if (node.syntaxKind() == SyntaxKind.FIELD_DECLARATION && !node.children(SyntaxRole.INITIALIZER).isEmpty()
          && execution.useEvidence(node.nodeId(), SyntaxRole.INITIALIZER, 0).isEmpty()) {
        reasons.add(nodeReason(Category.MISSING_REQUIRED_EVIDENCE, EvidenceRole.INITIALIZER_USE, node));
      }
    }
  }

  private static boolean hasWrittenExpressionGap(NormalizedExpressionCorrespondence value) {
    var occurrence = value.region().occurrences().stream().filter(item -> item.path().equals(value.occurrencePath())).findFirst()
        .orElseThrow();
    return occurrence.restrictedDecimal().isEmpty() && (value.derivedConstantExpression().isEmpty()
        || (value.derivedConstantExpression().orElseThrow() && value.derivedConstant().isEmpty()));
  }

  private static void collectProfileGaps(JavaAstAggregate source, SyntaxNode node, AstProfile profile,
      List<SourceQualificationReason> reasons) {
    if (profile.equals(AstProfile.JAVA_SOURCE_BOUNDED) && (node.syntaxKind() == SyntaxKind.CLASS_LITERAL
        || node.syntaxKind() == SyntaxKind.UPDATE || node.syntaxKind() == SyntaxKind.COMPOUND_ASSIGNMENT
        || node.syntaxKind() == SyntaxKind.ARRAY_CREATION || isIteration(node.syntaxKind()) || node.syntaxKind() == SyntaxKind.ASSERT
        || node.syntaxKind() == SyntaxKind.SYNCHRONIZED || node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION
        || node.syntaxKind() == SyntaxKind.MEMBER_REFERENCE || node.syntaxKind() == SyntaxKind.INSTANCE_OF
        || node.syntaxKind() == SyntaxKind.BINDING_PATTERN || node.syntaxKind() == SyntaxKind.DECONSTRUCTION_PATTERN
        || node.syntaxKind() == SyntaxKind.ANY_PATTERN || node.syntaxKind() == SyntaxKind.SWITCH_STATEMENT
        || (node.syntaxKind() == SyntaxKind.CASE && !SourceSwitchRequirements.isBoundedCase(node, source))
        || (node.syntaxKind() == SyntaxKind.TRY
            && (!node.children(SyntaxRole.RESOURCE).isEmpty() || !node.children(SyntaxRole.FINALLY_BLOCK).isEmpty()))
        || node.sourceKind() == JavaSourceKind.UNION_TYPE || node.sourceKind() == JavaSourceKind.INTERSECTION_TYPE)) {
      reasons.add(nodeReason(Category.PROFILE_RESTRICTION, EvidenceRole.PROFILE_FORM, node));
    }
    if (!profile.supportsNestedDeclarations()
        && (!node.children(SyntaxRole.CLASS_BODY).isEmpty() || (node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
            && node.structuralPath().steps().stream().anyMatch(step -> step.role() == SyntaxRole.STATEMENT)))) {
      reasons.add(nodeReason(Category.PROFILE_RESTRICTION, EvidenceRole.PROFILE_FORM, node));
    }
    if (!(node.value() instanceof SyntaxValue.LocalDeclaration local)) {
      return;
    }
    if (profile.equals(AstProfile.JAVA_SOURCE_BOUNDED)) {
      if (UNNAMED_LOCAL.equals(local.name())) {
        reasons.add(nodeReason(Category.PROFILE_RESTRICTION, EvidenceRole.LOCAL_NAME, node));
      }
      if (!local.writtenModifiers().isEmpty()) {
        reasons.add(nodeReason(Category.PROFILE_RESTRICTION, EvidenceRole.LOCAL_MODIFIERS, node));
      }
      if (!node.children(SyntaxRole.ANNOTATION).isEmpty()) {
        reasons.add(nodeReason(Category.PROFILE_RESTRICTION, EvidenceRole.LOCAL_ANNOTATIONS, node));
      }
      local.bindingEvidence().ifPresent(binding -> {
        if (binding.typeForm() != LocalBindingEvidence.TypeForm.DECLARED) {
          reasons.add(nodeReason(Category.PROFILE_RESTRICTION, EvidenceRole.LOCAL_TYPE_FORM, node));
        }
        if (!binding.sharedAnnotationKeys().isEmpty()) {
          reasons.add(nodeReason(Category.PROFILE_RESTRICTION, EvidenceRole.LOCAL_SHARED_ANNOTATIONS, node));
        }
      });
      return;
    }
    if (UNNAMED_LOCAL.equals(local.name()) && local.bindingEvidence().isEmpty()) {
      reasons.add(nodeReason(Category.MISSING_REQUIRED_EVIDENCE, EvidenceRole.LOCAL_BINDING, node));
    }
    if (local.bindingEvidence().filter(binding -> binding.declarationKey().isEmpty()).isPresent()) {
      reasons.add(nodeReason(Category.MISSING_REQUIRED_EVIDENCE, EvidenceRole.LOCAL_DECLARATION, node));
    }
  }

  private static void collectDeclarationScopeGaps(JavaAstAggregate aggregate, SyntaxNode node, AstProfile profile,
      List<SourceQualificationReason> reasons) {
    if (!profile.supportsMemberInitialization()) {
      return;
    }
    if (node.syntaxKind() == SyntaxKind.FIELD_DECLARATION) {
      if (aggregate.executionSyntax(node.value(SyntaxValue.VariableDeclaration.class).declarationKey())
          .filter(scope -> scope.scope() instanceof ExecutionScope.Field).isEmpty()) {
        reasons.add(nodeReason(Category.MISSING_REQUIRED_EVIDENCE, EvidenceRole.DECLARATION_SCOPE, node));
      }
      return;
    }
    if (profile.supportsEnumInitialization() && node.syntaxKind() == SyntaxKind.ENUM_CONSTANT_DECLARATION) {
      var scope = aggregate.executionSyntax(node.value(SyntaxValue.VariableDeclaration.class).declarationKey()).map(ExecutionSyntax::scope)
          .filter(ExecutionScope.EnumConstant.class::isInstance).map(ExecutionScope.EnumConstant.class::cast);
      if (scope.isEmpty()) {
        reasons.add(nodeReason(Category.MISSING_REQUIRED_EVIDENCE, EvidenceRole.DECLARATION_SCOPE, node));
      } else {
        var constructor = scope.orElseThrow().selectedConstructor();
        if (constructor.filter(reference -> reference.status() == ResolutionStatus.RESOLVED).flatMap(reference -> reference.symbolKey())
            .isEmpty()) {
          reasons.add(nodeReason(constructor.filter(reference -> reference.status() == ResolutionStatus.ERROR).isPresent()
              ? Category.ERRONEOUS_EVIDENCE
              : Category.MISSING_REQUIRED_EVIDENCE, EvidenceRole.ENUM_CONSTRUCTOR, node));
        }
      }
      return;
    }
    if ((node.syntaxKind() == SyntaxKind.STATIC_INITIALIZER_BLOCK || node.syntaxKind() == SyntaxKind.INSTANCE_INITIALIZER_BLOCK)
        && aggregate.executionScopes().stream().filter(ExecutionScope.Initializer.class::isInstance)
            .noneMatch(scope -> aggregate.executionSyntax(scope.key()).orElseThrow().root().nodeId().equals(node.nodeId()))) {
      reasons.add(nodeReason(Category.MISSING_REQUIRED_EVIDENCE, EvidenceRole.DECLARATION_SCOPE, node));
    }
  }

  private static boolean isIteration(SyntaxKind kind) {
    return switch (kind) {
      case WHILE_LOOP, DO_WHILE_LOOP, FOR_LOOP, ENHANCED_FOR_LOOP, LABELED_STATEMENT, BREAK, CONTINUE -> true;
      default -> false;
    };
  }

  private static boolean isStaticMember(SyntaxNode node) {
    return (node.value() instanceof SyntaxValue.FieldAccess access && access.receiver().isStatic())
        || (node.syntaxKind() == SyntaxKind.INVOCATION && node.value(SyntaxValue.Invocation.class).form().isStatic());
  }

  private static void collectModuleServiceGaps(SyntaxAnalysisInput input, List<SourceQualificationReason> reasons) {
    var aggregate = input.aggregate().orElseThrow();
    var inventory = input.languageFeatureCoverageResult().validationEvidence().writtenInventory();
    var endpoints = inventory.contexts().stream()
        .filter(context -> context.address().partition() == SourceContext.Partition.MODULE_ENDPOINT).collect(Collectors
            .groupingBy(context -> context.address().sourceFileKey(), Collectors.groupingBy(context -> context.address().ownerPath())));
    for (var node : aggregate.nodes()) {
      if ((node.value() instanceof SyntaxValue.Uses || node.value() instanceof SyntaxValue.Provides) && node.children().isEmpty()) {
        reasons.add(nodeReason(Category.STRUCTURAL_COVERAGE_GAP, EvidenceRole.MODULE_SERVICE_SLOTS, node));
      }
      if (node.syntaxKind() != SyntaxKind.MODULE_TYPE_REFERENCE) {
        continue;
      }
      if (node.semanticReference()
          .filter(reference -> reference.status() == ResolutionStatus.RESOLVED && reference.declarationIdentity().isPresent()).isEmpty()) {
        reasons.add(nodeReason(node.semanticReference().filter(reference -> reference.status() == ResolutionStatus.ERROR).isPresent()
            ? Category.ERRONEOUS_EVIDENCE
            : Category.MISSING_REQUIRED_EVIDENCE, EvidenceRole.MODULE_SERVICE_TARGET, node));
      }
      var association = aggregate.writtenSourceAssociation(node.nodeId());
      var contexts = association
          .map(value -> endpoints.getOrDefault(node.sourceFileKey(), Map.of()).getOrDefault(value.ownerPath(), List.of()))
          .orElse(List.of());
      if (contexts.stream().noneMatch(context -> context.origins().equals(node.origin().stream().toList()))) {
        reasons.add(nodeReason(Category.STRUCTURAL_COVERAGE_GAP, EvidenceRole.MODULE_ENDPOINT_CONTEXT, node));
      }
    }
  }
}
