package org.aquilagraph.repository.neo4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Arrays;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.AstRetentionService;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxValue;

/** Independent retained-value consumer that runs without compiler modules. */
public final class RetainedAstQueryProcess {
  private RetainedAstQueryProcess() {}

  public static void main(String[] arguments) throws Exception {
    if (ModuleLayer.boot().findModule("jdk.compiler").isPresent() || ModuleLayer.boot().findModule("java.compiler").isPresent()) {
      throw new IllegalStateException("The independent query requires compiler modules to be absent");
    }
    var identity = AstIdentity.fromContent(arguments[0], arguments[1], arguments[2], arguments[3], arguments[4]);
    var settings = Neo4jConnectionSettings.fromEnvironment(System.getenv());
    var service = new AstRetentionService(new Neo4jAstResultRepositoryFactory(() -> settings));
    var lookup = service.findAst(identity);
    if (lookup.status() != AstLookupStatus.FOUND) { throw new IllegalStateException("Expected retained source: " + lookup.status()); }
    var retained = lookup.outcome().orElseThrow().result().orElseThrow();
    if (arguments.length > 6 && !"-".equals(arguments[6])) {
      var expected = Files.readAllLines(Path.of(arguments[6]));
      var actual = retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.MODULE_TYPE_REFERENCE)
          .sorted(Comparator.comparingLong(node -> node.origin().orElseThrow().startOffset())).map(node -> {
            var reference = node.semanticReference().orElseThrow();
            var target = reference.declarationIdentity().orElseThrow();
            var role = node.structuralPath().steps().getLast();
            var origin = node.origin().orElseThrow();
            var spelling = node.value(SyntaxValue.ModuleTypeReference.class).name().dottedName();
            var source = retained.snapshot().sourceFiles().stream().filter(file -> file.sourceFileKey().equals(node.sourceFileKey())).findFirst().orElseThrow();
            if (!source.content().substring((int) origin.startOffset(), (int) origin.endOffsetExclusive()).equals(spelling)
                || !reference.symbolKey().orElseThrow().equals(target.declarationKey())
                || retained.aggregate().writtenSourceAssociation(node.nodeId()).isEmpty()) {
              throw new IllegalStateException("Retained module operand lost its source or declaration association");
            }
            return role.role() + "\t" + role.position() + "\t" + spelling + "\t" + target.moduleName().orElse("<unnamed>")
                + "\t" + target.binaryName() + "\t" + reference.status() + "\t" + origin.startOffset() + "\t" + origin.endOffsetExclusive();
          }).toList();
      if (!expected.equals(actual)) {
        throw new IllegalStateException("Retained module endpoints differ: expected " + expected + " but observed " + actual);
      }
    }
    if (arguments.length > 7) {
      var expectedBytes = Files.readAllBytes(Path.of(arguments[7]));
      var expected = AstCodec.decode(expectedBytes, identity);
      if (!expected.environment().equals(retained.environment()) || !expected.snapshot().equals(retained.snapshot())
          || !expected.diagnostics().equals(retained.diagnostics()) || !Arrays.equals(expectedBytes, AstCodec.encode(retained))) {
        throw new IllegalStateException("Independent retained source lost exact environment facts, provenance, diagnostics, or bytes");
      }
    }
    Files.write(Path.of(arguments[5]), AstCodec.encode(retained));
    System.out.println("Closed independent query: " + retained.identity().astResultKey() + " " + retained.profile().version());
  }
}
