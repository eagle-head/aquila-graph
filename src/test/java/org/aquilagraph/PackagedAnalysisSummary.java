package org.aquilagraph;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;

/** Expected packaged output for the complete single-type publication fixtures. */
final class PackagedAnalysisSummary {
  private PackagedAnalysisSummary() {}

  static Expectation expected(Path sourceRoot, PublicationStatus status) {
    var source = new AstProductionRequest(sourceRoot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_BOUNDED);
    var selection = CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED);
    var computation = AnalysisCapabilities.computation(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    var run = computation.analyze(new AnalysisComputationRequest(source, Optional.of(selection)));
    var ast = run.representation().ast().result().orElseThrow();
    var methods = ast.program().methods();
    var graphs = run.representation().cfg().orElseThrow().methods().stream().map(method -> method.graph().orElseThrow()).toList();
    var lines = new ArrayList<>(List.of("Run: \"" + run.identity().key() + "\"", "Software version: \"" + ast.identity().softwareSignature() + "\"",
        "Completion: COMPLETE", "Java release: 25", "Java source files: 1", "AST: COMPLETE", "AST profile: \"java-source-bounded/1\"",
        "CFG: COMPLETE", "CFG profile: \"java-cfg-bounded/1\"", "CFG graph model: \"java-cfg/3\"", "CFG selection: ALL_BODIES",
        "CFG binding: \"" + ast.identity().astResultKey() + "\""));
    methods.forEach(method -> lines.add("Selected method: \"" + method.methodKey() + "\""));
    lines.addAll(List.of("Java types: 1", "Java methods: " + methods.size(), "Analyzed method bodies: " + graphs.size(),
        "CFG operations: " + graphs.stream().mapToInt(graph -> graph.operationNodes().size()).sum(),
        "CFG nodes: " + graphs.stream().mapToInt(graph -> graph.nodeCount()).sum(),
        "CFG edges: " + graphs.stream().mapToInt(graph -> graph.edges().size()).sum()));
    methods.forEach(method -> lines.add("Method CFG: \"" + method.methodKey() + "\" AVAILABLE"));
    lines.add("Repository: " + status);
    lines.add("Publication intent: SAVE_COMPLETED");
    lines.add("Publication subject: COMPLETED_RUN \"" + run.identity().key() + "\"");
    lines.add("Commit knowledge: " + (status == PublicationStatus.SAVED ? "KNOWN_COMMIT" : "NOT_STARTED"));
    return new Expectation(String.join(System.lineSeparator(), lines) + System.lineSeparator(), run.identity().key());
  }
  static String invalidConfigurationError(String runId) {
    return String.join(System.lineSeparator(),
        "Repository save failed: \"Invalid Neo4j repository configuration\"", "Failure reference: 1",
        "Failure: 1", "  Type: \"org.aquilagraph.application.PublicationFailure\"", "  Message: \"Publication failed\"",
        "  Cause: 2", "  Carrier: 2", "  Effective primary: 3", "  Suppressed: none", "  Operation: not-applicable",
        "Failure: 2", "  Type: \"org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException\"", "  Message: \"Repository operation failed\"",
        "  Cause: 3", "  Carrier: not-applicable", "  Effective primary: 3", "  Suppressed: none", "  Operation: SAVE_COMPLETED_RUN",
        "  Operation subject: COMPLETED_RUN \"" + runId + "\"", "  Commit knowledge: NOT_STARTED", "  Application boundary: not-applicable",
        "  Adapter: \"Neo4j\"", "  Adapter phase: \"ACQUISITION\"",
        "Failure: 3", "  Type: \"org.aquilagraph.application.port.RepositoryException\"", "  Message: \"Invalid Neo4j repository configuration\"",
        "  Cause: 4", "  Carrier: not-applicable", "  Effective primary: not-applicable", "  Suppressed: none", "  Operation: not-applicable",
        "Failure: 4", "  Type: \"java.lang.IllegalArgumentException\"",
        "  Message: \"Set AQUILA_GRAPH_NEO4J_PASSWORD or NEO4J_PASSWORD before using --persist\"", "  Cause: unavailable",
        "  Carrier: not-applicable", "  Effective primary: not-applicable", "  Suppressed: none", "  Operation: not-applicable")
        + System.lineSeparator();
  }

  record Expectation(String output, String runId) { }
}
