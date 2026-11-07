package org.aquilagraph.cli;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.aquilagraph.application.AnalysisRequestIdentity.SourceSubject;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cli.AnalysisRunSummary.MethodDescriptor;
import org.aquilagraph.cli.ReportDiagnostic.Evidence;
import org.aquilagraph.cli.ReportDiagnostic.Link;
import org.aquilagraph.cli.ReportDiagnostic.Origin;
import org.aquilagraph.identity.api.SourceOccurrence;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.CoverageFactReference.Kind;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceQualificationReason.Category;
import org.aquilagraph.source.api.SourceQualificationReason.EvidenceRole;

/** Detached native qualification forms; collections retain their independent order. */
sealed interface ReportQualification {
  String PROFILE_PARAMETER = "profile";
  List<Link> links();

  record Source(Category category, EvidenceRole role, AstProfile profile, Subject subject, List<Origin> origins, List<Link> links,
      List<Evidence> evidence) implements ReportQualification {
    public Source {
      Objects.requireNonNull(category, "category");
      Objects.requireNonNull(role, "role");
      Objects.requireNonNull(profile, PROFILE_PARAMETER);
      Objects.requireNonNull(subject, "subject");
      origins = List.copyOf(origins);
      links = List.copyOf(links);
      evidence = List.copyOf(evidence);
    }
  }

  sealed interface Subject {
    enum Capture implements Subject {
      INSTANCE
    }
    record Node(String nodeKey, String sourceFileKey) implements Subject {
      public Node {
        Objects.requireNonNull(nodeKey, "nodeKey");
        Objects.requireNonNull(sourceFileKey, "sourceFileKey");
      }
    }
    record Declaration(String key) implements Subject {
      public Declaration {
        Objects.requireNonNull(key, "key");
      }
    }
    record Context(SourceContext.Address address) implements Subject {
      public Context {
        Objects.requireNonNull(address, "address");
      }
    }
    record Fact(Kind kind, String identity, String sourceFileKey, Optional<SourceSpan> origin) implements Subject {
      public Fact {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(sourceFileKey, "sourceFileKey");
        Objects.requireNonNull(origin, "origin");
      }
    }
  }

  record Request(CfgRequest request, List<Origin> origins, List<Link> links) implements ReportQualification {
    public Request {
      Objects.requireNonNull(request, "request");
      origins = List.copyOf(origins);
      links = List.copyOf(links);
    }
  }

  record Failed(MethodDescriptor method, CfgProfile profile, int failureReference, List<Origin> origins,
      List<Link> links) implements ReportQualification {
    public Failed {
      Objects.requireNonNull(method, "method");
      Objects.requireNonNull(profile, PROFILE_PARAMETER);
      origins = List.copyOf(origins);
      links = List.copyOf(links);
    }
  }

  record Blocker(MethodDescriptor method, CfgProfile profile, MethodCfgState kind, Optional<SourceOccurrence> occurrence, String slot,
      List<Origin> origins, List<Link> links, Optional<Evidence> evidence) implements ReportQualification {
    public Blocker {
      Objects.requireNonNull(method, "method");
      Objects.requireNonNull(profile, PROFILE_PARAMETER);
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(occurrence, "occurrence");
      Objects.requireNonNull(slot, "slot");
      origins = List.copyOf(origins);
      links = List.copyOf(links);
      Objects.requireNonNull(evidence, "evidence");
    }
  }

  record Application(ApplicationContext context, List<Origin> origins, List<Link> links) implements ReportQualification {
    public Application {
      Objects.requireNonNull(context, "context");
      origins = List.copyOf(origins);
      links = List.copyOf(links);
    }
  }

  sealed interface ApplicationContext {
    record Source(SourceSubject subject, AstProfile profile) implements ApplicationContext {
      public Source {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(profile, PROFILE_PARAMETER);
      }
    }
    record Selection(CfgRequest request) implements ApplicationContext {
      public Selection {
        Objects.requireNonNull(request, "request");
      }
    }
    record Method(MethodDescriptor method, CfgProfile profile, OptionalInt diagnosticPosition) implements ApplicationContext {
      public Method {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(profile, PROFILE_PARAMETER);
        Objects.requireNonNull(diagnosticPosition, "diagnosticPosition");
      }
    }
    record Diagnostic(int nativePosition) implements ApplicationContext {
    }
  }
}
