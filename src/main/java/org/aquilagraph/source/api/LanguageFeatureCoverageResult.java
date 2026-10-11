package org.aquilagraph.source.api;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.identity.api.JavaRelease;

/** Immutable admitted per-input language-feature outcome and independent validation evidence. */
public final class LanguageFeatureCoverageResult {
  private static final long EXPECTED_COMPILATION_UNIT_CONTEXTS = 1L;
  private static final int EXPECTED_WRITTEN_TYPE_USE_FACTS = 1;
  private static final int EXPECTED_WRITTEN_TYPE_USE_OWNERS = 1;
  private static final Map<JavaRelease, Set<String>> AUTHORITATIVE_CONTRACT_IDENTITIES = Map.of(JavaRelease.JAVA_25,
      Set.of("java-language-feature-coverage:1aa72c351c5b0c6af37bed7951ef08eb038cdb214148b933ac6a6bf93db97bb5",
          "java-language-feature-coverage:e0968b3e0d4da804090dff9f60d557181733dff543864f096e1a854a0e0d3ac2",
          "java-language-feature-coverage:307ae3a6c3bf92cbb9cae296574a250b2558b403e949f57d534a1ef141a17e0e",
          "java-language-feature-coverage:6661250f922a3b0bb0f7aa7a01dc27b8f2c3bdc880b95cd781b0e15a32b017e4",
          "java-language-feature-coverage:0acd09f319196e03148af21d885419a440820cc7506a081b9f8cad0222eecb91",
          "java-language-feature-coverage:b9ed57677bf06688e4f3b06cb3287035dc4f0951aeb4766f50a22a3ab7d8417e",
          "java-language-feature-coverage:57c6cdc127ac8360381c721d17d84ed5ca5b682275ab72ddb3bdae324baad5bf",
          "java-language-feature-coverage:09c76957002dba67b40c181c9df2283dfc5fbd2e72d70202adbab2e9606bf8e1",
          "java-language-feature-coverage:6c67a26fcdbe5f3d7fe6302796e917a38886a348f5619c98817ac3c4958d9bf4",
          "java-language-feature-coverage:f39568916d81bfafc9f3300c9d8cb9a69aacf1073874cea3855a628d77d5f54c",
          "java-language-feature-coverage:aa7f14c098cdf30b107ec6b2c7670c85cc1f66fd4001c71b0287f664a907503d",
          "java-language-feature-coverage:eef5d9c12e297d9a4b530a7b84d7f7d25af272382b954b8ace2efb7522d2ccb6"));
  private final LanguageFeatureCoverage coverageContract;
  private final List<LanguageFeatureOccurrence> occurrenceValues;
  private final List<LanguageFeatureObservation> observationValues;
  private final List<AnalysisDiagnostic> capabilityErrors;
  private final CoverageValidationEvidence retainedEvidence;

  private LanguageFeatureCoverageResult(LanguageFeatureCoverage coverageContract, CoverageValidationEvidence evidence) {
    this.coverageContract = coverageContract;
    retainedEvidence = evidence;
    evidence.diagnosticEmissions().forEach(emission -> SourceDiagnosticAdmission.requireCreatingStage(emission.diagnostic()));
    occurrenceValues = List.copyOf(evidence.outcomes().stream().flatMap(outcome -> outcome.occurrence().stream()).toList());
    observationValues = List.copyOf(evidence.outcomes().stream().flatMap(outcome -> outcome.observation().stream()).toList());
    capabilityErrors = List
        .copyOf(evidence.diagnosticEmissions().stream().map(DiagnosticEmission::diagnostic).filter(this::isCapabilityDiagnostic).toList());
    validateOccurrences(coverageContract, occurrenceValues);
    validateObservations(coverageContract, observationValues, capabilityErrors);
    validateOccurrenceInventory(occurrenceValues, observationValues);
    validateCapabilityDiagnostics(coverageContract, capabilityErrors);
    validateCoverageEventOwnership(coverageContract, evidence);
    validateDiagnosticOwnership(observationValues, capabilityErrors);
  }

  /**
   * Admits complete contextual accounting against independently supplied written and attributed expectations.
   *
   * @param coverageContract
   *          exact active release contract
   * @param writtenInventory
   *          frozen pre-attribution source obligations
   * @param attributedExpectations
   *          independently queried semantic projections
   * @param outcomes
   *          one ordered policy/support outcome for each captured context
   * @param unmatchedFacts
   *          diagnosed owner-bound extra actual facts
   * @param diagnosticEmissions
   *          creating-site events with ordered final slots
   * @return immutable admitted result
   */
  public static LanguageFeatureCoverageResult fromEvidence(LanguageFeatureCoverage coverageContract,
      SourceCoverageInventory writtenInventory, AttributedCoverageExpectations attributedExpectations, List<FeatureContextOutcome> outcomes,
      List<UnmatchedSourceFact> unmatchedFacts, List<DiagnosticEmission> diagnosticEmissions) {
    Objects.requireNonNull(coverageContract, "coverage");
    validateAuthoritativeContract(coverageContract);
    var evidence = new CoverageValidationEvidence(writtenInventory, attributedExpectations, outcomes, unmatchedFacts, diagnosticEmissions);
    validateIndependentEvidence(coverageContract, evidence);
    return new LanguageFeatureCoverageResult(coverageContract, evidence);
  }

  /**
   * Returns the exact release-bound contract.
   *
   * @return immutable contract
   */
  public LanguageFeatureCoverage coverage() {
    return coverageContract;
  }

  /**
   * Returns known policy occurrences in producer partition order.
   *
   * @return immutable occurrences
   */
  public List<LanguageFeatureOccurrence> occurrences() {
    return occurrenceValues;
  }

  /**
   * Returns known policy observations, including those with separately failed support.
   *
   * @return immutable observations
   */
  public List<LanguageFeatureObservation> observations() {
    return observationValues;
  }

  /**
   * Returns the catalog capability subset of retained diagnostic events.
   *
   * @return immutable diagnostics
   */
  public List<AnalysisDiagnostic> capabilityDiagnostics() {
    return capabilityErrors;
  }

  /**
   * Returns immutable evidence for actual source/fact/event admission.
   *
   * @return complete validation evidence
   */
  public CoverageValidationEvidence validationEvidence() {
    return retainedEvidence;
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof LanguageFeatureCoverageResult result && coverageContract.equals(result.coverageContract)
        && retainedEvidence.equals(result.retainedEvidence));
  }

  @Override
  public int hashCode() {
    return Objects.hash(coverageContract, retainedEvidence);
  }

  @Override
  public String toString() {
    return "LanguageFeatureCoverageResult[coverage=" + coverageContract + ", validationEvidence=" + retainedEvidence + ']';
  }

  private static void validateIndependentEvidence(LanguageFeatureCoverage coverage, CoverageValidationEvidence evidence) {
    var inventory = evidence.writtenInventory();
    if (inventory.release() != coverage.release() || !inventory.contractIdentity().equals(coverage.contractIdentity())
        || !evidence.attributedExpectations().writtenInventory().equals(inventory)) {
      throw new IllegalArgumentException("Both independent expectation stages must bind the active source and release contract");
    }
    var contexts = inventory.contexts();
    if (!contexts.stream().map(SourceContext::address).toList()
        .equals(evidence.outcomes().stream().map(FeatureContextOutcome::address).toList())) {
      throw new IllegalArgumentException("Coverage outcomes must exactly match the independent context inventory in order");
    }
    for (var source : inventory.snapshot().sourceFiles()) {
      var count = contexts.stream()
          .filter(context -> context.address().sourceFileKey().equals(source.sourceFileKey())
              && context.address().partition() == SourceContext.Partition.TREE
              && context.feature().filter(feature -> feature.category() == LanguageFeatureCategory.COMPILER_TREE_KIND
                  && "COMPILATION_UNIT".equals(feature.identifier())).isPresent())
          .count();
      if (count != EXPECTED_COMPILATION_UNIT_CONTEXTS) {
        throw new IllegalArgumentException("Every captured source requires exactly one independent compilation-unit context");
      }
    }
    var emissions = new HashMap<DiagnosticEmission.Address, DiagnosticEmission>();
    for (var index = 0; index < evidence.diagnosticEmissions().size(); index++) {
      var emission = evidence.diagnosticEmissions().get(index);
      if (emission.slot() != index || emissions.putIfAbsent(emission.address(), emission) != null
          || !contexts.stream().map(SourceContext::address).toList().containsAll(emission.contexts())) {
        throw new IllegalArgumentException("Diagnostic events require unique creating-site addresses and exact ordered slots");
      }
    }
    for (var index = 0; index < contexts.size(); index++) {
      validateContext(coverage, contexts.get(index), evidence.outcomes().get(index), evidence, emissions);
    }
    var unmatched = new HashSet<String>();
    for (var fact : evidence.unmatchedFacts()) {
      if (!contexts.stream().map(SourceContext::address).toList().contains(fact.owner())
          || !unmatched.add(fact.fact().kind() + ":" + fact.fact().identity() + ":" + fact.ordinal())) {
        throw new IllegalArgumentException("Unmatched actual facts require a unique captured owner and actual ordinal");
      }
      var owner = contexts.stream().filter(context -> context.address().equals(fact.owner())).findFirst().orElseThrow();
      var code = requireBlockingEvent(fact.emission(), owner, emissions).diagnostic().code();
      if (generalizedEvidenceCode(code) && !code.equals(fact.reason() == UnmatchedSourceFact.Reason.DUPLICATE
          ? "frontend.duplicate-source-evidence"
          : "frontend.unexpected-source-evidence")) {
        throw new IllegalArgumentException("Unmatched actual facts require their exact extra or duplicate evidence cause");
      }
    }
  }

  private static void validateCoverageEventOwnership(LanguageFeatureCoverage coverage, CoverageValidationEvidence evidence) {
    var capabilityCodes = diagnosedCodes(coverage);
    for (var event : evidence.diagnosticEmissions()) {
      var capability = capabilityCodes.contains(event.diagnostic().code());
      if (!capability && !generalizedEvidenceCode(event.diagnostic().code())) {
        continue;
      }
      var policyOwner = false;
      if (event.contexts().isEmpty()) {
        throw new IllegalArgumentException("Every coverage event requires exact address-based context ownership");
      }
      for (var address : event.contexts()) {
        var outcome = evidence.outcomes().stream().filter(value -> value.address().equals(address)).findFirst().orElseThrow();
        var ownsPolicy = outcome.emissions().contains(event.address())
            && outcome.observation().flatMap(LanguageFeatureObservation::diagnostic).filter(event.diagnostic()::equals).isPresent();
        var ownsFailure = outcome.emissions().contains(event.address()) && outcome.support().mode() == FeatureContextOutcome.Mode.FAILED;
        var ownsUnmatched = evidence.unmatchedFacts().stream()
            .anyMatch(fact -> fact.owner().equals(address) && fact.emission().equals(event.address()));
        if (!(ownsPolicy || ownsFailure || ownsUnmatched)) {
          throw new IllegalArgumentException("Every coverage event requires exact address-based context ownership");
        }
        policyOwner |= ownsPolicy;
      }
      if (capability && !policyOwner) {
        throw new IllegalArgumentException("Every capability event requires its exact creating-address policy owner");
      }
    }
  }

  private static void validateContext(LanguageFeatureCoverage coverage, SourceContext context, FeatureContextOutcome outcome,
      CoverageValidationEvidence evidence, Map<DiagnosticEmission.Address, DiagnosticEmission> emissions) {
    context.feature().ifPresent(feature -> {
      if (outcome.occurrence().isEmpty() || !outcome.occurrence().orElseThrow().classification().feature().equals(feature)) {
        throw new IllegalArgumentException("Known source contexts require their independently produced exact policy pair");
      }
    });
    if (outcome.occurrence().isEmpty() && outcome.support().mode() != FeatureContextOutcome.Mode.FAILED
        && context.address().partition() != SourceContext.Partition.WRITTEN_TYPE_USE) {
      throw new IllegalArgumentException("Unavailable feature identity requires blocking failed support without a guessed pair");
    }
    outcome.occurrence().ifPresent(occurrence -> validateOccurrence(coverage, occurrence));
    validateContextBoundary(context, outcome);
    if (context.address().partition() == SourceContext.Partition.WRITTEN_TYPE_USE) {
      validateWrittenTypeUse(context, outcome, evidence);
    }
    outcome.occurrence().ifPresent(occurrence -> {
      if (!occurrence.origins().equals(context.origins())) {
        throw new IllegalArgumentException("Known policy pairs must retain their exact independently captured owner origins");
      }
    });
    outcome.occurrence().ifPresent(occurrence -> {
      if ((occurrence.classification().policy() == LanguageFeaturePolicy.REPRESENTED) == (outcome.support()
          .mode() == FeatureContextOutcome.Mode.NOT_REQUIRED)) {
        throw new IllegalArgumentException(
            "Represented policy requires explicit support; diagnosed or irrelevant policy cannot claim represented success");
      }
    });
    var expected = context.requiresAttribution()
        ? evidence.attributedExpectations().projections().stream().filter(projection -> projection.address().equals(context.address()))
            .map(AttributedCoverageExpectations.Projection::value).findFirst()
        : context.writtenValue();
    if (expected.isPresent() && !expected.equals(outcome.encounteredValue())) {
      throw new IllegalArgumentException("Actual accessor encounters must exactly match the independent scalar/list projection");
    }
    if (outcome.support().mode() == FeatureContextOutcome.Mode.FAILED) {
      if (outcome.emissions().isEmpty()) {
        throw new IllegalArgumentException("Failed represented support requires an ordinary blocking emission");
      }
      outcome.emissions().forEach(address -> {
        var code = requireBlockingEvent(address, context, emissions).diagnostic().code();
        if (generalizedEvidenceCode(code)
            && !List.of("frontend.missing-source-evidence", "frontend.inconsistent-source-evidence").contains(code)) {
          throw new IllegalArgumentException("Failed expected support requires its exact missing or inconsistent evidence cause");
        }
      });
    } else if (expected.filter(value -> value.state() == CoveragePropertyValue.State.UNAVAILABLE).isPresent()) {
      throw new IllegalArgumentException("Unavailable attribution cannot certify successful support");
    }
    if (outcome.support().mode() == FeatureContextOutcome.Mode.NEGATIVE && !expected.equals(outcome.support().negativeState())) {
      throw new IllegalArgumentException("Negative support must match an independently captured absent or empty owner accessor");
    }
    if ((outcome.support().mode() == FeatureContextOutcome.Mode.SOURCE_FILE
        && !"CompilationUnitTree::CompilationUnitTree#getSourceFile()".equals(context.address().role()))
        || (outcome.support().mode() == FeatureContextOutcome.Mode.POLICY_PAIR
            && !"CompilationUnitTree::Tree#getKind()".equals(context.address().role()))) {
      throw new IllegalArgumentException("Successful support mode must match the exact catalog accessor boundary");
    }
    if (outcome.support().mode() == FeatureContextOutcome.Mode.ATTRIBUTED_PAIR
        && (context.address().partition() != SourceContext.Partition.TYPE || outcome.observation().isEmpty())) {
      throw new IllegalArgumentException("Attributed pair support belongs only to a known attributed type-kind context");
    }
    if (outcome.occurrence().filter(value -> value.classification().policy() == LanguageFeaturePolicy.REPRESENTED).isPresent()
        && outcome.support().mode() != FeatureContextOutcome.Mode.FAILED) {
      var mode = outcome.support().mode();
      var correct = context.address().partition() == SourceContext.Partition.TYPE
          ? mode == (CoverageActualFacts.MATCH_ALL_TYPE_ROLE.equals(context.address().role())
              ? FeatureContextOutcome.Mode.FACTS
              : FeatureContextOutcome.Mode.ATTRIBUTED_PAIR)
          : "CompilationUnitTree::Tree#getKind()".equals(context.address().role())
              ? mode == FeatureContextOutcome.Mode.POLICY_PAIR
              : "CompilationUnitTree::CompilationUnitTree#getSourceFile()".equals(context.address().role())
                  ? mode == FeatureContextOutcome.Mode.SOURCE_FILE
                  : expected.filter(
                      value -> value.state() == CoveragePropertyValue.State.ABSENT || value.state() == CoveragePropertyValue.State.EMPTY)
                      .isPresent() ? mode == FeatureContextOutcome.Mode.NEGATIVE : mode == FeatureContextOutcome.Mode.FACTS;
      if (!correct) {
        throw new IllegalArgumentException("Represented success requires its exact catalog support mode");
      }
      if (CoverageActualFacts.MATCH_ALL_TYPE_ROLE.equals(context.address().role())
          && !CoverageActualFacts.supportsPatternType(context, expected.orElseThrow(), outcome.support().facts())) {
        throw new IllegalArgumentException("Match-all type support requires its exact attributed source fact");
      }
    }
    if (outcome.support().mode() == FeatureContextOutcome.Mode.POLICY_PAIR) {
      var linked = evidence.outcomes().stream()
          .filter(candidate -> candidate.address().equals(outcome.support().policyContext().orElseThrow())).findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Policy support requires an admitted tree-kind context"));
      if (!linked.address().sourceFileKey().equals(context.address().sourceFileKey())
          || !linked.address().ownerPath().equals(context.address().ownerPath())
          || linked.observation().filter(value -> value.classification().feature().category() == LanguageFeatureCategory.COMPILER_TREE_KIND
              && "COMPILATION_UNIT".equals(value.classification().feature().identifier())).isEmpty()) {
        throw new IllegalArgumentException("Unit-kind property must reference the exact same-owner compilation-unit policy pair");
      }
    }
    outcome.support().sourceFile().ifPresent(source -> {
      if (!source.sourceFileKey().equals(context.address().sourceFileKey())
          || !evidence.writtenInventory().snapshot().sourceFiles().contains(source)) {
        throw new IllegalArgumentException("Source-file support must name the exact captured source value");
      }
    });
    outcome.support().facts().forEach(fact -> {
      if (!fact.sourceFileKey().equals(context.address().sourceFileKey())) {
        throw new IllegalArgumentException("Positive support facts must belong to their captured source owner");
      }
    });
    if (context.address().partition() == SourceContext.Partition.ATTRIBUTED_PROPERTY
        && outcome.support().mode() == FeatureContextOutcome.Mode.FACTS) {
      var owner = evidence.attributedExpectations().projections().stream().filter(value -> value.address().equals(context.address()))
          .flatMap(value -> value.ownerIdentity().stream()).findFirst().orElseThrow();
      if (outcome.support().facts().stream().noneMatch(fact -> fact.identity().equals(owner))) {
        throw new IllegalArgumentException("Semantic property support requires its independently resolved exact retained owner");
      }
    }
    outcome.observation().flatMap(LanguageFeatureObservation::diagnostic).ifPresent(diagnostic -> {
      if (outcome.emissions().stream().map(emissions::get).filter(Objects::nonNull)
          .noneMatch(event -> event.diagnostic().equals(diagnostic))) {
        throw new IllegalArgumentException("Diagnosed policy pair requires its creating-site diagnostic association");
      }
    });
    for (var address : outcome.emissions()) {
      if (!emissions.containsKey(address) || !emissions.get(address).contexts().contains(context.address())) {
        throw new IllegalArgumentException("Context emission associations must name exact retained events");
      }
    }
  }

  private static void validateContextBoundary(SourceContext context, FeatureContextOutcome outcome) {
    var feature = outcome.occurrence().map(value -> value.classification().feature());
    var correctCategory = switch (context.address().partition()) {
      case TREE -> feature.filter(value -> value.category() == LanguageFeatureCategory.COMPILER_TREE_KIND).isPresent();
      case TYPE -> feature.isEmpty() || feature.orElseThrow().category() == LanguageFeatureCategory.COMPILER_TYPE_KIND;
      case WRITTEN_TYPE_USE -> feature.isEmpty();
      case WRITTEN_PROPERTY, ATTRIBUTED_PROPERTY ->
        feature.filter(value -> value.category() == LanguageFeatureCategory.DECLARATION_PROPERTY).isPresent();
      case TRANSLATION -> true;
    };
    if (!correctCategory) {
      throw new IllegalArgumentException("Context policy must match its exact source evidence partition");
    }
    if (context.address().partition() == SourceContext.Partition.WRITTEN_PROPERTY
        || context.address().partition() == SourceContext.Partition.ATTRIBUTED_PROPERTY) {
      var accessor = feature.orElseThrow().identifier();
      var written = List.of("CompilationUnitTree::", "ClassTree::", "MethodTree::", "VariableTree::", "NewArrayTree::").stream()
          .anyMatch(accessor::startsWith) || CoverageExecutableFacts.isSurface(accessor);
      var attributed = List.of("TypeElement::", "ExecutableElement::").stream().anyMatch(accessor::startsWith);
      var correctSurface = context.address().partition() == SourceContext.Partition.WRITTEN_PROPERTY ? written : attributed;
      if (!accessor.equals(context.address().role()) || !correctSurface
          || outcome.occurrence().orElseThrow().classification().policy() != LanguageFeaturePolicy.REPRESENTED) {
        throw new IllegalArgumentException("Declaration property contexts require their exact accessor and owner surface");
      }
    }
    var semantic = context.address().partition() == SourceContext.Partition.TYPE
        || context.address().partition() == SourceContext.Partition.ATTRIBUTED_PROPERTY;
    if (context.address().partition() != SourceContext.Partition.TRANSLATION && (context.requiresAttribution() != semantic
        || (context.address().partition() == SourceContext.Partition.WRITTEN_PROPERTY) != context.writtenValue().isPresent())) {
      throw new IllegalArgumentException("Context evidence must use its exact written or attributed expectation stage");
    }
  }

  private static void validateWrittenTypeUse(SourceContext context, FeatureContextOutcome outcome, CoverageValidationEvidence evidence) {
    if (context.feature().isPresent() || context.writtenValue().isPresent() || context.requiresAttribution()
        || outcome.occurrence().isPresent() || outcome.observation().isPresent() || outcome.encounteredValue().isPresent()
        || (outcome.support().mode() != FeatureContextOutcome.Mode.FACTS
            && outcome.support().mode() != FeatureContextOutcome.Mode.FAILED)) {
      throw new IllegalArgumentException("Written type-use obligations require independent source facts or blocking failed support");
    }
    var owners = evidence.writtenInventory().contexts().stream()
        .filter(candidate -> candidate.address().partition() == SourceContext.Partition.TREE
            && candidate.address().sourceFileKey().equals(context.address().sourceFileKey())
            && candidate.address().ownerPath().equals(context.address().ownerPath()))
        .toList();
    if (owners.size() != EXPECTED_WRITTEN_TYPE_USE_OWNERS || !owners.getFirst().origins().equals(context.origins())) {
      throw new IllegalArgumentException("Written type-use obligations require their exact independently captured tree owner");
    }
    if (outcome.support().mode() == FeatureContextOutcome.Mode.FAILED) {
      return;
    }
    if (outcome.support().facts().size() != EXPECTED_WRITTEN_TYPE_USE_FACTS) {
      throw new IllegalArgumentException("Written type-use support requires one exact source occurrence");
    }
    var fact = outcome.support().facts().getFirst();
    var sourceKind = owners.getFirst().feature().orElseThrow().identifier();
    if (fact.kind() != CoverageFactReference.Kind.SYNTAX || !(fact.value() instanceof CoverageFactValue.Source source)
        || !"TYPE_USE".equals(source.value().syntaxKind()) || !source.value().sourceKind().equals(sourceKind)
        || !fact.origin().stream().toList().equals(context.origins())
        || source.value().sourceAssociation()
            .filter(association -> association.nodeKey().equals(fact.identity())
                && association.sourceFileKey().equals(context.address().sourceFileKey())
                && association.ownerPath().equals(context.address().ownerPath()) && association.sourceKind().equals(sourceKind)
                && association.origin().equals(fact.origin()))
            .isEmpty()) {
      throw new IllegalArgumentException("Written type-use support must retain its exact source kind, owner path and origin");
    }
  }

  private static DiagnosticEmission requireBlockingEvent(DiagnosticEmission.Address address, SourceContext context,
      Map<DiagnosticEmission.Address, DiagnosticEmission> emissions) {
    var emission = emissions.get(address);
    if (emission == null || !emission.diagnostic().isError() || emission.diagnostic().stage() != DiagnosticStage.FRONTEND
        || !emission.contexts().contains(context.address())) {
      throw new IllegalArgumentException("Blocking source evidence requires an exact retained owner-associated error event");
    }
    if (generalizedEvidenceCode(emission.diagnostic().code())
        && (emission.cause() != AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR || !emission.diagnostic().origins().equals(context.origins()))) {
      throw new IllegalArgumentException("Generalized source evidence requires its exact frontend cause and captured origins");
    }
    return emission;
  }

  private static boolean generalizedEvidenceCode(String code) {
    return List.of("frontend.missing-source-evidence", "frontend.duplicate-source-evidence", "frontend.unexpected-source-evidence",
        "frontend.inconsistent-source-evidence").contains(code);
  }

  private static void validateOccurrences(LanguageFeatureCoverage coverage, List<LanguageFeatureOccurrence> occurrences) {
    occurrences.forEach(occurrence -> validateOccurrence(coverage, occurrence));
  }

  private static void validateOccurrence(LanguageFeatureCoverage coverage, LanguageFeatureOccurrence occurrence) {
    var classification = Objects.requireNonNull(occurrence, "occurrence").classification();
    var declared = coverage.classificationOf(classification.feature())
        .orElseThrow(() -> new IllegalArgumentException("Occurred language feature is absent from the coverage contract"));
    if (!declared.equals(classification)) {
      throw new IllegalArgumentException("Occurred language feature classification differs from the coverage contract");
    }
  }

  private static void validateOccurrenceInventory(List<LanguageFeatureOccurrence> occurrences,
      List<LanguageFeatureObservation> observations) {
    var observedOccurrences = observations.stream().map(LanguageFeatureOccurrence::fromObservation).toList();
    if (!occurrences.equals(observedOccurrences)) {
      throw new IllegalArgumentException("Language-feature observations must exactly match the source occurrence manifest");
    }
  }

  /**
   * Indicates whether a diagnostic code belongs to a diagnosed feature in this contract.
   *
   * @param diagnostic
   *          analysis diagnostic
   * @return {@code true} when the exact code is declared by a diagnosed feature
   */
  public boolean isCapabilityDiagnostic(AnalysisDiagnostic diagnostic) {
    Objects.requireNonNull(diagnostic, "diagnostic");
    return diagnosedCodes(coverageContract).contains(diagnostic.code());
  }

  private static void validateObservations(LanguageFeatureCoverage coverage, List<LanguageFeatureObservation> observations,
      List<AnalysisDiagnostic> capabilityDiagnostics) {
    for (var observation : observations) {
      validateObservation(coverage, capabilityDiagnostics, observation);
    }
  }

  private static void validateObservation(LanguageFeatureCoverage coverage, List<AnalysisDiagnostic> capabilityDiagnostics,
      LanguageFeatureObservation observation) {
    var classification = Objects.requireNonNull(observation, "observation").classification();
    var declared = coverage.classificationOf(classification.feature())
        .orElseThrow(() -> new IllegalArgumentException("Observed language feature is absent from the coverage contract"));
    if (!declared.equals(classification)) {
      throw new IllegalArgumentException("Observed language feature classification differs from the coverage contract");
    }
    observation.diagnostic().ifPresent(diagnostic -> {
      if (!capabilityDiagnostics.contains(diagnostic)) {
        throw new IllegalArgumentException("Observed capability diagnostic is absent from the coverage result");
      }
    });
  }

  private static void validateCapabilityDiagnostics(LanguageFeatureCoverage coverage, List<AnalysisDiagnostic> capabilityDiagnostics) {
    for (var diagnostic : capabilityDiagnostics) {
      var requiredDiagnostic = Objects.requireNonNull(diagnostic, "capabilityDiagnostic");
      var classifications = coverage.classifications().stream()
          .filter(classification -> classification.diagnosticCode().filter(requiredDiagnostic.code()::equals).isPresent()).toList();
      if (classifications.isEmpty()) {
        throw new IllegalArgumentException("Capability diagnostic code is absent from the coverage contract");
      }
      if (requiredDiagnostic.stage() != DiagnosticStage.FRONTEND || !requiredDiagnostic.isError()) {
        throw new IllegalArgumentException("Language feature capability diagnostics must be frontend errors");
      }
      var expectedOutcomes = classifications.stream().map(LanguageFeatureObservation::expectedOutcomeKind).distinct().toList();
      if (expectedOutcomes.size() != 1 || requiredDiagnostic.outcomeKind() != expectedOutcomes.getFirst()) {
        throw new IllegalArgumentException("Language feature capability diagnostic has an inconsistent outcome kind");
      }
    }
  }

  private static void validateAuthoritativeContract(LanguageFeatureCoverage coverage) {
    var expectedIdentity = AUTHORITATIVE_CONTRACT_IDENTITIES.get(coverage.release());
    if (expectedIdentity == null || !expectedIdentity.contains(coverage.contractIdentity())) {
      throw new IllegalArgumentException("Language-feature coverage must match the authoritative release contract");
    }
  }

  private static void validateDiagnosticOwnership(List<LanguageFeatureObservation> observations,
      List<AnalysisDiagnostic> capabilityDiagnostics) {
    for (var diagnostic : capabilityDiagnostics) {
      var observed = observations.stream().anyMatch(observation -> observation.diagnostic().filter(diagnostic::equals).isPresent());
      if (!observed) {
        throw new IllegalArgumentException("Capability diagnostic has no contextual language-feature observation");
      }
    }
  }

  private static Set<String> diagnosedCodes(LanguageFeatureCoverage coverage) {
    return coverage.classifications().stream().filter(LanguageFeatureClassification::blocksCompletion)
        .map(classification -> classification.diagnosticCode().orElseThrow()).collect(Collectors.toUnmodifiableSet());
  }
}
