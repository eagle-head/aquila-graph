package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.SourcePositions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.tools.Diagnostic;
import javax.tools.ToolProvider;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.JavaTypeNesting;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnitTranslationFailureTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  @TempDir Path directory;

  @Test
  void givenAnEmittingEnumBodyBeforeALaterNestedRangeFailure_whenTheUnitUnwinds_thenTheEarlierBatchSurvivesThroughFinalSlots()
      throws IOException {
    // Arrange
    var text = "enum Sample { FIRST { @Missing class Earlier {} }, SECOND { class Later {} }; }";
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Sample.java"), text)));
    var source = snapshot.sourceFiles().getFirst();
    var sources = new SnapshotSourceCatalog(snapshot);
    var policy = JavaTranslationPolicy.forProfile(AstProfile.JAVA_SOURCE_UNITS);
    var damaged = new AtomicBoolean(true);
    var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CONFIGURATION, AstProfile.JAVA_SOURCE_UNITS);
    var originalEvidence = original.coverageQualification().orElseThrow().validationEvidence();
    DiagnosticEmissions creating;
    TranslatedProgram withheld;
    var expected = List.of(new DiagnosticEmission.Address("JavaAnnotationProjector", source.sourceFileKey(), 0),
        new DiagnosticEmission.Address("JavaSourceEvidence", source.sourceFileKey(), 0));
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(CONFIGURATION), sources)) {
      var compilation = session.analyze(session.parse(), policy);
      var actual = compilation.sourceEvidence();
      var positions = new SourcePositions() {
        @Override
        public long getStartPosition(CompilationUnitTree unit, Tree tree) {
          return damaged.get() && tree instanceof ClassTree type && type.getSimpleName().contentEquals("Later")
              ? Diagnostic.NOPOS : actual.rawStart(unit, tree);
        }

        @Override
        public long getEndPosition(CompilationUnitTree unit, Tree tree) {
          return actual.rawEnd(unit, tree);
        }
      };
      var translator = new JavaProgramTranslator(sources, compilation.semantics(), new JavaSourceEvidence(positions),
          compilation.treeInventory(), policy);

      // Act
      var failed = translator.translate(snapshot, compilation.units(), true);
      damaged.set(false);
      var restored = translator.translate(snapshot, compilation.units(), true);

      // Assert
      assertTrue(compilation.diagnostics().stream().anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
      assertTrue(failed.value().aggregate().isEmpty());
      assertTrue(failed.value().program().types().isEmpty());
      assertEquals(expected, failed.emissions().events().stream().map(DiagnosticEmission::address).toList());
      assertEquals(List.of("frontend.unresolved-annotation-type", "frontend.missing-source-position"),
          failed.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
      var annotationStart = text.indexOf("@Missing");
      assertEquals(List.of(new SourceSpan(source.sourceFileKey(), annotationStart, annotationStart + "@Missing".length())),
          failed.diagnostics().getFirst().origins());
      assertTrue(restored.value().aggregate().isPresent());
      assertEquals(List.of("frontend.unresolved-annotation-type"), restored.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
      assertEquals(expected.getFirst(), restored.emissions().events().getFirst().address());
      creating = DiagnosticEmissions.combine(new CompilerDiagnosticTranslator(sources).translate(compilation.diagnostics()), failed.emissions());
      withheld = failed.value();
    }
    var reconciled = new JavaSourceCoverageReconciler(originalEvidence.writtenInventory(), originalEvidence.attributedExpectations(),
        withheld.program(), withheld.aggregate(), creating)
        .reconcileRetained(JavaLanguageFeatureCoverage.fromConfiguration(CONFIGURATION), originalEvidence.outcomes());
    var finalEvidence = reconciled.coverageResult().validationEvidence();
    assertFalse(finalEvidence.diagnosticEmissions().getFirst().contexts().isEmpty());
    assertEquals(creating.events().stream().map(DiagnosticEmission::address).toList(),
        finalEvidence.diagnosticEmissions().subList(0, creating.events().size()).stream().map(DiagnosticEmission::address).toList());
    for (var index = 0; index < creating.events().size(); index++) {
      var originalEvent = creating.events().get(index);
      var retained = finalEvidence.diagnosticEmissions().get(index);
      assertSame(originalEvent.diagnostic(), retained.diagnostic());
      assertSame(reconciled.diagnostics().get(retained.slot()), retained.diagnostic());
      assertEquals(index, retained.slot());
      assertEquals(originalEvent.cause(), retained.cause());
      for (var context : retained.contexts()) {
        assertTrue(finalEvidence.outcomes().stream().anyMatch(value -> value.address().equals(context)
            && value.emissions().contains(retained.address())));
      }
      if (index > 0) {
        assertEquals(List.of(), retained.contexts());
        assertTrue(finalEvidence.outcomes().stream().noneMatch(value -> value.emissions().contains(retained.address())));
      }
    }
  }

  @Test
  void givenEarlierUnitAndExecutableEmissions_whenLaterNestedProjectionFails_thenCreatingEventsSurviveUnwindingAndFinalReconciliation()
      throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Sample.java"),
        "import java.util.List; class Sample { void subject() { int value = 1; class Nested {} } }")));
    var source = snapshot.sourceFiles().getFirst();
    var sources = new SnapshotSourceCatalog(snapshot);
    var policy = JavaTranslationPolicy.forProfile(AstProfile.JAVA_SOURCE_UNITS);
    var damaged = new AtomicBoolean(true);
    var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CONFIGURATION, AstProfile.JAVA_SOURCE_UNITS);
    var originalEvidence = original.coverageQualification().orElseThrow().validationEvidence();
    TranslationResult<TranslatedProgram> failed;
    List<DiagnosticEmission.Address> expected;
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(CONFIGURATION), sources)) {
      var compilation = session.analyze(session.parse(), policy);
      var actual = compilation.sourceEvidence();
      var positions = new SourcePositions() {
        @Override
        public long getStartPosition(CompilationUnitTree unit, Tree tree) {
          var omitted = tree instanceof ImportTree || tree instanceof LiteralTree literal && Integer.valueOf(1).equals(literal.getValue())
              || tree instanceof ClassTree type && type.getSimpleName().contentEquals("Nested");
          return damaged.get() && omitted ? Diagnostic.NOPOS : actual.rawStart(unit, tree);
        }

        @Override
        public long getEndPosition(CompilationUnitTree unit, Tree tree) {
          return actual.rawEnd(unit, tree);
        }
      };
      var unit = compilation.units().getFirst();
      var type = (ClassTree) unit.getTypeDecls().getFirst();
      var method = type.getMembers().stream().filter(MethodTree.class::isInstance).map(MethodTree.class::cast)
          .filter(candidate -> candidate.getName().contentEquals("subject")).findFirst().orElseThrow();
      var initializerPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
          .child(SyntaxRole.MEMBER, type.getMembers().indexOf(method)).child(SyntaxRole.BODY, 0)
          .child(SyntaxRole.STATEMENT, 0).child(SyntaxRole.INITIALIZER, 0);
      expected = List.of(new DiagnosticEmission.Address("JavaSourceEvidence", source.sourceFileKey(), 0),
          new DiagnosticEmission.Address("JavaMethodBodyTranslator", "type:Sample#subject():" + initializerPath, 0),
          new DiagnosticEmission.Address("JavaSourceEvidence", source.sourceFileKey(), 1));
      var translator = new JavaProgramTranslator(sources, compilation.semantics(), new JavaSourceEvidence(positions),
          compilation.treeInventory(), policy);

      // Act
      failed = translator.translate(snapshot, compilation.units());
      damaged.set(false);
      var complete = translator.translate(snapshot, compilation.units());

      // Assert
      assertFalse(compilation.diagnostics().stream().anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
      assertTrue(failed.value().aggregate().isEmpty());
      assertTrue(failed.value().program().types().isEmpty());
      assertEquals(expected, failed.emissions().events().stream().map(DiagnosticEmission::address).toList());
      assertEquals(List.of("frontend.missing-source-position", "frontend.missing-source-position", "frontend.missing-source-position"),
          failed.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
      assertEquals(List.of(), complete.diagnostics());
      assertTrue(complete.value().aggregate().isPresent());
    }
    var reconciled = new JavaSourceCoverageReconciler(originalEvidence.writtenInventory(), originalEvidence.attributedExpectations(),
        failed.value().program(), failed.value().aggregate(), failed.emissions())
        .reconcileRetained(JavaLanguageFeatureCoverage.fromConfiguration(CONFIGURATION), originalEvidence.outcomes());
    var finalEvidence = reconciled.coverageResult().validationEvidence();
    for (var index = 0; index < expected.size(); index++) {
      var creating = failed.emissions().events().get(index);
      var event = finalEvidence.diagnosticEmissions().get(index);
      assertEquals(expected.get(index), event.address());
      assertSame(creating.diagnostic(), event.diagnostic());
      assertSame(reconciled.diagnostics().get(event.slot()), event.diagnostic());
      assertEquals(creating.cause(), event.cause());
      assertEquals(List.of(), event.contexts());
      assertTrue(finalEvidence.outcomes().stream().noneMatch(value -> value.emissions().contains(event.address())));
    }
  }

  @Test
  void givenMissingMiddleCompilerUnit_whenProgramTranslationResumesWithAllUnits_thenWholeProgramWithholdingAndEventAddressRemain()
      throws IOException {
    // Arrange
    var snapshot = snapshot();
    var sources = new SnapshotSourceCatalog(snapshot);
    var policy = JavaTranslationPolicy.forProfile(AstProfile.JAVA_SOURCE_UNITS);
    TranslatedProgram recovered;
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(CONFIGURATION), sources)) {
      var compilation = session.analyze(session.parse(), policy);
      var units = compilation.units();
      var translator = new JavaProgramTranslator(sources, compilation.semantics(), compilation.sourceEvidence(),
          compilation.treeInventory(), policy);

      // Act
      var missing = translator.translate(snapshot, List.of(units.getFirst(), units.getLast()));
      var complete = translator.translate(snapshot, units);
      recovered = complete.value();

      // Assert
      assertTrue(missing.value().aggregate().isEmpty());
      assertTrue(missing.value().program().types().isEmpty());
      assertEquals(List.of("frontend.missing-compilation-unit"), missing.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
      assertEquals(new DiagnosticEmission.Address("JavaProgramTranslator", "input-program-translation", 0),
          missing.emissions().events().getFirst().address());
      assertEquals(List.of(), complete.diagnostics());
    }
    assertEquals(snapshot.sourceFiles().stream().map(SourceFile::sourceFileKey).toList(),
        recovered.aggregate().orElseThrow().roots().stream().map(node -> node.sourceFileKey()).toList());
  }

  @Test
  void givenNestedRangeFailuresAroundValidUnit_whenProjectionFailsThenSameProgramRetries_thenOrderedEventsAndClosedValuesRemain()
      throws IOException {
    // Arrange
    var snapshot = snapshot();
    var sources = new SnapshotSourceCatalog(snapshot);
    var policy = JavaTranslationPolicy.forProfile(AstProfile.JAVA_SOURCE_UNITS);
    var missingRanges = new AtomicBoolean(true);
    TranslatedProgram recovered;
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(CONFIGURATION), sources)) {
      var compilation = session.analyze(session.parse(), policy);
      var actual = compilation.sourceEvidence();
      var positions = new SourcePositions() {
        @Override
        public long getStartPosition(CompilationUnitTree unit, Tree tree) {
          return missingRanges.get() && tree instanceof ClassTree type && type.getSimpleName().contentEquals("Nested")
              ? Diagnostic.NOPOS : actual.rawStart(unit, tree);
        }

        @Override
        public long getEndPosition(CompilationUnitTree unit, Tree tree) {
          return actual.rawEnd(unit, tree);
        }
      };
      var translator = new JavaProgramTranslator(sources, compilation.semantics(), new JavaSourceEvidence(positions),
          compilation.treeInventory(), policy);

      // Act
      var failed = translator.translate(snapshot, compilation.units());
      missingRanges.set(false);
      var complete = translator.translate(snapshot, compilation.units());
      recovered = complete.value();

      // Assert
      assertTrue(failed.value().aggregate().isEmpty());
      assertTrue(failed.value().program().types().isEmpty());
      assertEquals(List.of("frontend.missing-source-position", "frontend.missing-source-position"),
          failed.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
      assertEquals(List.of(snapshot.sourceFiles().getFirst().sourceFileKey(), snapshot.sourceFiles().getLast().sourceFileKey()),
          failed.emissions().events().stream().map(event -> event.address().owner()).toList());
      assertTrue(failed.emissions().events().stream().allMatch(event -> event.address().producer().equals("JavaSourceEvidence")
          && event.address().ordinal() == 0 && event.slot() == -1 && event.contexts().isEmpty()));
      assertFalse(complete.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()), complete.diagnostics().toString());
    }
    assertEquals(snapshot.sourceFiles().stream().map(SourceFile::sourceFileKey).toList(),
        recovered.aggregate().orElseThrow().roots().stream().map(node -> node.sourceFileKey()).toList());
    assertEquals(2, recovered.program().types().stream().filter(type -> type.nesting().kind() == JavaTypeNesting.Kind.LOCAL).count());
  }

  private InputSnapshot snapshot() {
    return InputSnapshot.fromSources(directory, List.of(
        SourceFile.fromContent(Path.of("ABefore.java"), "class Before { void subject() { class Nested { int value() { return 1; } } } }"),
        SourceFile.fromContent(Path.of("BMiddle.java"), "class Middle { int subject() { return 2; } }"),
        SourceFile.fromContent(Path.of("CAfter.java"), "class After { void subject() { class Nested { int value() { return 3; } } } }")));
  }
}
