package org.aquilagraph.source.internal;

import org.aquilagraph.source.admission.AstProductionAdmission;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.frontend.internal.JavaCoverageTestSupport;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.CanonicalValueReader;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.DependencyKind;
import org.aquilagraph.identity.api.DependencySemantics;
import org.aquilagraph.identity.api.DependencySnapshot;
import org.aquilagraph.identity.api.EnvironmentIdentityScheme;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.ModuleCandidate;
import org.aquilagraph.identity.api.ModuleNameOrigin;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.SourceQualificationReason;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnvironmentQualificationTest {
  @TempDir Path directory;

  @Test
  void givenCopiedUnavailableSourceWithDifferentModuleFacts_whenAdmitted_thenCapturedFactsQualifyTheUnavailableSubject() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Example.java"), "class Example { int value() { return 1; } }")));
    var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_MODULE_SERVICES).result().orElseThrow();
    var firstEnvironment = environment(original.environment(), "alpha");
    var secondEnvironment = environment(original.environment(), "beta");
    var originalInput = new SyntaxAnalysisInput(snapshot, original.environment(), original.program(), Optional.of(original.aggregate()),
        original.diagnostics(), original.coverage());
    var unavailable = JavaCoverageTestSupport.withFacts(originalInput, new JavaProgram(List.of()), Optional.empty(),
        original.coverage().validationEvidence().diagnosticEmissions());
    var firstInput = new SyntaxAnalysisInput(snapshot, firstEnvironment, unavailable.program(), Optional.empty(), unavailable.diagnostics(), unavailable.languageFeatureCoverageResult());
    var secondInput = new SyntaxAnalysisInput(snapshot, secondEnvironment, unavailable.program(), Optional.empty(), unavailable.diagnostics(), unavailable.languageFeatureCoverageResult());

    // Act
    var first = AstProductionAdmission.outcome(firstInput, original.profile(), original.identity().semanticProductionRevision());
    var second = AstProductionAdmission.outcome(secondInput, original.profile(), original.identity().semanticProductionRevision());
    var empty = new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory, List.of()),
        firstEnvironment.compilerConfiguration(), original.profile());

    // Assert
    assertEquals(AstCompletion.UNAVAILABLE, first.completion());
    assertEquals(AstCompletion.UNAVAILABLE, second.completion());
    assertTrue(first.result().isEmpty());
    assertEquals(Optional.of(original.identity().semanticProductionRevision()), first.qualification().semanticRevision());
    assertTrue(first.qualification().reasons().stream().anyMatch(reason -> reason.category() == SourceQualificationReason.Category.UNAVAILABLE_ROOTS
        && reason.subject() == SourceQualificationReason.Capture.INSTANCE && reason.origins().isEmpty()));
    assertEquals(Optional.of(firstEnvironment), first.capturedEnvironment());
    assertEquals(Optional.of(secondEnvironment), second.capturedEnvironment());
    assertNotEquals(first.sourceSubjectDigest(), second.sourceSubjectDigest());
    assertEquals(AstCompletion.UNAVAILABLE, empty.completion());
    assertTrue(empty.capturedEnvironment().isEmpty());
    assertTrue(empty.result().isEmpty());
  }

  @Test
  void givenUnsupportedSchemesAndOlderEnvelopes_whenValuesAreEncodedOrDecoded_thenNoSemanticFactsCanBeSilentlyDropped() {
    // Arrange
    var writer = new CanonicalValueWriter("java-source/21");
    writer.text("environment-identity-scheme");
    writer.text("UNSUPPORTED");
    var semantics = new DependencySemantics(DependencySemantics.State.MODULES,
        List.of(new ModuleCandidate(".", "alpha", Optional.empty(), ModuleNameOrigin.FILENAME)));

    // Act
    var unknown = assertThrows(IllegalArgumentException.class,
        () -> new RetainedValueCodec(false, "java-source/21").read(new CanonicalValueReader(writer.toByteArray(), "java-source/21")));

    // Assert
    assertTrue(unknown.getMessage().contains("Unknown retained enum value"));
    for (var version = 2; version <= 20; version++) {
      var format = "java-source/" + version;
      assertThrows(IllegalArgumentException.class,
          () -> new RetainedValueCodec(false, format).write(new CanonicalValueWriter(format), semantics));
    }
  }

  private AnalysisEnvironmentSnapshot environment(AnalysisEnvironmentSnapshot original, String name) {
    var path = directory.resolve("dependency.jar");
    var dependency = new DependencySnapshot(path, DependencyKind.REGULAR_FILE, 7, 1, "supplied-physical-label",
        new DependencySemantics(DependencySemantics.State.MODULES,
            List.of(new ModuleCandidate(".", name, Optional.empty(), ModuleNameOrigin.FILENAME))));
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(path), List.of());
    return new AnalysisEnvironmentSnapshot(configuration, original.compilerImplementation(), original.compilerVendor(), original.compilerVersion(),
        original.analysisOptions(), List.of(), List.of(dependency), EnvironmentIdentityScheme.MODULE_SEMANTICS);
  }
}
