package org.aquilagraph.repository.internal.neo4j;

import java.util.Objects;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.identity.api.SoftwareVersion;

/** Coordinates deterministic mapping of a completed analysis aggregate into Neo4j records. */
final class Neo4jAnalysisRunMapper {
  private final Neo4jInputMapper inputMapper;
  private final Neo4jEnvironmentMapper environmentMapper;
  private final Neo4jEvidenceMapper evidenceMapper;
  private final Neo4jProgramMapper programMapper;

  Neo4jAnalysisRunMapper() {
    this.inputMapper = new Neo4jInputMapper();
    this.environmentMapper = new Neo4jEnvironmentMapper();
    this.evidenceMapper = new Neo4jEvidenceMapper();
    this.programMapper = new Neo4jProgramMapper(evidenceMapper, new Neo4jControlFlowMapper(evidenceMapper));
  }

  Neo4jGraphProjection map(AnalysisRun run) {
    Objects.requireNonNull(run, "run");
    if (!run.isPublishable()) {
      throw new IllegalArgumentException("Only completed analysis runs may be stored");
    }

    var runId = run.identity().key();
    var ast = run.representation().ast().result().orElseThrow();
    var projection = new Neo4jProjectionBuilder(runId);
    projection.node("AnalysisRun", runId, "completion", run.completion().name(), "astResultKey", ast.identity().astResultKey(), "operation",
        run.identity().operation().name());
    var softwareVersion = SoftwareVersion.from(ast.snapshot(), ast.environment());
    projection.node("SoftwareVersion", softwareVersion.signature(), "signatureAlgorithm", softwareVersion.signatureAlgorithm(), "signature",
        softwareVersion.signature(), "sourceSignature", softwareVersion.sourceSignature(), "environmentSignature",
        softwareVersion.environmentSignature());
    projection.relate("ANALYZED_SOFTWARE", runId, softwareVersion.signature(), "software-version");
    inputMapper.map(softwareVersion.signature(), ast.snapshot(), projection);
    environmentMapper.map(softwareVersion.signature(), ast.environment(), projection);
    evidenceMapper.mapDiagnostics(runId, run.diagnostics().values(), projection);
    programMapper.map(runId, run.representation(), projection);
    return projection.build();
  }
}
