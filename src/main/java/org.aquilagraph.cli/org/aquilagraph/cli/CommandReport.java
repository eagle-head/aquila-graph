package org.aquilagraph.cli;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationEligibility.Impediment;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.PublicationSubjectIdentity;

/** Complete immutable semantic report, independent of layout and operational objects. */
sealed interface CommandReport {
  FailureCatalog failures();

  record Usage(ArgumentRejection reason) implements CommandReport {
    public Usage {
      Objects.requireNonNull(reason, "reason");
    }
    @Override
    public FailureCatalog failures() {
      return new FailureCatalog(List.of());
    }
  }

  enum FailureKind {
    INPUT, ENVIRONMENT, PUBLICATION
  }

  record Failed(FailureKind kind, String description, int failureReference, FailureCatalog failures) implements CommandReport {
    public Failed {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(description, "description");
      failures.node(failureReference);
    }
  }

  record Executed(AnalysisRunSummary analysis, List<ReportDiagnostic> diagnostics, List<ReportQualification> qualifications,
      Publication publication, FailureCatalog failures) implements CommandReport {
    public Executed {
      Objects.requireNonNull(analysis, "analysis");
      diagnostics = List.copyOf(diagnostics);
      qualifications = List.copyOf(qualifications);
      Objects.requireNonNull(publication, "publication");
      Objects.requireNonNull(failures, "failures");
      for (var index = 0; index < diagnostics.size(); index++) {
        if (diagnostics.get(index).reference() != index + 1) {
          throw new IllegalArgumentException("Diagnostic definitions must have consecutive local identities");
        }
      }
      for (var reason : qualifications) {
        for (var link : reason.links()) {
          FailureCatalog.requireReference(link.reference(), diagnostics.size());
        }
        if (reason instanceof ReportQualification.Failed failed) {
          failures.node(failed.failureReference());
        }
      }
      publication.failureReference().ifPresent(failures::node);
    }
  }

  record Publication(PublicationIntent intent, Optional<PublicationSubjectIdentity> subject, PublicationStatus status,
      Optional<CommitKnowledge> knowledge, OptionalInt failureReference, Optional<String> failureDescription,
      List<Impediment> impediments) {
    public Publication {
      Objects.requireNonNull(intent, "intent");
      Objects.requireNonNull(subject, "subject");
      Objects.requireNonNull(status, "status");
      Objects.requireNonNull(knowledge, "knowledge");
      Objects.requireNonNull(failureReference, "failureReference");
      Objects.requireNonNull(failureDescription, "failureDescription");
      impediments = List.copyOf(impediments);
    }
  }
}
