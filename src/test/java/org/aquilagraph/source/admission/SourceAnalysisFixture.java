package org.aquilagraph.source.admission;

import java.util.Optional;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoverageFactValue;
import org.aquilagraph.source.api.CoverageSourceFact;
import org.aquilagraph.source.api.CoverageEvidenceValue;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.UnmatchedSourceFact;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureClassification;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.api.AttributedCoverageExpectations;

/** Admits copied frontend facts and drives native source and graph coordination. */
public final class SourceAnalysisFixture {
  /** Creates a fixture using standard source admission and CFG ports. */
  public SourceAnalysisFixture() {}

  /**
   * Admits a supplied compatible source value in the preceding binding-aware format.
   * @param original values without update expressions
   * @return preceding-format result with exact format-qualified identity
   */
  public static AstResult previousResult(AstResult original) {
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(original.aggregate()),
        original.diagnostics(), original.coverage());
    return AstResultAdmission.admit(input, original.profile(), original.identity().semanticProductionRevision(), AstResult.PREVIOUS_SOURCE_MODEL_VERSION);
  }

  /**
   * Admits compatible copied facts against the preceding Java 25 coverage catalog.
   * @param original source facts that do not encounter update operators or intersection types
   * @return original-format values with the preceding catalog and consistently qualified expectations
   */
  public static AstResult withPreviousCoverage(AstResult original) {
    return withCoverage(original, false);
  }

  /**
   * Admits supplied facts with the compatible update-aware schema and pre-array coverage contract.
   * @param original source values without executable arrays
   * @return format-four result retaining its exact qualified identity
   */
  public static AstResult updateResult(AstResult original) {
    var compatible = withCoverage(original, true);
    var input = new SyntaxAnalysisInput(compatible.snapshot(), compatible.environment(), compatible.program(), Optional.of(compatible.aggregate()),
        compatible.diagnostics(), compatible.coverage());
    return AstResultAdmission.admit(input, compatible.profile(), compatible.identity().semanticProductionRevision(), AstResult.UPDATE_SOURCE_MODEL_VERSION);
  }

  /**
   * Admits supplied compatible array values against the pre-iteration catalog and format.
   * @param original source values without iteration statements
   * @return format-five result with exact qualified identity
   */
  public static AstResult arrayResult(AstResult original) {
    var compatible = withCoverage(original, true, true);
    var input = new SyntaxAnalysisInput(compatible.snapshot(), compatible.environment(), compatible.program(), Optional.of(compatible.aggregate()),
        compatible.diagnostics(), compatible.coverage());
    return AstResultAdmission.admit(input, compatible.profile(), compatible.identity().semanticProductionRevision(), AstResult.ARRAY_SOURCE_MODEL_VERSION);
  }

  /**
   * Admits compatible iteration values in the preceding retained format and coverage catalog.
   * @param original source values without assertions or synchronized statements
   * @return format-six result retaining its qualified identity
   */
  public static AstResult iterationResult(AstResult original) {
    var compatible = withCoverage(original, true, true, true);
    var input = new SyntaxAnalysisInput(compatible.snapshot(), compatible.environment(), compatible.program(), Optional.of(compatible.aggregate()),
        compatible.diagnostics(), compatible.coverage());
    return AstResultAdmission.admit(input, compatible.profile(), compatible.identity().semanticProductionRevision(), AstResult.ITERATION_SOURCE_MODEL_VERSION);
  }

  private static AstResult withCoverage(AstResult original, boolean updates) {
    return withCoverage(original, updates, false);
  }

  private static AstResult withCoverage(AstResult original, boolean updates, boolean arrays) {
    return withCoverage(original, updates, arrays, false);
  }

  private static AstResult withCoverage(AstResult original, boolean updates, boolean arrays, boolean iteration) {
    return withCoverage(original, updates, arrays, iteration, false);
  }

  /**
   * Admits supplied pre-lambda values with the preceding assertion/monitor catalog and retained format.
   * @param original compatible source values without lambdas
   * @return compatible java-source/7 result
   */
  public static AstResult assertionResult(AstResult original) {
    var compatible = withCoverage(original, true, true, true, true);
    var input = new SyntaxAnalysisInput(compatible.snapshot(), compatible.environment(), compatible.program(), Optional.of(compatible.aggregate()),
        compatible.diagnostics(), compatible.coverage());
    return AstResultAdmission.admit(input, compatible.profile(), compatible.identity().semanticProductionRevision(), AstResult.ASSERTION_SOURCE_MODEL_VERSION);
  }

  private static AstResult withCoverage(AstResult original, boolean updates, boolean arrays, boolean iteration, boolean assertions) {
    return withCoverage(original, updates, arrays, iteration, assertions, false);
  }

  /**
   * Admits compatible lambda syntax against its original catalog and retained format.
   * @param original source values without member references
   * @return compatible java-source/8 result
   */
  public static AstResult lambdaResult(AstResult original) {
    var compatible = withCoverage(original, true, true, true, true, true);
    var input = new SyntaxAnalysisInput(compatible.snapshot(), compatible.environment(), compatible.program(), Optional.of(compatible.aggregate()),
        compatible.diagnostics(), compatible.coverage());
    return AstResultAdmission.admit(input, compatible.profile(), compatible.identity().semanticProductionRevision(), AstResult.LAMBDA_SOURCE_MODEL_VERSION);
  }

  private static AstResult withCoverage(AstResult original, boolean updates, boolean arrays, boolean iteration, boolean assertions, boolean lambdas) {
    return withCoverage(original, updates, arrays, iteration, assertions, lambdas, false);
  }

  /**
   * Admits supplied compatible reference values with their preceding catalog and exact format.
   * @param original source values without instance tests or patterns
   * @return compatible java-source/9 result
   */
  public static AstResult referenceResult(AstResult original) {
    var compatible = withCoverage(original, true, true, true, true, true, true);
    var input = new SyntaxAnalysisInput(compatible.snapshot(), compatible.environment(), compatible.program(), Optional.of(compatible.aggregate()),
        compatible.diagnostics(), compatible.coverage());
    return AstResultAdmission.admit(input, compatible.profile(), compatible.identity().semanticProductionRevision(), AstResult.REFERENCE_SOURCE_MODEL_VERSION);
  }

  /**
   * Admits compatible pattern values with the preceding catalog and exact format.
   * @param original source values without expanded switch forms
   * @return compatible java-source/10 result
   */
  public static AstResult patternResult(AstResult original) {
    var compatible = withCoverage(original, true, true, true, true, true, true, true);
    var input = new SyntaxAnalysisInput(compatible.snapshot(), compatible.environment(), compatible.program(), Optional.of(compatible.aggregate()),
        compatible.diagnostics(), compatible.coverage());
    return AstResultAdmission.admit(input, compatible.profile(), compatible.identity().semanticProductionRevision(), AstResult.PATTERN_SOURCE_MODEL_VERSION);
  }

  /**
   * Admits compatible switch values against the catalog predating exception expansion.
   * @param original source values without resources, finally clauses, or union catch types
   * @return compatible java-source/11 result with its original catalog and identity
   */
  public static AstResult switchResult(AstResult original) {
    var compatible = withCoverage(original, true, true, true, true, true, true, true, true);
    var input = new SyntaxAnalysisInput(compatible.snapshot(), compatible.environment(), compatible.program(), Optional.of(compatible.aggregate()),
        compatible.diagnostics(), compatible.coverage());
    return AstResultAdmission.admit(input, compatible.profile(), compatible.identity().semanticProductionRevision(), AstResult.SWITCH_SOURCE_MODEL_VERSION);
  }

  private static AstResult withCoverage(AstResult original, boolean updates, boolean arrays, boolean iteration, boolean assertions, boolean lambdas, boolean references) {
    return withCoverage(original, updates, arrays, iteration, assertions, lambdas, references, false);
  }

  private static AstResult withCoverage(AstResult original, boolean updates, boolean arrays, boolean iteration, boolean assertions, boolean lambdas, boolean references, boolean patterns) {
    return withCoverage(original, updates, arrays, iteration, assertions, lambdas, references, patterns, false);
  }

  private static AstResult withCoverage(AstResult original, boolean updates, boolean arrays, boolean iteration, boolean assertions, boolean lambdas, boolean references, boolean patterns, boolean switches) {
    var exceptionSurfaces = Set.of("TryTree", "CatchTree", "UnionTypeTree");
    var switchKinds = switches ? Set.<String>of() : Set.of("SWITCH", "PATTERN_CASE_LABEL");
    var switchSurfaces = switches ? Set.<String>of() : Set.of("SwitchTree", "SwitchExpressionTree", "CaseTree", "YieldTree");
    var patternKinds = Set.of("INSTANCE_OF", "BINDING_PATTERN", "DECONSTRUCTION_PATTERN", "ANY_PATTERN");
    var patternSurfaces = Set.of("InstanceOfTree", "BindingPatternTree", "DeconstructionPatternTree", "AnyPatternTree");
    var iterationKinds = assertions ? Set.of("LAMBDA_EXPRESSION") : iteration ? Set.of("LAMBDA_EXPRESSION", "ASSERT", "SYNCHRONIZED") : Set.of("LAMBDA_EXPRESSION", "ASSERT", "SYNCHRONIZED", "WHILE_LOOP", "DO_WHILE_LOOP", "FOR_LOOP", "ENHANCED_FOR_LOOP", "LABELED_STATEMENT", "BREAK", "CONTINUE");
    var iterationSurfaces = assertions ? Set.of("LambdaExpressionTree") : iteration ? Set.of("LambdaExpressionTree", "AssertTree", "SynchronizedTree") : Set.of("LambdaExpressionTree", "AssertTree", "SynchronizedTree", "WhileLoopTree", "DoWhileLoopTree", "ForLoopTree", "EnhancedForLoopTree", "LabeledStatementTree", "BreakTree", "ContinueTree");
    var changedKinds = Set.of("MULTIPLY_ASSIGNMENT", "DIVIDE_ASSIGNMENT", "REMAINDER_ASSIGNMENT", "PLUS_ASSIGNMENT",
        "MINUS_ASSIGNMENT", "LEFT_SHIFT_ASSIGNMENT", "RIGHT_SHIFT_ASSIGNMENT", "UNSIGNED_RIGHT_SHIFT_ASSIGNMENT",
        "AND_ASSIGNMENT", "XOR_ASSIGNMENT", "OR_ASSIGNMENT", "PREFIX_INCREMENT", "PREFIX_DECREMENT", "POSTFIX_INCREMENT",
        "POSTFIX_DECREMENT", "INTERSECTION_TYPE");
    var catalog = LanguageFeatureCoverage.forRelease(original.coverage().coverage().release(),
        original.coverage().coverage().classifications().stream()
            .filter(classification -> (arrays || !classification.feature().identifier().startsWith("NewArrayTree::"))
                && exceptionSurfaces.stream().noneMatch(surface -> classification.feature().identifier().startsWith(surface + "::"))
                && (references || !classification.feature().identifier().startsWith("MemberReferenceTree::"))
                && (patterns || patternSurfaces.stream().noneMatch(surface -> classification.feature().identifier().startsWith(surface + "::")))
                && switchSurfaces.stream().noneMatch(surface -> classification.feature().identifier().startsWith(surface + "::"))
                && iterationSurfaces.stream().noneMatch(surface -> classification.feature().identifier().startsWith(surface + "::") && !(lambdas && surface.equals("LambdaExpressionTree"))))
            .map(classification -> {
          var feature = classification.feature();
          if (!arrays && feature.category() == LanguageFeatureCategory.COMPILER_TREE_KIND && "NEW_ARRAY".equals(feature.identifier())) {
            return LanguageFeatureClassification.represented(feature,
                "JavaAstAggregate annotation array value; unsupported method-body arrays retain method-syntax diagnostics");
          }
          return feature.category() == LanguageFeatureCategory.COMPILER_TREE_KIND
              && ("UNION_TYPE".equals(feature.identifier()) || (!references && "MEMBER_REFERENCE".equals(feature.identifier())) || (!patterns && patternKinds.contains(feature.identifier())) || switchKinds.contains(feature.identifier()) || (iterationKinds.contains(feature.identifier()) && !(lambdas && feature.identifier().equals("LAMBDA_EXPRESSION"))) || (!updates && changedKinds.contains(feature.identifier())))
              ? LanguageFeatureClassification.diagnosed(feature,
                  "frontend.unsupported-tree-kind." + feature.identifier().toLowerCase(Locale.ROOT).replace('_', '-'))
              : classification;
        }).toList());
    var evidence = original.coverage().validationEvidence();
    var written = evidence.writtenInventory();
    var contexts = written.contexts().stream().filter(context -> context.feature()
        .map(feature -> (arrays || !feature.identifier().startsWith("NewArrayTree::"))
            && exceptionSurfaces.stream().noneMatch(surface -> feature.identifier().startsWith(surface + "::"))
            && (references || !feature.identifier().startsWith("MemberReferenceTree::"))
            && (patterns || patternSurfaces.stream().noneMatch(surface -> feature.identifier().startsWith(surface + "::")))
            && switchSurfaces.stream().noneMatch(surface -> feature.identifier().startsWith(surface + "::"))
            && iterationSurfaces.stream().noneMatch(surface -> feature.identifier().startsWith(surface + "::") && !(lambdas && surface.equals("LambdaExpressionTree")))).orElse(true)).toList();
    var inventory = new SourceCoverageInventory(written.snapshot(), written.release(), catalog.contractIdentity(), contexts);
    var attributed = new AttributedCoverageExpectations(inventory, evidence.attributedExpectations().projections());
    var addresses = contexts.stream().map(context -> context.address()).collect(Collectors.toUnmodifiableSet());
    var outcomes = evidence.outcomes().stream().filter(outcome -> addresses.contains(outcome.address()))
        .map(outcome -> new FeatureContextOutcome(outcome.address(), outcome.occurrence().map(occurrence ->
            new LanguageFeatureOccurrence(occurrence.sourceFileKey(), catalog.classificationOf(occurrence.classification().feature()).orElseThrow(),
                occurrence.origins())), outcome.observation().map(observation -> new LanguageFeatureObservation(observation.sourceFileKey(),
                    catalog.classificationOf(observation.classification().feature()).orElseThrow(), observation.origins(), observation.diagnostic())),
            outcome.encounteredValue(), outcome.support(), outcome.emissions())).toList();
    var coverage = LanguageFeatureCoverageResult.fromEvidence(catalog, inventory, attributed, outcomes, evidence.unmatchedFacts(),
        evidence.diagnosticEmissions());
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(original.aggregate()),
        original.diagnostics(), coverage);
    return AstResultAdmission.admit(input, original.profile(), original.identity().semanticProductionRevision(), original.identity().sourceModelVersion());
  }

  /**
   * Admits compatible older-schema content with its original qualified format.
   * @param original source facts containing only older-compatible plain named locals
   * @return older-format result used by closed-format and real repository controls
   */
  public static AstResult legacyResult(AstResult original) {
    var captured = CopiedAstValues.capture(original.aggregate());
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    var sources = captured.sources().stream().map(source -> new SyntaxSourceContribution(source.sourceFileKey(),
        legacyNode(source.root(), replacements), source.nodes().stream().map(node -> legacyNode(node, replacements)).toList(),
        source.structuralEdges(), source.semanticEdges())).toList();
    var bodies = captured.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), legacyNode(body.root(), replacements),
        body.origin(), body.uses(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(),
        body.attributedOccurrences())).toList();
    var aggregate = AstValueAdmission.admit(new CopiedAstValues(captured.snapshot(), sources, bodies, captured.memberOrders(),
        captured.associations(), captured.declarationTypes()));
    var evidence = original.coverage().validationEvidence();
    var outcomes = evidence.outcomes().stream().map(outcome -> {
      var support = outcome.support();
      return new FeatureContextOutcome(outcome.address(), outcome.occurrence(), outcome.observation(), outcome.encounteredValue(),
          new FeatureContextOutcome.Support(support.mode(), support.facts().stream().map(SourceAnalysisFixture::legacyFact).toList(),
              support.policyContext(), support.sourceFile(), support.negativeState()), outcome.emissions());
    }).toList();
    var unmatched = evidence.unmatchedFacts().stream().map(fact -> new UnmatchedSourceFact(fact.owner(), legacyFact(fact.fact()),
        fact.ordinal(), fact.reason(), fact.emission())).toList();
    var coverage = LanguageFeatureCoverageResult.fromEvidence(original.coverage().coverage(), evidence.writtenInventory(),
        evidence.attributedExpectations(), outcomes, unmatched, evidence.diagnosticEmissions());
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(aggregate),
        original.diagnostics(), coverage);
    return AstResultAdmission.admit(input, original.profile(), original.identity().semanticProductionRevision(), AstResult.LEGACY_SOURCE_MODEL_VERSION);
  }

  private static SyntaxNode legacyNode(SyntaxNode node, Map<SyntaxNode, SyntaxNode> replacements) {
    var existing = replacements.get(node);
    if (existing != null) {
      return existing;
    }
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), legacyNode(child.node(), replacements))).toList();
    var value = node.value();
    if (value instanceof SyntaxValue.LocalDeclaration local) {
      var binding = local.bindingEvidence().orElseThrow();
      if (local.name().equals("_") || !local.writtenModifiers().isEmpty() || binding.typeForm() != LocalBindingEvidence.TypeForm.DECLARED
          || binding.annotations() != 0 || !binding.sharedAnnotationKeys().isEmpty()) {
        throw new IllegalArgumentException("The older-schema control requires plain named declared locals");
      }
      value = new SyntaxValue.LocalDeclaration(local.name(), local.writtenType(), local.writtenModifiers());
    }
    var result = node.origin().isPresent()
        ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), value, children,
            node.semanticReference(), node.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), value,
            children, node.semanticReference());
    replacements.put(node, result);
    return result;
  }

  private static CoverageFactReference legacyFact(CoverageFactReference reference) {
    if (!(reference.value() instanceof CoverageFactValue.Source source)
        || source.value().value().tag() != CoverageEvidenceValue.Tag.LOCAL_DECLARATION) {
      return reference;
    }
    var fact = source.value();
    var value = new CoverageEvidenceValue.Structure(CoverageEvidenceValue.Tag.LOCAL_DECLARATION,
        fact.value().fields().stream().filter(field -> !field.name().equals("bindingEvidence")).toList());
    var legacy = new CoverageSourceFact(fact.structuralPath(), fact.sourceKind(), fact.syntaxKind(), fact.type(), value, fact.children(),
        fact.semanticReference(), fact.sourceAssociation());
    return new CoverageFactReference(reference.kind(), reference.identity(), reference.sourceFileKey(), new CoverageFactValue.Source(legacy), reference.origin());
  }

  /**
   * Admits actual compiler observations without reacquiring the compiler.
   * @param input copied frontend capture
   * @return native run with all produced bodies selected
   */
  public AnalysisRun analyze(SyntaxAnalysisInput input) {
    return analyze(AstResultAdmission.outcome(input, AstProfile.JAVA_SOURCE_BOUNDED, "javac-public/1"));
  }

  /**
   * Coordinates an admitted source outcome without replacing its ownership.
   * @param ast exact admitted outcome
   * @return native run with all produced bodies selected
   */
  public AnalysisRun analyze(AstOutcome ast) {
    var request = new AnalysisRequest(new SourceInput.Snapshot(ast.capturedInput().orElseThrow()),
        ast.requestedConfiguration().orElseThrow(), ast.profile(), Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)),
        PublicationIntent.DO_NOT_SAVE);
    return new SourceAnalysisRunner(ignored -> ast, CfgDerivers.standard()).analyze(request);
  }
}
