package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class AnalyzeCommandAdmissionTest {
  @TestFactory
  Stream<DynamicTest> givenInvalidArgumentPartitions_whenCommandRuns_thenUsageIsReportedBeforeApplicationInvocation() {
    // Arrange
    var cases = List.of(
        new RejectedArguments("MissingRoot", List.of(), "Exactly one source root is required"),
        new RejectedArguments("ExtraRoot", List.of("first", "second"), "Exactly one source root is required"),
        new RejectedArguments("InvalidPlatformPath", List.of("\0"), "Invalid source root: \"\\u0000\""),
        new RejectedArguments("UnknownOption", List.of("source", "--unknown"), "Unknown option: \"--unknown\""),
        new RejectedArguments("MissingRelease", List.of("source", "--release"), "--release requires a Java feature-release number"),
        new RejectedArguments("OptionAsRelease", List.of("source", "--release", "--persist"), "--release requires a Java feature-release number"),
        new RejectedArguments("MalformedRelease", List.of("source", "--release", "latest"), "Invalid Java release: \"latest\""),
        new RejectedArguments("OverflowRelease", List.of("source", "--release", "2147483648"), "Invalid Java release: \"2147483648\""),
        new RejectedArguments("UnsupportedRelease", List.of("source", "--release", "26"), "Unsupported Java release: 26. Supported releases: 25"),
        new RejectedArguments("DuplicateRelease", List.of("source", "--release", "25", "--release", "25"), "--release may be supplied only once"),
        new RejectedArguments("DuplicatePersistence", List.of("source", "--persist", "--persist"), "--persist may be supplied only once"));

    return cases.stream().map(example -> DynamicTest.dynamicTest(
        "given" + example.context() + "_whenCommandRuns_thenUsageIsReportedWithoutInvocation", () -> {
          // Arrange
          var useCase = new CapturingAnalysisUseCase(request -> { throw new AssertionError("Rejected arguments must not invoke analysis"); });
          var command = new AnalyzeCommand(useCase);
          var streams = new CliStreams();

          // Act
          var code = new CommandRunner(command).run(example.tokens().toArray(String[]::new), CommandDestination.printStream(streams.output()), CommandDestination.printStream(streams.error())).exitCode();

          // Assert
          assertEquals(CommandExitPolicy.EXIT_REJECTED, code);
          assertEquals(List.of(), useCase.requests());
          assertEquals("", streams.outputText());
          assertEquals(List.of(example.message(), AnalyzeCommandParser.USAGE), streams.errorLines());
        }));
  }

  @Test
  void givenNullArgumentsOrBorrowedChannel_whenCommandRuns_thenJavaPreconditionFailureEscapesWithoutInvocationOrOutput() {
    // Arrange
    var useCase = new CapturingAnalysisUseCase(request -> { throw new AssertionError("Invalid Java calls must not invoke analysis"); });
    var command = new AnalyzeCommand(useCase);
    var streams = new CliStreams();

    // Act
    var arguments = assertThrows(NullPointerException.class, () -> new CommandRunner(command).run(null, CommandDestination.printStream(streams.output()), CommandDestination.printStream(streams.error())).exitCode());
    var output = assertThrows(NullPointerException.class, () -> new CommandRunner(command).run(new String[] {"source"}, null, CommandDestination.printStream(streams.error())).exitCode());
    var error = assertThrows(NullPointerException.class, () -> new CommandRunner(command).run(new String[] {"source"}, CommandDestination.printStream(streams.output()), null).exitCode());
    var token = assertThrows(NullPointerException.class,
        () -> new CommandRunner(command).run(new String[] {"--unknown", null}, CommandDestination.printStream(streams.output()), CommandDestination.printStream(streams.error())).exitCode());

    // Assert
    assertEquals("arguments", arguments.getMessage());
    assertEquals("output", output.getMessage());
    assertEquals("error", error.getMessage());
    assertEquals("argument", token.getMessage());
    assertEquals(List.of(), useCase.requests());
    assertEquals("", streams.outputText());
    assertEquals("", streams.errorText());
  }

  private record RejectedArguments(String context, List<String> tokens, String message) {}
}
