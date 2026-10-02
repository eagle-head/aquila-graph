package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.AnalysisEnvironmentSnapshot;
import org.aquilagraph.model.AnalysisOutcomeKind;
import org.aquilagraph.model.AnalysisRun;
import org.aquilagraph.model.CfgEdge;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.ControlFlowGraph;
import org.aquilagraph.model.DependencyKind;
import org.aquilagraph.model.DependencySnapshot;
import org.aquilagraph.model.DiagnosticSeverity;
import org.aquilagraph.model.DiagnosticStage;
import org.aquilagraph.model.EntryNode;
import org.aquilagraph.model.ExitNode;
import org.aquilagraph.model.FlowValue;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.InvocationForm;
import org.aquilagraph.model.InvocationMode;
import org.aquilagraph.model.InvocationSemantics;
import org.aquilagraph.model.JavaField;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.JavaParameter;
import org.aquilagraph.model.JavaProgram;
import org.aquilagraph.model.JavaRecordComponent;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.JavaSynthesizedDeclaration;
import org.aquilagraph.model.JavaType;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.JavaTypeKind;
import org.aquilagraph.model.JavaTypeModifier;
import org.aquilagraph.model.JavaTypeParameter;
import org.aquilagraph.model.JavaTypeReference;
import org.aquilagraph.model.MethodAnalysis;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.OperationNode;
import org.aquilagraph.model.OperationOperand;
import org.aquilagraph.model.ProgramRepresentation;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.model.SoftwareVersion;
import org.aquilagraph.model.StableId;
import org.aquilagraph.model.TransferKind;
import org.aquilagraph.model.ValueKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Neo4jStoredSurfaceContractTest {
  private static final String RUN_ID = "supplied-run";
  private static final String OWNER = "type:Owner";
  private static final String RECORD = "type:Owner.Record";
  private static final String MODE = "type:Mode";
  private static final String METHOD = RECORD + "#evaluate";
  private static final String CONSTRUCTOR = RECORD + "#constructor";
  private static final String ACCESSOR = RECORD + "#value";
  private static final String GRAPH = "supplied-graph";
  private static final String SOURCE = "source:main";
  private static final String SUPPORT = "source:support";
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
  void givenOrderedCompilerDependencies_whenMapped_thenConfigurationProvenanceRolesCountsAndPositionsArePreserved() {
    // Arrange
    var run = suppliedRun();
    var expected = expectedSurface(run);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(run);

    // Assert
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
    assertEquals(RUN_ID, projection.runId());
    assertEquals(expected.nodes, actualNodes(projection));
    assertEquals(expected.relationships, actualRelationships(projection));
  }

  @Test
  void givenRelocatedInputsWithUnchangedOptions_whenMapped_thenIdentityIsRetainedWhileStoredLocationsAndConfigurationKeysChange() {
    // Arrange
    var original = withDerivedIdentity(suppliedRun());
    var relocated = relocate(original, false);

    // Act
    var originalProjection = new Neo4jAnalysisRunMapper().map(original);
    var relocatedProjection = new Neo4jAnalysisRunMapper().map(relocated);

    // Assert
    assertEquals(original.environmentSnapshot().environmentDigest(), relocated.environmentSnapshot().environmentDigest());
    assertEquals(original.softwareVersion(), relocated.softwareVersion());
    assertEquals(original.runId(), relocated.runId());
    assertEquals(original.inputSnapshot().snapshotDigest(), relocated.inputSnapshot().snapshotDigest());
    assertEquals(nodesWithLabel(originalProjection, "SourceFile"), nodesWithLabel(relocatedProjection, "SourceFile"));
    assertNotEquals(nodesWithLabel(originalProjection, "InputSnapshot"), nodesWithLabel(relocatedProjection, "InputSnapshot"));
    assertNotEquals(nodesWithLabel(originalProjection, "CompilerConfiguration").keySet(),
        nodesWithLabel(relocatedProjection, "CompilerConfiguration").keySet());
    assertEquals(nodesWithLabel(originalProjection, "DependencySnapshot").keySet(),
        nodesWithLabel(relocatedProjection, "DependencySnapshot").keySet());
    assertNotEquals(nodesWithLabel(originalProjection, "DependencySnapshot"), nodesWithLabel(relocatedProjection, "DependencySnapshot"));
    assertEquals(expectedSurface(relocated).nodes, actualNodes(relocatedProjection));
    assertEquals(expectedSurface(relocated).relationships, actualRelationships(relocatedProjection));
  }

  @Test
  void givenRelocatedPathBearingOptionText_whenMapped_thenEnvironmentVersionAndDerivedRunIdentitiesChange() {
    // Arrange
    var original = withDerivedIdentity(suppliedRun());
    var relocated = relocate(original, true);

    // Act
    var originalProjection = new Neo4jAnalysisRunMapper().map(original);
    var relocatedProjection = new Neo4jAnalysisRunMapper().map(relocated);

    // Assert
    assertNotEquals(original.environmentSnapshot().environmentDigest(), relocated.environmentSnapshot().environmentDigest());
    assertNotEquals(original.softwareVersion(), relocated.softwareVersion());
    assertNotEquals(original.runId(), relocated.runId());
    assertNotEquals(nodesWithLabel(originalProjection, "AnalysisEnvironmentSnapshot").keySet(),
        nodesWithLabel(relocatedProjection, "AnalysisEnvironmentSnapshot").keySet());
    assertNotEquals(nodesWithLabel(originalProjection, "SoftwareVersion").keySet(), nodesWithLabel(relocatedProjection, "SoftwareVersion").keySet());
    assertEquals(original.inputSnapshot().snapshotDigest(), relocated.inputSnapshot().snapshotDigest());
    assertEquals(expectedSurface(relocated).nodes, actualNodes(relocatedProjection));
    assertEquals(expectedSurface(relocated).relationships, actualRelationships(relocatedProjection));
  }

  private AnalysisRun suppliedRun() {
    var source = new SourceFile(SOURCE, Path.of("z", "Main.java"), "main-content-digest", "record Record(String value, String... rest) {}");
    var support = new SourceFile(SUPPORT, Path.of("a", "Support.java"), "support-content-digest", "sealed interface Owner permits Record {}");
    var input = new InputSnapshot(temporaryDirectory.resolve("source-root"), "SHA-256", "supplied-source-digest", List.of(support, source));
    var classFirst = temporaryDirectory.resolve("dependencies", "first.jar");
    var classSecond = temporaryDirectory.resolve("dependencies", "missing");
    var module = temporaryDirectory.resolve("modules", "compiled");
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(classFirst, classSecond), List.of(module), List.of("-Xlint:all"));
    var environment = new AnalysisEnvironmentSnapshot(configuration, "supplied.Compiler", "Supplied Vendor", "25-supplied",
        List.of("--release", "25", "--class-path", classFirst.toString(), "-Xlint:all"),
        List.of(new DependencySnapshot(classFirst, DependencyKind.REGULAR_FILE, 17L, 1, "class-first-fingerprint"),
            new DependencySnapshot(classSecond, DependencyKind.MISSING, 0L, 0, "class-missing-fingerprint")),
        List.of(new DependencySnapshot(module, DependencyKind.DIRECTORY, 23L, 2, "module-fingerprint")));
    var mainOrigin = new SourceSpan(SOURCE, 0L, 12L);
    var supportOrigin = new SourceSpan(SUPPORT, 1L, 9L);
    var integer = new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int");
    var string = new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.String", "java.lang.String");
    var array = new JavaTypeIdentity(JavaTypeCategory.ARRAY, "java.lang.String[]", "java.lang.String[]");
    var inputParameter = new JavaParameter(METHOD + ":input", "input", integer, true, List.of(mainOrigin));
    var method = new JavaMethod(METHOD, RECORD, "evaluate", "evaluate(int)", "int", List.of(inputParameter), Set.of("static", "public"),
        false, true, true, List.of(mainOrigin));
    var constructor = new JavaMethod(CONSTRUCTOR, RECORD, "Record", "Record(java.lang.String,java.lang.String[])", "void",
        List.of(new JavaParameter(CONSTRUCTOR + ":value", "value", string, false, List.of()),
            new JavaParameter(CONSTRUCTOR + ":rest", "rest", array, false, List.of())), Set.of("public"), true, false, false, List.of());
    var accessor = new JavaMethod(ACCESSOR, RECORD, "value", "value()", "java.lang.String", List.of(), Set.of("public"), false, false, false,
        List.of());
    var ownerReference = new JavaTypeReference(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Owner", "Owner"), Optional.of(OWNER));
    var recordReference = new JavaTypeReference(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Owner.Record<T>", "Owner.Record"),
        Optional.of(RECORD));
    var owner = new JavaType(OWNER, "Owner", JavaTypeKind.INTERFACE, Set.of(JavaTypeModifier.SEALED, JavaTypeModifier.PUBLIC), Optional.empty(),
        Optional.empty(), List.of(), List.of(recordReference), List.of(), true, List.of(), List.of(), List.of(), List.of(), List.of(supportOrigin));
    var parameter = new JavaTypeParameter(RECORD + ":T", RECORD, "T",
        List.of(reference("java.lang.Number", "java.lang.Number", Optional.of("type:java.lang.Number")),
            reference("java.lang.Comparable<T>", "java.lang.Comparable", Optional.empty())), true, List.of(mainOrigin));
    var implicitParameter = new JavaTypeParameter(RECORD + ":U", RECORD, "U",
        List.of(reference("java.lang.Object", "java.lang.Object", Optional.of("type:java.lang.Object"))), false, List.of());
    var record = new JavaType(RECORD, "Owner.Record", JavaTypeKind.RECORD, Set.of(JavaTypeModifier.PUBLIC, JavaTypeModifier.FINAL),
        Optional.of(ownerReference), Optional.of(reference("java.lang.Record", "java.lang.Record", Optional.of("type:java.lang.Record"))),
        List.of(ownerReference, reference("java.io.Serializable", "java.io.Serializable", Optional.empty())), List.of(),
        List.of(parameter, implicitParameter), true,
        List.of(new JavaField(RECORD + "#count", RECORD, "count", integer, Set.of("static", "public"), false, true, List.of(mainOrigin)),
            new JavaField(RECORD + "#backing", RECORD, "value", string, Set.of("private", "final"), false, false, List.of())),
        List.of(new JavaRecordComponent(RECORD + "#record-component:0", RECORD, 0, "value", string, Set.of(), Set.of(JavaTypeModifier.FINAL),
            false, Optional.of(ACCESSOR), Optional.of(RECORD + "#backing"), Optional.of(CONSTRUCTOR + ":value"), mainOrigin),
            new JavaRecordComponent(RECORD + "#record-component:1", RECORD, 1, "rest", array, Set.of(), Set.of(), true, Optional.empty(),
                Optional.empty(), Optional.empty(), supportOrigin)),
        List.of(method, constructor, accessor), List.of(new JavaSynthesizedDeclaration(RECORD + "#equals", RECORD,
            JavaSynthesizedDeclaration.Kind.METHOD)), List.of(mainOrigin));
    var mode = new JavaType(MODE, "Mode", JavaTypeKind.ENUM, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), true,
        List.of(new JavaField(MODE + "#READY", MODE, "READY", new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Mode", "Mode"),
            Set.of("static", "public", "final"), true, true, List.of(supportOrigin))), List.of(), List.of(),
        List.of(new JavaSynthesizedDeclaration(MODE + "#values", MODE, JavaSynthesizedDeclaration.Kind.METHOD)), List.of(supportOrigin));
    var implicitType = new JavaType("type:Implicit", "Implicit", JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(),
        List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of());
    var graph = suppliedGraph(integer, mainOrigin, supportOrigin);
    var representation = new ProgramRepresentation(new JavaProgram(List.of(owner, record, mode, implicitType)),
        List.of(new MethodAnalysis("supplied-analysis", method, graph)));
    var diagnostic = new AnalysisDiagnostic("diagnostic:source", "frontend.observation", DiagnosticStage.FRONTEND, DiagnosticSeverity.WARNING,
        "Supplied source observation", List.of(mainOrigin, supportOrigin), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var noOrigin = new AnalysisDiagnostic("diagnostic:implicit", "frontend.observation", DiagnosticStage.FRONTEND, DiagnosticSeverity.WARNING,
        "Supplied observation without origin", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    return new AnalysisRun(RUN_ID, input, environment, Optional.of(representation), List.of(diagnostic, noOrigin, diagnostic),
        AnalysisCompletion.COMPLETED);
  }

  private static ControlFlowGraph suppliedGraph(JavaTypeIdentity integer, SourceSpan mainOrigin, SourceSpan supportOrigin) {
    var parameter = new FlowValue("value:parameter", GRAPH, ValueKind.PARAMETER, Optional.of("input"), Optional.empty(), Optional.of(integer));
    var constant = new FlowValue("value:constant", GRAPH, ValueKind.CONSTANT, Optional.empty(), Optional.of("7"), Optional.of(integer));
    var unknown = new FlowValue("value:unknown", GRAPH, ValueKind.TEMPORARY, Optional.empty(), Optional.empty(), Optional.empty());
    var resolved = new SemanticReference(ReferenceKind.METHOD, ResolutionStatus.RESOLVED, Optional.of("type:External#compute"), Optional.of("int"),
        List.of("int"), List.of("int"));
    var unresolved = new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.UNRESOLVED, Optional.empty(), Optional.empty(), List.of(), List.of());
    var symbolOnly = new SemanticReference(ReferenceKind.PACKAGE, ResolutionStatus.RESOLVED, Optional.of("package:external"), Optional.empty(),
        List.of(), List.of());
    var typeOnly = new SemanticReference(ReferenceKind.FIELD, ResolutionStatus.UNRESOLVED, Optional.empty(), Optional.of("java.lang.String"),
        List.of("java.lang.String"), List.of("java.lang.Object", "java.lang.String"));
    var invoke = new OperationNode("node:invoke", GRAPH, OperationKind.INVOKE, "External.compute",
        List.of(new OperationOperand(0, parameter), new OperationOperand(1, constant)), Optional.of(unknown),
        Optional.of(new InvocationSemantics(InvocationMode.STATIC, reference("External", "External", Optional.of("type:External")),
            InvocationForm.STATIC_TYPE)), List.of(resolved, unresolved, symbolOnly, typeOnly),
        List.of("java.lang.ArithmeticException", "java.lang.OutOfMemoryError"),
        List.of(mainOrigin, supportOrigin));
    var construct = new OperationNode("node:construct", GRAPH, OperationKind.NEW_OBJECT, "new External", List.of(), Optional.empty(),
        Optional.of(new InvocationSemantics(InvocationMode.CONSTRUCTOR, reference("External", "External", Optional.empty()),
            InvocationForm.OBJECT_CREATION)), List.of(), List.of("java.lang.OutOfMemoryError"), List.of());
    var returning = new OperationNode("node:return", GRAPH, OperationKind.RETURN, "return", List.of(), Optional.empty(), Optional.empty(),
        List.of(), List.of(), List.of());
    return new ControlFlowGraph(GRAPH, METHOD, new EntryNode("node:entry", GRAPH), List.of(invoke, construct, returning),
        List.of(parameter, constant, unknown), new ExitNode("node:exit", GRAPH),
        List.of(new CfgEdge("edge:normal", GRAPH, "node:entry", "node:invoke", TransferKind.NORMAL, Optional.empty()),
            new CfgEdge("edge:true", GRAPH, "node:invoke", "node:return", TransferKind.TRUE_BRANCH, Optional.of("condition")),
            new CfgEdge("edge:false", GRAPH, "node:invoke", "node:construct", TransferKind.FALSE_BRANCH, Optional.of("condition")),
            new CfgEdge("edge:switch", GRAPH, "node:construct", "node:return", TransferKind.SWITCH_ALTERNATIVE, Optional.of("case:7")),
            new CfgEdge("edge:return", GRAPH, "node:return", "node:exit", TransferKind.RETURN, Optional.empty()),
            new CfgEdge("edge:exception", GRAPH, "node:invoke", "node:exit", TransferKind.EXCEPTIONAL,
                Optional.of("java.lang.ArithmeticException"))));
  }

  private static JavaTypeReference reference(String display, String erasure, Optional<String> declaration) {
    return new JavaTypeReference(new JavaTypeIdentity(JavaTypeCategory.DECLARED, display, erasure), declaration);
  }

  private AnalysisRun relocate(AnalysisRun run, boolean changeOptionText) {
    var location = temporaryDirectory.resolve("relocated");
    var input = new InputSnapshot(location.resolve("source-root"), "SHA-256", run.inputSnapshot().snapshotDigest(), run.inputSnapshot().sourceFiles());
    var classFirst = location.resolve("first.jar");
    var classSecond = location.resolve("missing");
    var module = location.resolve("compiled");
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(classFirst, classSecond), List.of(module), List.of("-Xlint:all"));
    var options = changeOptionText ? List.of("--release", "25", "--class-path", classFirst.toString(), "-Xlint:all")
        : run.environmentSnapshot().analysisOptions();
    var environment = new AnalysisEnvironmentSnapshot(configuration, "supplied.Compiler", "Supplied Vendor", "25-supplied", options,
        List.of(new DependencySnapshot(classFirst, DependencyKind.REGULAR_FILE, 17L, 1, "class-first-fingerprint"),
            new DependencySnapshot(classSecond, DependencyKind.MISSING, 0L, 0, "class-missing-fingerprint")),
        List.of(new DependencySnapshot(module, DependencyKind.DIRECTORY, 23L, 2, "module-fingerprint")));
    var runId = StableId.sha256("analysis-run", SoftwareVersion.from(input, environment).signature());
    return new AnalysisRun(runId, input, environment, run.representation(), run.diagnostics(), AnalysisCompletion.COMPLETED);
  }

  private static AnalysisRun withDerivedIdentity(AnalysisRun run) {
    return new AnalysisRun(StableId.sha256("analysis-run", run.softwareVersion().signature()), run.inputSnapshot(), run.environmentSnapshot(),
        run.representation(), run.diagnostics(), AnalysisCompletion.COMPLETED);
  }

  private static ExpectedSurface expectedSurface(AnalysisRun run) {
    var runId = run.runId();
    var expected = new ExpectedSurface(runId);
    var version = run.softwareVersion();
    var input = StableId.sha256("input-snapshot", "supplied-source-digest");
    var environment = run.environmentSnapshot().environmentDigest();
    var configuration = StableId.sha256("compiler-configuration", run.compilerConfiguration().canonicalForm());
    var program = StableId.sha256("program", runId);
    expected.node("AnalysisRun", runId, properties("completion", "COMPLETED"));
    expected.node("SoftwareVersion", version.signature(), properties("signatureAlgorithm", "SHA-256", "signature", version.signature(),
        "sourceSignature", "supplied-source-digest", "environmentSignature", environment));
    expected.edge("ANALYZED_SOFTWARE", runId, version.signature(), "software-version");
    expected.node("InputSnapshot", input, properties("sourceRoot", run.inputSnapshot().sourceRoot().toString(), "digestAlgorithm", "SHA-256",
        "snapshotDigest", "supplied-source-digest"));
    expected.node("SourceFile", SUPPORT, properties("relativePath", "a/Support.java", "contentDigest", "support-content-digest", "content",
        "sealed interface Owner permits Record {}"));
    expected.node("SourceFile", SOURCE, properties("relativePath", "z/Main.java", "contentDigest", "main-content-digest", "content",
        "record Record(String value, String... rest) {}"));
    expected.edge("HAS_INPUT", version.signature(), input, "input");
    expected.edge("HAS_SOURCE", input, SUPPORT, Path.of("a", "Support.java").toString());
    expected.edge("HAS_SOURCE", input, SOURCE, Path.of("z", "Main.java").toString());
    expected.node("AnalysisEnvironmentSnapshot", environment, properties("digestAlgorithm", "SHA-256", "environmentDigest", environment,
        "compilerImplementation", "supplied.Compiler", "compilerVendor", "Supplied Vendor", "compilerVersion", "25-supplied", "analysisOptions",
        run.environmentSnapshot().analysisOptions()));
    expected.node("CompilerConfiguration", configuration, properties("release", 25, "classPath",
        run.compilerConfiguration().classPath().stream().map(Path::toString).toList(), "modulePath",
        run.compilerConfiguration().modulePath().stream().map(Path::toString).toList(), "options", List.of("-Xlint:all")));
    expected.edge("HAS_ENVIRONMENT", version.signature(), environment, "environment");
    expected.edge("HAS_CONFIGURATION", environment, configuration, "configuration");
    var first = StableId.sha256("dependency-snapshot", environment, "class-path", "0", "class-first-fingerprint");
    var second = StableId.sha256("dependency-snapshot", environment, "class-path", "1", "class-missing-fingerprint");
    var module = StableId.sha256("dependency-snapshot", environment, "module-path", "0", "module-fingerprint");
    expected.node("DependencySnapshot", first, properties("location", run.compilerConfiguration().classPath().getFirst().toString(), "kind",
        "REGULAR_FILE", "byteCount", 17L, "entryCount", 1, "fingerprint", "class-first-fingerprint", "position", 0));
    expected.node("DependencySnapshot", second, properties("location", run.compilerConfiguration().classPath().getLast().toString(), "kind",
        "MISSING", "byteCount", 0L, "entryCount", 0, "fingerprint", "class-missing-fingerprint", "position", 1));
    expected.node("DependencySnapshot", module, properties("location", run.compilerConfiguration().modulePath().getFirst().toString(), "kind",
        "DIRECTORY", "byteCount", 23L, "entryCount", 2, "fingerprint", "module-fingerprint", "position", 0));
    expected.edge("HAS_CLASS_PATH_ENTRY", environment, first, "0", "position", 0);
    expected.edge("HAS_CLASS_PATH_ENTRY", environment, second, "1", "position", 1);
    expected.edge("HAS_MODULE_PATH_ENTRY", environment, module, "0", "position", 0);
    expected.node("Diagnostic", "diagnostic:source", properties("code", "frontend.observation", "stage", "FRONTEND", "severity", "WARNING",
        "message", "Supplied source observation"));
    expected.node("Diagnostic", "diagnostic:implicit", properties("code", "frontend.observation", "stage", "FRONTEND", "severity", "WARNING",
        "message", "Supplied observation without origin"));
    expected.edge("HAS_DIAGNOSTIC", runId, "diagnostic:source", "0", "position", 0);
    expected.edge("HAS_DIAGNOSTIC", runId, "diagnostic:implicit", "1", "position", 1);
    expected.edge("HAS_DIAGNOSTIC", runId, "diagnostic:source", "2", "position", 2);
    expected.origin("diagnostic:source", 0, SOURCE, 0L, 12L);
    expected.origin("diagnostic:source", 1, SUPPORT, 1L, 9L);
    expected.node("Program", program, properties("typeCount", 4));
    expected.edge("HAS_PROGRAM", runId, program, "program");
    expected.node("JavaType", OWNER, properties("qualifiedName", "Owner", "kind", "INTERFACE", "modifiers", List.of("PUBLIC", "SEALED"),
        "sourceDeclared", true));
    expected.node("JavaType", RECORD, properties("qualifiedName", "Owner.Record", "kind", "RECORD", "modifiers", List.of("FINAL", "PUBLIC"),
        "sourceDeclared", true));
    expected.node("JavaType", MODE, properties("qualifiedName", "Mode", "kind", "ENUM", "modifiers", List.of(), "sourceDeclared", true));
    expected.node("JavaType", "type:Implicit", properties("qualifiedName", "Implicit", "kind", "CLASS", "modifiers", List.of(),
        "sourceDeclared", false));
    expected.edge("DECLARES_TYPE", program, OWNER, "0", "position", 0);
    expected.edge("DECLARES_TYPE", program, RECORD, "1", "position", 1);
    expected.edge("DECLARES_TYPE", program, MODE, "2", "position", 2);
    expected.edge("DECLARES_TYPE", program, "type:Implicit", "3", "position", 3);
    expected.origin(OWNER, 0, SUPPORT, 1L, 9L);
    expected.origin(RECORD, 0, SOURCE, 0L, 12L);
    expected.origin(MODE, 0, SUPPORT, 1L, 9L);
    expected.typeReference(OWNER, "PERMITS_SUBCLASS", 0, "Owner.Record<T>", "Owner.Record", Optional.of(RECORD));
    expected.typeReference(RECORD, "ENCLOSED_BY", 0, "Owner", "Owner", Optional.of(OWNER));
    expected.typeReference(RECORD, "HAS_DIRECT_SUPERCLASS", 0, "java.lang.Record", "java.lang.Record", Optional.of("type:java.lang.Record"));
    expected.typeReference(RECORD, "HAS_DIRECT_INTERFACE", 0, "Owner", "Owner", Optional.of(OWNER));
    expected.typeReference(RECORD, "HAS_DIRECT_INTERFACE", 1, "java.io.Serializable", "java.io.Serializable", Optional.empty());
    expected.node("JavaTypeParameter", RECORD + ":T", properties("name", "T", "position", 0, "sourceDeclared", true));
    expected.edge("DECLARES_TYPE_PARAMETER", RECORD, RECORD + ":T", "0", "position", 0);
    expected.origin(RECORD + ":T", 0, SOURCE, 0L, 12L);
    expected.typeReference(RECORD + ":T", "HAS_TYPE_BOUND", 0, "java.lang.Number", "java.lang.Number", Optional.of("type:java.lang.Number"));
    expected.typeReference(RECORD + ":T", "HAS_TYPE_BOUND", 1, "java.lang.Comparable<T>", "java.lang.Comparable", Optional.empty());
    expected.node("JavaTypeParameter", RECORD + ":U", properties("name", "U", "position", 1, "sourceDeclared", false));
    expected.edge("DECLARES_TYPE_PARAMETER", RECORD, RECORD + ":U", "1", "position", 1);
    expected.typeReference(RECORD + ":U", "HAS_TYPE_BOUND", 0, "java.lang.Object", "java.lang.Object", Optional.of("type:java.lang.Object"));
    expected.node("JavaField", RECORD + "#count", typed("staticType", "INT", "int", "int", "name", "count", "modifiers",
        List.of("public", "static"), "enumConstant", false, "sourceDeclared", true));
    expected.node("JavaField", RECORD + "#backing", typed("staticType", "DECLARED", "java.lang.String", "java.lang.String", "name", "value",
        "modifiers", List.of("final", "private"), "enumConstant", false, "sourceDeclared", false));
    expected.node("JavaField", MODE + "#READY", typed("staticType", "DECLARED", "Mode", "Mode", "name", "READY", "modifiers",
        List.of("final", "public", "static"), "enumConstant", true, "sourceDeclared", true));
    expected.edge("DECLARES_FIELD", RECORD, RECORD + "#count", "0", "position", 0);
    expected.edge("DECLARES_FIELD", RECORD, RECORD + "#backing", "1", "position", 1);
    expected.edge("DECLARES_FIELD", MODE, MODE + "#READY", "0", "position", 0);
    expected.origin(RECORD + "#count", 0, SOURCE, 0L, 12L);
    expected.origin(MODE + "#READY", 0, SUPPORT, 1L, 9L);
    expected.node("JavaRecordComponent", RECORD + "#record-component:0", typed("staticType", "DECLARED", "java.lang.String", "java.lang.String",
        "name", "value", "position", 0, "varArgs", false, "writtenModifiers", List.of(), "effectiveModifiers", List.of("FINAL"),
        "accessorKey", ACCESSOR, "backingFieldKey", RECORD + "#backing", "canonicalParameterKey", CONSTRUCTOR + ":value"));
    expected.node("JavaRecordComponent", RECORD + "#record-component:1", typed("staticType", "ARRAY", "java.lang.String[]", "java.lang.String[]",
        "name", "rest", "position", 1, "varArgs", true, "writtenModifiers", List.of(), "effectiveModifiers", List.of()));
    expected.edge("DECLARES_RECORD_COMPONENT", RECORD, RECORD + "#record-component:0", "0", "position", 0);
    expected.edge("DECLARES_RECORD_COMPONENT", RECORD, RECORD + "#record-component:1", "1", "position", 1);
    expected.origin(RECORD + "#record-component:0", 0, SOURCE, 0L, 12L);
    expected.origin(RECORD + "#record-component:1", 0, SUPPORT, 1L, 9L);
    expected.node("JavaSynthesizedDeclaration", RECORD + "#equals", properties("kind", "METHOD", "sourceDeclared", false));
    expected.node("JavaSynthesizedDeclaration", MODE + "#values", properties("kind", "METHOD", "sourceDeclared", false));
    expected.edge("DECLARES_SYNTHESIZED", RECORD, RECORD + "#equals", "0", "position", 0);
    expected.edge("DECLARES_SYNTHESIZED", MODE, MODE + "#values", "0", "position", 0);
    expected.node("JavaMethod", METHOD, properties("name", "evaluate", "signature", "evaluate(int)", "returnType", "int", "modifiers",
        List.of("public", "static"), "constructor", false, "sourceDeclared", true, "hasBody", true));
    expected.node("JavaMethod", CONSTRUCTOR, properties("name", "Record", "signature", "Record(java.lang.String,java.lang.String[])", "returnType",
        "void", "modifiers", List.of("public"), "constructor", true, "sourceDeclared", false, "hasBody", false));
    expected.node("JavaMethod", ACCESSOR, properties("name", "value", "signature", "value()", "returnType", "java.lang.String", "modifiers",
        List.of("public"), "constructor", false, "sourceDeclared", false, "hasBody", false));
    expected.edge("DECLARES_METHOD", RECORD, METHOD, "0", "position", 0);
    expected.edge("DECLARES_METHOD", RECORD, CONSTRUCTOR, "1", "position", 1);
    expected.edge("DECLARES_METHOD", RECORD, ACCESSOR, "2", "position", 2);
    expected.origin(METHOD, 0, SOURCE, 0L, 12L);
    expected.node("JavaParameter", METHOD + ":input", typed("staticType", "INT", "int", "int", "name", "input", "position", 0,
        "sourceDeclared", true));
    expected.node("JavaParameter", CONSTRUCTOR + ":value", typed("staticType", "DECLARED", "java.lang.String", "java.lang.String", "name", "value",
        "position", 0, "sourceDeclared", false));
    expected.node("JavaParameter", CONSTRUCTOR + ":rest", typed("staticType", "ARRAY", "java.lang.String[]", "java.lang.String[]", "name", "rest",
        "position", 1, "sourceDeclared", false));
    expected.edge("DECLARES_PARAMETER", METHOD, METHOD + ":input", "0", "position", 0);
    expected.edge("DECLARES_PARAMETER", CONSTRUCTOR, CONSTRUCTOR + ":value", "0", "position", 0);
    expected.edge("DECLARES_PARAMETER", CONSTRUCTOR, CONSTRUCTOR + ":rest", "1", "position", 1);
    expected.origin(METHOD + ":input", 0, SOURCE, 0L, 12L);
    expected.node("MethodAnalysis", "supplied-analysis", properties("methodKey", METHOD));
    expected.node("ControlFlowGraph", GRAPH, properties("methodKey", METHOD));
    expected.edge("HAS_METHOD_ANALYSIS", program, "supplied-analysis", "0", "position", 0);
    expected.edge("ANALYZES_METHOD", "supplied-analysis", METHOD, "method");
    expected.edge("HAS_CFG", "supplied-analysis", GRAPH, "cfg");
    expected.node("FlowValue", "value:parameter", typed("staticType", "INT", "int", "int", "graphId", GRAPH, "kind", "PARAMETER", "position", 0,
        "displayName", "input"));
    expected.node("FlowValue", "value:constant", typed("staticType", "INT", "int", "int", "graphId", GRAPH, "kind", "CONSTANT", "position", 1,
        "literal", "7"));
    expected.node("FlowValue", "value:unknown", properties("graphId", GRAPH, "kind", "TEMPORARY", "position", 2));
    expected.edge("HAS_VALUE", GRAPH, "value:parameter", "0", "position", 0);
    expected.edge("HAS_VALUE", GRAPH, "value:constant", "1", "position", 1);
    expected.edge("HAS_VALUE", GRAPH, "value:unknown", "2", "position", 2);
    expected.node("EntryNode", "node:entry", properties("graphId", GRAPH));
    expected.node("OperationNode", "node:invoke", typed("qualifyingType", "DECLARED", "External", "External", "graphId", GRAPH, "kind", "INVOKE",
        "detail", "External.compute", "possibleExceptions", List.of("java.lang.ArithmeticException", "java.lang.OutOfMemoryError"), "invocationMode",
        "STATIC", "invocationForm", "STATIC_TYPE", "qualifyingTypeDeclarationKey", "type:External"));
    expected.node("OperationNode", "node:construct", typed("qualifyingType", "DECLARED", "External", "External", "graphId", GRAPH, "kind",
        "NEW_OBJECT", "detail", "new External", "possibleExceptions", List.of("java.lang.OutOfMemoryError"), "invocationMode", "CONSTRUCTOR",
        "invocationForm", "OBJECT_CREATION"));
    expected.node("OperationNode", "node:return", properties("graphId", GRAPH, "kind", "RETURN", "detail", "return", "possibleExceptions", List.of()));
    expected.node("ExitNode", "node:exit", properties("graphId", GRAPH));
    expected.edge("HAS_NODE", GRAPH, "node:entry", "0", "position", 0);
    expected.edge("HAS_NODE", GRAPH, "node:invoke", "1", "position", 1);
    expected.edge("HAS_NODE", GRAPH, "node:construct", "2", "position", 2);
    expected.edge("HAS_NODE", GRAPH, "node:return", "3", "position", 3);
    expected.edge("HAS_NODE", GRAPH, "node:exit", "4", "position", 4);
    expected.edge("USES_VALUE", "node:invoke", "value:parameter", "0", "position", 0);
    expected.edge("USES_VALUE", "node:invoke", "value:constant", "1", "position", 1);
    expected.edge("DEFINES_VALUE", "node:invoke", "value:unknown", "definition");
    expected.origin("node:invoke", 0, SOURCE, 0L, 12L);
    expected.origin("node:invoke", 1, SUPPORT, 1L, 9L);
    var resolved = StableId.sha256("semantic-reference", "node:invoke", "0");
    var unresolved = StableId.sha256("semantic-reference", "node:invoke", "1");
    var symbolOnly = StableId.sha256("semantic-reference", "node:invoke", "2");
    var typeOnly = StableId.sha256("semantic-reference", "node:invoke", "3");
    expected.node("SemanticReference", resolved, properties("kind", "METHOD", "status", "RESOLVED", "position", 0, "symbolKey",
        "type:External#compute", "typeName", "int", "typeAlternativeNames", List.of("int"), "assignableToTypeNames", List.of("int")));
    expected.node("SemanticReference", unresolved, properties("kind", "TYPE", "status", "UNRESOLVED", "position", 1, "typeAlternativeNames",
        List.of(), "assignableToTypeNames", List.of()));
    expected.node("SemanticReference", symbolOnly, properties("kind", "PACKAGE", "status", "RESOLVED", "position", 2, "symbolKey",
        "package:external", "typeAlternativeNames", List.of(), "assignableToTypeNames", List.of()));
    expected.node("SemanticReference", typeOnly, properties("kind", "FIELD", "status", "UNRESOLVED", "position", 3, "typeName",
        "java.lang.String", "typeAlternativeNames", List.of("java.lang.String"), "assignableToTypeNames", List.of("java.lang.Object", "java.lang.String")));
    expected.edge("HAS_REFERENCE", "node:invoke", resolved, "0", "position", 0);
    expected.edge("HAS_REFERENCE", "node:invoke", unresolved, "1", "position", 1);
    expected.edge("HAS_REFERENCE", "node:invoke", symbolOnly, "2", "position", 2);
    expected.edge("HAS_REFERENCE", "node:invoke", typeOnly, "3", "position", 3);
    expected.edge("NORMAL_FLOW", "node:entry", "node:invoke", "edge:normal", "edgeId", "edge:normal");
    expected.edge("TRUE_FLOW", "node:invoke", "node:return", "edge:true", "edgeId", "edge:true", "discriminator", "condition");
    expected.edge("FALSE_FLOW", "node:invoke", "node:construct", "edge:false", "edgeId", "edge:false", "discriminator", "condition");
    expected.edge("SWITCH_FLOW", "node:construct", "node:return", "edge:switch", "edgeId", "edge:switch", "discriminator", "case:7");
    expected.edge("RETURN_FLOW", "node:return", "node:exit", "edge:return", "edgeId", "edge:return");
    expected.edge("EXCEPTIONAL_FLOW", "node:invoke", "node:exit", "edge:exception", "edgeId", "edge:exception", "discriminator",
        "java.lang.ArithmeticException");
    return expected;
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
      assertNull(nodes.put(key, new NodeExpectation(label, Map.copyOf(properties))), "Expected node keys must not be repeated");
    }

    void edge(String type, String source, String target, String discriminator, Object... pairs) {
      var key = StableId.sha256("relationship", type, source, target, discriminator);
      var properties = properties(pairs);
      properties.put("runId", runId);
      properties.put("key", key);
      assertNull(relationships.put(key, new RelationshipExpectation(type, source, target, Map.copyOf(properties))),
          "Expected relationship keys must not be repeated");
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
