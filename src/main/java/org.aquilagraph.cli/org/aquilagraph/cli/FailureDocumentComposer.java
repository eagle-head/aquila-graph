package org.aquilagraph.cli;

import static org.aquilagraph.cli.ReportDocument.number;
import static org.aquilagraph.cli.ReportDocument.text;
import static org.aquilagraph.cli.ReportDocument.token;

import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalInt;
import org.aquilagraph.cli.ReportDocument.Absence;
import org.aquilagraph.cli.ReportDocument.Field;
import org.aquilagraph.cli.ReportDocument.Group;
import org.aquilagraph.cli.ReportDocument.Node;
import org.aquilagraph.cli.ReportDocument.Section;
import org.aquilagraph.cli.ReportDocument.Value;

/** Composes each immutable failure definition once in local identity order. */
final class FailureDocumentComposer {
  Section compose(FailureCatalog catalog) {
    return new Section(catalog.nodes().stream().<Node>map(this::failure).toList());
  }

  private Group failure(FailureCatalog.Node failure) {
    var children = new ArrayList<Node>();
    children.add(new Field("Type", text(failure.type())));
    children.add(new Field("Message", failure.message().map(ReportDocument::text).orElse(Absence.UNAVAILABLE)));
    children.add(new Field("Cause", reference(failure.cause(), Absence.UNAVAILABLE)));
    children.add(new Field("Carrier", reference(failure.carrier(), Absence.NOT_APPLICABLE)));
    children.add(new Field("Effective primary", reference(failure.effectivePrimary(), Absence.NOT_APPLICABLE)));
    if (failure.suppressed().isEmpty()) {
      children.add(new Field("Suppressed", token("none")));
    } else {
      failure.suppressed().forEach(reference -> children.add(new Field("Suppressed", number(reference))));
    }
    children.add(new Field("Operation",
        failure.operation().map(operation -> token(operation.context().kind().name())).orElse(Absence.NOT_APPLICABLE)));
    failure.operation().ifPresent(operation -> {
      PublicationDocumentComposer.subject("Operation", Optional.of(operation.context().subject()), children);
      children
          .add(new Field("Commit knowledge", operation.knowledge().map(knowledge -> token(knowledge.name())).orElse(Absence.UNAVAILABLE)));
      children.add(
          new Field("Application boundary", operation.boundary().map(boundary -> token(boundary.name())).orElse(Absence.NOT_APPLICABLE)));
      children.add(new Field("Adapter", operation.adapter().map(detail -> text(detail.adapter())).orElse(Absence.NOT_APPLICABLE)));
      children.add(new Field("Adapter phase", operation.adapter().map(detail -> text(detail.phase())).orElse(Absence.NOT_APPLICABLE)));
    });
    return new Group(new Field("Failure", number(failure.reference())), children);
  }

  private static Value reference(OptionalInt reference, Absence absence) {
    return reference.isPresent() ? number(reference.getAsInt()) : absence;
  }
}
