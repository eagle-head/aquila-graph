package org.aquilagraph.frontend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AttributedCoverageExpectations;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoverageFactValue;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.CoveragePropertyValue.Item;
import org.aquilagraph.source.api.CoveragePropertyValue.Provenance;
import org.aquilagraph.source.api.CoveragePropertyValue.State;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.FeatureContextOutcome.Mode;
import org.aquilagraph.source.api.FeatureContextOutcome.Support;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;
import org.aquilagraph.source.api.LanguageFeaturePolicy;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceContext.Partition;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.UnmatchedSourceFact;
import org.aquilagraph.source.api.CoverageActualFacts;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.CoverageActualFactView;
import org.aquilagraph.source.api.CoverageWrittenTypeUseTopology;
import org.aquilagraph.source.api.CoverageWrittenQualifierTopology;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxKind;

/** Compares independent expectation stages, independently emitted pairs, and actual retained facts in both directions. */
final class JavaSourceCoverageReconciler {
  private static final String MISSING_SOURCE_EVIDENCE = "frontend.missing-source-evidence";
  private static final int EXPECTED_WRITTEN_TYPE_USE_FACTS = 1;
  private static final String UNIT_KIND_PROPERTY = "CompilationUnitTree::Tree#getKind()";
  private static final String UNIT_SOURCE_PROPERTY = "CompilationUnitTree::CompilationUnitTree#getSourceFile()";
  private static final String UNIT_TYPES_PROPERTY = "CompilationUnitTree::CompilationUnitTree#getTypeDecls()";
  private static final String TYPE_MEMBERS_PROPERTY = "TypeElement::TypeElement#getEnclosedElements()";
  private final SourceCoverageInventory inventory;
  private final AttributedCoverageExpectations attributed;
  private final CoverageActualFactView actualFacts;
  private final List<DiagnosticEmission> events;
  private final Map<DiagnosticIdentity, DiagnosticEmission.Address> policyEvents = new HashMap<>();
  private final Map<DiagnosticEmission.Address, List<SourceContext.Address>> associations = new HashMap<>();
  private final List<FeatureContextOutcome> outcomes = new ArrayList<>();
  private final List<UnmatchedSourceFact> unmatched = new ArrayList<>();

  JavaSourceCoverageReconciler(SourceCoverageInventory inventory, AttributedCoverageExpectations attributed, JavaProgram program,
      Optional<JavaAstAggregate> aggregate, List<DiagnosticEmission> emissions) {
    this.inventory = inventory;
    this.attributed = attributed;
    actualFacts = CoverageActualFacts.capture(program, aggregate);
    events = new ArrayList<>(emissions);
    for (var event : events) {
      registerEvent(event);
    }
  }

  private void registerEvent(DiagnosticEmission event) {
    if (policyEvents.put(identity(event.diagnostic()), event.address()) != null) {
      throw new IllegalArgumentException("One diagnostic instance cannot be reminted as a second emission");
    }
    associations.put(event.address(), new ArrayList<>(event.contexts()));
  }

  ReconciledCoverage reconcile(LanguageFeatureCoverage coverage, ObservedLanguageFeatures trees,
      JavaTypeKindObserver.ObservedTypeKinds types, TranslationResult<TranslatedProgram> translation,
      ObservedPropertyEncounters writtenProperties, ObservedPropertyEncounters attributedProperties) {
    var treeContexts = inventory.contexts().stream().filter(context -> context.address().partition() == Partition.TREE).toList();
    if (treeContexts.size() != trees.observations().size() || trees.occurrences().size() != trees.observations().size()) {
      throw new IllegalArgumentException("Actual tree encounters must exactly cover the frozen written traversal");
    }
    for (var index = 0; index < treeContexts.size(); index++) {
      var context = treeContexts.get(index);
      var observation = trees.observations().get(index);
      if (!observation.classification().feature().equals(context.feature().orElseThrow())
          || !observation.sourceFileKey().equals(context.address().sourceFileKey()) || !observation.origins().equals(context.origins())) {
        throw new IllegalArgumentException("Actual tree encounter order, kind or source differs from the frozen traversal");
      }
      addKnown(context, trees.occurrences().get(index), observation, Optional.empty());
    }
    var typeIndex = 0;
    for (var context : inventory.contexts().stream().filter(value -> value.address().partition() == Partition.TYPE).toList()) {
      var expected = projection(context);
      if (expected.state() == State.UNAVAILABLE) {
        var addresses = failure(context, "frontend.unresolved-attributed-type-kind", unavailableTypeMessage(context));
        outcomes.add(new FeatureContextOutcome(context.address(), Optional.empty(), Optional.empty(), Optional.of(expected),
            support(Mode.FAILED), addresses));
      } else {
        if (typeIndex >= types.observations().size()) {
          throw new IllegalArgumentException("Actual attributed-kind encounters omitted a captured written header slot");
        }
        var observation = types.observations().get(typeIndex);
        var actual = typeKindValue(observation.classification().feature().identifier());
        var pattern = CoverageActualFacts.MATCH_ALL_TYPE_ROLE.equals(context.address().role());
        var expectedKind = pattern && expected.items().getFirst().typeIdentity().isPresent()
            ? typeKindValue(expected.items().getFirst().typeIdentity().orElseThrow().category() == JavaTypeCategory.TYPE_VARIABLE
                ? "TYPEVAR"
                : expected.items().getFirst().typeIdentity().orElseThrow().category().name())
            : expected;
        if (!actual.equals(expectedKind) || !observation.sourceFileKey().equals(context.address().sourceFileKey())) {
          throw new IllegalArgumentException("Actual attributed kind differs from the independently queried header slot");
        }
        addKnown(context, types.occurrences().get(typeIndex), observation, Optional.of(pattern ? expected : actual));
        typeIndex++;
      }
    }
    if (typeIndex != types.observations().size() || !translation.featureOccurrences().isEmpty()
        || !translation.featureObservations().isEmpty()) {
      throw new IllegalArgumentException("Unexpected policy encounters have no captured source context");
    }
    writtenTypeUses();
    properties(Partition.WRITTEN_PROPERTY, writtenProperties);
    properties(Partition.ATTRIBUTED_PROPERTY, attributedProperties);
    return finish(coverage);
  }

  ReconciledCoverage reconcileRetained(LanguageFeatureCoverage coverage, List<FeatureContextOutcome> actualEncounters) {
    if (!inventory.contexts().stream().map(SourceContext::address).toList()
        .equals(actualEncounters.stream().map(FeatureContextOutcome::address).toList())) {
      throw new IllegalArgumentException("Retained encounters must exactly cover the independent context inventory");
    }
    for (var index = 0; index < actualEncounters.size(); index++) {
      var context = inventory.contexts().get(index);
      var encounter = actualEncounters.get(index);
      if (encounter.observation().isPresent()) {
        addKnown(context, encounter.occurrence().orElseThrow(), encounter.observation().orElseThrow(), encounter.encounteredValue());
      } else if (context.address().partition() == Partition.WRITTEN_TYPE_USE) {
        writtenTypeUse(context);
      } else {
        var addresses = failure(context, "frontend.unresolved-attributed-type-kind", unavailableTypeMessage(context));
        outcomes.add(new FeatureContextOutcome(context.address(), Optional.empty(), Optional.empty(), encounter.encounteredValue(),
            support(Mode.FAILED), addresses));
      }
    }
    return finish(coverage);
  }

  private void writtenTypeUses() {
    inventory.contexts().stream().filter(context -> context.address().partition() == Partition.WRITTEN_TYPE_USE)
        .forEach(this::writtenTypeUse);
  }

  private void writtenTypeUse(SourceContext context) {
    var tree = inventory.contexts().stream()
        .filter(candidate -> candidate.address().partition() == Partition.TREE
            && candidate.address().sourceFileKey().equals(context.address().sourceFileKey())
            && candidate.address().ownerPath().equals(context.address().ownerPath()))
        .findFirst().orElseThrow();
    var kind = tree.feature().orElseThrow().identifier();
    var facts = actualFacts.stream()
        .filter(fact -> fact.sourceFileKey().equals(context.address().sourceFileKey())
            && CoverageWrittenTypeUseTopology.matchesParent(fact, actualFacts)
            && actualFacts.isSource(fact,
                syntax -> syntax.syntaxKind() == SyntaxKind.TYPE_USE && syntax.sourceKind().name().equals(kind)
                    && syntax.sourceAssociation().filter(association -> association.ownerPath().equals(context.address().ownerPath())
                        && association.origin().stream().toList().equals(context.origins())).isPresent()))
        .toList();
    var emissions = List.<DiagnosticEmission.Address>of();
    var disposition = support(Mode.FAILED);
    if (facts.size() == EXPECTED_WRITTEN_TYPE_USE_FACTS) {
      disposition = new Support(Mode.FACTS, facts, Optional.empty(), Optional.empty(), Optional.empty());
    } else {
      emissions = failure(context, MISSING_SOURCE_EVIDENCE, "Required recursive written type syntax is unavailable at its exact owner");
    }
    outcomes
        .add(new FeatureContextOutcome(context.address(), Optional.empty(), Optional.empty(), Optional.empty(), disposition, emissions));
  }

  private ReconciledCoverage finish(LanguageFeatureCoverage coverage) {
    reconcileLeftovers();
    var retained = new ArrayList<DiagnosticEmission>();
    for (var index = 0; index < events.size(); index++) {
      var event = events.get(index);
      retained.add(finalEvent(event, index));
    }
    var result = LanguageFeatureCoverageResult.fromEvidence(coverage, inventory, attributed, outcomes, unmatched, retained);
    return new ReconciledCoverage(result, retained.stream().map(DiagnosticEmission::diagnostic).toList());
  }

  private void properties(Partition partition, ObservedPropertyEncounters evidence) {
    var contexts = inventory.contexts().stream().filter(context -> context.address().partition() == partition).toList();
    if (!contexts.stream().map(SourceContext::address).toList()
        .equals(evidence.encounters().stream().map(ObservedPropertyEncounters.Encounter::address).toList())) {
      throw new IllegalArgumentException(
          "Actual property encounters must independently cover every ordered captured owner/accessor exactly once");
    }
    for (var index = 0; index < contexts.size(); index++) {
      var encounter = evidence.encounters().get(index);
      addKnown(contexts.get(index), encounter.occurrence(), encounter.observation(), Optional.of(encounter.value()));
    }
  }

  private void addKnown(SourceContext context, LanguageFeatureOccurrence occurrence, LanguageFeatureObservation observation,
      Optional<CoveragePropertyValue> encountered) {
    var addresses = new ArrayList<DiagnosticEmission.Address>();
    observation.diagnostic().ifPresent(diagnostic -> {
      var address = policyEvents.get(identity(diagnostic));
      if (address == null) {
        throw new IllegalArgumentException("Known policy diagnostics require creating-site event transport");
      }
      associate(address, context.address());
      addresses.add(address);
    });
    var disposition = support(Mode.NOT_REQUIRED);
    if (observation.classification().policy() == LanguageFeaturePolicy.REPRESENTED) {
      if (context.address().partition() == Partition.TYPE) {
        if (CoverageActualFacts.MATCH_ALL_TYPE_ROLE.equals(context.address().role())) {
          var facts = ownerFacts(context).stream()
              .filter(fact -> actualFacts.isSource(fact, syntax -> syntax.syntaxKind() == SyntaxKind.ANY_PATTERN)).toList();
          if (CoverageActualFacts.supportsPatternType(context, projection(context), facts)) {
            disposition = new Support(Mode.FACTS, facts, Optional.empty(), Optional.empty(), Optional.empty());
          } else {
            disposition = support(Mode.FAILED);
            addresses.addAll(
                failure(context, MISSING_SOURCE_EVIDENCE, "Required match-all component attribution is unavailable at its exact owner"));
          }
        } else {
          disposition = support(Mode.ATTRIBUTED_PAIR);
        }
      } else if (UNIT_KIND_PROPERTY.equals(context.address().role())) {
        var tree = inventory.contexts().stream()
            .filter(candidate -> candidate.address().partition() == Partition.TREE
                && candidate.address().sourceFileKey().equals(context.address().sourceFileKey())
                && candidate.address().ownerPath().equals(context.address().ownerPath()))
            .findFirst().orElseThrow();
        disposition = new Support(Mode.POLICY_PAIR, List.of(), Optional.of(tree.address()), Optional.empty(), Optional.empty());
      } else if (UNIT_SOURCE_PROPERTY.equals(context.address().role())) {
        var source = inventory.snapshot().sourceFiles().stream()
            .filter(file -> file.sourceFileKey().equals(context.address().sourceFileKey())).findFirst().orElseThrow();
        disposition = new Support(Mode.SOURCE_FILE, List.of(), Optional.empty(), Optional.of(source), Optional.empty());
      } else {
        var owned = ownerFacts(context);
        var expected = encountered.isPresent() ? Optional.of(projection(context)) : Optional.<CoveragePropertyValue>empty();
        var supported = !owned.isEmpty() && expected.map(value -> value.equals(encountered.orElseThrow())
            && CoverageActualFacts.supportsProperty(context, value, owned, actualFacts, inventory.snapshot())).orElse(true);
        if (!supported) {
          disposition = support(Mode.FAILED);
          addresses.addAll(failure(context,
              expected.filter(value -> value.state() == State.UNAVAILABLE).isPresent()
                  ? "frontend.inconsistent-source-evidence"
                  : MISSING_SOURCE_EVIDENCE,
              "Required represented source evidence is unavailable at the captured owner"));
        } else if (expected.filter(value -> value.state() == State.ABSENT || value.state() == State.EMPTY).isPresent()) {
          disposition = new Support(Mode.NEGATIVE, List.of(), Optional.empty(), Optional.empty(), expected);
        } else {
          disposition = new Support(Mode.FACTS, owned, Optional.empty(), Optional.empty(), Optional.empty());
        }
      }
    }
    outcomes.add(new FeatureContextOutcome(context.address(), Optional.of(occurrence), Optional.of(observation), encountered, disposition,
        addresses));
  }

  private CoveragePropertyValue projection(SourceContext context) {
    return context.requiresAttribution()
        ? attributed.projections().stream().filter(value -> value.address().equals(context.address()))
            .map(AttributedCoverageExpectations.Projection::value).findFirst().orElseThrow()
        : context.writtenValue().orElseThrow();
  }

  private List<CoverageFactReference> ownerFacts(SourceContext context) {
    var owned = new ArrayList<>(CoverageActualFacts.ownedBy(context, actualFacts));
    var ownerIdentity = attributed.projections().stream()
        .filter(value -> value.address().partition() == Partition.ATTRIBUTED_PROPERTY
            && value.address().sourceFileKey().equals(context.address().sourceFileKey())
            && value.address().ownerPath().equals(context.address().ownerPath()))
        .flatMap(value -> value.ownerIdentity().stream()).findFirst();
    if (ownerIdentity.isPresent()) {
      actualFacts.stream()
          .filter(
              fact -> fact.sourceFileKey().equals(context.address().sourceFileKey()) && (fact.identity().equals(ownerIdentity.orElseThrow())
                  || (fact.value() instanceof CoverageFactValue.MemberOrder order
                      && order.value().ownerTypeKey().equals(ownerIdentity.orElseThrow()))
                  || (fact.value() instanceof CoverageFactValue.DeclarationType type
                      && type.value().ownerTypeKey().equals(ownerIdentity.orElseThrow())))
                  && !owned.contains(fact))
          .forEach(owned::add);
      if (owned.stream().anyMatch(fact -> fact.value() instanceof CoverageFactValue.Type type && !type.value().sourceDeclared())) {
        actualFacts.stream().filter(fact -> fact.sourceFileKey().equals(context.address().sourceFileKey()) && actualFacts.isSource(fact,
            syntax -> syntax.value() instanceof SyntaxValue.CompilationUnit unit && unit.form() == SyntaxValue.CompilationUnitForm.COMPACT)
            && !owned.contains(fact)).forEach(owned::add);
      }
      owned.removeIf(fact -> (fact.kind() == CoverageFactReference.Kind.TYPE || fact.kind() == CoverageFactReference.Kind.METHOD)
          ? !fact.identity().equals(ownerIdentity.orElseThrow())
          : actualFacts.isSource(fact,
              syntax -> (syntax.value() instanceof SyntaxValue.TypeDeclaration type
                  && !type.declarationKey().equals(ownerIdentity.orElseThrow()))
                  || (syntax.value() instanceof SyntaxValue.ExecutableDeclaration method
                      && !method.declarationKey().equals(ownerIdentity.orElseThrow()))));
    }
    if (UNIT_TYPES_PROPERTY.equals(context.address().role())) {
      actualFacts.stream()
          .filter(fact -> fact.sourceFileKey().equals(context.address().sourceFileKey())
              && fact.value() instanceof CoverageFactValue.Type type && !type.value().sourceDeclared() && !owned.contains(fact))
          .forEach(owned::add);
    }
    if (context.address().partition() == Partition.TREE && context.feature().isPresent()) {
      var kind = context.feature().orElseThrow().identifier();
      var matching = owned.stream()
          .filter(fact -> actualFacts.sourceValue(fact)
              .map(syntax -> syntax.sourceKind().name().equals(kind)
                  || (List.of("ANNOTATION", "TYPE_ANNOTATION").contains(kind) && syntax.value() instanceof SyntaxValue.Annotation))
              .orElseGet(() -> canonicalTreeKind(kind, fact)))
          .toList();
      if (!matching.isEmpty()) {
        return matching;
      }
      if (CoverageWrittenQualifierTopology.requiresExactOccurrence(context, inventory)) {
        return List.of();
      }
      if (List.of("CONSTANT_CASE_LABEL", "DEFAULT_CASE_LABEL").contains(kind)) {
        return inventory.contexts().stream()
            .filter(candidate -> candidate.address().partition() == Partition.TREE
                && candidate.address().sourceFileKey().equals(context.address().sourceFileKey())
                && ancestor(candidate.address().ownerPath(), context.address().ownerPath())
                && candidate.feature().filter(feature -> "CASE".equals(feature.identifier())).isPresent())
            .sorted((first, second) -> Integer.compare(second.address().ownerPath().size(), first.address().ownerPath().size()))
            .map(candidate -> CoverageActualFacts.ownedBy(candidate, actualFacts).stream()
                .filter(fact -> actualFacts.isSource(fact, syntax -> syntax.value() instanceof SyntaxValue.SwitchCase)).toList())
            .filter(facts -> !facts.isEmpty()).findFirst().orElseGet(List::of);
      }
      if (!List.of("MODIFIERS", "IDENTIFIER", "MEMBER_SELECT", "PRIMITIVE_TYPE", "ARRAY_TYPE").contains(kind)) {
        return List.of();
      }
    } else if (!owned.isEmpty()) {
      var expected = context.requiresAttribution() ? projection(context) : context.writtenValue().orElseThrow();
      expected.items().forEach(item -> actualFacts.stream().filter(fact -> fact.sourceFileKey().equals(context.address().sourceFileKey())
          && fact.identity().equals(item.identity()) && !owned.contains(fact)).forEach(owned::add));
      if (TYPE_MEMBERS_PROPERTY.equals(context.address().role())) {
        var parameterKeys = owned.stream().map(CoverageFactReference::value).filter(CoverageFactValue.Method.class::isInstance)
            .map(CoverageFactValue.Method.class::cast).flatMap(method -> method.value().parameters().stream())
            .map(parameter -> parameter.parameterKey()).toList();
        actualFacts.stream().filter(fact -> parameterKeys.contains(fact.identity()) && !owned.contains(fact)).forEach(owned::add);
      }
      return List.copyOf(owned);
    }
    return inventory.contexts().stream()
        .filter(candidate -> candidate.address().partition() == Partition.TREE
            && candidate.address().sourceFileKey().equals(context.address().sourceFileKey())
            && ancestor(candidate.address().ownerPath(), context.address().ownerPath()))
        .sorted((first, second) -> Integer.compare(second.address().ownerPath().size(), first.address().ownerPath().size()))
        .map(candidate -> CoverageActualFacts.ownedBy(candidate, actualFacts)).filter(facts -> !facts.isEmpty()).findFirst()
        .orElseGet(List::of);
  }

  private static boolean canonicalTreeKind(String kind, CoverageFactReference fact) {
    return ("METHOD".equals(kind) && fact.value() instanceof CoverageFactValue.Method)
        || ("VARIABLE".equals(kind) && (fact.value() instanceof CoverageFactValue.Field
            || fact.value() instanceof CoverageFactValue.Parameter || fact.value() instanceof CoverageFactValue.RecordComponent))
        || (fact.value() instanceof CoverageFactValue.Type type
            && ("ANNOTATION".equals(type.value().kind().name()) ? "ANNOTATION_TYPE" : type.value().kind().name()).equals(kind));
  }

  private void reconcileLeftovers() {
    var accounted = new HashSet<CoverageFactReference>();
    outcomes.forEach(outcome -> accounted.addAll(outcome.support().facts()));
    for (var ordinal = 0; ordinal < actualFacts.size(); ordinal++) {
      var fact = actualFacts.get(ordinal);
      if (accounted.contains(fact)) {
        continue;
      }
      var owner = unmatchedOwner(fact);
      var emission = failure(owner, "frontend.unexpected-source-evidence",
          "An actual retained fact has no represented expected support slot").getFirst();
      unmatched.add(extraFact(owner, fact, ordinal, emission));
    }
  }

  private SourceContext unmatchedOwner(CoverageFactReference fact) {
    if (fact.origin().isEmpty() && fact.kind() != CoverageFactReference.Kind.SYNTAX) {
      return CoverageActualFacts.capturedDeclarationOwner(fact, inventory, attributed, actualFacts)
          .orElseThrow(() -> new IllegalArgumentException("Originless declaration facts require their exact captured declaring owner"));
    }
    var exact = inventory.contexts().stream()
        .filter(context -> context.address().partition() == Partition.TREE && context.address().sourceFileKey().equals(fact.sourceFileKey())
            && (fact.kind() == CoverageFactReference.Kind.SYNTAX
                ? actualFacts.isSource(fact,
                    syntax -> syntax.sourceAssociation()
                        .filter(association -> association.ownerPath().equals(context.address().ownerPath())).isPresent())
                : ownerFacts(context).contains(fact)))
        .findFirst();
    return exact.orElseGet(() -> inventory.contexts().stream()
        .filter(context -> context.address().partition() == Partition.TREE && context.address().sourceFileKey().equals(fact.sourceFileKey())
            && (fact.origin().filter(span -> context.origins().stream().anyMatch(
                ownerSpan -> span.startOffset() >= ownerSpan.startOffset() && span.endOffsetExclusive() <= ownerSpan.endOffsetExclusive()))
                .isPresent()
                || (fact.origin().isEmpty() && context.feature()
                    .filter(feature -> List.of("CLASS", "INTERFACE", "ENUM", "RECORD", "ANNOTATION_TYPE", "COMPILATION_UNIT")
                        .contains(feature.identifier()))
                    .isPresent())))
        .findFirst().orElseThrow(() -> new IllegalArgumentException("Actual retained facts require an independently captured source owner: "
            + fact.kind() + ' ' + fact.identity() + ' ' + fact.origin())));
  }

  private static UnmatchedSourceFact extraFact(SourceContext owner, CoverageFactReference fact, int ordinal,
      DiagnosticEmission.Address emission) {
    return new UnmatchedSourceFact(owner.address(), fact, ordinal, UnmatchedSourceFact.Reason.EXTRA, emission);
  }

  private List<DiagnosticEmission.Address> failure(SourceContext context, String code, String message) {
    if (MISSING_SOURCE_EVIDENCE.equals(code) || "frontend.inconsistent-source-evidence".equals(code)) {
      var origins = context.origins().isEmpty()
          ? inventory.contexts().stream()
              .filter(owner -> owner.address().partition() == Partition.TREE
                  && owner.address().sourceFileKey().equals(context.address().sourceFileKey())
                  && ancestor(owner.address().ownerPath(), context.address().ownerPath()) && !owner.origins().isEmpty())
              .sorted((first, second) -> Integer.compare(second.address().ownerPath().size(), first.address().ownerPath().size()))
              .map(SourceContext::origins).findFirst().orElseGet(List::of)
          : context.origins();
      var enclosingOrigins = inventory.contexts().stream()
          .filter(owner -> owner.address().partition() == Partition.TREE
              && owner.address().sourceFileKey().equals(context.address().sourceFileKey())
              && ancestor(owner.address().ownerPath(), context.address().ownerPath()))
          .flatMap(owner -> owner.origins().stream()).toList();
      var declarationOwners = actualFacts.stream().filter(fact -> fact.kind() != CoverageFactReference.Kind.SYNTAX
          && fact.sourceFileKey().equals(context.address().sourceFileKey()) && fact.origin().filter(enclosingOrigins::contains).isPresent())
          .map(CoverageFactReference::identity).toList();
      var existing = events.stream()
          .filter(
              event -> !"JavaSourceCoverageReconciler".equals(event.address().producer()) && event.diagnostic().isError()
                  && (declarationOwners.stream().anyMatch(owner -> event.address().owner().equals(owner + ":field")
                      || event.address().owner().equals(owner + ":declaration") || event.address().owner().equals(owner + ":parameters"))
                      || event.diagnostic().origins().stream()
                          .anyMatch(diagnosticOrigin -> origins.stream()
                              .anyMatch(owner -> diagnosticOrigin.sourceFileKey().equals(owner.sourceFileKey())
                                  && ((diagnosticOrigin.startOffset() >= owner.startOffset()
                                      && diagnosticOrigin.endOffsetExclusive() <= owner.endOffsetExclusive())
                                      || (owner.startOffset() >= diagnosticOrigin.startOffset()
                                          && owner.endOffsetExclusive() <= diagnosticOrigin.endOffsetExclusive()))))))
          .findFirst();
      if (existing.isPresent()) {
        var address = existing.orElseThrow().address();
        associate(address, context.address());
        return List.of(address);
      }
    }
    var diagnostic = FrontendDiagnosticFactory.otherError(context.address().toString(), code, message,
        context.origins().stream().findFirst());
    var address = new DiagnosticEmission.Address("JavaSourceCoverageReconciler", context.address().toString(), events.size());
    events.add(new DiagnosticEmission(address, diagnostic, diagnostic.outcomeKind(), List.of(context.address()), -1));
    associations.put(address, new ArrayList<>(List.of(context.address())));
    return List.of(address);
  }

  private void associate(DiagnosticEmission.Address event, SourceContext.Address context) {
    var owners = Objects.requireNonNull(associations.get(event), "creating-event context associations");
    if (!owners.contains(context)) {
      owners.add(context);
    }
  }

  private static boolean ancestor(SourceOwnerPath candidate, SourceOwnerPath path) {
    return candidate.isAncestorOf(path);
  }

  private static Support support(Mode mode) {
    return new Support(mode, List.of(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  private DiagnosticEmission finalEvent(DiagnosticEmission event, int finalSlot) {
    return new DiagnosticEmission(event.address(), event.diagnostic(), event.cause(),
        Objects.requireNonNull(associations.get(event.address()), "creating-event context associations"), finalSlot);
  }

  private static CoveragePropertyValue typeKindValue(String kind) {
    return new CoveragePropertyValue(State.SCALAR, List.of(new Item("type-kind", kind, Provenance.WRITTEN)));
  }

  private static String unavailableTypeMessage(SourceContext context) {
    return CoverageActualFacts.MATCH_ALL_TYPE_ROLE.equals(context.address().role())
        ? "A required match-all component type has no compiler attribution"
        : "A written declaration-header type has no compiler attribution";
  }

  private static DiagnosticIdentity identity(AnalysisDiagnostic diagnostic) {
    return new DiagnosticIdentity(diagnostic);
  }

  private record DiagnosticIdentity(AnalysisDiagnostic diagnostic) {
    @Override
    public boolean equals(Object other) {
      return other instanceof DiagnosticIdentity identity && JavaProjectionIdentity.sameInstance(diagnostic, identity.diagnostic());
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(diagnostic);
    }
  }

  record ReconciledCoverage(LanguageFeatureCoverageResult coverageResult, List<AnalysisDiagnostic> diagnostics) {
    ReconciledCoverage {
      diagnostics = List.copyOf(diagnostics);
    }
  }
}
