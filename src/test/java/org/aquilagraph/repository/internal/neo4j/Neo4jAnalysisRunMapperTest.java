package org.aquilagraph.repository.internal.neo4j;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;



import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstProductionRequest;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.PublicationEligibility.Impediment;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.internal.SourceAnalysisFixture;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Neo4jAnalysisRunMapperTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenNamedUnnamedAndAbsentTypedTargets_whenReferencesAreMapped_thenCompleteValuesAndHistoricalAbsenceArePreserved() {
    // Arrange
    var named = new JavaTypeDeclarationIdentity(Optional.of("service.sample"), "api.Service");
    var unnamed = new JavaTypeDeclarationIdentity(Optional.empty(), "api.Service");
    var plain = new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.RESOLVED, Optional.of(named.declarationKey()), Optional.of("api.Service"), List.of("api.Service"), List.of("api.Service"), Optional.empty());
    var namedReference = new SemanticReference(plain.referenceKind(), plain.status(), plain.symbolKey(), plain.typeName(), plain.typeAlternativeNames(), plain.assignableToTypeNames(), Optional.of(named));
    var unnamedReference = new SemanticReference(plain.referenceKind(), plain.status(), Optional.of(unnamed.declarationKey()), plain.typeName(), plain.typeAlternativeNames(), plain.assignableToTypeNames(), Optional.of(unnamed));
    var builder = new Neo4jProjectionBuilder("analysis");
    builder.node("OperationNode", "operation", "kind", "CONSTANT");
    // Act
    new Neo4jEvidenceMapper().mapSemanticReferences("operation", List.of(plain, namedReference, unnamedReference), builder);
    var projection = builder.build();
    // Assert
    var references = projection.nodes().stream().filter(node -> node.label().equals("SemanticReference")).toList();
    assertEquals(3, references.size());
    var absent = references.getFirst().properties();
    assertEquals(Set.of("runId", "key", "kind", "status", "position", "symbolKey", "typeName", "typeAlternativeNames", "assignableToTypeNames"), absent.keySet());
    var namedProperties = references.get(1).properties();
    assertEquals("NAMED", namedProperties.get("declarationModuleKind"));
    assertEquals("service.sample", namedProperties.get("declarationModuleName"));
    assertEquals("api.Service", namedProperties.get("declarationBinaryName"));
    var unnamedProperties = references.getLast().properties();
    assertEquals("UNNAMED", unnamedProperties.get("declarationModuleKind"));
    assertFalse(unnamedProperties.containsKey("declarationModuleName"));
    assertEquals("api.Service", unnamedProperties.get("declarationBinaryName"));
    assertEquals(List.of(0, 1, 2), projection.relationships().stream().map(relation -> relation.properties().get("position")).toList());
  }

  @Test
  void givenEqualDiagnosticValuesInTwoSlots_whenRunIsMapped_thenOneValueAndBothOrderedSlotsArePreserved() throws IOException {
    // Arrange
    var sourceRoot = completedSourceRoot("diagnostics");
    var original = completedMapperFixture(new SourceAnalysisFixture().analyze(new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow()));
    var origin = new SourceSpan(original.representation().ast().capturedInput().orElseThrow().sourceFiles().getFirst().sourceFileKey(), 0, 1);
    var first = diagnostic("diagnostic", "Observed source", List.of(origin));
    var second = diagnostic("diagnostic", "Observed source", List.of(origin));
    var run = AnalysisRun.create(original.identity(), original.representation(), List.of(first, second));

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(run);

    // Assert
    assertEquals(1, projection.nodes().stream().filter(node -> node.label().equals("Diagnostic")).count());
    assertEquals(List.of(0, 1), projection.relationships().stream().filter(relation -> relation.type().equals("HAS_DIAGNOSTIC"))
        .map(relation -> relation.properties().get("position")).toList());
    assertEquals(1, projection.relationships().stream().filter(relation -> relation.type().equals("HAS_ORIGIN")
        && relation.sourceKey().equals(first.diagnosticKey())).count());
  }

  @Test
  void givenUnequalDiagnosticValuesWithOneKey_whenRunIsMapped_thenCollisionIsRejected() throws IOException {
    // Arrange
    var original = completedMapperFixture(new SourceAnalysisFixture().analyze(new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(
        completedSourceRoot("collision"), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow()));
    var run = AnalysisRun.create(original.identity(), original.representation(), List.of(diagnostic("diagnostic", "First observation", List.of()), diagnostic("diagnostic", "Second observation", List.of())));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new Neo4jAnalysisRunMapper().map(run));

    // Assert
    assertEquals("Equal diagnostic keys require equal diagnostic values and ordered origins", failure.getMessage());
  }

  @Test
  void givenDiagnosticKeyWithReorderedOrigins_whenRunIsMapped_thenUnequalOrderedEvidenceIsRejected() throws IOException {
    // Arrange
    var original = completedMapperFixture(new SourceAnalysisFixture().analyze(new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(
        completedSourceRoot("ordered-origins"), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow()));
    var sourceFileKey = original.representation().ast().capturedInput().orElseThrow().sourceFiles().getFirst().sourceFileKey();
    var first = new SourceSpan(sourceFileKey, 0, 1);
    var second = new SourceSpan(sourceFileKey, 1, 2);
    var run = AnalysisRun.create(original.identity(), original.representation(), List.of(diagnostic("diagnostic", "Observed source", List.of(first, second)),
            diagnostic("diagnostic", "Observed source", List.of(second, first))));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new Neo4jAnalysisRunMapper().map(run));

    // Assert
    assertEquals("Equal diagnostic keys require equal diagnostic values and ordered origins", failure.getMessage());
  }

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
        new SourceAnalysisFixture()
            .analyze(
                new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(
                        sourceRoot,
                        new CompilerConfiguration(
                            JavaRelease.JAVA_25,
                            List.of(capturedAbsolute(firstDependency)),
                            List.of(),
                            List.of()), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow()));
    var secondRun = completedMapperFixture(
        new SourceAnalysisFixture()
            .analyze(
                new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(
                        sourceRoot,
                        new CompilerConfiguration(
                            JavaRelease.JAVA_25,
                            List.of(capturedAbsolute(secondDependency)),
                            List.of(),
                            List.of()), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow()));
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
      assertNotEquals(firstRun.identity().key(), secondRun.identity().key());
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
        new SourceAnalysisFixture()
            .analyze(
                new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(
                        sourceRoot,
                        new CompilerConfiguration(
                            JavaRelease.JAVA_25, List.of(capturedAbsolute(dependency)), List.of(), List.of()), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow()));

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(run);

    // Assert
    assertTrue(projection.nodes().stream().allMatch(node -> node.properties().get("runId").equals(run.identity().key())));
    assertTrue(
        projection.relationships().stream()
            .allMatch(relationship -> relationship.properties().get("runId").equals(run.identity().key())));
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
    assertEquals(Set.of("runId", "key", "completion", "astResultKey", "operation"), analysisRunNode.properties().keySet());
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
    assertEquals(run.identity().key(), projection.runId());
  }

  @Test
  void givenSourceField_whenCompletedRunIsMapped_thenDeclarationTypeOwnershipAndOriginArePreserved() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("FieldOwner.java"),
        "final class FieldOwner { private String value; String read() { return value; } }");
    var run = completedMapperFixture(new SourceAnalysisFixture().analyze(new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(temporaryDirectory,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow()));

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
    var input = new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(temporaryDirectory,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow();
    var run = completedMapperFixture(new SourceAnalysisFixture().analyze(input));
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
      assertEquals(run.identity().key(), node.properties().get("runId"));
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
    var input = new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow();
    var run = completedMapperFixture(new SourceAnalysisFixture().analyze(input));

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
        new SourceAnalysisFixture()
            .analyze(
                new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)));
    var mapper = new Neo4jAnalysisRunMapper();

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> mapper.map(run));

    // Assert
    assertEquals("Only completed analysis runs may be stored", exception.getMessage());
  }

  @Test
  void givenCompleteSourceWithFullOrIncompleteSelection_whenMapping_thenAdmissionAgreesWithPublicEligibility() {
    // Arrange
    var request = new AstProductionRequest(InputSnapshot.fromSources(capturedAbsolute(temporaryDirectory), List.of(SourceFile.fromContent(
        capturedPath(Path.of("Example.java")), "interface Example { static int first() { return 1; } static int second() { return 2; } }"))),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED);
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(request);
    var result = ast.result().orElseThrow();
    var keys = result.program().methods().stream().filter(JavaMethod::hasBody).map(JavaMethod::methodKey).toList();
    var derivation = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard());
    var full = derivation.deriveExisting(ast, new CfgRequest(result.identity(), keys, CfgProfile.JAVA_BOUNDED));
    var partial = derivation.deriveExisting(ast, new CfgRequest(result.identity(), keys.subList(0, 1), CfgProfile.JAVA_BOUNDED));
    var empty = derivation.deriveExisting(ast, new CfgRequest(result.identity(), List.of(), CfgProfile.JAVA_BOUNDED));
    var absent = AnalysisCapabilities.computation(ignored -> ast, CfgDerivers.standard())
        .analyze(new AnalysisComputationRequest(request, Optional.empty()));
    var mapper = new Neo4jAnalysisRunMapper();

    // Act
    var projection = mapper.map(full);
    var partialFailure = assertThrows(IllegalArgumentException.class, () -> mapper.map(partial));
    var emptyFailure = assertThrows(IllegalArgumentException.class, () -> mapper.map(empty));
    var absentFailure = assertThrows(IllegalArgumentException.class, () -> mapper.map(absent));

    // Assert
    assertTrue(full.publicationEligibility().eligible());
    assertEquals(List.of(), full.publicationEligibility().impediments());
    assertTrue(projection.nodes().stream().anyMatch(node -> node.label().equals("AnalysisRun")
        && node.key().equals(full.identity().key())));
    assertEquals(List.of(Impediment.BODY_COVERAGE_INCOMPLETE), partial.publicationEligibility().impediments());
    assertEquals(List.of(Impediment.BODY_COVERAGE_INCOMPLETE), empty.publicationEligibility().impediments());
    assertEquals(List.of(Impediment.CFG_NOT_COMPLETE), absent.publicationEligibility().impediments());
    assertEquals("Only completed analysis runs may be stored", partialFailure.getMessage());
    assertEquals(partialFailure.getMessage(), emptyFailure.getMessage());
    assertEquals(partialFailure.getMessage(), absentFailure.getMessage());
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
    return AnalysisRun.create(analyzedRun.identity(), analyzedRun.representation(), List.of());
  }

  private static AnalysisDiagnostic diagnostic(String key, String message, List<SourceSpan> origins) {
    return new AnalysisDiagnostic(key, "frontend.observation", DiagnosticStage.FRONTEND, DiagnosticSeverity.WARNING, message, origins,
        AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
  }
}
