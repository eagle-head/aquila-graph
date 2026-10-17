package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.ToolProvider;
import org.aquilagraph.frontend.JavaAnnotationProjector.DeclarationSite;
import org.aquilagraph.frontend.JavaUnitTranslator.ProjectionDecision;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.DiagnosticEmission.Address;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxValue.Annotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalProjectionOutcomeTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final JavaTranslationPolicy POLICY = JavaTranslationPolicy.forProfile(AstProfile.JAVA_SOURCE_UNITS);
  @TempDir Path directory;

  @Test
  void givenUnavailableLocalRecordMetadata_whenOuterDeclarationsFinish_thenAvailableMetadataSurvivesWithExplicitIneligibility()
      throws IOException {
    // Arrange
    withCompilation("class Sample { void subject() { record Local(int value) {} } }", fixture -> {
      var compilation = fixture.compilation();
      var unit = compilation.units().getFirst();
      var outer = (ClassTree) unit.getTypeDecls().getFirst();
      var method = outer.getMembers().stream().filter(MethodTree.class::isInstance).map(MethodTree.class::cast)
          .filter(value -> value.getName().contentEquals("subject")).findFirst().orElseThrow();
      var local = (ClassTree) method.getBody().getStatements().getFirst();
      var owner = (TypeElement) compilation.semantics().element(TreePath.getPath(unit, local));
      var component = compilation.semantics().sourceTree(owner.getRecordComponents().getFirst());
      var base = compilation.sourceEvidence();
      var evidence = new JavaSourceEvidence(new SourcePositions() {
        @Override
        public long getStartPosition(CompilationUnitTree selected, Tree tree) {
          return tree == component ? Diagnostic.NOPOS : base.rawStart(selected, tree);
        }

        @Override
        public long getEndPosition(CompilationUnitTree selected, Tree tree) {
          return base.rawEnd(selected, tree);
        }
      });
      var coordinator = new JavaUnitTranslationFactory(compilation.semantics(), evidence, compilation.treeInventory(), POLICY)
          .create(unit, fixture.source());

      // Act
      var declarations = coordinator.translateDeclarations();

      // Assert
      assertTrue(declarations.value().complete(), declarations.diagnostics().toString());
      assertEquals(List.of("Sample"), declarations.value().types().stream().map(value -> value.qualifiedName()).toList());
      assertEquals(ProjectionEligibility.INELIGIBLE, declarations.value().eligibility());
      assertTrue(declarations.diagnostics().stream().anyMatch(value -> value.code().equals("frontend.unavailable-record-component")));
      assertThrows(IllegalStateException.class, () -> coordinator.decideProjection(ProjectionDecision.ALLOW));
    });
  }

  @Test
  void givenCompletedDuplicateOrEmptyDeclarations_whenUnitPermissionIsDecided_thenEligibilityIsIndependentOfPhaseCompletion()
      throws IOException {
    for (var content : List.of("class Sample { void subject() {} void subject() {} }", "class Sample { int value; int value; }",
        "class Sample { class Nested { int value; int value; } }",
        "class Sample { void subject() { class Local { int value; int value; } } }",
        "class Sample { Object value = new Object() { void subject() {} void subject() {} }; }", "")) {
      // Arrange
      withCompilation(content, fixture -> {
        var unit = fixture.compilation().units().getFirst();
        var coordinator = fixture.factory().create(unit, fixture.source());

        // Act
        var declarations = coordinator.translateDeclarations();

        // Assert
        assertTrue(declarations.value().complete(), declarations.diagnostics().toString());
        if (content.isEmpty()) {
          assertEquals(ProjectionEligibility.ELIGIBLE, declarations.value().eligibility());
          coordinator.decideProjection(ProjectionDecision.ALLOW);
          coordinator.projectSource();
          assertTrue(coordinator.finalizeSource().contribution().isPresent());
        } else {
          assertEquals(ProjectionEligibility.INELIGIBLE, declarations.value().eligibility());
          assertTrue(declarations.diagnostics().stream().anyMatch(value -> value.code().equals("frontend.duplicate-member-declaration")));
          assertThrows(IllegalStateException.class, () -> coordinator.decideProjection(ProjectionDecision.ALLOW));
          assertThrows(IllegalStateException.class, coordinator::projectSource);
        }
      });
    }
  }

  @Test
  void givenValidMissingAndAbsentEnclosedMetadata_whenUnrelatedEmissionsAreAdded_thenReconciliationReturnsItsOwnCompletion()
      throws IOException {
    // Arrange
    withCompilation("class Sample { int value; } class Other { int foreign; }", fixture -> {
      var unit = fixture.compilation().units().getFirst();
      var declaration = (ClassTree) unit.getTypeDecls().getFirst();
      var path = new TreePath(new TreePath(unit), declaration);
      var compilation = fixture.compilation();
      var semantics = compilation.semantics();
      var owner = (TypeElement) semantics.element(path);
      var types = fixture.factory().create(unit, fixture.source()).translateDeclarations().value().types();
      var type = types.getFirst();
      var diagnostics = new DiagnosticEmissionCollector("declaration-observation", fixture.source().sourceFileKey());
      var supplied = (TypeElement) Proxy.newProxyInstance(TypeElement.class.getClassLoader(), new Class<?>[] {TypeElement.class},
          (proxy, method, arguments) -> {
            if (method.getName().equals("getEnclosedElements")) {
              diagnostics.emit(FrontendDiagnosticFactory.otherError(type.typeKey(), "frontend.duplicate-member-declaration",
                  "Controlled independent emission during metadata observation", Optional.empty()));
            }
            return method.invoke(owner, arguments);
          });
      var bodies = new JavaMethodBodyTranslator(semantics, compilation.sourceEvidence(), compilation.treeInventory(),
          JavaExecutableProjectionContext.metadata(POLICY));
      var translator = new JavaTypeTranslator(semantics, compilation.sourceEvidence(), compilation.treeInventory(),
          new JavaMethodTranslator(semantics, compilation.sourceEvidence(), compilation.treeInventory(), bodies), POLICY);
      var origin = compilation.sourceEvidence().origin(unit, fixture.source(), declaration);

      // Act
      var valid = translator.reconcileEnclosedDeclarations(unit, path, declaration, supplied, fixture.source(), type.typeKey(), origin,
          type.fields(), type.recordComponents(), type.methods(), List.of(), diagnostics);
      var absent = translator.reconcileEnclosedDeclarations(unit, path, declaration, null, fixture.source(), type.typeKey(), origin,
          List.of(), List.of(), List.of(), List.of(), diagnostics);
      var missing = translator.reconcileEnclosedDeclarations(unit, path, declaration, owner, fixture.source(), type.typeKey(), origin,
          List.of(), List.of(), type.methods(), List.of(), diagnostics);
      var mismatched = translator.reconcileEnclosedDeclarations(unit, path, declaration, owner, fixture.source(), type.typeKey(), origin,
          types.getLast().fields(), List.of(), type.methods(), List.of(), diagnostics);

      // Assert
      assertTrue(valid.complete());
      assertTrue(valid.synthesized().isEmpty());
      assertTrue(absent.complete());
      assertTrue(absent.synthesized().isEmpty());
      assertFalse(missing.complete());
      assertFalse(mismatched.complete());
      assertEquals(List.of("frontend.duplicate-member-declaration", "frontend.unrepresented-written-declaration",
          "frontend.unrepresented-written-declaration", "frontend.unmatched-enclosed-declaration"),
          diagnostics.snapshot().diagnostics().stream().map(value -> value.code()).toList());
    });
  }

  @Test
  void givenNestedArrayAndClassAnnotationValues_whenIndependentMessagesSurroundMissingRanges_thenActualFailureReasonAndEventSurvive()
      throws IOException {
    for (var example : List.of(
        new AnnotationCase("@interface A { int value(); } @A(7) class Sample {}", 2),
        new AnnotationCase("@interface A { int[] value(); } @A({7,8}) class Sample {}", 1),
        new AnnotationCase("@interface Inner { int value(); } @interface A { Inner value(); } @A(@Inner(7)) class Sample {}", 2),
        new AnnotationCase("@interface A { Class<?> value(); } @A(String.class) class Sample {}", 1))) {
      for (var reverse : List.of(false, true)) {
        // Arrange
        withCompilation(example.source(), fixture -> {
          var unit = fixture.compilation().units().getFirst();
          var annotation = ((ClassTree) unit.getTypeDecls().getLast()).getModifiers().getAnnotations().getFirst();
          var diagnostics = new DiagnosticEmissionCollector("annotation-observation", fixture.source().sourceFileKey());
          var injected = new AtomicBoolean();
          var base = fixture.compilation().sourceEvidence();
          var evidence = new JavaSourceEvidence(new SourcePositions() {
            @Override
            public long getStartPosition(CompilationUnitTree selected, Tree tree) {
              if ((tree instanceof LiteralTree literal && Integer.valueOf(7).equals(literal.getValue()))
                  || (tree instanceof IdentifierTree name && name.getName().contentEquals("String"))) {
                if (injected.compareAndSet(false, true)) {
                  emitIndependent(diagnostics, reverse ? "controlled.second" : "controlled.first");
                }
                return Diagnostic.NOPOS;
              }
              return base.rawStart(selected, tree);
            }

            @Override
            public long getEndPosition(CompilationUnitTree selected, Tree tree) {
              return base.rawEnd(selected, tree);
            }
          });
          var projector = new JavaAnnotationProjector(fixture.compilation().semantics(), evidence, fixture.compilation().treeInventory());
          var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 1).child(SyntaxRole.ANNOTATION, 0);

          // Act
          var projected = projector.declaration(unit, fixture.source(), annotation, path, path,
              DeclarationSite.TYPE, diagnostics);
          emitIndependent(diagnostics, reverse ? "controlled.first" : "controlled.second");

          // Assert
          assertTrue(injected.get());
          var node = projected.value().orElseThrow();
          assertEquals(Optional.of("frontend.missing-source-position"), node.value(Annotation.class).argumentFailureCode());
          assertTrue(node.children().isEmpty());
          var events = diagnostics.snapshot().events();
          assertEquals(reverse ? "controlled.second" : "controlled.first", events.getFirst().diagnostic().code());
          assertEquals(reverse ? "controlled.first" : "controlled.second", events.getLast().diagnostic().code());
          assertEquals(example.rangeEvents() + 2, events.size());
          for (var ordinal = 0; ordinal < example.rangeEvents(); ordinal++) {
            assertEquals("frontend.missing-source-position", events.get(ordinal + 1).diagnostic().code());
            assertEquals(new Address("JavaSourceEvidence", fixture.source().sourceFileKey(), ordinal),
                events.get(ordinal + 1).address());
          }
          assertEquals(new Address("JavaSourceEvidence", fixture.source().sourceFileKey(), 0), events.get(1).address());
          assertEquals(List.of(), events.get(1).contexts());
          assertEquals(-1, events.get(1).slot());
        });
      }
    }
  }

  @Test
  void givenEmptyAndFailedAnnotationArguments_whenOperationsReturnDetachedBatches_thenEmptySuccessAndExactFailureAreDistinct()
      throws IOException {
    // Arrange
    withCompilation("@interface A {} @A() class Sample {}", fixture -> {
      var unit = fixture.compilation().units().getFirst();
      AnnotationTree annotation = ((ClassTree) unit.getTypeDecls().getLast()).getModifiers().getAnnotations().getFirst();
      var inventory = fixture.compilation().treeInventory();
      var type = (TypeElement) fixture.compilation().semantics().element(inventory.annotationTypePath(annotation).orElseThrow());
      var wrong = (TypeElement) fixture.compilation().semantics().element(TreePath.getPath(unit, unit.getTypeDecls().getLast()));
      var collector = new DiagnosticEmissionCollector("annotation-observation", fixture.source().sourceFileKey());
      var projector = new JavaAnnotationValueProjector(fixture.compilation().semantics(), fixture.compilation().sourceEvidence(), inventory);
      var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.ANNOTATION, 0);

      // Act
      var empty = projector.arguments(unit, fixture.source(), annotation, type, path, collector);
      var refused = projector.arguments(unit, fixture.source(), annotation, wrong, path, collector);
      collector.include(refused.emissions());

      // Assert
      assertEquals(Optional.of(List.of()), empty.value().value());
      assertTrue(empty.value().failure().isEmpty());
      assertTrue(empty.emissions().events().isEmpty());
      assertTrue(refused.value().value().isEmpty());
      var failure = refused.value().failure().orElseThrow();
      assertSame(failure, refused.emissions().events().getFirst());
      assertSame(failure, collector.snapshot().events().getFirst());
      assertEquals("frontend.unavailable-annotation-attribution", failure.diagnostic().code());
      assertThrows(UnsupportedOperationException.class, () -> refused.emissions().events().clear());
    });
  }

  private static void emitIndependent(DiagnosticEmissionCollector diagnostics, String code) {
    diagnostics.emit("independent-observation", FrontendDiagnosticFactory.otherError("annotation-boundary", code,
        "Controlled independent diagnostic at an operation boundary", Optional.empty()));
  }

  private record AnnotationCase(String source, int rangeEvents) {
  }

  private void withCompilation(String content, Observation observation) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Sample.java"), content)));
    var sources = new SnapshotSourceCatalog(snapshot);
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(CONFIGURATION), sources)) {
      observation.observe(new Fixture(snapshot.sourceFiles().getFirst(), session.analyze(session.parse(), POLICY)));
    }
  }

  @FunctionalInterface
  private interface Observation {
    void observe(Fixture fixture) throws IOException;
  }

  private record Fixture(SourceFile source, JavaCompilation compilation) {
    JavaUnitTranslationFactory factory() {
      return new JavaUnitTranslationFactory(compilation.semantics(), compilation.sourceEvidence(), compilation.treeInventory(), POLICY);
    }
  }
}
