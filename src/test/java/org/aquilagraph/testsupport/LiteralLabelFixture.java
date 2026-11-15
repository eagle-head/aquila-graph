package org.aquilagraph.testsupport;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.TransferKind;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
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
    return InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), SOURCE)));
  }

  public static RetiredSourceFixture historical(AstProfile profile) throws IOException {
    var directory = switch (profile.version()) {
      case "java-source-bounded/1" -> "bounded";
      case "java-source-expanded/7" -> "fidelity";
      default -> throw new IllegalArgumentException("No historical literal-label fixture for " + profile.version());
    };
    return RetiredSourceFixture.read("/retained-ast/normalized-literal-labels/" + directory);
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
