package org.aquilagraph.repository.neo4j;

import java.util.List;
import java.util.HashMap;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.model.StableId;

/** Maps diagnostics and source evidence shared by program and control-flow projections. */
final class Neo4jEvidenceMapper {
  void mapDiagnostics(String runId, List<AnalysisDiagnostic> diagnostics, Neo4jProjectionBuilder projection) {
    var diagnosticsByKey = new HashMap<String, AnalysisDiagnostic>();
    for (var position = 0; position < diagnostics.size(); position++) {
      var diagnostic = diagnostics.get(position);
      var existing = diagnosticsByKey.putIfAbsent(diagnostic.diagnosticKey(), diagnostic);
      if (existing == null) {
        projection.node("Diagnostic", diagnostic.diagnosticKey(), "code", diagnostic.code(), "stage", diagnostic.stage().name(), "severity",
            diagnostic.severity().name(), "message", diagnostic.message());
        mapOrigins(diagnostic.diagnosticKey(), diagnostic.origins(), projection);
      } else if (!existing.equals(diagnostic)) {
        throw new IllegalArgumentException("Equal diagnostic keys require equal diagnostic values and ordered origins");
      }
      projection.relate("HAS_DIAGNOSTIC", runId, diagnostic.diagnosticKey(), Integer.toString(position), Neo4jSchema.POSITION_PROPERTY,
          position);
    }
  }

  void mapSemanticReferences(String ownerKey, List<SemanticReference> semanticReferences, Neo4jProjectionBuilder projection) {
    for (var position = 0; position < semanticReferences.size(); position++) {
      var reference = semanticReferences.get(position);
      var key = StableId.sha256("semantic-reference", ownerKey, Integer.toString(position));
      var properties = Neo4jProjectionBuilder.properties(Neo4jSchema.KIND_PROPERTY, reference.referenceKind().name(), "status",
          reference.status().name(), Neo4jSchema.POSITION_PROPERTY, position);
      reference.symbolKey().ifPresent(value -> properties.put("symbolKey", value));
      reference.typeName().ifPresent(value -> properties.put("typeName", value));
      properties.put("typeAlternativeNames", reference.typeAlternativeNames());
      properties.put("assignableToTypeNames", reference.assignableToTypeNames());
      projection.node("SemanticReference", key, properties);
      projection.relate("HAS_REFERENCE", ownerKey, key, Integer.toString(position), Neo4jSchema.POSITION_PROPERTY, position);
    }
  }

  void mapOrigins(String ownerKey, List<SourceSpan> origins, Neo4jProjectionBuilder projection) {
    for (var position = 0; position < origins.size(); position++) {
      var origin = origins.get(position);
      var key = StableId.sha256("source-origin", ownerKey, Integer.toString(position), origin.sourceFileKey(),
          Long.toString(origin.startOffset()), Long.toString(origin.endOffsetExclusive()));
      projection.node("SourceOrigin", key, "startOffset", origin.startOffset(), "endOffsetExclusive", origin.endOffsetExclusive(),
          Neo4jSchema.POSITION_PROPERTY, position);
      projection.relate("HAS_ORIGIN", ownerKey, key, Integer.toString(position), Neo4jSchema.POSITION_PROPERTY, position);
      projection.relate("IN_SOURCE", key, origin.sourceFileKey(), "source");
    }
  }
}
