package org.aquilagraph.source.api;

import java.util.ArrayList;
import java.util.function.Function;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;

/**
 * Frontend boundary input passed to semantic analysis.
 *
 * <p>
 * This boundary container is transient. Its sealed aggregate owns source occurrences and core-facing method bodies.
 *
 * @param snapshot
 *          analyzed source snapshot
 * @param environmentSnapshot
 *          captured compiler environment
 * @param program
 *          declaration-only Java program
 * @param aggregate
 *          sealed source AST, absent only when a required source root could not be published
 * @param diagnostics
 *          frontend diagnostics
 * @param languageFeatureCoverageResult
 *          release-bound contract and source-owned outcomes produced by the frontend
 */
public record SyntaxAnalysisInput(InputSnapshot snapshot, AnalysisEnvironmentSnapshot environmentSnapshot, JavaProgram program,
    Optional<JavaAstAggregate> aggregate, List<AnalysisDiagnostic> diagnostics,
    LanguageFeatureCoverageResult languageFeatureCoverageResult) {
  private static final long EXPECTED_COMPILATION_UNIT_OBSERVATIONS = 1L;
  private static final String COMPILATION_UNIT_KIND = "COMPILATION_UNIT";
  private static final String METHOD_TREE_KIND = "METHOD";
  private static final String VARIABLE_TREE_KIND = "VARIABLE";
  private static final String DEFAULT_CASE_LABEL_KIND = "DEFAULT_CASE_LABEL";
  private static final String UNIT_TYPES_PROPERTY = "CompilationUnitTree::CompilationUnitTree#getTypeDecls()";

  /** Validates and defensively copies boundary data. */
  public SyntaxAnalysisInput {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(environmentSnapshot, "environmentSnapshot");
    Objects.requireNonNull(program, "program");
    Objects.requireNonNull(aggregate, "aggregate");
    if (aggregate.isPresent() && !aggregate.orElseThrow().snapshot().equals(snapshot)) {
      throw new IllegalArgumentException("AST aggregate must belong to the analyzed input snapshot");
    }
    if (aggregate.isEmpty() && !program.types().isEmpty()) {
      throw new IllegalArgumentException("Input without a source AST cannot expose program declarations");
    }
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    Objects.requireNonNull(languageFeatureCoverageResult, "languageFeatureCoverageResult");
    if (languageFeatureCoverageResult.coverage().release() != environmentSnapshot.compilerConfiguration().release()) {
      throw new IllegalArgumentException("Language-feature coverage release must match the compiler configuration release");
    }
    validateCoverageResult(snapshot, diagnostics, languageFeatureCoverageResult);
    aggregate.ifPresent(value -> validateMethodSyntax(program, value));
    validateActualEvidence(snapshot, program, aggregate, diagnostics, languageFeatureCoverageResult);
    var retainedDiagnostics = diagnostics;
    if (aggregate.stream().flatMap(value -> value.executionScopes().stream().flatMap(scope -> value.executionSyntax(scope.key()).stream()))
        .flatMap(body -> body.evidence().diagnostics().stream()).anyMatch(diagnostic -> !retainedDiagnostics.contains(diagnostic))) {
      throw new IllegalArgumentException("Evidence diagnostics must retain an exact emitted source diagnostic");
    }
  }

  private static void validateActualEvidence(InputSnapshot snapshot, JavaProgram program, Optional<JavaAstAggregate> aggregate,
      List<AnalysisDiagnostic> diagnostics, LanguageFeatureCoverageResult coverageResult) {
    var evidence = coverageResult.validationEvidence();
    if (!evidence.writtenInventory().snapshot().equals(snapshot)) {
      throw new IllegalArgumentException("Independent coverage expectations must bind the actual input snapshot value");
    }
    var projectedDiagnostics = evidence.diagnosticEmissions().stream().map(emission -> emission.diagnostic()).toList();
    if (!diagnostics.equals(projectedDiagnostics)) {
      throw new IllegalArgumentException("Actual frontend diagnostic slots must exactly match ordered creating-site events");
    }
    var facts = CoverageActualFacts.capture(program, aggregate);
    aggregate.ifPresent(value -> CoverageActualFacts.validateDeclarationMemberOrders(program, value));
    for (var fact : facts.references()) {
      var selectedTypeUse = facts.isSource(fact,
          syntax -> syntax.syntaxKind() == SyntaxKind.TYPE_USE && syntax.sourceAssociation()
              .filter(association -> evidence.writtenInventory().contexts().stream()
                  .anyMatch(context -> context.address().partition() == SourceContext.Partition.WRITTEN_TYPE_USE
                      && context.address().sourceFileKey().equals(fact.sourceFileKey())
                      && context.address().ownerPath().equals(association.ownerPath())))
              .isPresent());
      if (selectedTypeUse && !CoverageWrittenTypeUseTopology.matchesParent(fact, facts)) {
        throw new IllegalArgumentException("Recursive written type support requires its exact actual parent role and ordinal");
      }
    }
    var accounted = new HashSet<CoverageFactReference>();
    for (var outcome : evidence.outcomes()) {
      var context = evidence.writtenInventory().contexts().stream().filter(value -> value.address().equals(outcome.address())).findFirst()
          .orElseThrow();
      var owned = actualOwner(context, evidence, facts);
      for (var reference : outcome.support().facts()) {
        accounted.add(actualReference(reference, facts));
      }
      if (outcome.support().mode() == FeatureContextOutcome.Mode.FACTS) {
        if (context.address().partition() == SourceContext.Partition.EMPTY_BODY_DECLARATION && (outcome.support().facts().size() != 1
            || !CoverageActualFacts.matchesEmptyDeclaration(context, outcome.support().facts().getFirst(), facts))) {
          throw new IllegalArgumentException("Empty declaration support requires its exact actual member occurrence and order");
        }
        if (context.address().partition() == SourceContext.Partition.EMPTY_BODY_DECLARATION) {
          var range = context.origins().getFirst();
          var source = snapshot.sourceFiles().stream().filter(file -> file.sourceFileKey().equals(range.sourceFileKey())).findFirst()
              .orElseThrow();
          var text = source.content().substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive()));
          if (!";".equals(text) && !text.matches("\\\\u+003[bB]")) {
            throw new IllegalArgumentException("An empty declaration must retain its actual written semicolon");
          }
        }
        if (context.address().partition() == SourceContext.Partition.WRITTEN_TYPE_USE
            && !writtenTypeUseSupport(context, outcome.support().facts(), evidence, facts)) {
          throw new IllegalArgumentException("Recursive written type support requires its exact actual source occurrence");
        }
        if (context.address().partition() == SourceContext.Partition.TREE
            && !treeSupport(context, outcome.support().facts(), evidence, facts)) {
          throw new IllegalArgumentException("Tree support must match the exact retained kind and captured owner topology: "
              + context.address() + " claimed " + outcome.support().facts().stream().map(CoverageFactReference::identity).toList());
        }
        if (outcome.support().facts().stream().noneMatch(owned::contains)
            && context.address().partition() != SourceContext.Partition.TREE) {
          throw new IllegalArgumentException("Claimed positive coverage support requires its exact actual captured owner");
        }
        for (var reference : outcome.support().facts()) {
          if (!owned.contains(reference)
              && outcome.encounteredValue()
                  .filter(value -> value.items().stream().anyMatch(item -> item.identity().equals(reference.identity()))).isEmpty()
              && !projectedTreeOwner(context, reference, evidence, facts) && !enclosedParameter(context, reference, outcome)) {
            throw new IllegalArgumentException("Positive coverage support cannot account for unrelated actual facts");
          }
        }
      }
      if (outcome.support().mode() == FeatureContextOutcome.Mode.NEGATIVE) {
        if (!CoverageActualFacts.supportsProperty(context, outcome.support().negativeState().orElseThrow(), owned, facts, snapshot)) {
          throw new IllegalArgumentException(
              "Claimed negative coverage support requires an actual existing owner with absent or empty accessor state");
        }
      } else if (outcome.support().mode() == FeatureContextOutcome.Mode.FACTS && outcome.encounteredValue().isPresent()
          && !CoverageActualFacts.supportsProperty(context, outcome.encounteredValue().orElseThrow(), outcome.support().facts(), facts,
              snapshot)) {
        throw new IllegalArgumentException("Claimed accessor support disagrees with the actual retained owner facts");
      }
    }
    var unmatchedOrdinals = new HashSet<Integer>();
    for (var unmatched : evidence.unmatchedFacts()) {
      var context = evidence.writtenInventory().contexts().stream().filter(value -> value.address().equals(unmatched.owner())).findFirst()
          .orElseThrow();
      if (unmatched.ordinal() >= facts.size() || !facts.get(unmatched.ordinal()).equals(unmatched.fact())
          || accounted.contains(unmatched.fact()) || !unmatchedOrdinals.add(unmatched.ordinal())
          || !capturedOwnerContains(context, unmatched.fact(), evidence, facts, snapshot)) {
        throw new IllegalArgumentException("Diagnosed unmatched facts must form an exact bijection with actual extra fact slots");
      }
    }
    for (var ordinal = 0; ordinal < facts.size(); ordinal++) {
      var fact = facts.get(ordinal);
      if (!snapshot.sourceFiles().stream().anyMatch(source -> source.sourceFileKey().equals(fact.sourceFileKey()))
          || (!accounted.contains(fact) && !unmatchedOrdinals.contains(ordinal))) {
        throw new IllegalArgumentException(
            "Every actual retained fact requires exact positive support or a diagnosed captured-owner unmatched entry");
      }
    }
  }

  private static CoverageFactReference actualReference(CoverageFactReference reference, CoverageActualFactView facts) {
    return facts.stream().filter(fact -> fact.equals(reference)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Claimed positive coverage support must match an exact actual owned fact value"));
  }

  private static boolean treeSupport(SourceContext context, List<CoverageFactReference> claimed, CoverageValidationEvidence evidence,
      CoverageActualFactView facts) {
    var kind = context.feature().orElseThrow().identifier();
    var candidates = facts.stream()
        .filter(fact -> fact.sourceFileKey().equals(context.address().sourceFileKey()) && facts.isSource(fact,
            syntax -> syntax.sourceAssociation()
                .filter(association -> association.ownerPath().equals(context.address().ownerPath())
                    && association.origin().stream().toList().equals(context.origins()))
                .isPresent()
                && (syntax.sourceKind().name().equals(kind)
                    || (List.of("ANNOTATION", "TYPE_ANNOTATION").contains(kind) && syntax.value() instanceof SyntaxValue.Annotation))))
        .toList();
    if (!candidates.isEmpty()) {
      return candidates.stream().anyMatch(claimed::contains);
    }
    if (COMPILATION_UNIT_KIND.equals(kind)) {
      return actualOwner(context, evidence, facts).stream()
          .filter(fact -> facts.isSource(fact, syntax -> syntax.value() instanceof SyntaxValue.CompilationUnit))
          .anyMatch(claimed::contains);
    }
    if (List.of("CLASS", "INTERFACE", "ENUM", "RECORD", "ANNOTATION_TYPE").contains(kind)) {
      return actualOwner(context, evidence, facts).stream()
          .filter(fact -> fact.value() instanceof CoverageFactValue.Type type
              && ("ANNOTATION".equals(type.value().kind().name()) ? "ANNOTATION_TYPE" : type.value().kind().name()).equals(kind))
          .anyMatch(claimed::contains);
    }
    if (METHOD_TREE_KIND.equals(kind)) {
      return actualOwner(context, evidence, facts).stream().filter(fact -> fact.value() instanceof CoverageFactValue.Method)
          .anyMatch(claimed::contains);
    }
    if (CoverageWrittenQualifierTopology.requiresExactOccurrence(context, evidence.writtenInventory())) {
      return false;
    }
    if (VARIABLE_TREE_KIND.equals(kind)) {
      return actualOwner(context, evidence, facts).stream().filter(fact -> fact.value() instanceof CoverageFactValue.Field
          || fact.value() instanceof CoverageFactValue.Parameter || fact.value() instanceof CoverageFactValue.RecordComponent)
          .anyMatch(claimed::contains);
    }
    return claimed.stream().allMatch(fact -> projectedTreeOwner(context, fact, evidence, facts));
  }

  private static boolean writtenTypeUseSupport(SourceContext context, List<CoverageFactReference> claimed,
      CoverageValidationEvidence evidence, CoverageActualFactView facts) {
    var tree = evidence.writtenInventory().contexts().stream()
        .filter(candidate -> candidate.address().partition() == SourceContext.Partition.TREE
            && candidate.address().sourceFileKey().equals(context.address().sourceFileKey())
            && candidate.address().ownerPath().equals(context.address().ownerPath()))
        .findFirst().orElseThrow();
    var kind = tree.feature().orElseThrow().identifier();
    return claimed.size() == 1 && facts.isSource(claimed.getFirst(),
        syntax -> syntax.syntaxKind() == SyntaxKind.TYPE_USE && syntax.sourceKind().name().equals(kind)
            && syntax.sourceAssociation().filter(association -> association.ownerPath().equals(context.address().ownerPath())
                && association.origin().stream().toList().equals(context.origins())).isPresent());
  }

  private static boolean enclosedParameter(SourceContext context, CoverageFactReference reference, FeatureContextOutcome outcome) {
    return "TypeElement::TypeElement#getEnclosedElements()".equals(context.address().role())
        && reference.kind() == CoverageFactReference.Kind.PARAMETER
        && outcome.support().facts().stream()
            .filter(
                fact -> outcome.encounteredValue().orElseThrow().items().stream().anyMatch(item -> item.identity().equals(fact.identity())))
            .map(CoverageFactReference::value).filter(CoverageFactValue.Method.class::isInstance).map(CoverageFactValue.Method.class::cast)
            .flatMap(method -> method.value().parameters().stream())
            .anyMatch(parameter -> reference.value().equals(new CoverageFactValue.Parameter(parameter)));
  }

  private static List<CoverageFactReference> actualOwner(SourceContext context, CoverageValidationEvidence evidence,
      CoverageActualFactView facts) {
    var owned = new ArrayList<>(CoverageActualFacts.ownedBy(context, facts));
    var ownerIdentity = evidence.attributedExpectations().projections().stream()
        .filter(projection -> projection.address().sourceFileKey().equals(context.address().sourceFileKey())
            && projection.address().ownerPath().equals(context.address().ownerPath()))
        .flatMap(projection -> projection.ownerIdentity().stream()).findFirst();
    ownerIdentity.ifPresent(owner -> {
      owned.removeIf(fact -> (fact.kind() == CoverageFactReference.Kind.TYPE || fact.kind() == CoverageFactReference.Kind.METHOD)
          && !fact.identity().equals(owner));
      facts.stream()
          .filter(fact -> fact.sourceFileKey().equals(context.address().sourceFileKey())
              && (fact.identity().equals(owner)
                  || (fact.value() instanceof CoverageFactValue.MemberOrder order && order.value().ownerTypeKey().equals(owner))
                  || (fact.value() instanceof CoverageFactValue.DeclarationType type && type.value().ownerTypeKey().equals(owner)))
              && !owned.contains(fact))
          .forEach(owned::add);
      if (owned.stream().anyMatch(fact -> fact.value() instanceof CoverageFactValue.Type type && !type.value().sourceDeclared())) {
        facts.stream().filter(fact -> fact.sourceFileKey().equals(context.address().sourceFileKey()) && facts.isSource(fact,
            syntax -> syntax.value() instanceof SyntaxValue.CompilationUnit unit && unit.form() == SyntaxValue.CompilationUnitForm.COMPACT)
            && !owned.contains(fact)).forEach(owned::add);
      }
    });
    if (UNIT_TYPES_PROPERTY.equals(context.address().role())) {
      facts.stream()
          .filter(fact -> fact.sourceFileKey().equals(context.address().sourceFileKey())
              && fact.value() instanceof CoverageFactValue.Type type && !type.value().sourceDeclared() && !owned.contains(fact))
          .forEach(owned::add);
    }
    return List.copyOf(owned);
  }

  private static boolean projectedTreeOwner(SourceContext context, CoverageFactReference fact, CoverageValidationEvidence evidence,
      CoverageActualFactView facts) {
    if (CoverageWrittenQualifierTopology.requiresExactOccurrence(context, evidence.writtenInventory())) {
      return false;
    }
    if (context.address().partition() == SourceContext.Partition.TREE && context.feature()
        .filter(feature -> List.of("CONSTANT_CASE_LABEL", DEFAULT_CASE_LABEL_KIND, "PATTERN_CASE_LABEL").contains(feature.identifier()))
        .isPresent()) {
      return switchLabelSupport(context, fact, evidence, facts);
    }
    if (context.address().partition() != SourceContext.Partition.TREE || context.feature()
        .filter(
            feature -> List.of("MODIFIERS", "IDENTIFIER", "MEMBER_SELECT", "PRIMITIVE_TYPE", "ARRAY_TYPE").contains(feature.identifier()))
        .isEmpty()) {
      return false;
    }
    var ancestors = facts.stream()
        .filter(actual -> actual.sourceFileKey().equals(context.address().sourceFileKey()) && facts.isSource(actual,
            syntax -> syntax.sourceAssociation().filter(association -> association.ownerPath().isAncestorOf(context.address().ownerPath()))
                .isPresent()))
        .sorted((first, second) -> Integer.compare(facts.requiredSyntax(second).sourceAssociation().orElseThrow().ownerPath().size(),
            facts.requiredSyntax(first).sourceAssociation().orElseThrow().ownerPath().size()))
        .toList();
    if (ancestors.isEmpty()) {
      return false;
    }
    var path = facts.requiredSyntax(ancestors.getFirst()).sourceAssociation().orElseThrow().ownerPath();
    return evidence.writtenInventory().contexts().stream()
        .filter(candidate -> candidate.address().partition() == SourceContext.Partition.TREE
            && candidate.address().sourceFileKey().equals(context.address().sourceFileKey())
            && candidate.address().ownerPath().equals(path))
        .findFirst().filter(candidate -> actualOwner(candidate, evidence, facts).contains(fact)).isPresent();
  }

  private static boolean switchLabelSupport(SourceContext context, CoverageFactReference fact, CoverageValidationEvidence evidence,
      CoverageActualFactView facts) {
    if (!facts.isSource(fact, syntax -> syntax.value() instanceof SyntaxValue.SwitchCase)) {
      return false;
    }
    var syntax = facts.requiredSyntax(fact);
    var value = (SyntaxValue.SwitchCase) syntax.value();
    var owner = evidence.writtenInventory().contexts().stream()
        .filter(candidate -> candidate.address().partition() == SourceContext.Partition.TREE
            && candidate.address().sourceFileKey().equals(context.address().sourceFileKey())
            && candidate.feature().filter(feature -> "CASE".equals(feature.identifier())).isPresent()
            && context.address().ownerPath().parent().filter(candidate.address().ownerPath()::equals).isPresent())
        .findFirst();
    if (owner.isEmpty() || !actualOwner(owner.orElseThrow(), evidence, facts).contains(fact)) {
      return false;
    }
    var step = context.address().ownerPath().lastStep().orElseThrow();
    if (step.role() != SourceOwnerPath.Role.LABEL) {
      return false;
    }
    var ordinal = step.ordinal();
    if (ordinal >= value.labels().size()) {
      return false;
    }
    var label = value.labels().get(ordinal);
    if (DEFAULT_CASE_LABEL_KIND.equals(context.feature().orElseThrow().identifier())) {
      return label instanceof SyntaxValue.SwitchLabel.Default;
    }
    var position = label instanceof SyntaxValue.SwitchLabel.Expression expression
        ? expression.childPosition()
        : label instanceof SyntaxValue.SwitchLabel.Pattern pattern ? pattern.childPosition() : -1;
    if ("PATTERN_CASE_LABEL".equals(context.feature().orElseThrow().identifier()) != (label instanceof SyntaxValue.SwitchLabel.Pattern)) {
      return false;
    }
    return position >= 0 && syntax.children().stream().filter(child -> child.role() == SyntaxRole.LABEL && child.ordinal() == position)
        .anyMatch(child -> facts.stream()
            .anyMatch(actual -> actual.identity().equals(child.nodeKey()) && actual.origin().stream().toList().equals(context.origins())));
  }

  private static boolean capturedOwnerContains(SourceContext owner, CoverageFactReference fact, CoverageValidationEvidence evidence,
      CoverageActualFactView facts, InputSnapshot snapshot) {
    if (owner.address().partition() != SourceContext.Partition.TREE || !owner.address().sourceFileKey().equals(fact.sourceFileKey())) {
      return false;
    }
    if (fact.origin().isEmpty() && fact.kind() != CoverageFactReference.Kind.SYNTAX) {
      return CoverageActualFacts.capturedDeclarationOwner(fact, evidence.writtenInventory(), evidence.attributedExpectations(), facts)
          .filter(context -> context.address().equals(owner.address())).isPresent();
    }
    if (facts.isSource(fact, syntax -> syntax.sourceAssociation().isPresent())) {
      var path = facts.requiredSyntax(fact).sourceAssociation().orElseThrow().ownerPath();
      var canonicalOwner = evidence.writtenInventory().contexts().stream()
          .filter(context -> context.address().partition() == SourceContext.Partition.TREE
              && context.address().sourceFileKey().equals(fact.sourceFileKey()) && context.address().ownerPath().equals(path))
          .findFirst();
      return canonicalOwner.filter(context -> context.address().equals(owner.address())).isPresent();
    }
    var canonicalOwners = evidence.writtenInventory().contexts().stream().filter(
        context -> context.address().partition() == SourceContext.Partition.TREE && actualOwner(context, evidence, facts).contains(fact))
        .toList();
    if (!canonicalOwners.isEmpty()) {
      return canonicalOwners.getFirst().address().equals(owner.address());
    }
    if (fact.origin().isEmpty()) {
      return owner.feature().filter(feature -> List.of(COMPILATION_UNIT_KIND, "CLASS", "INTERFACE", "ENUM", "RECORD", "ANNOTATION_TYPE")
          .contains(feature.identifier())).isPresent();
    }
    var span = fact.origin().orElseThrow();
    if (owner.origins().stream()
        .anyMatch(origin -> origin.startOffset() <= span.startOffset() && span.endOffsetExclusive() <= origin.endOffsetExclusive())) {
      return true;
    }
    return facts.isSource(fact,
        syntax -> syntax.value() instanceof SyntaxValue.CompilationUnit unit && unit.form() == SyntaxValue.CompilationUnitForm.COMPACT)
        && owner.feature().filter(feature -> COMPILATION_UNIT_KIND.equals(feature.identifier())).isPresent() && span.startOffset() == 0
        && snapshot.sourceFiles().stream().anyMatch(
            source -> source.sourceFileKey().equals(fact.sourceFileKey()) && source.content().length() == span.endOffsetExclusive());
  }

  /**
   * Returns the release-bound language-feature contract used for this input.
   *
   * @return immutable feature contract
   */
  public LanguageFeatureCoverage languageFeatureCoverage() {
    return languageFeatureCoverageResult.coverage();
  }

  /**
   * Returns the compiler configuration used by the frontend.
   *
   * @return compiler configuration
   */
  public CompilerConfiguration compilerConfiguration() {
    return environmentSnapshot.compilerConfiguration();
  }

  /**
   * Finds durable in-memory syntax evidence by stable method identity.
   *
   * @param methodKey
   *          method identity
   * @return matching syntax evidence when the method declares a body
   */
  public Optional<MethodSyntax> methodSyntax(String methodKey) {
    Objects.requireNonNull(methodKey, "methodKey");
    return aggregate.flatMap(value -> value.methodSyntax(methodKey));
  }

  /**
   * Returns core-facing method views in program declaration order.
   *
   * @return derived method views
   */
  public List<MethodSyntax> methodSyntax() {
    return program.methods().stream().filter(JavaMethod::hasBody).map(method -> methodSyntax(method.methodKey()).orElseThrow()).toList();
  }

  private static void validateMethodSyntax(JavaProgram program, JavaAstAggregate aggregate) {
    CanonicalMethodCorrespondence.validate(program, aggregate);
    Map<String, JavaMethod> methods = program.methods().stream()
        .collect(Collectors.toUnmodifiableMap(JavaMethod::methodKey, Function.identity()));
    var declaredBodyKeys = methods.values().stream().filter(JavaMethod::hasBody).map(JavaMethod::methodKey).collect(Collectors.toSet());
    if (!aggregate.methodBodyKeys().equals(declaredBodyKeys)) {
      throw new IllegalArgumentException("Every declared method body requires syntax evidence");
    }
    for (var type : program.types()) {
      var sourceFileKey = CoverageActualFacts.sourceFileKey(type, Optional.of(aggregate));
      for (var method : type.methods()) {
        aggregate.methodSyntax(method.methodKey()).ifPresent(body -> {
          if (!sourceFileKey.equals(body.root().sourceFileKey())) {
            throw new IllegalArgumentException("Method body source must match its canonical declaration owner: " + method.methodKey());
          }
          body.evidence().constructors().forEach(evidence -> {
            if (!method.constructor() || !method.ownerTypeKey().equals(evidence.ownerTypeKey())) {
              throw new IllegalArgumentException("Constructor evidence must belong to its actual source-program declaration");
            }
          });
        });
      }
    }
  }

  private static void validateCoverageResult(InputSnapshot snapshot, List<AnalysisDiagnostic> diagnostics,
      LanguageFeatureCoverageResult coverageResult) {
    validateObservationSources(snapshot, coverageResult);
    for (var capabilityDiagnostic : coverageResult.capabilityDiagnostics()) {
      if (!diagnostics.contains(capabilityDiagnostic)) {
        throw new IllegalArgumentException("Coverage capability diagnostics must be retained in frontend diagnostics");
      }
    }
    for (var diagnostic : diagnostics) {
      if (coverageResult.isCapabilityDiagnostic(diagnostic) && !coverageResult.capabilityDiagnostics().contains(diagnostic)) {
        throw new IllegalArgumentException("Frontend capability diagnostics must be retained in the coverage result");
      }
    }
    validateCompilationUnitObservations(snapshot, coverageResult);
  }

  private static void validateObservationSources(InputSnapshot snapshot, LanguageFeatureCoverageResult coverageResult) {
    var sourceFileKeys = new HashSet<String>();
    snapshot.sourceFiles().forEach(sourceFile -> sourceFileKeys.add(sourceFile.sourceFileKey()));
    for (var observation : coverageResult.observations()) {
      if (!sourceFileKeys.contains(observation.sourceFileKey())) {
        throw new IllegalArgumentException("Language feature observations must belong to the input snapshot");
      }
    }
  }

  private static void validateCompilationUnitObservations(InputSnapshot snapshot, LanguageFeatureCoverageResult coverageResult) {
    if (snapshot.sourceFiles().isEmpty()) {
      return;
    }
    var compilationUnitFeature = new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, COMPILATION_UNIT_KIND);
    var classification = coverageResult.coverage().classificationOf(compilationUnitFeature)
        .orElseThrow(() -> new IllegalArgumentException("Nonempty Java input requires COMPILATION_UNIT coverage"));
    for (var sourceFile : snapshot.sourceFiles()) {
      var count = coverageResult.observations().stream()
          .filter(observation -> observation.sourceFileKey().equals(sourceFile.sourceFileKey())
              && observation.classification().equals(classification))
          .count();
      if (count != EXPECTED_COMPILATION_UNIT_OBSERVATIONS) {
        throw new IllegalArgumentException("Every source file requires exactly one COMPILATION_UNIT coverage observation");
      }
    }
  }
}
