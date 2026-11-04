package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.testsupport.StatementBranchFixture;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompilationUnitAnalysisTest {
  @TempDir Path directory;

  @Test
  void givenRejectedRetainedBranch_whenApplicationRequestsAstRetention_thenContractFailurePreventsPublication() {
    // Arrange
    var valid = StatementBranchFixture.produce(directory, AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var result = valid.result().orElseThrow();
    var malformed = StatementBranchFixture.withDeclaration(AstCodec.encode(result), result.identity().sourceModelVersion(),
        StatementBranchFixture.branch(valid, SyntaxRole.THEN_BRANCH), JavaSourceKind.CLASS, false);
    var runner = AnalysisCapabilities.computation(ignored -> AstOutcome.available(AstCodec.decode(malformed, result.identity())), CfgDerivers.standard());
    var service = AnalysisCapabilities.execution(runner, ignored -> { throw new AssertionError("Rejected values must not acquire retention"); }, ignored -> { throw new AssertionError("No completed run may be published"); });
    var request = new AnalysisRequest(new AnalysisComputationRequest(
        new AstProductionRequest(result.snapshot(), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), result.profile()), Optional.empty()), PublicationIntent.SAVE_COMPLETE_AST);
    // Act
    var failure = assertThrows(AstContractException.class, () -> service.execute(request));
    // Assert
    assertTrue(failure.getMessage().contains("child category"), failure.toString());
    assertTrue(failure.getCause() instanceof IllegalArgumentException);
  }

  @Test
  void givenACompleteNoTypeUnit_whenTheApplicationRequestsNoSaveAndDerivesAnEmptySelection_thenSourceAndItsIdentitySurvive() {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Empty.java"), "")));
    var runner = AnalysisCapabilities.computation(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    var request = new AnalysisComputationRequest(
        new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_UNITS), Optional.empty());
    var retention = AnalysisCapabilities.astRetention(ignored -> { throw new AssertionError("No save must not acquire storage"); });
    // Act
    var produced = runner.analyze(request).representation().ast();
    var ast = produced.result().orElseThrow();
    var selected = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(produced, new CfgRequest(ast.identity(), List.of(), CfgProfile.JAVA_BOUNDED));
    var noSave = retention.retainAst(new AstPublicationRequest(produced, PublicationIntent.DO_NOT_SAVE));
    var decoded = AstCodec.decode(AstCodec.encode(ast), ast.identity());
    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertTrue(ast.program().methods().isEmpty());
    assertEquals(ast.identity(), selected.representation().ast().result().orElseThrow().identity());
    assertTrue(selected.representation().cfg().orElseThrow().methods().isEmpty());
    assertEquals(PublicationStatus.NOT_REQUESTED, noSave.status());
    assertEquals(produced, new AstPublicationRequest(produced, PublicationIntent.SAVE_COMPLETE_AST).ast());
    assertEquals(ast.identity(), decoded.identity());
    assertEquals(0, decoded.aggregate().roots().getFirst().origin().orElseThrow().length());
  }

  @Test
  void givenZeroCaptureAndErroneousUnit_whenOfferedForExplicitRetention_thenNoRepositoryIsAcquired() {
    // Arrange
    var runner = AnalysisCapabilities.computation(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    var retention = AnalysisCapabilities.astRetention(ignored -> { throw new AssertionError("Ineligible input must not acquire storage"); });
    var snapshots = List.of(InputSnapshot.fromSources(directory, List.of()), InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Example.java"), "import missing.Type;"))));
    // Act
    var outcomes = snapshots.stream().map(snapshot -> runner.analyze(new AnalysisComputationRequest(
        new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_UNITS), Optional.empty()))
        .representation().ast()).toList();
    // Assert
    for (AstOutcome outcome : outcomes) {
      assertTrue(outcome.completion() != AstCompletion.COMPLETE);
      assertEquals(PublicationStatus.NOT_ELIGIBLE, retention.retainAst(new AstPublicationRequest(outcome,
          PublicationIntent.SAVE_COMPLETE_AST)).status());
    }
    assertThrows(IllegalArgumentException.class, () -> new AstPublicationRequest(outcomes.getFirst(), PublicationIntent.SAVE_COMPLETED));
  }
}
