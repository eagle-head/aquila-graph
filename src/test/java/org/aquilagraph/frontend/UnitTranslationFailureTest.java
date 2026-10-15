package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
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
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.JavaTypeNesting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnitTranslationFailureTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  @TempDir Path directory;

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
          compilation.treeInventory(), JavaLanguageFeatureCoverage.fromConfiguration(CONFIGURATION), policy);

      // Act
      var missing = translator.translate(snapshot, List.of(units.getFirst(), units.getLast()));
      var complete = translator.translate(snapshot, units);
      recovered = complete.value();

      // Assert
      assertTrue(missing.value().aggregate().isEmpty());
      assertTrue(missing.value().program().types().isEmpty());
      assertEquals(List.of("frontend.missing-compilation-unit"), missing.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
      assertEquals(new DiagnosticEmission.Address("JavaProgramTranslator", "input-program-translation", 0),
          missing.diagnosticEmissions().getFirst().address());
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
          compilation.treeInventory(), JavaLanguageFeatureCoverage.fromConfiguration(CONFIGURATION), policy);

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
          failed.diagnosticEmissions().stream().map(event -> event.address().owner()).toList());
      assertTrue(failed.diagnosticEmissions().stream().allMatch(event -> event.address().producer().equals("JavaSourceEvidence")
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
