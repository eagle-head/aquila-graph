package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import javax.tools.Diagnostic;
import javax.tools.ToolProvider;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SyntaxKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnitTranslationLifecycleTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final JavaTranslationPolicy POLICY = JavaTranslationPolicy.forProfile(AstProfile.JAVA_SOURCE_UNITS);
  @TempDir Path directory;

  @Test
  void givenDeclarationFreeUnit_whenGatedAndFinalizedOnce_thenCapturedEnvelopeAndTerminalProgressionRemain() throws IOException {
    // Arrange
    withCompilation(List.of(SourceFile.fromContent(Path.of("Empty.java"), "")), fixture -> {
      var reached = new ArrayList<String>();
      var coordinator = fixture.factory(fixture.compilation().sourceEvidence(), recording(reached)).create(
          fixture.compilation().units().getFirst(), fixture.snapshot().sourceFiles().getFirst());
      assertThrows(IllegalStateException.class, coordinator::projectSource);
      assertThrows(IllegalStateException.class, coordinator::finalizeSource);
      assertThrows(IllegalStateException.class, () -> coordinator.decideProjection(JavaUnitTranslator.ProjectionDecision.ALLOW));

      // Act
      var declarations = coordinator.translateDeclarations();
      assertThrows(IllegalStateException.class, coordinator::translateDeclarations);
      assertThrows(IllegalStateException.class, coordinator::projectSource);
      coordinator.decideProjection(JavaUnitTranslator.ProjectionDecision.ALLOW);
      assertThrows(IllegalStateException.class, () -> coordinator.decideProjection(JavaUnitTranslator.ProjectionDecision.ALLOW));
      assertThrows(IllegalStateException.class, coordinator::finalizeSource);
      coordinator.projectSource();
      assertThrows(IllegalStateException.class, coordinator::projectSource);
      var finalized = coordinator.finalizeSource();

      // Assert
      assertTrue(declarations.value().complete());
      assertTrue(declarations.value().types().isEmpty());
      assertEquals(List.of("Empty.java"), reached);
      assertEquals(SyntaxKind.COMPILATION_UNIT, finalized.contribution().orElseThrow().root().syntaxKind());
      assertTrue(finalized.contribution().orElseThrow().root().children().isEmpty());
      assertEquals(0, finalized.contribution().orElseThrow().root().origin().orElseThrow().endOffsetExclusive());
      assertTrue(finalized.memberOrders().isEmpty());
      assertTrue(finalized.declarationTypes().isEmpty());
      assertThrows(IllegalStateException.class, coordinator::finalizeSource);
      assertThrows(IllegalStateException.class, coordinator::translateDeclarations);
      assertThrows(IllegalStateException.class, coordinator::projectSource);
      assertThrows(IllegalStateException.class, () -> coordinator.decideProjection(JavaUnitTranslator.ProjectionDecision.WITHHOLD));
    });
  }

  @Test
  void givenWithheldOrFailedUnit_whenAnyPhaseIsResumed_thenNoProjectionOrSecondContributionOccurs() throws IOException {
    // Arrange
    withCompilation(validSources(), fixture -> {
      var reached = new ArrayList<String>();
      var withheld = fixture.factory(fixture.compilation().sourceEvidence(), recording(reached)).create(
          fixture.compilation().units().getFirst(), fixture.snapshot().sourceFiles().getFirst());
      var failure = new IllegalStateException("Controlled source-projection boundary failure");
      var failed = fixture.factory(fixture.compilation().sourceEvidence(), ignored -> (unit, source, bodies, types) -> {
        reached.add("failed");
        throw failure;
      }).create(fixture.compilation().units().getFirst(), fixture.snapshot().sourceFiles().getFirst());

      // Act
      withheld.translateDeclarations();
      withheld.decideProjection(JavaUnitTranslator.ProjectionDecision.WITHHOLD);
      failed.translateDeclarations();
      failed.decideProjection(JavaUnitTranslator.ProjectionDecision.ALLOW);
      var actual = assertThrows(IllegalStateException.class, failed::projectSource);

      // Assert
      assertSame(failure, actual);
      assertEquals(List.of("failed"), reached);
      for (var coordinator : List.of(withheld, failed)) {
        assertThrows(IllegalStateException.class, coordinator::translateDeclarations);
        assertThrows(IllegalStateException.class, coordinator::projectSource);
        assertThrows(IllegalStateException.class, coordinator::finalizeSource);
        assertThrows(IllegalStateException.class, () -> coordinator.decideProjection(JavaUnitTranslator.ProjectionDecision.ALLOW));
      }
      var healthy = new JavaProgramTranslator(fixture.sources(), fixture.factory(fixture.compilation().sourceEvidence(), recording(reached)))
          .translate(fixture.snapshot(), fixture.compilation().units());
      assertTrue(healthy.value().aggregate().isPresent());
      assertEquals(List.of("failed", "A.java", "B.java", "C.java"), reached);
    });
  }

  @Test
  void givenProjectionError_whenTheOperationAborts_thenOriginalErrorEscapesAndUnitCannotResume() throws IOException {
    // Arrange
    withCompilation(validSources(), fixture -> {
      var failure = new AssertionError("Controlled projection abort");
      var coordinator = fixture.factory(fixture.compilation().sourceEvidence(), ignored -> (unit, source, bodies, types) -> {
        throw failure;
      }).create(fixture.compilation().units().getFirst(), fixture.snapshot().sourceFiles().getFirst());
      coordinator.translateDeclarations();
      coordinator.decideProjection(JavaUnitTranslator.ProjectionDecision.ALLOW);

      // Act
      var actual = assertThrows(AssertionError.class, coordinator::projectSource);

      // Assert
      assertSame(failure, actual);
      assertThrows(IllegalStateException.class, coordinator::projectSource);
      assertThrows(IllegalStateException.class, coordinator::finalizeSource);
      assertThrows(IllegalStateException.class, coordinator::translateDeclarations);
      assertThrows(IllegalStateException.class, () -> coordinator.decideProjection(JavaUnitTranslator.ProjectionDecision.ALLOW));
    });
  }

  @Test
  void givenMissingUnitAtEachPosition_whenProgramProjectsRemainingUnits_thenAggregateIsWithheldAndRetryIsIndependent()
      throws IOException {
    for (var position : List.of(0, 1, 2)) {
      // Arrange
      withCompilation(validSources(), fixture -> {
        var reached = new ArrayList<String>();
        var program = new JavaProgramTranslator(fixture.sources(), fixture.factory(fixture.compilation().sourceEvidence(), recording(reached)));
        var missing = fixture.compilation().units().get(position);
        var supplied = fixture.compilation().units().stream().filter(unit -> unit != missing).toList();

        // Act
        var withheld = program.translate(fixture.snapshot(), supplied);
        var reachedBeforeRetry = List.copyOf(reached);
        reached.clear();
        var restored = program.translate(fixture.snapshot(), fixture.compilation().units());

        // Assert
        assertTrue(withheld.value().aggregate().isEmpty());
        assertTrue(withheld.value().program().types().isEmpty());
        assertEquals(List.of("frontend.missing-compilation-unit"), withheld.diagnostics().stream().map(value -> value.code()).toList());
        assertEquals(fixture.snapshot().sourceFiles().stream().filter(source -> source != fixture.snapshot().sourceFiles().get(position))
            .map(source -> source.relativePath().toString()).toList(), reachedBeforeRetry);
        assertEquals(List.of("A.java", "B.java", "C.java"), reached);
        assertTrue(restored.value().aggregate().isPresent());
      });
    }
  }

  @Test
  void givenDuplicateMembersAtEachPosition_whenDeclarationsAreInspected_thenRejectedUnitNeverProjectsAndWholeRootIsWithheld()
      throws IOException {
    for (var position : List.of(0, 1, 2)) {
      // Arrange
      var files = new ArrayList<>(validSources());
      var name = List.of("A", "B", "C").get(position);
      files.set(position, SourceFile.fromContent(Path.of(name + ".java"), "class " + name + " { void value() {} void value() {} }"));
      withCompilation(files, fixture -> {
        var reached = new ArrayList<String>();
        var program = new JavaProgramTranslator(fixture.sources(), fixture.factory(fixture.compilation().sourceEvidence(), recording(reached)));

        // Act
        var withheld = program.translate(fixture.snapshot(), fixture.compilation().units(), true);

        // Assert
        assertTrue(fixture.compilation().diagnostics().stream().anyMatch(value -> value.getKind() == Diagnostic.Kind.ERROR));
        assertTrue(withheld.diagnostics().stream().anyMatch(value -> value.code().equals("frontend.duplicate-member-declaration")),
            withheld.diagnostics().toString());
        assertFalse(reached.contains(name + ".java"));
        assertEquals(2, reached.size());
        assertTrue(withheld.value().aggregate().isEmpty());
        assertTrue(withheld.value().program().types().isEmpty());
      });
    }
  }

  @Test
  void givenDuplicateTypesAtEachUnitPosition_whenDeclarationsAreInspected_thenRejectedUnitNeverProjects() throws IOException {
    for (var position : List.of(0, 1, 2)) {
      // Arrange
      var files = new ArrayList<>(validSources());
      var name = List.of("A", "B", "C").get(position);
      files.set(position, SourceFile.fromContent(Path.of(name + ".java"), "class " + name + " {} class " + name + " {}"));
      withCompilation(files, fixture -> {
        var reached = new ArrayList<String>();
        var program = new JavaProgramTranslator(fixture.sources(),
            fixture.factory(fixture.compilation().sourceEvidence(), recording(reached)));

        // Act
        var withheld = program.translate(fixture.snapshot(), fixture.compilation().units(), true);

        // Assert
        assertTrue(fixture.compilation().diagnostics().stream().anyMatch(value -> value.getKind() == Diagnostic.Kind.ERROR));
        assertTrue(withheld.value().aggregate().isEmpty());
        assertTrue(withheld.value().program().types().isEmpty());
        assertFalse(reached.contains(name + ".java"));
        assertEquals(2, reached.size());
        assertTrue(withheld.diagnostics().stream().anyMatch(value -> value.code().equals("frontend.duplicate-type-declaration")),
            withheld.diagnostics().toString());
      });
    }
  }

  @Test
  void givenLaterDuplicateType_whenAnEarlierUnitAlreadyProjected_thenCurrentProjectionIsPreventedAndAggregateIsWithheld() throws IOException {
    // Arrange
    withCompilation(List.of(SourceFile.fromContent(Path.of("A.java"), "class Shared {}"),
        SourceFile.fromContent(Path.of("B.java"), "class Shared {}"), SourceFile.fromContent(Path.of("C.java"), "class C {}")), fixture -> {
      var reached = new ArrayList<String>();
      var program = new JavaProgramTranslator(fixture.sources(), fixture.factory(fixture.compilation().sourceEvidence(), recording(reached)));

      // Act
      var withheld = program.translate(fixture.snapshot(), fixture.compilation().units(), true);

      // Assert
      assertTrue(fixture.compilation().diagnostics().stream().anyMatch(value -> value.getKind() == Diagnostic.Kind.ERROR));
      assertTrue(withheld.value().aggregate().isEmpty());
      assertTrue(withheld.value().program().types().isEmpty());
      assertTrue(reached.contains("A.java"));
      assertFalse(reached.contains("B.java"));
      assertTrue(reached.contains("C.java"));
      assertTrue(withheld.diagnostics().stream().anyMatch(value -> value.code().equals("frontend.duplicate-type-declaration")),
          withheld.diagnostics().toString());
    });
  }

  @Test
  void givenIncompleteRecordMetadataAtEachPosition_whenRequiredEvidenceIsRestored_thenGatePreventsProjectionBeforeIndependentRetry()
      throws IOException {
    for (var position : List.of(0, 1, 2)) {
      // Arrange
      var files = new ArrayList<>(validSources());
      var name = List.of("A", "B", "C").get(position);
      files.set(position, SourceFile.fromContent(Path.of(name + ".java"), "record " + name + "(int value) {}"));
      withCompilation(files, fixture -> {
        var damaged = new AtomicBoolean(true);
        var actual = fixture.compilation().sourceEvidence();
        var evidence = new JavaSourceEvidence(new SourcePositions() {
          @Override public long getStartPosition(CompilationUnitTree unit, Tree tree) {
            return damaged.get() && tree instanceof VariableTree variable && variable.getName().contentEquals("value")
                ? Diagnostic.NOPOS : actual.rawStart(unit, tree);
          }
          @Override public long getEndPosition(CompilationUnitTree unit, Tree tree) { return actual.rawEnd(unit, tree); }
        });
        var reached = new ArrayList<String>();
        var program = new JavaProgramTranslator(fixture.sources(), fixture.factory(evidence, recording(reached)));
        var incomplete = fixture.factory(evidence, recording(reached)).create(fixture.compilation().units().get(position),
            fixture.snapshot().sourceFiles().get(position));

        // Act
        var metadata = incomplete.translateDeclarations();
        var invalidDecision = assertThrows(IllegalStateException.class,
            () -> incomplete.decideProjection(JavaUnitTranslator.ProjectionDecision.ALLOW));
        var withheld = program.translate(fixture.snapshot(), fixture.compilation().units());
        var firstReached = List.copyOf(reached);
        damaged.set(false);
        reached.clear();
        var restored = program.translate(fixture.snapshot(), fixture.compilation().units());

        // Assert
        assertFalse(metadata.value().complete());
        assertTrue(invalidDecision.getMessage().contains("Incomplete unit declarations"));
        assertThrows(IllegalStateException.class, incomplete::projectSource);
        assertThrows(IllegalStateException.class, incomplete::finalizeSource);
        assertTrue(withheld.value().aggregate().isEmpty());
        assertFalse(firstReached.contains(name + ".java"));
        assertEquals(2, firstReached.size());
        assertTrue(withheld.diagnostics().stream().anyMatch(value -> value.isError()));
        assertEquals(List.of("A.java", "B.java", "C.java"), reached);
        assertTrue(restored.value().aggregate().isPresent());
      });
    }
  }

  @Test
  void givenDuplicateOrForeignSuppliedCompilerUnit_whenInputsAreIndexed_thenAbruptFailurePrecedesAnyUnitProjection() throws IOException {
    // Arrange
    withCompilation(validSources(), fixture -> {
      var reached = new ArrayList<String>();
      var program = new JavaProgramTranslator(fixture.sources(), fixture.factory(fixture.compilation().sourceEvidence(), recording(reached)));
      var duplicate = new ArrayList<>(fixture.compilation().units());
      duplicate.add(fixture.compilation().units().getFirst());
      var narrowed = InputSnapshot.fromSources(directory, List.of(fixture.snapshot().sourceFiles().getFirst()));
      var foreignProgram = new JavaProgramTranslator(new SnapshotSourceCatalog(narrowed),
          fixture.factory(fixture.compilation().sourceEvidence(), recording(reached)));

      // Act
      var repeated = assertThrows(IllegalArgumentException.class, () -> program.translate(fixture.snapshot(), duplicate));
      var foreign = assertThrows(IllegalStateException.class, () -> foreignProgram.translate(narrowed, fixture.compilation().units()));
      var beforeNormal = List.copyOf(reached);
      var normal = program.translate(fixture.snapshot(), fixture.compilation().units());

      // Assert
      assertTrue(repeated.getMessage().contains("Duplicate parsed compilation unit"));
      assertTrue(foreign.getMessage().contains("not owned by the input snapshot"));
      assertTrue(beforeNormal.isEmpty());
      assertEquals(List.of("A.java", "B.java", "C.java"), reached);
      assertTrue(normal.value().aggregate().isPresent());
    });
  }

  private static UnaryOperator<JavaUnitTranslator.SourceProjection> recording(List<String> reached) {
    return delegate -> (unit, source, bodies, types) -> {
      reached.add(source.relativePath().toString());
      return delegate.project(unit, source, bodies, types);
    };
  }

  private static List<SourceFile> validSources() {
    return List.of(SourceFile.fromContent(Path.of("A.java"), "class A {}"), SourceFile.fromContent(Path.of("B.java"), "class B {}"),
        SourceFile.fromContent(Path.of("C.java"), "class C {}"));
  }

  private void withCompilation(List<SourceFile> files, Observation observation) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, files);
    var sources = new SnapshotSourceCatalog(snapshot);
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(CONFIGURATION), sources)) {
      observation.observe(new Fixture(snapshot, sources, session.analyze(session.parse(), POLICY)));
    }
  }

  @FunctionalInterface
  private interface Observation { void observe(Fixture fixture) throws IOException; }

  private record Fixture(InputSnapshot snapshot, SnapshotSourceCatalog sources, JavaCompilation compilation) {
    JavaUnitTranslationFactory factory(JavaSourceEvidence evidence, UnaryOperator<JavaUnitTranslator.SourceProjection> projections) {
      return new JavaUnitTranslationFactory(compilation.semantics(), evidence, compilation.treeInventory(), POLICY, projections);
    }
  }
}
