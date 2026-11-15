package org.aquilagraph.repository.internal.neo4j;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.AstPublicationRequest;
import org.aquilagraph.application.AstQuery;
import org.aquilagraph.application.AstRetention;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;
import org.aquilagraph.repository.neo4j.Neo4jRepositories;
import org.aquilagraph.retention.consumer.PublicAstObservation;
import org.aquilagraph.retention.consumer.RetainedAstQueryProcess;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.net.URISyntaxException;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.testsupport.AnalysisExecutionFixture;
import org.jspecify.annotations.NullMarked;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.File;
import java.util.jar.JarOutputStream;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.DependencySemantics;
import org.aquilagraph.identity.api.ModuleCandidate;
import org.aquilagraph.identity.api.ModuleNameOrigin;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.internal.AstResultAdmission;
import org.aquilagraph.source.api.AnnotationScalar;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.aquilagraph.testsupport.Neo4jIntegration;
import org.aquilagraph.testsupport.SourceFidelityFixture;
import org.aquilagraph.testsupport.LiteralLabelFixture;
import org.aquilagraph.testsupport.RetiredSourceFixture;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;

@ExtendWith(Neo4jIntegration.class)
@Tag("persistence")
class Neo4jDeclarationAstRetentionNeo4jIT {
  @TempDir Path directory;

  @Test
  void givenRetiredAndCurrentModuleResults_whenReplacingAndRemovingDependencies_thenIndependentQueriesPreserveCurrentAndRefuseRetired() throws Exception {
    // Arrange
    var dependency = directory.resolve("alpha.jar");
    var manifest = new Manifest();
    manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
    try (var output = new JarOutputStream(Files.newOutputStream(dependency), manifest)) {
      output.putNextEntry(new JarEntry("content.txt"));
      output.write(new byte[] {1, 2, 3});
      output.closeEntry();
    }
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(capturedAbsolute(dependency)), List.of());
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(
        SourceFile.fromContent(capturedPath(Path.of("module-info.java")), "module consumer.sample { requires alpha; }"),
        SourceFile.fromContent(capturedPath(Path.of("sample/Example.java")), "package sample; public class Example { public int value() { return 7; } }")));
    var settings = settings();
    var runner = AnalysisCapabilities.computation(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    AnalysisRunRepositoryFactory completed = Neo4jRepositories.completedRuns(() -> settings);
    var application = AnalysisCapabilities.execution(runner, Neo4jRepositories.astRetention(() -> settings), completed);
    var request = new AnalysisRequest(new AnalysisComputationRequest(
        new AstProductionRequest(snapshot, configuration, AstProfile.JAVA_SOURCE_MODULE_SERVICES), Optional.empty()), PublicationIntent.SAVE_COMPLETE_AST);
    var historical = HistoricalAstFixtures.read(20);
    var execution = application.execute(request);
    var outcome = execution.run().representation().ast();
    var original = outcome.result().orElseThrow();
    var completedRequest = new AnalysisRequest(new AnalysisComputationRequest(
        new AstProductionRequest(snapshot, configuration, AstProfile.JAVA_SOURCE_MODULE_SERVICES), Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED))), PublicationIntent.SAVE_COMPLETED);
    var run = runner.analyze(completedRequest.computation());
    try {
      // Act
      var firstRead = query(settings).findAst(original.identity());
      storeRetiredRoot(settings, historical);
      var runSave = AnalysisExecutionFixture.executeComputed(run, PublicationIntent.SAVE_COMPLETED,
          AnalysisExecutionFixture::unexpectedAstRepository, completed).publication();
      assertEquals(PublicationStatus.SAVED, runSave.status(), run.diagnostics().values().toString());
      var replacement = retention(settings).retainAst(new AstPublicationRequest(outcome, PublicationIntent.SAVE_COMPLETE_AST));
      assertEquals(PublicationStatus.SAVED, replacement.status());
      withSession(settings, session -> {
        var stored = session.run("MATCH (run:AnalysisRun {runId: $runId})-[:ANALYZED_SOFTWARE]->"
            + "(:SoftwareVersion {runId: $runId})-[:HAS_ENVIRONMENT]->"
            + "(environment:AnalysisEnvironmentSnapshot {runId: $runId}) "
            + "RETURN run.completion AS completion, environment.environmentDigest AS digest",
            Values.parameters("runId", run.identity().key())).single();
        assertEquals("COMPLETE", stored.get("completion").asString());
        assertEquals(original.environment().environmentDigest(), stored.get("digest").asString());
      });
      var runReplacement = AnalysisExecutionFixture.executeComputed(run, PublicationIntent.SAVE_COMPLETED,
          AnalysisExecutionFixture::unexpectedAstRepository, completed).publication();
      Files.delete(dependency);
      var retained = queryRetainedInIndependentProcess(settings, outcome, Optional.empty(), List.of(run.identity().key()));
      assertIndependentRetiredRefusal(historical);

      // Assert
      assertEquals(PublicationStatus.SAVED, execution.publication().status());
      assertEquals(AstLookupStatus.FOUND, firstRead.status());
      assertEquals(original.environment(), firstRead.outcome().orElseThrow().result().orElseThrow().environment());
      assertEquals(PublicationStatus.SAVED, runReplacement.status());
      assertEquals(List.of(new ModuleCandidate(".", "alpha", Optional.empty(), ModuleNameOrigin.FILENAME)),
          retained.environment().modulePath().getFirst().semantics().modules());
      assertEquals(DependencySemantics.State.MODULES, retained.environment().modulePath().getFirst().semantics().state());
      assertRetiredRootRefused(settings, historical);
      assertNotEquals(original.identity(), historical.identity());
      assertNotEquals(run.identity().key(), original.identity().softwareSignature());
      withSession(settings, session -> {
        var environment = session.run("MATCH (environment:AnalysisEnvironmentSnapshot {runId: $runId}) RETURN environment",
            Values.parameters("runId", run.identity().key())).single().get("environment").asNode();
        assertEquals("analysis-environment/2", environment.get("identityScheme").asString());
        assertEquals(original.environment().environmentDigest(), environment.get("environmentDigest").asString());
        var stored = session.run("MATCH (entry:DependencySnapshot {runId: $runId}) RETURN entry", Values.parameters("runId", run.identity().key()))
            .single().get("entry").asNode();
        assertEquals("MODULES", stored.get("interpretation").asString());
        assertEquals(List.of("alpha"), stored.get("moduleLookupNames").asList(value -> value.asString()));
        assertEquals(List.of("."), stored.get("moduleMembers").asList(value -> value.asString()));
        assertEquals(List.of(""), stored.get("moduleDescriptorNames").asList(value -> value.asString()));
        assertEquals(List.of("FILENAME"), stored.get("moduleNameOrigins").asList(value -> value.asString()));
        assertEquals(dependency.toString(), stored.get("location").asString());
        assertEquals(0, stored.get("position").asInt());
        assertEquals(original.environment().modulePath().getFirst().fingerprint(), stored.get("fingerprint").asString());
        assertEquals(original.environment().modulePath().getFirst().byteCount(), stored.get("byteCount").asLong());
        assertEquals(1, stored.get("entryCount").asInt());
      });
    } finally {
      delete(settings, original.identity());
      delete(settings, historical.identity());
      withSession(settings, session -> session.run("MATCH (entity:AnalysisEntity {runId: $runId}) DETACH DELETE entity",
          Values.parameters("runId", run.identity().key())).consume());
    }
  }

  @Test
  void givenModuleServicesAndAutomaticDependency_whenApplicationRetainsAndCompilerFreeConsumerQueries_thenNamesTargetsAndSourceRemainExact() throws Exception {
    // Arrange
    var alpha = directory.resolve("alpha.jar");
    var beta = directory.resolve("beta.jar");
    var manifest = new Manifest();
    manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
    try (var output = new JarOutputStream(Files.newOutputStream(alpha), manifest)) {
      output.putNextEntry(new JarEntry("content.txt"));
      output.write(new byte[] {1, 2, 3});
      output.closeEntry();
    }
    Files.copy(alpha, beta);
    var alphaBytes = Files.readAllBytes(alpha);
    var betaBytes = Files.readAllBytes(beta);
    var matchingConfiguration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(capturedAbsolute(alpha)), List.of());
    var nonmatchingConfiguration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(capturedAbsolute(beta)), List.of());
    var descriptor = "import api.Service; import impl.AlphaProvider; module service.integrated { requires alpha; uses api.Service; provides Service with impl.ZuluFactory, AlphaProvider; }";
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(
        SourceFile.fromContent(capturedPath(Path.of("module-info.java")), descriptor),
        SourceFile.fromContent(capturedPath(Path.of("api/Service.java")), "package api; public interface Service { int value(); }"),
        SourceFile.fromContent(capturedPath(Path.of("api/Mark.java")), "package api; public @interface Mark { Class<?> type() default String.class; int count() default 3; }"),
        SourceFile.fromContent(capturedPath(Path.of("impl/Product.java")), "package impl; public class Product implements api.Service { public int value() { return 11; } }"),
        SourceFile.fromContent(capturedPath(Path.of("impl/ZuluFactory.java")), "package impl; public final class ZuluFactory { private ZuluFactory() {} public static Product provider() { return new Product(); } }"),
        SourceFile.fromContent(capturedPath(Path.of("impl/AlphaProvider.java")), "package impl; @api.Mark public class AlphaProvider implements api.Service { public int value() { return 7; } }"),
        SourceFile.fromContent(capturedPath(Path.of("model/Payload.java")), "package model; @api.Mark public class Payload<T extends Comparable<T>> { public T echo(T value) { return value; } public int supported() { return 9; } public Runnable lambda() { return () -> {}; } public Object boxed() { return 4; } public Class<?> literal() { return String.class; } }")));
    var settings = settings();
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var application = AnalysisCapabilities.execution(AnalysisCapabilities.computation(frontend, CfgDerivers.standard()),
        Neo4jRepositories.astRetention(() -> settings), AnalysisExecutionFixture::unexpectedRunRepository);
    var request = new AnalysisRequest(new AnalysisComputationRequest(
        new AstProductionRequest(snapshot, matchingConfiguration, AstProfile.JAVA_SOURCE_MODULE_SERVICES), Optional.empty()), PublicationIntent.SAVE_COMPLETE_AST);

    // Act
    var direct = frontend.produce(new AstProductionRequest(new SourceInput.Snapshot(snapshot), matchingConfiguration,
        AstProfile.JAVA_SOURCE_MODULE_SERVICES));
    var execution = application.execute(request);
    var outcome = execution.run().representation().ast();
    var original = outcome.result().orElseThrow();
    try {
      var nonmatching = application.execute(new AnalysisRequest(new AnalysisComputationRequest(
          new AstProductionRequest(snapshot, nonmatchingConfiguration, AstProfile.JAVA_SOURCE_MODULE_SERVICES), Optional.empty()), PublicationIntent.SAVE_COMPLETE_AST));
      var nonmatchingAst = nonmatching.run().representation().ast();
      var nonmatchingEnvironment = nonmatchingAst.capturedEnvironment().orElseThrow();
      Files.delete(alpha);
      Files.delete(beta);
      var retained = queryRetainedInIndependentProcess(settings, outcome, Optional.of(expectedModuleOperands(snapshot, "service.integrated",
          List.of("api.Service", "Service"),
          List.of("impl.ZuluFactory", "AlphaProvider"), List.of("impl.ZuluFactory", "impl.AlphaProvider"))));
      var supported = retained.program().methods().stream().filter(method -> method.name().equals("supported")).findFirst().orElseThrow().methodKey();
      var lambda = retained.program().methods().stream().filter(method -> method.name().equals("lambda")).findFirst().orElseThrow().methodKey();
      var graphs = CfgDerivers.standard().derive(retained,
          new CfgRequest(retained.identity(), List.of(supported, lambda), CfgProfile.JAVA_BOUNDED));

      // Assert
      assertArrayEquals(alphaBytes, betaBytes);
      assertEquals(direct.result().orElseThrow().identity(), original.identity());
      assertEquals(snapshot, retained.snapshot());
      assertEquals(snapshot, nonmatchingAst.capturedInput().orElseThrow());
      assertEquals(List.of(), retained.diagnostics());
      assertEquals(original.environment(), retained.environment());
      assertEquals(List.of(new ModuleCandidate(".", "alpha", Optional.empty(), ModuleNameOrigin.FILENAME)),
          retained.environment().modulePath().getFirst().semantics().modules());
      assertEquals(List.of(new ModuleCandidate(".", "beta", Optional.empty(), ModuleNameOrigin.FILENAME)),
          nonmatchingEnvironment.modulePath().getFirst().semantics().modules());
      assertEquals(retained.environment().modulePath().getFirst().fingerprint(), nonmatchingEnvironment.modulePath().getFirst().fingerprint());
      assertNotEquals(retained.environment().environmentDigest(), nonmatchingEnvironment.environmentDigest());
      assertNotEquals(retained.identity().softwareSignature(), SoftwareVersion.from(snapshot, nonmatchingEnvironment).signature());
      assertNotEquals(AstCompletion.COMPLETE, nonmatchingAst.completion());
      assertTrue(nonmatchingAst.diagnostics().stream().anyMatch(diagnostic -> diagnostic.message().contains("alpha")));
      assertEquals(PublicationStatus.NOT_ELIGIBLE, nonmatching.publication().status());
      assertNotEquals(execution.run().identity().key(), retained.identity().softwareSignature());
      assertEquals(PublicationStatus.SAVED, execution.publication().status());
      assertTrue(execution.run().representation().cfg().isEmpty());
      assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
      assertEquals("java-source-expanded/8", retained.profile().version());
      assertEquals("javac-public/14", retained.identity().semanticProductionRevision());
      assertEquals("java-source/23", retained.identity().sourceModelVersion());
      DeclarationAstRetentionFixture.assertPreserved(original, retained);
      assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.UNSUPPORTED), graphs.methods().stream().map(method -> method.state()).toList());
      assertEquals(2, retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION).count());
      var parameter = retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_PARAMETER_DECLARATION).findFirst().orElseThrow();
      assertEquals("T", parameter.value(SyntaxValue.TypeParameterDeclaration.class).name());
      var factory = retained.program().types().stream().filter(type -> type.qualifiedName().equals("impl.ZuluFactory")).findFirst().orElseThrow();
      assertTrue(factory.directInterfaces().isEmpty());
      assertTrue(retained.program().methods().stream().anyMatch(method -> method.name().equals("boxed")));
      assertTrue(retained.program().methods().stream().anyMatch(method -> method.name().equals("literal")));
    } finally {
      delete(settings, original.identity());
    }
  }

  @Test
  void givenCurrentModuleEndpointsAndRetiredEnvelope_whenReplacedAndQueriedWithoutCompiler_thenCurrentTargetsAndRetiredRefusalSurvive() throws Exception {
    // Arrange
    var historical = HistoricalAstFixtures.read(19);
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var providerOrder = List.of("impl.ZuluProvider", "impl.AlphaProvider");
    var reverseOrder = providerOrder.reversed();
    var first = produceModuleEndpoints(frontend, "service.sample", providerOrder);
    var second = produceModuleEndpoints(frontend, "service.alternate", providerOrder);
    var reordered = produceModuleEndpoints(frontend, "service.sample", reverseOrder);
    var subjects = List.of(first, second, reordered);
    assertNotEquals(first.result().orElseThrow().identity().canonicalContentDigest(), reordered.result().orElseThrow().identity().canonicalContentDigest());
    var settings = settings();
    try {
      storeRetiredRoot(settings, historical);
      for (var subject : subjects) {
        assertEquals(AstCompletion.COMPLETE, subject.completion(), subject.diagnostics().toString());
        assertEquals(PublicationStatus.SAVED, retention(settings).retainAst(new AstPublicationRequest(subject,
            PublicationIntent.SAVE_COMPLETE_AST)).status());
      }
      // Act
      var replaced = retention(settings).retainAst(new AstPublicationRequest(first, PublicationIntent.SAVE_COMPLETE_AST));
      var lookups = subjects.stream().map(subject -> query(settings).findAst(subject.result().orElseThrow().identity())).toList();
      assertIndependentRetiredRefusal(historical);
      // Assert
      assertEquals(PublicationStatus.SAVED, replaced.status());
      assertRetiredRootRefused(settings, historical);
      for (var index = 0; index < subjects.size(); index++) {
        var original = subjects.get(index).result().orElseThrow();
        assertEquals(AstLookupStatus.FOUND, lookups.get(index).status());
        DeclarationAstRetentionFixture.assertPreserved(original, lookups.get(index).outcome().orElseThrow().result().orElseThrow());
        assertMaterialization(settings, subjects.get(index));
        var module = index == 1 ? "service.alternate" : "service.sample";
        var expected = expectedModuleOperands(original.snapshot(), module, index == 2 ? reverseOrder : providerOrder);
        var retained = independentQuery(settings, subjects.get(index), Optional.of(expected));
        assertEquals(original.identity(), retained.identity());
      }
    } finally {
      for (var subject : subjects) { delete(settings, subject.result().orElseThrow().identity()); }
      delete(settings, historical.identity());
    }
  }

  private AstOutcome produceModuleEndpoints(JavaFrontend frontend, String module, List<String> providerOrder) {
    var descriptor = "import api.Service; module " + module + " { uses Service; provides api.Service with "
        + String.join(", ", providerOrder) + "; }";
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(
        SourceFile.fromContent(capturedPath(Path.of("module-info.java")), descriptor),
        SourceFile.fromContent(capturedPath(Path.of("api/Service.java")), "package api; public interface Service {}"),
        SourceFile.fromContent(capturedPath(Path.of("impl/AlphaProvider.java")),
            "package impl; public final class AlphaProvider implements api.Service {}"),
        SourceFile.fromContent(capturedPath(Path.of("impl/ZuluProvider.java")),
            "package impl; public final class ZuluProvider implements api.Service {}")));
    return frontend.produce(new AstProductionRequest(snapshot, DeclarationAstRetentionFixture.configuration(),
        AstProfile.JAVA_SOURCE_MODULE_SERVICES));
  }

  private static List<String> expectedModuleOperands(InputSnapshot snapshot, String module, List<String> providers) {
    return expectedModuleOperands(snapshot, module, List.of("Service", "api.Service"), providers, providers);
  }

  private static List<String> expectedModuleOperands(InputSnapshot snapshot, String module, List<String> services, List<String> providers, List<String> providerBinaries) {
    var descriptor = snapshot.sourceFiles().stream().filter(source -> source.canonicalRelativePath().equals("module-info.java")).findFirst().orElseThrow().content();
    var spellings = new ArrayList<>(services);
    spellings.addAll(providers);
    var binaries = new ArrayList<>(List.of("api.Service", "api.Service"));
    binaries.addAll(providerBinaries);
    var result = new ArrayList<String>();
    var cursor = descriptor.indexOf("uses ") + "uses ".length();
    for (var index = 0; index < spellings.size(); index++) {
      var start = descriptor.indexOf(spellings.get(index), cursor);
      assertTrue(start >= cursor);
      var role = index < 2 ? "SERVICE_TYPE" : "PROVIDER_TYPE";
      result.add(role + "\t" + (index < 2 ? 0 : index - 2) + "\t" + spellings.get(index) + "\t" + module + "\t" + binaries.get(index)
          + "\tRESOLVED\t" + start + "\t" + (start + spellings.get(index).length()));
      cursor = start + spellings.get(index).length();
    }
    return List.copyOf(result);
  }

  @Test
  void givenCurrentAndRetiredLiteralLabels_whenQueriedWithoutCompiler_thenCurrentLabelsSurviveAndRetiredEnvelopeRefuses() throws Exception {
    // Arrange
    var historical = LiteralLabelFixture.historical(AstProfile.JAVA_SOURCE_FIDELITY);
    var settings = settings();

    try {
      storeRetiredRoot(settings, historical);
      assertIndependentRetiredRefusal(historical);
      assertRetiredRootRefused(settings, historical);
    } finally {
      delete(settings, historical.identity());
    }
    for (var profile : List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_FIDELITY)) {
      var original = new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(
          new SourceInput.Snapshot(LiteralLabelFixture.snapshot(directory)), DeclarationAstRetentionFixture.configuration(),
          profile)).result().orElseThrow();
      // Act
      var retained = independentQuery(settings, AstOutcome.available(original));
      var methods = List.of("negative", "text").stream().map(name -> retained.program().methods().stream()
          .filter(method -> method.name().equals(name)).findFirst().orElseThrow().methodKey()).toList();
      var derived = CfgDerivers.standard().derive(retained, new CfgRequest(retained.identity(), methods, CfgProfile.JAVA_BOUNDED));

      // Assert
      assertEquals(AstCompletion.COMPLETE, retained.completion());
      DeclarationAstRetentionFixture.assertPreserved(original, retained);
      assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.AVAILABLE), derived.methods().stream().map(method -> method.state()).toList());
      LiteralLabelFixture.assertLabels(derived, retained);
    }
  }

  @Test
  void givenCombinedSourceCorrections_whenSavedAndQueriedWithoutCompiler_thenExactIntegratedFactsSurvive() throws Exception {
    // Arrange
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(
        new SourceInput.Snapshot(SourceFidelityFixture.snapshot(directory)), DeclarationAstRetentionFixture.configuration(),
        AstProfile.JAVA_SOURCE_FIDELITY));

    // Act
    var retained = independentQuery(settings(), ast);

    // Assert
    SourceFidelityFixture.assertFaithful(retained);
    DeclarationAstRetentionFixture.assertPreserved(ast.result().orElseThrow(), retained);
  }

  @Test
  void givenNestedSameStartRegions_whenRepeatedProductionIsRetainedAndQueriedWithoutCompiler_thenOneCanonicalIdentitySurvives() throws Exception {
    // Arrange
    var source = "class Example { String value() { return -1 * 2 + \"a\" + \"b\"; } }";
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
    var request = new AstProductionRequest(new SourceInput.Snapshot(snapshot), DeclarationAstRetentionFixture.configuration(),
        AstProfile.JAVA_SOURCE_FIDELITY);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var first = frontend.produce(request);
    var repeated = frontend.produce(request).result().orElseThrow();
    var retained = independentQuery(settings(), first);

    // Assert
    DeclarationAstRetentionFixture.assertPreserved(first.result().orElseThrow(), retained);
    DeclarationAstRetentionFixture.assertPreserved(repeated, retained);
    assertEquals(AstCompletion.COMPLETE, retained.completion());
    assertEquals(List.of("-1", "-1 * 2 + \"a\" + \"b\""), retained.coverage().validationEvidence().writtenInventory()
        .writtenExpressions().orElseThrow().stream()
        .map(region -> source.substring((int) region.origin().startOffset(), (int) region.origin().endOffsetExclusive())).toList());
    assertEquals(repeated.identity(), retained.identity());
    assertArrayEquals(AstCodec.encode(repeated), AstCodec.encode(retained));
  }

  @Test
  void givenWrittenRegionsAndRestrictedMinima_whenRetainedAndQueriedWithoutCompiler_thenTopologyAndEvidenceSurvive() throws Exception {
    // Arrange
    var source = """
        @interface Mark { String text() default "a" + "b"; int number() default -2147483648; }
        @Mark(text="c" + "d", number=-1) class Example {
          long field = -9223372036854775808L;
          String subject(String input) { return 1 + 2 + "e" + "f" + input; }
        }
        """;
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(new SourceInput.Snapshot(snapshot),
        DeclarationAstRetentionFixture.configuration(), AstProfile.JAVA_SOURCE_FIDELITY));
    var original = ast.result().orElseThrow();

    // Act
    var retained = independentQuery(settings(), ast);

    // Assert
    DeclarationAstRetentionFixture.assertPreserved(original, retained);
    assertEquals(AstProfile.JAVA_SOURCE_FIDELITY, retained.profile());
    assertEquals("java-source/23", retained.identity().sourceModelVersion());
    assertEquals("javac-public/14", retained.identity().semanticProductionRevision());
    assertEquals(6, retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.BINARY).count());
    assertEquals(3, retained.aggregate().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.UNARY_MINUS).count());
    assertEquals(2, retained.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.Literal literal
        && literal.restrictedDecimal().isPresent()).count());
    assertTrue(retained.coverage().validationEvidence().writtenInventory().writtenExpressions().orElseThrow().size() >= 5);
    assertTrue(retained.aggregate().writtenSourceAssociations().stream().anyMatch(value -> value.normalizedExpression().isPresent()));
  }

  @Test
  void givenRepeatedAnnotationsAndExplicitNestedContainers_whenRetainedAndQueriedWithoutCompiler_thenOccurrencesAndValuesSurvive()
      throws Exception {
    // Arrange
    var ast = DeclarationAstRetentionFixture.produce(directory, "Tags.java", """
        import java.lang.annotation.Repeatable;
        @Repeatable(Tags.class) @interface Tag { int value(); }
        @Repeatable(Groups.class) @interface Tags { Tag[] value(); }
        @interface Groups { Tags[] value(); }
        @Tag(7) @Tag(7) @Tag(9) class Repeated { int subject() { return 1; } }
        @Tags({@Tag(11), @Tag(13)}) @Tags({@Tag(17)}) class Containers {}
        @Groups({@Tags({@Tag(19)})}) class Explicit {}
        """);

    var current = ast.result().orElseThrow();
    var preceding = AstResultAdmission.admit(new SyntaxAnalysisInput(current.snapshot(), current.environment(), current.program(),
        Optional.of(current.aggregate()), current.diagnostics(), current.coverage()), current.profile(), "javac-public/7");

    // Act
    var retained = independentQuery(settings(), ast);
    var prior = independentQuery(settings(), AstOutcome.available(preceding));

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion());
    assertEquals("javac-public/14", retained.identity().semanticProductionRevision());
    assertEquals(ast.result().orElseThrow().identity(), retained.identity());
    assertEquals(ast.result().orElseThrow().aggregate(), retained.aggregate());
    assertEquals(preceding.identity(), prior.identity());
    assertEquals("javac-public/7", prior.identity().semanticProductionRevision());
    assertArrayEquals(AstCodec.encode(preceding), AstCodec.encode(prior));
    var repeated = retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
        && node.value(SyntaxValue.TypeDeclaration.class).name().equals("Repeated")).findFirst().orElseThrow();
    var annotations = repeated.children(SyntaxRole.ANNOTATION);
    assertEquals(3, annotations.size());
    assertEquals(3, annotations.stream().map(node -> node.nodeId()).distinct().count());
    assertEquals(List.of(new AnnotationScalar.IntValue(7), new AnnotationScalar.IntValue(7), new AnnotationScalar.IntValue(9)),
        annotations.stream().map(node -> node.requiredChild(SyntaxRole.ANNOTATION_ARGUMENT)
            .value(SyntaxValue.AnnotationArgument.class).value()).toList());
    assertEquals(List.of(7, 7, 9, 11, 13, 17, 19), retained.aggregate().nodes().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION_ARGUMENT
            && node.value(SyntaxValue.AnnotationArgument.class).value() instanceof AnnotationScalar.IntValue)
        .map(node -> ((AnnotationScalar.IntValue) node.value(SyntaxValue.AnnotationArgument.class).value()).value()).toList());
    assertTrue(retained.diagnostics().isEmpty(), retained.diagnostics().toString());
  }

  @Test
  void givenCompleteNonSealedHierarchy_whenExplicitlyRetainedAndQueriedWithoutCompiler_thenCorrectedPropertiesAndIdentitySurvive()
      throws Exception {
    // Arrange
    var settings = settings();
    var ast = DeclarationAstRetentionFixture.produce(directory, "Hierarchy.java", """
        sealed class Base permits Open {}
        non-sealed abstract class Open extends Base { int subject() { return 7; } }
        sealed interface Contract permits Extension {}
        non-sealed interface Extension extends Contract {}
        """);

    // Act
    var retained = independentQuery(settings, ast);

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion());
    assertEquals("javac-public/14", retained.identity().semanticProductionRevision());
    assertEquals("java-source/23", retained.identity().sourceModelVersion());
    var modifiers = retained.coverage().validationEvidence().outcomes().stream()
        .filter(value -> value.address().role().equals("TypeElement::Element#getModifiers()"))
        .flatMap(value -> value.encounteredValue().stream()).flatMap(value -> value.items().stream())
        .filter(value -> value.value().equals("non-sealed")).toList();
    assertEquals(2, modifiers.size());
    for (var item : modifiers) {
      assertEquals("non-sealed", item.identity());
      assertEquals(CoveragePropertyValue.Provenance.COMPILER_DERIVED, item.provenance());
    }
    assertEquals(List.of("Open"), retained.program().types().stream().filter(type -> type.qualifiedName().equals("Open"))
        .map(type -> type.qualifiedName()).toList());
    assertTrue(retained.diagnostics().isEmpty(), retained.diagnostics().toString());
  }

  @Test
  void givenQualifiedAnonymousCreation_whenSavedClosedAndQueried_thenInstantiatedConstructorTargetsRemainExact() throws IOException {
    // Arrange
    var settings = settings();
    var ast = DeclarationAstRetentionFixture.produce(directory, "Example.java",
        "class Outer<T> { class Inner { Inner(T value) {} } } class Example { "
            + "Outer<String> owner = new Outer<>(); Object value = owner.new Inner(\"x\") { String text = \"retained\"; }; }");
    var original = ast.result().orElseThrow();
    try {
      // Act
      var publication = retention(settings).retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(original.identity());
      var retained = lookup.outcome().orElseThrow().result().orElseThrow();

      // Assert
      assertEquals(AstCompletion.COMPLETE, original.completion());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(CommitKnowledge.KNOWN_COMMIT, publication.knowledge());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      DeclarationAstRetentionFixture.assertPreserved(original, retained);
      var creation = retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION
          && !node.children(SyntaxRole.CLASS_BODY).isEmpty()).findFirst().orElseThrow();
      var evidence = retained.aggregate().executionScopes().stream()
          .flatMap(scope -> retained.aggregate().executionSyntax(scope.key()).orElseThrow()
              .useEvidence(creation.nodeId(), SyntaxRole.ARGUMENT, 0).stream()).toList();
      assertEquals(1, evidence.size());
      assertEquals("java.lang.String", evidence.getFirst().selectedTarget().displayName());
      var properties = retained.coverage().validationEvidence().outcomes().stream()
          .filter(outcome -> outcome.address().partition() == SourceContext.Partition.WRITTEN_PROPERTY
              && outcome.address().role().startsWith("NewClassTree::")
              && outcome.occurrence().orElseThrow().origins().equals(List.of(creation.origin().orElseThrow())))
          .collect(Collectors.toMap(outcome -> outcome.address().role(), outcome -> outcome));
      var expected = Map.of("NewClassTree::Tree#getKind()", List.of("NEW_CLASS"),
          "NewClassTree::NewClassTree#getEnclosingExpression()", List.of("owner"),
          "NewClassTree::NewClassTree#getIdentifier()", List.of("Inner"),
          "NewClassTree::NewClassTree#getTypeArguments()", List.<String>of(),
          "NewClassTree::NewClassTree#getArguments()", List.of("\"x\""),
          "NewClassTree::NewClassTree#getClassBody()", List.of("{ String text = \"retained\"; }"));
      assertEquals(expected.keySet(), properties.keySet());
      expected.forEach((role, values) -> {
        var outcome = properties.get(role);
        var property = outcome.encounteredValue().orElseThrow();
        assertEquals(values, property.items().stream().map(CoveragePropertyValue.Item::value).toList());
        assertEquals(values.isEmpty() ? CoveragePropertyValue.State.EMPTY
            : role.endsWith("#getArguments()") ? CoveragePropertyValue.State.ITEMS : CoveragePropertyValue.State.SCALAR, property.state());
        assertEquals(values.isEmpty() ? FeatureContextOutcome.Mode.NEGATIVE : FeatureContextOutcome.Mode.FACTS, outcome.support().mode());
      });
      assertMaterialization(settings, ast);
    } finally {
      delete(settings, original.identity());
    }
    assertEquals(AstLookupStatus.NOT_FOUND, query(settings).findAst(original.identity()).status());
  }

  @Test
  void givenSavedMixedDeclarations_whenQueriedInAnIndependentProcessWithoutCompilerModules_thenClosedSourceBytesRemainExact() throws Exception {
    // Arrange
    var settings = settings();
    var ast = DeclarationAstRetentionFixture.mixed(directory);

    // Act
    var retained = independentQuery(settings, ast);

    // Assert
    DeclarationAstRetentionFixture.assertMixed(retained);
  }

  @Test
  void givenSavedSharedLocalsAndAnnotatedArrayReferences_whenQueriedWithoutCompilerModules_thenExactCanonicalAttachmentsSurvive()
      throws Exception {
    // Arrange
    var settings = settings();
    var ast = DeclarationAstRetentionFixture.produce(directory, "Example.java", """
        import java.lang.annotation.*;
        @Target({ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE}) @interface Mark {}
        class Example {
          Object field = new @Mark String("field");
          static { int[] array = new int @Mark [1]; }
          Object subject() {
            @Mark int first = 1, second = 2;
            java.util.function.IntFunction<String[]> factory = @Mark String[]::new;
            return factory.apply(first + second);
          }
        }
        """);

    // Act
    var retained = independentQuery(settings, ast);

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion());
    var nodes = retained.aggregate().nodes();
    var locals = nodes.stream().filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration value
        && (value.name().equals("first") || value.name().equals("second"))).toList();
    assertEquals(2, locals.size());
    var annotation = locals.getFirst().requiredChild(SyntaxRole.ANNOTATION);
    assertTrue(locals.getLast().children(SyntaxRole.ANNOTATION).isEmpty());
    assertEquals(List.of(annotation.nodeId()), locals.getLast().value(SyntaxValue.LocalDeclaration.class)
        .bindingEvidence().orElseThrow().sharedAnnotationKeys());
    var reference = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.MEMBER_REFERENCE).findFirst().orElseThrow();
    var qualifier = reference.requiredChild(SyntaxRole.QUALIFIER);
    assertEquals(SyntaxKind.TYPE_USE, qualifier.syntaxKind());
    assertEquals(1, qualifier.children(SyntaxRole.ANNOTATION).size());
    assertEquals(qualifier.origin().orElseThrow().startOffset(), qualifier.requiredChild(SyntaxRole.ANNOTATION)
        .origin().orElseThrow().startOffset());
  }

  private AstResult independentQuery(Neo4jConnectionSettings settings, AstOutcome ast) throws Exception {
    return independentQuery(settings, ast, Optional.empty());
  }

  private AstResult independentQuery(Neo4jConnectionSettings settings, AstOutcome ast, Optional<List<String>> expectedEndpoints) throws Exception {
    try {
      assertEquals(PublicationStatus.SAVED, retention(settings).retainAst(new AstPublicationRequest(ast,
          PublicationIntent.SAVE_COMPLETE_AST)).status());
      return queryRetainedInIndependentProcess(settings, ast, expectedEndpoints);
    } finally {
      delete(settings, ast.result().orElseThrow().identity());
    }
  }

  private AstResult queryRetainedInIndependentProcess(Neo4jConnectionSettings settings, AstOutcome ast, Optional<List<String>> expectedEndpoints) throws Exception {
    return queryRetainedInIndependentProcess(settings, ast, expectedEndpoints, List.of());
  }

  private AstResult queryRetainedInIndependentProcess(Neo4jConnectionSettings settings, AstOutcome ast,
      Optional<List<String>> expectedEndpoints, List<String> expectedRunIds) throws Exception {
    var original = ast.result().orElseThrow();
    var identity = original.identity();
    var output = directory.resolve("retained-query.ast");
    var expectations = directory.resolve("module-endpoints.tsv");
    if (expectedEndpoints.isPresent()) {
      Files.write(expectations, expectedEndpoints.orElseThrow());
    }
    var expectedSource = directory.resolve("expected-query.ast");
    Files.writeString(expectedSource, PublicAstObservation.describe(original));
    assertMaterialization(settings, ast, expectedRunIds);

    var exit = invokeIndependentConsumer(identity, output,
        List.of(expectedEndpoints.isPresent() ? expectations.toString() : "-", expectedSource.toString()));

    assertEquals(0, exit.status(), exit.diagnostics());
    assertEquals(Files.readString(expectedSource), Files.readString(output));
    var found = query(settings).findAst(identity).outcome().orElseThrow().result().orElseThrow();
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(found));
    return found;
  }

  private void assertIndependentRetiredRefusal(RetiredSourceFixture retired) throws Exception {
    var output = directory.resolve("retired-query.ast");

    var exit = invokeIndependentConsumer(retired.identity(), output, List.of());

    assertNotEquals(0, exit.status(), exit.diagnostics());
    assertTrue(exit.diagnostics().contains("Expected retained source: INCOMPATIBLE"), exit.diagnostics());
    assertFalse(Files.exists(output));
  }

  private IndependentQueryExit invokeIndependentConsumer(AstIdentity identity, Path output, List<String> expectations) throws Exception {
    var log = directory.resolve("retained-query.log");
    var consumerClasses = compileQueryConsumer();
    var modulePath = Path.of("target/aquila-graph/lib").toAbsolutePath() + File.pathSeparator + consumerClasses;
    var executable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
    var arguments = new ArrayList<>(List.of(executable.toString(),
        "-Xms64m", "-Xmx1g",
        "--limit-modules", "java.base,java.logging,java.naming,java.management,jdk.unsupported,org.aquilagraph.retention.consumer,org.neo4j.bolt.connection.netty",
        "-Djava.io.tmpdir=" + directory, "--module-path", modulePath, "--module",
        "org.aquilagraph.retention.consumer/" + RetainedAstQueryProcess.class.getName(), identity.softwareSignature(),
        identity.sourceModelVersion(), identity.astProfileVersion(), identity.semanticProductionRevision(), identity.canonicalContentDigest(),
        output.toString()));
    arguments.addAll(expectations);
    var builder = new ProcessBuilder(arguments).redirectErrorStream(true).redirectOutput(log.toFile());
    builder.environment().putAll(Neo4jIntegration.environment());
    var process = builder.start();
    try {
      var completed = process.waitFor(60, TimeUnit.SECONDS);

      var diagnostics = Files.readString(log);
      System.out.print(diagnostics);
      assertTrue(completed, () -> "Independent query timed out: " + process.pid() + "\n" + diagnostics);
      System.out.println("Independent query process " + process.pid() + " completed with exit " + process.exitValue());
      return new IndependentQueryExit(process.exitValue(), diagnostics);
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Independent query process did not close");
      }
    }
  }

  private record IndependentQueryExit(int status, String diagnostics) {}

  private Path compileQueryConsumer() throws IOException {
    var root = Files.createTempDirectory(directory, "query-module-");
    var descriptor = root.resolve("module-info.java");
    Files.writeString(descriptor, """
        module org.aquilagraph.retention.consumer {
          requires org.aquilagraph.source;
          requires org.aquilagraph.cfg;
          requires org.aquilagraph.application;
          requires org.aquilagraph.repository;
        }
        """);
    var classes = Files.createDirectories(root.resolve("classes"));
    Path annotations;
    // Use the URI form on Windows to preserve drive letters and encoded spaces.
    try {
      annotations = Path.of(NullMarked.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    } catch (URISyntaxException exception) {
      throw new IOException(exception);
    }
    var modulePath = Path.of("target/aquila-graph/lib").toAbsolutePath() + File.pathSeparator + annotations;
    var sources = Path.of("src/test/java/org/aquilagraph/retention/consumer");
    var diagnostics = new ByteArrayOutputStream();
    var result = ToolProvider.getSystemJavaCompiler().run(null, diagnostics, diagnostics, "--release", "25", "--module-path", modulePath,
        "--class-path", classes.toString(), "-d", classes.toString(), descriptor.toString(), sources.resolve("PublicAstObservation.java").toString(),
        sources.resolve("RetainedAstQueryProcess.java").toString());
    assertEquals(0, result, diagnostics.toString(StandardCharsets.UTF_8));
    return classes;
  }

  @TestFactory
  Stream<DynamicTest> givenMixedDeclarationsAndIndependentCfgDisposition_whenSavedClosedAndQueried_thenCompleteSourceSurvives() {
    // Arrange
    var dispositions = Stream.of("ABSENT", "REFUSED", "FAILED");

    // Act
    var observations = dispositions.map(disposition -> DynamicTest.dynamicTest("givenMixedDeclarationsAnd" + disposition
        + "Cfg_whenSavedClosedAndQueried_thenCompleteSourceSurvives", () -> assertMixedRetention(disposition)));

    // Assert
    return observations;
  }

  private void assertMixedRetention(String disposition) throws IOException {
    // Arrange
    var settings = settings();
    var ast = DeclarationAstRetentionFixture.mixed(directory);
    var original = ast.result().orElseThrow();
    DeclarationAstRetentionFixture.assertMixed(original);
    var subject = original.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var supported = original.program().methods().stream().filter(value -> value.name().equals("supported")).findFirst().orElseThrow();
    var cause = new IllegalStateException("Declared CFG construction failure");
    var runner = AnalysisCapabilities.computation(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    var selected = disposition.equals("FAILED") ? supported : subject;
    var request = new CfgRequest(original.identity(), List.of(selected.methodKey()), CfgProfile.JAVA_BOUNDED);
    var run = disposition.equals("ABSENT")
        ? runner.analyze(new AnalysisComputationRequest(
            new AstProductionRequest(original.snapshot(), DeclarationAstRetentionFixture.configuration(), AstProfile.JAVA_SOURCE_UNITS), Optional.empty()))
        : disposition.equals("FAILED")
        ? AnalysisCapabilities.existingSourceDerivation(RecoverableCfgFailures.atMethod(supported.methodKey(), cause))
            .deriveExisting(ast, request)
        : AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(ast, request);
    try {
      // Act
      var before = query(settings).findAst(original.identity());
      var publication = AnalysisExecutionFixture.executeComputed(run, PublicationIntent.SAVE_COMPLETE_AST, Neo4jRepositories.astRetention(() -> settings),
          AnalysisExecutionFixture::unexpectedRunRepository).publication();
      var lookup = query(settings).findAst(original.identity());
      var retained = lookup.outcome().orElseThrow().result().orElseThrow();

      // Assert
      assertEquals(AstLookupStatus.NOT_FOUND, before.status());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(CommitKnowledge.KNOWN_COMMIT, publication.knowledge());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertFalse(run.isPublishable());
      assertEquals(disposition.equals("ABSENT") ? AnalysisCompletion.COMPLETE
          : disposition.equals("FAILED") ? AnalysisCompletion.FAILED : AnalysisCompletion.PARTIAL, run.completion());
      if (disposition.equals("ABSENT")) {
        assertTrue(run.representation().cfg().isEmpty());
      } else {
        assertEquals(disposition.equals("FAILED") ? MethodCfgState.FAILED : MethodCfgState.UNSUPPORTED,
            run.representation().cfg().orElseThrow().methods().getFirst().state());
        if (disposition.equals("FAILED")) {
          assertSame(cause, run.representation().cfg().orElseThrow().methods().getFirst().failure().orElseThrow());
        }
      }
      DeclarationAstRetentionFixture.assertPreserved(original, retained);
      DeclarationAstRetentionFixture.assertMixed(retained);
      assertMaterialization(settings, ast);
    } finally {
      delete(settings, original.identity());
    }
    assertEquals(AstLookupStatus.NOT_FOUND, query(settings).findAst(original.identity()).status());
  }

  @TestFactory
  Stream<DynamicTest> givenLegalUnitShape_whenSavedClosedAndQueried_thenActualRootAndOriginalQualificationSurvive() {
    // Arrange
    var units = List.of(new Unit("Ordinary.java", "class Ordinary {}", true),
        new Unit("Compact.java", "int field = 2; void main() {}", true),
        new Unit("module-info.java", "module retained.example { requires java.base; }", false),
        new Unit("package-info.java", "@Deprecated package retained.example;", false),
        new Unit("Empty.java", "", false),
        new Unit("Imports.java", "import java.util.List; ;", false));

    // Act
    var observations = units.stream().map(unit -> DynamicTest.dynamicTest("given" + unit.filename()
        + "_whenSavedClosedAndQueried_thenRootAndQualificationSurvive", () -> {
          // Arrange
          var settings = settings();
          var ast = DeclarationAstRetentionFixture.produce(directory, unit.filename(), unit.source());
          var original = ast.result().orElseThrow();
          try {
            // Act
            var publication = retention(settings).retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
            var lookup = query(settings).findAst(original.identity());
            var retained = lookup.outcome().orElseThrow().result().orElseThrow();

            // Assert
            assertEquals(PublicationStatus.SAVED, publication.status());
            assertEquals(AstLookupStatus.FOUND, lookup.status());
            DeclarationAstRetentionFixture.assertPreserved(original, retained);
            assertEquals(1, retained.aggregate().roots().size());
            assertEquals(unit.hasType(), !retained.program().types().isEmpty());
            assertEquals(unit.source().length(), retained.aggregate().roots().getFirst().origin().orElseThrow().length());
            assertMaterialization(settings, ast);
          } finally {
            delete(settings, original.identity());
          }
        }));

    // Assert
    return observations;
  }

  @Test
  void givenCurrentAndRetiredSubjects_whenReplacingSourceAndCompletedNamespaces_thenRetiredEnvelopesRemainUntouched() throws IOException {
    // Arrange
    var settings = settings();
    var current = DeclarationAstRetentionFixture.mixed(directory);
    var historical = new ArrayList<RetiredSourceFixture>();
    for (var format = 2; format <= 21; format++) { historical.add(HistoricalAstFixtures.read(format)); }
    var simple = new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(capturedAbsolute(directory),
        List.of(SourceFile.fromContent(capturedPath(Path.of("LegacyRun.java")), "class LegacyRun { int value() { return 4; } }"))),
        DeclarationAstRetentionFixture.configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var result = simple.result().orElseThrow();
    var run = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(simple,
        new CfgRequest(result.identity(), result.program().methods().stream().filter(value -> value.hasBody()).map(value -> value.methodKey()).toList(),
            CfgProfile.JAVA_BOUNDED));
    try {
      assertEquals(PublicationStatus.SAVED, retention(settings).retainAst(new AstPublicationRequest(current,
          PublicationIntent.SAVE_COMPLETE_AST)).status());
      for (var prior : historical) { storeRetiredRoot(settings, prior); }

      // Act
      var replaced = retention(settings).retainAst(new AstPublicationRequest(current, PublicationIntent.SAVE_COMPLETE_AST));
      try (var repository = Neo4jRepositories.completedRuns(() -> settings).open(RepositoryOperationContext.saveCompletedRun(run.identity().key()))) {
        repository.save(run);
        repository.save(run);
      }
      var retained = query(settings).findAst(current.result().orElseThrow().identity()).outcome().orElseThrow().result().orElseThrow();

      // Assert
      assertTrue(run.isPublishable());
      assertEquals(PublicationStatus.SAVED, replaced.status());
      DeclarationAstRetentionFixture.assertPreserved(current.result().orElseThrow(), retained);
      DeclarationAstRetentionFixture.assertMixed(retained);
      assertMaterialization(settings, current);
      for (var prior : historical) {
        assertRetiredRootRefused(settings, prior);
      }
    } finally {
      delete(settings, current.result().orElseThrow().identity());
      for (var prior : historical) { delete(settings, prior.identity()); }
      withSession(settings, session -> session.run("MATCH (entity:AnalysisEntity {runId: $runId}) DETACH DELETE entity",
          Values.parameters("runId", run.identity().key())).consume());
    }
  }

  @TestFactory
  Stream<DynamicTest> givenPresentUnsupportedIdentity_whenQueried_thenIncompatibleDiffersFromAbsenceAndDecodeFailure() {
    // Arrange
    var versions = Stream.of("format", "profile");

    // Act
    var observations = versions.map(version -> DynamicTest.dynamicTest("givenPresentUnsupported" + version
        + "_whenQueried_thenIncompatibleDiffersFromAbsenceAndDecodeFailure", () -> {
          // Arrange
          var settings = settings();
          var ast = DeclarationAstRetentionFixture.mixed(directory);
          var original = ast.result().orElseThrow().identity();
          var unknown = AstIdentity.fromContent(original.softwareSignature(), version.equals("format") ? "java-source/future" : original.sourceModelVersion(),
              version.equals("profile") ? "java-source-expanded/future" : original.astProfileVersion(), original.semanticProductionRevision(), original.canonicalContentDigest());
          var properties = new HashMap<>(new Neo4jAstResultCodec().map(ast).root().properties());
          properties.put("key", unknown.astResultKey());
          properties.put("astResultKey", unknown.astResultKey());
          properties.put("formatVersion", unknown.sourceModelVersion());
          properties.put("sourceModelVersion", unknown.sourceModelVersion());
          properties.put("astProfileVersion", unknown.astProfileVersion());
          properties.put("payload", "invalid-base64");
          try {
            var before = query(settings).findAst(unknown);
            withSession(settings, session -> session.run("CREATE (root:AstEntity:AstResult) SET root = $properties",
                Values.parameters("properties", properties)).consume());

            // Act
            var lookup = query(settings).findAst(unknown);

            // Assert
            assertEquals(AstLookupStatus.NOT_FOUND, before.status());
            assertEquals(AstLookupStatus.INCOMPATIBLE, lookup.status());
            assertEquals(unknown, lookup.requestedIdentity());
            assertEquals(version.equals("format") ? unknown.sourceModelVersion() : unknown.astProfileVersion(), lookup.unsupportedFormat().orElseThrow());
            assertTrue(lookup.outcome().isEmpty());
          } finally {
            delete(settings, unknown);
          }
        }));

    // Assert
    return observations;
  }

  @TestFactory
  Stream<DynamicTest> givenSupportedDeclarationResultWithStoredContradiction_whenQueriedAndReplaced_thenFailureIsTruthfulAndRepairIsExact() {
    // Arrange
    var contradictions = Stream.of("order", "payload", "identity", "completion");

    // Act
    var observations = contradictions.map(contradiction -> DynamicTest.dynamicTest("givenSupportedDeclarationResultWith" + contradiction
        + "Contradiction_whenQueriedAndReplaced_thenFailureIsTruthfulAndRepairIsExact", () -> {
          // Arrange
          var settings = settings();
          var ast = DeclarationAstRetentionFixture.mixed(directory);
          var original = ast.result().orElseThrow();
          var identity = original.identity();
          var mutation = switch (contradiction) {
            case "order" -> "MATCH (:AstOccurrence)-[:AST_CHILD {astResultKey: $scope}]->(:AstValue)-[r:AST_FIELD {astResultKey: $scope}]->() "
                + "WHERE r.field = 'item' AND r.position = 1 SET r.position = 0 RETURN count(r) AS changed";
            case "payload" -> "MATCH (r:AstEntity:AstResult {astResultKey: $scope}) SET r.payload = $malformed RETURN count(r) AS changed";
            case "identity" -> "MATCH (r:AstEntity:AstResult {astResultKey: $scope}) SET r.canonicalContentDigest = 'foreign-content' RETURN count(r) AS changed";
            case "completion" -> "MATCH (r:AstEntity:AstResult {astResultKey: $scope}) SET r.completion = 'INCOMPLETE' RETURN count(r) AS changed";
            default -> throw new IllegalArgumentException("Unknown stored contradiction");
          };
          try {
            assertEquals(PublicationStatus.SAVED, retention(settings).retainAst(new AstPublicationRequest(ast,
                PublicationIntent.SAVE_COMPLETE_AST)).status());
            withSession(settings, session -> assertTrue(session.run(mutation, Values.parameters("scope", identity.astResultKey(),
                "malformed", Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}))).single().get("changed").asLong() > 0));

            // Act
            var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> query(settings).findAst(identity));
            var replacement = retention(settings).retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
            var lookup = query(settings).findAst(identity);

            // Assert
            assertEquals(RepositoryOperationContext.queryAst(identity), failure.context());
            assertEquals(Neo4jRepositoryPhase.DECODE, failure.phase());
            assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
      assertTrue(failure.effectivePrimary() instanceof IllegalArgumentException);
            assertEquals(PublicationStatus.SAVED, replacement.status());
            assertEquals(AstLookupStatus.FOUND, lookup.status());
            DeclarationAstRetentionFixture.assertPreserved(original, lookup.outcome().orElseThrow().result().orElseThrow());
            assertMaterialization(settings, ast);
          } finally {
            delete(settings, identity);
          }
        }));

    // Assert
    return observations;
  }

  private record Unit(String filename, String source, boolean hasType) {}

  private static Neo4jConnectionSettings settings() {
    return Neo4jIntegration.settings();
  }

  private static AstQuery query(Neo4jConnectionSettings settings) {
    return AnalysisCapabilities.astQuery(Neo4jRepositories.astQuery(() -> settings));
  }

  private static AstRetention retention(Neo4jConnectionSettings settings) {
    return AnalysisCapabilities.astRetention(Neo4jRepositories.astRetention(() -> settings));
  }

  private static void storeRetiredRoot(Neo4jConnectionSettings settings, RetiredSourceFixture retired) {
    var root = HistoricalAstFixtures.storedRoot(retired).root();
    withSession(settings, session -> session.run("CREATE (root:AstEntity:AstResult) SET root = $properties",
        Values.parameters("properties", root.properties())).consume());
  }

  private static void assertRetiredRootRefused(Neo4jConnectionSettings settings, RetiredSourceFixture retired) {
    var lookup = query(settings).findAst(retired.identity());
    assertEquals(AstLookupStatus.INCOMPATIBLE, lookup.status());
    assertEquals(retired.identity(), lookup.requestedIdentity());
    assertEquals(retired.identity().sourceModelVersion(), lookup.unsupportedFormat().orElseThrow());
    assertTrue(lookup.outcome().isEmpty());
    withSession(settings, session -> {
      var stored = session.run("MATCH (root:AstEntity:AstResult {astResultKey: $scope}) RETURN properties(root) AS properties",
          Values.parameters("scope", retired.identity().astResultKey())).single().get("properties").asMap();
      assertEquals(HistoricalAstFixtures.storedRoot(retired).root().properties(), stored);
    });
  }

  private static void assertMaterialization(Neo4jConnectionSettings settings, AstOutcome ast) {
    assertMaterialization(settings, ast, List.of());
  }

  private static void assertMaterialization(Neo4jConnectionSettings settings, AstOutcome ast, List<String> expectedRunIds) {
    var projection = new Neo4jAstResultCodec().map(ast);
    withSession(settings, session -> {
      var parameters = Values.parameters("scope", projection.identity().astResultKey());
      var storedPayload = session.run("MATCH (entity:AstResult {astResultKey: $scope}) RETURN entity.payload AS payload", parameters)
          .single().get("payload").asString();
      assertArrayEquals(AstCodec.encode(ast.result().orElseThrow()), Base64.getDecoder().decode(storedPayload));
      var nodes = session.run("MATCH (entity:AstEntity {astResultKey: $scope}) RETURN entity.key AS key, properties(entity) AS properties", parameters).list();
      assertEquals(projection.nodes().stream().collect(Collectors.toMap(Neo4jNode::key, Neo4jNode::properties)),
          nodes.stream().collect(Collectors.toMap(record -> record.get("key").asString(), record -> record.get("properties").asMap())));
      var links = session.run("MATCH (source:AstEntity)-[relation {astResultKey: $scope}]->(target:AstEntity) RETURN relation.key AS key, "
          + "type(relation) AS type, source.key AS source, target.key AS target, properties(relation) AS properties", parameters).list();
      assertEquals(projection.relationships().stream().collect(Collectors.toMap(Neo4jRelationship::key, value -> value)),
          links.stream().map(record -> new Neo4jRelationship(record.get("key").asString(), record.get("type").asString(),
              record.get("source").asString(), record.get("target").asString(), record.get("properties").asMap()))
              .collect(Collectors.toMap(Neo4jRelationship::key, value -> value)));
      assertEquals(expectedRunIds, session.run("MATCH (entity:AnalysisEntity {astResultKey: $scope}) RETURN DISTINCT entity.runId AS runId ORDER BY runId",
          parameters).list(record -> record.get("runId").asString()));
    });
  }

  private static void delete(Neo4jConnectionSettings settings, AstIdentity identity) {
    withSession(settings, session -> Neo4jIntegration.discardAstFixture(session, identity.astResultKey()));
  }

  private static void withSession(Neo4jConnectionSettings settings, Consumer<Session> action) {
    try (var driver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
        var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
      action.accept(session);
    }
  }
}
