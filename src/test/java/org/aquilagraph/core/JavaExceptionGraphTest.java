package org.aquilagraph.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.ControlFlowGraph;
import org.aquilagraph.model.DiagnosticSeverity;
import org.aquilagraph.model.DiagnosticStage;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.OperationNode;
import org.aquilagraph.model.TransferKind;
import org.aquilagraph.model.SourceOwnerPath;
import org.aquilagraph.syntax.SyntaxAnalysisInput;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxType;
import org.aquilagraph.syntax.SyntaxValue;
import org.aquilagraph.syntax.JavaTypeStructure;
import org.aquilagraph.syntax.JavaSourceKind;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaExceptionGraphTest {
  @TempDir Path sourceDirectory;

  @Test
  void givenBroadNullableThrow_whenFrontendAndGraphRun_thenNarrowCatchPossibilitiesAndSeparateNpeCategoryAreRetained() throws IOException {
    // Arrange
    Files.writeString(sourceDirectory.resolve("Throws.java"), """
        class Throws {
          void execute(Exception failure) {
            try { throw failure; }
            catch (java.io.IOException checkedFailure) { }
            catch (RuntimeException uncheckedFailure) { }
            catch (Exception remainingFailure) { }
          }
        }
        """);

    // Act
    var input = analyze();
    var graph = graph(input);
    var thrown = thrown(graph);

    // Assert
    assertEquals(AnalysisCompletion.COMPLETED, new AnalysisEngine().analyze(input).completion());
    assertEquals(List.of("java.lang.Exception", "java.lang.NullPointerException"), thrown.possibleExceptions());
    var exceptionalCategories = graph.edges().stream()
        .filter(edge -> edge.sourceNodeId().equals(thrown.nodeId()) && edge.transferKind() == TransferKind.EXCEPTIONAL)
        .map(edge -> edge.discriminator().orElseThrow()).toList();
    assertEquals(List.of("java.lang.Exception", "java.lang.NullPointerException"), exceptionalCategories.stream().distinct().toList());
    assertTrue(exceptionalCategories.lastIndexOf("java.lang.Exception") < exceptionalCategories.indexOf("java.lang.NullPointerException"));
    assertEquals(List.of(caught(graph, "java.io.IOException").nodeId(), caught(graph, "java.lang.RuntimeException").nodeId(),
        caught(graph, "java.lang.Exception").nodeId()), targets(graph, thrown, "java.lang.Exception"));
    assertTrue(targets(graph, thrown, "java.lang.NullPointerException").contains(caught(graph, "java.lang.RuntimeException").nodeId()));
    assertFalse(exceptionalExit(graph, thrown));
  }

  @Test
  void givenExplicitNullThrow_whenFrontendAndGraphRun_thenOnlyExactNpeReachesItsFirstProvenCatch() throws IOException {
    // Arrange
    Files.writeString(sourceDirectory.resolve("Throws.java"), """
        class Throws {
          void execute() {
            try { throw null; }
            catch (NullPointerException nullFailure) { }
            catch (Exception remainingFailure) { }
          }
        }
        """);

    // Act
    var input = analyze();
    var graph = graph(input);
    var thrown = thrown(graph);

    // Assert
    assertEquals(AnalysisCompletion.COMPLETED, new AnalysisEngine().analyze(input).completion());
    assertEquals(List.of("java.lang.NullPointerException"), thrown.possibleExceptions());
    assertEquals(List.of(caught(graph, "java.lang.NullPointerException").nodeId()), targets(graph, thrown, "java.lang.NullPointerException"));
    assertFalse(exceptionalExit(graph, thrown));
  }

  @Test
  void givenDirectConstructedThrow_whenFrontendAndGraphRun_thenSuccessfulNewIsExactNonNullAndStopsAtItsFirstMatch() throws IOException {
    // Arrange
    Files.writeString(sourceDirectory.resolve("Throws.java"), """
        class Throws {
          void execute() {
            try { throw new java.io.IOException(); }
            catch (java.io.IOException checkedFailure) { }
            catch (Exception remainingFailure) { }
          }
        }
        """);

    // Act
    var input = analyze();
    var graph = graph(input);
    var thrown = thrown(graph);

    // Assert
    assertEquals(AnalysisCompletion.COMPLETED, new AnalysisEngine().analyze(input).completion());
    assertEquals(List.of("java.io.IOException"), thrown.possibleExceptions());
    assertEquals(List.of(caught(graph, "java.io.IOException").nodeId()), targets(graph, thrown, "java.io.IOException"));
    assertFalse(exceptionalExit(graph, thrown));
    var creation = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.NEW_OBJECT).findFirst().orElseThrow();
    assertEquals(creation.operands().getFirst().value(), thrown.operands().getFirst().value());
    assertTrue(graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(creation.nodeId())
        && edge.targetNodeId().equals(thrown.nodeId()) && edge.transferKind() == TransferKind.NORMAL));
  }

  @Test
  void givenNullableThrowAndQualifiedMultiCatch_whenFrontendAndGraphRun_thenWrittenUnionRefusalAndAttributedAlternativeRoutesAreRetained() throws IOException {
    // Arrange
    Files.writeString(sourceDirectory.resolve("Throws.java"), """
        class Throws {
          void execute(Exception failure) {
            try { throw failure; }
            catch (java.io.IOException | IllegalArgumentException combinedFailure) { }
            catch (Exception remainingFailure) { }
          }
        }
        """);

    // Act
    var input = analyze();
    var graph = graph(input);
    var thrown = thrown(graph);
    var run = new AnalysisEngine().analyze(input);

    // Assert
    assertEquals(AnalysisCompletion.INCOMPLETE, run.completion());
    var errors = run.diagnostics().stream().filter(AnalysisDiagnostic::isError).toList();
    assertEquals(List.of("frontend.unsupported-tree-kind.union-type"), errors.stream().map(AnalysisDiagnostic::code).toList());
    assertEquals(DiagnosticStage.FRONTEND, errors.getFirst().stage());
    assertEquals(DiagnosticSeverity.ERROR, errors.getFirst().severity());
    var handlers = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.CATCH).toList();
    assertEquals(2, handlers.size());
    assertEquals(handlers.stream().map(OperationNode::nodeId).toList(), targets(graph, thrown, "java.lang.Exception"));
    assertEquals(List.of("java.lang.Exception", "java.lang.NullPointerException"), thrown.possibleExceptions());
    assertFalse(exceptionalExit(graph, thrown));
    var methodKey = input.program().methods().stream().filter(method -> method.name().equals("execute"))
        .findFirst().orElseThrow().methodKey();
    var body = input.methodSyntax(methodKey).orElseThrow();
    var parameter = body.root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.CATCH).findFirst().orElseThrow()
        .requiredChild(SyntaxRole.PARAMETER);
    var alternatives = parameter.requiredReference().typeAlternativeNames();
    assertEquals(List.of("java.io.IOException", "java.lang.IllegalArgumentException"), alternatives);
    var aggregate = input.aggregate().orElseThrow();
    var declarations = aggregate.nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration declaration
            && declaration.declarationKey().equals(methodKey)).toList();
    assertEquals(1, declarations.size());
    var sourceMethod = declarations.getFirst();
    var sourceBody = sourceMethod.requiredChild(SyntaxRole.BODY);
    var matches = sourceBody.descendantsAndSelf().stream()
        .filter(node -> node.sourceFileKey().equals(parameter.sourceFileKey())
            && node.sourceKind() == parameter.sourceKind() && node.syntaxKind() == parameter.syntaxKind()
            && node.origin().equals(parameter.origin()) && node.value().equals(parameter.value())
            && node.type().equals(parameter.type()) && node.semanticReference().equals(parameter.semanticReference()))
        .toList();
    assertEquals(1, matches.size());
    var sourceParameter = matches.getFirst();
    assertEquals(sourceParameter, aggregate.node(sourceParameter.nodeId()).orElseThrow());
    var executionSteps = parameter.structuralPath().steps();
    var executionRootSteps = body.root().structuralPath().steps();
    var sourceSteps = sourceParameter.structuralPath().steps();
    var sourceRootSteps = sourceBody.structuralPath().steps();
    assertEquals(executionRootSteps, executionSteps.subList(0, executionRootSteps.size()));
    assertEquals(sourceRootSteps, sourceSteps.subList(0, sourceRootSteps.size()));
    assertEquals(executionSteps.subList(executionRootSteps.size(), executionSteps.size()),
        sourceSteps.subList(sourceRootSteps.size(), sourceSteps.size()));
    var owner = aggregate.writtenSourceAssociation(sourceMethod.nodeId()).orElseThrow();
    var bodyOwner = aggregate.writtenSourceAssociation(sourceBody.nodeId()).orElseThrow();
    var parameterOwner = aggregate.writtenSourceAssociation(sourceParameter.nodeId()).orElseThrow();
    assertEquals(owner.ownerPath().child(SourceOwnerPath.Role.BODY, 0), bodyOwner.ownerPath());
    assertTrue(bodyOwner.ownerPath().isAncestorOf(parameterOwner.ownerPath()));
    assertEquals(sourceParameter.nodeId(), parameterOwner.nodeKey());
    assertEquals(parameter.sourceFileKey(), parameterOwner.sourceFileKey());
    assertEquals(parameter.sourceKind(), parameterOwner.sourceKind());
    assertEquals(parameter.origin(), parameterOwner.origin());
    assertTrue(body.root().descendantsAndSelf().stream()
        .allMatch(node -> node.children(SyntaxRole.DECLARED_TYPE).isEmpty() && node.children(SyntaxRole.TYPE_OPERAND).isEmpty()));
    assertTrue(body.root().descendantsAndSelf().stream()
        .noneMatch(node -> node.syntaxKind() == SyntaxKind.TYPE_USE || node.syntaxKind() == SyntaxKind.ANNOTATION));
    var written = sourceParameter.requiredChild(SyntaxRole.DECLARED_TYPE);
    assertEquals(SyntaxKind.TYPE_USE, written.syntaxKind());
    assertEquals(JavaSourceKind.UNION_TYPE, written.sourceKind());
    assertEquals(new JavaTypeStructure.Union(List.of(
        new JavaTypeStructure.Declared("type:java.io.IOException", Optional.empty(), List.of()),
        new JavaTypeStructure.Declared("type:java.lang.IllegalArgumentException", Optional.empty(), List.of()))),
        ((SyntaxType.Resolved) written.type()).structure().orElseThrow());
    assertEquals(List.of(JavaSourceKind.MEMBER_SELECT, JavaSourceKind.IDENTIFIER),
        written.children(SyntaxRole.UNION_ALTERNATIVE).stream().map(node -> node.sourceKind()).toList());
    var origin = written.origin().orElseThrow();
    assertEquals("java.io.IOException | IllegalArgumentException", input.snapshot().sourceFiles().getFirst().content()
        .substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive())));
  }

  @Test
  void givenReferenceCastsOfNull_whenFrontendAndGraphRun_thenThrowHasOnlyExactNpeInsteadOfItsStaticCastBound() throws IOException {
    // Arrange, Act and Assert
    for (var target : List.of("Exception", "RuntimeException")) {
      Files.writeString(sourceDirectory.resolve("Throws.java"), """
          class Throws {
            void execute() {
              try { throw (%s) (null); }
              catch (NullPointerException nullFailure) { }
              catch (Exception remainingFailure) { }
            }
          }
          """.formatted(target));
      var input = analyze();
      var graph = graph(input);
      var thrown = thrown(graph);

      assertEquals(AnalysisCompletion.COMPLETED, new AnalysisEngine().analyze(input).completion());
      assertEquals(List.of("java.lang.NullPointerException"), thrown.possibleExceptions());
      assertEquals(List.of(caught(graph, "java.lang.NullPointerException").nodeId()), targets(graph, thrown, "java.lang.NullPointerException"));
      assertFalse(exceptionalExit(graph, thrown));
      var cast = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.CAST).findFirst().orElseThrow();
      assertTrue(cast.possibleExceptions().isEmpty());
      assertEquals(cast.definedValue().orElseThrow(), thrown.operands().getFirst().value());
    }
  }

  @Test
  void givenWideningReferenceCastOfFreshException_whenFrontendAndGraphRun_thenThrowRetainsItsActualRuntimeClassAndNonNullProvenance() throws IOException {
    // Arrange
    Files.writeString(sourceDirectory.resolve("Throws.java"), """
        class Throws {
          void execute() {
            try { throw (Exception) new IllegalArgumentException(); }
            catch (IllegalArgumentException argumentFailure) { }
            catch (Exception remainingFailure) { }
          }
        }
        """);

    // Act
    var input = analyze();
    var graph = graph(input);
    var thrown = thrown(graph);

    // Assert
    assertEquals(AnalysisCompletion.COMPLETED, new AnalysisEngine().analyze(input).completion());
    assertEquals(List.of("java.lang.IllegalArgumentException"), thrown.possibleExceptions());
    assertEquals(List.of(caught(graph, "java.lang.IllegalArgumentException").nodeId()), targets(graph, thrown, "java.lang.IllegalArgumentException"));
    assertFalse(exceptionalExit(graph, thrown));
    var creation = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.NEW_OBJECT).findFirst().orElseThrow();
    var cast = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.CAST).findFirst().orElseThrow();
    assertEquals(creation.definedValue().orElseThrow(), cast.operands().getFirst().value());
    assertEquals(cast.definedValue().orElseThrow(), thrown.operands().getFirst().value());
    assertTrue(cast.possibleExceptions().isEmpty());
    assertTrue(graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(creation.nodeId())
        && edge.targetNodeId().equals(cast.nodeId()) && edge.transferKind() == TransferKind.NORMAL));
    assertTrue(graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(cast.nodeId())
        && edge.targetNodeId().equals(thrown.nodeId()) && edge.transferKind() == TransferKind.NORMAL));
  }

  private SyntaxAnalysisInput analyze() throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).analyze(sourceDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
  }

  private static ControlFlowGraph graph(SyntaxAnalysisInput input) {
    var method = input.program().methods().stream().filter(candidate -> candidate.name().equals("execute")).findFirst().orElseThrow();
    var result = new ControlFlowGraphBuilder().build(method, input.methodSyntax(method.methodKey()).orElseThrow());
    assertTrue(result.diagnostics().isEmpty());
    return result.graph().orElseThrow();
  }

  private static OperationNode thrown(ControlFlowGraph graph) {
    var thrown = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.THROW).toList();
    assertEquals(1, thrown.size());
    return thrown.getFirst();
  }

  private static OperationNode caught(ControlFlowGraph graph, String typeName) {
    return graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.CATCH)
        .filter(node -> node.definedValue().orElseThrow().staticType().orElseThrow().erasedName().equals(typeName)).findFirst().orElseThrow();
  }

  private static List<String> targets(ControlFlowGraph graph, OperationNode thrown, String category) {
    return graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(thrown.nodeId()) && edge.transferKind() == TransferKind.EXCEPTIONAL
        && edge.discriminator().filter(category::equals).isPresent()).map(edge -> edge.targetNodeId()).toList();
  }

  private static boolean exceptionalExit(ControlFlowGraph graph, OperationNode thrown) {
    return graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(thrown.nodeId())
        && edge.targetNodeId().equals(graph.exit().nodeId()) && edge.transferKind() == TransferKind.EXCEPTIONAL);
  }
}
