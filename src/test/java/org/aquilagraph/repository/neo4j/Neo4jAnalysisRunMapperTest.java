package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisRun;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.StableId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Neo4jAnalysisRunMapperTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenSharedMapper_whenDifferentRunsMappedConcurrently_thenEachProjectionIsIsolated()
      throws Exception {
    // Arrange
    var sourceRoot = completedSourceRoot("concurrent-source");
    var firstDependency = Files.createDirectory(temporaryDirectory.resolve("first-dependency"));
    var secondDependency = Files.createDirectory(temporaryDirectory.resolve("second-dependency"));
    Files.writeString(firstDependency.resolve("marker.txt"), "first");
    Files.writeString(secondDependency.resolve("marker.txt"), "second");
    var firstRun = completedMapperFixture(
        new AnalysisEngine()
            .analyze(
                new JavaFrontend(new JavaSourceLoader())
                    .analyze(
                        sourceRoot,
                        new CompilerConfiguration(
                            JavaRelease.JAVA_25,
                            List.of(firstDependency),
                            List.of(),
                            List.of()))));
    var secondRun = completedMapperFixture(
        new AnalysisEngine()
            .analyze(
                new JavaFrontend(new JavaSourceLoader())
                    .analyze(
                        sourceRoot,
                        new CompilerConfiguration(
                            JavaRelease.JAVA_25,
                            List.of(secondDependency),
                            List.of(),
                            List.of()))));
    var mapper = new Neo4jAnalysisRunMapper();
    var runs = List.of(firstRun, secondRun);
    var expectedProjections =
        List.of(mapper.map(firstRun), mapper.map(secondRun));
    var workerCount = 8;
    var ready = new CountDownLatch(workerCount);
    var start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(workerCount)) {
      var pendingProjections = new ArrayList<Future<Neo4jGraphProjection>>();
      for (var worker = 0; worker < workerCount; worker++) {
        var run = runs.get(worker % runs.size());
        pendingProjections.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start projection");
                  }
                  return mapper.map(run);
                }));
      }
      assertTrue(ready.await(30, TimeUnit.SECONDS));

      // Act
      start.countDown();
      var actualProjections = new ArrayList<Neo4jGraphProjection>();
      for (var pendingProjection : pendingProjections) {
        actualProjections.add(pendingProjection.get(30, TimeUnit.SECONDS));
      }

      // Assert
      assertNotEquals(firstRun.runId(), secondRun.runId());
      for (var worker = 0; worker < workerCount; worker++) {
        assertEquals(
            expectedProjections.get(worker % expectedProjections.size()),
            actualProjections.get(worker),
            "Every concurrent call must produce its own complete projection");
      }
    }
  }

  @Test
  void givenCompletedSupportedRun_whenMapped_thenIdentityOriginsAndTypedFlowArePreserved()
      throws IOException {
    // Arrange
    var sourceRoot = completedSourceRoot("mapped-source");
    var dependency = Files.createDirectory(temporaryDirectory.resolve("dependency"));
    var run = completedMapperFixture(
        new AnalysisEngine()
            .analyze(
                new JavaFrontend(new JavaSourceLoader())
                    .analyze(
                        sourceRoot,
                        new CompilerConfiguration(
                            JavaRelease.JAVA_25, List.of(dependency), List.of(), List.of()))));

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(run);

    // Assert
    assertTrue(projection.nodes().stream().allMatch(node -> node.properties().get("runId").equals(run.runId())));
    assertTrue(
        projection.relationships().stream()
            .allMatch(relationship -> relationship.properties().get("runId").equals(run.runId())));
    assertTrue(projection.nodes().stream().anyMatch(node -> node.label().equals("SourceOrigin")));
    assertTrue(projection.nodes().stream().anyMatch(node -> node.label().equals("FlowValue")));
    assertTrue(projection.nodes().stream().filter(node -> node.label().equals("FlowValue"))
        .anyMatch(node -> node.properties().containsKey("staticTypeKey")
            && node.properties().containsKey("staticTypeErasure")
            && node.properties().containsKey("staticTypeCategory")));
    assertTrue(projection.nodes().stream().filter(node -> node.label().equals("JavaParameter"))
        .anyMatch(node -> node.properties().containsKey("staticTypeKey")
            && node.properties().containsKey("staticTypeErasure")
            && node.properties().containsKey("staticTypeCategory")));
    assertTrue(
        projection.nodes().stream()
            .anyMatch(node -> node.label().equals("AnalysisEnvironmentSnapshot")));
    assertTrue(
        projection.nodes().stream().anyMatch(node -> node.label().equals("DependencySnapshot")));
    assertTrue(
        projection.nodes().stream()
            .filter(node -> node.label().equals("OperationNode"))
            .anyMatch(node -> node.properties().containsKey("kind")));
    assertTrue(
        projection.nodes().stream()
            .filter(node -> node.label().equals("SemanticReference"))
            .anyMatch(node -> node.properties().get("typeAlternativeNames") instanceof List<?> types && !types.isEmpty()));
    assertTrue(
        projection.nodes().stream()
            .filter(node -> node.label().equals("SemanticReference"))
            .anyMatch(node -> node.properties().get("assignableToTypeNames") instanceof List<?> types && !types.isEmpty()));
    assertFalse(
        projection.nodes().stream()
            .anyMatch(node -> Set.of("MethodBody", "SyntaxNode").contains(node.label())));
    assertTrue(
        projection.nodes().stream()
            .filter(node -> node.label().equals("JavaMethod"))
            .anyMatch(node -> Boolean.TRUE.equals(node.properties().get("hasBody"))));
    assertTrue(projection.relationships().stream().anyMatch(relation -> relation.type().equals("IN_SOURCE")));
    var operationKeys =
        projection.nodes().stream()
            .filter(node -> node.label().equals("OperationNode"))
            .map(Neo4jNode::key)
            .collect(Collectors.toSet());
    assertTrue(
        projection.relationships().stream()
            .anyMatch(
                relation ->
                    relation.type().equals("HAS_REFERENCE")
                        && operationKeys.contains(relation.sourceKey())));
    assertTrue(
        projection.relationships().stream()
            .anyMatch(
                relation ->
                    relation.type().equals("HAS_ORIGIN")
                        && operationKeys.contains(relation.sourceKey())));
    assertTrue(
        projection.relationships().stream()
            .anyMatch(relation -> relation.type().equals("HAS_ENVIRONMENT")));
    assertTrue(
        projection.relationships().stream()
            .anyMatch(relation -> relation.type().equals("HAS_CLASS_PATH_ENTRY")));
    var analysisRunNode = projection.nodes().stream().filter(node -> node.label().equals("AnalysisRun")).findFirst().orElseThrow();
    assertEquals(Set.of("runId", "key", "completion"), analysisRunNode.properties().keySet());
    assertFalse(
        projection.relationships().stream()
            .anyMatch(
                relation ->
                    Set.of(
                            "HAS_BODY",
                            "HAS_SYNTAX_ROOT",
                            "CHILD",
                            "HAS_OPERATION",
                            "REPRESENTS_OPERATION")
                        .contains(relation.type())));
    var flowTypes =
        projection.relationships().stream()
            .map(Neo4jRelationship::type)
            .filter(type -> type.endsWith("_FLOW"))
            .collect(Collectors.toSet());
    assertTrue(flowTypes.containsAll(Set.of("NORMAL_FLOW", "RETURN_FLOW", "EXCEPTIONAL_FLOW")));
    assertEquals(run.runId(), projection.runId());
  }

  @Test
  void givenSourceField_whenCompletedRunIsMapped_thenDeclarationTypeOwnershipAndOriginArePreserved() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("FieldOwner.java"),
        "final class FieldOwner { private String value; String read() { return value; } }");
    var run = completedMapperFixture(new AnalysisEngine().analyze(new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25))));

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(run);

    // Assert
    var field = projection.nodes().stream().filter(node -> node.label().equals("JavaField")).findFirst().orElseThrow();
    assertEquals("value", field.properties().get("name"));
    assertEquals("java.lang.String", field.properties().get("staticTypeErasure"));
    assertTrue(projection.relationships().stream().anyMatch(relation -> relation.type().equals("DECLARES_FIELD")
        && relation.targetKey().equals(field.key())));
    assertTrue(projection.relationships().stream().anyMatch(relation -> relation.type().equals("HAS_ORIGIN")
        && relation.sourceKey().equals(field.key())));
  }

  @Test
  void givenRecordComponentsAndImplicitEnumMembers_whenMapped_thenIndependentDeclarationFactsSurvive() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Declarations.java"), """
        record Name(@Deprecated String first, String... rest) {}
        enum Empty { ; }
        """);
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var run = completedMapperFixture(new AnalysisEngine().analyze(input));
    var record = input.program().types().stream().filter(type -> type.qualifiedName().equals("Name")).findFirst().orElseThrow();
    var enumType = input.program().types().stream().filter(type -> type.qualifiedName().equals("Empty")).findFirst().orElseThrow();

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(run);

    // Assert
    var componentNodes = projection.nodes().stream().filter(node -> node.label().equals("JavaRecordComponent")).toList();
    assertEquals(record.recordComponents().stream().map(component -> component.componentKey()).toList(),
        componentNodes.stream().map(Neo4jNode::key).toList());
    assertEquals(List.of("first", "rest"), componentNodes.stream().map(node -> node.properties().get("name")).toList());
    assertEquals(List.of(false, true), componentNodes.stream().map(node -> node.properties().get("varArgs")).toList());
    for (var ordinal = 0; ordinal < componentNodes.size(); ordinal++) {
      var component = record.recordComponents().get(ordinal);
      var node = componentNodes.get(ordinal);
      assertEquals(Set.of("runId", "key", "name", "position", "varArgs", "writtenModifiers", "effectiveModifiers", "staticType",
          "staticTypeKey", "staticTypeErasure", "staticTypeCategory", "accessorKey", "backingFieldKey", "canonicalParameterKey"),
          node.properties().keySet());
      assertEquals(run.runId(), node.properties().get("runId"));
      assertEquals(component.componentKey(), node.properties().get("key"));
      assertEquals(List.of(component.name(), component.ordinal(), component.varArgs(),
          component.writtenModifiers().stream().map(Enum::name).sorted().toList(),
          component.effectiveModifiers().stream().map(Enum::name).sorted().toList(), component.staticType().displayName(),
          component.staticType().typeKey(), component.staticType().erasedName(), component.staticType().category().name(),
          component.accessorKey().orElseThrow(), component.backingFieldKey().orElseThrow(),
          component.canonicalParameterKey().orElseThrow()),
          List.of(node.properties().get("name"), node.properties().get("position"), node.properties().get("varArgs"),
              node.properties().get("writtenModifiers"), node.properties().get("effectiveModifiers"),
              node.properties().get("staticType"), node.properties().get("staticTypeKey"),
              node.properties().get("staticTypeErasure"), node.properties().get("staticTypeCategory"),
              node.properties().get("accessorKey"), node.properties().get("backingFieldKey"),
              node.properties().get("canonicalParameterKey")));
      var origin = component.origins().getFirst();
      var originKey = StableId.sha256("source-origin", component.componentKey(), "0", origin.sourceFileKey(),
          Long.toString(origin.startOffset()), Long.toString(origin.endOffsetExclusive()));
      var originNode = projection.nodes().stream().filter(candidate -> candidate.key().equals(originKey)).findFirst().orElseThrow();
      assertEquals(List.of(origin.startOffset(), origin.endOffsetExclusive(), 0),
          List.of(originNode.properties().get("startOffset"), originNode.properties().get("endOffsetExclusive"),
              originNode.properties().get("position")));
      assertTrue(projection.relationships().stream().anyMatch(relation -> relation.type().equals("HAS_ORIGIN")
          && relation.sourceKey().equals(component.componentKey()) && relation.targetKey().equals(originKey)));
      assertTrue(projection.relationships().stream().anyMatch(relation -> relation.type().equals("IN_SOURCE")
          && relation.sourceKey().equals(originKey) && relation.targetKey().equals(origin.sourceFileKey())));
    }
    assertEquals(record.recordComponents().stream().map(component -> List.of(record.typeKey(), component.componentKey(),
        component.ordinal())).toList(), projection.relationships().stream()
        .filter(relation -> relation.type().equals("DECLARES_RECORD_COMPONENT"))
        .map(relation -> List.of(relation.sourceKey(), relation.targetKey(), relation.properties().get("position"))).toList());
    assertFalse(enumType.synthesizedDeclarations().isEmpty());
    assertEquals(input.program().types().stream().flatMap(type -> type.synthesizedDeclarations().stream())
        .map(declaration -> declaration.declarationKey()).toList(),
        projection.nodes().stream().filter(node -> node.label().equals("JavaSynthesizedDeclaration"))
            .map(Neo4jNode::key).toList());
    assertTrue(projection.relationships().stream().anyMatch(relation -> relation.type().equals("DECLARES_SYNTHESIZED")
        && relation.sourceKey().equals(enumType.typeKey())));
    assertTrue(projection.nodes().stream().filter(node -> node.label().equals("JavaSynthesizedDeclaration"))
        .allMatch(node -> Boolean.FALSE.equals(node.properties().get("sourceDeclared"))));
    var canonicalKeys = record.recordComponents().stream().map(component -> component.canonicalParameterKey().orElseThrow()).toList();
    assertEquals(canonicalKeys, projection.nodes().stream().filter(node -> node.label().equals("JavaParameter"))
        .map(Neo4jNode::key).filter(canonicalKeys::contains).toList());
    assertTrue(projection.nodes().stream().filter(node -> node.label().equals("JavaParameter"))
        .filter(node -> canonicalKeys.contains(node.key()))
        .allMatch(node -> Boolean.FALSE.equals(node.properties().get("sourceDeclared"))));
  }

  @Test
  void givenTypeRelationshipsAndInvocationSemantics_whenCompletedRunIsMapped_thenTypedFactsSurviveProjection() throws IOException {
    // Arrange
    var sourceRoot = Files.createDirectory(temporaryDirectory.resolve("typed-projection"));
    Files.writeString(sourceRoot.resolve("Projected.java"), """
        sealed interface Contract<T extends Number & Comparable<T>> permits Projected { }
        class Base { }
        final class Projected extends Base implements Contract<Integer> {
          String call(Contract<Integer> value) { return value.toString(); }
        }
        class Owner { class Nested { } }
        """);
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var run = completedMapperFixture(new AnalysisEngine().analyze(input));

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(run);

    // Assert
    assertTrue(projection.relationships().stream().anyMatch(relation -> relation.type().equals("HAS_DIRECT_SUPERCLASS")));
    assertTrue(projection.relationships().stream().anyMatch(relation -> relation.type().equals("HAS_DIRECT_INTERFACE")));
    assertTrue(projection.relationships().stream().anyMatch(relation -> relation.type().equals("PERMITS_SUBCLASS")));
    assertTrue(projection.relationships().stream().anyMatch(relation -> relation.type().equals("ENCLOSED_BY")));
    assertTrue(projection.relationships().stream().anyMatch(relation -> relation.type().equals("DECLARES_TYPE_PARAMETER")));
    assertEquals(2, projection.relationships().stream().filter(relation -> relation.type().equals("HAS_TYPE_BOUND")).count());
    assertTrue(projection.nodes().stream().filter(node -> node.label().equals("JavaTypeReference"))
        .allMatch(node -> node.properties().containsKey("typeKey") && node.properties().containsKey("typeErasure")
            && node.properties().containsKey("typeCategory") && node.properties().containsKey("declarationKey")));
    var invocation = projection.nodes().stream().filter(node -> node.label().equals("OperationNode"))
        .filter(node -> node.properties().get("kind").equals("INVOKE") && node.properties().get("detail").equals("toString"))
        .findFirst().orElseThrow();
    assertEquals("VIRTUAL", invocation.properties().get("invocationMode"));
    assertEquals("INSTANCE_EXPRESSION", invocation.properties().get("invocationForm"));
    assertEquals("type:java.lang.Object", invocation.properties().get("qualifyingTypeDeclarationKey"));
    assertEquals("java.lang.Object", invocation.properties().get("qualifyingTypeErasure"));
  }

  @Test
  void givenRejectedRun_whenMapped_thenRunIsRejected() throws IOException {
    // Arrange
    var run =
        new AnalysisEngine()
            .analyze(
                new JavaFrontend(new JavaSourceLoader())
                    .analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25)));
    var mapper = new Neo4jAnalysisRunMapper();

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> mapper.map(run));

    // Assert
    assertEquals("Only completed analysis runs may be stored", exception.getMessage());
  }

  private Path completedSourceRoot(String directoryName) throws IOException {
    var sourceRoot = Files.createDirectory(temporaryDirectory.resolve(directoryName));
    Files.writeString(sourceRoot.resolve("Persisted.java"), """
        final class Persisted {
          int field;
          int evaluate(String input, int denominator) {
            try {
              return input.length() / denominator;
            } catch (ArithmeticException exception) {
              return field;
            }
          }
        }
        """);
    return sourceRoot;
  }

  private static AnalysisRun completedMapperFixture(AnalysisRun analyzedRun) {
    return new AnalysisRun(analyzedRun.runId(), analyzedRun.inputSnapshot(), analyzedRun.environmentSnapshot(),
        analyzedRun.representation(), List.of(), AnalysisCompletion.COMPLETED);
  }
}
