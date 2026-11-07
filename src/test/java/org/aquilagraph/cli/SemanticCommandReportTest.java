package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnalysisInputException;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.SourceQualificationReason;
import org.aquilagraph.source.api.SourceQualificationReason.EvidenceRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SemanticCommandReportTest {
  @TempDir Path directory;

  @Test
  void givenIndependentRestrictionsAtOneLocal_whenRealCommandPreparesItsReport_thenBothRolesAndExactNativeSubjectsRemainDistinct()
      throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "interface Example { static int run() { final var value = 1; return value; } }");
    var service = CliTestFixtures.service(CliTestFixtures.runner(), CliTestFixtures.repositoriesMustNotOpen());
    var executions = new AtomicInteger();
    var command = new AnalyzeCommand(request -> { executions.incrementAndGet(); return service.execute(request); });

    // Act
    var outcome = command.execute(new String[] {directory.toString()});
    var executed = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, outcome);
    var report = assertInstanceOf(CommandReport.Executed.class, new CommandReportProjector(failure -> Optional.empty()).project(outcome));
    var prepared = command.prepare(outcome);
    var repeated = command.prepare(outcome);

    // Assert
    assertEquals(1, executions.get());
    assertEquals(prepared, repeated);
    assertEquals(AstCompletion.INCOMPLETE, report.analysis().sourceState());
    var reasons = report.qualifications().stream().filter(ReportQualification.Source.class::isInstance)
        .map(ReportQualification.Source.class::cast).toList();
    assertEquals(List.of(EvidenceRole.LOCAL_MODIFIERS, EvidenceRole.LOCAL_TYPE_FORM), reasons.stream().map(ReportQualification.Source::role).toList());
    assertEquals(reasons.getFirst().subject(), reasons.getLast().subject());
    assertEquals(reasons.getFirst().origins(), reasons.getLast().origins());
    var ast = executed.execution().run().representation().ast();
    var originalReasons = ast.qualification().reasons();
    var expected = new ArrayList<String>();
    for (var position = 0; position < originalReasons.size(); position++) {
      var reason = originalReasons.get(position);
      var node = assertInstanceOf(SourceQualificationReason.Node.class, reason.subject());
      var role = position == 0 ? "LOCAL_MODIFIERS" : "LOCAL_TYPE_FORM";
      expected.addAll(List.of("Qualification: SOURCE PROFILE_RESTRICTION", "  Profile: \"java-source-bounded/1\"", "  Role: " + role,
          "  Subject: \"" + node.nodeKey() + "\"", "  Subject kind: NODE", "  Subject source file: \"" + node.sourceFileKey() + "\""));
      reason.origins().forEach(origin -> expected.add(expectedOrigin(origin)));
    }
    // Native diagnostic blocks and complete application causes are checked separately by the full report contracts.
    assertEquals(List.of("LOCAL_MODIFIERS", "LOCAL_TYPE_FORM"),
        prepared.error().lines().filter(line -> line.startsWith("  Role: ")).map(line -> line.substring(8)).toList());
    assertEquals(expected,
        NativeReportAssertions.qualificationBlocks(prepared.error().lines().toList(), "SOURCE").stream().flatMap(List::stream).toList());
  }

  @Test
  void givenNamedInputFailureWithCauseAndOrderedCleanup_whenCommandExecutesAndPrepares_thenOriginalAndImmutableGraphRemainSeparate() {
    // Arrange
    var cause = new IOException("input cause");
    var first = new IllegalStateException("first cleanup");
    var second = new IllegalStateException("second cleanup");
    var failure = new AnalysisInputException("source \"description\"\nnext", cause);
    failure.addSuppressed(first);
    failure.addSuppressed(second);
    var calls = new AtomicInteger();
    var command = new AnalyzeCommand(request -> { calls.incrementAndGet(); throw failure; });

    // Act
    var outcome = assertInstanceOf(AnalyzeCommandOutcome.InputRejected.class, command.execute(new String[] {"source"}));
    var report = assertInstanceOf(CommandReport.Failed.class, new CommandReportProjector(ignored -> Optional.empty()).project(outcome));
    var rendered = new CommandTextRenderer("\n").render(new CommandDocumentComposer().compose(report));
    failure.addSuppressed(new IllegalStateException("later cleanup"));
    var repeated = new CommandTextRenderer("\n").render(new CommandDocumentComposer().compose(report));

    // Assert
    assertEquals(1, calls.get());
    assertSame(failure, outcome.failure());
    assertSame(cause, outcome.failure().getCause());
    assertEquals(List.of(first, second, failure.getSuppressed()[2]), List.of(failure.getSuppressed()));
    assertEquals(rendered, repeated);
    assertEquals("", rendered.output());
    var expected = new ArrayList<>(List.of("Input rejected: \"source \\\"description\\\"\\nnext\"", "Failure reference: 1"));
    expected.addAll(failureBlock(1, "org.aquilagraph.source.api.AnalysisInputException", "\"source \\\"description\\\"\\nnext\"", "2",
        List.of("  Suppressed: 3", "  Suppressed: 4")));
    expected.addAll(failureBlock(2, "java.io.IOException", "\"input cause\"", "unavailable", List.of("  Suppressed: none")));
    expected.addAll(failureBlock(3, "java.lang.IllegalStateException", "\"first cleanup\"", "unavailable", List.of("  Suppressed: none")));
    expected.addAll(failureBlock(4, "java.lang.IllegalStateException", "\"second cleanup\"", "unavailable", List.of("  Suppressed: none")));
    assertEquals(String.join("\n", expected) + "\n", rendered.error());
    assertEquals(List.of(3, 4), report.failures().node(1).suppressed());
  }

  private static List<String> failureBlock(int reference, String type, String message, String cause, List<String> suppressed) {
    var result = new ArrayList<>(List.of("Failure: " + reference, "  Type: \"" + type + "\"", "  Message: " + message,
        "  Cause: " + cause, "  Carrier: not-applicable", "  Effective primary: not-applicable"));
    result.addAll(suppressed);
    result.add("  Operation: not-applicable");
    return result;
  }

  private static String expectedOrigin(SourceSpan origin) {
    return "  Origin: \"" + origin.sourceFileKey() + "\" file=\"Example.java\" range=[" + origin.startOffset() + "," + origin.endOffsetExclusive() + ")";
  }
}
