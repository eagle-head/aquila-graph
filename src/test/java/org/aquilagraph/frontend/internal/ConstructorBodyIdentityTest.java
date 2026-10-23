package org.aquilagraph.frontend.internal;

import org.aquilagraph.cfg.CfgDerivers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.cfg.api.ValueKind;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConstructorBodyIdentityTest {
  @TempDir Path directory;

  @Test
  void givenWrittenConstructorsWithInsertedSuperclassCalls_whenRetainedAndDerived_thenGeneratedOperationsKeepTheCanonicalBody()
      throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Stored.java"), """
        class Parent { Parent(int... values) {} }
        class Stored extends Parent { Stored() {} }
        class Plain { Plain() {} }
        enum Status { ; Status() {} }
        """)));
    var produced = new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
        new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of()), AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var ast = AstCodec.decode(AstCodec.encode(produced), produced.identity());
    var constructors = ast.program().types().stream().filter(type -> !type.qualifiedName().equals("Parent"))
        .flatMap(type -> type.methods().stream()).filter(method -> method.constructor()).toList();
    assertEquals(3, constructors.size());
    for (var constructor : constructors) {
      var syntax = ast.body(constructor.methodKey()).orElseThrow();
      var declaration = ast.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration value
          && value.declarationKey().equals(constructor.methodKey())).findFirst().orElseThrow();
      assertSame(declaration.requiredChild(SyntaxRole.BODY), syntax.root());
      assertSame(ast.aggregate().node(syntax.root().nodeId()).orElseThrow(), syntax.root());
      assertTrue(syntax.root().children(SyntaxRole.STATEMENT).isEmpty());
      assertEquals(Optional.of(MethodBodyOrigin.SOURCE_AST), ast.aggregate().methodBodyOrigin(constructor.methodKey()));
      assertEquals(Optional.of(syntax.root().nodeId()), ast.bodyRef(constructor.methodKey()).orElseThrow().sourceOccurrenceKey());
      var outcome = CfgDerivers.standard().derive(ast,
          new CfgRequest(ast.identity(), List.of(constructor.methodKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst();
      assertEquals(MethodCfgState.AVAILABLE, outcome.state());
      var graph = outcome.graph().orElseThrow();
      assertTrue(graph.operationNodes().stream().allMatch(operation -> operation.origins().isEmpty()
          && operation.sourceRef().body().equals(outcome.body().orElseThrow())
          && operation.sourceRef().occurrence().isEmpty()));
      var call = graph.operationNodes().stream().filter(operation -> operation.kind() == OperationKind.INVOKE).findFirst().orElseThrow();
      assertEquals("compiler-inserted-superclass/invoke", call.sourceRef().generatedRole());
      if (constructor.ownerTypeKey().equals("type:Stored")) {
        assertEquals(List.of(OperationKind.VARARGS_ARRAY, OperationKind.INVOKE), graph.operationNodes().stream().map(operation -> operation.kind()).toList());
        assertEquals(2, call.operands().size());
      } else if (constructor.ownerTypeKey().equals("type:Status")) {
        assertEquals(List.of(ValueKind.RECEIVER, ValueKind.IMPLICIT_PARAMETER, ValueKind.IMPLICIT_PARAMETER),
            call.operands().stream().map(operand -> operand.value().kind()).toList());
      } else {
        assertEquals(1, call.operands().size());
      }
    }
  }

  @Test
  void givenWrittenThisAndSuperDelegations_whenProducedRetainedAndDerived_thenEachConstructorUsesItsOneCanonicalSourceBody()
      throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Stored.java"), """
        class Parent { Parent(int n) {} }
        class Stored extends Parent {
          Stored() { this(1); }
          Stored(int n) { super(n); }
        }
        """)));
    var produced = new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
        new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of()), AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var ast = AstCodec.decode(AstCodec.encode(produced), produced.identity());
    var constructors = ast.program().types().stream().filter(type -> type.qualifiedName().equals("Stored"))
        .flatMap(type -> type.methods().stream()).filter(method -> method.constructor()).toList();
    assertEquals(2, constructors.size());
    for (var constructor : constructors) {
      var syntax = ast.body(constructor.methodKey()).orElseThrow();
      var declaration = ast.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration value
          && value.declarationKey().equals(constructor.methodKey())).findFirst().orElseThrow();
      assertSame(declaration.requiredChild(SyntaxRole.BODY), syntax.root());
      assertSame(ast.aggregate().node(syntax.root().nodeId()).orElseThrow(), syntax.root());
      assertEquals(Optional.of(MethodBodyOrigin.SOURCE_AST), ast.aggregate().methodBodyOrigin(constructor.methodKey()));
      assertEquals(Optional.of(syntax.root().nodeId()), ast.bodyRef(constructor.methodKey()).orElseThrow().sourceOccurrenceKey());
      var outcome = CfgDerivers.standard().derive(ast,
          new CfgRequest(ast.identity(), List.of(constructor.methodKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst();
      assertEquals(MethodCfgState.AVAILABLE, outcome.state());
      assertTrue(outcome.graph().orElseThrow().operationNodes().stream().allMatch(operation -> operation.sourceRef() != null));
    }
  }
}
