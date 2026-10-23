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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutableEvidencePreservationTest {
  private static final List<AstProfile> PROFILES = List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED,
      AstProfile.JAVA_SOURCE_INITIALIZATION, AstProfile.JAVA_SOURCE_NESTED, AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION,
      AstProfile.JAVA_SOURCE_DECLARATIONS, AstProfile.JAVA_SOURCE_UNITS);
  @TempDir Path directory;

  @Test
  void givenAnonymousFieldBeforeNestedCapability_whenProduced_thenUnsupportedInitializerHasNoIneligibleTypedUse() throws IOException {
    for (var staticField : List.of(false, true)) {
      for (var duplicateMethod : List.of(false, true)) {
        // Arrange
        var nested = "String nested() { return \"a\" + 2; }";
        var source = "class Example { " + (staticField ? "static " : "") + "Object field = new Object() { " + nested
            + (duplicateMethod ? nested : "") + " }; int sibling() { return 7; } }";
        var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));

        // Act
        var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
            AstProfile.JAVA_SOURCE_INITIALIZATION);

        // Assert
        assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
        var field = outcome.result().orElseThrow().aggregate().executionSyntax("type:Example#field:field").orElseThrow();
        assertEquals(SyntaxKind.UNSUPPORTED, field.root().requiredChild(SyntaxRole.INITIALIZER).syntaxKind());
        assertEquals(0, field.evidence().uses().size());
        assertTrue(outcome.diagnostics().stream().anyMatch(value -> value.code().equals("frontend.unsupported-syntax")));
        assertEquals(duplicateMethod,
            outcome.diagnostics().stream().anyMatch(value -> value.code().equals("compiler.err.already.defined")));
        assertFalse(outcome.diagnostics().stream().anyMatch(value -> value.code().equals("frontend.unavailable-source-structure")));
        assertTrue(outcome.result().orElseThrow().program().types().stream().flatMap(type -> type.methods().stream())
            .anyMatch(method -> method.name().equals("sibling")));
        var result = outcome.result().orElseThrow();
        var encoded = AstCodec.encode(result);
        var decoded = AstCodec.decode(encoded, result.identity());
        assertEquals(result.aggregate(), decoded.aggregate());
        assertArrayEquals(encoded, AstCodec.encode(decoded));
      }
    }
  }

  @Test
  void givenAnonymousArguments_whenAllProfilesProduceAndDecode_thenOnlyEligibleChildrenHaveTypedUses() throws IOException {
    // Arrange
    var sources = List.of(
        "class Example { static void accept(Object value) {} void subject() { accept(new Object() {}); } }",
        "class Box { <T> Box(T value) {} } class Example { Object subject() { return new Box(new Object() {}); } }",
        "class Example { static Object accept(Object value) { return value; } Object field = accept(new Object() {}); }");
    var frontend = new JavaFrontend(new JavaSourceLoader());
    for (var index = 0; index < sources.size(); index++) {
      var snapshot = InputSnapshot.fromSources(directory.resolve("arguments-" + index),
          List.of(SourceFile.fromContent(Path.of("Example.java"), sources.get(index))));
      for (var profile : PROFILES) {
        // Act
        var outcome = frontend.produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile);

        // Assert
        assertEquals(profile.supportsNestedDeclarations() ? AstCompletion.COMPLETE : AstCompletion.INCOMPLETE, outcome.completion(),
            profile + ": " + outcome.diagnostics());
        assertFalse(outcome.diagnostics().stream().anyMatch(value -> value.code().startsWith("compiler.err.")));
        assertFalse(outcome.diagnostics().stream().anyMatch(value -> value.code().equals("frontend.unavailable-source-structure")));
        var result = outcome.result().orElseThrow();
        if (profile.equals(AstProfile.JAVA_SOURCE_INITIALIZATION)) {
          var consumers = result.aggregate().nodes().stream()
              .filter(node -> node.children(SyntaxRole.ARGUMENT).stream().anyMatch(child -> child.syntaxKind() == SyntaxKind.UNSUPPORTED))
              .toList();
          assertFalse(consumers.isEmpty());
          var uses = result.aggregate().executionScopes().stream()
              .flatMap(scope -> result.aggregate().executionSyntax(scope.key()).orElseThrow().evidence().uses().stream()).toList();
          var invocations = result.aggregate().executionScopes().stream()
              .flatMap(scope -> result.aggregate().executionSyntax(scope.key()).orElseThrow().evidence().invocations().stream()).toList();
          for (var consumer : consumers) {
            assertTrue(invocations.stream().anyMatch(invocation -> invocation.consumerNodeId().equals(consumer.nodeId())
                && invocation.applicability().knownValue().isPresent()));
            for (var child : consumer.children()) {
              if (child.role() == SyntaxRole.ARGUMENT && child.node().syntaxKind() == SyntaxKind.UNSUPPORTED) {
                assertTrue(uses.stream().noneMatch(use -> use.consumerNodeId().equals(consumer.nodeId())
                    && use.role() == SyntaxRole.ARGUMENT && use.position() == child.position()));
              }
            }
          }
        }
        var encoded = AstCodec.encode(result);
        var decoded = AstCodec.decode(encoded, result.identity());
        assertEquals(result.aggregate(), decoded.aggregate());
        assertArrayEquals(encoded, AstCodec.encode(decoded));
      }
    }
  }

  @Test
  void givenMethodAndInitializationStringOperands_whenEveryProfileProducesClosedValues_thenOwnFactsAndCanonicalBytesRemainStable()
      throws IOException {
    // Arrange
    var sources = List.of(
        "class Example { static String field = \"f\" + 2; static { field = \"s\" + 3; } { field = \"i\" + 4; } String subject(int value) { return \"m\" + value; } }",
        "enum Example { FIRST(\"a\" + 1, \"b\" + 2) { String nested() { return \"n\" + 3; } }; Example(String first, String second) {} }",
        "class Example { String subject() { class Local { String nested() { return \"c\" + 1; } } return new Local().nested(); } Object another() { return new Object() { String nested() { return \"a\" + 2; } }; } }",
        "class Example { String subject() { return \"m\" + missing; } String field = \"f\" + absent; static { String local = \"s\" + unknown; } { String local = \"i\" + unresolved; } }",
        "enum Example { FIRST(\"a\" + missing, \"b\" + absent); Example(String first, String second) {} }");
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var preservation = System.getProperty("frontend.preservation.directory");
    var root = preservation == null ? directory : Path.of(preservation).resolve("inputs").resolve("executables");
    for (var index = 0; index < sources.size(); index++) {
      var snapshot = InputSnapshot.fromSources(root.resolve("case-" + index),
          List.of(SourceFile.fromContent(Path.of("Example.java"), sources.get(index))));
      for (var profile : PROFILES) {
        // Act
        var outcome = frontend.produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile);

        // Assert
        if (index < 3 && PROFILES.indexOf(profile) >= 4) {
          assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
          var result = outcome.result().orElseThrow();
          var facts = result.aggregate().executionScopes().stream()
              .flatMap(scope -> result.aggregate().executionSyntax(scope.key()).orElseThrow().evidence().stringExpressions().stream()).toList();
          assertFalse(facts.isEmpty());
          assertTrue(facts.stream().allMatch(fact -> fact.leftIsString().knownValue().orElseThrow()));
          assertTrue(facts.stream().allMatch(fact -> !fact.rightIsString().knownValue().orElseThrow()));
          assertEquals(index == 0 ? 4 : index == 1 ? 3 : 2, facts.size());
        } else if (index >= 3) {
          assertNotEquals(AstCompletion.COMPLETE, outcome.completion());
          assertTrue(outcome.diagnostics().stream().anyMatch(value -> value.isError()));
        }
        observeBaseline(index, profile, outcome);
      }
    }
  }

  private static void observeBaseline(int index, AstProfile profile, AstOutcome outcome) throws IOException {
    var preservation = System.getProperty("frontend.preservation.directory");
    if (preservation == null) {
      return;
    }
    var baseline = Path.of(preservation).resolve("baseline");
    var key = "executable-" + index + "-" + profile.version().replace('/', '-');
    var encoded = outcome.result().map(AstCodec::encode).orElseGet(() -> new byte[0]);
    var qualification = (outcome.profile() + "\n" + outcome.completion() + "\n" + outcome.sourceSubjectDigest() + "\n"
        + outcome.diagnostics() + "\n" + outcome.coverageQualification()).getBytes(StandardCharsets.UTF_8);
    if (Boolean.getBoolean("frontend.preservation.capture")) {
      Files.createDirectories(baseline);
      Files.write(baseline.resolve(key + ".ast"), encoded);
      Files.write(baseline.resolve(key + ".qualification"), qualification);
    } else {
      assertArrayEquals(Files.readAllBytes(baseline.resolve(key + ".ast")), encoded, key);
      assertArrayEquals(Files.readAllBytes(baseline.resolve(key + ".qualification")), qualification, key);
    }
  }
}
