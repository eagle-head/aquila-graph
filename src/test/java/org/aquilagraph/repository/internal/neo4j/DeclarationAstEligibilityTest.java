package org.aquilagraph.repository.internal.neo4j;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AstPublicationRequest;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeclarationAstEligibilityTest {
  @TempDir Path directory;

  @Test
  void givenCompleteMixedSourceAndIneligibleInputs_whenExplicitIntentIsApplied_thenOnlyEligibleSaveCanAcquireStorage() throws IOException {
    // Arrange
    var complete = DeclarationAstRetentionFixture.mixed(directory);
    var incomplete = DeclarationAstRetentionFixture.produce(directory, "Broken.java", "class Broken { int field = missing; }");
    var unavailable = new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(capturedAbsolute(directory), List.of()),
        DeclarationAstRetentionFixture.configuration(), AstProfile.JAVA_SOURCE_UNITS);
    var retention = AnalysisCapabilities.astRetention(ignored -> { throw new AssertionError("No-save or ineligible input must not acquire a repository"); });

    // Act
    var noSave = retention.retainAst(new AstPublicationRequest(complete, PublicationIntent.DO_NOT_SAVE));
    var partial = retention.retainAst(new AstPublicationRequest(incomplete, PublicationIntent.SAVE_COMPLETE_AST));
    var absent = retention.retainAst(new AstPublicationRequest(unavailable, PublicationIntent.SAVE_COMPLETE_AST));

    // Assert
    DeclarationAstRetentionFixture.assertMixed(complete.result().orElseThrow());
    assertEquals(PublicationStatus.NOT_REQUESTED, noSave.status());
    assertEquals(AstCompletion.INCOMPLETE, incomplete.completion());
    assertTrue(incomplete.result().orElseThrow().diagnostics().stream().anyMatch(value -> value.isError()));
    assertEquals(AstCompletion.UNAVAILABLE, unavailable.completion());
    assertEquals(PublicationStatus.NOT_ELIGIBLE, partial.status());
    assertEquals(PublicationStatus.NOT_ELIGIBLE, absent.status());
    assertTrue(partial.subjectIdentity().isPresent());
    assertFalse(absent.subjectIdentity().isPresent());
    for (var publication : List.of(noSave, partial, absent)) {
      assertEquals(CommitKnowledge.NOT_STARTED, publication.knowledge());
      assertTrue(publication.failure().isEmpty());
    }
  }
}
