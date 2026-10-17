package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.DiagnosticEmission.Address;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue.ExecutableDeclaration;
import org.aquilagraph.source.api.SyntaxValue.ImportDeclaration;
import org.aquilagraph.source.api.SyntaxValue.Literal;
import org.aquilagraph.source.api.SyntaxValue.TypeDeclaration;
import org.aquilagraph.source.api.SyntaxValue.VariableDeclaration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FrontendContractIntegrationTest {
  @TempDir Path directory;

  @Test
  void givenRecursiveUnitsWithEmptyEnvelopeAndWarning_whenPublicProductionAndDetachedDerivationRun_thenOwnersEventsAndValuesRemain()
      throws IOException {
    // Arrange
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var snapshot = InputSnapshot.fromSources(directory, List.of(
        SourceFile.fromContent(Path.of("Empty.java"), "package sample; import java.util.List;"),
        SourceFile.fromContent(Path.of("Legacy.java"), "package sample; @Deprecated(forRemoval=true) class Legacy {}"),
        SourceFile.fromContent(Path.of("Subject.java"), """
            package sample;
            @java.lang.annotation.Target(java.lang.annotation.ElementType.FIELD)
            @interface Mark {}
            class Subject {
              @Mark int left = 1, right = 2;
              Legacy legacy;
              Runnable action = new Runnable() { public void run() { left++; } };
              static { int seed = Math.abs(-3); }
              { left = Math.abs(-4); }
              int subject() {
                class Local { int value() { return left; } }
                return new Local().value();
              }
              int plain() { return 9; }
              enum Choice {
                FIRST(Math.abs(-5)) { int value() { return 6; } };
                Choice(int value) {}
              }
              record Pair(int value) { Pair { if (value < 0) throw new IllegalArgumentException(); } }
            }
            """)));
    var request = new AstProductionRequest(snapshot, configuration, AstProfile.JAVA_SOURCE_UNITS);
    var deriver = CfgDerivers.standard();
    var consumer = new SourceAnalysisRunner(ignored -> { throw new AssertionError("Detached derivation must not produce source"); }, deriver);

    // Act
    var outcome = frontend.produce(request);
    var result = outcome.result().orElseThrow();
    var encoded = AstCodec.encode(result);
    var retained = AstCodec.decode(encoded, result.identity());
    var graphs = consumer.deriveExisting(outcome, new CfgRequest(result.identity(),
        List.of("type:sample.Subject#plain()", "type:sample.Subject#subject()"), CfgProfile.JAVA_BOUNDED));
    var empty = frontend.produce(InputSnapshot.fromSources(directory.resolve("empty"), List.of()), configuration,
        AstProfile.JAVA_SOURCE_UNITS);
    var repeated = frontend.produce(request).result().orElseThrow();

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(AstCompletion.UNAVAILABLE, empty.completion());
    assertEquals(snapshot.sourceFiles().stream().map(SourceFile::sourceFileKey).toList(), result.aggregate().roots().stream()
        .map(SyntaxNode::sourceFileKey).toList());
    var emptyRoot = result.aggregate().roots().getFirst();
    assertTrue(emptyRoot.children(SyntaxRole.TYPE_DECLARATION).isEmpty());
    assertEquals(List.of(SyntaxRole.PACKAGE_DECLARATION, SyntaxRole.IMPORT_DECLARATION),
        emptyRoot.children().stream().map(SyntaxChild::role).toList());
    assertEquals(new ImportDeclaration("java.util.List", false, false, false),
        emptyRoot.children(SyntaxRole.IMPORT_DECLARATION).getFirst().value());
    assertEquals("package sample; import java.util.List;".length(), emptyRoot.origin().orElseThrow().endOffsetExclusive());
    var subject = type(result, "Subject");
    var members = subject.children(SyntaxRole.MEMBER);
    assertEquals(List.of("left", "right", "legacy", "action", "STATIC_INITIALIZER_BLOCK", "INSTANCE_INITIALIZER_BLOCK",
        "subject", "plain", "Choice", "Pair"), members.stream().map(FrontendContractIntegrationTest::memberName).toList());
    assertEquals("1", members.get(0).requiredChild(SyntaxRole.INITIALIZER).value(Literal.class).text());
    assertEquals("2", members.get(1).requiredChild(SyntaxRole.INITIALIZER).value(Literal.class).text());
    assertEquals(1, members.get(0).children(SyntaxRole.ANNOTATION).size());
    assertTrue(members.get(1).children(SyntaxRole.ANNOTATION).isEmpty());
    var local = type(result, "Local");
    var localKey = local.value(TypeDeclaration.class).declarationKey();
    assertEquals(Optional.of("type:sample.Subject#subject()"), result.program().types().stream()
        .filter(value -> value.typeKey().equals(localKey)).findFirst().orElseThrow().nesting().lexicalOwnerKey());
    assertSame(local, result.body("type:sample.Subject#subject()").orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.nodeId().equals(local.nodeId())).findFirst().orElseThrow());
    for (var method : result.program().methods()) {
      if (result.aggregate().methodBodyOrigin(method.methodKey()).equals(Optional.of(MethodBodyOrigin.SOURCE_AST))) {
        var body = result.body(method.methodKey()).orElseThrow().root();
        assertSame(body, result.aggregate().node(body.nodeId()).orElseThrow());
      }
    }
    var events = result.coverage().validationEvidence().diagnosticEmissions();
    assertEquals(1, events.size());
    assertEquals(new Address("CompilerDiagnosticTranslator", "input-compiler-diagnostics", 0),
        events.getFirst().address());
    assertEquals(0, events.getFirst().slot());
    assertSame(result.diagnostics().getFirst(), events.getFirst().diagnostic());
    assertEquals("compiler.warn.has.been.deprecated.for.removal", events.getFirst().diagnostic().code());
    assertFalse(result.diagnostics().stream().anyMatch(value -> value.isError()));
    assertTrue(result.coverage().validationEvidence().unmatchedFacts().isEmpty());
    assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.UNSUPPORTED),
        graphs.representation().cfg().orElseThrow().methods().stream().map(value -> value.state()).toList());
    assertSame(outcome, graphs.representation().ast());
    assertEquals(result.aggregate(), retained.aggregate());
    assertEquals(result.coverage(), retained.coverage());
    assertEquals(events, retained.coverage().validationEvidence().diagnosticEmissions());
    assertArrayEquals(encoded, AstCodec.encode(retained));
    assertArrayEquals(encoded, AstCodec.encode(repeated));
  }

  private static SyntaxNode type(AstResult result, String name) {
    return result.aggregate().nodes().stream().filter(node -> node.value() instanceof TypeDeclaration value
        && value.name().equals(name)).findFirst().orElseThrow();
  }

  private static String memberName(SyntaxNode node) {
    return switch (node.value()) {
      case VariableDeclaration value -> value.name();
      case ExecutableDeclaration value -> value.name();
      case TypeDeclaration value -> value.name();
      default -> node.syntaxKind().name();
    };
  }
}
