package org.aquilagraph.cli;

import org.aquilagraph.cli.ReportQualification.Subject;
import org.aquilagraph.source.api.SourceQualificationReason;

/** Copies subject descriptors while leaving complete copied fact payloads with their domain owner. */
final class SourceSubjectProjector {
  Subject project(SourceQualificationReason.Subject subject) {
    return switch (subject) {
      case SourceQualificationReason.Capture _ -> Subject.Capture.INSTANCE;
      case SourceQualificationReason.Node node -> new Subject.Node(node.nodeKey(), node.sourceFileKey());
      case SourceQualificationReason.Declaration declaration -> new Subject.Declaration(declaration.key());
      case SourceQualificationReason.Context context -> new Subject.Context(context.address());
      case SourceQualificationReason.Fact fact ->
        new Subject.Fact(fact.reference().kind(), fact.reference().identity(), fact.reference().sourceFileKey(), fact.reference().origin());
    };
  }
}
