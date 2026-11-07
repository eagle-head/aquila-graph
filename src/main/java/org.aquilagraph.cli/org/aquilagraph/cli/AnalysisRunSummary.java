package org.aquilagraph.cli;

import java.util.Objects;
import java.util.Optional;
import java.util.List;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisRequestIdentity;
import org.aquilagraph.application.MethodCoverageState;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.BodyRef;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;

/** Detached run identity, independent qualifications and actual graph metrics. */
record AnalysisRunSummary(AnalysisRequestIdentity identity, AnalysisCompletion completion, Optional<SoftwareVersion> software,
    Optional<JavaRelease> release, int sourceFileCount, AstCompletion sourceState, AstProfile sourceProfile, Optional<CfgState> cfgState,
    Optional<ProgramSummary> program, List<MethodCoverage> methods) {
  AnalysisRunSummary {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(completion, "completion");
    Objects.requireNonNull(software, "software");
    Objects.requireNonNull(release, "release");
    Objects.requireNonNull(sourceState, "sourceState");
    Objects.requireNonNull(sourceProfile, "sourceProfile");
    Objects.requireNonNull(cfgState, "cfgState");
    Objects.requireNonNull(program, "program");
    methods = List.copyOf(methods);
  }

  record ProgramSummary(int typeCount, int methodCount, int analyzedMethodBodyCount, int controlFlowOperationCount,
      int controlFlowNodeCount, int controlFlowEdgeCount) {
  }

  record MethodDescriptor(AstIdentity astIdentity, String methodKey, Optional<BodyRef> body) {
    MethodDescriptor {
      Objects.requireNonNull(astIdentity, "astIdentity");
      Objects.requireNonNull(methodKey, "methodKey");
      Objects.requireNonNull(body, "body");
    }
  }

  record MethodCoverage(MethodDescriptor method, MethodCoverageState state) {
    MethodCoverage {
      Objects.requireNonNull(method, "method");
      Objects.requireNonNull(state, "state");
    }
  }
}
