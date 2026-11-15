package org.aquilagraph.source.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.source.retention.AstCodec;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.internal.AstValueSchema.Fields;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class NonPreviewAdmissionTest {
  private static final List<AstProfile> PROFILES = List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED);
  private static final String PREVIEW_OPTION = "--enable-preview";
  private static final String ORDINARY_SOURCE = "class LanguageMode { int subject(int x) { return switch (x) { default -> 7; }; } }";
  private static final String PREVIEW_SOURCE = "class LanguageMode { int subject(boolean x) { return switch (x) { case true -> 1; default -> 0; }; } }";
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenPreviewCompilerOption_whenProducingEitherProfile_thenLanguageModeIsRejected() {
    return PROFILES.stream().flatMap(profile -> Stream.of(ORDINARY_SOURCE, PREVIEW_SOURCE).map(source ->
        DynamicTest.dynamicTest(profile.version() + ": " + source, () -> {
          // Arrange
          var snapshot = snapshot(source);
          var producer = new JavaFrontend(new JavaSourceLoader());

          // Act
          var failure = assertThrows(IllegalArgumentException.class, () -> {
            var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of(PREVIEW_OPTION));
            var outcome = producer.produce(snapshot, configuration, profile);
            fail("Non-preview profile admitted preview mode with completion " + outcome.completion());
          });

          // Assert
          assertTrue(failure.getMessage().contains(PREVIEW_OPTION), failure.toString());
        })));
  }

  @TestFactory
  Stream<DynamicTest> givenPreviewOnlySyntaxWithoutTheOption_whenProducingEitherProfile_thenCompilerErrorPreventsCompletion() {
    return PROFILES.stream().map(profile -> DynamicTest.dynamicTest(profile.version(), () -> {
      // Act
      var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot(PREVIEW_SOURCE),
          CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile);

      // Assert
      assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
      assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError() && diagnostic.code().contains("preview")),
          outcome.diagnostics().toString());
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenOrdinaryNonPreviewSyntax_whenProducingAndDecodingEitherProfile_thenSupportedValuesRemainComplete() {
    return PROFILES.stream().map(profile -> DynamicTest.dynamicTest(profile.version(), () -> {
      // Arrange
      var original = fixture(profile);

      // Act
      var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

      // Assert
      assertEquals(AstCompletion.COMPLETE, decoded.completion());
      assertEquals(original.profile(), decoded.profile());
      assertEquals(original.aggregate(), decoded.aggregate());
      assertEquals(original.coverage(), decoded.coverage());
      assertTrue(decoded.diagnostics().isEmpty(), decoded.diagnostics().toString());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenPreviewEffectiveOptionsInCopiedInput_whenAdmittingEitherProfile_thenEnvironmentCannotBypassConfiguration() {
    return PROFILES.stream().map(profile -> DynamicTest.dynamicTest(profile.version(), () -> {
      // Arrange
      var original = fixture(profile);
      var environment = original.environment();

      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> {
        var changed = new AnalysisEnvironmentSnapshot(environment.compilerConfiguration(), environment.compilerImplementation(),
            environment.compilerVendor(), environment.compilerVersion(), withPreview(environment.analysisOptions()),
            environment.classPath(), environment.modulePath());
        var input = new SyntaxAnalysisInput(original.snapshot(), changed, original.program(), Optional.of(original.aggregate()),
            original.diagnostics(), original.coverage());
        var admitted = AstResultAdmission.admit(input, profile, original.identity().semanticProductionRevision());
        fail("Copied non-preview input admitted preview mode with completion " + admitted.completion());
      });

      // Assert
      assertTrue(failure.getMessage().contains(PREVIEW_OPTION), failure.toString());
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenPreviewOptionsInAFullStoredPayload_whenDecodingEitherProfile_thenLanguageModeRejectionPrecedesIdentityComparison() {
    return PROFILES.stream().flatMap(profile -> Stream.of("configuration", "effective").map(owner ->
        DynamicTest.dynamicTest(profile.version() + ": " + owner, () -> {
          // Arrange
          var original = fixture(profile);
          var unchanged = payload(original, "");
          assertArrayEquals(AstCodec.encode(original), unchanged);

          // Act
          var failure = assertThrows(AstContractException.class, () -> AstCodec.decode(payload(original, owner), original.identity()));
          var decoded = AstCodec.decode(unchanged, original.identity());

          // Assert
          Throwable cause = failure;
          while (cause.getCause() != null) {
            cause = cause.getCause();
          }
          assertInstanceOf(IllegalArgumentException.class, cause);
          assertTrue(cause.getMessage().contains(PREVIEW_OPTION), cause.toString());
          assertEquals(AstCompletion.COMPLETE, decoded.completion());
          assertArrayEquals(unchanged, AstCodec.encode(decoded));
        })));
  }

  private AstResult fixture(AstProfile profile) throws IOException {
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot(ORDINARY_SOURCE),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile);
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    return outcome.result().orElseThrow();
  }

  private InputSnapshot snapshot(String source) {
    return InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("LanguageMode.java")), source)));
  }

  private static List<String> withPreview(List<String> options) {
    var changed = new ArrayList<>(options);
    changed.add(PREVIEW_OPTION);
    return List.copyOf(changed);
  }

  private static byte[] payload(AstResult original, String changedOwner) {
    var writer = new CanonicalValueWriter(original.identity().sourceModelVersion());
    writer.text(original.profile().version());
    writer.text(original.identity().semanticProductionRevision());
    writer.text(original.completion().name());
    var codec = new RetainedValueCodec(false);
    codec.write(writer, original.identity());
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(original.aggregate()),
        original.diagnostics(), original.coverage());
    writeRecord(writer, codec, input, changedOwner);
    return writer.toByteArray();
  }

  private static void writeRecord(CanonicalValueWriter writer, RetainedValueCodec codec, Object value, String changedOwner) {
    var shape = (Fields) AstValueSchema.shape(value);
    var components = shape.components(value, false);
    writer.text(shape.tag());
    writer.count(components.size());
    for (var fieldValue : components) {
      var field = fieldValue.name();
      var component = fieldValue.value();
      writer.text(field);
      if (component instanceof AnalysisEnvironmentSnapshot environment) {
        writeRecord(writer, codec, environment, changedOwner);
      } else if (component instanceof CompilerConfiguration configuration) {
        writeRecord(writer, codec, configuration, changedOwner);
      } else if (value instanceof CompilerConfiguration configuration && "options".equals(field) && "configuration".equals(changedOwner)) {
        codec.write(writer, withPreview(configuration.options()));
      } else if (value instanceof AnalysisEnvironmentSnapshot environment && "analysisOptions".equals(field) && "effective".equals(changedOwner)) {
        codec.write(writer, withPreview(environment.analysisOptions()));
      } else {
        codec.write(writer, component);
      }
    }
  }
}
