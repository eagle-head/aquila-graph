package org.aquilagraph.cli;

import static org.aquilagraph.cli.ReportDocument.number;
import static org.aquilagraph.cli.ReportDocument.sequence;
import static org.aquilagraph.cli.ReportDocument.text;
import static org.aquilagraph.cli.ReportDocument.token;

import java.util.ArrayList;
import java.util.List;
import org.aquilagraph.cli.ReportDiagnostic.Evidence;
import org.aquilagraph.cli.ReportDiagnostic.Link;
import org.aquilagraph.cli.ReportDiagnostic.Origin;
import org.aquilagraph.cli.ReportDiagnostic.Value;
import org.aquilagraph.cli.ReportDocument.Absence;
import org.aquilagraph.cli.ReportDocument.Field;
import org.aquilagraph.cli.ReportDocument.Group;
import org.aquilagraph.cli.ReportDocument.Node;
import org.aquilagraph.cli.ReportDocument.Section;

/** Composes diagnostic facts and exact catalog links without reading native owners. */
final class DiagnosticDocumentComposer {
  Group nativeEvent(ReportDiagnostic diagnostic) {
    var children = new ArrayList<Node>();
    children.add(new Field("Occurrence", number(diagnostic.reference())));
    var owner = switch (diagnostic.owner()) {
      case ReportDiagnostic.Owner.Source _ -> "SOURCE";
      case ReportDiagnostic.Owner.Request _ -> "CFG_REQUEST";
      case ReportDiagnostic.Owner.Method _ -> "CFG_METHOD";
      case ReportDiagnostic.Owner.Application _ -> "APPLICATION";
    };
    children.add(new Field("Owner", token(owner)));
    children.add(new Field("Native position", number(diagnostic.nativePosition())));
    if (diagnostic.owner() instanceof ReportDiagnostic.Owner.Method method) {
      children.add(new Field("Owner method", text(method.method().methodKey())));
    }
    children.addAll(fields(diagnostic.value()));
    return new Group(new Field("Diagnostic", text(diagnostic.value().key())), children);
  }

  Group evidence(Evidence evidence) {
    var children = new ArrayList<Node>();
    children.add(new Field("Evidence role", evidence.role().map(role -> token(role.name())).orElse(Absence.NOT_APPLICABLE)));
    children.addAll(fields(evidence.diagnostic()));
    return new Group(new Field("Evidence diagnostic", text(evidence.diagnostic().key())), children);
  }

  private static List<Node> fields(Value diagnostic) {
    return List.of(new Field("Severity", token(diagnostic.severity().name())), new Field("Stage", token(diagnostic.stage().name())),
        new Field("Cause", token(diagnostic.cause().name())), new Field("Code", text(diagnostic.code())),
        new Field("Message", text(diagnostic.message())), origins(diagnostic.origins()));
  }

  static Section origins(List<Origin> origins) {
    if (origins.isEmpty()) {
      return new Section(List.of(new Field("Origins", Absence.UNAVAILABLE)));
    }
    return new Section(origins.stream()
        .<Node>map(origin -> new Field("Origin",
            sequence(text(origin.span().sourceFileKey()), token(" file="),
                origin.relativePath().map(ReportDocument::text).orElse(Absence.UNAVAILABLE), token(" range=["),
                number(origin.span().startOffset()), token(","), number(origin.span().endOffsetExclusive()), token(")"))))
        .toList());
  }

  static Section links(List<Link> links, List<ReportDiagnostic> catalog, boolean source) {
    return new Section(links.stream().map(link -> link(link, catalog, source)).toList());
  }

  private static Node link(Link link, List<ReportDiagnostic> catalog, boolean source) {
    var event = catalog.get(link.reference() - 1);
    var header = new Field("Related diagnostic", sequence(text(event.value().key()), token(" occurrence="), number(link.reference())));
    if (!source) {
      return header;
    }
    var address = link.emission()
        .map(emission -> List.<Node>of(new Field("Emission producer", text(emission.producer())),
            new Field("Emission owner", text(emission.owner())), new Field("Emission ordinal", number(emission.ordinal()))))
        .orElseGet(() -> List.of(new Field("Emission", Absence.UNAVAILABLE)));
    return new Group(header, address);
  }
}
