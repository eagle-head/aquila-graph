package org.aquilagraph.source.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.retention.AstCodec;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CanonicalValueReader;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue.SwitchCase;
import org.aquilagraph.source.api.SyntaxValue.SwitchCaseForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceFormRequirementsTest {
  @TempDir Path directory;

  @Test
  void givenProducedResourceFinallyAndColonForms_whenAdmittedAndDecoded_thenCompleteWrittenFormsSurvive() throws IOException {
    // Arrange
    var text = """
        class Example {
          void resource(AutoCloseable input) throws Exception { try (input) {} }
          void cleanup() { try {} finally {} }
          int choice(int input) { return switch (input) { case 1: yield 2; default: yield 3; }; }
        }
        """;
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), text)));
    var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
    var supplied = SourceValueCapture.capture(original.aggregate());

    // Act
    var admitted = AstValueAdmission.admit(supplied);
    var writer = new CanonicalValueWriter("retained-source-values/1");
    writer.text("source-aggregate");
    writer.count(1);
    writer.text("copiedValues");
    new RetainedValueCodec(false).write(writer, supplied);
    var decoded = (JavaAstAggregate) new RetainedValueCodec(false)
        .read(new CanonicalValueReader(writer.toByteArray(), "retained-source-values/1"));
    var variants = decoded.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TRY || node.syntaxKind() == SyntaxKind.CASE).toList();

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(AstCompletion.COMPLETE, AstCodec.decode(AstCodec.encode(original), original.identity()).completion());
    assertEquals(admitted, decoded);
    assertEquals(4, variants.size());
    for (var variant : variants) {
      assertTrue(SourceFormRequirements.supports(variant), variant.nodeId());
    }
    assertTrue(variants.stream().anyMatch(node -> !node.children(SyntaxRole.RESOURCE).isEmpty()));
    assertTrue(variants.stream().anyMatch(node -> !node.children(SyntaxRole.FINALLY_BLOCK).isEmpty()));
    assertTrue(variants.stream().anyMatch(node -> node.value() instanceof SwitchCase value
        && value.form() == SwitchCaseForm.STATEMENT_GROUP));
  }
}
