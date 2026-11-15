package org.aquilagraph.source.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.frontend.internal.JavaCoverageTestSupport;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.NormalizedExpressionCorrespondence;
import org.aquilagraph.source.api.SourceQualificationReason;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.api.WrittenExpressionRegion;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class SourceQualificationNormalizationTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenMissingCopiedNormalizationFacts_whenAdmittingAndDecoding_thenConstantEvidenceCannotBeQualifiedComplete() {
    return Stream.of(false, true).map(knownStatus -> DynamicTest.dynamicTest(
        "given" + (knownStatus ? "KnownConstantWithoutValue" : "UnknownConstantStatus")
            + "_whenAdmittingAndDecoding_thenTheMissingEvidenceIsExplained", () -> {
          // Arrange
          var source = "@interface Marker { String value(); } @Marker(\"left\" + \"right\""
              + (knownStatus ? " + Subject.TOKEN" : "")
              + ") class Subject { static final String TOKEN = \"suffix\"; }";
          var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Subject.java")), source)));
          var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
              AstProfile.JAVA_SOURCE_MODULE_SERVICES).result().orElseThrow();
          var copied = SourceValueCapture.capture(original.aggregate());
          var eligible = copied.associations().stream().flatMap(value -> value.normalizedExpression().stream())
              .filter(value -> !knownStatus || value.region().occurrences().stream().anyMatch(item -> item.compilerOperand().isPresent()))
              .map(NormalizedExpressionCorrespondence::region).distinct().toList();
          assertFalse(eligible.isEmpty(), "The source must exercise actual normalization before copied evidence is removed");
          var associations = copied.associations().stream().map(association -> {
            var normalized = association.normalizedExpression();
            if (normalized.isEmpty() || !eligible.contains(normalized.orElseThrow().region())) {
              return association;
            }
            var value = normalized.orElseThrow();
            var region = knownStatus ? value.region() : new WrittenExpressionRegion(value.region().compilerOwner(), value.region().origin(),
                value.region().occurrences().stream().map(item -> new WrittenExpressionRegion.Occurrence(item.path(), item.sourceKind(),
                    item.origin(), item.compilerOperand(), item.restrictedDecimal(), Optional.empty())).toList());
            var observations = value.observations().stream().map(item -> new NormalizedExpressionCorrespondence.Observation(item.ownerPath(),
                item.sourceKind(), item.origin(), item.type(), !knownStatus || item.ownerPath().equals(region.compilerOwner())
                    || item.sourceKind() == JavaSourceKind.IDENTIFIER || item.sourceKind() == JavaSourceKind.MEMBER_SELECT
                        ? Optional.empty() : item.constant(),
                knownStatus ? item.constantExpression() : Optional.empty())).toList();
            var revised = new NormalizedExpressionCorrespondence(region, value.occurrencePath(), value.compilerAnchor(), value.compilerKind(),
                value.compilerType(), !knownStatus || value.occurrencePath().isEmpty() ? Optional.empty() : value.derivedConstant(),
                knownStatus ? value.derivedConstantExpression() : Optional.empty(), observations);
            return new WrittenSourceAssociation(association.nodeKey(), association.sourceFileKey(), association.ownerPath(),
                association.sourceKind(), association.origin(), Optional.of(revised));
          }).toList();
          var aggregate = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), copied.sources(), copied.bodies(), copied.memberOrders(),
              associations, copied.declarationTypes(), copied.scopes()));
          var before = original.coverage().validationEvidence().writtenInventory();
          var regions = before.writtenExpressions().map(values -> values.stream().map(region -> associations.stream()
              .flatMap(value -> value.normalizedExpression().stream()).map(NormalizedExpressionCorrespondence::region)
              .filter(value -> value.compilerOwner().equals(region.compilerOwner()) && value.origin().equals(region.origin()))
              .findFirst().orElse(region)).toList());
          var inventory = new SourceCoverageInventory(before.snapshot(), before.release(), before.contractIdentity(), before.contexts(), regions);
          var input = JavaCoverageTestSupport.withFacts(SourceValueCapture.capture(original), original.program(), Optional.of(aggregate),
              original.coverage().validationEvidence().diagnosticEmissions(), inventory);

          // Act
          var result = AstResultAdmission.admit(input, original.profile(), original.identity().semanticProductionRevision());
          var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
          var reasons = decoded.qualification().reasons().stream()
              .filter(reason -> reason.role() == SourceQualificationReason.EvidenceRole.CONSTANT_EXPRESSION).toList();

          // Assert
          assertEquals(AstCompletion.COMPLETE, original.completion());
          assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
          assertEquals(knownStatus ? 1 : 3, reasons.size());
          assertTrue(reasons.stream().allMatch(reason -> reason.category() == SourceQualificationReason.Category.MISSING_REQUIRED_EVIDENCE));
          assertEquals(result.qualification(), decoded.qualification());
        }));
  }
}
