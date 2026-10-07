package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.cfg.internal.ControlFlowGraphBuilder;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNodeKey;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttributedOccurrenceIdentityTest {
  @TempDir Path directory;

  @Test
  void givenCompilerInsertedConstructorBeforeAWrittenMethod_whenRetainingAndBuildingGraphDraft_thenCanonicalKeysAndActualCompilerAddressesRemainDistinct()
      throws IOException {
    // Arrange
    var source = "class Stored { int value() { return 7; } }";
    var compilerRoot = actualCompilerBodyPath(source);
    var file = SourceFile.fromContent(Path.of("Stored.java"), source);
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory, List.of(file)),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var retained = AstCodec.decode(AstCodec.encode(ast), ast.identity());
    var method = retained.program().methods().stream().filter(value -> value.name().equals("value")).findFirst().orElseThrow();
    var syntax = retained.body(method.methodKey()).orElseThrow();
    var returned = syntax.root().descendantsAndSelf().stream().filter(value -> value.syntaxKind() == SyntaxKind.RETURN).findFirst().orElseThrow();
    var address = syntax.attributedOccurrences().stream().filter(value -> value.nodeKey().equals(returned.nodeId())).findFirst().orElseThrow();
    var originalReturnPath = compilerRoot.child(SyntaxRole.STATEMENT, 0);
    var originalReturnKey = SyntaxNodeKey.from(file.sourceFileKey(), originalReturnPath, JavaSourceKind.RETURN).value();

    // Act
    var graph = new ControlFlowGraphBuilder().build(method, syntax).graph().orElseThrow();
    var operation = graph.operationNodes().stream().filter(value -> value.kind() == OperationKind.RETURN).findFirst().orElseThrow();
    var graphKey = StableId.sha256("cfg", method.methodKey());
    var expectedKey = StableId.sha256("cfg-node", graphKey, "operation",
        StableId.sha256("operation", method.methodKey(), returned.nodeId(), "return"));

    // Assert
    assertEquals(originalReturnPath, address.compilerPath());
    assertNotEquals(originalReturnPath, returned.structuralPath());
    assertNotEquals(originalReturnKey, returned.nodeId());
    assertEquals(expectedKey, operation.nodeId());
    assertEquals(ast.aggregate().bodyAttributions(method.methodKey()), syntax.attributedOccurrences());
    assertTrue(retained.aggregate().bodyAttributions("unknown").isEmpty());
  }

  private SyntaxStructuralPath actualCompilerBodyPath(String source) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var input = SimpleJavaFileObject.forSource(directory.resolve("Stored.java").toUri(), source);
    try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
      var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("--release", "25", "-proc:none"), null, List.of(input));
      var unit = task.parse().iterator().next();
      task.analyze();
      var type = (ClassTree) unit.getTypeDecls().getFirst();
      var method = type.getMembers().stream().filter(value -> value instanceof MethodTree executable && executable.getName().contentEquals("value"))
          .findFirst().orElseThrow();
      assertTrue(diagnostics.getDiagnostics().stream().noneMatch(value -> value.getKind() == Diagnostic.Kind.ERROR));
      var ordinal = type.getMembers().indexOf(method);
      assertEquals(1, ordinal);
      return SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0).child(SyntaxRole.MEMBER, ordinal)
          .child(SyntaxRole.BODY, 0);
    }
  }
}
