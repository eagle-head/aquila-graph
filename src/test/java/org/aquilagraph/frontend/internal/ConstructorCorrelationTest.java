package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.NewClassTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.aquilagraph.frontend.internal.ConstructorCorrelation.Status;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class ConstructorCorrelationTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final AstProfile PROFILE = AstProfile.JAVA_SOURCE_MODULE_SERVICES;
  private static final String SINGLE_CANDIDATE = "compiler.err.cant.apply.symbol";
  private static final String MULTIPLE_CANDIDATES = "compiler.err.cant.apply.symbols";
  private static final String BEFORE = "/* before */";
  private static final String AFTER = "/* after */";
  private static final String FIXED_SOURCE = """
      class Selection { <T> Selection(T value) {} }
      class Probe { Selection create() { return /* before */new Selection("text")/* after */; } }
      """;
  private static final String VARARGS_SOURCE = """
      class Selection { <T extends Number> Selection(T... values) {} }
      class Probe { Selection create() { return /* before */new Selection(null)/* after */; } }
      """;

  @TempDir Path directory;

  @Test
  void givenEqualLookingCorrelatedCreationsAndAnOrdinaryCreation_whenQueried_thenEachOccurrenceKeepsItsOwnResult() throws IOException {
    // Arrange
    var source = """
        class Selection { <T> Selection(T value) {} }
        class Ordinary { Ordinary() {} }
        class Probe {
          Object text(String value) { return new Selection(value); }
          Object number(int value) { return new Selection(value); }
          Object ordinary() { return new Ordinary(); }
        }
        """;
    var compiler = new WitnessCompiler(Optional.empty(), Optional.empty());
    try (var session = open(compiler, source);
        var foreignSession = open(ToolProvider.getSystemJavaCompiler(), source)) {
      var compilation = session.analyze(session.parse(), JavaTranslationPolicy.forProfile(PROFILE));
      var foreign = foreignSession.analyze(foreignSession.parse(), JavaTranslationPolicy.forProfile(PROFILE));
      var creations = creations(compilation);
      var text = creations.getFirst();
      var number = creations.get(1);
      var ordinary = creations.getLast();
      var foreignText = creations(foreign).getFirst();
      var semantics = compilation.semantics();
      var selected = selected(semantics, text);
      var ordinarySelected = selected(semantics, ordinary);

      // Act
      var textCorrelation = semantics.selectedConstructor(text, selected);
      var numberCorrelation = semantics.selectedConstructor(number, selected);
      var absent = semantics.selectedConstructor(ordinary, ordinarySelected);
      var mismatch = assertThrows(IllegalArgumentException.class, () -> semantics.selectedConstructor(text, ordinarySelected));
      var foreignMismatch = assertThrows(IllegalArgumentException.class, () -> semantics.selectedConstructor(foreignText, selected));
      var repeated = semantics.selectedConstructor(text, selected);

      // Assert
      assertEquals(3, creations.size());
      assertEquals(List.of(), compilation.diagnostics());
      assertEquals(text.getLeaf().toString(), number.getLeaf().toString());
      assertEquals(text.getLeaf().toString(), foreignText.getLeaf().toString());
      assertEquals(Status.SELECTED, textCorrelation.status());
      assertEquals(Status.SELECTED, numberCorrelation.status());
      assertEquals("java.lang.String", textCorrelation.facts().orElseThrow().arguments().getFirst().selectedTarget().displayName());
      assertEquals("java.lang.Integer", numberCorrelation.facts().orElseThrow().arguments().getFirst().selectedTarget().displayName());
      assertEquals(Status.UNAVAILABLE, absent.status());
      assertTrue(absent.facts().isEmpty());
      assertEquals(textCorrelation, repeated);
      assertEquals("Constructor selection requires its exact original attributed element", mismatch.getMessage());
      assertEquals(mismatch.getMessage(), foreignMismatch.getMessage());
      assertEquals(List.of(true, true), compiler.completedAttributions());
    }
  }

  @Test
  void givenOriginalSelectionAndACompiledWitnessWithoutItsCall_whenQueried_thenCorrelationIsUnavailable() throws IOException {
    // Arrange
    var compiler = new WitnessCompiler(Optional.empty(), Optional.of("class PrivateProof {}"));

    // Act
    var correlation = correlate(compiler, FIXED_SOURCE);

    // Assert
    assertEquals(Status.UNAVAILABLE, correlation.status());
    assertTrue(correlation.facts().isEmpty());
    assertEquals(List.of(true), compiler.completedAttributions());
    assertTrue(compiler.witnessErrors.isEmpty());
  }

  @Test
  void givenOriginalSelectionAndActualWitnessAttributionError_whenQueried_thenRejectedCorrelationHasNoFacts() throws IOException {
    // Arrange
    var compiler = new WitnessCompiler(Optional.empty(), Optional.of("class PrivateProof { MissingType value; }"));

    // Act
    var correlation = correlate(compiler, FIXED_SOURCE);

    // Assert
    assertEquals(Status.WITNESS_REJECTED, correlation.status());
    assertTrue(correlation.facts().isEmpty());
    assertEquals(1, compiler.attempts.size());
    assertEquals(1, compiler.witnessErrors.size());
    var error = compiler.witnessErrors.getFirst();
    assertEquals(URI.create("string:///PrivateProof.java"), error.getSource().toUri());
    assertTrue(error.getMessage(Locale.ROOT).contains("MissingType"));
  }

  @TestFactory
  Stream<DynamicTest> givenFixedWitnessDiagnostics_whenCorrelated_thenClassificationRequiresCodeSourceAndPosition() {
    // Arrange
    var cases = List.of(
        new DiagnosticCase("MatchingCodeAtStart", SINGLE_CANDIDATE, true, Position.START, Status.INAPPLICABLE),
        new DiagnosticCase("MatchingCodeAtLastCharacter", MULTIPLE_CANDIDATES, true, Position.LAST_CHARACTER, Status.INAPPLICABLE),
        new DiagnosticCase("DifferentCodeAtStart", "compiler.err.cant.resolve", true, Position.START, Status.WITNESS_REJECTED),
        new DiagnosticCase("ForeignSourceAtStart", SINGLE_CANDIDATE, false, Position.START, Status.WITNESS_REJECTED),
        new DiagnosticCase("MatchingCodeBeforeStart", SINGLE_CANDIDATE, true, Position.BEFORE_START, Status.WITNESS_REJECTED),
        new DiagnosticCase("MatchingCodeAtEnd", SINGLE_CANDIDATE, true, Position.END, Status.WITNESS_REJECTED));

    // Act
    var tests = cases.stream().map(example -> DynamicTest.dynamicTest(
        "given" + example.name() + "_whenFixedWitnessIsCorrelated_then" + example.expected(), () -> {
          // Arrange
          var compiler = new WitnessCompiler(Optional.of(example), Optional.empty());

          // Act
          var correlation = correlate(compiler, FIXED_SOURCE);

          // Assert
          assertEquals(example.expected(), correlation.status());
          assertTrue(correlation.facts().isEmpty());
          assertEquals(List.of(true), compiler.completedAttributions());
        }));

    // Assert
    return tests;
  }

  @Test
  void givenInapplicableFixedWitnessAndTypedArgument_whenProduced_thenMissingCorrelationUsesOriginalOccurrence() {
    // Arrange
    var compiler = new WitnessCompiler(Optional.of(inapplicableAtStart()), Optional.empty());
    var frontend = new JavaFrontend(new JavaSourceLoader(), new JavaAnalysisPipeline(
        new AnalysisEnvironmentCapture(new CompilerDependencyCapture()), () -> compiler));
    var snapshot = snapshot(FIXED_SOURCE);

    // Act
    var outcome = frontend.produce(new AstProductionRequest(snapshot, CONFIGURATION, PROFILE));

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, outcome.completion(), outcome.diagnostics().toString());
    assertEquals(List.of("frontend.missing-constructor-correlation"), outcome.diagnostics().stream().map(value -> value.code()).toList());
    var diagnostic = outcome.diagnostics().getFirst();
    assertEquals(DiagnosticStage.FRONTEND, diagnostic.stage());
    assertTrue(diagnostic.isError());
    var origin = diagnostic.origins().getFirst();
    assertEquals(1, diagnostic.origins().size());
    assertEquals(snapshot.sourceFiles().getFirst().sourceFileKey(), origin.sourceFileKey());
    assertEquals("new Selection(\"text\")", FIXED_SOURCE.substring(Math.toIntExact(origin.startOffset()),
        Math.toIntExact(origin.endOffsetExclusive())));
    assertEquals(List.of(true), compiler.completedAttributions());
  }

  @Test
  void givenFixedVarargsWitnessAndControlledInapplicability_whenCorrelated_thenExpandedAttemptSuppliesExactFacts() throws IOException {
    // Arrange
    var ordinary = new WitnessCompiler(Optional.empty(), Optional.empty());
    var inapplicable = new WitnessCompiler(Optional.of(inapplicableAtStart()), Optional.empty());

    // Act
    var fixed = correlate(ordinary, VARARGS_SOURCE);
    var variable = correlate(inapplicable, VARARGS_SOURCE);

    // Assert
    assertEquals(Status.SELECTED, fixed.status());
    assertEquals(InvocationApplicabilityMode.FIXED, fixed.facts().orElseThrow().mode());
    assertEquals(List.of(true), ordinary.completedAttributions());
    assertEquals(Status.SELECTED, variable.status());
    var facts = variable.facts().orElseThrow();
    assertEquals(InvocationApplicabilityMode.VARIABLE, facts.mode());
    assertEquals(1, facts.formalCount());
    assertEquals("java.lang.Number[]", facts.varargArrayType().orElseThrow().displayName());
    assertEquals("java.lang.Number", facts.arguments().getFirst().selectedTarget().displayName());
    assertEquals(List.of(true, true), inapplicable.completedAttributions());
  }

  @Test
  void givenZeroArgumentsAndOneGenericVarargsFormal_whenCorrelated_thenOnlyExpandedAttributionSuppliesExactArray() throws IOException {
    // Arrange
    var source = VARARGS_SOURCE.replace("new Selection(null)", "new Selection()");
    var compiler = new WitnessCompiler(Optional.empty(), Optional.empty());

    // Act
    var correlation = correlate(compiler, source);

    // Assert
    assertEquals(Status.SELECTED, correlation.status());
    var facts = correlation.facts().orElseThrow();
    assertEquals(InvocationApplicabilityMode.VARIABLE, facts.mode());
    assertEquals(1, facts.formalCount());
    assertTrue(facts.arguments().isEmpty());
    assertEquals("java.lang.Number[]", facts.varargArrayType().orElseThrow().displayName());
    assertEquals(List.of(true), compiler.completedAttributions());
  }

  private static DiagnosticCase inapplicableAtStart() {
    return new DiagnosticCase("MatchingCodeAtStart", SINGLE_CANDIDATE, true, Position.START, Status.INAPPLICABLE);
  }

  private ConstructorCorrelation correlate(JavaCompiler compiler, String source) throws IOException {
    try (var session = open(compiler, source)) {
      var compilation = session.analyze(session.parse(), JavaTranslationPolicy.forProfile(PROFILE));
      assertTrue(compilation.diagnostics().stream().noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR),
          compilation.diagnostics().toString());
      var path = creations(compilation).getFirst();
      return compilation.semantics().selectedConstructor(path, selected(compilation.semantics(), path));
    }
  }

  private CompilerSession open(JavaCompiler compiler, String source) throws IOException {
    return CompilerSessionAcquisition.open(compiler, JavaCompilerInvocation.from(CONFIGURATION),
        new SnapshotSourceCatalog(snapshot(source)));
  }

  private InputSnapshot snapshot(String source) {
    return InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Probe.java"), source)));
  }

  private static ExecutableElement selected(SemanticResolver semantics, TreePath path) {
    return assertInstanceOf(ExecutableElement.class, semantics.element(path));
  }

  private static List<TreePath> creations(JavaCompilation compilation) {
    var paths = new ArrayList<TreePath>();
    var scanner = new TreePathScanner<Void, Void>() {
      @Override
      public Void visitNewClass(NewClassTree creation, Void ignored) {
        paths.add(getCurrentPath());
        return super.visitNewClass(creation, ignored);
      }
    };
    compilation.units().forEach(unit -> scanner.scan(unit, null));
    return paths;
  }

  private enum Position {
    BEFORE_START, START, LAST_CHARACTER, END
  }

  private record DiagnosticCase(String name, String code, boolean sameSource, Position position, Status expected) {
    WitnessDiagnostic at(JavaFileObject input) {
      var content = read(input);
      var before = content.indexOf(BEFORE);
      var start = before + BEFORE.length();
      var end = content.indexOf(AFTER);
      assertTrue(before > FIXED_SOURCE.indexOf(BEFORE));
      assertTrue(end > start);
      var offset = switch (position) {
        case BEFORE_START -> start - 1;
        case START -> start;
        case LAST_CHARACTER -> end - 1;
        case END -> end;
      };
      var source = sameSource ? input : SimpleJavaFileObject.forSource(URI.create("string:///Different.java"), content);
      return new WitnessDiagnostic(code, source, offset);
    }

    private static String read(JavaFileObject input) {
      try {
        return input.getCharContent(true).toString();
      } catch (IOException failure) {
        throw new UncheckedIOException(failure);
      }
    }
  }

  private record WitnessDiagnostic(String code, JavaFileObject source, long position) implements Diagnostic<JavaFileObject> {
    @Override public Kind getKind() { return Kind.ERROR; }
    @Override public JavaFileObject getSource() { return source; }
    @Override public long getPosition() { return position; }
    @Override public long getStartPosition() { return position; }
    @Override public long getEndPosition() { return position + 1; }
    @Override public long getLineNumber() { return NOPOS; }
    @Override public long getColumnNumber() { return NOPOS; }
    @Override public String getCode() { return code; }
    @Override public String getMessage(Locale locale) { return "Controlled constructor witness diagnostic"; }
  }

  private static final class WitnessCompiler implements JavaCompiler {
    private final JavaCompiler delegate = Objects.requireNonNull(ToolProvider.getSystemJavaCompiler());
    private final Optional<DiagnosticCase> firstDiagnostic;
    private final Optional<String> replacementSource;
    private final List<WitnessAttempt> attempts = new ArrayList<>();
    private final List<Diagnostic<? extends JavaFileObject>> witnessErrors = new ArrayList<>();
    private boolean originalCreated;

    WitnessCompiler(Optional<DiagnosticCase> firstDiagnostic, Optional<String> replacementSource) {
      this.firstDiagnostic = firstDiagnostic;
      this.replacementSource = replacementSource;
    }

    List<Boolean> completedAttributions() {
      return attempts.stream().map(attempt -> attempt.attributed).toList();
    }

    @Override
    public CompilationTask getTask(@Nullable Writer output, @Nullable JavaFileManager fileManager,
        @Nullable DiagnosticListener<? super JavaFileObject> listener, @Nullable Iterable<String> options,
        @Nullable Iterable<String> classes, @Nullable Iterable<? extends JavaFileObject> units) {
      if (!originalCreated) {
        originalCreated = true;
        return delegate.getTask(output, fileManager, listener, options, classes, units);
      }
      var selectedDiagnostic = attempts.isEmpty() ? firstDiagnostic.map(value -> value.at(Objects.requireNonNull(units).iterator().next()))
          : Optional.<WitnessDiagnostic>empty();
      var witnessUnits = replacementSource.<Iterable<? extends JavaFileObject>>map(source ->
          List.of(SimpleJavaFileObject.forSource(URI.create("string:///PrivateProof.java"), source))).orElse(units);
      var compilerListener = Objects.requireNonNull(listener);
      var attempt = new WitnessAttempt(compilerListener, selectedDiagnostic, replacementSource.isPresent() ? "PrivateProof" : "Probe");
      attempts.add(attempt);
      var task = (JavacTask) delegate.getTask(output, fileManager, diagnostic -> {
        if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
          witnessErrors.add(diagnostic);
        }
        compilerListener.report(diagnostic);
      }, options, classes, witnessUnits);
      task.addTaskListener(attempt);
      return task;
    }

    @Override
    public StandardJavaFileManager getStandardFileManager(@Nullable DiagnosticListener<? super JavaFileObject> listener,
        @Nullable Locale locale, @Nullable Charset charset) {
      return delegate.getStandardFileManager(listener, locale, charset);
    }

    @Override
    public int run(@Nullable InputStream input, @Nullable OutputStream output, @Nullable OutputStream error, String... arguments) {
      return delegate.run(input, output, error, arguments);
    }

    @Override
    public Set<SourceVersion> getSourceVersions() {
      return delegate.getSourceVersions();
    }

    @Override
    public int isSupportedOption(String option) {
      return delegate.isSupportedOption(option);
    }
  }

  private static final class WitnessAttempt implements TaskListener {
    private final DiagnosticListener<? super JavaFileObject> listener;
    private final Optional<WitnessDiagnostic> diagnostic;
    private final String attributedType;
    private boolean attributed;

    WitnessAttempt(DiagnosticListener<? super JavaFileObject> listener, Optional<WitnessDiagnostic> diagnostic, String attributedType) {
      this.listener = listener;
      this.diagnostic = diagnostic;
      this.attributedType = attributedType;
    }

    @Override
    public void finished(TaskEvent event) {
      var type = event.getTypeElement();
      if (!attributed && event.getKind() == TaskEvent.Kind.ANALYZE && type != null
          && type.getQualifiedName().contentEquals(attributedType)) {
        attributed = true;
        diagnostic.ifPresent(listener::report);
      }
    }
  }
}
