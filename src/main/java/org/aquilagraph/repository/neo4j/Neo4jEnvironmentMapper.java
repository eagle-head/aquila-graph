package org.aquilagraph.repository.neo4j;

import java.util.List;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.DependencySnapshot;
import org.aquilagraph.identity.api.StableId;

/** Maps compiler and dependency identity into a Neo4j projection. */
final class Neo4jEnvironmentMapper {
  void map(String runId, AnalysisEnvironmentSnapshot environment, Neo4jProjectionBuilder projection) {
    var environmentKey = environment.environmentDigest();
    projection.node("AnalysisEnvironmentSnapshot", environmentKey, "digestAlgorithm", environment.digestAlgorithm(), "environmentDigest",
        environment.environmentDigest(), "compilerImplementation", environment.compilerImplementation(), "compilerVendor",
        environment.compilerVendor(), "compilerVersion", environment.compilerVersion(), "analysisOptions", environment.analysisOptions());
    projection.relate("HAS_ENVIRONMENT", runId, environmentKey, "environment");

    var configuration = environment.compilerConfiguration();
    var configurationKey = StableId.sha256("compiler-configuration", configuration.canonicalForm());
    projection.node("CompilerConfiguration", configurationKey, "release", configuration.release().feature(), "classPath",
        configuration.classPath().stream().map(Object::toString).toList(), "modulePath",
        configuration.modulePath().stream().map(Object::toString).toList(), "options", configuration.options());
    projection.relate("HAS_CONFIGURATION", environmentKey, configurationKey, "configuration");
    dependencies(environmentKey, "HAS_CLASS_PATH_ENTRY", "class-path", environment.classPath(), projection);
    dependencies(environmentKey, "HAS_MODULE_PATH_ENTRY", "module-path", environment.modulePath(), projection);
  }

  private static void dependencies(String environmentKey, String relationshipType, String role, List<DependencySnapshot> dependencies,
      Neo4jProjectionBuilder projection) {
    for (var position = 0; position < dependencies.size(); position++) {
      var dependency = dependencies.get(position);
      var dependencyKey = StableId.sha256("dependency-snapshot", environmentKey, role, Integer.toString(position),
          dependency.fingerprint());
      projection.node("DependencySnapshot", dependencyKey, "location", dependency.location().toString(), Neo4jSchema.KIND_PROPERTY,
          dependency.kind().name(), "byteCount", dependency.byteCount(), "entryCount", dependency.entryCount(), "fingerprint",
          dependency.fingerprint(), Neo4jSchema.POSITION_PROPERTY, position);
      projection.relate(relationshipType, environmentKey, dependencyKey, Integer.toString(position), Neo4jSchema.POSITION_PROPERTY,
          position);
    }
  }
}
