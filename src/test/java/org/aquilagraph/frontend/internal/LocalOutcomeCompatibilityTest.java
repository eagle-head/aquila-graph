package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue.Annotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalOutcomeCompatibilityTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final List<AstProfile> PROFILES = List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED,
      AstProfile.JAVA_SOURCE_INITIALIZATION, AstProfile.JAVA_SOURCE_NESTED, AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION,
      AstProfile.JAVA_SOURCE_DECLARATIONS, AstProfile.JAVA_SOURCE_UNITS);
  @TempDir Path directory;

  @Test
  void givenValidDuplicateAndAnnotatedDeclarations_whenEveryProfileProducesClosedValues_thenContractAndUnaffectedBytesRemainStable()
      throws IOException {
    // Arrange
    var sources = List.of(
        "class Sample { int value; int subject() { return 1; } }",
        "class Sample { void subject() {} void subject() {} }",
        "class Sample { int value; int value; }",
        "class Sample { class Nested { void subject() {} void subject() {} } }",
        "class Sample { void subject() { class Local { int value; int value; } } }",
        "class Sample { Object value = new Object() { void subject() {} void subject() {} }; }",
        "@interface Inner { int value(); } enum Choice { FIRST } @interface Mark { int number(); Choice choice(); Class<?> type(); Inner nested(); int[] array(); } @Mark(number=1+2, choice=Choice.FIRST, type=String.class, nested=@Inner(4), array={5,6}) class Sample {}",
        "@interface Mark { int value(); } @Mark(unknown=1) class Sample {}");
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var preservation = System.getProperty("frontend.preservation.directory");
    var root = preservation == null ? directory : Path.of(preservation).resolve("inputs").resolve("local-outcomes");
    for (var index = 0; index < sources.size(); index++) {
      var snapshot = InputSnapshot.fromSources(root.resolve("case-" + index),
          List.of(SourceFile.fromContent(Path.of("Sample.java"), sources.get(index))));
      for (var profile : PROFILES) {
        // Act
        var outcome = frontend.produce(snapshot, CONFIGURATION, profile);

        // Assert
        if (index == 0 || (index == 6 && PROFILES.indexOf(profile) >= 4)) {
          assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
        } else {
          assertNotEquals(AstCompletion.COMPLETE, outcome.completion());
          assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()));
          if (index == 6) {
            assertTrue(outcome.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-enum-constant-initialization")));
          }
        }
        if (index == 6) {
          var annotation = outcome.result().orElseThrow().aggregate().nodes().stream()
              .filter(node -> node.value() instanceof Annotation && node.children().size() == 5).findFirst().orElseThrow();
          assertEquals(5, annotation.children().size());
        }
        if (index == 5 && profile.equals(AstProfile.JAVA_SOURCE_INITIALIZATION)) {
          assertUnsupportedInitializer(outcome);
        }
        observeBaseline("declaration-" + index, profile, outcome);
      }
    }
  }

  private static void observeBaseline(String name, AstProfile profile, AstOutcome outcome) throws IOException {
    var preservation = System.getProperty("frontend.preservation.directory");
    if (preservation == null) {
      return;
    }
    var baseline = Path.of(preservation).resolve("baseline");
    var key = name + "-" + profile.version().replace('/', '-');
    var encoded = outcome.result().map(AstCodec::encode).orElseGet(() -> new byte[0]);
    var qualification = (outcome.profile() + "\n" + outcome.completion() + "\n" + outcome.sourceSubjectDigest() + "\n"
        + outcome.diagnostics() + "\n" + outcome.coverageQualification()).getBytes(StandardCharsets.UTF_8);
    if (Boolean.getBoolean("frontend.preservation.capture")) {
      Files.createDirectories(baseline);
      Files.write(baseline.resolve(key + ".ast"), encoded);
      Files.write(baseline.resolve(key + ".qualification"), qualification);
    } else if (name.equals("declaration-5") && profile.equals(AstProfile.JAVA_SOURCE_INITIALIZATION)
        && Files.size(baseline.resolve(key + ".ast")) == 0) {
      assertArrayEquals(new byte[0], Files.readAllBytes(baseline.resolve(key + ".ast")), key);
      var original = Files.readString(baseline.resolve(key + ".qualification"), StandardCharsets.UTF_8);
      assertTrue(original.startsWith(profile + "\nUNAVAILABLE\n"), key);
      assertTrue(original.contains("code=frontend.unavailable-source-structure"), key);
      assertTrue(original.contains("A typed-use decision must select an eligible direct child"), key);
      assertUnsupportedInitializer(outcome);
    } else {
      assertArrayEquals(Files.readAllBytes(baseline.resolve(key + ".ast")), encoded, key);
      assertArrayEquals(Files.readAllBytes(baseline.resolve(key + ".qualification")), qualification, key);
    }
  }

  private static void assertUnsupportedInitializer(AstOutcome outcome) {
    assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
    var result = outcome.result().orElseThrow();
    var field = result.aggregate().executionSyntax("type:Sample#field:value").orElseThrow();
    assertEquals(SyntaxKind.UNSUPPORTED, field.root().requiredChild(SyntaxRole.INITIALIZER).syntaxKind());
    assertTrue(field.evidence().uses().isEmpty());
    assertTrue(outcome.diagnostics().stream().anyMatch(value -> value.code().equals("compiler.err.already.defined")));
    assertTrue(outcome.diagnostics().stream().anyMatch(value -> value.code().equals("frontend.unsupported-syntax")));
    assertFalse(outcome.diagnostics().stream().anyMatch(value -> value.code().equals("frontend.unavailable-source-structure")));
    var encoded = AstCodec.encode(result);
    var decoded = AstCodec.decode(encoded, result.identity());
    assertEquals(result.aggregate(), decoded.aggregate());
    assertEquals(result.diagnostics(), decoded.diagnostics());
    assertArrayEquals(encoded, AstCodec.encode(decoded));
  }
}
