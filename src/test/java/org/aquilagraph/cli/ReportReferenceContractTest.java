package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.cli.ReportQualification.ApplicationContext;
import org.junit.jupiter.api.Test;

class ReportReferenceContractTest {
  @Test
  void givenReportLinksOutsideItsNativeCatalog_whenReportIsConstructed_thenMissingLocalReferencesAreRejected() {
    // Arrange
    var base = CliTestFixtures.report(CliTestFixtures.completedRunWithWarning());
    var invalidReferences = List.of(0, 2);
    for (var reference : invalidReferences) {
      var reason = new ReportQualification.Application(new ApplicationContext.Diagnostic(0), List.of(),
          List.of(new ReportDiagnostic.Link(reference, Optional.empty())));

      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> new CommandReport.Executed(base.analysis(), base.diagnostics(),
          List.of(reason), base.publication(), base.failures()));

      // Assert
      assertEquals("Reference is outside its report catalog", failure.getMessage());
    }
  }

  @Test
  void givenNonconsecutiveDiagnosticDefinitions_whenReportIsConstructed_thenDisplayIdentityCannotAliasAnotherSlot() {
    // Arrange
    var base = CliTestFixtures.report(CliTestFixtures.completedRunWithWarning());
    var diagnostic = base.diagnostics().getFirst();
    var renamed = new ReportDiagnostic(2, diagnostic.owner(), diagnostic.nativePosition(), diagnostic.value());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new CommandReport.Executed(base.analysis(), List.of(renamed),
        base.qualifications(), base.publication(), base.failures()));

    // Assert
    assertEquals("Diagnostic definitions must have consecutive local identities", failure.getMessage());
  }

  @Test
  void givenFailureEdgesAndDefinitionsOutsideTheirCatalog_whenCatalogIsConstructed_thenAllReferenceKindsAreValidated() {
    // Arrange
    var absent = OptionalInt.empty();
    var invalid = OptionalInt.of(2);
    var definitions = List.of(new FailureCatalog.Node(2, "type", Optional.empty(), absent, absent, absent, List.of(), Optional.empty()),
        new FailureCatalog.Node(1, "type", Optional.empty(), invalid, absent, absent, List.of(), Optional.empty()),
        new FailureCatalog.Node(1, "type", Optional.empty(), absent, invalid, absent, List.of(), Optional.empty()),
        new FailureCatalog.Node(1, "type", Optional.empty(), absent, absent, invalid, List.of(), Optional.empty()),
        new FailureCatalog.Node(1, "type", Optional.empty(), absent, absent, absent, List.of(2), Optional.empty()));

    for (var definition : definitions) {
      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> new FailureCatalog(List.of(definition)));

      // Assert
      assertEquals(definition.reference() == 2 ? "Failure definitions must have consecutive local identities"
          : "Reference is outside its report catalog", failure.getMessage());
    }
  }

  @Test
  void givenRepeatedNativeValuesAndCallerOwnedCollections_whenAliasesChange_thenReportKeepsBothEventsAndImmutableMembership() {
    // Arrange
    var original = CliTestFixtures.completedRun();
    var warning = CliTestFixtures.warningDiagnostic();
    var run = AnalysisRun.create(original.identity(), original.representation(), List.of(warning, warning));
    var base = CliTestFixtures.report(run);
    var diagnostics = new ArrayList<>(base.diagnostics());
    var qualifications = new ArrayList<>(base.qualifications());
    var nodes = new ArrayList<>(base.failures().nodes());
    var report = new CommandReport.Executed(base.analysis(), diagnostics, qualifications, base.publication(), new FailureCatalog(nodes));

    // Act
    diagnostics.clear(); qualifications.clear(); nodes.clear();
    var rendered = new CommandTextRenderer("\n").render(new CommandDocumentComposer().compose(report));

    // Assert
    assertEquals(List.of(1, 2), report.diagnostics().stream().map(ReportDiagnostic::reference).toList());
    assertEquals(List.of(0, 1), report.diagnostics().stream().map(ReportDiagnostic::nativePosition).toList());
    assertEquals(2, rendered.error().lines().filter(line -> line.equals("Diagnostic: \"" + warning.diagnosticKey() + "\"")).count());
    assertThrows(UnsupportedOperationException.class, () -> report.diagnostics().clear());
    assertThrows(UnsupportedOperationException.class, () -> report.qualifications().clear());
    assertThrows(UnsupportedOperationException.class, () -> report.analysis().methods().clear());
  }
}
