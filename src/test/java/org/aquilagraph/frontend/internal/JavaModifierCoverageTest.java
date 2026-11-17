package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.CoveragePropertyValue.Item;
import org.aquilagraph.source.api.CoveragePropertyValue.Provenance;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.FeatureContextOutcome.Mode;
import org.aquilagraph.source.api.JavaTypeModifier;
import org.aquilagraph.source.api.SyntaxValue.TypeDeclaration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaModifierCoverageTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final List<AstProfile> PROFILES = List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED,
      AstProfile.JAVA_SOURCE_INITIALIZATION, AstProfile.JAVA_SOURCE_NESTED, AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION,
      AstProfile.JAVA_SOURCE_DECLARATIONS, AstProfile.JAVA_SOURCE_UNITS);
  @TempDir Path directory;

  @Test
  void givenNonSealedClassAndInterfaceWithExplicitOrInferredPermits_whenProduced_thenCanonicalModifiersQualifyCompletely() throws IOException {
    // Arrange
    for (var permits : List.of(" permits Open", "")) {
      var source = "sealed class Base" + permits + " {} non-sealed abstract class Open extends Base {} "
          + "sealed interface Contract" + (permits.isEmpty() ? "" : " permits Extension")
          + " {} non-sealed interface Extension extends Contract {}";
      var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Hierarchy.java")), source)));
      for (var profile : PROFILES) {
        // Act
        var outcome = new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(snapshot, CONFIGURATION, profile));

        // Assert
        assertEquals(AstCompletion.COMPLETE, outcome.completion(), profile + ": " + outcome.diagnostics());
        assertTrue(outcome.diagnostics().isEmpty(), outcome.diagnostics().toString());
        var result = outcome.result().orElseThrow();
        var declarations = result.aggregate().nodes().stream().map(node -> node.value())
            .filter(TypeDeclaration.class::isInstance).map(TypeDeclaration.class::cast).toList();
        var open = declarations.stream().filter(value -> value.name().equals("Open")).findFirst().orElseThrow();
        var extension = declarations.stream().filter(value -> value.name().equals("Extension")).findFirst().orElseThrow();
        assertEquals(Set.of(JavaTypeModifier.ABSTRACT, JavaTypeModifier.NON_SEALED), open.writtenModifiers());
        assertEquals(open.writtenModifiers(), open.effectiveModifiers());
        assertEquals(Set.of(JavaTypeModifier.NON_SEALED), extension.writtenModifiers());
        assertEquals(Set.of(JavaTypeModifier.ABSTRACT, JavaTypeModifier.NON_SEALED), extension.effectiveModifiers());
        var modifiers = result.coverage().validationEvidence().outcomes().stream()
            .filter(value -> value.address().role().equals("TypeElement::Element#getModifiers()"))
            .flatMap(value -> value.encounteredValue().stream()).flatMap(value -> value.items().stream())
            .filter(item -> item.value().equals("non-sealed")).toList();
        assertEquals(2, modifiers.size());
        for (var item : modifiers) {
          assertEquals("non-sealed", item.identity());
          assertEquals(Provenance.COMPILER_DERIVED, item.provenance());
        }
        var kinds = result.coverage().validationEvidence().outcomes().stream()
            .filter(value -> value.address().role().equals("TypeElement::Element#getKind()"))
            .flatMap(value -> value.encounteredValue().stream()).flatMap(value -> value.items().stream()).toList();
        assertEquals(List.of("CLASS", "CLASS", "INTERFACE", "INTERFACE"), kinds.stream().map(Item::identity).toList());
        assertEquals(kinds.stream().map(Item::identity).toList(), kinds.stream().map(Item::value).toList());
        assertEquals("javac-public/15", result.identity().semanticProductionRevision());
        var encoded = AstCodec.encode(result);
        var decoded = AstCodec.decode(encoded, result.identity());
        assertEquals(result.program(), decoded.program());
        assertEquals(result.coverage(), decoded.coverage());
        assertArrayEquals(encoded, AstCodec.encode(decoded));
      }
    }
  }

  @Test
  void givenOrdinaryAndImplicitModifiers_whenProduced_thenWrittenAndEffectiveFlagsRemainDistinct() throws IOException {
    // Arrange
    var source = "abstract class Base { protected static final class Nested {} } final class Leaf {} "
        + "strictfp class Floating {} interface Contract { int value(); }";
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Controls.java")), source)));

    // Act
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(snapshot, CONFIGURATION, AstProfile.JAVA_SOURCE_UNITS));

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    assertFalse(outcome.diagnostics().stream().anyMatch(value -> value.severity() == DiagnosticSeverity.ERROR));
    assertEquals(List.of("compiler.warn.strictfp"), outcome.diagnostics().stream().map(value -> value.code()).toList());
    var result = outcome.result().orElseThrow();
    var declarations = result.aggregate().nodes().stream().map(node -> node.value())
        .filter(TypeDeclaration.class::isInstance).map(TypeDeclaration.class::cast).toList();
    assertEquals(Set.of(JavaTypeModifier.ABSTRACT), declarations.stream().filter(value -> value.name().equals("Base")).findFirst().orElseThrow().writtenModifiers());
    assertEquals(Set.of(JavaTypeModifier.PROTECTED, JavaTypeModifier.STATIC, JavaTypeModifier.FINAL),
        declarations.stream().filter(value -> value.name().equals("Nested")).findFirst().orElseThrow().writtenModifiers());
    assertEquals(Set.of(JavaTypeModifier.FINAL), declarations.stream().filter(value -> value.name().equals("Leaf")).findFirst().orElseThrow().writtenModifiers());
    var floating = declarations.stream().filter(value -> value.name().equals("Floating")).findFirst().orElseThrow();
    assertEquals(Set.of(JavaTypeModifier.STRICTFP), floating.writtenModifiers());
    assertTrue(declarations.stream().filter(value -> value.name().equals("Contract")).findFirst().orElseThrow().writtenModifiers().isEmpty());
    var semanticModifiers = result.coverage().validationEvidence().outcomes().stream()
        .filter(value -> value.address().role().equals("TypeElement::Element#getModifiers()"))
        .filter(value -> !value.encounteredValue().orElseThrow().items().isEmpty()).toList();
    assertFalse(semanticModifiers.isEmpty());
    assertTrue(semanticModifiers.stream().allMatch(value -> value.support().mode() == Mode.FACTS));
  }

  @Test
  void givenCompleteNonSealedSourceWithAnUnsupportedBody_whenDirectAndApplicationCfgRun_thenSourceCompletionDoesNotAuthorizePublication()
      throws IOException {
    // Arrange
    var source = "import java.util.function.IntSupplier; sealed class Base permits Open {} "
        + "non-sealed class Open extends Base { int supported() { return 7; } IntSupplier subject() { return () -> 9; } }";
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Hierarchy.java")), source)));
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var outcome = frontend.produce(new AstProductionRequest(snapshot, CONFIGURATION, AstProfile.JAVA_SOURCE_UNITS));
    var result = outcome.result().orElseThrow();
    var selection = new CfgRequest(result.identity(), List.of("type:Open#supported()", "type:Open#subject()"), CfgProfile.JAVA_BOUNDED);
    var deriver = CfgDerivers.standard();
    var runner = AnalysisCapabilities.existingSourceDerivation(deriver);

    // Act
    var direct = deriver.derive(result, selection);
    var coordinated = runner.deriveExisting(outcome, selection);

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.UNSUPPORTED), direct.methods().stream().map(value -> value.state()).toList());
    assertEquals(direct, coordinated.representation().cfg().orElseThrow());
    assertSame(outcome, coordinated.representation().ast());
    assertFalse(coordinated.isPublishable());
  }

  @Test
  void givenNonSealedDeclarationWithoutSealedParent_whenProduced_thenActualCompilerErrorsPreventCompleteQualification() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory),
        List.of(SourceFile.fromContent(capturedPath(Path.of("Invalid.java")), "non-sealed class Invalid {}")));

    // Act
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(snapshot, CONFIGURATION, AstProfile.JAVA_SOURCE_UNITS));

    // Assert
    assertNotEquals(AstCompletion.COMPLETE, outcome.completion());
    assertTrue(outcome.diagnostics().stream().anyMatch(value -> value.severity() == DiagnosticSeverity.ERROR
        && value.code().startsWith("compiler.err.")), outcome.diagnostics().toString());
  }
}
