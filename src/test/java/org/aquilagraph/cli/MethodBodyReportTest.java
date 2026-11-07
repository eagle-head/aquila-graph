package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.junit.jupiter.api.Test;

class MethodBodyReportTest {
  @Test
  void givenAdmittedNonwrittenConstructorAndRecoverableFailure_whenReportIsPrepared_thenBodyProvenanceRemainsDistinctFromSourceOccurrence()
      throws IOException {
    // Arrange
    var ast = CliTestFixtures.produce("class Example { static void run() {} }");
    var source = ast.result().orElseThrow();
    var constructor = source.program().methods().stream().filter(method -> method.constructor()).findFirst().orElseThrow();
    var key = constructor.methodKey();
    var body = source.bodyRef(key).orElseThrow();
    var provenance = body.nonwrittenProvenance().orElseThrow();
    var cause = new IllegalStateException("construction observation");
    var run = AnalysisCapabilities.existingSourceDerivation(RecoverableCfgFailures.atMethod(key, cause))
        .deriveExisting(ast, new CfgRequest(source.identity(), List.of(key), CfgProfile.JAVA_BOUNDED));
    var outcome = new AnalyzeCommandOutcome.Executed(CliTestFixtures.withoutRepositoryRequest(run));

    // Act
    var report = assertInstanceOf(CommandReport.Executed.class, new CommandReportProjector(failure -> Optional.empty()).project(outcome));
    var prepared = CliTestFixtures.prepared(outcome);

    // Assert
    var reason = report.qualifications().stream().filter(ReportQualification.Failed.class::isInstance)
        .map(ReportQualification.Failed.class::cast).findFirst().orElseThrow();
    assertEquals(Optional.of(body), reason.method().body());
    assertEquals(source.identity(), reason.method().astIdentity());
    assertEquals(key, reason.method().methodKey());
    var expectedBody = List.of("  AST result: \"" + source.identity().astResultKey() + "\"", "  Body: NONWRITTEN",
        "  Body origin: \"" + provenance.origin() + "\"", "  Body source file: \"" + provenance.sourceFileKey() + "\"",
        "  Body owner declaration: \"" + provenance.ownerDeclarationKey() + "\"");
    var expectedCfg = new ArrayList<>(List.of("Qualification: CFG FAILED", "  Profile: \"java-cfg-bounded/1\"", "  Subject: \"" + key + "\""));
    expectedCfg.addAll(expectedBody);
    expectedCfg.addAll(List.of("  Failure type: \"java.lang.IllegalStateException\"", "  Failure message: \"construction observation\"",
        "  Failure reference: 1", "  Origins: unavailable", "  Related diagnostic: \"" + run.diagnostics().values().getFirst().diagnosticKey() + "\" occurrence=1"));
    assertEquals(List.of(expectedCfg), NativeReportAssertions.qualificationBlocks(prepared.error().lines().toList(), "CFG"));
    var application = NativeReportAssertions.qualificationBlocks(prepared.error().lines().toList(), "APPLICATION").getFirst();
    assertEquals(expectedBody, application.subList(3, 8));
    assertTrue(prepared.error().lines().noneMatch(line -> line.startsWith("  Source occurrence:")));
  }
}
