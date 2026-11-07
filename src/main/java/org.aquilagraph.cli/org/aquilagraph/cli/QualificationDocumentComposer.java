package org.aquilagraph.cli;

import static org.aquilagraph.cli.ReportDocument.number;
import static org.aquilagraph.cli.ReportDocument.sequence;
import static org.aquilagraph.cli.ReportDocument.text;
import static org.aquilagraph.cli.ReportDocument.token;

import java.util.ArrayList;
import java.util.List;
import org.aquilagraph.application.AnalysisRequestIdentity.AvailableSource;
import org.aquilagraph.cli.AnalysisRunSummary.MethodDescriptor;
import org.aquilagraph.cli.ReportDocument.Absence;
import org.aquilagraph.cli.ReportDocument.Decimal;
import org.aquilagraph.cli.ReportDocument.Field;
import org.aquilagraph.cli.ReportDocument.Group;
import org.aquilagraph.cli.ReportDocument.Literal;
import org.aquilagraph.cli.ReportDocument.Node;
import org.aquilagraph.cli.ReportDocument.Part;
import org.aquilagraph.cli.ReportDocument.QuotedComposite;
import org.aquilagraph.cli.ReportDocument.Text;
import org.aquilagraph.cli.ReportDocument.Value;
import org.aquilagraph.cli.ReportQualification.ApplicationContext;
import org.aquilagraph.cli.ReportQualification.Subject;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.SourceContext;

/** Arranges typed qualification forms; no original run or outcome is available here. */
final class QualificationDocumentComposer {
  private static final String PROFILE_FIELD = "Profile";
  private static final String SUBJECT_FIELD = "Subject";
  private static final String SUBJECT_KIND_FIELD = "Subject kind";
  private final DiagnosticDocumentComposer diagnostics = new DiagnosticDocumentComposer();

  Group compose(ReportQualification reason, List<ReportDiagnostic> catalog, FailureCatalog failures) {
    var children = new ArrayList<Node>();
    final Header header;
    switch (reason) {
      case ReportQualification.Source source -> {
        header = new Header("SOURCE", source.category().name());
        children.add(new Field(PROFILE_FIELD, text(source.profile().version())));
        children.add(new Field("Role", token(source.role().name())));
        children.add(new Field(SUBJECT_FIELD, subject(source.subject())));
        subjectFields(source.subject(), children);
        children.add(DiagnosticDocumentComposer.origins(source.origins()));
        children.add(DiagnosticDocumentComposer.links(source.links(), catalog, true));
        source.evidence().forEach(evidence -> children.add(diagnostics.evidence(evidence)));
      }
      case ReportQualification.Request request -> {
        header = new Header("CFG", "REJECTED");
        children.add(new Field(PROFILE_FIELD, text(request.request().profile().version())));
        children.add(new Field(SUBJECT_FIELD, text(request.request().astIdentity().astResultKey())));
        children.add(new Field("AST result", text(request.request().astIdentity().astResultKey())));
        children.add(DiagnosticDocumentComposer.origins(request.origins()));
        children.add(DiagnosticDocumentComposer.links(request.links(), catalog, false));
      }
      case ReportQualification.Failed failed -> {
        header = new Header("CFG", "FAILED");
        methodHeader(failed.profile().version(), failed.method(), children);
        var failure = failures.node(failed.failureReference());
        children.add(new Field("Failure type", text(failure.type())));
        children.add(new Field("Failure message", failure.message().map(ReportDocument::text).orElse(Absence.UNAVAILABLE)));
        children.add(new Field("Failure reference", number(failed.failureReference())));
        children.add(DiagnosticDocumentComposer.origins(failed.origins()));
        children.add(DiagnosticDocumentComposer.links(failed.links(), catalog, false));
      }
      case ReportQualification.Blocker blocker -> {
        header = new Header("CFG", blocker.kind().name());
        methodHeader(blocker.profile().version(), blocker.method(), children);
        children.add(new Field("Source occurrence", blocker.occurrence().map(value -> text(value.nodeKey())).orElse(Absence.UNAVAILABLE)));
        children.add(new Field("Slot", text(blocker.slot())));
        children.add(DiagnosticDocumentComposer.origins(blocker.origins()));
        children.add(DiagnosticDocumentComposer.links(blocker.links(), catalog, false));
        blocker.evidence().ifPresent(evidence -> children.add(diagnostics.evidence(evidence)));
      }
      case ReportQualification.Application application -> {
        header = new Header("APPLICATION", application(application.context(), children));
        children.add(DiagnosticDocumentComposer.origins(application.origins()));
        children.add(DiagnosticDocumentComposer.links(application.links(), catalog, false));
      }
    }
    return new Group(new Field("Qualification", sequence(token(header.owner()), token(" "), token(header.category()))), children);
  }

  private static String application(ApplicationContext context, List<Node> children) {
    return switch (context) {
      case ApplicationContext.Source source -> {
        children.add(new Field(PROFILE_FIELD, text(source.profile().version())));
        children.add(new Field(SUBJECT_FIELD,
            source.subject() instanceof AvailableSource available ? text(available.identity().astResultKey()) : Absence.UNAVAILABLE));
        yield "SOURCE";
      }
      case ApplicationContext.Selection selection -> {
        children.add(new Field(PROFILE_FIELD, text(selection.request().profile().version())));
        children.add(new Field(SUBJECT_FIELD, text(selection.request().astIdentity().astResultKey())));
        yield "SELECTION";
      }
      case ApplicationContext.Method method -> {
        methodHeader(method.profile().version(), method.method(), children);
        yield method.diagnosticPosition().isPresent() ? "METHOD_DIAGNOSTIC" : "METHOD";
      }
      case ApplicationContext.Diagnostic _ -> {
        children.add(new Field(PROFILE_FIELD, Absence.NOT_APPLICABLE));
        children.add(new Field(SUBJECT_FIELD, Absence.UNAVAILABLE));
        yield "APPLICATION_DIAGNOSTIC";
      }
    };
  }

  private static void methodHeader(String profile, MethodDescriptor method, List<Node> children) {
    children.add(new Field(PROFILE_FIELD, text(profile)));
    children.add(new Field(SUBJECT_FIELD, text(method.methodKey())));
    children.add(new Field("AST result", text(method.astIdentity().astResultKey())));
    if (method.body().isEmpty()) {
      children.add(new Field("Body", Absence.UNAVAILABLE));
    } else {
      var body = method.body().orElseThrow();
      if (body.sourceOccurrenceKey().isPresent()) {
        children.add(new Field("Body", token("WRITTEN")));
        children.add(new Field("Body occurrence", text(body.sourceOccurrenceKey().orElseThrow())));
      } else {
        var provenance = body.nonwrittenProvenance().orElseThrow();
        children.add(new Field("Body", token("NONWRITTEN")));
        children.add(new Field("Body origin", text(provenance.origin())));
        children.add(new Field("Body source file", text(provenance.sourceFileKey())));
        children.add(new Field("Body owner declaration", text(provenance.ownerDeclarationKey())));
      }
    }
  }

  private static Value subject(Subject subject) {
    return switch (subject) {
      case Subject.Capture _ -> Absence.UNAVAILABLE;
      case Subject.Node node -> text(node.nodeKey());
      case Subject.Declaration declaration -> text(declaration.key());
      case Subject.Fact fact -> text(fact.identity());
      case Subject.Context context -> context(context.address());
    };
  }

  private static Value context(SourceContext.Address address) {
    var parts = new ArrayList<Part>();
    parts.add(new Text(address.sourceFileKey()));
    parts.add(new Literal("/"));
    parts.add(new Literal(address.partition().name()));
    parts.add(new Literal("/"));
    var steps = address.ownerPath().steps();
    for (var index = 0; index < steps.size(); index++) {
      if (index != 0) {
        parts.add(new Literal("/"));
      }
      var step = steps.get(index);
      parts.add(new Literal(step.role().name()));
      parts.add(new Literal("/"));
      parts.add(new Decimal(step.ordinal()));
    }
    parts.add(new Literal("/"));
    parts.add(new Text(address.role()));
    parts.add(new Literal("/"));
    parts.add(new Decimal(address.ordinal()));
    return new QuotedComposite(parts);
  }

  private static void subjectFields(Subject subject, List<Node> children) {
    switch (subject) {
      case Subject.Capture _ -> children.add(new Field(SUBJECT_KIND_FIELD, token("CAPTURE")));
      case Subject.Node node -> {
        children.add(new Field(SUBJECT_KIND_FIELD, token("NODE")));
        children.add(new Field("Subject source file", text(node.sourceFileKey())));
      }
      case Subject.Declaration _ -> children.add(new Field(SUBJECT_KIND_FIELD, token("DECLARATION")));
      case Subject.Context context -> {
        children.add(new Field(SUBJECT_KIND_FIELD, token("CONTEXT")));
        var address = context.address();
        children.add(new Field("Subject source file", text(address.sourceFileKey())));
        children.add(new Field("Subject partition", token(address.partition().name())));
        if (address.ownerPath().steps().isEmpty()) {
          children.add(new Field("Subject owner path", token("ROOT")));
        }
        address.ownerPath().steps().forEach(
            step -> children.add(new Field("Subject owner step", sequence(token(step.role().name()), token(" "), number(step.ordinal())))));
        children.add(new Field("Subject role", text(address.role())));
        children.add(new Field("Subject ordinal", number(address.ordinal())));
      }
      case Subject.Fact fact -> {
        children.add(new Field(SUBJECT_KIND_FIELD, token("FACT")));
        children.add(new Field("Subject fact kind", token(fact.kind().name())));
        children.add(new Field("Subject source file", text(fact.sourceFileKey())));
        children.add(
            new Field("Subject fact origin", fact.origin().map(QualificationDocumentComposer::factOrigin).orElse(Absence.UNAVAILABLE)));
      }
    }
  }

  private static Value factOrigin(SourceSpan span) {
    return sequence(text(span.sourceFileKey()), token(" range=["), number(span.startOffset()), token(","),
        number(span.endOffsetExclusive()), token(")"));
  }

  private record Header(String owner, String category) {
  }
}
