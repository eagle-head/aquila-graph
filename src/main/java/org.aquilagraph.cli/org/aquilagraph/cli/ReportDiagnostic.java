package org.aquilagraph.cli;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.application.AnalysisRequestIdentity;
import org.aquilagraph.application.AnalysisRequestIdentity.SourceSubject;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cli.AnalysisRunSummary.MethodDescriptor;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.SourceQualificationReason.EvidenceRole;

/** One native event with detached owner, native slot and local report position. */
record ReportDiagnostic(int reference, Owner owner, int nativePosition, Value value) {
  ReportDiagnostic {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(value, "value");
  }

  sealed interface Owner {
    record Source(SourceSubject subject) implements Owner {
      public Source {
        Objects.requireNonNull(subject, "subject");
      }
    }
    record Request(CfgRequest request) implements Owner {
      public Request {
        Objects.requireNonNull(request, "request");
      }
    }
    record Method(MethodDescriptor method) implements Owner {
      public Method {
        Objects.requireNonNull(method, "method");
      }
    }
    record Application(AnalysisRequestIdentity identity) implements Owner {
      public Application {
        Objects.requireNonNull(identity, "identity");
      }
    }
  }

  record Origin(SourceSpan span, Optional<String> relativePath) {
    Origin {
      Objects.requireNonNull(span, "span");
      Objects.requireNonNull(relativePath, "relativePath");
    }
  }

  record Value(String key, DiagnosticSeverity severity, DiagnosticStage stage, AnalysisOutcomeKind cause, String code, String message,
      List<Origin> origins) {
    Value {
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(severity, "severity");
      Objects.requireNonNull(stage, "stage");
      Objects.requireNonNull(cause, "cause");
      Objects.requireNonNull(code, "code");
      Objects.requireNonNull(message, "message");
      origins = List.copyOf(origins);
    }
  }

  record Link(int reference, Optional<DiagnosticEmission.Address> emission) {
    Link {
      Objects.requireNonNull(emission, "emission");
    }
  }

  record Evidence(Optional<EvidenceRole> role, Value diagnostic) {
    Evidence {
      Objects.requireNonNull(role, "role");
      Objects.requireNonNull(diagnostic, "diagnostic");
    }
  }
}
