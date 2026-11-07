package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.cli.ReportDiagnostic.Link;
import org.aquilagraph.cli.ReportDocument.Section;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.SourceQualificationReason.EvidenceRole;
import org.junit.jupiter.api.Test;

class SourceLinkReportTest {
  @Test
  void givenSourceReportLinksWithPresentAbsentAndRepeatedAddresses_whenComposed_thenAddressesRemainIndependentOfNativeAndDisplayPositions() {
    // Arrange
    var run = CliTestFixtures.analyze("interface Example { static int run() { return missing; } }");
    var report = CliTestFixtures.report(run);
    var original = report.qualifications().stream().filter(ReportQualification.Source.class::isInstance)
        .map(ReportQualification.Source.class::cast).filter(reason -> reason.role() == EvidenceRole.DIAGNOSTIC).findFirst().orElseThrow();
    var reference = original.links().getFirst().reference();
    var event = report.diagnostics().get(reference - 1);
    var suppliedAddress = new DiagnosticEmission.Address("producer\n\"", "owner\\slot", 7);
    var suppliedLinks = List.of(new Link(reference, Optional.of(suppliedAddress)), new Link(reference, Optional.empty()),
        new Link(reference, Optional.of(suppliedAddress)));
    var reason = new ReportQualification.Source(original.category(), original.role(), original.profile(), original.subject(),
        original.origins(), suppliedLinks, original.evidence());
    var document = new ReportDocument(new Section(List.of()), new Section(List.of(
        new QualificationDocumentComposer().compose(reason, report.diagnostics(), report.failures()))));

    // Act
    var prepared = new CommandTextRenderer("\n").render(document);

    // Assert
    assertInstanceOf(ReportQualification.Subject.Capture.class, reason.subject());
    assertInstanceOf(ReportDiagnostic.Owner.Source.class, event.owner());
    assertEquals(reference - 1, event.nativePosition());
    assertEquals(List.of(Optional.of(suppliedAddress), Optional.empty(), Optional.of(suppliedAddress)),
        reason.links().stream().map(Link::emission).toList());
    assertEquals(List.of(reference, reference, reference), reason.links().stream().map(Link::reference).toList());
    var expected = new ArrayList<>(List.of("Qualification: SOURCE COMPILER_ERROR", "  Profile: \"java-source-bounded/1\"",
        "  Role: DIAGNOSTIC", "  Subject: unavailable", "  Subject kind: CAPTURE"));
    var sourceReason = run.representation().ast().qualification().reasons().stream()
        .filter(value -> value.role() == EvidenceRole.DIAGNOSTIC).findFirst().orElseThrow();
    sourceReason.origins().forEach(origin -> expected.add("  Origin: \"" + origin.sourceFileKey() + "\" file=\"Example.java\" range=["
        + origin.startOffset() + "," + origin.endOffsetExclusive() + ")"));
    var related = "  Related diagnostic: \"" + run.diagnostics().values().get(reference - 1).diagnosticKey() + "\" occurrence=" + reference;
    expected.addAll(List.of(related, "    Emission producer: \"producer\\n\\\"\"", "    Emission owner: \"owner\\\\slot\"", "    Emission ordinal: 7",
        related, "    Emission: unavailable", related, "    Emission producer: \"producer\\n\\\"\"", "    Emission owner: \"owner\\\\slot\"", "    Emission ordinal: 7"));
    assertEquals(String.join("\n", expected) + "\n", prepared.error());
    assertEquals("", prepared.output());
  }
}
