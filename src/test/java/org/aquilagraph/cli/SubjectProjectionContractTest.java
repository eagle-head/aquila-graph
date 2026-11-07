package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.cli.ReportDocument.Section;
import org.aquilagraph.source.admission.CoverageActualFacts;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.SourceQualificationReason;
import org.aquilagraph.source.api.SourceQualificationReason.Category;
import org.aquilagraph.source.api.SourceQualificationReason.EvidenceRole;
import org.junit.jupiter.api.Test;

class SubjectProjectionContractTest {
  @Test
  void givenActualCompilerFactReferencesWithAndWithoutOrigins_whenDescriptorsAreProjectedAndComposed_thenCompleteMetadataSurvivesWithoutPayloadCopies()
      throws IOException {
    // Arrange
    var source = CliTestFixtures.produce("class Example { int value() { return 1; } }").result().orElseThrow();
    var facts = CoverageActualFacts.capture(source.program(), Optional.of(source.aggregate()));
    var present = facts.stream().filter(fact -> fact.origin().isPresent()).findFirst().orElseThrow();
    var absent = facts.stream().filter(fact -> fact.origin().isEmpty()).findFirst().orElseThrow();
    var projector = new SourceSubjectProjector();
    for (var fact : List.of(present, absent)) {
      var original = new SourceQualificationReason.Fact(fact);

      // Act
      var subject = assertInstanceOf(ReportQualification.Subject.Fact.class, projector.project(original));
      var prepared = render(subject);

      // Assert
      assertNotSame(original, subject);
      assertEquals(fact.kind(), subject.kind());
      assertEquals(fact.identity(), subject.identity());
      assertEquals(fact.sourceFileKey(), subject.sourceFileKey());
      assertEquals(fact.origin(), subject.origin());
      var expected = new ArrayList<>(List.of("Qualification: SOURCE STRUCTURAL_COVERAGE_GAP", "  Profile: \"java-source-bounded/1\"",
          "  Role: UNMATCHED_FACT", "  Subject: \"" + fact.identity() + "\"", "  Subject kind: FACT", "  Subject fact kind: " + fact.kind(),
          "  Subject source file: \"" + fact.sourceFileKey() + "\""));
      expected.add("  Subject fact origin: " + fact.origin().map(span -> "\"" + span.sourceFileKey() + "\" range=[" + span.startOffset()
          + "," + span.endOffsetExclusive() + ")").orElse("unavailable"));
      expected.add("  Origins: unavailable");
      assertEquals(String.join("\n", expected) + "\n", prepared.error());
      assertEquals("", prepared.output());
    }
  }

  @Test
  void givenRootContextAddressWithRawRole_whenDescriptorIsProjectedAndComposed_thenEmptyOwnerPathAndQuotedComponentsStayExplicit() {
    // Arrange
    var address = new SourceContext.Address("unavailable", SourceContext.Partition.TREE, SourceOwnerPath.root(), "role\n\"", 3);

    // Act
    var subject = new SourceSubjectProjector().project(new SourceQualificationReason.Context(address));
    var reason = new ReportQualification.Source(Category.STRUCTURAL_COVERAGE_GAP, EvidenceRole.COVERAGE_SUPPORT,
        AstProfile.JAVA_SOURCE_BOUNDED, subject, List.of(), List.of(), List.of());
    var group = new QualificationDocumentComposer().compose(reason, List.of(), new FailureCatalog(List.of()));
    var prepared = new CommandTextRenderer("\n").render(new ReportDocument(new Section(List.of()), new Section(List.of(group))));

    // Assert
    assertEquals(address, assertInstanceOf(ReportQualification.Subject.Context.class, subject).address());
    assertEquals("Qualification: SOURCE STRUCTURAL_COVERAGE_GAP\n  Profile: \"java-source-bounded/1\"\n  Role: COVERAGE_SUPPORT\n"
        + "  Subject: \"unavailable/TREE//role\\n\\\"/3\"\n  Subject kind: CONTEXT\n  Subject source file: \"unavailable\"\n"
        + "  Subject partition: TREE\n  Subject owner path: ROOT\n  Subject role: \"role\\n\\\"\"\n  Subject ordinal: 3\n  Origins: unavailable\n",
        prepared.error());
  }

  private static PreparedCommandOutput render(ReportQualification.Subject.Fact subject) {
    var reason = new ReportQualification.Source(Category.STRUCTURAL_COVERAGE_GAP, EvidenceRole.UNMATCHED_FACT,
        AstProfile.JAVA_SOURCE_BOUNDED, subject, List.of(), List.of(), List.of());
    var group = new QualificationDocumentComposer().compose(reason, List.of(), new FailureCatalog(List.of()));
    return new CommandTextRenderer("\n").render(new ReportDocument(new Section(List.of()), new Section(List.of(group))));
  }
}
