package org.aquilagraph.cfg.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.cfg.api.CfgBlocker;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.MethodSyntax;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxOperator;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxValue.ExecutableDeclaration.ExecutableKind;
import org.aquilagraph.source.api.SyntaxValue.SwitchCase;
import org.aquilagraph.source.api.SyntaxValue.SwitchCaseForm;
import org.aquilagraph.source.api.ChildUseEvidence;
import org.aquilagraph.source.api.ConstructorEvidence;
import org.aquilagraph.source.api.EvidenceState;
import org.aquilagraph.source.api.StaticMemberKind;
import org.aquilagraph.source.api.StringExpressionEvidence;
import org.aquilagraph.source.api.EvidenceValue;

/** Consumer-owned prerequisites for control-flow lowering of retained source facts. */
public final class CfgAdmission {
  private static final int MAX_ENUM_CONTEXT_CONSUMERS = 1;
  private CfgAdmission() {
  }

  /**
   * Assesses one owned declaration without acquiring a producer or constructing a graph.
   *
   * @param ast
   *          admitted source result
   * @param methodKey
   *          exact canonical declaration
   * @param profile
   *          compatible graph interpretation
   * @return bodyless, admitted, or all ordered refusal causes
   */
  public static CfgAdmissionResult assess(AstResult ast, String methodKey, CfgProfile profile) {
    Objects.requireNonNull(ast, "ast");
    Objects.requireNonNull(profile, "profile");
    if (!supportsSourceProfile(ast.profile()) || !supportsProfile(profile)) {
      throw new IllegalArgumentException("CFG admission requires a compatible profile");
    }
    var method = ast.method(methodKey).orElseThrow(() -> new IllegalArgumentException("Selected method is not owned by the AST"));
    if (!method.hasBody()) {
      return new CfgAdmissionResult(MethodCfgState.NOT_APPLICABLE, Optional.empty(), Optional.empty(), List.of());
    }
    var syntax = ast.body(methodKey);
    if (syntax.isEmpty() || ast.aggregate().methodBodyOrigin(methodKey)
        .filter(origin -> origin == MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION).isPresent()) {
      var blocker = blocker(ast, methodKey, Optional.empty(), MethodCfgState.SOURCE_UNAVAILABLE, "body",
          "The selected written body has unavailable source facts");
      return new CfgAdmissionResult(MethodCfgState.SOURCE_UNAVAILABLE, Optional.empty(), Optional.empty(), List.of(blocker));
    }
    var body = ast.bodyRef(methodKey).orElseThrow();
    var blockers = new ArrayList<CfgBlocker>();
    if (enumContexts(syntax.orElseThrow()).size() > MAX_ENUM_CONTEXT_CONSUMERS) {
      blockers.add(blocker(ast, methodKey, Optional.of(syntax.orElseThrow().root()), MethodCfgState.UNSUPPORTED, "constructor.binding",
          "The CFG enum binding plan supports one incoming-context consumer"));
    }
    for (var node : ExecutableSyntaxTraversal.nodes(syntax.orElseThrow().root())) {
      observeNode(ast, methodKey, syntax.orElseThrow(), node, profile, blockers);
    }
    if (blockers.isEmpty()) {
      return new CfgAdmissionResult(MethodCfgState.AVAILABLE, Optional.of(body), Optional.of(new LoweringPlan(body, profile)), List.of());
    }
    var state = MethodCfgState.refusalSummary(blockers);
    return new CfgAdmissionResult(state, Optional.of(body), Optional.empty(), blockers);
  }

  static boolean supportsProfile(CfgProfile profile) {
    return CfgProfile.JAVA_BOUNDED.equals(profile) || CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS.equals(profile);
  }

  static boolean supportsSourceProfile(AstProfile profile) {
    return AstProfile.JAVA_SOURCE_BOUNDED.equals(profile) || AstProfile.JAVA_SOURCE_EXPANDED.equals(profile)
        || profile.supportsMemberInitialization();
  }

  private static void observeNode(AstResult ast, String methodKey, MethodSyntax syntax, SyntaxNode node, CfgProfile profile,
      List<CfgBlocker> blockers) {
    if (node.syntaxKind() == SyntaxKind.UNSUPPORTED) {
      blockers.add(blocker(ast, methodKey, Optional.of(node), MethodCfgState.SOURCE_UNAVAILABLE, "source-form",
          "Required source facts for this form are unavailable"));
      return;
    }
    if (!supports(node, syntax.source())
        || (CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS.equals(profile) && node.syntaxKind() == SyntaxKind.SWITCH_EXPRESSION)) {
      blockers.add(blocker(ast, methodKey, Optional.of(node), MethodCfgState.UNSUPPORTED, "construct",
          "Represented source construct is outside the selected CFG profile"));
      return;
    }
    if (hasErroneousSemantics(node)) {
      blockers.add(blocker(ast, methodKey, Optional.of(node), MethodCfgState.ERRONEOUS_EVIDENCE, "semantics",
          "Compiler-neutral semantics required by CFG are erroneous"));
    } else if (hasUnresolvedSemantics(node)) {
      blockers.add(blocker(ast, methodKey, Optional.of(node), MethodCfgState.INSUFFICIENT_EVIDENCE, "semantics",
          "Compiler-neutral semantics required by CFG are unavailable"));
    }
    for (var child : ExecutableSyntaxTraversal.orderedChildren(node)) {
      if (!requiredUse(node, child)) {
        continue;
      }
      var use = syntax.useEvidence(node.nodeId(), child.role(), child.position());
      if (use.isEmpty()) {
        blockers.add(blocker(ast, methodKey, Optional.of(node), MethodCfgState.INSUFFICIENT_EVIDENCE,
            "use/" + child.role().stableId() + "/" + child.position(), "Selected direct-child conversion evidence is missing"));
      } else {
        for (var slot : useSlots(use.orElseThrow())) {
          if (slot.state() == EvidenceState.MISSING || slot.state() == EvidenceState.ERRONEOUS) {
            var state = slot.state() == EvidenceState.ERRONEOUS ? MethodCfgState.ERRONEOUS_EVIDENCE : MethodCfgState.INSUFFICIENT_EVIDENCE;
            blockers.add(evidenceBlocker(ast, methodKey, node, state, slot));
          }
        }
      }
    }
    if (requiresConstructorContext(node, syntax)) {
      var context = syntax.constructorEvidence(node.nodeId());
      var state = context.map(value -> value.corroboration().state()).orElse(EvidenceState.MISSING);
      if (state == EvidenceState.MISSING || state == EvidenceState.ERRONEOUS) {
        var kind = state == EvidenceState.ERRONEOUS ? MethodCfgState.ERRONEOUS_EVIDENCE : MethodCfgState.INSUFFICIENT_EVIDENCE;
        if (context.isPresent()) {
          blockers.add(evidenceBlocker(ast, methodKey, node, kind, context.orElseThrow().corroboration()));
        } else {
          blockers.add(blocker(ast, methodKey, Optional.of(node), kind, ConstructorEvidence.SLOT,
              "Selected constructor corroboration is unavailable"));
        }
      } else if (requiresEnumContext(node, syntax) && !EnumBindingPlan.supports(context.orElseThrow())) {
        blockers.add(blocker(ast, methodKey, Optional.of(node), MethodCfgState.UNSUPPORTED, "constructor.binding",
            "The corroborated constructor declarations do not support the selected enum binding plan"));
      }
      context.flatMap(value -> value.corroboration().knownValue()).flatMap(value -> value.insertedInvocation())
          .filter(value -> node.equals(syntax.root()) && !requiresEnumContext(node, syntax)).ifPresent(value -> {
            var slot = value.applicability().applicability();
            if (slot.state() == EvidenceState.MISSING || slot.state() == EvidenceState.ERRONEOUS) {
              var kind = slot.state() == EvidenceState.ERRONEOUS ? MethodCfgState.ERRONEOUS_EVIDENCE : MethodCfgState.INSUFFICIENT_EVIDENCE;
              blockers.add(evidenceBlocker(ast, methodKey, node, kind, slot));
            }
          });
    }
    var applicability = syntax.invocationEvidence(node.nodeId());
    if (!usesEnumSuperclassPlan(node, syntax)
        && applicability.map(value -> value.applicability().state() == EvidenceState.ERRONEOUS).orElse(false)) {
      blockers.add(evidenceBlocker(ast, methodKey, node, MethodCfgState.ERRONEOUS_EVIDENCE, applicability.orElseThrow().applicability()));
    } else if (!usesEnumSuperclassPlan(node, syntax)
        && (node.syntaxKind() == SyntaxKind.INVOCATION || node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
        && applicability.map(value -> value.applicability().state() == EvidenceState.MISSING).orElse(true)) {
      if (applicability.isPresent()) {
        blockers
            .add(evidenceBlocker(ast, methodKey, node, MethodCfgState.INSUFFICIENT_EVIDENCE, applicability.orElseThrow().applicability()));
      } else {
        blockers.add(blocker(ast, methodKey, Optional.of(node), MethodCfgState.INSUFFICIENT_EVIDENCE, "invocation-applicability",
            "Invocation applicability evidence is missing"));
      }
    }
    var staticMember = syntax.staticAccess(node.nodeId());
    staticMember.ifPresent(value -> {
      var owner = value.declaringOwner();
      if (owner.state() == EvidenceState.MISSING || owner.state() == EvidenceState.ERRONEOUS) {
        var kind = owner.state() == EvidenceState.ERRONEOUS ? MethodCfgState.ERRONEOUS_EVIDENCE : MethodCfgState.INSUFFICIENT_EVIDENCE;
        blockers.add(evidenceBlocker(ast, methodKey, node, kind, owner));
      }
    });
    if (staticMember.filter(value -> value.kind() == StaticMemberKind.FIELD_READ).isPresent()) {
      var state = staticMember.orElseThrow().constantVariable().state();
      if (state == EvidenceState.ERRONEOUS || state == EvidenceState.MISSING) {
        var kind = state == EvidenceState.ERRONEOUS ? MethodCfgState.ERRONEOUS_EVIDENCE : MethodCfgState.INSUFFICIENT_EVIDENCE;
        blockers.add(evidenceBlocker(ast, methodKey, node, kind, staticMember.orElseThrow().constantVariable()));
      }
    }
    var concatenation = syntax.stringConcatenation(node.nodeId());
    if (concatenation.isPresent()) {
      for (var slot : stringSlots(concatenation.orElseThrow())) {
        if (slot.state() == EvidenceState.ERRONEOUS || slot.state() == EvidenceState.MISSING) {
          var kind = slot.state() == EvidenceState.ERRONEOUS ? MethodCfgState.ERRONEOUS_EVIDENCE : MethodCfgState.INSUFFICIENT_EVIDENCE;
          blockers.add(evidenceBlocker(ast, methodKey, node, kind, slot));
        }
      }
    } else if (isStringPlus(node)) {
      blockers.add(blocker(ast, methodKey, Optional.of(node), MethodCfgState.INSUFFICIENT_EVIDENCE, "string-expression",
          "String expression classification slots are missing"));
    }
    if (hasMissingRuntimeDecisions(node, syntax)
        && staticMember.map(value -> value.declaringOwner().state() == EvidenceState.KNOWN).orElse(true)) {
      blockers.add(blocker(ast, methodKey, Optional.of(node), MethodCfgState.INSUFFICIENT_EVIDENCE, "selected-evidence",
          "Selected member, applicability, or runtime classification is unavailable"));
    }
  }

  private static List<EvidenceValue<Boolean>> stringSlots(StringExpressionEvidence evidence) {
    return List.of(evidence.leftIsString(), evidence.rightIsString(), evidence.compileTimeConstant());
  }

  private static List<EvidenceValue<?>> useSlots(ChildUseEvidence evidence) {
    return List.of(evidence.conversion(), evidence.referenceCheck());
  }

  private static CfgBlocker blocker(AstResult ast, String methodKey, Optional<SyntaxNode> node, MethodCfgState kind, String slot,
      String message) {
    var occurrence = node.flatMap(ast::occurrence);
    var origins = node.flatMap(SyntaxNode::origin).map(List::of).orElseGet(List::of);
    var code = "control-flow." + kind.name().toLowerCase(Locale.ROOT).replace('_', '-');
    var diagnostic = new AnalysisDiagnostic(
        StableId.sha256(
            "cfg-diagnostic", ast.identity().astResultKey(), methodKey, node.map(SyntaxNode::nodeId).orElse("body"), kind.name(), slot),
        code, DiagnosticStage.CONTROL_FLOW, DiagnosticSeverity.ERROR, message, origins,
        kind == MethodCfgState.ERRONEOUS_EVIDENCE
            ? AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE
            : AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE);
    return new CfgBlocker(kind, ast.identity(), methodKey, occurrence, slot, diagnostic);
  }

  private static CfgBlocker evidenceBlocker(AstResult ast, String methodKey, SyntaxNode node, MethodCfgState kind,
      EvidenceValue<?> evidence) {
    var cause = blocker(ast, methodKey, Optional.of(node), kind, evidence.origin().slot(), evidence.explanation().orElseThrow());
    return new CfgBlocker(cause.kind(), cause.astIdentity(), cause.methodKey(), cause.occurrence(), cause.slot(), cause.diagnostic(),
        evidence.diagnostic());
  }

  /**
   * Classifies the prerequisites of the existing control-flow lowering profile.
   *
   * @param syntax
   *          immutable copied source body and evidence
   * @return consumer classification without changing source facts
   */
  public static CfgReadiness classify(MethodSyntax syntax) {
    Objects.requireNonNull(syntax, "syntax");
    var nodes = ExecutableSyntaxTraversal.nodes(syntax.root());
    if (nodes.stream().anyMatch(node -> !supports(node, syntax.source())) || enumContexts(syntax).size() > MAX_ENUM_CONTEXT_CONSUMERS
        || enumContexts(syntax).stream().filter(value -> value.corroboration().state() == EvidenceState.KNOWN)
            .anyMatch(value -> !EnumBindingPlan.supports(value))) {
      return CfgReadiness.UNSUPPORTED;
    }
    if (nodes.stream()
        .anyMatch(node -> hasErroneousSemantics(node)
            || node.children().stream().filter(child -> requiredUse(node, child))
                .anyMatch(child -> syntax.useEvidence(node.nodeId(), child.role(), child.position())
                    .map(value -> useSlots(value).stream().anyMatch(slot -> slot.state() == EvidenceState.ERRONEOUS)).orElse(false))
            || (!usesEnumSuperclassPlan(node, syntax) && syntax.invocationEvidence(node.nodeId())
                .map(value -> value.applicability().state() == EvidenceState.ERRONEOUS).orElse(false))
            || syntax.staticAccess(node.nodeId())
                .map(value -> value.constantVariable().state() == EvidenceState.ERRONEOUS
                    || value.declaringOwner().state() == EvidenceState.ERRONEOUS)
                .orElse(false)
            || syntax.constructorEvidence(node.nodeId()).map(value -> value.corroboration().state() == EvidenceState.ERRONEOUS)
                .orElse(false)
            || insertedApplicabilityState(node, syntax) == EvidenceState.ERRONEOUS || syntax.stringConcatenation(node.nodeId())
                .map(value -> stringSlots(value).stream().anyMatch(slot -> slot.state() == EvidenceState.ERRONEOUS)).orElse(false))) {
      return CfgReadiness.ERRONEOUS_SEMANTICS;
    }
    if (nodes.stream().anyMatch(node -> hasUnresolvedSemantics(node) || hasMissingDecisions(node, syntax)
        || (requiresConstructorContext(node, syntax)
            && syntax.constructorEvidence(node.nodeId()).map(value -> value.corroboration().state() == EvidenceState.MISSING).orElse(true))
        || insertedApplicabilityState(node, syntax) == EvidenceState.MISSING)) {
      return CfgReadiness.UNRESOLVED_SEMANTICS;
    }
    return CfgReadiness.LOWERABLE;
  }

  private static boolean supports(SyntaxNode node, JavaAstAggregate source) {
    return switch (node.syntaxKind()) {
      case LOCAL_DECLARATION -> !"_".equals(node.value(SyntaxValue.LocalDeclaration.class).name());
      case TRY -> node.children(SyntaxRole.RESOURCE).isEmpty() && node.children(SyntaxRole.FINALLY_BLOCK).isEmpty();
      case CASE -> node.value(SwitchCase.class).form() == SwitchCaseForm.RULE && node.children(SyntaxRole.GUARD).isEmpty()
          && node.value(SwitchCase.class).labels().stream().noneMatch(SyntaxValue.SwitchLabel.Pattern.class::isInstance)
          && node.children(SyntaxRole.LABEL).stream().allMatch(label -> supportsSwitchLabel(label, source));
      case ASSIGNMENT -> switch (node.requiredChild(SyntaxRole.LOCATION).syntaxKind()) {
        case VARIABLE_ACCESS, FIELD_ACCESS, ARRAY_ACCESS -> true;
        default -> false;
      };
      case BLOCK, EXPRESSION_STATEMENT, RETURN, THROW, IF, CATCH, SWITCH_EXPRESSION, YIELD, INVOCATION, OBJECT_CREATION, FIELD_ACCESS,
          VARIABLE_ACCESS, RECEIVER, LITERAL, UNARY, BINARY, CONDITIONAL_AND, CONDITIONAL_OR, ARRAY_ACCESS, ARRAY_LENGTH, PARENTHESIZED,
          TYPE_CAST, CONDITIONAL_EXPRESSION, EMPTY ->
        true;
      default -> false;
    };
  }

  private static boolean hasErroneousSemantics(SyntaxNode node) {
    return node.type() == SyntaxType.Erroneous.INSTANCE
        || node.resolvedType().map(type -> type.category() == JavaTypeCategory.ERROR).orElse(false)
        || node.semanticReference().map(reference -> reference.status() == ResolutionStatus.ERROR).orElse(false);
  }

  private static boolean supportsSwitchLabel(SyntaxNode label, JavaAstAggregate source) {
    return source.executableLiteralText(label.nodeId()).isPresent()
        ? label.sourceKind() != JavaSourceKind.NULL_LITERAL
        : (label.syntaxKind() == SyntaxKind.VARIABLE_ACCESS || label.syntaxKind() == SyntaxKind.FIELD_ACCESS)
            && label.semanticReference().filter(reference -> reference.referenceKind() == ReferenceKind.ENUM_CONSTANT)
                .flatMap(reference -> reference.symbolKey()).isPresent();
  }

  private static boolean hasUnresolvedSemantics(SyntaxNode node) {
    return node.type() == SyntaxType.Unresolved.INSTANCE || node.type() instanceof SyntaxType.NoType
        || node.type() == SyntaxType.Unsupported.INSTANCE || node.type() instanceof SyntaxType.StructureUnavailable
        || node.resolvedType().map(type -> type.category() == JavaTypeCategory.OTHER).orElse(false)
        || node.semanticReference().map(reference -> reference.status() == ResolutionStatus.UNRESOLVED).orElse(false)
        || (node.value() instanceof SyntaxValue.Invocation invocation && invocation.semantics().isEmpty())
        || (node.syntaxKind() == SyntaxKind.CATCH && hasUnresolvedCatch(node));
  }

  private static boolean hasUnresolvedCatch(SyntaxNode node) {
    var parameter = node.requiredChild(SyntaxRole.PARAMETER);
    return parameter.resolvedType().isEmpty()
        || parameter
            .semanticReference().map(reference -> reference.status() != ResolutionStatus.RESOLVED
                || reference.typeAlternativeNames().isEmpty() || reference.typeAlternativeNames().stream().anyMatch(String::isBlank))
            .orElse(true);
  }

  private static boolean hasMissingDecisions(SyntaxNode node, MethodSyntax syntax) {
    if (node.children().stream().filter(child -> requiredUse(node, child))
        .anyMatch(child -> syntax.useEvidence(node.nodeId(), child.role(), child.position())
            .map(value -> useSlots(value).stream().anyMatch(slot -> slot.state() == EvidenceState.MISSING)).orElse(true))) {
      return true;
    }
    if (!usesEnumSuperclassPlan(node, syntax)
        && (node.syntaxKind() == SyntaxKind.INVOCATION || node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
        && syntax.invocationEvidence(node.nodeId()).map(value -> value.applicability().state() == EvidenceState.MISSING).orElse(true)) {
      return true;
    }
    if (syntax.staticAccess(node.nodeId())
        .map(value -> value.constantVariable().state() == EvidenceState.MISSING || value.declaringOwner().state() == EvidenceState.MISSING)
        .orElse(false)) {
      return true;
    }
    if (isStringPlus(node) && syntax.stringConcatenation(node.nodeId())
        .map(value -> stringSlots(value).stream().anyMatch(slot -> slot.state() == EvidenceState.MISSING)).orElse(true)) {
      return true;
    }
    return hasMissingRuntimeDecisions(node, syntax);
  }

  private static boolean hasMissingRuntimeDecisions(SyntaxNode node, MethodSyntax syntax) {
    if (isStaticAccess(node)) {
      var access = syntax.staticAccess(node.nodeId());
      if (access.isEmpty() || access.orElseThrow().declaringOwner().knownValue().isEmpty() || node.requiredReference().symbolKey().isEmpty()
          || ((access.orElseThrow().kind() != StaticMemberKind.FIELD_READ
              || access.orElseThrow().constantVariable().state() == EvidenceState.KNOWN)
              && ClassInitializationPolicy.required(access.orElseThrow())
              && access.orElseThrow().declaringType().declarationKey().isEmpty())) {
        return true;
      }
    }
    return node.syntaxKind() == SyntaxKind.OBJECT_CREATION && node.value(SyntaxValue.Invocation.class).semantics()
        .map(semantics -> semantics.qualifyingType().declarationKey().isEmpty()).orElse(true);
  }

  private static boolean requiredUse(SyntaxNode node, SyntaxChild child) {
    return switch (node.syntaxKind()) {
      case CASE -> child.role() == SyntaxRole.BODY && node.value(SyntaxValue.SwitchCase.class).form() == SyntaxValue.SwitchCaseForm.RULE
          && child.node().syntaxKind().requiresType();
      case INVOCATION, OBJECT_CREATION -> child.role() == SyntaxRole.ARGUMENT;
      default -> fixedRole(node.syntaxKind(), child.role());
    };
  }

  private static boolean requiresEnumContext(SyntaxNode node, MethodSyntax syntax) {
    if (!requiresConstructorContext(node, syntax)) {
      return false;
    }
    var declaration = syntax.source().nodes().stream()
        .filter(value -> value.value() instanceof SyntaxValue.ExecutableDeclaration executable
            && executable.declarationKey().equals(syntax.methodKey()))
        .map(value -> value.value(SyntaxValue.ExecutableDeclaration.class)).findFirst();
    var ownerKey = syntax.constructorEvidence(node.nodeId()).map(ConstructorEvidence::ownerTypeKey)
        .or(() -> declaration.map(SyntaxValue.ExecutableDeclaration::ownerKey));
    var bodyPath = syntax.root().structuralPath().steps();
    return syntax.source().nodes().stream()
        .anyMatch(owner -> owner.sourceKind() == JavaSourceKind.ENUM && owner.sourceFileKey().equals(node.sourceFileKey())
            && owner.value() instanceof SyntaxValue.TypeDeclaration type
            && (ownerKey.filter(type.declarationKey()::equals).isPresent()
                || (ownerKey.isEmpty() && owner.structuralPath().steps().size() < bodyPath.size()
                    && bodyPath.subList(0, owner.structuralPath().steps().size()).equals(owner.structuralPath().steps()))));
  }

  private static boolean usesEnumSuperclassPlan(SyntaxNode node, MethodSyntax syntax) {
    return node.origin().isEmpty() && node.value() instanceof SyntaxValue.Invocation invocation
        && invocation.form() == InvocationForm.SUPER_CONSTRUCTOR && requiresEnumContext(node, syntax);
  }

  private static boolean requiresConstructorContext(SyntaxNode node, MethodSyntax syntax) {
    if (syntax.constructorEvidence(node.nodeId()).isPresent()) {
      return true;
    }
    if (node.equals(syntax.root()) && syntax.source().methodBodyOrigin(syntax.methodKey()).orElseThrow() == MethodBodyOrigin.SOURCE_AST
        && syntax.source().nodes().stream()
            .anyMatch(value -> value.value() instanceof SyntaxValue.ExecutableDeclaration declaration
                && declaration.declarationKey().equals(syntax.methodKey())
                && (declaration.kind() == ExecutableKind.CONSTRUCTOR || declaration.kind() == ExecutableKind.COMPACT_CONSTRUCTOR))) {
      return node.children(SyntaxRole.STATEMENT).stream().filter(value -> value.syntaxKind() == SyntaxKind.EXPRESSION_STATEMENT)
          .map(value -> value.requiredChild(SyntaxRole.EXPRESSION))
          .noneMatch(value -> value.value() instanceof SyntaxValue.Invocation invocation
              && (invocation.form() == InvocationForm.THIS_CONSTRUCTOR || invocation.form() == InvocationForm.SUPER_CONSTRUCTOR
                  || invocation.form() == InvocationForm.QUALIFIED_SUPER_CONSTRUCTOR));
    }
    return node.syntaxKind() == SyntaxKind.INVOCATION && node.value() instanceof SyntaxValue.Invocation invocation
        && (invocation.form() == InvocationForm.THIS_CONSTRUCTOR || invocation.form() == InvocationForm.SUPER_CONSTRUCTOR)
        && syntax.source().nodes().stream()
            .anyMatch(owner -> owner.sourceKind() == JavaSourceKind.ENUM && owner.value() instanceof SyntaxValue.TypeDeclaration
                && owner.sourceFileKey().equals(node.sourceFileKey())
                && owner.structuralPath().steps().size() < syntax.root().structuralPath().steps().size() && syntax.root().structuralPath()
                    .steps().subList(0, owner.structuralPath().steps().size()).equals(owner.structuralPath().steps()));
  }

  private static EvidenceState insertedApplicabilityState(SyntaxNode node, MethodSyntax syntax) {
    if (!node.equals(syntax.root()) || requiresEnumContext(node, syntax)) {
      return EvidenceState.NOT_APPLICABLE;
    }
    return syntax.constructorEvidence(node.nodeId()).flatMap(value -> value.corroboration().knownValue())
        .flatMap(value -> value.insertedInvocation()).map(value -> value.applicability().applicability().state())
        .orElse(EvidenceState.NOT_APPLICABLE);
  }

  private static List<ConstructorEvidence> enumContexts(MethodSyntax syntax) {
    return syntax.evidence().constructors().stream()
        .filter(value -> syntax.source().nodes().stream()
            .anyMatch(owner -> owner.sourceKind() == JavaSourceKind.ENUM && owner.value() instanceof SyntaxValue.TypeDeclaration declaration
                && declaration.declarationKey().equals(value.ownerTypeKey())))
        .toList();
  }

  private static boolean isStaticAccess(SyntaxNode node) {
    if (node.value() instanceof SyntaxValue.FieldAccess access && access.receiver().isStatic()) {
      return node.syntaxKind() == SyntaxKind.FIELD_ACCESS;
    }
    return node.syntaxKind() == SyntaxKind.INVOCATION && node.value() instanceof SyntaxValue.Invocation invocation
        && (invocation.form() == InvocationForm.STATIC_UNQUALIFIED || invocation.form() == InvocationForm.STATIC_TYPE
            || invocation.form() == InvocationForm.STATIC_EXPRESSION);
  }

  private static boolean isStringPlus(SyntaxNode node) {
    return node.syntaxKind() == SyntaxKind.BINARY && node.value(SyntaxValue.Operator.class).operator() == SyntaxOperator.PLUS
        && (node.requiredChild(SyntaxRole.LEFT_OPERAND).resolvedType().map(CfgAdmission::isString).orElse(false)
            || node.requiredChild(SyntaxRole.RIGHT_OPERAND).resolvedType().map(CfgAdmission::isString).orElse(false));
  }

  private static boolean isString(JavaTypeIdentity type) {
    return type.category() == JavaTypeCategory.DECLARED && "java.lang.String".equals(type.erasedName());
  }

  private static boolean fixedRole(SyntaxKind consumerKind, SyntaxRole role) {
    return switch (consumerKind) {
      case UNARY -> role == SyntaxRole.OPERAND;
      case BINARY, CONDITIONAL_AND, CONDITIONAL_OR -> role == SyntaxRole.LEFT_OPERAND || role == SyntaxRole.RIGHT_OPERAND;
      case IF -> role == SyntaxRole.CONDITION;
      case CONDITIONAL_EXPRESSION ->
        role == SyntaxRole.CONDITION || role == SyntaxRole.TRUE_EXPRESSION || role == SyntaxRole.FALSE_EXPRESSION;
      case SWITCH_EXPRESSION -> role == SyntaxRole.SELECTOR;
      case YIELD, ASSIGNMENT -> role == SyntaxRole.VALUE;
      case TYPE_CAST, RETURN -> role == SyntaxRole.EXPRESSION;
      case ARRAY_ACCESS -> role == SyntaxRole.INDEX;
      case LOCAL_DECLARATION -> role == SyntaxRole.INITIALIZER;
      default -> false;
    };
  }
}
