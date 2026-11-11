package org.aquilagraph.repository.internal.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.aquilagraph.CliPublicationReporting;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.AnalysisPublicationException;
import org.aquilagraph.application.AstLookup;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.AstPublicationRequest;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cli.AnalyzeCommandOutcome;
import org.aquilagraph.cli.CommandDestination;
import org.aquilagraph.cli.CommandExitPolicy;
import org.aquilagraph.cli.CommandInvocation;
import org.aquilagraph.cli.CommandRunner;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;
import org.aquilagraph.repository.neo4j.Neo4jRepositories;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.testsupport.AnalysisExecutionFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.Neo4jException;

@Isolated("Selects process-wide JUL levels")
class HostLoggingTest {
  private static final LoggerLevels HOST_LEVELS = new LoggerLevels(Level.FINE, null, Level.SEVERE);
  private static final Neo4jConnectionSettings SETTINGS =
      new Neo4jConnectionSettings(URI.create("bolt://localhost:8765"), "analysis", "test-password", "analysis-store");
  private static final String COMPLETE = "class Sample { static int run(int value) { return value + 1; } }";

  private final Logger driverLogger = Logger.getLogger("org.neo4j.driver");
  private final Logger connectionLogger = Logger.getLogger("org.neo4j.bolt.connection");
  private final Logger applicationLogger = Logger.getLogger("org.aquilagraph.application");
  private LoggerLevels previousLevels;

  @TempDir Path directory;

  @BeforeEach
  void selectHostLevels() {
    previousLevels = levels();
    setLevels(HOST_LEVELS);
  }

  @AfterEach
  void restoreHostLevels() {
    setLevels(previousLevels);
  }

  @Test
  void givenHostLevelsAndValidSettings_whenDefaultRepositorySaves_thenConnectionAndSessionHonorSettingsAndCallerOwnsDriver()
      throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Sample.java"), COMPLETE);
    var computation = AnalysisCapabilities.computation(JavaFrontends.standard(), CfgDerivers.standard());
    var run = computation.analyze(new AnalysisComputationRequest(source(), Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED))));
    var context = RepositoryOperationContext.saveCompletedRun(run.identity().key());
    var driver = AstRepositoryDriverStub.completedRuns();
    var addresses = new ArrayList<URI>();
    var tokens = new ArrayList<AuthToken>();
    var factory = Neo4jAnalysisRunRepository.factory(() -> SETTINGS, (uri, auth) -> {
      assertHostLevels();
      addresses.add(uri);
      tokens.add(auth);
      return driver.driver();
    });

    // Act
    List<String> effectsBeforeClose;
    try (var repository = factory.open(context)) {
      repository.save(run);
      effectsBeforeClose = List.copyOf(driver.events);
    }

    // Assert
    assertEquals(List.of(SETTINGS.uri()), addresses);
    assertEquals(List.of(AuthTokens.basic("analysis", "test-password")), tokens);
    assertEquals(List.of("analysis-store", "analysis-store"),
        driver.sessionConfigurations.stream().map(SessionConfig::database).map(Optional::orElseThrow).toList());
    assertEquals(run.identity().key(), driver.statements.getFirst().parameters().get("runId").asString());
    assertTrue(effectsBeforeClose.contains("write.acknowledged"));
    assertEquals(0L, effectsBeforeClose.stream().filter("driver.close"::equals).count());
    assertEquals(1L, driver.events.stream().filter("driver.close"::equals).count());
    assertHostLevels();
  }

  @Test
  void givenHostLevelsAndDriverCreationFailure_whenDefaultRepositoryOpens_thenAcquisitionRetainsCauseAndHostLevels() {
    // Arrange
    var context = RepositoryOperationContext.saveCompletedRun("completed-run");
    var signal = new Neo4jException("Controlled driver creation failure");
    var factory = Neo4jAnalysisRunRepository.factory(() -> SETTINGS, (uri, auth) -> {
      assertHostLevels();
      throw signal;
    });

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> factory.open(context));

    // Assert
    assertSame(signal, acquisitionCause(context, failure, "Neo4j driver initialization failed"));
    assertHostLevels();
  }

  @Test
  void givenHostLevelsAndFailingSettings_whenPublicFactoryOpens_thenOnlyValidOperationResolvesSettingsAndRetainsCause()
      throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Sample.java"), COMPLETE);
    var identity = JavaFrontends.standard().produce(source()).result().orElseThrow().identity();
    var foreign = RepositoryOperationContext.queryAst(identity);
    var context = RepositoryOperationContext.saveCompletedRun("completed-run");
    var resolutions = new AtomicInteger();
    var signal = new IllegalArgumentException("Controlled settings failure");

    // Act
    var factory = Neo4jRepositories.completedRuns(() -> {
      resolutions.incrementAndGet();
      assertHostLevels();
      throw signal;
    });
    var afterConstruction = resolutions.get();
    assertThrows(IllegalArgumentException.class, () -> factory.open(foreign));
    var afterWrongKind = resolutions.get();
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> factory.open(context));

    // Assert
    assertEquals(0, afterConstruction);
    assertEquals(0, afterWrongKind);
    assertEquals(1, resolutions.get());
    assertSame(signal, acquisitionCause(context, failure, "Invalid Neo4j repository configuration"));
    assertHostLevels();
  }

  @Test
  void givenHostLevelsAndUriWithoutScheme_whenPublicFactoryOpens_thenDriverRejectsConnectionInputAndHostLevelsRemain() {
    // Arrange
    var context = RepositoryOperationContext.saveCompletedRun("completed-run");
    var factory = Neo4jRepositories.completedRuns(() ->
        new Neo4jConnectionSettings(URI.create("neo4j-host"), SETTINGS.username(), SETTINGS.password(), SETTINGS.database()));

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> factory.open(context));

    // Assert
    var cause = assertInstanceOf(IllegalArgumentException.class,
        acquisitionCause(context, failure, "Invalid Neo4j repository configuration"));
    assertEquals("Scheme must not be null", cause.getMessage());
    assertHostLevels();
  }

  @Test
  void givenHostLevels_whenCliSkipsRefusesAndSavesPublication_thenReportsAndHostLevelsMatchEachOutcome() throws IOException {
    // Arrange
    var complete = Files.createDirectory(directory.resolve("complete"));
    Files.writeString(complete.resolve("Sample.java"), COMPLETE);
    var refused = Files.createDirectory(directory.resolve("refused"));
    Files.writeString(refused.resolve("Sample.java"),
        "interface Sample { static int run(int value) { (value) = 1; return value; } }");
    var resolutions = new AtomicInteger();
    var driver = AstRepositoryDriverStub.completedRuns();
    var runs = Neo4jAnalysisRunRepository.factory(() -> {
      resolutions.incrementAndGet();
      return SETTINGS;
    }, (uri, auth) -> {
      assertHostLevels();
      return driver.driver();
    });

    // Act
    var command = CliPublicationReporting.runner(AnalysisExecutionFixture::unexpectedAstRepository, runs);
    var skipped = invoke(command, complete.toString());
    var rejected = invoke(command, refused.toString(), "--persist");
    var resolutionsBeforeSave = resolutions.get();
    var saved = invoke(command, complete.toString(), "--persist");

    // Assert
    assertEquals(0, resolutionsBeforeSave);
    assertEquals(1, resolutions.get());
    assertCliPublication(skipped, PublicationStatus.NOT_REQUESTED, CommandExitPolicy.EXIT_COMPLETED);
    assertEquals("", skipped.error());
    assertCliPublication(rejected, PublicationStatus.NOT_ELIGIBLE, CommandExitPolicy.EXIT_INCOMPLETE);
    assertTrue(rejected.error().lines().anyMatch("  Severity: ERROR"::equals));
    assertCliPublication(saved, PublicationStatus.SAVED, CommandExitPolicy.EXIT_COMPLETED);
    assertEquals("", saved.error());
    assertTrue(saved.output().lines().anyMatch("Commit knowledge: KNOWN_COMMIT"::equals));
    assertTrue(driver.events.contains("write.acknowledged"));
    assertEquals("driver.close", driver.events.getLast());
  }

  @Test
  void givenHostLevelsAndDriverFailures_whenCliPublicationAborts_thenRealCompositionReportsOrderedPhasesAndRetainsHostLevels()
      throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Sample.java"), COMPLETE);
    var driver = AstRepositoryDriverStub.completedRuns();
    var primary = new IllegalStateException("Managed write failure");
    driver.writeFailure = primary;
    driver.failingClose = 1;
    driver.closeFailure = new IllegalStateException("Publication session closure failure");
    driver.driverCloseFailure = new IllegalStateException("Driver closure failure");
    var runs = Neo4jAnalysisRunRepository.factory(() -> SETTINGS, (uri, auth) -> {
      assertHostLevels();
      return driver.driver();
    });
    var command = CliPublicationReporting.runner(AnalysisExecutionFixture::unexpectedAstRepository, runs);
    var output = new StringWriter();
    var error = new StringWriter();

    // Act
    var failure = assertThrows(AnalysisPublicationException.class, () -> command.run(new String[] {directory.toString(), "--persist"},
        CommandDestination.throwingWriter(output), CommandDestination.throwingWriter(error)));

    // Assert
    assertSame(primary, failure.getCause());
    assertEquals("", output.toString());
    var diagnostics = error.toString().lines().toList();
    assertTrue(diagnostics.getFirst().startsWith("Publication aborted: "));
    assertTrue(diagnostics.contains("  Message: \"Managed write failure\""));
    assertTrue(diagnostics.contains("  Commit knowledge: UNKNOWN"));
    assertEquals(List.of("  Adapter phase: \"REPLACEMENT\"", "  Adapter phase: \"PUBLICATION_SESSION_CLOSE\"",
        "  Adapter phase: \"REPOSITORY_CLOSE\""), diagnostics.stream().filter(line -> line.startsWith("  Adapter phase:")).toList());
    assertEquals("driver.close", driver.events.getLast());
    assertHostLevels();
  }

  @Test
  void givenHostLevelsAndCompleteSource_whenApplicationRetainsAndQueriesAst_thenBothCapabilitiesPreserveHostLevels()
      throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Sample.java"), COMPLETE);
    var ast = JavaFrontends.standard().produce(source());
    var identity = ast.result().orElseThrow().identity();
    var write = new AstRepositoryDriverStub();
    write.lockedAstSubject = identity.astResultKey();
    var read = new AstRepositoryDriverStub();
    var retention = new Neo4jAstResultRepositoryFactory(() -> SETTINGS, (uri, auth) -> {
      assertHostLevels();
      return write.driver();
    });
    var queries = new Neo4jAstResultRepositoryFactory(() -> SETTINGS, (uri, auth) -> {
      assertHostLevels();
      return read.driver();
    });

    // Act
    var saved = AnalysisCapabilities.astRetention(retention::openRetention)
        .retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
    var afterRetention = levels();
    var absent = AnalysisCapabilities.astQuery(queries::openQuery).findAst(identity);

    // Assert
    assertEquals(PublicationStatus.SAVED, saved.status());
    assertEquals(CommitKnowledge.KNOWN_COMMIT, saved.knowledge());
    assertEquals(RepositoryOperationContext.saveAst(identity).subject(), saved.subjectIdentity().orElseThrow());
    assertEquals(new AstLookup(identity, AstLookupStatus.NOT_FOUND, Optional.empty(), Optional.empty()), absent);
    assertEquals("driver.close", write.events.getLast());
    assertEquals("driver.close", read.events.getLast());
    assertEquals(HOST_LEVELS, afterRetention);
    assertHostLevels();
  }

  private AstProductionRequest source() {
    return new AstProductionRequest(directory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED);
  }

  private CommandObservation invoke(CommandRunner command, String... arguments) {
    var output = new StringWriter();
    var error = new StringWriter();
    var invocation = command.run(arguments, CommandDestination.throwingWriter(output), CommandDestination.throwingWriter(error));
    return new CommandObservation(invocation, output.toString(), error.toString(), levels());
  }

  private static void assertCliPublication(CommandObservation observation, PublicationStatus status, int exitCode) {
    var invocation = observation.invocation();
    var execution = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, invocation.outcome()).execution();
    assertEquals(status, execution.publication().status());
    assertEquals(exitCode, invocation.exitCode());
    assertTrue(observation.output().lines().anyMatch(("Repository: " + status)::equals));
    assertEquals(HOST_LEVELS, observation.levels());
  }

  private static Throwable acquisitionCause(RepositoryOperationContext context, Neo4jRepositoryOperationException failure,
      String description) {
    assertEquals(context, failure.context());
    assertEquals(Neo4jRepositoryPhase.ACQUISITION, failure.phase());
    assertEquals(Optional.of(CommitKnowledge.NOT_STARTED), failure.knowledge());
    var primary = assertInstanceOf(RepositoryException.class, failure.effectivePrimary());
    assertEquals(description, primary.description());
    return primary.getCause();
  }

  private void assertHostLevels() {
    assertEquals(HOST_LEVELS, levels());
  }

  private LoggerLevels levels() {
    return new LoggerLevels(driverLogger.getLevel(), connectionLogger.getLevel(), applicationLogger.getLevel());
  }

  private void setLevels(LoggerLevels levels) {
    driverLogger.setLevel(levels.driver());
    connectionLogger.setLevel(levels.connection());
    applicationLogger.setLevel(levels.application());
  }

  private record LoggerLevels(Level driver, Level connection, Level application) {}

  private record CommandObservation(CommandInvocation invocation, String output, String error, LoggerLevels levels) {}
}
