package org.aquilagraph.source.admission;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemberInitializationCodecTest {
  @TempDir Path directory;

  @Test
  void givenOriginalMethodOnlyFormat_whenDecodedAndMaterialized_thenHistoricalFactNamesIdentityAndQualificationRemainIntact() throws IOException {
    // Arrange
    var current = produce("class Example { int subject(int value) { return Math.abs(value); } }", AstProfile.JAVA_SOURCE_EXPANDED);
    var previous = SourceAnalysisFixture.classLiteralResult(current);
    var bytes = AstCodec.encode(previous);

    // Act
    var retained = AstCodec.decode(bytes, previous.identity());
    var projection = AstCodec.materialize(retained);

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion());
    assertEquals(previous.identity(), retained.identity());
    assertEquals(current.aggregate(), retained.aggregate());
    assertEquals("java-source/13", retained.identity().sourceModelVersion());
    assertEquals("java-language-feature-coverage:aa7f14c098cdf30b107ec6b2c7670c85cc1f66fd4001c71b0287f664a907503d",
        retained.coverage().coverage().contractIdentity());
    assertArrayEquals(bytes, AstCodec.encode(retained));
    assertFalse(projection.links().stream().anyMatch(link -> link.field().equals("scopeKey") || link.field().equals("scopes")));
    assertTrue(projection.links().stream().anyMatch(link -> link.field().equals("methodKey")));
  }

  @Test
  void givenDeclarationScopes_whenOriginalMethodOnlyFormatIsRequested_thenTheNewValuesCannotBeEncodedAsHistoricalResults() throws IOException {
    // Arrange
    var original = produce("class Example { int field = Math.abs(-1); { field++; } }", AstProfile.JAVA_SOURCE_INITIALIZATION);

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstResultAdmission.admit(copiedInput(original), original.profile(),
        original.identity().semanticProductionRevision(), "java-source/13"));

    // Assert
    assertTrue(failure.getMessage().contains("Declaration execution scopes require retained AST format java-source/14"));
    assertEquals(AstCompletion.COMPLETE, original.completion());
  }

  private AstResult produce(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile)
        .result().orElseThrow();
  }

  private static SyntaxAnalysisInput copiedInput(AstResult result) {
    return new SyntaxAnalysisInput(result.snapshot(), result.environment(), result.program(), Optional.of(result.aggregate()),
        result.diagnostics(), result.coverage());
  }
}
