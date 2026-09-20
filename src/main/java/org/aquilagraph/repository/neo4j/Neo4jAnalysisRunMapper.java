package org.aquilagraph.repository.neo4j;

import java.util.Objects;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisRun;

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
    if (run.completion() != AnalysisCompletion.COMPLETED) {
      throw new IllegalArgumentException("Only completed analysis runs may be stored");
    }

    var projection = new Neo4jProjectionBuilder(run.runId());
    projection.node("AnalysisRun", run.runId(), "completion", run.completion().name(), "analyzerVersion", run.analyzerVersion(),
        "modelVersion", run.modelVersion());
    inputMapper.map(run.runId(), run.inputSnapshot(), projection);
    environmentMapper.map(run.runId(), run.environmentSnapshot(), projection);
    evidenceMapper.mapDiagnostics(run.runId(), run.diagnostics(), projection);
    programMapper.map(run.runId(), run.representation().orElseThrow(), projection);
    return projection.build();
  }
}
