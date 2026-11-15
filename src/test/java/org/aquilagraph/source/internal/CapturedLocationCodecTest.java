package org.aquilagraph.source.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CapturedLocation;
import org.aquilagraph.identity.api.CapturedLocation.RootKind;
import org.aquilagraph.identity.api.CapturedLocation.Syntax;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.Test;

class CapturedLocationCodecTest {
  @Test
  void givenSameTextRootsUnderDifferentSyntaxes_whenProducedAndRetained_thenTheirRootMeaningsAndExactNamesSurvive() {
    // Arrange
    var text = "//server/share/source";
    var windows = CapturedLocation.parse(Syntax.WINDOWS, text);
    var posix = CapturedLocation.parse(Syntax.POSIX, text);
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    for (var root : List.of(windows, posix)) {
      var relative = CapturedLocation.parse(root.syntax(), "nested/Example.java");
      var source = SourceFile.fromContent(relative, "package nested; class Example {}");
      var snapshot = InputSnapshot.fromSources(root, List.of(source));

      // Act
      var produced = frontend.produce(new AstProductionRequest(snapshot, configuration, AstProfile.JAVA_SOURCE_FIDELITY))
          .result().orElseThrow();
      var restored = AstCodec.decode(AstCodec.encode(produced), produced.identity());

      // Assert
      assertEquals(AstCompletion.COMPLETE, restored.completion());
      assertEquals("nested.Example", restored.program().types().getFirst().qualifiedName());
      assertEquals(root, restored.snapshot().sourceRoot());
      assertEquals(relative, restored.snapshot().sourceFiles().getFirst().relativePath());
    }
    assertNotEquals(windows, posix);
    assertEquals(RootKind.WINDOWS_UNC, windows.rootKind());
    assertEquals(List.of("server", "share"), windows.rootComponents());
    assertEquals(List.of("source"), windows.components());
    assertEquals(RootKind.POSIX_ABSOLUTE, posix.rootKind());
    assertEquals(List.of("server", "share", "source"), posix.components());
  }
}
