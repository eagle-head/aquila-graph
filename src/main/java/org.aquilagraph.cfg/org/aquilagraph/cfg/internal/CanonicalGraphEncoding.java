package org.aquilagraph.cfg.internal;

import org.aquilagraph.cfg.api.GraphSourceRef;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.AnalysisDiagnostic;

/** Complete graph content encoding with stable local roles and validated relationship positions. */
final class CanonicalGraphEncoding {
  private CanonicalGraphEncoding() {
  }

  static String digest(DraftGraph graph, LoweringPlan plan, List<GraphSourceRef> sources, List<AnalysisDiagnostic> diagnostics) {
    if (sources.size() != graph.operationNodes().size()) {
      throw new IllegalArgumentException("Every operation requires one exact source association");
    }
    var declarationIdentities = graph.operationNodes().stream().flatMap(operation -> operation.semanticReferences().stream())
        .anyMatch(reference -> reference.declarationIdentity().isPresent());
    var writer = new CanonicalValueWriter(declarationIdentities ? "canonical-java-cfg/6" : "canonical-java-cfg/5");
    writer.text(plan.profile().version());
    writer.text(plan.profile().graphModelVersion());
    writer.text(plan.body().astIdentity().astResultKey());
    writer.text(plan.body().methodKey());
    text(writer, plan.body().sourceOccurrenceKey());
    writer.flag(plan.body().nonwrittenProvenance().isPresent());
    plan.body().nonwrittenProvenance().ifPresent(value -> {
      writer.text(value.origin());
      writer.text(value.sourceFileKey());
      writer.text(value.ownerDeclarationKey());
    });
    var nodes = ordinals(graph.nodes().stream().map(value -> value.nodeId()).toList());
    var values = ordinals(graph.values().stream().map(DraftValue::valueId).toList());
    writer.count(graph.values().size());
    for (var value : graph.values()) {
      writer.text(value.valueId());
      writer.text(value.kind().name());
      text(writer, value.displayName());
      text(writer, value.literal());
      writer.flag(value.staticType().isPresent());
      value.staticType().ifPresent(type -> type(writer, type));
    }
    writer.text("entry");
    writer.text(graph.entry().nodeId());
    writer.count(graph.operationNodes().size());
    for (var index = 0; index < graph.operationNodes().size(); index++) {
      var operation = graph.operationNodes().get(index);
      writer.text(operation.nodeId());
      writer.text(operation.kind().name());
      writer.text(operation.detail());
      writer.count(operation.operands().size());
      operation.operands().forEach(operand -> {
        writer.integer(operand.position());
        writer.integer(owned(values, operand.value().valueId()));
      });
      writer.flag(operation.definedValue().isPresent());
      operation.definedValue().ifPresent(value -> writer.integer(owned(values, value.valueId())));
      writer.flag(operation.invocationSemantics().isPresent());
      operation.invocationSemantics().ifPresent(value -> {
        writer.text(value.mode().name());
        type(writer, value.qualifyingType().identity());
        text(writer, value.qualifyingType().declarationKey());
        writer.text(value.form().name());
        writer.text(value.selection().name());
      });
      writer.count(operation.semanticReferences().size());
      operation.semanticReferences().forEach(reference -> reference(writer, reference, declarationIdentities));
      strings(writer, operation.possibleExceptions());
      writer.count(operation.origins().size());
      operation.origins().forEach(origin -> {
        writer.text(origin.sourceFileKey());
        writer.longInteger(origin.startOffset());
        writer.longInteger(origin.endOffsetExclusive());
      });
      var source = sources.get(index);
      if (!source.body().equals(plan.body())) {
        throw new IllegalArgumentException("Graph canonical source association belongs to another body");
      }
      writer.flag(source.occurrence().isPresent());
      source.occurrence().ifPresent(value -> writer.text(value.nodeKey()));
      writer.text(source.generatedRole());
    }
    writer.text("exit");
    writer.text(graph.exit().nodeId());
    writer.count(graph.edges().size());
    graph.edges().forEach(edge -> {
      writer.text(edge.edgeId());
      writer.integer(owned(nodes, edge.sourceNodeId()));
      writer.integer(owned(nodes, edge.targetNodeId()));
      writer.text(edge.transferKind().name());
      text(writer, edge.discriminator());
    });
    writer.count(diagnostics.size());
    diagnostics.forEach(diagnostic -> {
      writer.text(diagnostic.diagnosticKey());
      writer.text(diagnostic.code());
      writer.text(diagnostic.stage().name());
      writer.text(diagnostic.severity().name());
      writer.text(diagnostic.message());
      writer.text(diagnostic.outcomeKind().name());
      writer.count(diagnostic.origins().size());
      diagnostic.origins().forEach(origin -> {
        writer.text(origin.sourceFileKey());
        writer.longInteger(origin.startOffset());
        writer.longInteger(origin.endOffsetExclusive());
      });
    });
    return writer.digest("cfg-content");
  }

  private static Map<String, Integer> ordinals(List<String> keys) {
    var result = new HashMap<String, Integer>();
    for (var index = 0; index < keys.size(); index++) {
      if (result.putIfAbsent(keys.get(index), index) != null) {
        throw new IllegalArgumentException("Graph canonical local keys must be unique");
      }
    }
    return result;
  }

  private static int owned(Map<String, Integer> roles, String key) {
    return Objects.requireNonNull(roles.get(key), "owned graph local role");
  }

  private static void text(CanonicalValueWriter writer, Optional<String> value) {
    writer.flag(value.isPresent());
    value.ifPresent(writer::text);
  }

  private static void strings(CanonicalValueWriter writer, List<String> values) {
    writer.count(values.size());
    values.forEach(writer::text);
  }

  private static void type(CanonicalValueWriter writer, JavaTypeIdentity type) {
    writer.text(type.category().name());
    writer.text(type.displayName());
    writer.text(type.erasedName());
  }

  private static void reference(CanonicalValueWriter writer, SemanticReference reference, boolean declarationIdentities) {
    writer.text(reference.referenceKind().name());
    writer.text(reference.status().name());
    text(writer, reference.symbolKey());
    text(writer, reference.typeName());
    strings(writer, reference.typeAlternativeNames());
    strings(writer, reference.assignableToTypeNames());
    if (declarationIdentities) {
      writer.flag(reference.declarationIdentity().isPresent());
      reference.declarationIdentity().ifPresent(identity -> {
        text(writer, identity.moduleName());
        writer.text(identity.binaryName());
      });
    }
  }
}
