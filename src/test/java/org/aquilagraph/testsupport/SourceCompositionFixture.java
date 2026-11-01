package org.aquilagraph.testsupport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.retention.AstCodec;

public final class SourceCompositionFixture {
  private SourceCompositionFixture() {
  }

  public static String source(boolean escaped) {
    var delimiter = escaped ? "\\" + "u007d" : "}";
    var literal = escaped ? "tr" + "\\" + "u0000ue" : "true";
    return "class Box { <T> Box(T value) {} " + delimiter + """

        class Probe {
          Object scope() {
            class Local {
              Object make(boolean flag) {
                if (flag) {
                  boolean truth = LITERAL;
                  record Data(int number) {}
                  if (truth) return new Box(1);
                } else { while (flag) { break; } }
                return null;
              }
            }
            return new Local().make(true);
          }
          int refused(int value) { return switch (value) { case 0 -> 1; default -> 2; }; }
          int supported(int value) { if (value > 0) return value; return 0; }
        }
        """.replace("LITERAL", literal);
  }

  public static AstResult produce(Path directory, boolean escaped) {
    var snapshot = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Probe.java"), source(escaped))));
    var outcome = JavaFrontends.standard().produce(new AstProductionRequest(new SourceInput.Snapshot(snapshot),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES));
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    assertTrue(outcome.diagnostics().isEmpty(), outcome.diagnostics().toString());
    return outcome.result().orElseThrow();
  }

  public static void assertComposition(AstResult ast, boolean escaped) {
    var text = source(escaped);
    assertEquals(text, ast.snapshot().sourceFiles().getFirst().content());
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertEquals(AstProfile.JAVA_SOURCE_MODULE_SERVICES, ast.profile());
    assertEquals("javac-public/14", ast.identity().semanticProductionRevision());
    var creation = occurrence(ast, SyntaxKind.OBJECT_CREATION, "new Box(1)");
    var literal = occurrence(ast, SyntaxKind.LITERAL, escaped ? "tr" + "\\" + "u0000ue" : "true", text.indexOf("boolean truth"));
    assertEquals(JavaSourceKind.BOOLEAN_LITERAL, literal.sourceKind());
    assertEquals("true", literal.value(SyntaxValue.Literal.class).text());
    assertEquals(List.of(occurrence(ast, SyntaxKind.LITERAL, "1")), creation.children(SyntaxRole.ARGUMENT));
    var owner = ast.program().types().stream().filter(type -> type.qualifiedName().equals("Box")).findFirst().orElseThrow();
    var constructor = owner.methods().stream().filter(method -> method.constructor()).findFirst().orElseThrow();
    assertEquals(constructor.methodKey(), creation.semanticReference().orElseThrow().symbolKey().orElseThrow());
    var method = ast.program().methods().stream().filter(value -> value.name().equals("make")).findFirst().orElseThrow();
    var body = ast.body(method.methodKey()).orElseThrow();
    assertTrue(body.source().nodes().contains(creation));
    var use = body.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow();
    assertEquals(method.methodKey(), use.scopeKey());
    assertEquals("int", use.sourceType().displayName());
    assertEquals("java.lang.Integer", use.selectedTarget().displayName());
    assertEquals("int", use.preBoxPrimitiveType().orElseThrow().displayName());
    assertEquals("java.lang.Integer", use.boxedType().orElseThrow().displayName());
    assertEquals(InvocationApplicabilityMode.FIXED, body.invocationArity(creation.nodeId()).orElseThrow().mode());
    var branch = atStart(ast, SyntaxKind.IF, text.indexOf("if (flag)"));
    var block = branch.requiredChild(SyntaxRole.THEN_BRANCH);
    assertEquals(List.of(JavaSourceKind.VARIABLE, JavaSourceKind.RECORD, JavaSourceKind.IF),
        block.children().stream().map(child -> child.node().sourceKind()).toList());
    assertEquals(List.of(0, 1, 2), block.children().stream().map(child -> child.position()).toList());
    for (var child : block.children()) {
      assertEquals(block.structuralPath().child(SyntaxRole.STATEMENT, child.position()), child.node().structuralPath());
      assertEquals(block.sourceFileKey(), child.node().sourceFileKey());
    }
    assertFalse(ast.program().methods().stream().anyMatch(value -> value.name().startsWith("constructorSelection")));
    assertEquals(List.of("Box", "Probe", "Local", "Data"), ast.aggregate().nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration)
        .map(node -> node.value(SyntaxValue.TypeDeclaration.class).name()).toList());
  }

  public static void assertRoundTrip(AstResult original, AstResult decoded) {
    assertEquals(original.identity(), decoded.identity());
    assertEquals(original.aggregate(), decoded.aggregate());
    assertEquals(original.coverage(), decoded.coverage());
    assertEquals(original.program(), decoded.program());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
  }

  private static SyntaxNode occurrence(AstResult ast, SyntaxKind kind, String raw) {
    return occurrence(ast, kind, raw, 0);
  }

  private static SyntaxNode occurrence(AstResult ast, SyntaxKind kind, String raw, int after) {
    var text = ast.snapshot().sourceFiles().getFirst().content();
    var start = text.indexOf(raw, after);
    return ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == kind)
        .filter(node -> node.origin().equals(Optional.of(new SourceSpan(node.sourceFileKey(), start, start + raw.length()))))
        .findFirst().orElseThrow();
  }

  private static SyntaxNode atStart(AstResult ast, SyntaxKind kind, int start) {
    return ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == kind)
        .filter(node -> node.origin().orElseThrow().startOffset() == start).findFirst().orElseThrow();
  }
}
