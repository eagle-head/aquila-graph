package org.aquilagraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.file.Path;
import java.util.Optional;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceQualificationReason.Node;

/** Complete, independently authored blocks for the fixed loop and empty-input fixtures. */
final class PackagedQualificationExpectations {
  private PackagedQualificationExpectations() {}

  static Expectation unsupportedLoop(Path root) {
    var run = analyze(root);
    var ast = run.representation().ast().result().orElseThrow();
    var reasons = ast.qualification().reasons();
    assertEquals(1, reasons.size());
    var node = assertInstanceOf(Node.class, reasons.getFirst().subject());
    assertEquals(1, run.diagnostics().values().size());
    var diagnostic = run.diagnostics().values().getFirst().diagnosticKey();
    var body = ast.bodyRef("type:Example#run()").orElseThrow().sourceOccurrenceKey().orElseThrow();
    var identity = ast.identity();
    var output = block("""
        Run: "%1$s"
        Software version: "%2$s"
        Completion: PARTIAL
        Java release: 25
        Java source files: 1
        AST: INCOMPLETE
        AST profile: "java-source-bounded/1"
        CFG: PARTIAL
        CFG profile: "java-cfg-bounded/1"
        CFG graph model: "java-cfg/3"
        CFG selection: ALL_BODIES
        CFG binding: "%3$s"
        Selected method: "type:Example#<init>()"
        Selected method: "type:Example#run()"
        Java types: 1
        Java methods: 2
        Analyzed method bodies: 1
        CFG operations: 1
        CFG nodes: 3
        CFG edges: 3
        Method CFG: "type:Example#<init>()" AVAILABLE
        Method CFG: "type:Example#run()" UNSUPPORTED
        Repository: NOT_REQUESTED
        Publication intent: DO_NOT_SAVE
        Publication subject: AST "%3$s"
        Publication software signature: "%2$s"
        Publication source model: "java-source/24"
        Publication AST profile: "java-source-bounded/1"
        Publication production revision: "javac-public/15"
        Publication content digest: "%4$s"
        Commit knowledge: NOT_STARTED
        """.formatted(run.identity().key(), identity.softwareSignature(), identity.astResultKey(), identity.canonicalContentDigest()));
    var error = block("""
        Diagnostic: "%1$s"
          Occurrence: 1
          Owner: CFG_METHOD
          Native position: 0
          Owner method: "type:Example#run()"
          Severity: ERROR
          Stage: CONTROL_FLOW
          Cause: CONTROL_FLOW_UNAVAILABLE
          Code: "control-flow.unsupported"
          Message: "Represented source construct is outside the selected CFG profile"
          Origin: "%2$s" file="Example.java" range=[29,44)
        Qualification: SOURCE PROFILE_RESTRICTION
          Profile: "java-source-bounded/1"
          Role: PROFILE_FORM
          Subject: "%3$s"
          Subject kind: NODE
          Subject source file: "%2$s"
          Origin: "%2$s" file="Example.java" range=[29,44)
        Qualification: CFG UNSUPPORTED
          Profile: "java-cfg-bounded/1"
          Subject: "type:Example#run()"
          AST result: "%4$s"
          Body: WRITTEN
          Body occurrence: "%5$s"
          Source occurrence: "%3$s"
          Slot: "construct"
          Origin: "%2$s" file="Example.java" range=[29,44)
          Related diagnostic: "%1$s" occurrence=1
        Qualification: APPLICATION SOURCE
          Profile: "java-source-bounded/1"
          Subject: "%4$s"
          Origin: "%2$s" file="Example.java" range=[29,44)
        Qualification: APPLICATION METHOD
          Profile: "java-cfg-bounded/1"
          Subject: "type:Example#run()"
          AST result: "%4$s"
          Body: WRITTEN
          Body occurrence: "%5$s"
          Origin: "%2$s" file="Example.java" range=[29,44)
          Related diagnostic: "%1$s" occurrence=1
        """.formatted(diagnostic, node.sourceFileKey(), node.nodeKey(), identity.astResultKey(), body));
    return new Expectation(output, error);
  }

  static Expectation emptyInput(Path root) {
    var run = analyze(root);
    assertEquals(Optional.empty(), run.representation().ast().result());
    return new Expectation(block("""
        Run: "%s"
        Software version: unavailable
        Completion: REJECTED
        Java release: 25
        Java source files: 0
        AST: UNAVAILABLE
        AST profile: "java-source-bounded/1"
        CFG: UNAVAILABLE
        CFG profile: "java-cfg-bounded/1"
        CFG graph model: "java-cfg/3"
        CFG selection: ALL_BODIES
        CFG binding: UNBOUND
        Repository: NOT_REQUESTED
        Publication intent: DO_NOT_SAVE
        Publication subject: unavailable
        Commit knowledge: NOT_STARTED
        """.formatted(run.identity().key())), block("""
        Qualification: SOURCE EMPTY_SOURCE
          Profile: "java-source-bounded/1"
          Role: SOURCE_CAPTURE
          Subject: unavailable
          Subject kind: CAPTURE
          Origins: unavailable
        Qualification: APPLICATION SOURCE
          Profile: "java-source-bounded/1"
          Subject: unavailable
          Origins: unavailable
        """));
  }

  private static AnalysisRun analyze(Path root) {
    var computation = AnalysisCapabilities.computation(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    return computation.analyze(new AnalysisComputationRequest(new AstProductionRequest(root,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED),
        Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED))));
  }

  private static String block(String text) {
    return text.replace("\n", System.lineSeparator());
  }

  record Expectation(String output, String error) {}
}
