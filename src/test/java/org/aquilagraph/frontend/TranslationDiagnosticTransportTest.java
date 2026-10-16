package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.ToolProvider;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.AggregateFixture;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class TranslationDiagnosticTransportTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final String BODY_PRODUCER = "JavaMethodBodyTranslator";
  private static final String VALUE_PRODUCER = "JavaAnnotationValueProjector";
  @TempDir Path sourceRoot;

  @TestFactory
  Stream<DynamicTest> givenEmittingNestedDeclarationsAcrossSourceOperations_whenPublicProductionCloses_thenAllocationAndFinalEvidenceRemainDistinct() {
    return Stream.of(
        "class Sample { void subject() { @MissingFirst class First {} @MissingSecond class Second {} } }",
        "class Sample { void first() { @MissingFirst class First {} } void second() { @MissingSecond class Second {} } }",
        "@MissingFirst class Sample { void subject() { @MissingSecond class Second {} } }",
        "class Sample { { @MissingFirst class First {} @MissingSecond class Second {} } }",
        "class Sample { Object first = new Object() { @MissingFirst class First {} }; Object second = new Object() { @MissingSecond class Second {} }; }",
        "class Sample { void subject() { class Outer { void nested() { @MissingFirst class First {} @MissingSecond class Second {} } } } }",
        "enum Sample { FIRST { @MissingFirst class First {} }, SECOND { @MissingSecond class Second {} }; }",
        "enum Sample { FIRST(new Object() { @MissingFirst class First {} }), SECOND(new Object() { @MissingSecond class Second {} }); Sample(Object value) {} }")
        .map(source -> DynamicTest.dynamicTest(source, () -> {
          // Arrange
          var snapshot = InputSnapshot.fromSources(sourceRoot, List.of(SourceFile.fromContent(Path.of("Sample.java"), source)));
          var sourceFile = snapshot.sourceFiles().getFirst();
          var healthy = InputSnapshot.fromSources(sourceRoot, List.of(SourceFile.fromContent(Path.of("Sample.java"),
              source.replace("@MissingFirst", "@Deprecated").replace("@MissingSecond", "@Deprecated"))));
          var frontend = new JavaFrontend(new JavaSourceLoader());

          // Act
          var outcome = frontend.produce(snapshot, CONFIGURATION, AstProfile.JAVA_SOURCE_UNITS);
          var control = frontend.produce(healthy, CONFIGURATION, AstProfile.JAVA_SOURCE_UNITS);
          var repeated = frontend.produce(snapshot, CONFIGURATION, AstProfile.JAVA_SOURCE_UNITS);

          // Assert
          assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
          assertEquals(AstCompletion.COMPLETE, control.completion(), control.diagnostics().toString());
          assertEquals(List.of(), control.diagnostics());
          var evidence = outcome.coverageQualification().orElseThrow().validationEvidence();
          var events = evidence.diagnosticEmissions().stream()
              .filter(event -> event.address().producer().equals("JavaAnnotationProjector")).toList();
          assertEquals(List.of(new DiagnosticEmission.Address("JavaAnnotationProjector", sourceFile.sourceFileKey(), 0),
              new DiagnosticEmission.Address("JavaAnnotationProjector", sourceFile.sourceFileKey(), 1)), addresses(events));
          assertEquals(List.of("frontend.unresolved-annotation-type", "frontend.unresolved-annotation-type"),
              events.stream().map(event -> event.diagnostic().code()).toList());
          assertEquals(List.of(span(sourceFile, "@MissingFirst"), span(sourceFile, "@MissingSecond")),
              events.stream().map(event -> event.diagnostic().origins().getFirst()).toList());
          for (var event : events) {
            assertSame(outcome.diagnostics().get(event.slot()), event.diagnostic());
            assertEquals(List.of(), event.contexts());
            assertTrue(evidence.outcomes().stream().noneMatch(value -> value.emissions().contains(event.address())));
          }
          assertEquals(outcome.diagnostics(), repeated.diagnostics());
          assertEquals(evidence.diagnosticEmissions(), repeated.coverageQualification().orElseThrow().validationEvidence().diagnosticEmissions());
        }));
  }

  @Test
  void givenWrittenAnnotationsInsideEnumConstantBodies_whenPublicProductionCloses_thenImplicitConstructionEnvelopesDoNotInventOwnerMismatches()
      throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(sourceRoot, List.of(SourceFile.fromContent(Path.of("Sample.java"),
        "enum Sample { FIRST { @Deprecated class First {} }, SECOND { @Deprecated class Second {} }; }")));

    // Act
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CONFIGURATION, AstProfile.JAVA_SOURCE_UNITS);

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    assertEquals(List.of(), outcome.diagnostics());
    assertEquals(2, outcome.result().orElseThrow().aggregate().nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.Annotation).count());
  }

  @Test
  void givenNestedAnonymousCreationsAndAnOrdinaryControl_whenPublicProductionCloses_thenCreatingAddressesReachFinalSlotsAndContexts()
      throws IOException {
    // Arrange
    var source = """
        class Sample {
          Object first() {
            Object left = new Object() { int value = 1; };
            { Object nested = new Object() { int value = 2; }; }
            return left;
          }
          Object second() { return new Object() { int value = 3; }; }
        }
        """;
    var creating = new ArrayList<DiagnosticEmission>();
    withCompilation(source, fixture -> creating.addAll(eventsFrom(programTranslation(fixture), BODY_PRODUCER)));
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var ordinary = InputSnapshot.fromSources(sourceRoot.resolve("ordinary"), List.of(SourceFile.fromContent(Path.of("Sample.java"),
        "class Sample { Object first() { return new Object(); } }")));

    // Act
    var outcome = frontend.produce(new JavaSourceLoader().discover(sourceRoot), CONFIGURATION, AstProfile.JAVA_SOURCE_BOUNDED);
    var control = frontend.produce(ordinary, CONFIGURATION, AstProfile.JAVA_SOURCE_BOUNDED);

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
    assertEquals(AstCompletion.COMPLETE, control.completion(), control.diagnostics().toString());
    assertEquals(List.of(), control.diagnostics());
    var coverage = outcome.coverageQualification().orElseThrow().validationEvidence();
    var finalEvents = coverage.diagnosticEmissions().stream().filter(event -> event.address().producer().equals(BODY_PRODUCER)).toList();
    assertEquals(3, creating.size());
    assertEquals(addresses(creating), addresses(finalEvents));
    assertEquals(creating.stream().map(DiagnosticEmission::diagnostic).toList(), finalEvents.stream().map(DiagnosticEmission::diagnostic).toList());
    for (var event : finalEvents) {
      assertSame(outcome.diagnostics().get(event.slot()), event.diagnostic());
      assertEquals(AnalysisOutcomeKind.UNSUPPORTED_LANGUAGE_FEATURE, event.cause());
      assertFalse(event.contexts().isEmpty());
      for (var context : event.contexts()) {
        assertTrue(coverage.outcomes().stream().anyMatch(value -> value.address().equals(context)
            && value.emissions().contains(event.address())));
      }
    }
    assertTrue(control.result().orElseThrow().aggregate().nodes().stream().anyMatch(node -> node.sourceKind().name().equals("NEW_CLASS")));
  }

  @Test
  void givenRealWrittenAnnotationAndOneReconciler_whenDamagedAndHealthyAggregatesAreRepeatedlyChecked_thenEqualDiagnosticsKeepFreshCreatorAddresses()
      throws IOException {
    // Arrange
    withCompilation("@Deprecated class Sample {}", fixture -> {
      var translation = programTranslation(fixture);
      var healthy = translation.value().aggregate().orElseThrow();
      assertEquals(List.of(), translation.diagnostics());
      var damaged = withoutDeclarationAnnotations(fixture.snapshot(), healthy);
      var reconciler = new JavaAnnotationCompleteness(fixture.inventory(), fixture.compilation().semantics(), List.of());

      // Act
      var first = reconciler.reconcile(Optional.of(damaged)).events();
      var second = reconciler.reconcile(Optional.of(damaged)).events();
      var complete = reconciler.reconcile(Optional.of(healthy)).events();
      var third = reconciler.reconcile(Optional.of(damaged)).events();

      // Assert
      assertEquals(List.of(), complete);
      assertEquals(1, first.size());
      assertEquals(1, second.size());
      assertEquals(1, third.size());
      assertEquals(first.getFirst().diagnostic(), second.getFirst().diagnostic());
      assertEquals(first.getFirst().diagnostic(), third.getFirst().diagnostic());
      assertEquals(List.of(0, 1, 2), List.of(first.getFirst(), second.getFirst(), third.getFirst()).stream()
          .map(event -> event.address().ordinal()).toList());
      for (var event : List.of(first.getFirst(), second.getFirst(), third.getFirst())) {
        assertEquals("JavaAnnotationCompleteness", event.address().producer());
        assertEquals("input-annotation-reconciliation", event.address().owner());
        assertEquals("frontend.unrepresented-source-annotation", event.diagnostic().code());
        assertEquals("A written annotation has no unique retained occurrence", event.diagnostic().message());
        assertEquals(List.of(new SourceSpan(fixture.source().sourceFileKey(), 0, "@Deprecated".length())), event.diagnostic().origins());
        assertUnslottedCapabilityEvent(event);
      }
      assertNotEquals(first.getFirst().address(), second.getFirst().address());
      assertNotEquals(second.getFirst().address(), third.getFirst().address());
      assertEquals(0, first.getFirst().address().ordinal());
      assertThrows(UnsupportedOperationException.class, () -> first.add(second.getFirst()));
      assertThrows(UnsupportedOperationException.class, second::clear);
      assertThrows(UnsupportedOperationException.class, third::clear);
    });
  }

  @Test
  void givenUnsupportedAnonymousCreationsInNestedBlocksAndSiblingMethods_whenActualTranslationReturnsThroughEveryParent_thenBodyCreatorAddressesAndSourceOrderRemainUnchanged()
      throws IOException {
    // Arrange
    var source = """
        class Sample {
          int first() {
            Object left = null; left = new Object() { int value = 1; };
            { Object nested = null; nested = new Object() { int value = 2; }; }
            return 1;
          }
          int second() {
            Object right = null; right = new Object() { int value = 3; };
            return 3;
          }
        }
        """;
    withCompilation(source, fixture -> {
      var unit = fixture.parsed().units().getFirst();
      var type = (ClassTree) unit.getTypeDecls().getFirst();
      var typePath = new TreePath(new TreePath(unit), type);
      var firstMethod = writtenMethod(type, "first");
      var secondMethod = writtenMethod(type, "second");
      var firstBodyPath = bodyPath(type.getMembers().indexOf(firstMethod));
      var secondBodyPath = bodyPath(type.getMembers().indexOf(secondMethod));
      var expected = List.of(
          new DiagnosticEmission.Address(BODY_PRODUCER,
              "type:Sample#first():" + firstBodyPath.child(SyntaxRole.STATEMENT, 1).child(SyntaxRole.EXPRESSION, 0).child(SyntaxRole.VALUE, 0), 0),
          new DiagnosticEmission.Address(BODY_PRODUCER,
              "type:Sample#first():" + firstBodyPath.child(SyntaxRole.STATEMENT, 2).child(SyntaxRole.STATEMENT, 1).child(SyntaxRole.EXPRESSION, 0).child(SyntaxRole.VALUE, 0), 0),
          new DiagnosticEmission.Address(BODY_PRODUCER,
              "type:Sample#second():" + secondBodyPath.child(SyntaxRole.STATEMENT, 1).child(SyntaxRole.EXPRESSION, 0).child(SyntaxRole.VALUE, 0), 0));
      var bodies = new JavaMethodBodyTranslator(fixture.compilation().semantics(), fixture.compilation().sourceEvidence(),
          fixture.compilation().treeInventory(), JavaExecutableProjectionContext.metadata(JavaTranslationPolicy.legacyAnalysis()));
      var methods = new JavaMethodTranslator(fixture.compilation().semantics(), fixture.compilation().sourceEvidence(),
          fixture.compilation().treeInventory(), bodies);
      var types = typeTranslator(fixture, methods);

      // Act
      var body = bodies.translate(unit, fixture.source(), "type:Sample#first()", firstMethod.getBody(),
          new TreePath(new TreePath(typePath, firstMethod), firstMethod.getBody()), false, false);
      var method = methods.translate(unit, typePath, fixture.source(), "type:Sample", firstMethod);
      var declaration = types.translate(unit, new TreePath(unit), fixture.source(), type);
      var program = programTranslation(fixture);
      var emitted = eventsFrom(program, BODY_PRODUCER);

      // Assert
      assertFalse(fixture.compilation().diagnostics().stream().anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
      assertEquals(expected.subList(0, 2), addresses(eventsFrom(body, BODY_PRODUCER)), () -> body.diagnostics().toString());
      assertEquals(expected.subList(0, 2), addresses(eventsFrom(method, BODY_PRODUCER)), () -> method.diagnostics().toString());
      assertEquals(expected, addresses(eventsFrom(declaration, BODY_PRODUCER)), () -> declaration.diagnostics().toString());
      assertEquals(expected, addresses(emitted), () -> program.diagnostics().toString());
      assertEquals(List.of(span(fixture.source(), "new Object() { int value = 1; }"), span(fixture.source(), "new Object() { int value = 2; }"),
          span(fixture.source(), "new Object() { int value = 3; }")), emitted.stream().map(event -> event.diagnostic().origins().getFirst()).toList());
      for (var event : emitted) {
        assertEquals("frontend.unsupported-syntax", event.diagnostic().code());
        assertEquals("Unsupported Java construct in method body: NEW_CLASS", event.diagnostic().message());
        assertUnslottedCapabilityEvent(event);
      }
      assertEquals(program.diagnostics(), program.emissions().events().stream().map(DiagnosticEmission::diagnostic).toList());
      assertEquals(eventsFrom(body, BODY_PRODUCER), eventsFrom(method, BODY_PRODUCER));
      assertEquals(eventsFrom(declaration, BODY_PRODUCER), emitted);
    });
  }

  @Test
  void givenRequiredElementsMissingFromSiblingNestedAnnotations_whenActualAstAndProgramTranslationReturn_thenLeafEventsKeepTheirCreatingOrdinalsAndRanges()
      throws IOException {
    // Arrange
    var source = """
        @interface Inner { int value(); }
        @interface Outer { Inner left(); Inner right(); }
        @Outer(left=@Inner(), right=@Inner())
        class Sample {}
        """;
    withCompilation(source, fixture -> {
      var unit = fixture.parsed().units().getFirst();
      var bodies = new JavaMethodBodyTranslator(fixture.compilation().semantics(), fixture.compilation().sourceEvidence(),
          fixture.compilation().treeInventory(), JavaExecutableProjectionContext.metadata(JavaTranslationPolicy.legacyAnalysis()));
      var methods = new JavaMethodTranslator(fixture.compilation().semantics(), fixture.compilation().sourceEvidence(),
          fixture.compilation().treeInventory(), bodies);
      var types = typeTranslator(fixture, methods);
      var declarations = new ArrayList<JavaType>();
      var translatedBodies = new ArrayList<TranslatedMethodBody>();
      for (var tree : unit.getTypeDecls()) {
        var translation = types.translate(unit, new TreePath(unit), fixture.source(), (ClassTree) tree);
        var translated = translation.value().orElseThrow();
        declarations.addAll(translated.types());
        translatedBodies.addAll(translated.methodBodies());
      }
      var expected = List.of(new DiagnosticEmission.Address(VALUE_PRODUCER, fixture.source().sourceFileKey(), 0),
          new DiagnosticEmission.Address(VALUE_PRODUCER, fixture.source().sourceFileKey(), 1));
      var firstStart = source.indexOf("@Inner()");
      var secondStart = source.lastIndexOf("@Inner()");
      var expectedOrigins = List.of(new SourceSpan(fixture.source().sourceFileKey(), firstStart, firstStart + "@Inner()".length()),
          new SourceSpan(fixture.source().sourceFileKey(), secondStart, secondStart + "@Inner()".length()));

      // Act
      var child = new UnitSourceFixture(fixture.compilation().semantics(), fixture.compilation().sourceEvidence(),
          fixture.compilation().treeInventory(), JavaTranslationPolicy.legacyAnalysis()).translate(unit, fixture.source(), translatedBodies, declarations);
      var program = programTranslation(fixture);
      var childEvents = child.emissions().events().stream()
          .filter(event -> event.address().producer().equals(VALUE_PRODUCER)).toList();
      var parentEvents = eventsFrom(program, VALUE_PRODUCER);

      // Assert
      assertTrue(fixture.compilation().diagnostics().stream().anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
      assertEquals(expected, addresses(childEvents), () -> child.diagnostics().toString());
      assertEquals(expected, addresses(parentEvents), () -> program.diagnostics().toString());
      assertEquals(childEvents, parentEvents);
      assertEquals(expectedOrigins, parentEvents.stream().map(event -> event.diagnostic().origins().getFirst()).toList());
      for (var event : parentEvents) {
        assertEquals("frontend.incomplete-nested-annotation", event.diagnostic().code());
        assertEquals("The nested annotation omits a required element value", event.diagnostic().message());
        assertUnslottedCapabilityEvent(event);
      }
      assertFalse(program.emissions().events().stream().anyMatch(event -> event.address().producer().equals("JavaAnnotationProjector")
          && event.diagnostic().code().equals("frontend.unavailable-annotation-attribution")), () -> program.diagnostics().toString());
      assertEquals(program.diagnostics(), program.emissions().events().stream().map(DiagnosticEmission::diagnostic).toList());
    });
  }

  private void withCompilation(String text, Consumer<Fixture> assertion) throws IOException {
    Files.writeString(sourceRoot.resolve("Sample.java"), text);
    var snapshot = new JavaSourceLoader().discover(sourceRoot);
    var sources = new SnapshotSourceCatalog(snapshot);
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(CONFIGURATION),
        sources)) {
      var parsed = session.parse();
      var inventory = JavaAnnotationSourceInventory.capture(parsed, sources);
      var compilation = session.analyze(parsed, JavaTranslationPolicy.legacyAnalysis());
      assertion.accept(new Fixture(snapshot, snapshot.sourceFiles().getFirst(), sources, parsed, inventory, compilation));
    }
  }

  private static TranslationResult<TranslatedProgram> programTranslation(Fixture fixture) {
    return new JavaProgramTranslator(fixture.sources(), fixture.compilation().semantics(), fixture.compilation().sourceEvidence(),
        fixture.compilation().treeInventory(), JavaLanguageFeatureCoverage.fromConfiguration(CONFIGURATION), JavaTranslationPolicy.legacyAnalysis())
            .translate(fixture.snapshot(), fixture.compilation().units());
  }

  private static JavaTypeTranslator typeTranslator(Fixture fixture, JavaMethodTranslator methods) {
    return new JavaTypeTranslator(fixture.compilation().semantics(), fixture.compilation().sourceEvidence(),
        fixture.compilation().treeInventory(), new JavaDeclarationPropertyObserver(JavaLanguageFeatureCoverage.fromConfiguration(CONFIGURATION)),
        methods, JavaTranslationPolicy.legacyAnalysis());
  }

  private static MethodTree writtenMethod(ClassTree type, String name) {
    return type.getMembers().stream().filter(MethodTree.class::isInstance).map(MethodTree.class::cast)
        .filter(method -> method.getName().contentEquals(name)).findFirst().orElseThrow();
  }

  private static SyntaxStructuralPath bodyPath(int memberPosition) {
    return SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0).child(SyntaxRole.MEMBER, memberPosition)
        .child(SyntaxRole.BODY, 0);
  }

  private static SourceSpan span(SourceFile source, String written) {
    var start = source.content().indexOf(written);
    assertTrue(start >= 0);
    return new SourceSpan(source.sourceFileKey(), start, start + written.length());
  }

  private static List<DiagnosticEmission> eventsFrom(TranslationResult<?> translation, String producer) {
    return translation.emissions().events().stream().filter(event -> event.address().producer().equals(producer)).toList();
  }

  private static List<DiagnosticEmission.Address> addresses(List<DiagnosticEmission> events) {
    return events.stream().map(DiagnosticEmission::address).toList();
  }

  private static void assertUnslottedCapabilityEvent(DiagnosticEmission event) {
    assertEquals(AnalysisOutcomeKind.UNSUPPORTED_LANGUAGE_FEATURE, event.cause());
    assertEquals(AnalysisOutcomeKind.UNSUPPORTED_LANGUAGE_FEATURE, event.diagnostic().outcomeKind());
    assertEquals(DiagnosticStage.FRONTEND, event.diagnostic().stage());
    assertEquals(DiagnosticSeverity.ERROR, event.diagnostic().severity());
    assertEquals(List.of(), event.contexts());
    assertEquals(-1, event.slot());
  }

  private static JavaAstAggregate withoutDeclarationAnnotations(InputSnapshot snapshot, JavaAstAggregate aggregate) {
    var roots = aggregate.roots().stream().map(root -> rewrite(root, node -> {
      if (!(node.value() instanceof SyntaxValue.TypeDeclaration type) || node.children(SyntaxRole.ANNOTATION).isEmpty()) {
        return node;
      }
      var shape = type.shape();
      var value = new SyntaxValue.TypeDeclaration(type.name(), type.declarationKey(), type.writtenModifiers(), type.effectiveModifiers(),
          new SyntaxValue.TypeDeclaration.Shape(0, shape.typeParameters(), shape.superclass(), shape.interfaces(), shape.permittedSubclasses(),
              shape.recordComponents(), shape.members()));
      return copy(node, value, node.children().stream().filter(child -> child.role() != SyntaxRole.ANNOTATION).toList());
    })).toList();
    var nodes = roots.stream().flatMap(root -> root.descendantsAndSelf().stream()).toList();
    var builder = AggregateFixture.builder(snapshot);
    for (var root : roots) {
      var ownedNodes = root.descendantsAndSelf();
      var ownedKeys = ownedNodes.stream().map(SyntaxNode::nodeId).toList();
      builder.add(new SyntaxSourceContribution(root.sourceFileKey(), root, ownedNodes,
          ownedNodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(),
          aggregate.semanticEdges().stream().filter(edge -> ownedKeys.contains(edge.sourceKey())).toList()));
    }
    for (var methodKey : aggregate.methodBodyKeys()) {
      var original = aggregate.methodSyntax(methodKey).orElseThrow().root();
      var origin = aggregate.methodBodyOrigin(methodKey).orElseThrow();
      var body = origin == MethodBodyOrigin.SOURCE_AST
          ? nodes.stream().filter(node -> node.nodeId().equals(original.nodeId())).findFirst().orElseThrow()
          : original;
      builder.addMethodBody(methodKey, body, origin);
    }
    return builder.seal();
  }

  private static SyntaxNode rewrite(SyntaxNode node, UnaryOperator<SyntaxNode> replacement) {
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), rewrite(child.node(), replacement)))
        .toList();
    return replacement.apply(copy(node, node.value(), children));
  }

  private static SyntaxNode copy(SyntaxNode node, SyntaxValue value, List<SyntaxChild> children) {
    return SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), value, children,
        node.semanticReference(), node.origin().orElseThrow());
  }

  private record Fixture(InputSnapshot snapshot, SourceFile source, SnapshotSourceCatalog sources, ParsedJavaSource parsed,
      JavaAnnotationSourceInventory inventory, JavaCompilation compilation) {
  }
}
