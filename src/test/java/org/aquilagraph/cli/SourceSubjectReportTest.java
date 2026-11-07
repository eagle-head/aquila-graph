package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.frontend.internal.JavaCoverageTestSupport;
import org.aquilagraph.source.admission.AstProductionAdmission;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.SourceQualificationReason;
import org.aquilagraph.source.internal.SourceValueCapture;
import org.junit.jupiter.api.Test;

class SourceSubjectReportTest {
  @Test
  void givenReconciledCopiedMemberContradiction_whenActualSourceOutcomeIsProjected_thenContextsAndFactIdentitiesRemainDetachedAndQualified()
      throws IOException {
    // Arrange
    var original = CliTestFixtures.produce("class First {} class Second { int first; void value() {} int second; }").result().orElseThrow();
    var copied = SourceValueCapture.capture(original.aggregate());
    var orders = copied.memberOrders().stream().map(order -> order.ownerTypeKey().equals("type:Second")
        ? new DeclarationMemberOrder(order.sourceFileKey(), order.ownerTypeKey(), order.members().stream().map(member ->
            member.declarationKey().equals("type:Second#field:first")
                ? new DeclarationMemberOrder.Member(member.declarationKey(), member.kind(), "FIELD:altered", member.provenance(), member.writtenOrdinal())
                : member).toList()) : order).toList();
    var aggregate = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), copied.sources(), copied.bodies(), orders,
        copied.associations(), copied.declarationTypes()));
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(original.aggregate()),
        original.diagnostics(), original.coverage());
    var reconciled = JavaCoverageTestSupport.withFacts(input, input.program(), Optional.of(aggregate), JavaCoverageTestSupport.emissions(input));
    var ast = AstProductionAdmission.outcome(reconciled, original.profile(), original.identity().semanticProductionRevision());
    var run = CliTestFixtures.analyze(ast);

    // Act
    var report = CliTestFixtures.report(run);
    var prepared = CliTestFixtures.prepared(new AnalyzeCommandOutcome.Executed(CliTestFixtures.withoutRepositoryRequest(run)));

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, ast.completion());
    assertEquals(ast.completion(), report.analysis().sourceState());
    var subjects = report.qualifications().stream().filter(ReportQualification.Source.class::isInstance)
        .map(ReportQualification.Source.class::cast).map(ReportQualification.Source::subject).toList();
    var facts = subjects.stream().filter(ReportQualification.Subject.Fact.class::isInstance)
        .map(ReportQualification.Subject.Fact.class::cast).toList();
    var contexts = subjects.stream().filter(ReportQualification.Subject.Context.class::isInstance)
        .map(ReportQualification.Subject.Context.class::cast).toList();
    assertFalse(facts.isEmpty());
    assertFalse(contexts.isEmpty());
    var nativeFacts = ast.qualification().reasons().stream()
        .filter(reason -> reason.subject() instanceof SourceQualificationReason.Fact)
        .map(reason -> ((SourceQualificationReason.Fact) reason.subject()).reference()).toList();
    assertEquals(nativeFacts.stream().map(fact -> fact.identity()).toList(), facts.stream().map(ReportQualification.Subject.Fact::identity).toList());
    assertEquals(nativeFacts.stream().map(fact -> fact.kind()).toList(), facts.stream().map(ReportQualification.Subject.Fact::kind).toList());
    assertEquals(nativeFacts.stream().map(fact -> fact.sourceFileKey()).toList(), facts.stream().map(ReportQualification.Subject.Fact::sourceFileKey).toList());
    assertEquals(nativeFacts.stream().map(fact -> fact.origin()).toList(), facts.stream().map(ReportQualification.Subject.Fact::origin).toList());
    assertTrue(facts.stream().anyMatch(fact -> fact.origin().isEmpty()));
    assertTrue(prepared.error().lines().anyMatch(line -> line.equals("  Subject kind: FACT")));
    assertTrue(prepared.error().lines().anyMatch(line -> line.equals("  Subject fact origin: unavailable")));
    assertTrue(prepared.error().lines().anyMatch(line -> line.startsWith("  Subject owner step: TYPE_DECLARATION 1")));
    assertTrue(contexts.stream().allMatch(context -> !context.address().ownerPath().steps().isEmpty()));
  }

}
