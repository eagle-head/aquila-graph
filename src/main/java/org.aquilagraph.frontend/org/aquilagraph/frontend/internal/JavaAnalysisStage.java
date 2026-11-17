package org.aquilagraph.frontend.internal;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.Map;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.CancellationException;
import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.EventFactory;
import jdk.jfr.AnnotationElement;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.frontend.AnalysisObserver;
import org.aquilagraph.frontend.AnalysisObserver.Observation;

/** Opt-in stage observations that do not participate in source identity or admission. */
final class JavaAnalysisStage {
  private static final int MAX_DIAGNOSTICS = 100;
  private static final String PRODUCTION = "production";
  private static final String STARTED = "STARTED";
  String stage = "";
  String phase = STARTED;
  String inputDigest = "";
  String profile = "";
  int sourceFiles;
  long sourceCharacters;
  int sourceNodes = -1;
  String completion = "";
  String failureType = "";
  String executionId = "";
  String traceId = "";
  String spanId = "";

  private static final EventFactory EVENTS = EventFactory.create(
      List.of(new AnnotationElement(Name.class, "org.aquilagraph.JavaAnalysisStage"),
          new AnnotationElement(Label.class, "Java analysis stage"),
          new AnnotationElement(Category.class, new String[]{"Aquila Graph", "Java analysis"}), new AnnotationElement(Enabled.class, false),
          new AnnotationElement(StackTrace.class, false)),
      List.of(new ValueDescriptor(String.class, "stage"), new ValueDescriptor(String.class, "phase"),
          new ValueDescriptor(String.class, "inputDigest"), new ValueDescriptor(String.class, "profile"),
          new ValueDescriptor(int.class, "sourceFiles"), new ValueDescriptor(long.class, "sourceCharacters"),
          new ValueDescriptor(int.class, "sourceNodes"), new ValueDescriptor(String.class, "completion"),
          new ValueDescriptor(String.class, "failureType"), new ValueDescriptor(String.class, "executionId"),
          new ValueDescriptor(String.class, "traceId"), new ValueDescriptor(String.class, "spanId")));
  private final Event recording = EVENTS.newEvent();

  private void commit() {
    var values = List.of(stage, phase, inputDigest, profile, sourceFiles, sourceCharacters, sourceNodes, completion, failureType,
        executionId, traceId, spanId);
    for (var index = 0; index < values.size(); index++) {
      recording.set(index, values.get(index));
    }
    recording.commit();
  }

  static <T> T measure(AnalysisObserver observer, String name, InputSnapshot snapshot, AstProfile requestedProfile, Operation<T> operation)
      throws IOException {
    checkCancellation();
    var event = new JavaAnalysisStage();
    if (!event.recording.isEnabled() && AnalysisObserver.none().equals(observer)) {
      return operation.run();
    }
    event.stage = name;
    event.inputDigest = snapshot.snapshotDigest();
    event.profile = requestedProfile.version();
    event.sourceFiles = snapshot.sourceFileCount();
    if (event.recording.isEnabled()) {
      event.sourceCharacters = snapshot.sourceFiles().stream().mapToLong(source -> source.content().length()).sum();
    }
    var observation = start(observer, name, Map.of("aquila.input.digest", event.inputDigest, "aquila.profile", event.profile,
        "aquila.source.files", Integer.toString(event.sourceFiles)));
    emit(observation, value -> {
      event.executionId = value.executionId();
      event.traceId = value.traceId();
      event.spanId = value.spanId();
    });
    event.commit();
    event.recording.begin();
    try {
      var value = operation.run();
      event.phase = "SUCCEEDED";
      if (value instanceof TranslationResult<?> translation && translation.value() instanceof TranslatedProgram program) {
        event.sourceNodes = program.aggregate().map(aggregate -> aggregate.nodes().size()).orElse(-1);
      }
      if (value instanceof AstOutcome outcome) {
        event.completion = outcome.completion().name();
        event.sourceNodes = outcome.result().map(result -> result.aggregate().nodes().size()).orElse(0);
        if (PRODUCTION.equals(name)) {
          for (var diagnostic : outcome.diagnostics().stream().limit(MAX_DIAGNOSTICS).toList()) {
            emit(observation,
                scope -> scope.event("analysis.diagnostic",
                    Map.of("diagnostic.code", diagnostic.code(), "diagnostic.severity", diagnostic.severity().name(), "diagnostic.stage",
                        diagnostic.stage().name(), "diagnostic.message", diagnostic.message())));
          }
          if (outcome.diagnostics().size() > MAX_DIAGNOSTICS) {
            emit(observation, scope -> scope.event("analysis.diagnostics.limited",
                Map.of("diagnostic.omitted", Integer.toString(outcome.diagnostics().size() - MAX_DIAGNOSTICS))));
          }
        }
      }
      emit(observation, scope -> scope
          .complete(Map.of("aquila.completion", event.completion, "aquila.source.nodes", Integer.toString(event.sourceNodes))));
      return value;
    } catch (CancellationException failure) {
      event.phase = "CANCELLED";
      event.failureType = failure.getClass().getName();
      emit(observation,
          scope -> scope.event("analysis.failure", Map.of("exception.type", event.failureType, "operation.state", "cancelled")));
      throw failure;
    } catch (IOException failure) {
      event.phase = "FAILED";
      event.failureType = failure.getClass().getName();
      emit(observation, scope -> scope.event("analysis.failure", Map.of("exception.type", event.failureType)));
      throw failure;
    } finally {
      if (STARTED.equals(event.phase)) {
        event.phase = "FAILED";
        emit(observation, scope -> scope.event("analysis.failure", Map.of("operation.state", "did_not_complete")));
      }
      event.recording.end();
      event.commit();
      emit(observation, Observation::close);
    }
  }

  static void checkCancellation() {
    if (Thread.currentThread().isInterrupted()) {
      throw new CancellationException("Java analysis was interrupted");
    }
  }

  private static Observation start(AnalysisObserver observer, String stage, Map<String, String> attributes) {
    try {
      return observer.start(stage, attributes);
    } catch (IllegalArgumentException | IllegalStateException failure) {
      report(failure);
      return AnalysisObserver.none().start(stage, attributes);
    }
  }

  private static void emit(Observation observation, Consumer<Observation> action) {
    try {
      action.accept(observation);
    } catch (IllegalArgumentException | IllegalStateException failure) {
      report(failure);
    }
  }

  private static void report(RuntimeException failure) {
    System.getLogger(JavaAnalysisStage.class.getName()).log(Level.WARNING, "Aquila observation failed; analysis outcome is retained",
        failure);
  }

  @FunctionalInterface
  interface Operation<T> {
    T run() throws IOException;
  }
}
