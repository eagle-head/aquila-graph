package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.aquilagraph.source.internal.SourceAnalysisFixture;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.internal.AstResultAdmission;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.application.SourceAnalysisRunner;

import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class JavaAstSourcePositionTest {
  private static final String CONTENT = "class Example { }";
  @TempDir Path sourceRoot;

  @Test
  void givenInjectedMissingBodyPosition_whenDerivingAdmittedSource_thenSourceUnavailableHasNoInventedBodyAndOtherGraphsSurvive()
      throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Example.java"), "class Example { int unavailable() { return 1; } int available() { return 2; } }");
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var loader = new JavaSourceLoader();
    var snapshot = loader.discover(sourceRoot);
    var baseline = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, configuration, JavaTranslationPolicy.legacyAnalysis());
    var sources = new SnapshotSourceCatalog(snapshot);
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(configuration), sources)) {
      var compilation = session.analyze(session.parse(), JavaTranslationPolicy.legacyAnalysis());
      var unit = compilation.units().getFirst();
      var type = (ClassTree) unit.getTypeDecls().getFirst();
      var declaration = (MethodTree) type.getMembers().stream().filter(MethodTree.class::isInstance)
          .filter(member -> ((MethodTree) member).getName().contentEquals("unavailable")).findFirst().orElseThrow();
      var sourceFile = snapshot.sourceFiles().getFirst();
      var positions = new SourcePositions() {
        @Override
        public long getStartPosition(CompilationUnitTree owner, Tree tree) {
          return JavaProjectionIdentity.sameInstance(tree, declaration.getBody()) ? Diagnostic.NOPOS
              : compilation.sourceEvidence().origin(owner, sourceFile, tree).map(SourceSpan::startOffset).orElse(Diagnostic.NOPOS);
        }

        @Override
        public long getEndPosition(CompilationUnitTree owner, Tree tree) {
          return JavaProjectionIdentity.sameInstance(tree, declaration.getBody()) ? Diagnostic.NOPOS
              : compilation.sourceEvidence().origin(owner, sourceFile, tree).map(SourceSpan::endOffsetExclusive).orElse(Diagnostic.NOPOS);
        }
      };
      var translated = new JavaProgramTranslator(sources, compilation.semantics(), new JavaSourceEvidence(positions),
          compilation.treeInventory(), JavaTranslationPolicy.legacyAnalysis()).translate(snapshot, compilation.units());
      var input = JavaCoverageTestSupport.withFacts(baseline, translated.value().program(), translated.value().aggregate(), translated.emissions().events());
      var ast = AstResultAdmission.admit(input, AstProfile.JAVA_SOURCE_BOUNDED, "javac-public/1");
      var unavailable = ast.program().methods().stream().filter(method -> method.name().equals("unavailable")).findFirst().orElseThrow();
      var available = ast.program().methods().stream().filter(method -> method.name().equals("available")).findFirst().orElseThrow();
      var request = new CfgRequest(ast.identity(), List.of(unavailable.methodKey(), available.methodKey()), CfgProfile.JAVA_BOUNDED);

      // Act
      var cfg = CfgDerivers.standard().derive(ast, request);
      var run = new SourceAnalysisRunner(ignored -> {
        throw new AssertionError("Existing derivation must not produce source");
      }, CfgDerivers.standard()).deriveExisting(AstOutcome.available(ast), request);

      // Assert
      assertEquals(AstCompletion.INCOMPLETE, ast.completion());
      assertEquals(MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION, ast.aggregate().methodBodyOrigin(unavailable.methodKey()).orElseThrow());
      assertEquals(CfgState.PARTIAL, cfg.state());
      assertEquals(List.of(MethodCfgState.SOURCE_UNAVAILABLE, MethodCfgState.AVAILABLE), cfg.methods().stream().map(method -> method.state()).toList());
      var refusal = cfg.methods().getFirst();
      assertTrue(refusal.body().isEmpty());
      assertTrue(refusal.graph().isEmpty());
      assertEquals(List.of(MethodCfgState.SOURCE_UNAVAILABLE), refusal.blockers().stream().map(blocker -> blocker.kind()).toList());
      var blocker = refusal.blockers().getFirst();
      assertTrue(blocker.occurrence().isEmpty());
      assertEquals("body", blocker.slot());
      assertEquals(ast.identity(), blocker.astIdentity());
      assertEquals(unavailable.methodKey(), blocker.methodKey());
      assertEquals("control-flow.source-unavailable", blocker.diagnostic().code());
      assertTrue(blocker.diagnostic().origins().isEmpty());
      assertEquals(List.of(blocker.diagnostic()), cfg.diagnostics());
      assertEquals(ast.bodyRef(available.methodKey()).orElseThrow(), cfg.methods().getLast().graph().orElseThrow().body());
      assertEquals(AnalysisCompletion.PARTIAL, run.completion());
      assertEquals(ast, run.representation().ast().result().orElseThrow());
    }
  }

  @Test
  void givenUnavailableAndInvalidRootPositions_whenContributionIsTranslated_thenSpecificBlockingDiagnosticsAreReturned()
      throws IOException {
    // Arrange
    var compiler = ToolProvider.getSystemJavaCompiler();
    var sourceFile = new SourceFile("source", Path.of("Example.java"), "fixture-content", CONTENT);
    var source = new SimpleJavaFileObject(URI.create("string:///Example.java"), JavaFileObject.Kind.SOURCE) {
      @Override
      public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return CONTENT;
      }
    };
    try (var fileManager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      var task = (JavacTask) compiler.getTask(null, fileManager, null, List.of("-proc:none", "--release", "25"), null,
          List.of(source));
      var unit = task.parse().iterator().next();
      var inventory = ParsedTreeInventory.capture(List.of(unit), ignored -> Optional.of(sourceFile),
          new JavaSourceEvidence(Trees.instance(task).getSourcePositions()));
      task.analyze();
      var trees = Trees.instance(task);
      var semantics = new SemanticResolver(trees, task.getTypes(), task.getElements(),
          new DeclarationIdentityRegistry(trees, task.getTypes(), task.getElements(), JavaTranslationPolicy.legacyAnalysis()));
      var validMetadata = new JavaUnitTranslationFactory(semantics, new JavaSourceEvidence(trees.getSourcePositions()), inventory,
          JavaTranslationPolicy.legacyAnalysis()).create(unit, sourceFile).translateDeclarations().value().types();
      assertEquals(1, validMetadata.size());
      var cases = List.of(new PositionCase(Diagnostic.NOPOS, 0, "frontend.missing-source-position"),
          new PositionCase(0, Diagnostic.NOPOS, "frontend.missing-source-position"),
          new PositionCase(-2, 1, "frontend.invalid-source-range"),
          new PositionCase(5, 4, "frontend.invalid-source-range"),
          new PositionCase(0, CONTENT.length() + 1L, "frontend.invalid-source-range"));

      // Act
      for (var position : cases) {
        var evidence = new JavaSourceEvidence(new FixedPositions(position.start(), position.end()));
        var translation = new UnitSourceFixture(semantics, evidence, inventory, JavaTranslationPolicy.legacyAnalysis()).translate(unit, sourceFile, List.of(), validMetadata);

        // Assert
        assertTrue(translation.contribution().isEmpty());
        assertEquals(1, translation.diagnostics().size());
        var diagnostic = translation.diagnostics().getFirst();
        assertEquals(position.code(), diagnostic.code());
        assertEquals(DiagnosticStage.FRONTEND, diagnostic.stage());
        assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity());
        assertEquals(AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR, diagnostic.outcomeKind());
        assertTrue(diagnostic.origins().isEmpty());
        var message = position.code().equals("frontend.missing-source-position")
            ? "A required source occurrence has no compiler position"
            : "A required source occurrence has an invalid compiler range";
        message += " in Example.java at source-file-root:0";
        var expected = new AnalysisDiagnostic(
            StableId.sha256("diagnostic", "source:source-file-root:0", position.code(), "no-direct-source"), position.code(),
            DiagnosticStage.FRONTEND, DiagnosticSeverity.ERROR, message, List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
        assertEquals(expected, diagnostic);
        assertTrue(evidence.origin(unit, sourceFile, unit).isEmpty());
      }
    }
  }

  @Test
  void givenExactContentBoundary_whenPositionIsRead_thenHalfOpenRangeIsRetained() throws IOException {
    // Arrange
    var sourceFile = new SourceFile("source", Path.of("Example.java"), "fixture-content", CONTENT);
    var compiler = ToolProvider.getSystemJavaCompiler();
    var source = new SimpleJavaFileObject(URI.create("string:///Example.java"), JavaFileObject.Kind.SOURCE) {
      @Override
      public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return CONTENT;
      }
    };
    try (var fileManager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      var task = (JavacTask) compiler.getTask(null, fileManager, null, List.of("-proc:none", "--release", "25"), null,
          List.of(source));
      var unit = task.parse().iterator().next();
      var evidence = new JavaSourceEvidence(new FixedPositions(0, CONTENT.length()));

      // Act
      var position = evidence.position(unit, sourceFile, unit);

      // Assert
      assertEquals(new JavaSourceEvidence.Position.Available(new SourceSpan("source", 0, CONTENT.length())), position);
    }
  }

  @Test
  void givenRealJdkPositions_whenRead_thenRootNestedAndEmptyRangesMatchCapturedContent() throws IOException {
    var content = "class Example { int value() { return 1; } }";
    withCompiledSource(content, fixture -> {
      var evidence = new JavaSourceEvidence(fixture.positions());
      var unit = fixture.unit();
      var type = (ClassTree) unit.getTypeDecls().getFirst();
      var method = (MethodTree) type.getMembers().stream().filter(member -> member instanceof MethodTree candidate
          && candidate.getName().contentEquals("value")).findFirst().orElseThrow();

      assertEquals(new JavaSourceEvidence.Position.Available(new SourceSpan("source", 0, content.length())),
          evidence.position(unit, fixture.sourceFile(), unit));
      var nested = evidence.position(unit, fixture.sourceFile(), method.getBody());
      assertTrue(nested instanceof JavaSourceEvidence.Position.Available);
      var range = ((JavaSourceEvidence.Position.Available) nested).span();
      assertTrue(range.startOffset() > 0);
      assertTrue(range.endOffsetExclusive() > range.startOffset());
      assertTrue(range.endOffsetExclusive() <= content.length());
    });
    withCompiledSource("", fixture -> {
      var evidence = new JavaSourceEvidence(fixture.positions());
      assertEquals(new JavaSourceEvidence.Position.Available(new SourceSpan("source", 0, 0)),
          evidence.position(fixture.unit(), fixture.sourceFile(), fixture.unit()));
    });
  }

  @Test
  void givenInjectedRootPositionFault_whenPublicEngineAnalyzes_thenCompletionIsBlocked() throws IOException {
    Files.writeString(sourceRoot.resolve("Example.java"), CONTENT);
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var loader = new JavaSourceLoader();
    var snapshot = loader.discover(sourceRoot);
    var baseline = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, configuration, JavaTranslationPolicy.legacyAnalysis());
    var sources = new SnapshotSourceCatalog(snapshot);
    var cases = List.of(new PositionCase(Diagnostic.NOPOS, 0, "frontend.missing-source-position"),
        new PositionCase(0, Diagnostic.NOPOS, "frontend.missing-source-position"),
        new PositionCase(-2, 1, "frontend.invalid-source-range"),
        new PositionCase(5, 4, "frontend.invalid-source-range"),
        new PositionCase(0, CONTENT.length() + 1L, "frontend.invalid-source-range"));
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(configuration), sources)) {
      var parsed = session.parse();
      var compilation = session.analyze(parsed, JavaTranslationPolicy.legacyAnalysis());
      for (var position : cases) {
        var evidence = new JavaSourceEvidence(new FixedPositions(position.start(), position.end()));
        var translated = new JavaProgramTranslator(sources, compilation.semantics(), evidence, compilation.treeInventory(), JavaTranslationPolicy.legacyAnalysis()).translate(snapshot, compilation.units());
        var input = JavaCoverageTestSupport.withFacts(baseline, translated.value().program(),
            translated.value().aggregate(), translated.emissions().events());
        var run = new SourceAnalysisFixture().analyze(input);

        assertTrue(input.aggregate().isEmpty());
        assertTrue(input.program().types().isEmpty());
        assertTrue(run.completion() != AnalysisCompletion.COMPLETE);
        assertTrue(run.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals(position.code())
            && diagnostic.message().contains("Example.java at source-file-root:0")));
      }
    }
  }

  @Test
  void givenWrittenSuperCallWithoutPosition_whenTranslated_thenMissingPositionBlocksItsSourceBody() throws IOException {
    // Arrange
    withCompiledSource("class Example { Example() { super(); } }", fixture -> {
      var type = (ClassTree) fixture.unit().getTypeDecls().getFirst();
      var constructor = (MethodTree) type.getMembers().stream().filter(member -> member instanceof MethodTree).findFirst()
          .orElseThrow();
      var statement = constructor.getBody().getStatements().getFirst();
      assertTrue(fixture.inventory().isWritten(statement));
      var evidence = new JavaSourceEvidence(new MissingPositions(fixture.positions(), List.of(statement)));
      var bodyTranslator = new JavaMethodBodyTranslator(fixture.semantics(), evidence, fixture.inventory(), JavaExecutableProjectionContext.metadata(JavaTranslationPolicy.legacyAnalysis()));
      var methodTranslator = new JavaMethodTranslator(fixture.semantics(), evidence, fixture.inventory(), bodyTranslator);
      // Act
      var method = methodTranslator.translate(fixture.unit(), new TreePath(new TreePath(fixture.unit()), type), fixture.sourceFile(),
          "type:Example", constructor);
      var body = method.value().body().orElseThrow();
      var typeTranslator = new JavaTypeTranslator(fixture.semantics(), evidence, fixture.inventory(),
          methodTranslator, JavaTranslationPolicy.legacyAnalysis());
      var declaredTypes = typeTranslator.translate(fixture.unit(), new TreePath(fixture.unit()), fixture.sourceFile(), type)
          .value().orElseThrow().types();
      var contribution = new UnitSourceFixture(fixture.semantics(), evidence, fixture.inventory(), JavaTranslationPolicy.legacyAnalysis())
          .translate(fixture.unit(), fixture.sourceFile(), List.of(body), declaredTypes);

      // Assert
      var missing = method.diagnostics().stream().filter(diagnostic -> diagnostic.code().equals("frontend.missing-source-position"))
          .toList();
      assertEquals(1, missing.size());
      assertEquals(DiagnosticStage.FRONTEND, missing.getFirst().stage());
      assertEquals(DiagnosticSeverity.ERROR, missing.getFirst().severity());
      assertEquals(AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR, missing.getFirst().outcomeKind());
      assertTrue(missing.getFirst().origins().isEmpty());
      assertTrue(missing.getFirst().message().contains("Example.java at source-file-root:0"));
      assertTrue(contribution.contribution().isPresent());
      assertTrue(contribution.contribution().orElseThrow().nodes().stream().noneMatch(node -> node.syntaxKind() == SyntaxKind.BLOCK));
      assertEquals(MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION, contribution.bodies().getFirst().origin());
    });
  }

  @Test
  void givenTwoMissingSiblingDeclarations_whenTranslated_thenDiagnosticsHaveDistinctOccurrenceKeys() throws IOException {
    // Arrange
    withCompiledSource("class First { } class Second { }", fixture -> {
      var first = fixture.unit().getTypeDecls().get(0);
      var second = fixture.unit().getTypeDecls().get(1);
      var evidence = new JavaSourceEvidence(new MissingPositions(fixture.positions(), List.of(first, second)));
      // Act
      var result = new UnitSourceFixture(fixture.semantics(), evidence, fixture.inventory(), JavaTranslationPolicy.legacyAnalysis())
          .translate(fixture.unit(), fixture.sourceFile(), List.of(), fixture.types());

      // Assert
      assertEquals(2, result.diagnostics().size());
      assertNotEquals(result.diagnostics().get(0).diagnosticKey(), result.diagnostics().get(1).diagnosticKey());
      assertNotEquals(result.diagnostics().get(0).message(), result.diagnostics().get(1).message());
      assertEquals(List.of("frontend.missing-source-position", "frontend.missing-source-position"),
          result.diagnostics().stream().map(AnalysisDiagnostic::code).toList());
      assertTrue(result.contribution().orElseThrow().root().children().isEmpty());
    });
    withCompiledSource("class Example { int first; int second; }", fixture -> {
      var type = (ClassTree) fixture.unit().getTypeDecls().getFirst();
      var fields = new ArrayList<Tree>();
      for (var member : type.getMembers()) {
        if (member instanceof VariableTree) {
          fields.add(member);
        }
      }
      var evidence = new JavaSourceEvidence(new MissingPositions(fixture.positions(), fields));
      var result = new UnitSourceFixture(fixture.semantics(), evidence, fixture.inventory(), JavaTranslationPolicy.legacyAnalysis())
          .translate(fixture.unit(), fixture.sourceFile(), List.of(), fixture.types());

      assertEquals(2, result.diagnostics().size());
      assertNotEquals(result.diagnostics().get(0).diagnosticKey(), result.diagnostics().get(1).diagnosticKey());
      assertTrue(result.contribution().orElseThrow().root().descendantsAndSelf().stream()
          .noneMatch(node -> node.syntaxKind() == SyntaxKind.FIELD_DECLARATION));
    });
  }

  @Test
  void givenMissingTypeHeaderRanges_whenTranslated_thenTypeIsWithheldAndDistinctErrorsAreReturned() throws IOException {
    // Arrange
    withCompiledSource("class Example implements First, Second {} interface First {} interface Second {}", fixture -> {
      var type = (ClassTree) fixture.unit().getTypeDecls().getFirst();
      var evidence = new JavaSourceEvidence(new MissingPositions(fixture.positions(), type.getImplementsClause()));

      // Act
      var result = new UnitSourceFixture(fixture.semantics(), evidence, fixture.inventory(), JavaTranslationPolicy.legacyAnalysis())
          .translate(fixture.unit(), fixture.sourceFile(), List.of(), fixture.types());
      var root = result.contribution().orElseThrow().root();

      // Assert
      assertEquals(2, root.children(SyntaxRole.TYPE_DECLARATION).size());
      assertTrue(root.children(SyntaxRole.TYPE_DECLARATION).stream()
          .noneMatch(node -> node.value(SyntaxValue.TypeDeclaration.class).name().equals("Example")));
      assertEquals(List.of("frontend.missing-source-position", "frontend.missing-source-position"),
          result.diagnostics().stream().map(AnalysisDiagnostic::code).toList());
      assertNotEquals(result.diagnostics().get(0).diagnosticKey(), result.diagnostics().get(1).diagnosticKey());
      assertTrue(result.diagnostics().stream().allMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR
          && diagnostic.outcomeKind() == AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR));
    });
  }

  @Test
  void givenMissingWrittenTypeArgumentRange_whenTranslated_thenOwningDeclarationIsWithheld() throws IOException {
    // Arrange
    withCompiledSource("class Example { java.util.List<String> values; }", fixture -> {
      var type = (ClassTree) fixture.unit().getTypeDecls().getFirst();
      var field = (VariableTree) type.getMembers().stream().filter(VariableTree.class::isInstance).findFirst().orElseThrow();
      var argument = ((ParameterizedTypeTree) field.getType()).getTypeArguments().getFirst();
      var evidence = new JavaSourceEvidence(new MissingPositions(fixture.positions(), List.of(argument)));

      // Act
      var result = new UnitSourceFixture(fixture.semantics(), evidence, fixture.inventory(), JavaTranslationPolicy.legacyAnalysis())
          .translate(fixture.unit(), fixture.sourceFile(), List.of(), fixture.types());

      // Assert
      assertEquals(List.of(), result.contribution().orElseThrow().root().children(SyntaxRole.TYPE_DECLARATION));
      assertEquals(List.of("frontend.missing-source-position"),
          result.diagnostics().stream().map(AnalysisDiagnostic::code).toList());
      assertEquals(DiagnosticSeverity.ERROR, result.diagnostics().getFirst().severity());
    });
  }

  @Test
  void givenMissingArgumentsInTwoTypes_whenTranslated_thenRecursiveDiagnosticsRetainOriginalTypeOrdinals() throws IOException {
    // Arrange
    withCompiledSource("class First { java.util.List<String> names; } class Second { java.util.List<Integer> counts; }",
        fixture -> {
          var first = (ClassTree) fixture.unit().getTypeDecls().get(0);
          var second = (ClassTree) fixture.unit().getTypeDecls().get(1);
          var firstField = (VariableTree) first.getMembers().stream().filter(VariableTree.class::isInstance)
              .findFirst().orElseThrow();
          var secondField = (VariableTree) second.getMembers().stream().filter(VariableTree.class::isInstance)
              .findFirst().orElseThrow();
          var firstArgument = ((ParameterizedTypeTree) firstField.getType()).getTypeArguments().getFirst();
          var secondArgument = ((ParameterizedTypeTree) secondField.getType()).getTypeArguments().getFirst();
          var evidence = new JavaSourceEvidence(new MissingPositions(fixture.positions(), List.of(firstArgument, secondArgument)));

          // Act
          var result = new UnitSourceFixture(fixture.semantics(), evidence, fixture.inventory(), JavaTranslationPolicy.legacyAnalysis())
              .translate(fixture.unit(), fixture.sourceFile(), List.of(), fixture.types());

          // Assert
          assertEquals(List.of(), result.contribution().orElseThrow().root().children(SyntaxRole.TYPE_DECLARATION));
          assertEquals(List.of("frontend.missing-source-position", "frontend.missing-source-position"),
              result.diagnostics().stream().map(AnalysisDiagnostic::code).toList());
          var sourceKey = fixture.sourceFile().sourceFileKey();
          for (var ordinal = 0; ordinal < 2; ordinal++) {
            var occurrence = "source-file-root:0:type-declaration:" + ordinal
                + ":member:1:declared-type:0:type-argument:0";
            var diagnostic = result.diagnostics().get(ordinal);
            assertTrue(diagnostic.message().contains("Example.java at " + occurrence), diagnostic::message);
            assertEquals(StableId.sha256("diagnostic", sourceKey + ':' + occurrence,
                "frontend.missing-source-position", "no-direct-source"), diagnostic.diagnosticKey());
          }
        });
  }

  @Test
  void givenMissingExecutableHeaderRanges_whenTranslated_thenIncompleteMethodsAreWithheld() throws IOException {
    // Arrange
    var source = "interface Contract { <T extends Number> T run(T value) throws java.io.IOException; }";
    withCompiledSource(source, fixture -> {
      var type = (ClassTree) fixture.unit().getTypeDecls().getFirst();
      var method = (MethodTree) type.getMembers().stream().filter(MethodTree.class::isInstance)
          .filter(member -> ((MethodTree) member).getName().contentEquals("run")).findFirst().orElseThrow();
      var missingHeaders = List.<Tree>of(method.getTypeParameters().getFirst().getBounds().getFirst(),
          method.getParameters().getFirst().getType(), method.getThrows().getFirst());

      // Act
      for (var missing : missingHeaders) {
        var evidence = new JavaSourceEvidence(new MissingPositions(fixture.positions(), List.of(missing)));
        var result = new UnitSourceFixture(fixture.semantics(), evidence, fixture.inventory(), JavaTranslationPolicy.legacyAnalysis())
            .translate(fixture.unit(), fixture.sourceFile(), List.of(), fixture.types());
        var retainedTypes = result.contribution().orElseThrow().root().children(SyntaxRole.TYPE_DECLARATION);

        // Assert
        assertEquals(List.of(), retainedTypes);
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.missing-source-position")
            && diagnostic.severity() == DiagnosticSeverity.ERROR));
      }
    });
  }

  @Test
  void givenMissingReceiverNameRange_whenTranslated_thenExecutableIsWithheld() throws IOException {
    // Arrange
    withCompiledSource("abstract class Receiver { abstract void run(Receiver this); }", fixture -> {
      var type = (ClassTree) fixture.unit().getTypeDecls().getFirst();
      var method = (MethodTree) type.getMembers().stream().filter(MethodTree.class::isInstance)
          .filter(member -> ((MethodTree) member).getName().contentEquals("run")).findFirst().orElseThrow();
      var receiverName = method.getReceiverParameter().getNameExpression();
      var evidence = new JavaSourceEvidence(new MissingPositions(fixture.positions(), List.of(receiverName)));

      // Act
      var result = new UnitSourceFixture(fixture.semantics(), evidence, fixture.inventory(), JavaTranslationPolicy.legacyAnalysis())
          .translate(fixture.unit(), fixture.sourceFile(), List.of(), fixture.types());

      // Assert
      assertEquals(List.of(), result.contribution().orElseThrow().root().children(SyntaxRole.TYPE_DECLARATION));
      assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.missing-source-position")
          && diagnostic.severity() == DiagnosticSeverity.ERROR));
      assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unavailable-receiver-name")
          && diagnostic.severity() == DiagnosticSeverity.ERROR));
    });
  }

  @Test
  void givenMissingTypeHeaderWithWrittenMembers_whenProgramIsTranslated_thenAggregateSealsWithoutOrphanEdges() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Example.java"),
        "class Example implements First { int value; void action() {} } interface First {}");
    var loader = new JavaSourceLoader();
    var snapshot = loader.discover(sourceRoot);
    var baseline = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var sources = new SnapshotSourceCatalog(snapshot);
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(),
        JavaCompilerInvocation.from(CompilerConfiguration.forRelease(JavaRelease.JAVA_25)), sources)) {
      var parsed = session.parse();
      var compilation = session.analyze(parsed, JavaTranslationPolicy.legacyAnalysis());
      var unit = compilation.units().getFirst();
      var declaration = (ClassTree) unit.getTypeDecls().getFirst();
      var positions = new SourcePositions() {
        @Override
        public long getStartPosition(CompilationUnitTree currentUnit, Tree tree) {
          return compilation.sourceEvidence().origin(currentUnit, sources.find(currentUnit).orElseThrow(), tree)
              .map(SourceSpan::startOffset).orElse(Diagnostic.NOPOS);
        }

        @Override
        public long getEndPosition(CompilationUnitTree currentUnit, Tree tree) {
          return compilation.sourceEvidence().origin(currentUnit, sources.find(currentUnit).orElseThrow(), tree)
              .map(SourceSpan::endOffsetExclusive).orElse(Diagnostic.NOPOS);
        }
      };
      var evidence = new JavaSourceEvidence(new MissingPositions(positions, List.of(declaration.getImplementsClause().getFirst())));

      // Act
      var translated = new JavaProgramTranslator(sources, compilation.semantics(), evidence, compilation.treeInventory(), JavaTranslationPolicy.legacyAnalysis()).translate(snapshot, compilation.units());
      var aggregate = translated.value().aggregate().orElseThrow();
      var diagnostics = JavaCoverageTestSupport.collector(baseline, "source-position-observation");
      diagnostics.include(translated.emissions());
      var input = JavaCoverageTestSupport.withFacts(baseline, translated.value().program(),
          translated.value().aggregate(), diagnostics.snapshot().events());
      var run = new SourceAnalysisFixture().analyze(input);

      // Assert
      assertTrue(aggregate.roots().getFirst().children(SyntaxRole.TYPE_DECLARATION).stream()
          .noneMatch(type -> type.value(SyntaxValue.TypeDeclaration.class).name().equals("Example")));
      assertTrue(aggregate.semanticEdges().stream().allMatch(edge -> aggregate.node(edge.sourceKey()).isPresent()));
      var example = translated.value().program().types().stream().filter(type -> type.qualifiedName().equals("Example")).findFirst().orElseThrow();
      var first = translated.value().program().types().stream().filter(type -> type.qualifiedName().equals("First")).findFirst().orElseThrow();
      assertTrue(aggregate.declarationTypeEvidence(example.typeKey()).isEmpty());
      assertEquals(List.of(first.typeKey()), aggregate.declarationTypeEvidence().stream().map(evidenceValue -> evidenceValue.ownerTypeKey()).toList());
      var action = example.methods().stream().filter(method -> method.name().equals("action")).findFirst().orElseThrow();
      assertEquals(MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, aggregate.methodBodyOrigin(action.methodKey()).orElseThrow());
      assertTrue(translated.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.missing-source-position")
          && diagnostic.severity() == DiagnosticSeverity.ERROR));
      assertTrue(run.completion() != AnalysisCompletion.COMPLETE);
    }
  }

  @Test
  void givenAttributedTypeMembers_whenComparedWithParsedInventory_thenOnlyWrittenBodyMembersHaveSourceEdges() throws IOException {
    // Arrange
    var source = """
        interface Example { int field = 1; void action(); class Nested {} }
        enum Shade { RED }
        record Pair(int left) {}
        """;
    withCompiledSource(source, fixture -> {
      var unitPath = new TreePath(fixture.unit());
      var evidence = new JavaSourceEvidence(fixture.positions());
      var bodyTranslator = new JavaMethodBodyTranslator(fixture.semantics(), evidence, fixture.inventory(), JavaExecutableProjectionContext.metadata(JavaTranslationPolicy.legacyAnalysis()));
      var methodTranslator = new JavaMethodTranslator(fixture.semantics(), evidence, fixture.inventory(), bodyTranslator);
      var typeTranslator = new JavaTypeTranslator(fixture.semantics(), evidence, fixture.inventory(), methodTranslator, JavaTranslationPolicy.legacyAnalysis());
      var modeledTypes = new ArrayList<JavaType>();
      for (var declaration : fixture.unit().getTypeDecls()) {
        if (declaration instanceof ClassTree type) {
          modeledTypes.addAll(typeTranslator.translate(fixture.unit(), unitPath, fixture.sourceFile(), type).value().orElseThrow().types());
        }
      }
      var contributor = new UnitSourceFixture(fixture.semantics(), evidence, fixture.inventory(), JavaTranslationPolicy.legacyAnalysis());

      // Act
      var root = contributor.translate(fixture.unit(), fixture.sourceFile(), List.of(), modeledTypes).contribution().orElseThrow().root();
      var types = root.children(SyntaxRole.TYPE_DECLARATION);
      var compilerGeneratedCount = 0L;
      var recordComponentCount = 0;
      for (var ordinal = 0; ordinal < types.size(); ordinal++) {
        var declaration = (ClassTree) fixture.unit().getTypeDecls().get(ordinal);
        var classPath = new TreePath(unitPath, declaration);
        var typeElement = (TypeElement) fixture.semantics().element(classPath);
        compilerGeneratedCount += typeElement.getEnclosedElements().stream()
            .filter(member -> fixture.elements().getOrigin(member) != Elements.Origin.EXPLICIT).count();
        var expected = new ArrayList<String>();
        for (var member : declaration.getMembers()) {
          if (!fixture.inventory().isWritten(member)) {
            continue;
          }
          var memberPath = new TreePath(classPath, member);
          var origin = fixture.elements().getOrigin(fixture.semantics().element(memberPath));
          if (declaration.getKind() == Tree.Kind.RECORD && member instanceof VariableTree variable
              && typeElement.getRecordComponents().stream()
                  .anyMatch(component -> component.getSimpleName().contentEquals(variable.getName()))) {
            assertEquals(Elements.Origin.EXPLICIT, origin);
            recordComponentCount++;
            continue;
          }
          assertEquals(Elements.Origin.EXPLICIT, origin);
          var syntaxKind = switch (member) {
            case MethodTree _ -> SyntaxKind.METHOD_DECLARATION;
            case VariableTree _ -> fixture.semantics().element(memberPath).getKind() == ElementKind.ENUM_CONSTANT
                ? SyntaxKind.ENUM_CONSTANT_DECLARATION : SyntaxKind.FIELD_DECLARATION;
            case ClassTree _ -> SyntaxKind.TYPE_DECLARATION;
            default -> throw new IllegalStateException("Unexpected source member kind: " + member.getKind());
          };
          var start = fixture.positions().getStartPosition(fixture.unit(), member);
          var end = fixture.positions().getEndPosition(fixture.unit(), member);
          expected.add(syntaxKind + "|" + JavaSourceKind.valueOf(member.getKind().name()) + "|"
              + source.substring(Math.toIntExact(start), Math.toIntExact(end)));
        }
        var actual = types.get(ordinal).children(SyntaxRole.MEMBER).stream().map(node -> memberTuple(source, node)).toList();

        // Assert
        assertEquals(expected, actual);
      }
      assertTrue(compilerGeneratedCount > 0);
      assertEquals(1, recordComponentCount);
    });
  }

  @Test
  void givenOrdinaryAndRecordDeclarationsWithoutSemanticInventory_whenTranslated_thenHeadersAreDiagnosedAndTypesAreWithheld() throws IOException {
    // Arrange
    for (var text : List.of("class Example {}", "record Example(int value) {}")) {
      withCompiledSource(text, fixture -> {
        var contributor = new UnitSourceFixture(fixture.semantics(), new JavaSourceEvidence(fixture.positions()),
            fixture.inventory(), JavaTranslationPolicy.legacyAnalysis());

        // Act
        var result = contributor.translate(fixture.unit(), fixture.sourceFile(), List.of(), List.of());

        // Assert
        assertTrue(result.contribution().orElseThrow().root().children(SyntaxRole.TYPE_DECLARATION).isEmpty());
        assertEquals(List.of("frontend.unavailable-record-header"),
            result.diagnostics().stream().map(AnalysisDiagnostic::code).toList());
      });
    }
  }

  @Test
  void givenFirstOfTwoRecordComponentRangesMissing_whenTranslated_thenDiagnosticWithholdsProgramAndAggregate()
      throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("R.java"), "record R(int first, int second) {}");
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var loader = new JavaSourceLoader();
    var snapshot = loader.discover(sourceRoot);
    var baseline = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, configuration, JavaTranslationPolicy.legacyAnalysis());
    var sources = new SnapshotSourceCatalog(snapshot);
    assertEquals(1, baseline.program().types().size());
    assertTrue(baseline.aggregate().isPresent());
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(configuration), sources)) {
      var parsed = session.parse();
      var compilation = session.analyze(parsed, JavaTranslationPolicy.legacyAnalysis());
      var unit = compilation.units().getFirst();
      var declaration = (ClassTree) unit.getTypeDecls().getFirst();
      var owner = (TypeElement) compilation.semantics().element(new TreePath(new TreePath(unit), declaration));
      var firstComponentTree = compilation.semantics().sourceTree(owner.getRecordComponents().getFirst());
      var actualPositions = new SourcePositions() {
        @Override
        public long getStartPosition(CompilationUnitTree currentUnit, Tree tree) {
          return compilation.sourceEvidence().origin(currentUnit, sources.find(currentUnit).orElseThrow(), tree)
              .map(SourceSpan::startOffset).orElse(Diagnostic.NOPOS);
        }

        @Override
        public long getEndPosition(CompilationUnitTree currentUnit, Tree tree) {
          return compilation.sourceEvidence().origin(currentUnit, sources.find(currentUnit).orElseThrow(), tree)
              .map(SourceSpan::endOffsetExclusive).orElse(Diagnostic.NOPOS);
        }
      };
      var evidence = new JavaSourceEvidence(new MissingPositions(actualPositions, List.of(firstComponentTree)));

      // Act
      var translated = new JavaProgramTranslator(sources, compilation.semantics(), evidence, compilation.treeInventory(), JavaTranslationPolicy.legacyAnalysis()).translate(snapshot, compilation.units());

      // Assert
      assertTrue(translated.value().aggregate().isEmpty());
      assertTrue(translated.value().program().types().isEmpty());
      var diagnostic = translated.diagnostics().stream()
          .filter(item -> item.code().equals("frontend.unavailable-record-component")).findFirst().orElseThrow();
      assertEquals(List.of(evidence.origin(unit, sources.find(unit).orElseThrow(), declaration).orElseThrow()),
          diagnostic.origins());
      assertEquals(DiagnosticStage.FRONTEND, diagnostic.stage());
      assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity());
      assertEquals(AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR, diagnostic.outcomeKind());
    }
  }

  @Test
  void givenUncorrelatableComponentTree_whenTypeIsTranslated_thenDiagnosticRetainsRecordSourceRange() throws IOException {
    // Arrange
    withCompiledSource("record Example(int value) {}", fixture -> {
      var declaration = (ClassTree) fixture.unit().getTypeDecls().getFirst();
      var classPath = new TreePath(new TreePath(fixture.unit()), declaration);
      var owner = (TypeElement) fixture.semantics().element(classPath);
      var componentTree = fixture.semantics().sourceTree(owner.getRecordComponents().getFirst());
      var positions = new SourcePositions() {
        @Override
        public long getStartPosition(CompilationUnitTree unit, Tree tree) {
          return tree == componentTree ? Diagnostic.NOPOS : fixture.positions().getStartPosition(unit, tree);
        }

        @Override
        public long getEndPosition(CompilationUnitTree unit, Tree tree) {
          return fixture.positions().getEndPosition(unit, tree);
        }
      };
      var evidence = new JavaSourceEvidence(positions);
      var bodyTranslator = new JavaMethodBodyTranslator(fixture.semantics(), evidence, fixture.inventory(), JavaExecutableProjectionContext.metadata(JavaTranslationPolicy.legacyAnalysis()));
      var methodTranslator = new JavaMethodTranslator(fixture.semantics(), evidence, fixture.inventory(), bodyTranslator);
      var translator = new JavaTypeTranslator(fixture.semantics(), evidence, fixture.inventory(), methodTranslator, JavaTranslationPolicy.legacyAnalysis());

      // Act
      var result = translator.translate(fixture.unit(), new TreePath(fixture.unit()), fixture.sourceFile(), declaration);
      var diagnostic = result.diagnostics().stream().filter(item -> item.code().equals("frontend.unavailable-record-component"))
          .findFirst().orElseThrow();

      // Assert
      assertTrue(result.value().isEmpty());
      assertEquals(List.of(evidence.origin(fixture.unit(), fixture.sourceFile(), declaration).orElseThrow()), diagnostic.origins());
    });
  }

  @Test
  void givenExplicitEnclosedMemberWithoutWrittenTreeEvidence_whenReconciled_thenItIsDiagnosedRatherThanSynthesized()
      throws IOException {
    // Arrange
    withCompiledSource("class Example { int value; }", fixture -> {
      var declaration = (ClassTree) fixture.unit().getTypeDecls().getFirst();
      var classPath = new TreePath(new TreePath(fixture.unit()), declaration);
      var owner = (TypeElement) fixture.semantics().element(classPath);
      var field = owner.getEnclosedElements().stream().filter(member -> member.getKind() == ElementKind.FIELD)
          .findFirst().orElseThrow();
      var fieldKey = fixture.semantics().declarationKey(field).orElseThrow();
      var typeKey = fixture.semantics().declarationKey(owner).orElseThrow();
      var evidence = new JavaSourceEvidence(fixture.positions());
      var origin = evidence.origin(fixture.unit(), fixture.sourceFile(), declaration);
      var emptyInventory = ParsedTreeInventory.capture(List.of(), ignored -> Optional.empty(), evidence);
      var bodyTranslator = new JavaMethodBodyTranslator(fixture.semantics(), evidence, emptyInventory, JavaExecutableProjectionContext.metadata(JavaTranslationPolicy.legacyAnalysis()));
      var methodTranslator = new JavaMethodTranslator(fixture.semantics(), evidence, emptyInventory, bodyTranslator);
      var translator = new JavaTypeTranslator(fixture.semantics(), evidence, emptyInventory, methodTranslator, JavaTranslationPolicy.legacyAnalysis());
      var diagnostics = new DiagnosticEmissionCollector("source-observation", "diagnostics");

      // Act
      var synthesized = translator.reconcileEnclosedDeclarations(fixture.unit(), classPath, declaration, owner,
          fixture.sourceFile(), typeKey, origin, List.of(), List.of(), List.of(), List.of(), diagnostics);

      // Assert
      assertTrue(synthesized.synthesized().stream().noneMatch(member -> member.declarationKey().equals(fieldKey)));
      var diagnostic = diagnostics.snapshot().diagnostics().stream().filter(item -> item.code().equals("frontend.unrepresented-written-declaration"))
          .findFirst().orElseThrow();
      assertEquals("frontend.unrepresented-written-declaration", diagnostic.code());
      assertEquals(List.of(evidence.origin(fixture.unit(), fixture.sourceFile(), fixture.semantics().sourceTree(field))
          .orElseThrow()), diagnostic.origins());
    });
  }

  @Test
  void givenMissingParsedTypeInventory_whenReconcilingImplicitMethods_thenPublicationIsBlocked() throws IOException {
    // Arrange
    withCompiledSource("enum Example { ONE }", fixture -> {
      var declaration = (ClassTree) fixture.unit().getTypeDecls().getFirst();
      var classPath = new TreePath(new TreePath(fixture.unit()), declaration);
      var owner = (TypeElement) fixture.semantics().element(classPath);
      var implicitMethod = owner.getEnclosedElements().stream().filter(member -> member.getKind() == ElementKind.METHOD)
          .filter(member -> fixture.semantics().sourceTree(member) == null).findFirst().orElseThrow();
      assertEquals(Elements.Origin.EXPLICIT, fixture.elements().getOrigin(implicitMethod));
      var methodKey = fixture.semantics().declarationKey(implicitMethod).orElseThrow();
      var typeKey = fixture.semantics().declarationKey(owner).orElseThrow();
      var evidence = new JavaSourceEvidence(fixture.positions());
      var emptyInventory = ParsedTreeInventory.capture(List.of(), ignored -> Optional.empty(), evidence);
      var bodyTranslator = new JavaMethodBodyTranslator(fixture.semantics(), evidence, fixture.inventory(), JavaExecutableProjectionContext.metadata(JavaTranslationPolicy.legacyAnalysis()));
      var methodTranslator = new JavaMethodTranslator(fixture.semantics(), evidence, emptyInventory, bodyTranslator);
      var translator = new JavaTypeTranslator(fixture.semantics(), evidence, emptyInventory, methodTranslator, JavaTranslationPolicy.legacyAnalysis());
      var diagnostics = new DiagnosticEmissionCollector("source-observation", "diagnostics");

      // Act
      var synthesized = translator.reconcileEnclosedDeclarations(fixture.unit(), classPath, declaration, owner,
          fixture.sourceFile(), typeKey, evidence.origin(fixture.unit(), fixture.sourceFile(), declaration), List.of(), List.of(),
          List.of(), List.of(), diagnostics);
      var translated = translator.translate(fixture.unit(), new TreePath(fixture.unit()), fixture.sourceFile(), declaration);

      // Assert
      assertTrue(synthesized.synthesized().stream().noneMatch(member -> member.declarationKey().equals(methodKey)));
      assertTrue(translated.value().isEmpty());
      assertTrue(diagnostics.snapshot().diagnostics().stream().anyMatch(item -> item.code().equals("frontend.unrepresented-written-declaration")
          && item.origins().equals(evidence.origin(fixture.unit(), fixture.sourceFile(), declaration).stream().toList())));
    });
  }

  @Test
  void givenWrittenOverloadOfImplicitRecordMethod_whenTranslated_thenEachElementRetainsItsProvenance() throws IOException {
    // Arrange
    withCompiledSource("record Example(int value) { public boolean equals(String other) { return false; } }", fixture -> {
      var declaration = (ClassTree) fixture.unit().getTypeDecls().getFirst();
      var classPath = new TreePath(new TreePath(fixture.unit()), declaration);
      var owner = (TypeElement) fixture.semantics().element(classPath);
      var equalsMethods = owner.getEnclosedElements().stream().filter(member -> member.getKind() == ElementKind.METHOD)
          .filter(member -> member.getSimpleName().contentEquals("equals")).toList();
      assertEquals(2, equalsMethods.size());
      var writtenMethod = equalsMethods.stream().filter(member -> fixture.semantics().sourceTree(member) != null)
          .findFirst().orElseThrow();
      var implicitMethod = equalsMethods.stream().filter(member -> fixture.semantics().sourceTree(member) == null)
          .findFirst().orElseThrow();
      var evidence = new JavaSourceEvidence(fixture.positions());
      var bodyTranslator = new JavaMethodBodyTranslator(fixture.semantics(), evidence, fixture.inventory(), JavaExecutableProjectionContext.metadata(JavaTranslationPolicy.legacyAnalysis()));
      var methodTranslator = new JavaMethodTranslator(fixture.semantics(), evidence, fixture.inventory(), bodyTranslator);
      var translator = new JavaTypeTranslator(fixture.semantics(), evidence, fixture.inventory(), methodTranslator, JavaTranslationPolicy.legacyAnalysis());

      // Act
      var result = translator.translate(fixture.unit(), new TreePath(fixture.unit()), fixture.sourceFile(), declaration);
      var type = result.value().orElseThrow().types().getFirst();

      // Assert
      assertTrue(type.methods().stream().anyMatch(method -> method.methodKey()
          .equals(fixture.semantics().declarationKey(writtenMethod).orElseThrow()) && method.sourceDeclared()));
      assertTrue(type.synthesizedDeclarations().stream().anyMatch(member -> member.declarationKey()
          .equals(fixture.semantics().declarationKey(implicitMethod).orElseThrow())));
      assertTrue(result.diagnostics().stream().noneMatch(item -> item.code().equals("frontend.unrepresented-written-declaration")));
    });
  }

  @Test
  void givenMissingFirstPackageAnnotationRange_whenTranslated_thenLaterAttachmentKeepsItsPublishedPosition() throws IOException {
    // Arrange
    withCompiledSource("@Deprecated @SuppressWarnings(\"unused\") package sample;", "package-info.java", fixture -> {
      var annotations = fixture.unit().getPackage().getAnnotations();
      var evidence = new JavaSourceEvidence(new MissingPositions(fixture.positions(), List.of(annotations.getFirst())));

      // Act
      var result = new UnitSourceFixture(fixture.semantics(), evidence, fixture.inventory(), JavaTranslationPolicy.legacyAnalysis())
          .translate(fixture.unit(), fixture.sourceFile(), List.of(), fixture.types());
      var root = result.contribution().orElseThrow().root();
      var packageNode = root.requiredChild(SyntaxRole.PACKAGE_DECLARATION);
      var retained = packageNode.children(SyntaxRole.ANNOTATION);

      // Assert
      assertEquals(1, retained.size());
      assertEquals(0, packageNode.children().getFirst().position());
      assertEquals(packageNode.structuralPath().child(SyntaxRole.ANNOTATION, 0), retained.getFirst().structuralPath());
      assertEquals(List.of("frontend.missing-source-position"),
          result.diagnostics().stream().map(AnalysisDiagnostic::code).toList());
      assertEquals(AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR, result.diagnostics().getFirst().outcomeKind());
    });
  }

  @Test
  void givenMissingPackageAndImportRanges_whenTranslated_thenBothFactsAreDiagnosedWithoutPublication() throws IOException {
    // Arrange
    withCompiledSource("package sample; import java.util.List; class Example { }", fixture -> {
      var missing = List.of(fixture.unit().getPackage(), fixture.unit().getImports().getFirst());
      var evidence = new JavaSourceEvidence(new MissingPositions(fixture.positions(), missing));

      // Act
      var result = new UnitSourceFixture(fixture.semantics(), evidence, fixture.inventory(), JavaTranslationPolicy.legacyAnalysis())
          .translate(fixture.unit(), fixture.sourceFile(), List.of(), fixture.types());
      var root = result.contribution().orElseThrow().root();

      // Assert
      assertEquals(List.of(), root.children(SyntaxRole.PACKAGE_DECLARATION));
      assertEquals(List.of(), root.children(SyntaxRole.IMPORT_DECLARATION));
      assertEquals(List.of("frontend.missing-source-position", "frontend.missing-source-position"),
          result.diagnostics().stream().map(AnalysisDiagnostic::code).toList());
      assertNotEquals(result.diagnostics().get(0).diagnosticKey(), result.diagnostics().get(1).diagnosticKey());
      assertTrue(result.diagnostics().stream().allMatch(diagnostic -> diagnostic.outcomeKind() == AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR));
    });
  }

  @Test
  void givenTwoMissingImportRanges_whenTranslated_thenDiagnosticsRetainSourceOrdinals() throws IOException {
    // Arrange
    withCompiledSource("import java.util.List; import java.util.Map; class Example { }", fixture -> {
      var evidence = new JavaSourceEvidence(new MissingPositions(fixture.positions(), fixture.unit().getImports()));

      // Act
      var result = new UnitSourceFixture(fixture.semantics(), evidence, fixture.inventory(), JavaTranslationPolicy.legacyAnalysis())
          .translate(fixture.unit(), fixture.sourceFile(), List.of(), fixture.types());

      // Assert
      assertEquals(List.of(), result.contribution().orElseThrow().root().children(SyntaxRole.IMPORT_DECLARATION));
      assertEquals(List.of("frontend.missing-source-position", "frontend.missing-source-position"),
          result.diagnostics().stream().map(AnalysisDiagnostic::code).toList());
      assertNotEquals(result.diagnostics().get(0).diagnosticKey(), result.diagnostics().get(1).diagnosticKey());
      assertNotEquals(result.diagnostics().get(0).message(), result.diagnostics().get(1).message());
    });
  }

  @Test
  void givenMissingCompactMethodRange_whenTranslated_thenInvalidRootIsNotPublished() throws IOException {
    // Arrange
    withCompiledSource("void main() { }", fixture -> {
      var wrapper = (ClassTree) fixture.unit().getTypeDecls().getFirst();
      var method = wrapper.getMembers().stream().filter(MethodTree.class::isInstance).map(MethodTree.class::cast)
          .filter(member -> member.getName().contentEquals("main")).findFirst().orElseThrow();
      var positions = new SourcePositions() {
        @Override
        public long getStartPosition(CompilationUnitTree unit, Tree tree) {
          return fixture.positions().getStartPosition(unit, tree);
        }

        @Override
        public long getEndPosition(CompilationUnitTree unit, Tree tree) {
          return tree == method ? Diagnostic.NOPOS : fixture.positions().getEndPosition(unit, tree);
        }
      };
      var evidence = new JavaSourceEvidence(positions);

      // Act
      var result = new UnitSourceFixture(fixture.semantics(), evidence, fixture.inventory(), JavaTranslationPolicy.legacyAnalysis())
          .translate(fixture.unit(), fixture.sourceFile(), List.of(), fixture.types());

      // Assert
      assertTrue(result.contribution().isEmpty());
      assertEquals(List.of("frontend.missing-source-position", "frontend.unavailable-compact-method"),
          result.diagnostics().stream().map(AnalysisDiagnostic::code).toList());
    });
  }

  @Test
  void givenMissingModuleOrLaterDirectiveRange_whenAnalyzed_thenFrontendErrorBlocksCompletion() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("module-info.java"), """
        module sample.module {
          requires java.sql;
          requires java.logging;
        }
        """);
    Files.createDirectory(sourceRoot.resolve("sample"));
    Files.writeString(sourceRoot.resolve("sample/Example.java"),
        "package sample; public class Example { public Example() { } }");
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var loader = new JavaSourceLoader();
    var snapshot = loader.discover(sourceRoot);
    var baseline = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, configuration, JavaTranslationPolicy.legacyAnalysis());
    var sources = new SnapshotSourceCatalog(snapshot);
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(baseline).completion());
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(configuration), sources)) {
      var parsed = session.parse();
      var compilation = session.analyze(parsed, JavaTranslationPolicy.legacyAnalysis());
      var unit = compilation.units().stream().filter(candidate -> candidate.getModule() != null).findFirst().orElseThrow();
      var module = unit.getModule();
      var writtenDirectives = module.getDirectives().stream().filter(compilation.treeInventory()::isWritten).toList();
      assertEquals(2, writtenDirectives.size());
      var actualPositions = new SourcePositions() {
        @Override
        public long getStartPosition(CompilationUnitTree currentUnit, Tree tree) {
          return compilation.sourceEvidence().origin(currentUnit, sources.find(currentUnit).orElseThrow(), tree)
              .map(SourceSpan::startOffset).orElse(Diagnostic.NOPOS);
        }

        @Override
        public long getEndPosition(CompilationUnitTree currentUnit, Tree tree) {
          return compilation.sourceEvidence().origin(currentUnit, sources.find(currentUnit).orElseThrow(), tree)
              .map(SourceSpan::endOffsetExclusive).orElse(Diagnostic.NOPOS);
        }
      };
      var cases = List.of(
          new MissingModulePosition(module, "source-file-root:0:module-declaration:0"),
          new MissingModulePosition(writtenDirectives.get(1),
              "source-file-root:0:module-declaration:0:module-directive:1"));

      // Act
      for (var position : cases) {
        var evidence = new JavaSourceEvidence(new MissingPositions(actualPositions, List.of(position.tree())));
        var translated = new JavaProgramTranslator(sources, compilation.semantics(), evidence, compilation.treeInventory(), JavaTranslationPolicy.legacyAnalysis()).translate(snapshot, compilation.units());
        var input = JavaCoverageTestSupport.withFacts(baseline, translated.value().program(),
            translated.value().aggregate(), translated.emissions().events());
        var run = new SourceAnalysisFixture().analyze(input);

        // Assert
        assertTrue(input.aggregate().isEmpty());
        assertTrue(input.program().types().isEmpty());
        assertTrue(run.completion() != AnalysisCompletion.COMPLETE);
        assertEquals(1, translated.diagnostics().size());
        var diagnostic = translated.diagnostics().getFirst();
        assertEquals("frontend.missing-source-position", diagnostic.code());
        assertEquals(DiagnosticStage.FRONTEND, diagnostic.stage());
        assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity());
        assertEquals(AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR, diagnostic.outcomeKind());
        assertTrue(diagnostic.origins().isEmpty());
        assertTrue(diagnostic.message().contains("module-info.java at " + position.path()));
      }
    }
  }

  private static void withCompiledSource(String content, Consumer<CompiledSource> assertion) throws IOException {
    withCompiledSource(content, "Example.java", assertion);
  }

  private static String memberTuple(String source, SyntaxNode node) {
    var range = node.origin().orElseThrow();
    return node.syntaxKind() + "|" + node.sourceKind() + "|"
        + source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive()));
  }

  private static void withCompiledSource(String content, String filename, Consumer<CompiledSource> assertion) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var sourceFile = new SourceFile("source", Path.of(filename), "fixture-content", content);
    var source = new SimpleJavaFileObject(URI.create("string:///" + filename), JavaFileObject.Kind.SOURCE) {
      @Override
      public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return content;
      }
    };
    try (var fileManager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      var task = (JavacTask) compiler.getTask(null, fileManager, null, List.of("-proc:none", "--release", "25"), null,
          List.of(source));
      var unit = task.parse().iterator().next();
      var inventory = ParsedTreeInventory.capture(List.of(unit), ignored -> Optional.of(sourceFile),
          new JavaSourceEvidence(Trees.instance(task).getSourcePositions()));
      task.analyze();
      var trees = Trees.instance(task);
      var semantics = new SemanticResolver(trees, task.getTypes(), task.getElements(),
          new DeclarationIdentityRegistry(trees, task.getTypes(), task.getElements(), JavaTranslationPolicy.legacyAnalysis()));
      var evidence = new JavaSourceEvidence(trees.getSourcePositions());
      var policy = JavaTranslationPolicy.legacyAnalysis();
      var bodies = new JavaMethodBodyTranslator(semantics, evidence, inventory, JavaExecutableProjectionContext.metadata(policy));
      var types = new JavaTypeTranslator(semantics, evidence, inventory, new JavaMethodTranslator(semantics, evidence, inventory, bodies), policy);
      var metadata = new ArrayList<JavaType>();
      for (var declaration : unit.getTypeDecls()) {
        if (declaration instanceof ClassTree type) {
          metadata.addAll(types.translate(unit, new TreePath(unit), sourceFile, type).value().orElseThrow().types());
        }
      }
      assertion.accept(new CompiledSource(unit, sourceFile, semantics, trees.getSourcePositions(), inventory, task.getElements(), List.copyOf(metadata)));
    }
  }

  private record CompiledSource(CompilationUnitTree unit, SourceFile sourceFile, SemanticResolver semantics,
      SourcePositions positions, ParsedTreeInventory inventory, Elements elements, List<JavaType> types) {
  }

  private static final class MissingPositions implements SourcePositions {
    private final SourcePositions delegate;
    private final Set<Tree> missing;

    private MissingPositions(SourcePositions delegate, List<? extends Tree> missingTrees) {
      this.delegate = delegate;
      missing = Collections.newSetFromMap(new IdentityHashMap<Tree, Boolean>());
      missing.addAll(missingTrees);
    }

    @Override
    public long getStartPosition(CompilationUnitTree unit, Tree tree) {
      return missing.contains(tree) ? Diagnostic.NOPOS : delegate.getStartPosition(unit, tree);
    }

    @Override
    public long getEndPosition(CompilationUnitTree unit, Tree tree) {
      return missing.contains(tree) ? Diagnostic.NOPOS : delegate.getEndPosition(unit, tree);
    }
  }

  private record PositionCase(long start, long end, String code) {
  }

  private record MissingModulePosition(Tree tree, String path) {
  }

  private record FixedPositions(long start, long end) implements SourcePositions {
    @Override
    public long getStartPosition(CompilationUnitTree unit, Tree tree) {
      return start;
    }

    @Override
    public long getEndPosition(CompilationUnitTree unit, Tree tree) {
      return end;
    }
  }
}
