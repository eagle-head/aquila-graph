package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class AstLookupTest {
  @TempDir Path directory;

  @Test
  void givenEqualDistinctIdentityAndCompleteSource_whenConstructingFoundLookup_thenFieldsAndSourceReferenceAreRetained() throws IOException {
    // Arrange
    var ast = completeSource();
    var sourceIdentity = ast.result().orElseThrow().identity();
    var identity = AstIdentity.fromContent(sourceIdentity.softwareSignature(), sourceIdentity.sourceModelVersion(),
        sourceIdentity.astProfileVersion(), sourceIdentity.semanticProductionRevision(), sourceIdentity.canonicalContentDigest());
    assertEquals(sourceIdentity, identity);
    assertNotSame(sourceIdentity, identity);

    // Act
    var lookup = new AstLookup(identity, AstLookupStatus.FOUND, Optional.of(ast), Optional.empty());

    // Assert
    assertEquals(identity, lookup.requestedIdentity());
    assertEquals(AstLookupStatus.FOUND, lookup.status());
    assertSame(ast, lookup.outcome().orElseThrow());
    assertEquals(Optional.empty(), lookup.unsupportedFormat());
  }

  @Test
  void givenAbsentSource_whenConstructingNotFoundLookup_thenEveryFieldIsRetained() throws IOException {
    // Arrange
    var identity = completeSource().result().orElseThrow().identity();

    // Act
    var lookup = new AstLookup(identity, AstLookupStatus.NOT_FOUND, Optional.empty(), Optional.empty());

    // Assert
    assertEquals(identity, lookup.requestedIdentity());
    assertEquals(AstLookupStatus.NOT_FOUND, lookup.status());
    assertEquals(Optional.empty(), lookup.outcome());
    assertEquals(Optional.empty(), lookup.unsupportedFormat());
  }

  @Test
  void givenUnsupportedFormat_whenConstructingIncompatibleLookup_thenEveryFieldIsRetained() throws IOException {
    // Arrange
    var identity = completeSource().result().orElseThrow().identity();
    var format = "unsupported-source-format";

    // Act
    var lookup = new AstLookup(identity, AstLookupStatus.INCOMPATIBLE, Optional.empty(), Optional.of(format));

    // Assert
    assertEquals(identity, lookup.requestedIdentity());
    assertEquals(AstLookupStatus.INCOMPATIBLE, lookup.status());
    assertEquals(Optional.empty(), lookup.outcome());
    assertEquals(Optional.of(format), lookup.unsupportedFormat());
  }

  @Test
  void givenFoundWithoutOutcome_whenConstructingLookup_thenIllegalArgumentExceptionIsThrown() throws IOException {
    // Arrange
    var ast = completeSource();
    var identity = ast.result().orElseThrow().identity();

    // Act
    Executable construct = () -> new AstLookup(identity, AstLookupStatus.FOUND, Optional.empty(), Optional.empty());

    // Assert
    assertThrowsExactly(IllegalArgumentException.class, construct);
  }

  @Test
  void givenNotFoundWithOutcome_whenConstructingLookup_thenIllegalArgumentExceptionIsThrown() throws IOException {
    // Arrange
    var ast = completeSource();
    var identity = ast.result().orElseThrow().identity();

    // Act
    Executable construct = () -> new AstLookup(identity, AstLookupStatus.NOT_FOUND, Optional.of(ast), Optional.empty());

    // Assert
    assertThrowsExactly(IllegalArgumentException.class, construct);
  }

  @Test
  void givenIncompatibleWithOutcome_whenConstructingLookup_thenIllegalArgumentExceptionIsThrown() throws IOException {
    // Arrange
    var ast = completeSource();
    var identity = ast.result().orElseThrow().identity();

    // Act
    Executable construct = () -> new AstLookup(identity, AstLookupStatus.INCOMPATIBLE, Optional.of(ast), Optional.of("unsupported-source-format"));

    // Assert
    assertThrowsExactly(IllegalArgumentException.class, construct);
  }

  @Test
  void givenIncompatibleWithoutFormat_whenConstructingLookup_thenIllegalArgumentExceptionIsThrown() throws IOException {
    // Arrange
    var ast = completeSource();
    var identity = ast.result().orElseThrow().identity();

    // Act
    Executable construct = () -> new AstLookup(identity, AstLookupStatus.INCOMPATIBLE, Optional.empty(), Optional.empty());

    // Assert
    assertThrowsExactly(IllegalArgumentException.class, construct);
  }

  @Test
  void givenNotFoundWithFormat_whenConstructingLookup_thenIllegalArgumentExceptionIsThrown() throws IOException {
    // Arrange
    var ast = completeSource();
    var identity = ast.result().orElseThrow().identity();

    // Act
    Executable construct = () -> new AstLookup(identity, AstLookupStatus.NOT_FOUND, Optional.empty(), Optional.of("unsupported-source-format"));

    // Assert
    assertThrowsExactly(IllegalArgumentException.class, construct);
  }

  @Test
  void givenFoundWithFormat_whenConstructingLookup_thenIllegalArgumentExceptionIsThrown() throws IOException {
    // Arrange
    var ast = completeSource();
    var identity = ast.result().orElseThrow().identity();

    // Act
    Executable construct = () -> new AstLookup(identity, AstLookupStatus.FOUND, Optional.of(ast), Optional.of("unsupported-source-format"));

    // Assert
    assertThrowsExactly(IllegalArgumentException.class, construct);
  }

  @Test
  void givenEmptyUnsupportedFormat_whenConstructingLookup_thenIllegalArgumentExceptionIsThrown() throws IOException {
    // Arrange
    var ast = completeSource();
    var identity = ast.result().orElseThrow().identity();

    // Act
    Executable construct = () -> new AstLookup(identity, AstLookupStatus.INCOMPATIBLE, Optional.empty(), Optional.of(""));

    // Assert
    assertThrowsExactly(IllegalArgumentException.class, construct);
  }

  @Test
  void givenWhitespaceUnsupportedFormat_whenConstructingLookup_thenIllegalArgumentExceptionIsThrown() throws IOException {
    // Arrange
    var ast = completeSource();
    var identity = ast.result().orElseThrow().identity();

    // Act
    Executable construct = () -> new AstLookup(identity, AstLookupStatus.INCOMPATIBLE, Optional.empty(), Optional.of(" \t\n"));

    // Assert
    assertThrowsExactly(IllegalArgumentException.class, construct);
  }

  @Test
  void givenNullRequestedIdentity_whenConstructingLookup_thenNullPointerExceptionIsThrown() throws IOException {
    // Arrange
    var ast = completeSource();
    var identity = ast.result().orElseThrow().identity();

    // Act
    Executable construct = () -> new AstLookup(null, AstLookupStatus.FOUND, Optional.of(ast), Optional.empty());

    // Assert
    assertThrowsExactly(NullPointerException.class, construct);
  }

  @Test
  void givenNullStatus_whenConstructingLookup_thenNullPointerExceptionIsThrown() throws IOException {
    // Arrange
    var ast = completeSource();
    var identity = ast.result().orElseThrow().identity();

    // Act
    Executable construct = () -> new AstLookup(identity, null, Optional.of(ast), Optional.empty());

    // Assert
    assertThrowsExactly(NullPointerException.class, construct);
  }

  @Test
  void givenNullOutcomeOptional_whenConstructingLookup_thenNullPointerExceptionIsThrown() throws IOException {
    // Arrange
    var ast = completeSource();
    var identity = ast.result().orElseThrow().identity();

    // Act
    Executable construct = () -> new AstLookup(identity, AstLookupStatus.FOUND, null, Optional.empty());

    // Assert
    assertThrowsExactly(NullPointerException.class, construct);
  }

  @Test
  void givenNullFormatOptional_whenConstructingLookup_thenNullPointerExceptionIsThrown() throws IOException {
    // Arrange
    var ast = completeSource();
    var identity = ast.result().orElseThrow().identity();

    // Act
    Executable construct = () -> new AstLookup(identity, AstLookupStatus.FOUND, Optional.of(ast), null);

    // Assert
    assertThrowsExactly(NullPointerException.class, construct);
  }

  @Test
  void givenAdmittedIncompleteSource_whenConstructingFoundLookup_thenIllegalArgumentExceptionIsThrown() throws IOException {
    // Arrange
    var ast = produce("class Stored { int field = 1; int value() { return 1; } }");
    assertEquals(AstCompletion.INCOMPLETE, ast.completion());
    var identity = ast.result().orElseThrow().identity();

    // Act
    Executable construct = () -> new AstLookup(identity, AstLookupStatus.FOUND, Optional.of(ast), Optional.empty());

    // Assert
    assertThrowsExactly(IllegalArgumentException.class, construct);
  }

  @Test
  void givenAdmittedUnavailableSource_whenConstructingFoundLookup_thenIllegalArgumentExceptionIsThrown() throws IOException {
    // Arrange
    var identity = completeSource().result().orElseThrow().identity();
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory, List.of()),
        configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    assertEquals(AstCompletion.UNAVAILABLE, ast.completion());
    assertTrue(ast.result().isEmpty());

    // Act
    Executable construct = () -> new AstLookup(identity, AstLookupStatus.FOUND, Optional.of(ast), Optional.empty());

    // Assert
    assertThrowsExactly(IllegalArgumentException.class, construct);
  }

  @Test
  void givenCompleteForeignSource_whenConstructingFoundLookup_thenIllegalArgumentExceptionIsThrown() throws IOException {
    // Arrange
    var ast = completeSource();
    var foreign = produce("class Stored { int value() { return 2; } }");
    assertEquals(AstCompletion.COMPLETE, foreign.completion());
    var identity = ast.result().orElseThrow().identity();
    assertNotEquals(identity, foreign.result().orElseThrow().identity());

    // Act
    Executable construct = () -> new AstLookup(identity, AstLookupStatus.FOUND, Optional.of(foreign), Optional.empty());

    // Assert
    assertThrowsExactly(IllegalArgumentException.class, construct);
  }

  private AstOutcome completeSource() throws IOException {
    var ast = produce("class Stored { int value() { return 1; } }");
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertTrue(ast.result().isPresent());
    return ast;
  }

  private AstOutcome produce(String source) throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Stored.java"), source))), configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
  }

  private static CompilerConfiguration configuration() {
    return new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of());
  }
}
