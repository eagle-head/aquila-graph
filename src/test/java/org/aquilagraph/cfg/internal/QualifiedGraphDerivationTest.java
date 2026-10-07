package org.aquilagraph.cfg.internal;

import org.aquilagraph.cfg.CfgDerivers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualifiedGraphDerivationTest {
  @TempDir Path directory;

  @Test
  void givenActualRetainedConstructorsAndMethods_whenQualifiedGraphsAreDerived_thenCanonicalOwnershipAndEverySemanticFieldSurvive()
      throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Stored.java"), """
        class Base {
          Base(int... values) {}
          int read() { return 1; }
        }
        class Stored extends Base {
          int value;
          Stored() { value = read(); }
          int choose(boolean flag, Integer number) { return flag ? number : 1; }
          int calculate() { return Math.max(value, 3); }
        }
        enum Status { ; Status() {} }
        """)));
    var produced = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var ast = AstCodec.decode(AstCodec.encode(produced), produced.identity());
    for (var method : ast.program().methods()) {
      if (!method.hasBody()) {
        continue;
      }
      var unqualified = new ControlFlowGraphBuilder().build(method, ast.body(method.methodKey()).orElseThrow()).graph().orElseThrow();
      // Act
      var outcome = CfgDerivers.standard().derive(ast,
          new CfgRequest(ast.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst();

      var qualified = outcome.graph().orElseThrow();

      // Assert
      assertEquals(MethodCfgState.AVAILABLE, outcome.state());
      assertEquals(ast.identity(), qualified.identity().orElseThrow().astIdentity());
      assertEquals(ast.bodyRef(method.methodKey()).orElseThrow(), qualified.body().orElseThrow());
      assertEquals(unqualified.operationNodes().stream().map(value -> List.of(value.kind(), value.detail(), value.origins(),
          value.possibleExceptions(), value.invocationSemantics(), value.semanticReferences())).toList(),
          qualified.operationNodes().stream().map(value -> List.of(value.kind(), value.detail(), value.origins(),
              value.possibleExceptions(), value.invocationSemantics(), value.semanticReferences())).toList());
      assertEquals(unqualified.values().stream().map(value -> List.of(value.kind(), value.displayName(), value.literal(), value.staticType())).toList(),
          qualified.values().stream().map(value -> List.of(value.kind(), value.displayName(), value.literal(), value.staticType())).toList());
      assertEquals(unqualified.edges().stream().map(value -> List.of(value.transferKind(), value.discriminator())).toList(),
          qualified.edges().stream().map(value -> List.of(value.transferKind(), value.discriminator())).toList());
      assertTrue(qualified.nodes().stream().allMatch(value -> value.graphId().equals(qualified.graphId())));
      assertTrue(qualified.operationNodes().stream().allMatch(value -> value.sourceRef().isPresent()));
      assertEquals(qualified, CfgDerivers.standard().derive(ast,
          new CfgRequest(ast.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst().graph().orElseThrow());
    }
  }
}
