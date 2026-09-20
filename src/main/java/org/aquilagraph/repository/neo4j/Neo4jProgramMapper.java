package org.aquilagraph.repository.neo4j;

import java.util.Objects;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.ProgramRepresentation;
import org.aquilagraph.model.StableId;

/** Maps the analyzed Java program and its method analyses into a Neo4j projection. */
final class Neo4jProgramMapper {
  private final Neo4jEvidenceMapper evidenceMapper;
  private final Neo4jControlFlowMapper controlFlowMapper;

  Neo4jProgramMapper(Neo4jEvidenceMapper evidenceMapper, Neo4jControlFlowMapper controlFlowMapper) {
    this.evidenceMapper = Objects.requireNonNull(evidenceMapper, "evidenceMapper");
    this.controlFlowMapper = Objects.requireNonNull(controlFlowMapper, "controlFlowMapper");
  }

  void map(String runId, ProgramRepresentation representation, Neo4jProjectionBuilder projection) {
    var programKey = StableId.sha256("program", runId);
    projection.node("Program", programKey, "typeCount", representation.program().types().size());
    projection.relate("HAS_PROGRAM", runId, programKey, "program");
    mapTypes(programKey, representation, projection);
    controlFlowMapper.map(programKey, representation.methodAnalyses(), projection);
  }

  private void mapTypes(String programKey, ProgramRepresentation representation, Neo4jProjectionBuilder projection) {
    for (var typePosition = 0; typePosition < representation.program().types().size(); typePosition++) {
      var type = representation.program().types().get(typePosition);
      projection.node("JavaType", type.typeKey(), "qualifiedName", type.qualifiedName(), Neo4jSchema.KIND_PROPERTY, type.kind().name(),
          "sourceDeclared", type.sourceDeclared());
      projection.relate("DECLARES_TYPE", programKey, type.typeKey(), Integer.toString(typePosition), Neo4jSchema.POSITION_PROPERTY,
          typePosition);
      evidenceMapper.mapOrigins(type.typeKey(), type.origins(), projection);
      for (var methodPosition = 0; methodPosition < type.methods().size(); methodPosition++) {
        var method = type.methods().get(methodPosition);
        projection.node("JavaMethod", method.methodKey(), "name", method.name(), "signature", method.signature(), "returnType",
            method.returnType(), "modifiers", method.modifiers().stream().sorted().toList(), "constructor", method.constructor(),
            "sourceDeclared", method.sourceDeclared());
        projection.relate("DECLARES_METHOD", type.typeKey(), method.methodKey(), Integer.toString(methodPosition),
            Neo4jSchema.POSITION_PROPERTY, methodPosition);
        evidenceMapper.mapOrigins(method.methodKey(), method.origins(), projection);
        mapParameters(method, projection);
        method.body().ifPresent(body -> {
          var bodyKey = StableId.sha256("method-body", method.methodKey());
          projection.node("MethodBody", bodyKey, "supported", body.supported());
          projection.relate("HAS_BODY", method.methodKey(), bodyKey, "body");
          evidenceMapper.mapSyntax(body.syntaxRoot(), bodyKey, 0, true, projection);
        });
      }
    }
  }

  private void mapParameters(JavaMethod method, Neo4jProjectionBuilder projection) {
    for (var position = 0; position < method.parameters().size(); position++) {
      var parameter = method.parameters().get(position);
      projection.node("JavaParameter", parameter.parameterKey(), "name", parameter.name(), "staticType", parameter.staticType(),
          Neo4jSchema.POSITION_PROPERTY, position);
      projection.relate("DECLARES_PARAMETER", method.methodKey(), parameter.parameterKey(), Integer.toString(position),
          Neo4jSchema.POSITION_PROPERTY, position);
      evidenceMapper.mapOrigins(parameter.parameterKey(), parameter.origins(), projection);
    }
  }
}
