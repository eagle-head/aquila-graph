package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.JavaTypeNesting;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class ExecutableProjectionContextTest {
  private static final List<AstProfile> PROFILES = List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED,
      AstProfile.JAVA_SOURCE_INITIALIZATION, AstProfile.JAVA_SOURCE_NESTED, AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION,
      AstProfile.JAVA_SOURCE_DECLARATIONS, AstProfile.JAVA_SOURCE_UNITS);
  @TempDir Path directory;

  @Test
  void givenEveryExactProfile_whenMetadataAndRemainingPurposesAreSelected_thenDeferralAndUnscopedCollaborationFollowPolicy() {
    for (var index = 0; index < PROFILES.size(); index++) {
      // Arrange
      var profile = PROFILES.get(index);
      var policy = JavaTranslationPolicy.forProfile(profile);

      // Act
      var metadata = JavaExecutableProjectionContext.metadata(policy);

      // Assert
      assertSame(policy, metadata.policy());
      assertSame(profile, metadata.policy().profile());
      assertTrue(metadata.declarations().isEmpty());
      assertFalse(metadata.supportsNestedDeclarations());
      assertDoesNotThrow(metadata::requireMethodBody);
      if (index >= 3) {
        assertThrows(IllegalStateException.class, metadata::requireProjection);
        var remaining = JavaExecutableProjectionContext.remainingAttributedBody(policy);
        assertSame(policy, remaining.policy());
        assertTrue(remaining.declarations().isEmpty());
        assertFalse(remaining.supportsNestedDeclarations());
        assertDoesNotThrow(remaining::requireMethodBody);
        assertDoesNotThrow(remaining::requireProjection);
      } else {
        assertDoesNotThrow(metadata::requireProjection);
        assertThrows(IllegalArgumentException.class, () -> JavaExecutableProjectionContext.remainingAttributedBody(policy));
      }
    }
  }

  @Test
  void givenPurposeProfileBoundaries_whenContextsAreConstructed_thenUnsupportedOrIncompleteCollaborationRejectsBeforeEffects() {
    // Arrange
    var effects = new AtomicInteger();
    JavaExecutableProjectionContext.DeclarationProjection declarations = (tree, path, structuralPath) -> {
      effects.incrementAndGet();
      throw new AssertionError("Construction must not project a declaration");
    };

    var constructions = new ArrayList<Construction>();
    var unsupported = new ArrayList<Executable>();
    var missing = new ArrayList<Executable>();
    for (var index = 0; index < PROFILES.size(); index++) {
      var policy = JavaTranslationPolicy.forProfile(PROFILES.get(index));
      if (index >= 3) {
        constructions.add(new Construction(() -> JavaExecutableProjectionContext.canonicalMethod(policy, declarations), policy, true, true));
        constructions.add(new Construction(() -> JavaExecutableProjectionContext.memberInitialization(policy, declarations), policy, true, false));
        unsupported.add(() -> JavaExecutableProjectionContext.memberInitialization(policy));
        missing.add(() -> JavaExecutableProjectionContext.canonicalMethod(policy, null));
        missing.add(() -> JavaExecutableProjectionContext.memberInitialization(policy, null));
      } else {
        unsupported.add(() -> JavaExecutableProjectionContext.canonicalMethod(policy, declarations));
        unsupported.add(() -> JavaExecutableProjectionContext.memberInitialization(policy, declarations));
        if (index >= 2) {
          constructions.add(new Construction(() -> JavaExecutableProjectionContext.memberInitialization(policy), policy, false, false));
        } else {
          unsupported.add(() -> JavaExecutableProjectionContext.memberInitialization(policy));
        }
      }
      if (index >= 4) {
        constructions.add(new Construction(() -> JavaExecutableProjectionContext.enumInitialization(policy, declarations), policy, true, false));
        missing.add(() -> JavaExecutableProjectionContext.enumInitialization(policy, null));
      } else {
        unsupported.add(() -> JavaExecutableProjectionContext.enumInitialization(policy, declarations));
      }
    }
    missing.add(() -> JavaExecutableProjectionContext.metadata(null));
    missing.add(() -> JavaExecutableProjectionContext.remainingAttributedBody(null));
    missing.add(() -> JavaExecutableProjectionContext.memberInitialization(null));
    missing.add(() -> JavaExecutableProjectionContext.memberInitialization(null, declarations));
    missing.add(() -> JavaExecutableProjectionContext.canonicalMethod(null, declarations));
    missing.add(() -> JavaExecutableProjectionContext.enumInitialization(null, declarations));

    // Act
    var constructed = constructions.stream().map(input -> new Constructed(input, input.factory().get())).toList();
    var refusals = unsupported.stream().map(action -> assertThrows(IllegalArgumentException.class, action)).toList();
    var missingRequirements = missing.stream().map(action -> assertThrows(NullPointerException.class, action)).toList();

    // Assert
    for (var observation : constructed) {
      var context = observation.value();
      assertSame(observation.input().policy(), context.policy());
      assertEquals(observation.input().recursive(), context.supportsNestedDeclarations());
      if (observation.input().recursive()) {
        assertSame(declarations, context.declarations().orElseThrow());
      } else {
        assertTrue(context.declarations().isEmpty());
      }
      assertDoesNotThrow(context::requireProjection);
      if (observation.input().methodBody()) {
        assertDoesNotThrow(context::requireMethodBody);
      } else {
        assertThrows(IllegalArgumentException.class, context::requireMethodBody);
      }
    }
    assertFalse(refusals.isEmpty());
    assertFalse(missingRequirements.isEmpty());
    assertEquals(0, effects.get());
  }

  @Test
  void givenExecutableCollaborators_whenTheirConstructionContractsAreInspected_thenAllRetainedConfigurationIsFinal() {
    // Arrange
    var collaborators = List.of(JavaMethodBodyTranslator.class, JavaExecutableSyntaxProjector.class, JavaExecutableSyntaxProfile.class);

    // Act
    var fields = collaborators.stream().flatMap(type -> Arrays.stream(type.getDeclaredFields()))
        .filter(field -> !Modifier.isStatic(field.getModifiers())).toList();

    // Assert
    assertTrue(fields.stream().allMatch(field -> Modifier.isFinal(field.getModifiers())));
    for (var type : collaborators) {
      assertTrue(Arrays.stream(type.getDeclaredFields()).anyMatch(field -> field.getType() == JavaExecutableProjectionContext.class));
      assertTrue(Arrays.stream(type.getDeclaredConstructors()).allMatch(constructor ->
          Arrays.asList(constructor.getParameterTypes()).contains(JavaExecutableProjectionContext.class)));
    }
    assertTrue(Arrays.stream(JavaExecutableProjectionContext.class.getDeclaredFields())
        .filter(field -> !Modifier.isStatic(field.getModifiers())).allMatch(field -> Modifier.isFinal(field.getModifiers())));
    assertTrue(Arrays.stream(JavaExecutableProjectionContext.class.getDeclaredConstructors())
        .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())));
  }

  @Test
  void givenRecursiveMethodAcrossExactNestedProfiles_whenRequestsAlternateWithBounded_thenClosedCanonicalOwnersAndBodyOriginsRemain()
      throws IOException {
    // Arrange
    var source = """
        class Example {
          Object subject() {
            class Local { Object nested() { return new Object() { int leaf() { return 7; } }; } }
            return new Local().nested();
          }
        }
        """;
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    for (var profile : PROFILES.stream().filter(AstProfile::supportsNestedDeclarations).toList()) {
      // Act
      var outcome = frontend.produce(snapshot, configuration, profile);
      var bounded = frontend.produce(snapshot, configuration, AstProfile.JAVA_SOURCE_BOUNDED);
      var repeated = frontend.produce(snapshot, configuration, profile);
      var result = outcome.result().orElseThrow();

      // Assert
      assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
      assertNotEquals(AstCompletion.COMPLETE, bounded.completion());
      assertEquals(profile, result.profile());
      assertEquals("javac-public/8", result.identity().semanticProductionRevision());
      var local = result.program().types().stream().filter(type -> type.nesting().kind() == JavaTypeNesting.Kind.LOCAL)
          .findFirst().orElseThrow();
      var nested = local.methods().stream().filter(method -> method.name().equals("nested")).findFirst().orElseThrow();
      var anonymous = result.program().types().stream().filter(type -> type.nesting().kind() == JavaTypeNesting.Kind.ANONYMOUS)
          .findFirst().orElseThrow();
      var leaf = anonymous.methods().stream().filter(method -> method.name().equals("leaf")).findFirst().orElseThrow();
      assertEquals(Optional.of("type:Example#subject()"), local.nesting().lexicalOwnerKey());
      assertEquals(Optional.of(nested.methodKey()), anonymous.nesting().lexicalOwnerKey());
      for (var method : List.of(nested, leaf)) {
        var body = result.body(method.methodKey()).orElseThrow();
        assertSame(body.root(), result.aggregate().node(body.root().nodeId()).orElseThrow());
        assertEquals(Optional.of(MethodBodyOrigin.SOURCE_AST), result.aggregate().methodBodyOrigin(method.methodKey()));
      }
      assertArrayEquals(AstCodec.encode(result), AstCodec.encode(repeated.result().orElseThrow()));
      var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
      assertEquals(result.program(), decoded.program());
      assertArrayEquals(AstCodec.encode(result), AstCodec.encode(decoded));
    }
  }

  private record Construction(Supplier<JavaExecutableProjectionContext> factory, JavaTranslationPolicy policy,
      boolean recursive, boolean methodBody) {}

  private record Constructed(Construction input, JavaExecutableProjectionContext value) {}
}
