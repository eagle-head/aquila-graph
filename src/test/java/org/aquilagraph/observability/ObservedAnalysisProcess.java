package org.aquilagraph.observability;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.aquilagraph.frontend.AnalysisObserver;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.frontend.JavaObservations;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;

/** Explicit process campaign consumer; never selected by the isolated unit suite. */
public final class ObservedAnalysisProcess {
  private ObservedAnalysisProcess() {
  }

  /**
   * Alternates observation modes against one immutable source directory.
   *
   * @param arguments source directory, output directory, and number of measured pairs
   * @throws Exception if analysis or evidence capture fails
   */
  public static void main(String[] arguments) throws Exception {
    var sources = Path.of(arguments[0]).toAbsolutePath();
    var output = Path.of(arguments[1]).toAbsolutePath();
    var pairs = Integer.parseInt(arguments[2]);
    Files.createDirectories(output);
    var request = new AstProductionRequest(sources, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var report = new Properties();
    report.setProperty("java.version", Runtime.version().toString());
    report.setProperty("source", sources.toString());
    var expected = new AtomicReference<String>();
    var equivalent = true;
    for (var iteration = -2; iteration < pairs * 2; iteration++) {
      var enabled = (iteration % 4 == 1 || iteration % 4 == 2 || iteration == -1)
          && (arguments.length < 4 || !arguments[3].equals("disabled"));
      var prefix = (enabled ? "enabled." : "disabled.") + iteration;
      var trace = new AtomicReference<>("");
      var execution = new AtomicReference<>("");
      var settings = Map.of("AQUILA_OBSERVABILITY_ENABLED", Boolean.toString(enabled), "OTEL_SERVICE_NAME", "aquila-graph",
          "OTEL_EXPORTER_OTLP_ENDPOINT", System.getenv().getOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT", "http://127.0.0.1:4318"));
      var lifecycleStarted = System.nanoTime();
      try (var observer = JavaObservations.open(settings); var recording = new Recording(Configuration.getConfiguration("profile"))) {
        if (enabled) {
          recording.enable("org.aquilagraph.JavaAnalysisStage").withoutThreshold();
          recording.start();
        }
        AnalysisObserver correlation = (stage, attributes) -> {
          var scope = observer.start(stage, attributes);
          if (stage.equals("production")) {
            trace.set(scope.traceId());
            execution.set(scope.executionId());
          }
          return scope;
        };
        var frontend = enabled ? JavaFrontends.observed(correlation) : JavaFrontends.standard();
        var started = System.nanoTime();
        var result = frontend.produce(request);
        report.setProperty(prefix + ".analysis.nanos", Long.toString(System.nanoTime() - started));
        var identity = result.result().orElseThrow().identity().toString();
        Files.writeString(output.resolve(prefix + ".ast.txt"), result.result().orElseThrow().aggregate().nodes().toString()
            + "\n" + result.result().orElseThrow().qualification() + "\n" + result.result().orElseThrow().diagnostics());
        if (expected.compareAndSet(null, identity)) {
          report.setProperty("ast.identity", identity);
        } else if (!expected.get().equals(identity)) {
          equivalent = false;
        }
        report.setProperty(prefix + ".ast.identity", identity);
        report.setProperty(prefix + ".completion", result.completion().name());
        report.setProperty(prefix + ".trace", trace.get());
        report.setProperty(prefix + ".execution", execution.get());
        report.setProperty(prefix + ".nodes", Integer.toString(result.result().orElseThrow().aggregate().nodes().size()));
        if (enabled) {
          if (trace.get().isEmpty()) {
            throw new IllegalStateException("The observation provider did not produce a trace identifier");
          }
          recording.stop();
          recording.dump(output.resolve(prefix + ".jfr"));
        }
      }
      report.setProperty(prefix + ".lifecycle.nanos", Long.toString(System.nanoTime() - lifecycleStarted));
    }
    report.setProperty("ast.identity.equal", Boolean.toString(equivalent));
    try (var writer = Files.newBufferedWriter(output.resolve("analysis.properties"))) {
      report.store(writer, "Actual source analysis observations; negative iterations are warmup");
    }
    if (!equivalent) {
      throw new IllegalStateException("AST identity equivalence failed; complete evidence was retained in " + output);
    }
  }
}
