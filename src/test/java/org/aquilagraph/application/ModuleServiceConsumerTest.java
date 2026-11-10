package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.port.AstRetentionRepository;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SyntaxKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleServiceConsumerTest {
  @TempDir Path directory;

  @Test
  void givenCompleteModuleSource_whenAbsentAvailableRefusedAndFailedCfgRequested_thenSourceIdentityAndTargetsRemainIndependent() {
    // Arrange
    var request = request(PublicationIntent.DO_NOT_SAVE);
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var runner = AnalysisCapabilities.computation(frontend, CfgDerivers.standard());
    var service = AnalysisCapabilities.execution(runner, ModuleServiceConsumerTest::unexpectedAstRepository, context -> {
      throw new AssertionError("No completed-run repository is required for these selections");
    });
    // Act
    var execution = service.execute(request);
    var ast = execution.run().representation().ast();
    var result = ast.result().orElseThrow();
    var supported = result.program().methods().stream().filter(method -> method.name().equals("value")).findFirst().orElseThrow().methodKey();
    var refused = result.program().methods().stream().filter(method -> method.name().equals("lambda")).findFirst().orElseThrow().methodKey();
    var available = CfgDerivers.standard().derive(result, new CfgRequest(result.identity(), List.of(supported), CfgProfile.JAVA_BOUNDED));
    var refusal = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(ast, new CfgRequest(result.identity(), List.of(refused), CfgProfile.JAVA_BOUNDED));
    var cause = new IllegalStateException("Controlled CFG construction failure");
    var failed = AnalysisCapabilities.existingSourceDerivation(RecoverableCfgFailures.atMethod(supported, cause))
        .deriveExisting(ast, new CfgRequest(result.identity(), List.of(supported), CfgProfile.JAVA_BOUNDED));
    var combined = service.execute(request(PublicationIntent.SAVE_COMPLETED));
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(PublicationStatus.NOT_REQUESTED, execution.publication().status());
    assertTrue(execution.run().representation().cfg().isEmpty());
    assertEquals(MethodCfgState.AVAILABLE, available.methods().getFirst().state());
    assertEquals(MethodCfgState.UNSUPPORTED, refusal.representation().cfg().orElseThrow().methods().getFirst().state());
    assertEquals(MethodCfgState.FAILED, failed.representation().cfg().orElseThrow().methods().getFirst().state());
    assertSame(ast, refusal.representation().ast());
    assertSame(ast, failed.representation().ast());
    assertEquals(result.identity(), combined.run().representation().ast().result().orElseThrow().identity());
    assertEquals(PublicationStatus.NOT_ELIGIBLE, combined.publication().status());
    assertEquals(3, result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.MODULE_TYPE_REFERENCE).count());
    assertEquals(PublicationStatus.NOT_REQUESTED, retention().retainAst(new AstPublicationRequest(ast,
        PublicationIntent.DO_NOT_SAVE)).status());
  }

  @Test
  void givenMissingRequiredModuleTarget_whenExplicitRetentionRequested_thenRepositoryRemainsUnopened() {
    // Arrange
    var original = request(PublicationIntent.SAVE_COMPLETE_AST);
    var missing = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("module-info.java"), "module service.missing { uses missing.Service; }")));
    var service = AnalysisCapabilities.execution(AnalysisCapabilities.computation(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard()), ModuleServiceConsumerTest::unexpectedAstRepository, run -> { throw new AssertionError("Combined publication was not requested"); });
    // Act
    var execution = service.execute(new AnalysisRequest(new AnalysisComputationRequest(
        new AstProductionRequest(missing, original.computation().source().configuration(), original.computation().source().profile()), Optional.empty()), original.publicationIntent()));
    var empty = service.execute(new AnalysisRequest(new AnalysisComputationRequest(
        new AstProductionRequest(InputSnapshot.fromSources(directory, List.of()), original.computation().source().configuration(), original.computation().source().profile()), Optional.empty()), original.publicationIntent()));
    // Assert
    assertEquals(AstCompletion.INCOMPLETE, execution.run().representation().ast().completion());
    assertEquals(PublicationStatus.NOT_ELIGIBLE, execution.publication().status());
    assertEquals(CommitKnowledge.NOT_STARTED, execution.publication().knowledge());
    assertEquals(AstCompletion.UNAVAILABLE, empty.run().representation().ast().completion());
    assertEquals(PublicationStatus.NOT_ELIGIBLE, empty.publication().status());
  }

  private static AstRetention retention() {
    return AnalysisCapabilities.astRetention(ModuleServiceConsumerTest::unexpectedAstRepository);
  }

  private static AstRetentionRepository unexpectedAstRepository(RepositoryOperationContext context) {
    throw new AssertionError("No AST repository may open for " + context);
  }

  private AnalysisRequest request(PublicationIntent intent) {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("module-info.java"),
        "module service.consumer { uses api.Service; provides api.Service with impl.Provider; }"),
        SourceFile.fromContent(Path.of("api/Service.java"), "package api; public interface Service {}"),
        SourceFile.fromContent(Path.of("impl/Provider.java"), "package impl; public class Provider implements api.Service { public int value() { return 7; } public Runnable lambda() { return () -> {}; } }")));
    return new AnalysisRequest(new AnalysisComputationRequest(
        new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES), Optional.empty()), intent);
  }
}
