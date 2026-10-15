package org.aquilagraph.repository.neo4j;

import java.nio.file.Files;
import java.nio.file.Path;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.AstRetentionService;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.source.admission.AstCodec;

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
    Files.write(Path.of(arguments[5]), AstCodec.encode(retained));
    System.out.println("Closed independent query: " + retained.identity().astResultKey() + " " + retained.profile().version());
  }
}
