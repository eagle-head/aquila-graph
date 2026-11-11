package org.aquilagraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicReference;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.application.port.AnalysisUseCase;
import org.aquilagraph.cli.AnalyzeCommand;
import org.aquilagraph.cli.AnalyzeCommandOutcome;
import org.aquilagraph.cli.ChannelEmission;
import org.aquilagraph.cli.CommandDestination;
import org.aquilagraph.cli.CommandInvocation;
import org.aquilagraph.cli.CommandRunner;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.testsupport.AnalysisExecutionFixture;

/** Composes the bootstrap's reporting collaborators around a live publication fixture. */
public final class CliPublicationReporting {
  private CliPublicationReporting() {}

  public static CommandRunner runner(AnalysisUseCase application) {
    return new CommandRunner(new AnalyzeCommand(application, JavaRelease.JAVA_25, new Neo4jFailureDetailProjector()));
  }

  public static Result run(AnalysisRun run, AnalysisRunRepositoryFactory repositories,
      AtomicReference<AnalysisExecution> returned) {
    return observe(run, repositories, returned, false);
  }

  public static Result runWithOutputFailure(AnalysisRun run, AnalysisRunRepositoryFactory repositories,
      AtomicReference<AnalysisExecution> returned) {
    return observe(run, repositories, returned, true);
  }

  private static Result observe(AnalysisRun run, AnalysisRunRepositoryFactory repositories,
      AtomicReference<AnalysisExecution> returned, boolean failOutput) {
    var application = AnalysisCapabilities.execution(ignored -> run, AnalysisExecutionFixture::unexpectedAstRepository, repositories);
    var runner = runner(request -> {
      var execution = application.execute(request);
      returned.set(execution);
      return execution;
    });
    var attemptedOutput = new StringWriter();
    var error = new StringWriter();
    var signal = new IOException("Controlled reporting write failure");
    Writer output = failOutput ? new Writer() {
      @Override public void write(char[] buffer, int offset, int length) throws IOException {
        attemptedOutput.write(buffer, offset, length);
        throw signal;
      }
      @Override public void flush() { throw new AssertionError("A failed write must not be flushed"); }
      @Override public void close() { throw new AssertionError("Borrowed output must not close"); }
    } : attemptedOutput;
    var root = run.representation().ast().capturedInput().orElseThrow().sourceRoot();
    var invocation = runner.run(new String[] {root.toString(), "--persist"},
        CommandDestination.throwingWriter(output), CommandDestination.throwingWriter(error));
    assertSame(returned.get(), assertInstanceOf(AnalyzeCommandOutcome.Executed.class, invocation.outcome()).execution());
    if (failOutput) {
      assertSame(signal, assertInstanceOf(ChannelEmission.IoFailure.class, invocation.emission().output()).cause());
    }
    return new Result(invocation, attemptedOutput.toString(), error.toString());
  }

  public record Result(CommandInvocation invocation, String attemptedOutput, String error) {
    public void assertPublication(String knowledge, String phase) {
      var execution = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, invocation.outcome()).execution();
      assertEquals("FAILED", execution.publication().status().name());
      assertTrue(attemptedOutput.endsWith(String.join(System.lineSeparator(), "Repository: FAILED",
          "Publication intent: SAVE_COMPLETED", "Publication subject: COMPLETED_RUN \"" + execution.run().identity().key() + "\"",
          "Commit knowledge: " + knowledge) + System.lineSeparator()), attemptedOutput);
      assertTrue(error.contains("  Adapter: \"Neo4j\"" + System.lineSeparator() + "  Adapter phase: \"" + phase + "\""), error);
      assertTrue(error.contains("  Commit knowledge: " + knowledge + System.lineSeparator()), error);
    }
  }
}
