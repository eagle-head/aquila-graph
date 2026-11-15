package org.aquilagraph.source.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.admission.CoverageAdmission;
import org.aquilagraph.source.retention.AstCodec;




import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.function.UnaryOperator;
import org.aquilagraph.frontend.internal.JavaCoverageTestSupport;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.SourceQualificationReason;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.AttributedCoverageExpectations;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.function.Executable;

class ModuleServiceAdmissionTest {
  @TempDir Path directory;

  @Test
  void givenModuleOperandWithoutItsCopiedSourceAssociation_whenAdmittingAndDecoding_thenTheMissingContextIsExplained() {
    // Arrange
    var original = fixture(AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var operand = operands(original).getFirst();
    var copied = SourceValueCapture.capture(original.aggregate());
    var aggregate = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), copied.sources(), copied.bodies(), copied.memberOrders(),
        copied.associations().stream().filter(value -> !value.nodeKey().equals(operand.nodeId())).toList(), copied.declarationTypes(), copied.scopes()));

    // Act
    var result = admitted(original, aggregate);
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
    var reasons = decoded.qualification().reasons().stream()
        .filter(reason -> reason.role() == SourceQualificationReason.EvidenceRole.MODULE_ENDPOINT_CONTEXT).toList();

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertEquals(1, reasons.size());
    assertEquals(new SourceQualificationReason.Node(operand.nodeId(), operand.sourceFileKey()), reasons.getFirst().subject());
    assertEquals(SourceQualificationReason.Category.STRUCTURAL_COVERAGE_GAP, reasons.getFirst().category());
    assertEquals(original.aggregate().node(operand.nodeId()), decoded.aggregate().node(operand.nodeId()));
    assertEquals(result.qualification(), decoded.qualification());
  }

  @Test
  void givenConflictingCapturedModuleDescriptors_whenProducingAndDecoding_thenCompilerErrorsAndFileSpecificOperandFactsRemain() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(
        SourceFile.fromContent(capturedPath(Path.of("first/module-info.java")), "module first { uses java.lang.Runnable; }"),
        SourceFile.fromContent(capturedPath(Path.of("second/module-info.java")), "module second { uses java.lang.AutoCloseable; }")));
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var original = frontend.produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES)
        .result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertTrue(decoded.diagnostics().stream().anyMatch(diagnostic -> diagnostic.outcomeKind() == AnalysisOutcomeKind.COMPILER_ERROR));
    assertEquals(2, operands(decoded).size());
    assertEquals(snapshot.sourceFiles().stream().map(SourceFile::sourceFileKey).toList(),
        operands(decoded).stream().map(SyntaxNode::sourceFileKey).toList());
    assertEquals(original.qualification(), decoded.qualification());
  }

  @Test
  void givenResolvedModuleOperandWithoutTypedDeclarationIdentity_whenAdmittingAndDecoding_thenTheRequiredTargetRemainsMissing() {
    // Arrange
    var original = fixture(AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var operand = operands(original).getFirst();
    var selected = operand.semanticReference().orElseThrow();
    var reference = new SemanticReference(selected.referenceKind(), selected.status(), selected.symbolKey(), selected.typeName(),
        selected.typeAlternativeNames(), selected.assignableToTypeNames(), Optional.empty());
    var aggregate = copied(original, node -> node.nodeId().equals(operand.nodeId())
        ? copy(node, node.children(), Optional.of(reference), node.origin().orElseThrow()) : node, UnaryOperator.identity());

    // Act
    var result = admitted(original, aggregate);
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
    var reasons = decoded.qualification().reasons().stream()
        .filter(reason -> reason.role() == SourceQualificationReason.EvidenceRole.MODULE_SERVICE_TARGET).toList();

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertEquals(1, reasons.size());
    assertEquals(SourceQualificationReason.Category.MISSING_REQUIRED_EVIDENCE, reasons.getFirst().category());
    assertEquals(new SourceQualificationReason.Node(operand.nodeId(), operand.sourceFileKey()), reasons.getFirst().subject());
    assertEquals(selected.symbolKey(), decoded.aggregate().node(operand.nodeId()).orElseThrow().requiredReference().symbolKey());
    assertEquals(result.qualification(), decoded.qualification());
  }

  @Test
  void givenWrittenDirectivesWithoutEndpointEvidence_whenSuccessorAdmitted_thenAvailableSourceRemainsIncomplete() {
    // Arrange
    var original = fixture(AstProfile.JAVA_SOURCE_FIDELITY);
    var input = input(original, original.coverage());
    // Act
    var result = AstResultAdmission.admit(input, AstProfile.JAVA_SOURCE_MODULE_SERVICES, "javac-public/11");
    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(AstCompletion.INCOMPLETE, result.completion());
    assertEquals(original.aggregate(), result.aggregate());
  }

  @Test
  void givenResolvedOperandsWithoutIndependentEndpointInventory_whenSuccessorAdmitted_thenMissingProofCannotQualifyComplete() {
    // Arrange
    var original = fixture(AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var evidence = original.coverage().validationEvidence();
    var written = evidence.writtenInventory();
    var inventory = new SourceCoverageInventory(written.snapshot(), written.release(), written.contractIdentity(), written.contexts().stream()
        .filter(context -> context.address().partition() != SourceContext.Partition.MODULE_ENDPOINT).toList(), written.writtenExpressions());
    var attributed = new AttributedCoverageExpectations(inventory, evidence.attributedExpectations().projections().stream()
        .filter(projection -> projection.address().partition() != SourceContext.Partition.MODULE_ENDPOINT).toList());
    var coverage = CoverageAdmission.fromEvidence(original.coverage().coverage(), inventory, attributed, evidence.outcomes().stream()
        .filter(outcome -> outcome.address().partition() != SourceContext.Partition.MODULE_ENDPOINT).toList(), evidence.unmatchedFacts(), evidence.diagnosticEmissions());
    // Act
    var result = AstResultAdmission.admit(input(original, coverage), original.profile(), "javac-public/11");
    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(AstCompletion.INCOMPLETE, result.completion());
  }

  @Test
  void givenContradictoryTypedTargetAndWrongDirectiveRole_whenValuesConstructed_thenIntrinsicContradictionsAreRejected() {
    // Arrange
    var original = fixture(AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var uses = original.aggregate().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.USES).findFirst().orElseThrow();
    var target = new JavaTypeDeclarationIdentity(Optional.of("other.module"), "api.Service");
    var service = uses.requiredChild(SyntaxRole.SERVICE_TYPE);
    // Act
    var referenceFailure = assertThrows(IllegalArgumentException.class, () -> new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.RESOLVED,
        service.semanticReference().orElseThrow().symbolKey(), Optional.of("api.Service"), List.of(), List.of(), Optional.of(target)));
    var shapeFailure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(uses.sourceFileKey(), uses.structuralPath(), uses.sourceKind(),
        uses.syntaxKind(), uses.type(), uses.value(), List.of(new SyntaxChild(SyntaxRole.PROVIDER_TYPE, 0, service)), Optional.empty(), uses.origin().orElseThrow()));
    // Assert
    assertTrue(referenceFailure.getMessage().contains("declaration key"));
    assertTrue(shapeFailure.getMessage().contains("SERVICE_TYPE"), shapeFailure.toString());
  }

  @Test
  void givenRequiredEndpointMarkedNotRequired_whenCoverageAdmitted_thenQualificationBypassIsRejected() {
    // Arrange
    var original = fixture(AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var evidence = original.coverage().validationEvidence();
    var outcomes = evidence.outcomes().stream().map(outcome -> outcome.address().partition() == SourceContext.Partition.MODULE_ENDPOINT
        ? new FeatureContextOutcome(outcome.address(), outcome.occurrence(), outcome.observation(), outcome.encounteredValue(),
            new FeatureContextOutcome.Support(FeatureContextOutcome.Mode.NOT_REQUIRED, List.of(), Optional.empty(), Optional.empty(), Optional.empty()), List.of())
        : outcome).toList();
    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> CoverageAdmission.fromEvidence(original.coverage().coverage(),
        evidence.writtenInventory(), evidence.attributedExpectations(), outcomes, evidence.unmatchedFacts(), evidence.diagnosticEmissions()));
    // Assert
    assertTrue(failure.getMessage().contains("module endpoint"), failure.toString());
  }

  @Test
  void givenRetainedModuleOperands_whenCopiedTargetsAreMissingOrErroneous_thenExactWrittenChildrenRemainQueryableAndIncomplete() {
    // Arrange
    var original = fixture(AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    assertTrue(original.diagnostics().isEmpty(), original.diagnostics().toString());
    var operands = operands(original);
    var positive = admitted(original, copied(original, UnaryOperator.identity(), UnaryOperator.identity()));
    assertEquals(AstCompletion.COMPLETE, positive.completion());
    assertEquals(original.identity(), positive.identity());
    // Act
    for (var operand : operands) {
      for (var status : List.of(ResolutionStatus.UNRESOLVED, ResolutionStatus.ERROR)) {
        var reference = new SemanticReference(ReferenceKind.TYPE, status, Optional.empty(), Optional.empty(), List.of(), List.of(), Optional.empty());
        var aggregate = copied(original, node -> node.nodeId().equals(operand.nodeId())
            ? copy(node, node.children(), Optional.of(reference), node.origin().orElseThrow()) : node, UnaryOperator.identity());
        var result = admitted(original, aggregate);
        var retained = AstCodec.decode(AstCodec.encode(result), result.identity());
        // Assert
        assertEquals(AstCompletion.INCOMPLETE, result.completion());
        assertEquals(result.qualification(), retained.qualification());
        var targetReasons = retained.qualification().reasons().stream()
            .filter(reason -> reason.role() == SourceQualificationReason.EvidenceRole.MODULE_SERVICE_TARGET).toList();
        assertEquals(1, targetReasons.size());
        assertEquals(new SourceQualificationReason.Node(operand.nodeId(), operand.sourceFileKey()), targetReasons.getFirst().subject());
        assertEquals(status == ResolutionStatus.ERROR ? SourceQualificationReason.Category.ERRONEOUS_EVIDENCE
            : SourceQualificationReason.Category.MISSING_REQUIRED_EVIDENCE, targetReasons.getFirst().category());
        assertEquals(result.identity(), retained.identity());
        assertEquals(operands.size(), operands(retained).size());
        var actual = retained.aggregate().node(operand.nodeId()).orElseThrow();
        assertEquals(operand.value(), actual.value());
        assertEquals(operand.origin(), actual.origin());
        assertEquals(operand.structuralPath(), actual.structuralPath());
        assertEquals(original.aggregate().writtenSourceAssociation(operand.nodeId()), retained.aggregate().writtenSourceAssociation(operand.nodeId()));
        assertEquals(status, actual.semanticReference().orElseThrow().status());
        assertTrue(actual.semanticReference().orElseThrow().symbolKey().isEmpty());
        assertEquals(original.coverage().validationEvidence().writtenInventory(), retained.coverage().validationEvidence().writtenInventory());
        assertEquals(original.coverage().validationEvidence().attributedExpectations(), retained.coverage().validationEvidence().attributedExpectations());
        var context = endpointContext(original, operand);
        var failed = retained.coverage().validationEvidence().outcomes().stream().filter(outcome -> outcome.address().equals(context.address())).findFirst().orElseThrow();
        assertEquals(FeatureContextOutcome.Mode.FAILED, failed.support().mode());
        var support = retained.qualification().reasons().stream()
            .filter(reason -> reason.subject().equals(new SourceQualificationReason.Context(context.address()))).findFirst().orElseThrow();
        assertEquals(SourceQualificationReason.EvidenceRole.COVERAGE_SUPPORT, support.role());
        assertEquals(context.origins(), support.origins());
        assertEquals(failed.emissions(), support.diagnostics().stream().map(eventReference -> eventReference.emission().orElseThrow()).toList());
        assertTrue(support.diagnostics().stream().allMatch(eventReference -> eventReference.diagnostic().equals(retained.diagnostics().get(eventReference.position()))));
        assertTrue(retained.coverage().validationEvidence().diagnosticEmissions().stream().anyMatch(event -> event.contexts().contains(context.address())
            && event.diagnostic().code().equals("frontend.missing-source-evidence") && event.diagnostic().origins().equals(context.origins())));
        assertTrue(retained.diagnostics().stream().noneMatch(diagnostic -> diagnostic.outcomeKind() == AnalysisOutcomeKind.COMPILER_ERROR));
        for (var unchanged : operands.stream().filter(node -> !node.nodeId().equals(operand.nodeId())).toList()) {
          assertEquals(unchanged.semanticReference(), retained.aggregate().node(unchanged.nodeId()).orElseThrow().semanticReference());
        }
      }
    }
  }

  @Test
  void givenOriginalOperandExpectations_whenUsesChildIsOmitted_thenExactMissingAddressPreventsCompletionAndOtherFactsSurvive() {
    // Arrange
    var original = fixture(AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var uses = original.aggregate().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.USES).findFirst().orElseThrow();
    var missing = uses.requiredChild(SyntaxRole.SERVICE_TYPE);
    var context = endpointContext(original, missing);
    // Act
    var aggregate = copied(original, node -> node.nodeId().equals(uses.nodeId())
        ? copy(node, List.of(), node.semanticReference(), node.origin().orElseThrow()) : node, UnaryOperator.identity());
    var result = admitted(original, aggregate);
    // Assert
    assertEquals(AstCompletion.INCOMPLETE, result.completion());
    assertTrue(result.aggregate().node(missing.nodeId()).isEmpty());
    assertEquals(original.coverage().validationEvidence().writtenInventory(), result.coverage().validationEvidence().writtenInventory());
    assertEquals(original.coverage().validationEvidence().attributedExpectations(), result.coverage().validationEvidence().attributedExpectations());
    assertTrue(result.coverage().validationEvidence().outcomes().stream().anyMatch(outcome -> outcome.address().equals(context.address())
        && outcome.support().mode() == FeatureContextOutcome.Mode.FAILED));
    assertTrue(result.coverage().validationEvidence().diagnosticEmissions().stream().anyMatch(event -> event.contexts().contains(context.address())
        && event.diagnostic().origins().equals(context.origins())));
    for (var unchanged : operands(original).stream().filter(node -> !node.nodeId().equals(missing.nodeId())).toList()) {
      assertEquals(unchanged.semanticReference(), result.aggregate().node(unchanged.nodeId()).orElseThrow().semanticReference());
    }
  }

  @Test
  void givenExactUsesShape_whenDuplicateExtraWrongRoleOrForeignChildrenAreSupplied_thenIntrinsicAdmissionRejectsEachContradiction() {
    // Arrange
    var original = fixture(AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var uses = original.aggregate().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.USES).findFirst().orElseThrow();
    var service = uses.requiredChild(SyntaxRole.SERVICE_TYPE);
    var extra = SyntaxNode.source(service.sourceFileKey(), uses.structuralPath().child(SyntaxRole.SERVICE_TYPE, 1), service.sourceKind(), service.syntaxKind(), service.type(), service.value(), List.of(), service.semanticReference(), service.origin().orElseThrow());
    var wrongRole = SyntaxNode.source(service.sourceFileKey(), uses.structuralPath().child(SyntaxRole.PROVIDER_TYPE, 0), service.sourceKind(), service.syntaxKind(), service.type(), service.value(), List.of(), service.semanticReference(), service.origin().orElseThrow());
    var otherFile = original.snapshot().sourceFiles().stream().filter(source -> !source.sourceFileKey().equals(service.sourceFileKey())).findFirst().orElseThrow().sourceFileKey();
    var foreign = SyntaxNode.source(otherFile, service.structuralPath(), service.sourceKind(), service.syntaxKind(), service.type(), service.value(), List.of(), service.semanticReference(), new SourceSpan(otherFile, service.origin().orElseThrow().startOffset(), service.origin().orElseThrow().endOffsetExclusive()));
    var contrasts = List.of(
        List.of(new SyntaxChild(SyntaxRole.SERVICE_TYPE, 0, service), new SyntaxChild(SyntaxRole.SERVICE_TYPE, 0, service)),
        List.of(new SyntaxChild(SyntaxRole.SERVICE_TYPE, 0, service), new SyntaxChild(SyntaxRole.SERVICE_TYPE, 1, extra)),
        List.of(new SyntaxChild(SyntaxRole.PROVIDER_TYPE, 0, wrongRole)),
        List.of(new SyntaxChild(SyntaxRole.SERVICE_TYPE, 0, foreign)));
    // Act
    for (var children : contrasts) {
      // Assert
      assertThrows(IllegalArgumentException.class, () -> copied(original, node -> node.nodeId().equals(uses.nodeId())
          ? copy(node, children, node.semanticReference(), node.origin().orElseThrow()) : node, UnaryOperator.identity()));
    }
  }

  @Test
  void givenCapturedIndependentAssociations_whenProviderTargetsRangeOrOwnerAreContradicted_thenKnownConflictsRejectAdmission() {
    // Arrange
    var original = orderedFixture();
    var provides = original.aggregate().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.PROVIDES).findFirst().orElseThrow();
    var providers = provides.children(SyntaxRole.PROVIDER_TYPE);
    var service = original.aggregate().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.USES).findFirst().orElseThrow().requiredChild(SyntaxRole.SERVICE_TYPE);
    var otherService = provides.requiredChild(SyntaxRole.SERVICE_TYPE);
    var shifted = new SourceSpan(service.sourceFileKey(), service.origin().orElseThrow().startOffset() + 1, service.origin().orElseThrow().endOffsetExclusive());
    var otherOwner = original.aggregate().writtenSourceAssociation(otherService.nodeId()).orElseThrow().ownerPath();
    // Act
    Executable swappedTargets = () -> admitted(original, copied(original, node -> {
      if (node.nodeId().equals(providers.getFirst().nodeId())) { return copy(node, node.children(), providers.getLast().semanticReference(), node.origin().orElseThrow()); }
      if (node.nodeId().equals(providers.getLast().nodeId())) { return copy(node, node.children(), providers.getFirst().semanticReference(), node.origin().orElseThrow()); }
      return node;
    }, UnaryOperator.identity()));
    Executable shiftedOrigin = () -> admitted(original, copied(original,
        node -> node.nodeId().equals(service.nodeId()) ? copy(node, node.children(), node.semanticReference(), shifted) : node,
        association -> association.nodeKey().equals(service.nodeId()) ? new WrittenSourceAssociation(association.nodeKey(), association.sourceFileKey(), association.ownerPath(), association.sourceKind(), Optional.of(shifted), association.normalizedExpression()) : association));
    Executable wrongOwner = () -> admitted(original, copied(original, UnaryOperator.identity(),
        association -> association.nodeKey().equals(service.nodeId()) ? new WrittenSourceAssociation(association.nodeKey(), association.sourceFileKey(), otherOwner, association.sourceKind(), association.origin(), association.normalizedExpression()) : association));
    // Assert
    assertThrows(IllegalArgumentException.class, swappedTargets);
    assertThrows(IllegalArgumentException.class, shiftedOrigin);
    assertThrows(IllegalArgumentException.class, wrongOwner);
    assertEquals(AstCompletion.COMPLETE, admitted(original, copied(original, UnaryOperator.identity(), UnaryOperator.identity())).completion());
  }

  private AstResult orderedFixture() {
    return orderedFixture("");
  }

  private AstResult orderedFixture(String packagePrefix) {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("module-info.java")),
        "module service.sample { uses " + packagePrefix + "api.Service; provides " + packagePrefix + "api.Service with " + packagePrefix
            + "impl.ZuluProvider, " + packagePrefix + "impl.AlphaProvider; }"),
        SourceFile.fromContent(capturedPath(Path.of("api/Service.java")), "package " + packagePrefix + "api; public interface Service {}"),
        SourceFile.fromContent(capturedPath(Path.of("impl/ZuluProvider.java")), "package " + packagePrefix + "impl; public class ZuluProvider implements " + packagePrefix + "api.Service {}"),
        SourceFile.fromContent(capturedPath(Path.of("impl/AlphaProvider.java")), "package " + packagePrefix + "impl; public class AlphaProvider implements " + packagePrefix + "api.Service {}")));
    return new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(new SourceInput.Snapshot(snapshot),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES)).result().orElseThrow();
  }

  @Test
  void givenIndependentEndpointSlots_whenOwnerAndRangeBorrowSameKindQualifiers_thenEveryRoleRejectsTheContradiction() {
    // Arrange
    var original = orderedFixture("top.");
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(4, operands(original).size());
    for (var operand : operands(original)) {
      var endpoint = endpointContext(original, operand);
      var qualifier = original.coverage().validationEvidence().writtenInventory().contexts().stream()
          .filter(context -> context.address().partition() == SourceContext.Partition.TREE
              && context.address().sourceFileKey().equals(operand.sourceFileKey())
              && endpoint.address().ownerPath().isAncestorOf(context.address().ownerPath())
              && !endpoint.address().ownerPath().equals(context.address().ownerPath())
              && context.feature().filter(feature -> feature.identifier().equals(operand.sourceKind().name())).isPresent())
          .findFirst().orElseThrow();
      var changedOrigin = qualifier.origins().getFirst();
      var aggregate = copied(original,
          node -> node.nodeId().equals(operand.nodeId()) ? copy(node, node.children(), node.semanticReference(), changedOrigin) : node,
          association -> association.nodeKey().equals(operand.nodeId())
              ? new WrittenSourceAssociation(association.nodeKey(), association.sourceFileKey(), qualifier.address().ownerPath(), association.sourceKind(), Optional.of(changedOrigin), association.normalizedExpression())
              : association);
      // Act
      Executable changedAssociation = () -> admitted(original, aggregate);
      // Assert
      assertEquals(operand.value(), aggregate.node(operand.nodeId()).orElseThrow().value());
      assertEquals(operand.structuralPath(), aggregate.node(operand.nodeId()).orElseThrow().structuralPath());
      assertThrows(IllegalArgumentException.class, changedAssociation);
    }
    assertEquals(AstCompletion.COMPLETE, admitted(original, copied(original, UnaryOperator.identity(), UnaryOperator.identity())).completion());
  }

  private static SourceContext endpointContext(AstResult original, SyntaxNode node) {
    var owner = original.aggregate().writtenSourceAssociation(node.nodeId()).orElseThrow().ownerPath();
    return original.coverage().validationEvidence().writtenInventory().contexts().stream().filter(context -> context.address().partition() == SourceContext.Partition.MODULE_ENDPOINT && context.address().ownerPath().equals(owner)).findFirst().orElseThrow();
  }

  private static List<SyntaxNode> operands(AstResult result) {
    return result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.MODULE_TYPE_REFERENCE).toList();
  }

  private static AstResult admitted(AstResult original, JavaAstAggregate aggregate) {
    var changed = JavaCoverageTestSupport.withFacts(input(original, original.coverage()), original.program(), Optional.of(aggregate), original.coverage().validationEvidence().diagnosticEmissions());
    return AstResultAdmission.admit(changed, original.profile(), original.identity().semanticProductionRevision());
  }

  private static JavaAstAggregate copied(AstResult original, UnaryOperator<SyntaxNode> change, UnaryOperator<WrittenSourceAssociation> associationChange) {
    var values = SourceValueCapture.capture(original.aggregate());
    var sources = values.sources().stream().map(source -> {
      if (source.nodes().stream().noneMatch(node -> node.sourceKind() == JavaSourceKind.MODULE)) { return source; }
      var root = rewrite(source.root(), change);
      var nodes = new ArrayList<SyntaxNode>();
      collect(root, nodes);
      return new SyntaxSourceContribution(source.sourceFileKey(), root, nodes, nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(), nodes.stream().flatMap(node -> node.semanticEdges().stream()).toList());
    }).toList();
    var keys = sources.stream().flatMap(source -> source.nodes().stream()).map(SyntaxNode::nodeId).toList();
    var associations = values.associations().stream().filter(association -> keys.contains(association.nodeKey())).map(associationChange).toList();
    return AstValueAdmission.admit(new CopiedAstValues(values.snapshot(), sources, values.bodies(), values.memberOrders(), associations, values.declarationTypes(), values.scopes()));
  }

  private static SyntaxNode rewrite(SyntaxNode node, UnaryOperator<SyntaxNode> change) {
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), rewrite(child.node(), change))).toList();
    return change.apply(copy(node, children, node.semanticReference(), node.origin().orElseThrow()));
  }

  private static SyntaxNode copy(SyntaxNode node, List<SyntaxChild> children, Optional<SemanticReference> reference, SourceSpan origin) {
    return SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), node.value(), children, reference, origin);
  }

  private static void collect(SyntaxNode node, List<SyntaxNode> nodes) {
    nodes.add(node);
    node.children().forEach(child -> collect(child.node(), nodes));
  }

  private AstResult fixture(AstProfile profile) {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("module-info.java")),
        "module service.sample { uses api.Service; provides api.Service with impl.Provider; }"),
        SourceFile.fromContent(capturedPath(Path.of("api/Service.java")), "package api; public interface Service {}"),
        SourceFile.fromContent(capturedPath(Path.of("impl/Provider.java")), "package impl; public class Provider implements api.Service {}")));
    return new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(new SourceInput.Snapshot(snapshot),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile)).result().orElseThrow();
  }

  private static SyntaxAnalysisInput input(AstResult original, LanguageFeatureCoverageResult coverage) {
    return new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(original.aggregate()), original.diagnostics(), coverage);
  }
}
