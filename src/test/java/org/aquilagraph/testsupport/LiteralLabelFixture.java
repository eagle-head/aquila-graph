package org.aquilagraph.testsupport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.TransferKind;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;

public final class LiteralLabelFixture {
  public static final String SOURCE = """
      class Example {
        int negative(int input) { return switch(input) { case -1, -2147483648 -> 1; default -> 2; }; }
        int text(String input) { return switch(input) { case "a" + "b", "c" + "d" + "e" -> 5; default -> 6; }; }
      }
      """;

  private LiteralLabelFixture() {}

  public static InputSnapshot snapshot(Path directory) {
    return InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), SOURCE)));
  }

  public static AstResult historical(AstProfile profile) throws IOException {
    if (!List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_FIDELITY).contains(profile)) {
      throw new IllegalArgumentException("No historical literal-label fixture for " + profile.version());
    }
    var prefix = "/retained-ast/normalized-literal-labels/" + (profile.equals(AstProfile.JAVA_SOURCE_BOUNDED) ? "bounded/" : "fidelity/");
    var properties = new Properties();
    try (var stream = LiteralLabelFixture.class.getResourceAsStream(prefix + "identity.properties")) {
      if (stream == null) { throw new IOException("Missing historical literal-label identity"); }
      properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
    var identity = AstIdentity.fromContent(properties.getProperty("softwareSignature"), properties.getProperty("format"),
        properties.getProperty("profile"), properties.getProperty("semanticRevision"), properties.getProperty("digest"));
    assertEquals(properties.getProperty("key"), identity.astResultKey());
    byte[] bytes;
    try (var stream = LiteralLabelFixture.class.getResourceAsStream(prefix + "source.ast.gz")) {
      if (stream == null) { throw new IOException("Missing historical literal-label payload"); }
      try (var gzip = new GZIPInputStream(stream)) { bytes = gzip.readAllBytes(); }
    }
    var result = AstCodec.decode(bytes, identity);
    assertEquals(identity, result.identity());
    assertEquals(AstCompletion.valueOf(properties.getProperty("completion")), result.completion());
    assertArrayEquals(bytes, AstCodec.encode(result));
    return result;
  }

  public static void assertLabels(CfgOutcome outcome, AstResult result) {
    var names = List.of("negative", "text");
    var types = List.of("int", "java.lang.String");
    var scalars = List.of(List.of("-1", "-2147483648"), List.of("ab", "cde"));
    for (var position = 0; position < names.size(); position++) {
      var name = names.get(position);
      var method = result.program().methods().stream().filter(value -> value.name().equals(name)).findFirst().orElseThrow();
      var label = result.aggregate().methodSyntax(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
          .filter(node -> node.syntaxKind() == SyntaxKind.CASE).findFirst().orElseThrow().children(SyntaxRole.LABEL).getFirst();
      assertEquals(types.get(position), label.resolvedType().orElseThrow().displayName());
      var prefix = "literal:" + label.resolvedType().orElseThrow().typeKey() + ':';
      var graph = outcome.methods().stream().filter(value -> value.methodKey().equals(method.methodKey())).findFirst().orElseThrow()
          .graph().orElseThrow();
      assertEquals(List.of(String.join(",", scalars.get(position).stream().map(value -> prefix + value).toList()), "default"),
          graph.edges().stream().filter(edge -> edge.transferKind() == TransferKind.SWITCH_ALTERNATIVE)
              .map(edge -> edge.discriminator().orElseThrow()).toList());
    }
  }
}
