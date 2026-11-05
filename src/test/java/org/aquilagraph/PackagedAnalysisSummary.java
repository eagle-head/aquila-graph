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

  static String expectedOutput(Path sourceRoot, PublicationStatus status) {
    var source = new AstProductionRequest(sourceRoot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_BOUNDED);
    var selection = CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED);
    var computation = AnalysisCapabilities.computation(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    var run = computation.analyze(new AnalysisComputationRequest(source, Optional.of(selection)));
    var ast = run.representation().ast().result().orElseThrow();
    var methods = ast.program().methods();
    var graphs = run.representation().cfg().orElseThrow().methods().stream().map(method -> method.graph().orElseThrow()).toList();
    var lines = new ArrayList<>(List.of("Run: " + run.identity().key(), "Software version: " + ast.identity().softwareSignature(),
        "Completion: COMPLETE", "Java release: 25", "Java source files: 1", "AST: COMPLETE", "AST profile: java-source-bounded/1",
        "CFG: COMPLETE", "CFG profile: \"java-cfg-bounded/1\"", "CFG graph model: \"java-cfg/2\"", "CFG selection: ALL_BODIES",
        "CFG binding: " + ast.identity().astResultKey()));
    methods.forEach(method -> lines.add("Selected method: \"" + method.methodKey() + "\""));
    lines.addAll(List.of("Java types: 1", "Java methods: " + methods.size(), "Analyzed method bodies: " + graphs.size(),
        "CFG operations: " + graphs.stream().mapToInt(graph -> graph.operationNodes().size()).sum(),
        "CFG nodes: " + graphs.stream().mapToInt(graph -> graph.nodeCount()).sum(),
        "CFG edges: " + graphs.stream().mapToInt(graph -> graph.edges().size()).sum()));
    methods.forEach(method -> lines.add("Method CFG: \"" + method.methodKey() + "\" AVAILABLE"));
    lines.add("Repository: " + status);
    return String.join(System.lineSeparator(), lines) + System.lineSeparator();
  }
}
