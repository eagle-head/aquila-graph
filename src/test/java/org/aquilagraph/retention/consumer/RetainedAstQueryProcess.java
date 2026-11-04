package org.aquilagraph.retention.consumer;

import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AstQuery;
import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.repository.neo4j.Neo4jRepositories;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxValue;

/** Independent retained-value consumer that runs without compiler modules. */
public final class RetainedAstQueryProcess {
  private RetainedAstQueryProcess() {}

  public static void main(String[] arguments) throws Exception {
    if (ModuleLayer.boot().findModule("jdk.compiler").isPresent() || ModuleLayer.boot().findModule("java.compiler").isPresent()
        || ModuleLayer.boot().findModule("org.aquilagraph.frontend").isPresent()) {
      throw new IllegalStateException("The independent query requires compiler modules to be absent");
    }
    if (!RetainedAstQueryProcess.class.getModule().isNamed()
        || !"org.aquilagraph.retention.consumer".equals(RetainedAstQueryProcess.class.getModule().getName())) {
      throw new IllegalStateException("The query must run as an independent named module");
    }
    var identity = AstIdentity.fromContent(arguments[0], arguments[1], arguments[2], arguments[3], arguments[4]);
    var settings = Neo4jConnectionSettings.fromEnvironment(System.getenv());
    AstQuery query = AnalysisCapabilities.astQuery(Neo4jRepositories.astResults(() -> settings));
    var lookup = query.findAst(identity);
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
      var expected = Files.readString(Path.of(arguments[7]));
      if (!expected.equals(PublicAstObservation.describe(retained))) {
        throw new IllegalStateException("Independent retained source lost public environment, source, coverage, or derived graph facts");
      }
    }
    Files.writeString(Path.of(arguments[5]), PublicAstObservation.describe(retained));
    System.out.println("Closed independent query: " + retained.identity().astResultKey() + " " + retained.profile().version());
  }
}
