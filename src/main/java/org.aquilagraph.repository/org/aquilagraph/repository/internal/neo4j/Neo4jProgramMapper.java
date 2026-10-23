package org.aquilagraph.repository.internal.neo4j;

import java.util.Objects;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.JavaTypeReference;
import org.aquilagraph.application.ProgramRepresentation;
import org.aquilagraph.source.api.JavaProgram;

/** Maps the analyzed Java program and its method analyses into a Neo4j projection. */
final class Neo4jProgramMapper {
  private static final String NAME_PROPERTY = "name";
  private static final String SOURCE_DECLARED_PROPERTY = "sourceDeclared";

  private final Neo4jEvidenceMapper evidenceMapper;
  private final Neo4jControlFlowMapper controlFlowMapper;

  Neo4jProgramMapper(Neo4jEvidenceMapper evidenceMapper, Neo4jControlFlowMapper controlFlowMapper) {
    this.evidenceMapper = Objects.requireNonNull(evidenceMapper, "evidenceMapper");
    this.controlFlowMapper = Objects.requireNonNull(controlFlowMapper, "controlFlowMapper");
  }

  void map(String runId, ProgramRepresentation representation, Neo4jProjectionBuilder projection) {
    var ast = representation.ast().result().orElseThrow();
    var programKey = StableId.sha256("program", runId);
    projection.node("Program", programKey, "typeCount", ast.program().types().size(), "astResultKey", ast.identity().astResultKey());
    projection.relate("HAS_PROGRAM", runId, programKey, "program");
    mapTypes(programKey, ast.program(), projection);
    controlFlowMapper.map(programKey, representation.cfg().orElseThrow().methods(), projection);
  }

  private void mapTypes(String programKey, JavaProgram program, Neo4jProjectionBuilder projection) {
    for (var typePosition = 0; typePosition < program.types().size(); typePosition++) {
      var type = program.types().get(typePosition);
      projection.node("JavaType", type.typeKey(), "qualifiedName", type.qualifiedName(), Neo4jSchema.KIND_PROPERTY, type.kind().name(),
          "modifiers", type.modifiers().stream().map(Enum::name).sorted().toList(), SOURCE_DECLARED_PROPERTY, type.sourceDeclared());
      projection.relate("DECLARES_TYPE", programKey, type.typeKey(), Integer.toString(typePosition), Neo4jSchema.POSITION_PROPERTY,
          typePosition);
      evidenceMapper.mapOrigins(type.typeKey(), type.origins(), projection);
      mapTypeRelationships(type, projection);
      for (var fieldPosition = 0; fieldPosition < type.fields().size(); fieldPosition++) {
        var field = type.fields().get(fieldPosition);
        var properties = Neo4jProjectionBuilder.properties(NAME_PROPERTY, field.name(), "modifiers",
            field.modifiers().stream().sorted().toList(), "enumConstant", field.enumConstant(), SOURCE_DECLARED_PROPERTY,
            field.sourceDeclared());
        Neo4jTypeProperties.put(properties, "staticType", field.staticType());
        projection.node("JavaField", field.fieldKey(), properties);
        projection.relate("DECLARES_FIELD", type.typeKey(), field.fieldKey(), Integer.toString(fieldPosition),
            Neo4jSchema.POSITION_PROPERTY, fieldPosition);
        evidenceMapper.mapOrigins(field.fieldKey(), field.origins(), projection);
      }
      for (var component : type.recordComponents()) {
        var properties = Neo4jProjectionBuilder.properties(NAME_PROPERTY, component.name(), Neo4jSchema.POSITION_PROPERTY,
            component.ordinal(), "varArgs", component.varArgs(), "writtenModifiers",
            component.writtenModifiers().stream().map(Enum::name).sorted().toList(), "effectiveModifiers",
            component.effectiveModifiers().stream().map(Enum::name).sorted().toList());
        Neo4jTypeProperties.put(properties, "staticType", component.staticType());
        component.accessorKey().ifPresent(key -> properties.put("accessorKey", key));
        component.backingFieldKey().ifPresent(key -> properties.put("backingFieldKey", key));
        component.canonicalParameterKey().ifPresent(key -> properties.put("canonicalParameterKey", key));
        projection.node("JavaRecordComponent", component.componentKey(), properties);
        projection.relate("DECLARES_RECORD_COMPONENT", type.typeKey(), component.componentKey(), Integer.toString(component.ordinal()),
            Neo4jSchema.POSITION_PROPERTY, component.ordinal());
        evidenceMapper.mapOrigins(component.componentKey(), component.origins(), projection);
      }
      for (var position = 0; position < type.synthesizedDeclarations().size(); position++) {
        var declaration = type.synthesizedDeclarations().get(position);
        projection.node("JavaSynthesizedDeclaration", declaration.declarationKey(), Neo4jSchema.KIND_PROPERTY, declaration.kind().name(),
            SOURCE_DECLARED_PROPERTY, false);
        projection.relate("DECLARES_SYNTHESIZED", type.typeKey(), declaration.declarationKey(), Integer.toString(position),
            Neo4jSchema.POSITION_PROPERTY, position);
      }
      for (var methodPosition = 0; methodPosition < type.methods().size(); methodPosition++) {
        var method = type.methods().get(methodPosition);
        projection.node("JavaMethod", method.methodKey(), NAME_PROPERTY, method.name(), "signature", method.signature(), "returnType",
            method.returnType(), "modifiers", method.modifiers().stream().sorted().toList(), "constructor", method.constructor(),
            SOURCE_DECLARED_PROPERTY, method.sourceDeclared(), "hasBody", method.hasBody());
        projection.relate("DECLARES_METHOD", type.typeKey(), method.methodKey(), Integer.toString(methodPosition),
            Neo4jSchema.POSITION_PROPERTY, methodPosition);
        evidenceMapper.mapOrigins(method.methodKey(), method.origins(), projection);
        mapParameters(method, projection);
      }
    }
  }

  private void mapTypeRelationships(JavaType type, Neo4jProjectionBuilder projection) {
    type.enclosingType().ifPresent(reference -> mapTypeReference(type.typeKey(), "ENCLOSED_BY", 0, reference, projection));
    type.directSuperclass().ifPresent(reference -> mapTypeReference(type.typeKey(), "HAS_DIRECT_SUPERCLASS", 0, reference, projection));
    for (var position = 0; position < type.directInterfaces().size(); position++) {
      mapTypeReference(type.typeKey(), "HAS_DIRECT_INTERFACE", position, type.directInterfaces().get(position), projection);
    }
    for (var position = 0; position < type.permittedSubclasses().size(); position++) {
      mapTypeReference(type.typeKey(), "PERMITS_SUBCLASS", position, type.permittedSubclasses().get(position), projection);
    }
    for (var position = 0; position < type.typeParameters().size(); position++) {
      var parameter = type.typeParameters().get(position);
      projection.node("JavaTypeParameter", parameter.typeParameterKey(), NAME_PROPERTY, parameter.name(), Neo4jSchema.POSITION_PROPERTY,
          position, SOURCE_DECLARED_PROPERTY, parameter.sourceDeclared());
      projection.relate("DECLARES_TYPE_PARAMETER", type.typeKey(), parameter.typeParameterKey(), Integer.toString(position),
          Neo4jSchema.POSITION_PROPERTY, position);
      evidenceMapper.mapOrigins(parameter.typeParameterKey(), parameter.origins(), projection);
      for (var boundPosition = 0; boundPosition < parameter.bounds().size(); boundPosition++) {
        mapTypeReference(parameter.typeParameterKey(), "HAS_TYPE_BOUND", boundPosition, parameter.bounds().get(boundPosition), projection);
      }
    }
  }

  private static void mapTypeReference(String ownerKey, String relationshipType, int position, JavaTypeReference reference,
      Neo4jProjectionBuilder projection) {
    var referenceKey = StableId.sha256("java-type-reference", ownerKey, relationshipType, Integer.toString(position));
    var properties = Neo4jProjectionBuilder.properties(Neo4jSchema.POSITION_PROPERTY, position);
    Neo4jTypeProperties.put(properties, "type", reference.identity());
    reference.declarationKey().ifPresent(key -> properties.put("declarationKey", key));
    projection.node("JavaTypeReference", referenceKey, properties);
    projection.relate(relationshipType, ownerKey, referenceKey, Integer.toString(position), Neo4jSchema.POSITION_PROPERTY, position);
  }

  private void mapParameters(JavaMethod method, Neo4jProjectionBuilder projection) {
    for (var position = 0; position < method.parameters().size(); position++) {
      var parameter = method.parameters().get(position);
      var properties = Neo4jProjectionBuilder.properties(NAME_PROPERTY, parameter.name(), Neo4jSchema.POSITION_PROPERTY, position,
          SOURCE_DECLARED_PROPERTY, parameter.sourceDeclared());
      Neo4jTypeProperties.put(properties, "staticType", parameter.staticType());
      projection.node("JavaParameter", parameter.parameterKey(), properties);
      projection.relate("DECLARES_PARAMETER", method.methodKey(), parameter.parameterKey(), Integer.toString(position),
          Neo4jSchema.POSITION_PROPERTY, position);
      evidenceMapper.mapOrigins(parameter.parameterKey(), parameter.origins(), projection);
    }
  }
}
