package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.aquilagraph.CliPublicationReporting;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.ProgramRepresentation;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.port.AnalysisRunRepository;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.application.port.AnalysisUseCase;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgBlocker;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cli.AnalyzeCommandOutcome;
import org.aquilagraph.cli.CommandDestination;
import org.aquilagraph.cli.CommandExitPolicy;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProducer;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.testsupport.AnalysisExecutionFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConstructorCorrelationCommandTest {
  private static final Path FILE = Path.of("Example.java");
  private static final String CREATION = "new Example(\"text\")";
  private static final String SOURCE = """
      class Example {
        <T> Example(T value) {}
        static Example create() { return new Example("text"); }
      }
      """;

  @TempDir Path directory;

  @Test
  void givenMissingOrRejectedConstructorWitness_whenCliRequestsPublication_thenItReportsTheOriginalOccurrenceWithoutOpeningStorage()
      throws IOException {
    // Arrange
    Files.writeString(directory.resolve(FILE), SOURCE);
    var start = SOURCE.indexOf(CREATION);
    var expectedOrigin = new SourceSpan(SourceFile.fromContent(FILE, SOURCE).sourceFileKey(), start, start + CREATION.length());
    var cases = List.of(
        new WitnessFailure("class PrivateProof {}", "frontend.missing-constructor-correlation",
            "Required constructor argument evidence could not be correlated with its captured source occurrence"),
        new WitnessFailure("class PrivateProof { MissingType value; }", "frontend.constructor-witness-rejected",
            "Private constructor witness compilation did not establish the required argument evidence"));
    for (var witness : cases) {
      var output = new StringWriter();
      var error = new StringWriter();
      var runner = CliPublicationReporting.runner(application(frontendWithWitness(witness.source()),
          AnalysisExecutionFixture::unexpectedRunRepository));

      // Act
      var invocation = runner.run(new String[] {directory.toString(), "--persist"}, CommandDestination.throwingWriter(output),
          CommandDestination.throwingWriter(error));

      // Assert
      var execution = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, invocation.outcome()).execution();
      var representation = execution.run().representation();
      var ast = representation.ast();
      assertEquals(CommandExitPolicy.EXIT_INCOMPLETE, invocation.exitCode());
      assertEquals(AstCompletion.INCOMPLETE, ast.completion());
      assertEquals(PublicationStatus.NOT_ELIGIBLE, execution.publication().status());
      assertEquals(CommitKnowledge.NOT_STARTED, execution.publication().knowledge());
      assertTrue(execution.publication().failure().isEmpty());
      assertEquals(List.of(witness.code()), ast.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
      assertEquals(List.of(expectedOrigin), ast.diagnostics().getFirst().origins());
      assertRefusedCreationGraph(representation, expectedOrigin);
      assertTrue(output.toString().lines().toList().containsAll(List.of("AST: INCOMPLETE", "Repository: NOT_ELIGIBLE",
          "Commit knowledge: NOT_STARTED")), output.toString());
      var diagnostic = ast.diagnostics().getFirst();
      var originLine = "  Origin: \"" + expectedOrigin.sourceFileKey() + "\" file=\"Example.java\" range=[" + expectedOrigin.startOffset()
          + "," + expectedOrigin.endOffsetExclusive() + ")";
      assertTrue(error.toString().startsWith(String.join(System.lineSeparator(),
          "Diagnostic: \"" + diagnostic.diagnosticKey() + "\"", "  Occurrence: 1", "  Owner: SOURCE", "  Native position: 0",
          "  Severity: ERROR", "  Stage: FRONTEND", "  Cause: OTHER_ANALYSIS_ERROR", "  Code: \"" + witness.code() + "\"",
          "  Message: \"" + witness.message() + "\"", originLine) + System.lineSeparator()), error.toString());
    }
  }

  private static void assertRefusedCreationGraph(ProgramRepresentation representation, SourceSpan expectedOrigin) {
    var ast = representation.ast().result().orElseThrow();
    var method = ast.program().methods().stream().filter(value -> value.name().equals("create")).findFirst().orElseThrow();
    var syntax = ast.body(method.methodKey()).orElseThrow();
    var creation = syntax.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
        .findFirst().orElseThrow();
    var outcome = representation.cfg().orElseThrow().methods().stream().filter(value -> value.methodKey().equals(method.methodKey()))
        .findFirst().orElseThrow();
    assertEquals(MethodCfgState.INSUFFICIENT_EVIDENCE, outcome.state());
    assertTrue(outcome.graph().isEmpty());
    assertEquals(ast.bodyRef(method.methodKey()), outcome.body());
    assertEquals(List.of("use/argument/0", InvocationEvidence.SLOT), outcome.blockers().stream().map(CfgBlocker::slot).toList());
    for (var blocker : outcome.blockers()) {
      assertEquals(MethodCfgState.INSUFFICIENT_EVIDENCE, blocker.kind());
      assertEquals(ast.occurrence(creation), blocker.occurrence());
      assertEquals(List.of(expectedOrigin), blocker.diagnostic().origins());
    }
  }

  @Test
  void givenCorrelatedGenericConstructor_whenCliRequestsPublication_thenItSavesTheRunWithTheSelectedArgumentTarget() throws IOException {
    // Arrange
    Files.writeString(directory.resolve(FILE), SOURCE);
    var acquisitions = new ArrayList<RepositoryOperationContext>();
    var stored = new ArrayList<AnalysisRun>();
    var closures = new AtomicInteger();
    var output = new StringWriter();
    var error = new StringWriter();
    var runner = CliPublicationReporting.runner(application(new JavaFrontend(new JavaSourceLoader()), context -> {
      acquisitions.add(context);
      return new AnalysisRunRepository() {
        @Override
        public void save(AnalysisRun run) {
          stored.add(run);
        }

        @Override
        public void close() {
          closures.incrementAndGet();
        }
      };
    }));

    // Act
    var invocation = runner.run(new String[] {directory.toString(), "--persist"}, CommandDestination.throwingWriter(output),
        CommandDestination.throwingWriter(error));

    // Assert
    var execution = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, invocation.outcome()).execution();
    assertEquals(CommandExitPolicy.EXIT_COMPLETED, invocation.exitCode(), error.toString());
    assertEquals(PublicationStatus.SAVED, execution.publication().status());
    assertEquals(CommitKnowledge.KNOWN_COMMIT, execution.publication().knowledge());
    assertEquals(List.of(RepositoryOperationContext.saveCompletedRun(execution.run().identity().key())), acquisitions);
    assertEquals(1, stored.size());
    assertSame(execution.run(), stored.getFirst());
    assertEquals(1, closures.get());
    var ast = stored.getFirst().representation().ast().result().orElseThrow();
    var method = ast.program().methods().stream().filter(value -> value.name().equals("create")).findFirst().orElseThrow();
    var body = ast.body(method.methodKey()).orElseThrow();
    var creation = body.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
        .findFirst().orElseThrow();
    assertEquals("java.lang.String", body.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().displayName());
    assertTrue(output.toString().lines().toList().containsAll(List.of("AST: COMPLETE", "Repository: SAVED",
        "Commit knowledge: KNOWN_COMMIT")), output.toString());
    assertEquals("", error.toString());
  }

  private static AnalysisUseCase application(AstProducer producer, AnalysisRunRepositoryFactory runs) {
    var computation = AnalysisCapabilities.computation(producer, CfgDerivers.standard());
    return AnalysisCapabilities.execution(computation, AnalysisExecutionFixture::unexpectedAstRepository, runs);
  }

  private static AstProducer frontendWithWitness(String source) {
    var delegate = ToolProvider.getSystemJavaCompiler();
    var witness = SimpleJavaFileObject.forSource(URI.create("string:///PrivateProof.java"), source);
    var compiler = (JavaCompiler) Proxy.newProxyInstance(JavaCompiler.class.getClassLoader(), new Class<?>[] {JavaCompiler.class},
        (_, method, arguments) -> {
          if (method.getName().equals("getTask") && arguments[1] instanceof ConstructorWitnessFileManager) {
            arguments[5] = List.of(witness);
          }
          try {
            return method.invoke(delegate, arguments);
          } catch (InvocationTargetException failure) {
            throw failure.getCause();
          }
        });
    return new JavaFrontend(new JavaSourceLoader(),
        new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture()), () -> compiler));
  }

  private record WitnessFailure(String source, String code, String message) {
  }
}
