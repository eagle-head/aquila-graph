package org.aquilagraph.repository.internal.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.EntryNode;
import org.aquilagraph.cfg.api.ExitNode;
import org.aquilagraph.cfg.api.OperationNode;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.DependencySnapshot;
import org.aquilagraph.identity.api.DependencySemantics;
import org.aquilagraph.identity.api.EnvironmentIdentityScheme;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Neo4jStoredSurfaceContractTest {
  private static final Set<String> RUN_LABELS = Set.of("AnalysisRun", "SoftwareVersion");
  private static final Set<String> INPUT_LABELS = Set.of("InputSnapshot", "SourceFile");
  private static final Set<String> ENVIRONMENT_LABELS = Set.of("AnalysisEnvironmentSnapshot", "CompilerConfiguration", "DependencySnapshot");
  private static final Set<String> EVIDENCE_LABELS = Set.of("Diagnostic", "SemanticReference", "SourceOrigin");
  private static final Set<String> PROGRAM_LABELS = Set.of("Program", "JavaType", "JavaField", "JavaRecordComponent",
      "JavaSynthesizedDeclaration", "JavaMethod", "JavaParameter", "JavaTypeParameter", "JavaTypeReference");
  private static final Set<String> FLOW_LABELS = Set.of("MethodAnalysis", "ControlFlowGraph", "FlowValue", "EntryNode", "OperationNode", "ExitNode");

  @TempDir Path temporaryDirectory;

  @Test
  void givenSuppliedRunAndVersion_whenMapped_thenDistinctIdentitiesAndSoftwareOwnershipArePreserved() {
    // Arrange
    var run = suppliedRun();
    var expected = expectedSurface(run);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(run);

    // Assert
    assertFamily(expected, projection, RUN_LABELS, Set.of("ANALYZED_SOFTWARE"));
  }

  @Test
  void givenCapturedSourceValues_whenMapped_thenExactContentPathProvenanceAndUnpositionedSourceEdgesArePreserved() {
    // Arrange
    var run = suppliedRun();
    var expected = expectedSurface(run);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(run);

    // Assert
    assertFamily(expected, projection, INPUT_LABELS, Set.of("HAS_INPUT", "HAS_SOURCE"));
  }

  @Test
  void givenOrderedCompilerDependencies_whenMapped_thenConfigurationProvenanceRolesCountsAndPositionsArePreserved() throws IOException {
    // Arrange
    var first = Files.createDirectory(temporaryDirectory.resolve("first-dependency"));
    var second = Files.createDirectory(temporaryDirectory.resolve("second-dependency"));
    Files.writeString(first.resolve("marker.txt"), "alpha");
    Files.writeString(second.resolve("marker.txt"), "beta");
    var run = producedRun(temporaryDirectory.resolve("source-root"), List.of(first, second, first), List.of(first));
    var expected = expectedSurface(run);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(run);

    // Assert
    var environment = run.representation().ast().capturedEnvironment().orElseThrow();
    assertEquals(List.of(5L, 4L, 5L), environment.classPath().stream().map(value -> value.byteCount()).toList());
    assertEquals(List.of(1, 1, 1), environment.classPath().stream().map(value -> value.entryCount()).toList());
    assertEquals(environment.classPath().getFirst().fingerprint(), environment.modulePath().getFirst().fingerprint());
    assertEquals(DependencySemantics.State.CLASS_PATH, environment.classPath().getFirst().semantics().state());
    assertEquals(DependencySemantics.State.MODULES, environment.modulePath().getFirst().semantics().state());
    assertEquals(4, nodesWithLabel(projection, "DependencySnapshot").size());
    assertEquals(3, projection.relationships().stream().filter(value -> value.type().equals("HAS_CLASS_PATH_ENTRY")).count());
    assertEquals(1, projection.relationships().stream().filter(value -> value.type().equals("HAS_MODULE_PATH_ENTRY")).count());
    assertFamily(expected, projection, ENVIRONMENT_LABELS,
        Set.of("HAS_ENVIRONMENT", "HAS_CONFIGURATION", "HAS_CLASS_PATH_ENTRY", "HAS_MODULE_PATH_ENTRY"));
  }

  @Test
  void givenRepeatedDiagnosticSlotsAndOptionalEvidence_whenMapped_thenExactValuesOriginsReferencesAndSlotPositionsArePreserved() {
    // Arrange
    var run = suppliedRun();
    var expected = expectedSurface(run);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(run);

    // Assert
    assertFamily(expected, projection, EVIDENCE_LABELS, Set.of("HAS_DIAGNOSTIC", "HAS_REFERENCE", "HAS_ORIGIN", "IN_SOURCE"));
  }

  @Test
  void givenCanonicalDeclarationsAndOptionalHierarchyLinks_whenMapped_thenExactTypedFactsOwnershipAndDeclarationPositionsArePreserved() {
    // Arrange
    var run = suppliedRun();
    var expected = expectedSurface(run);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(run);

    // Assert
    assertFamily(expected, projection, PROGRAM_LABELS, Set.of("HAS_PROGRAM", "DECLARES_TYPE", "DECLARES_FIELD",
        "DECLARES_RECORD_COMPONENT", "DECLARES_SYNTHESIZED", "DECLARES_METHOD", "DECLARES_PARAMETER", "DECLARES_TYPE_PARAMETER",
        "ENCLOSED_BY", "HAS_DIRECT_SUPERCLASS", "HAS_DIRECT_INTERFACE", "PERMITS_SUBCLASS", "HAS_TYPE_BOUND"));
  }

  @Test
  void givenSuppliedGraphValuesInvocationsAndAllTransferKinds_whenMapped_thenExactGraphFactsAndUnpositionedTransfersArePreserved() {
    // Arrange
    var run = suppliedRun();
    var expected = expectedSurface(run);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(run);

    // Assert
    assertFamily(expected, projection, FLOW_LABELS, Set.of("HAS_METHOD_ANALYSIS", "ANALYZES_METHOD", "HAS_CFG", "HAS_VALUE", "HAS_NODE",
        "USES_VALUE", "DEFINES_VALUE", "NORMAL_FLOW", "TRUE_FLOW", "FALSE_FLOW", "SWITCH_FLOW", "RETURN_FLOW", "EXCEPTIONAL_FLOW"));
  }

  @Test
  void givenCompleteSuppliedSurface_whenMapped_thenOnlyTheExactDurableNodesAndRelationshipsAreEmitted() {
    // Arrange
    var run = suppliedRun();
    var expected = expectedSurface(run);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(run);

    // Assert
    assertEquals(run.identity().key(), projection.runId());
    assertEquals(expected.nodes, actualNodes(projection));
    assertEquals(expected.relationships, actualRelationships(projection));
  }

  @Test
  void givenRelocatedInputsWithUnchangedOptions_whenMapped_thenIdentityIsRetainedWhileCapturedRootChanges() throws IOException {
    // Arrange
    var original = suppliedRun();
    var relocated = relocate(original, false);

    // Act
    var originalProjection = new Neo4jAnalysisRunMapper().map(original);
    var relocatedProjection = new Neo4jAnalysisRunMapper().map(relocated);

    // Assert
    assertEquals(original.representation().ast().capturedEnvironment().orElseThrow().environmentDigest(), relocated.representation().ast().capturedEnvironment().orElseThrow().environmentDigest());
    assertEquals(SoftwareVersion.from(original.representation().ast().capturedInput().orElseThrow(), original.representation().ast().capturedEnvironment().orElseThrow()), SoftwareVersion.from(relocated.representation().ast().capturedInput().orElseThrow(), relocated.representation().ast().capturedEnvironment().orElseThrow()));
    assertEquals(original.identity().key(), relocated.identity().key());
    assertEquals(original.representation().ast().capturedInput().orElseThrow().snapshotDigest(), relocated.representation().ast().capturedInput().orElseThrow().snapshotDigest());
    assertEquals(nodesWithLabel(originalProjection, "SourceFile"), nodesWithLabel(relocatedProjection, "SourceFile"));
    assertNotEquals(nodesWithLabel(originalProjection, "InputSnapshot"), nodesWithLabel(relocatedProjection, "InputSnapshot"));
    assertEquals(nodesWithLabel(originalProjection, "CompilerConfiguration").keySet(),
        nodesWithLabel(relocatedProjection, "CompilerConfiguration").keySet());
    assertEquals(nodesWithLabel(originalProjection, "DependencySnapshot").keySet(),
        nodesWithLabel(relocatedProjection, "DependencySnapshot").keySet());
    assertEquals(nodesWithLabel(originalProjection, "DependencySnapshot"), nodesWithLabel(relocatedProjection, "DependencySnapshot"));
    assertEquals(expectedSurface(relocated).nodes, actualNodes(relocatedProjection));
    assertEquals(expectedSurface(relocated).relationships, actualRelationships(relocatedProjection));
  }

  @Test
  void givenRelocatedPathBearingOptionText_whenMapped_thenEnvironmentVersionAndDerivedRunIdentitiesChange() throws IOException {
    // Arrange
    var original = suppliedRun();
    var relocated = relocate(original, true);

    // Act
    var originalProjection = new Neo4jAnalysisRunMapper().map(original);
    var relocatedProjection = new Neo4jAnalysisRunMapper().map(relocated);

    // Assert
    assertNotEquals(original.representation().ast().capturedEnvironment().orElseThrow().environmentDigest(), relocated.representation().ast().capturedEnvironment().orElseThrow().environmentDigest());
    assertNotEquals(SoftwareVersion.from(original.representation().ast().capturedInput().orElseThrow(), original.representation().ast().capturedEnvironment().orElseThrow()), SoftwareVersion.from(relocated.representation().ast().capturedInput().orElseThrow(), relocated.representation().ast().capturedEnvironment().orElseThrow()));
    assertNotEquals(original.identity().key(), relocated.identity().key());
    assertNotEquals(nodesWithLabel(originalProjection, "AnalysisEnvironmentSnapshot").keySet(),
        nodesWithLabel(relocatedProjection, "AnalysisEnvironmentSnapshot").keySet());
    assertNotEquals(nodesWithLabel(originalProjection, "SoftwareVersion").keySet(), nodesWithLabel(relocatedProjection, "SoftwareVersion").keySet());
    assertEquals(original.representation().ast().capturedInput().orElseThrow().snapshotDigest(), relocated.representation().ast().capturedInput().orElseThrow().snapshotDigest());
    assertEquals(expectedSurface(relocated).nodes, actualNodes(relocatedProjection));
    assertEquals(expectedSurface(relocated).relationships, actualRelationships(relocatedProjection));
  }

  private AnalysisRun suppliedRun() {
    return producedRun(temporaryDirectory.resolve("source-root"), List.of(), List.of());
  }

  private AnalysisRun producedRun(Path root, List<Path> dependencies, List<Path> modules) {
    var main = SourceFile.fromContent(Path.of("z", "Owner.java"), """
        sealed interface Owner permits Owner.Record {
          record Record<T extends Number & Comparable<T>>(String value, String... rest) implements Owner {
            public Record(String value, String... rest) { this.value = value; this.rest = rest; }
            static int count;
            static int evaluate(int input, int divisor) {
              if (input > 0) return input / divisor;
              return switch (input) { case 0 -> 7; default -> Math.max(input, 1); };
            }
          }
        }
        """);
    var support = SourceFile.fromContent(Path.of("a", "Support.java"), "enum Mode { ; Mode() {} } class Implicit {}");
    var input = InputSnapshot.fromSources(root, List.of(main, support));
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, dependencies, modules, List.of("-Xlint:all"));
    var request = new AnalysisComputationRequest(
        new AstProductionRequest(input, configuration, AstProfile.JAVA_SOURCE_BOUNDED), Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)));
    var original = AnalysisCapabilities.computation(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard()).analyze(request);
    var origin = original.representation().ast().result().orElseThrow().program().types().stream()
        .filter(type -> type.qualifiedName().equals("Owner")).findFirst().orElseThrow().origins().getFirst();
    var warning = new AnalysisDiagnostic("diagnostic:source", "frontend.observation", DiagnosticStage.FRONTEND, DiagnosticSeverity.WARNING,
        "Supplied source observation", List.of(origin), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var noOrigin = new AnalysisDiagnostic("diagnostic:implicit", "frontend.observation", DiagnosticStage.FRONTEND, DiagnosticSeverity.WARNING,
        "Supplied observation without origin", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    return AnalysisRun.create(original.identity(), original.representation(), List.of(warning, noOrigin, warning));
  }

  private AnalysisRun relocate(AnalysisRun run, boolean changeOptionText) throws IOException {
    var root = temporaryDirectory.resolve("relocated");
    Files.createDirectories(root);
    var dependencies = changeOptionText ? List.of(Files.createDirectory(root.resolve("dependency"))) : List.<Path>of();
    return producedRun(root, dependencies, List.of());
  }

  private static ExpectedSurface expectedSurface(AnalysisRun run) {
    var runId = run.identity().key();
    var ast = run.representation().ast().result().orElseThrow();
    var snapshot = ast.snapshot();
    var environment = ast.environment();
    var version = SoftwareVersion.from(snapshot, environment);
    var expected = new ExpectedSurface(runId);
    expected.node("AnalysisRun", runId, properties("completion", "COMPLETE", "astResultKey", ast.identity().astResultKey(),
        "operation", "PRODUCTION_WITH_CFG"));
    expected.node("SoftwareVersion", version.signature(), properties("signatureAlgorithm", version.signatureAlgorithm(), "signature",
        version.signature(), "sourceSignature", snapshot.snapshotDigest(), "environmentSignature", environment.environmentDigest()));
    expected.edge("ANALYZED_SOFTWARE", runId, version.signature(), "software-version");
    var inputKey = StableId.sha256("input-snapshot", snapshot.snapshotDigest());
    expected.node("InputSnapshot", inputKey, properties("sourceRoot", snapshot.sourceRoot().toString(), "digestAlgorithm", "SHA-256",
        "snapshotDigest", snapshot.snapshotDigest()));
    expected.edge("HAS_INPUT", version.signature(), inputKey, "input");
    var canonicalPaths = Map.of(Path.of("a", "Support.java"), "a/Support.java", Path.of("z", "Owner.java"), "z/Owner.java");
    for (var file : snapshot.sourceFiles()) {
      var canonicalPath = canonicalPaths.get(file.relativePath());
      expected.node("SourceFile", file.sourceFileKey(), properties("relativePath", canonicalPath, "contentDigest",
          file.contentDigest(), "content", file.content()));
      expected.edge("HAS_SOURCE", inputKey, file.sourceFileKey(), canonicalPath);
    }
    var environmentKey = environment.environmentDigest();
    var environmentProperties = properties("digestAlgorithm", environment.digestAlgorithm(),
        "environmentDigest", environmentKey, "compilerImplementation", environment.compilerImplementation(), "compilerVendor",
        environment.compilerVendor(), "compilerVersion", environment.compilerVersion(), "analysisOptions", environment.analysisOptions());
    if (environment.identityScheme() == EnvironmentIdentityScheme.MODULE_SEMANTICS) {
      environmentProperties.put("identityScheme", "analysis-environment/2");
    }
    expected.node("AnalysisEnvironmentSnapshot", environmentKey, environmentProperties);
    expected.edge("HAS_ENVIRONMENT", version.signature(), environmentKey, "environment");
    var config = environment.compilerConfiguration();
    var configKey = StableId.sha256("compiler-configuration", config.canonicalForm());
    expected.node("CompilerConfiguration", configKey, properties("release", 25, "classPath", config.classPath().stream().map(Path::toString).toList(),
        "modulePath", config.modulePath().stream().map(Path::toString).toList(), "options", List.of("-Xlint:all")));
    expected.edge("HAS_CONFIGURATION", environmentKey, configKey, "configuration");
    expectedDependencies(expected, environmentKey, "class-path", "HAS_CLASS_PATH_ENTRY", environment.classPath());
    expectedDependencies(expected, environmentKey, "module-path", "HAS_MODULE_PATH_ENTRY", environment.modulePath());
    for (var position = 0; position < run.diagnostics().values().size(); position++) {
      var diagnostic = run.diagnostics().values().get(position);
      expected.node("Diagnostic", diagnostic.diagnosticKey(), properties("code", diagnostic.code(), "stage", diagnostic.stage().name(),
          "severity", diagnostic.severity().name(), "message", diagnostic.message()));
      expected.edge("HAS_DIAGNOSTIC", runId, diagnostic.diagnosticKey(), Integer.toString(position), "position", position);
      expectedOrigins(expected, diagnostic.diagnosticKey(), diagnostic.origins());
    }
    var program = StableId.sha256("program", runId);
    expected.node("Program", program, properties("typeCount", ast.program().types().size(), "astResultKey", ast.identity().astResultKey()));
    expected.edge("HAS_PROGRAM", runId, program, "program");
    for (var index = 0; index < ast.program().types().size(); index++) {
      var type = ast.program().types().get(index);
      expected.node("JavaType", type.typeKey(), properties("qualifiedName", type.qualifiedName(), "kind", type.kind().name(), "modifiers",
          type.modifiers().stream().map(Enum::name).sorted().toList(), "sourceDeclared", type.sourceDeclared()));
      expected.edge("DECLARES_TYPE", program, type.typeKey(), Integer.toString(index), "position", index);
      expectedOrigins(expected, type.typeKey(), type.origins());
      type.enclosingType().ifPresent(value -> expected.typeReference(type.typeKey(), "ENCLOSED_BY", 0, value.identity().displayName(),
          value.identity().erasedName(), value.declarationKey()));
      type.directSuperclass().ifPresent(value -> expected.typeReference(type.typeKey(), "HAS_DIRECT_SUPERCLASS", 0,
          value.identity().displayName(), value.identity().erasedName(), value.declarationKey()));
      expectedTypeReferences(expected, type.typeKey(), "HAS_DIRECT_INTERFACE", type.directInterfaces());
      expectedTypeReferences(expected, type.typeKey(), "PERMITS_SUBCLASS", type.permittedSubclasses());
      for (var position = 0; position < type.typeParameters().size(); position++) {
        var parameter = type.typeParameters().get(position);
        expected.node("JavaTypeParameter", parameter.typeParameterKey(), properties("name", parameter.name(), "position", position,
            "sourceDeclared", parameter.sourceDeclared()));
        expected.edge("DECLARES_TYPE_PARAMETER", type.typeKey(), parameter.typeParameterKey(), Integer.toString(position), "position", position);
        expectedOrigins(expected, parameter.typeParameterKey(), parameter.origins());
        expectedTypeReferences(expected, parameter.typeParameterKey(), "HAS_TYPE_BOUND", parameter.bounds());
      }
      for (var position = 0; position < type.fields().size(); position++) {
        var field = type.fields().get(position);
        expected.node("JavaField", field.fieldKey(), expectedType(field.staticType(), "name", field.name(), "modifiers",
            field.modifiers().stream().sorted().toList(), "enumConstant", field.enumConstant(), "sourceDeclared", field.sourceDeclared()));
        expected.edge("DECLARES_FIELD", type.typeKey(), field.fieldKey(), Integer.toString(position), "position", position);
        expectedOrigins(expected, field.fieldKey(), field.origins());
      }
      for (var component : type.recordComponents()) {
        var values = expectedType(component.staticType(), "name", component.name(), "position", component.ordinal(), "varArgs", component.varArgs(),
            "writtenModifiers", component.writtenModifiers().stream().map(Enum::name).sorted().toList(), "effectiveModifiers",
            component.effectiveModifiers().stream().map(Enum::name).sorted().toList());
        component.accessorKey().ifPresent(key -> values.put("accessorKey", key));
        component.backingFieldKey().ifPresent(key -> values.put("backingFieldKey", key));
        component.canonicalParameterKey().ifPresent(key -> values.put("canonicalParameterKey", key));
        expected.node("JavaRecordComponent", component.componentKey(), values);
        expected.edge("DECLARES_RECORD_COMPONENT", type.typeKey(), component.componentKey(), Integer.toString(component.ordinal()),
            "position", component.ordinal());
        expectedOrigins(expected, component.componentKey(), component.origins());
      }
      for (var position = 0; position < type.synthesizedDeclarations().size(); position++) {
        var declaration = type.synthesizedDeclarations().get(position);
        expected.node("JavaSynthesizedDeclaration", declaration.declarationKey(), properties("kind", declaration.kind().name(), "sourceDeclared", false));
        expected.edge("DECLARES_SYNTHESIZED", type.typeKey(), declaration.declarationKey(), Integer.toString(position), "position", position);
      }
      for (var position = 0; position < type.methods().size(); position++) {
        var method = type.methods().get(position);
        expected.node("JavaMethod", method.methodKey(), properties("name", method.name(), "signature", method.signature(), "returnType", method.returnType(),
            "modifiers", method.modifiers().stream().sorted().toList(), "constructor", method.constructor(), "sourceDeclared", method.sourceDeclared(),
            "hasBody", method.hasBody()));
        expected.edge("DECLARES_METHOD", type.typeKey(), method.methodKey(), Integer.toString(position), "position", position);
        expectedOrigins(expected, method.methodKey(), method.origins());
        for (var ordinal = 0; ordinal < method.parameters().size(); ordinal++) {
          var parameter = method.parameters().get(ordinal);
          expected.node("JavaParameter", parameter.parameterKey(), expectedType(parameter.staticType(), "name", parameter.name(), "position", ordinal,
              "sourceDeclared", parameter.sourceDeclared()));
          expected.edge("DECLARES_PARAMETER", method.methodKey(), parameter.parameterKey(), Integer.toString(ordinal), "position", ordinal);
          expectedOrigins(expected, parameter.parameterKey(), parameter.origins());
        }
      }
    }
    for (var position = 0; position < run.representation().cfg().orElseThrow().methods().size(); position++) {
      var outcome = run.representation().cfg().orElseThrow().methods().get(position);
      var graph = outcome.graph().orElseThrow();
      var identity = graph.identity();
      var analysis = StableId.sha256("method-analysis", graph.graphId());
      expected.node("MethodAnalysis", analysis, properties("methodKey", graph.methodKey(), "state", "AVAILABLE"));
      expected.edge("HAS_METHOD_ANALYSIS", program, analysis, Integer.toString(position), "position", position);
      expected.edge("ANALYZES_METHOD", analysis, graph.methodKey(), "method");
      var values = properties("methodKey", graph.methodKey(), "astResultKey", ast.identity().astResultKey(), "cfgProfileVersion", identity.cfgProfileVersion(),
          "graphModelVersion", "java-cfg/2", "canonicalContentDigest", identity.canonicalContentDigest());
      var body = graph.body();
      body.sourceOccurrenceKey().ifPresent(key -> values.put("sourceOccurrenceKey", key));
      body.nonwrittenProvenance().ifPresent(origin -> {
        values.put("nonwrittenProvenance", origin.origin());
        values.put("bodySourceFileKey", origin.sourceFileKey());
        values.put("bodyOwnerDeclarationKey", origin.ownerDeclarationKey());
      });
      expected.node("ControlFlowGraph", graph.graphId(), values);
      expected.edge("HAS_CFG", analysis, graph.graphId(), "cfg");
      for (var ordinal = 0; ordinal < graph.values().size(); ordinal++) {
        var value = graph.values().get(ordinal);
        var props = properties("graphId", graph.graphId(), "kind", value.kind().name(), "position", ordinal);
        value.displayName().ifPresent(text -> props.put("displayName", text));
        value.literal().ifPresent(text -> props.put("literal", text));
        value.staticType().ifPresent(type -> putType(props, "staticType", type));
        expected.node("FlowValue", value.valueId(), props);
        expected.edge("HAS_VALUE", graph.graphId(), value.valueId(), Integer.toString(ordinal), "position", ordinal);
      }
      for (var ordinal = 0; ordinal < graph.nodes().size(); ordinal++) {
        var node = graph.nodes().get(ordinal);
        var props = properties("graphId", graph.graphId());
        var label = node instanceof EntryNode ? "EntryNode" : node instanceof ExitNode ? "ExitNode" : "OperationNode";
        if (node instanceof OperationNode operation) {
          props.putAll(properties("kind", operation.kind().name(), "detail", operation.detail(), "possibleExceptions", operation.possibleExceptions()));
          operation.invocationSemantics().ifPresent(semantics -> {
            props.put("invocationMode", semantics.mode().name());
            props.put("invocationForm", semantics.form().name());
            putType(props, "qualifyingType", semantics.qualifyingType().identity());
            semantics.qualifyingType().declarationKey().ifPresent(key -> props.put("qualifyingTypeDeclarationKey", key));
          });
          for (var operand : operation.operands()) expected.edge("USES_VALUE", node.nodeId(), operand.value().valueId(), Integer.toString(operand.position()),
              "position", operand.position());
          operation.definedValue().ifPresent(value -> expected.edge("DEFINES_VALUE", node.nodeId(), value.valueId(), "definition"));
          for (var slot = 0; slot < operation.semanticReferences().size(); slot++) {
            var reference = operation.semanticReferences().get(slot);
            var key = StableId.sha256("semantic-reference", node.nodeId(), Integer.toString(slot));
            var referenceValues = properties("kind", reference.referenceKind().name(), "status", reference.status().name(), "position", slot,
                "typeAlternativeNames", reference.typeAlternativeNames(), "assignableToTypeNames", reference.assignableToTypeNames());
            reference.symbolKey().ifPresent(text -> referenceValues.put("symbolKey", text));
            reference.typeName().ifPresent(text -> referenceValues.put("typeName", text));
            expected.node("SemanticReference", key, referenceValues);
            expected.edge("HAS_REFERENCE", node.nodeId(), key, Integer.toString(slot), "position", slot);
          }
        }
        expected.node(label, node.nodeId(), props);
        expected.edge("HAS_NODE", graph.graphId(), node.nodeId(), Integer.toString(ordinal), "position", ordinal);
        expectedOrigins(expected, node.nodeId(), node.origins());
      }
      for (var edge : graph.edges()) {
        var label = switch (edge.transferKind()) {
          case NORMAL -> "NORMAL_FLOW";
          case TRUE_BRANCH -> "TRUE_FLOW";
          case FALSE_BRANCH -> "FALSE_FLOW";
          case SWITCH_ALTERNATIVE -> "SWITCH_FLOW";
          case RETURN -> "RETURN_FLOW";
          case EXCEPTIONAL -> "EXCEPTIONAL_FLOW";
        };
        if (edge.discriminator().isPresent()) expected.edge(label, edge.sourceNodeId(), edge.targetNodeId(), edge.edgeId(), "edgeId", edge.edgeId(),
            "discriminator", edge.discriminator().orElseThrow());
        else expected.edge(label, edge.sourceNodeId(), edge.targetNodeId(), edge.edgeId(), "edgeId", edge.edgeId());
      }
    }
    return expected;
  }

  private static void expectedDependencies(ExpectedSurface expected, String environment, String role, String relation, List<DependencySnapshot> dependencies) {
    for (var position = 0; position < dependencies.size(); position++) {
      var dependency = dependencies.get(position);
      var key = StableId.sha256("dependency-snapshot", environment, role, Integer.toString(position), dependency.fingerprint());
      var values = properties("location", dependency.location().toString(), "kind", dependency.kind().name(),
          "byteCount", dependency.byteCount(), "entryCount", dependency.entryCount(), "fingerprint", dependency.fingerprint(), "position", position);
      if (dependency.semantics().state() != DependencySemantics.State.UNOBSERVED) {
        values.put("interpretation", role.equals("class-path") ? "CLASS_PATH" : "MODULES");
        values.put("moduleMembers", List.of());
        values.put("moduleLookupNames", List.of());
        values.put("moduleDescriptorNames", List.of());
        values.put("moduleNameOrigins", List.of());
      }
      expected.node("DependencySnapshot", key, values);
      expected.edge(relation, environment, key, Integer.toString(position), "position", position);
    }
  }

  private static void expectedTypeReferences(ExpectedSurface expected, String owner, String relation, List<JavaTypeReference> references) {
    for (var index = 0; index < references.size(); index++) {
      var reference = references.get(index);
      expected.typeReference(owner, relation, index, reference.identity().displayName(), reference.identity().erasedName(), reference.declarationKey());
    }
  }

  private static void expectedOrigins(ExpectedSurface expected, String owner, List<SourceSpan> origins) {
    for (var index = 0; index < origins.size(); index++) {
      var origin = origins.get(index);
      expected.origin(owner, index, origin.sourceFileKey(), origin.startOffset(), origin.endOffsetExclusive());
    }
  }

  private static Map<String, Object> expectedType(JavaTypeIdentity type, Object... values) {
    var result = properties(values);
    putType(result, "staticType", type);
    return result;
  }

  private static void putType(Map<String, Object> result, String name, JavaTypeIdentity type) {
    result.put(name, type.displayName());
    result.put(name + "Key", type.typeKey());
    result.put(name + "Erasure", type.erasedName());
    result.put(name + "Category", type.category().name());
  }

  private static void assertFamily(ExpectedSurface expected, Neo4jGraphProjection actual, Set<String> labels, Set<String> types) {
    assertEquals(expected.nodes.entrySet().stream().filter(entry -> labels.contains(entry.getValue().label()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)), actualNodes(actual).entrySet().stream()
            .filter(entry -> labels.contains(entry.getValue().label())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    assertEquals(expected.relationships.entrySet().stream().filter(entry -> types.contains(entry.getValue().type()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)), actualRelationships(actual).entrySet().stream()
            .filter(entry -> types.contains(entry.getValue().type())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
  }

  private static Map<String, NodeExpectation> actualNodes(Neo4jGraphProjection projection) {
    return projection.nodes().stream().collect(Collectors.toMap(Neo4jNode::key, node -> new NodeExpectation(node.label(), node.properties())));
  }

  private static Map<String, RelationshipExpectation> actualRelationships(Neo4jGraphProjection projection) {
    return projection.relationships().stream().collect(Collectors.toMap(Neo4jRelationship::key,
        relation -> new RelationshipExpectation(relation.type(), relation.sourceKey(), relation.targetKey(), relation.properties())));
  }

  private static Map<String, NodeExpectation> nodesWithLabel(Neo4jGraphProjection projection, String label) {
    return actualNodes(projection).entrySet().stream().filter(entry -> entry.getValue().label().equals(label))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static Map<String, Object> properties(Object... pairs) {
    var properties = new LinkedHashMap<String, Object>();
    for (var position = 0; position < pairs.length; position += 2) {
      properties.put((String) pairs[position], pairs[position + 1]);
    }
    return properties;
  }

  private static Map<String, Object> typed(String prefix, String category, String display, String erasure, Object... pairs) {
    var properties = properties(pairs);
    properties.put(prefix, display);
    properties.put(prefix + "Key", StableId.sha256("java-type", category, display, erasure));
    properties.put(prefix + "Erasure", erasure);
    properties.put(prefix + "Category", category);
    return properties;
  }

  private record NodeExpectation(String label, Map<String, Object> properties) {
  }

  private record RelationshipExpectation(String type, String source, String target, Map<String, Object> properties) {
  }

  private static final class ExpectedSurface {
    private final String runId;
    private final Map<String, NodeExpectation> nodes = new LinkedHashMap<>();
    private final Map<String, RelationshipExpectation> relationships = new LinkedHashMap<>();

    private ExpectedSurface(String runId) {
      this.runId = runId;
    }

    void node(String label, String key, Map<String, Object> supplied) {
      var properties = new LinkedHashMap<>(supplied);
      properties.put("runId", runId);
      properties.put("key", key);
      var value = new NodeExpectation(label, Map.copyOf(properties));
      var previous = nodes.putIfAbsent(key, value);
      if (previous != null) assertEquals(previous, value, "Repeated keys require identical node values");
    }

    void edge(String type, String source, String target, String discriminator, Object... pairs) {
      var key = StableId.sha256("relationship", type, source, target, discriminator);
      var properties = properties(pairs);
      properties.put("runId", runId);
      properties.put("key", key);
      var value = new RelationshipExpectation(type, source, target, Map.copyOf(properties));
      var previous = relationships.putIfAbsent(key, value);
      if (previous != null) assertEquals(previous, value, "Repeated keys require identical relationship values");
    }

    void origin(String owner, int position, String source, long start, long end) {
      var key = StableId.sha256("source-origin", owner, Integer.toString(position), source, Long.toString(start), Long.toString(end));
      node("SourceOrigin", key, properties("startOffset", start, "endOffsetExclusive", end, "position", position));
      edge("HAS_ORIGIN", owner, key, Integer.toString(position), "position", position);
      edge("IN_SOURCE", key, source, "source");
    }

    void typeReference(String owner, String relation, int position, String display, String erasure, Optional<String> declaration) {
      var key = StableId.sha256("java-type-reference", owner, relation, Integer.toString(position));
      var properties = typed("type", "DECLARED", display, erasure, "position", position);
      declaration.ifPresent(value -> properties.put("declarationKey", value));
      node("JavaTypeReference", key, properties);
      edge(relation, owner, key, Integer.toString(position), "position", position);
    }
  }
}
