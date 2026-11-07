package org.aquilagraph.cli;

import static org.aquilagraph.cli.ReportDocument.sequence;
import static org.aquilagraph.cli.ReportDocument.text;
import static org.aquilagraph.cli.ReportDocument.token;

import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.PublicationSubjectIdentity;
import org.aquilagraph.cli.ReportDocument.Absence;
import org.aquilagraph.cli.ReportDocument.Field;
import org.aquilagraph.cli.ReportDocument.Node;

/** Composes exact publication subjects and their complete qualification tuples. */
final class PublicationDocumentComposer {
  private PublicationDocumentComposer() {
  }

  static void subject(String prefix, Optional<PublicationSubjectIdentity> subject, List<Node> children) {
    if (subject.isEmpty()) {
      children.add(new Field(prefix + " subject", Absence.UNAVAILABLE));
      return;
    }
    switch (subject.orElseThrow()) {
      case PublicationSubjectIdentity.CompletedRun run ->
        children.add(new Field(prefix + " subject", sequence(token("COMPLETED_RUN "), text(run.runId()))));
      case PublicationSubjectIdentity.Ast ast -> {
        var identity = ast.identity();
        children.add(new Field(prefix + " subject", sequence(token("AST "), text(identity.astResultKey()))));
        children.add(new Field(prefix + " software signature", text(identity.softwareSignature())));
        children.add(new Field(prefix + " source model", text(identity.sourceModelVersion())));
        children.add(new Field(prefix + " AST profile", text(identity.astProfileVersion())));
        children.add(new Field(prefix + " production revision", text(identity.semanticProductionRevision())));
        children.add(new Field(prefix + " content digest", text(identity.canonicalContentDigest())));
      }
    }
  }
}
