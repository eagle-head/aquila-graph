package org.aquilagraph.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.aquilagraph.frontend.JavaFrontends;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryObservationTest {
  @TempDir Path directory;

  @Test
  void givenLibraryWithOtlpEndpoint_whenAnalyzingAndClosing_thenAllSignalsAreFlushedAndCorrelateWithJfr() throws Exception {
    // Arrange
    var request = AquilaGraphTest.request(directory);
    var expected = JavaFrontends.standard().produce(request).result().orElseThrow().identity();
    Map<String, String> payloads = new ConcurrentHashMap<>();
    Map<String, String> binary = new ConcurrentHashMap<>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/", exchange -> {
      try (exchange) {
        var bytes = exchange.getRequestBody().readAllBytes();
        var path = exchange.getRequestURI().getPath();
        payloads.merge(path, new String(bytes, StandardCharsets.ISO_8859_1), String::concat);
        binary.merge(path, HexFormat.of().formatHex(bytes), String::concat);
        exchange.getResponseHeaders().set("Content-Type", "application/x-protobuf");
        exchange.sendResponseHeaders(200, -1);
      }
    });
    server.start();
    var configuration = new ObservabilityConfiguration(true, URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
        "library-consumer", "test");
    var destination = directory.resolve("analysis.jfr");
    try (var recording = new Recording()) {
      recording.enable("org.aquilagraph.JavaAnalysisStage").withoutThreshold();
      recording.start();
      // Act
      try (var aquila = AquilaGraph.open(configuration)) {
        var actual = aquila.produceAst(request);
        // Assert
        assertEquals(expected, actual.result().orElseThrow().identity());
      }
      recording.stop();
      recording.dump(destination);
      var events = RecordingFile.readAllEvents(destination).stream()
          .filter(event -> event.getEventType().getName().equals("org.aquilagraph.JavaAnalysisStage")).toList();
      assertEquals(20, events.size());
      var trace = events.getFirst().getString("traceId");
      assertTrue(trace.matches("[0-9a-f]{32}"));
      assertTrue(events.stream().allMatch(event -> event.getString("traceId").equals(trace)));
      assertTrue(payloads.getOrDefault("/v1/traces", "").contains("aquila.analysis.production"));
      assertTrue(payloads.getOrDefault("/v1/metrics", "").contains("aquila.analysis.completed"));
      assertTrue(payloads.getOrDefault("/v1/logs", "").contains("library-consumer"));
      assertTrue(binary.getOrDefault("/v1/traces", "").contains(trace));
      assertTrue(binary.getOrDefault("/v1/logs", "").contains(trace));
    } finally {
      server.stop(0);
    }
  }
}
