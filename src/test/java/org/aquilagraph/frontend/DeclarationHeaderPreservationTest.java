package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeclarationHeaderPreservationTest {
  @TempDir Path directory;

  @Test
  void givenAnnotatedGenericReceiverAndRecursiveBody_whenEveryProfileProduces_thenClosedHeadersAndCanonicalValuesArePreserved()
      throws IOException {
    // Arrange
    var text = """
        import java.lang.annotation.*;
        @Target(ElementType.TYPE_USE) @interface Use {}
        class Example<T extends Number & Comparable<T>> {
          @Use String first, second;
          <U extends Number & Comparable<U>> @Use String subject(Example<T> this, final U value, String @Use ... rest)
              throws java.io.@Use IOException { return value == null ? rest[0] : subject(value, rest); }
        }
        """;
    var preservation = System.getProperty("frontend.preservation.directory");
    var root = preservation == null ? directory : Path.of(preservation).resolve("inputs/headers");
    var snapshot = InputSnapshot.fromSources(root, List.of(SourceFile.fromContent(Path.of("Example.java"), text)));
    for (var profile : List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED,
        AstProfile.JAVA_SOURCE_INITIALIZATION, AstProfile.JAVA_SOURCE_NESTED, AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION,
        AstProfile.JAVA_SOURCE_DECLARATIONS, AstProfile.JAVA_SOURCE_UNITS)) {
      // Act
      var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile);

      // Assert
      assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
      var result = outcome.result().orElseThrow();
      var method = result.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration value
          && value.name().equals("subject")).findFirst().orElseThrow();
      assertEquals(List.of(SyntaxRole.ANNOTATION, SyntaxRole.TYPE_PARAMETER, SyntaxRole.RECEIVER_PARAMETER, SyntaxRole.PARAMETER, SyntaxRole.PARAMETER,
          SyntaxRole.RETURN_TYPE, SyntaxRole.THROWN_TYPE, SyntaxRole.BODY), method.children().stream().map(child -> child.role()).toList());
      var signature = method.value(SyntaxValue.ExecutableDeclaration.class);
      assertEquals("type:Example", signature.ownerKey());
      assertEquals(2, method.requiredChild(SyntaxRole.TYPE_PARAMETER).children(SyntaxRole.TYPE_BOUND).size());
      assertEquals("this", method.requiredChild(SyntaxRole.RECEIVER_PARAMETER).requiredChild(SyntaxRole.QUALIFIER)
          .value(SyntaxValue.SourceSymbol.class).name());
      assertEquals(List.of("value", "rest"), method.children(SyntaxRole.PARAMETER).stream()
          .map(node -> node.value(SyntaxValue.ParameterDeclaration.class).name()).toList());
      var semanticMethod = result.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
      assertEquals(method.requiredChild(SyntaxRole.BODY), result.body(semanticMethod.methodKey()).orElseThrow().root());
      var bytes = AstCodec.encode(result);
      assertArrayEquals(bytes, AstCodec.encode(AstCodec.decode(bytes, result.identity())));
      assertTrue(outcome.diagnostics().isEmpty());
      if (preservation != null) {
        var baseline = Path.of(preservation).resolve("baseline");
        var key = "header-" + profile.version().replace('/', '-');
        var qualification = (outcome.profile() + "\n" + outcome.completion() + "\n" + outcome.diagnostics()).getBytes(StandardCharsets.UTF_8);
        if (Boolean.getBoolean("frontend.preservation.capture")) {
          Files.createDirectories(baseline);
          Files.write(baseline.resolve(key + ".ast"), bytes);
          Files.write(baseline.resolve(key + ".qualification"), qualification);
        } else {
          assertArrayEquals(Files.readAllBytes(baseline.resolve(key + ".ast")), bytes);
          assertArrayEquals(Files.readAllBytes(baseline.resolve(key + ".qualification")), qualification);
        }
      }
    }
  }
}
