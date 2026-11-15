package org.aquilagraph.retention.consumer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.ControlFlowGraph;
import org.aquilagraph.identity.api.CapturedLocation;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.ExecutionSyntax;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxNodeKey;

/** Deterministic test observation of exported values, without retained-format privileges. */
public final class PublicAstObservation {
  private PublicAstObservation() {}

  public static String describe(AstResult ast) {
    var bodies = ast.program().methods().stream().filter(method -> ast.body(method.methodKey()).isPresent())
        .map(method -> method.methodKey()).toList();
    var fields = new ArrayList<Object>();
    fields.add(ast);
    if (!bodies.isEmpty()) {
      fields.add(CfgDerivers.standard().derive(ast, new CfgRequest(ast.identity(), bodies, CfgProfile.JAVA_BOUNDED)));
    }
    return render(fields);
  }

  private static String render(Object value) {
    return switch (value) {
      case AstResult ast -> tuple("ast", ast.identity(), ast.profile(), ast.completion(), ast.qualification(), ast.qualification().reasons(), ast.snapshot(), ast.environment(),
          ast.program(), ast.aggregate(), ast.diagnostics(), ast.coverage());
      case JavaAstAggregate aggregate -> tuple("aggregate", aggregate.roots().stream().map(SyntaxNode::nodeId).toList(), aggregate.nodes(),
          aggregate.nodeTuples(), aggregate.rangeTuples(), aggregate.structuralEdges(), aggregate.semanticEdges(),
          aggregate.executionScopes().stream().map(scope -> aggregate.executionSyntax(scope.key()).orElseThrow()).toList(),
          aggregate.declarationMemberOrders(), aggregate.writtenSourceAssociations(), aggregate.declarationTypeEvidence());
      case ExecutionSyntax syntax -> tuple("execution", syntax.scope(), syntax.root().nodeId(), syntax.attributedOccurrences(), syntax.evidence());
      case SyntaxNode node -> tuple("node", node.nodeId(), node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(),
          node.type(), node.value(), node.children().stream().map(child -> tuple("child", child.role(), child.position(), child.node().nodeId())).toList(),
          node.semanticReference(), node.origin());
      case LanguageFeatureCoverageResult coverage -> tuple("coverage-result", coverage.coverage(), coverage.occurrences(), coverage.observations(),
          coverage.capabilityDiagnostics(), coverage.validationEvidence());
      case LanguageFeatureCoverage coverage -> tuple("coverage", coverage.release(), coverage.classifications(), coverage.contractIdentity());
      case AnalysisDiagnostic diagnostic -> tuple("diagnostic", diagnostic.diagnosticKey(), diagnostic.code(), diagnostic.stage(),
          diagnostic.severity(), diagnostic.message(), diagnostic.origins(), diagnostic.outcomeKind());
      case ControlFlowGraph graph -> tuple("graph", graph.identity(), graph.body(), graph.graphId(), graph.methodKey(), graph.entry(),
          graph.operationNodes(), graph.values(), graph.exit(), graph.edges(), graph.diagnostics());
      case SyntaxNodeKey key -> atom("node-key", key.value());
      case Optional<?> optional -> tuple("optional", optional.stream().toList());
      case Set<?> values -> tuple("set", values.stream().map(PublicAstObservation::render).sorted().toList());
      case Map<?, ?> values -> tuple("map", values.entrySet().stream().map(entry -> tuple("entry", entry.getKey(), entry.getValue())).sorted().toList());
      case List<?> values -> atom("list", String.join("", values.stream().map(PublicAstObservation::render).toList()));
      case Enum<?> enumeration -> atom(enumeration.getDeclaringClass().getName(), enumeration.name());
      case CapturedLocation location -> tuple("captured-location", location.syntax(), location.rootKind(), location.rootComponents(),
          location.components(), location.spelling());
      case String text -> atom("text", text);
      case Number number -> atom(number.getClass().getSimpleName(), number.toString());
      case Boolean flag -> atom("flag", flag.toString());
      case Character character -> atom("character", character.toString());
      default -> publicValue(value);
    };
  }

  private static String publicValue(Object value) {
    var type = value.getClass();
    if (!Modifier.isPublic(type.getModifiers()) || !Set.of("org.aquilagraph.identity.api", "org.aquilagraph.source.api", "org.aquilagraph.cfg.api")
        .contains(type.getPackageName())) {
      throw new IllegalArgumentException("Unobserved exported value: " + type.getName());
    }
    var fields = new ArrayList<Object>();
    var accessors = type.isRecord()
        ? Arrays.stream(type.getRecordComponents()).map(component -> component.getAccessor()).toList()
        : Arrays.stream(type.getMethods()).filter(method -> !Modifier.isStatic(method.getModifiers()) && !method.isBridge() && !method.isSynthetic() && method.getParameterCount() == 0
            && method.getReturnType() != void.class && method.getDeclaringClass() != Object.class
            && !Set.of("hashCode", "toString").contains(method.getName())).sorted(Comparator.comparing(method -> method.getName())).toList();
    if (accessors.isEmpty()) {
      throw new IllegalArgumentException("Exported value has no readable properties: " + type.getName());
    }
    for (var accessor : accessors) {
      fields.add(accessor.getName());
      try {
        fields.add(accessor.invoke(value));
      } catch (IllegalAccessException | InvocationTargetException exception) {
        throw new IllegalStateException("Exported value accessor is unavailable: " + accessor, exception);
      }
    }
    return tuple(type.getName(), fields);
  }

  private static String tuple(String name, Object... values) {
    return atom(name, render(List.of(values)));
  }

  private static String atom(String name, String text) {
    return name.length() + ":" + name + text.length() + ":" + text;
  }
}
