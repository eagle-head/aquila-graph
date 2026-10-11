package org.aquilagraph.frontend;

import java.io.IOException;
import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.List;
import java.util.Objects;
import javax.tools.Diagnostic;
import java.util.Optional;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
import org.aquilagraph.source.api.AnalysisInputException;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.source.api.AttributedCoverageExpectations;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.admission.AstResultAdmission;
import org.jspecify.annotations.Nullable;

final class JavaAnalysisPipeline {
  private final AnalysisEnvironmentCapture environmentCapture;
  private final Supplier<@Nullable JavaCompiler> compilerSupplier;

  JavaAnalysisPipeline(AnalysisEnvironmentCapture environmentCapture) {
    this(environmentCapture, ToolProvider::getSystemJavaCompiler);
  }

  JavaAnalysisPipeline(AnalysisEnvironmentCapture environmentCapture, Supplier<@Nullable JavaCompiler> compilerSupplier) {
    this.environmentCapture = Objects.requireNonNull(environmentCapture, "environmentCapture");
    this.compilerSupplier = Objects.requireNonNull(compilerSupplier, "compilerSupplier");
  }

  AstOutcome produce(InputSnapshot snapshot, CompilerConfiguration configuration, AstProfile profile, String semanticRevision)
      throws IOException {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(profile, "profile");
    if (snapshot.sourceFiles().isEmpty()) {
      var contract = JavaLanguageFeatureCoverage.fromConfiguration(configuration);
      var written = JavaSourceCoverageCapture.empty(snapshot, contract);
      var coverage = LanguageFeatureCoverageResult.fromEvidence(contract, written, new AttributedCoverageExpectations(written, List.of()),
          List.of(), List.of(), List.of());
      return AstResultAdmission.empty(snapshot, configuration, profile, coverage);
    }
    return AstResultAdmission.outcome(analyze(snapshot, configuration,
        profile.equals(AstProfile.JAVA_SOURCE_INITIALIZATION) || profile.equals(AstProfile.JAVA_SOURCE_NESTED),
        profile.equals(AstProfile.JAVA_SOURCE_NESTED)), profile, semanticRevision);
  }

  SyntaxAnalysisInput analyze(InputSnapshot snapshot, CompilerConfiguration configuration) throws IOException {
    return analyze(snapshot, configuration, false, false);
  }

  private SyntaxAnalysisInput analyze(InputSnapshot snapshot, CompilerConfiguration configuration, boolean memberInitialization,
      boolean nestedDeclarations) throws IOException {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(configuration, "configuration");
    var compiler = systemCompiler(configuration);
    var invocation = JavaCompilerInvocation.from(configuration);
    var environment = environmentCapture.capture(compiler, configuration, invocation.analysisOptions());
    var languageFeatureCoverage = JavaLanguageFeatureCoverage.fromConfiguration(configuration);
    if (snapshot.sourceFiles().isEmpty()) {
      var written = JavaSourceCoverageCapture.empty(snapshot, languageFeatureCoverage);
      var coverageResult = LanguageFeatureCoverageResult.fromEvidence(languageFeatureCoverage, written,
          new AttributedCoverageExpectations(written, List.of()), List.of(), List.of(), List.of());
      return new SyntaxAnalysisInput(snapshot, environment, new JavaProgram(List.of()),
          Optional.of(AstValueAdmission.admit(CopiedAstValues.empty(snapshot))), List.of(), coverageResult);
    }

    var sources = new SnapshotSourceCatalog(snapshot);
    SyntaxAnalysisInput result;
    try (var session = openCompilerSession(compiler, invocation, sources)) {
      var parsedSource = session.parse();
      var coverageCapture = JavaSourceCoverageCapture.capture(snapshot, parsedSource, sources, languageFeatureCoverage);
      var propertyProducer = new JavaDeclarationPropertyEncounterProducer(parsedSource.units(), sources, parsedSource.sourceEvidence(),
          languageFeatureCoverage);
      var writtenProperties = propertyProducer.observeWritten();
      var annotationInventory = JavaAnnotationSourceInventory.capture(parsedSource, sources);
      var kindEvidence = new JavaCompilerKindDiagnostics(sources, parsedSource.sourceEvidence(), languageFeatureCoverage)
          .observe(parsedSource.units());
      var compilation = session.analyze(parsedSource, nestedDeclarations);
      var attributedExpectations = coverageCapture.captureAttributed(compilation.semantics());
      var attributedProperties = propertyProducer.observeAttributed(compilation.semantics());
      var typeEvidence = new JavaTypeKindObserver(sources, compilation.semantics(), compilation.sourceEvidence(),
          compilation.treeInventory(), languageFeatureCoverage).observe(compilation.units());
      var translation = new JavaProgramTranslator(sources, compilation.semantics(), compilation.sourceEvidence(),
          compilation.treeInventory(), languageFeatureCoverage, memberInitialization, nestedDeclarations).translate(snapshot,
              compilation.units(),
              compilation.diagnostics().stream().anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
      var annotationCompleteness = new JavaAnnotationCompleteness(annotationInventory, compilation.semantics(), translation.diagnostics());
      var typeUseEvents = JavaTypeUseCompleteness.diagnosticsWithEvents(snapshot, kindEvidence.observations(),
          translation.value().aggregate());
      var emissions = combineEmissions(new CompilerDiagnosticTranslator(sources).translateWithEvents(compilation.diagnostics()),
          TranslationDiagnosticEvidence.events(kindEvidence.diagnostics()),
          TranslationDiagnosticEvidence.events(typeEvidence.diagnostics()), translation.diagnosticEmissions(), typeUseEvents,
          annotationCompleteness.reconcileWithEvents(translation.value().aggregate()));
      var reconciled = new JavaSourceCoverageReconciler(coverageCapture.inventory(), attributedExpectations, translation.value().program(),
          translation.value().aggregate(), emissions)
          .reconcile(languageFeatureCoverage, kindEvidence, typeEvidence, translation, writtenProperties, attributedProperties);
      result = new SyntaxAnalysisInput(snapshot, environment, translation.value().program(), translation.value().aggregate(),
          reconciled.diagnostics(), reconciled.coverageResult());
    }

    environmentCapture.verifyUnchanged(compiler, configuration, invocation.analysisOptions(), environment);
    return result;
  }

  private static CompilerSession openCompilerSession(JavaCompiler compiler, JavaCompilerInvocation invocation,
      SnapshotSourceCatalog sources) throws IOException {
    try {
      return CompilerSessionAcquisition.open(compiler, invocation, sources);
    } catch (IllegalArgumentException exception) {
      throw new AnalysisInputException("The compiler rejected the analysis configuration", exception);
    }
  }

  private JavaCompiler systemCompiler(CompilerConfiguration configuration) {
    var compiler = compilerSupplier.get();
    if (compiler == null) {
      throw new AnalysisEnvironmentException("A JDK system compiler is required for Java release " + configuration.release().feature());
    }
    try {
      JavaCompilerReleaseSupport.requireSupported(compiler.getSourceVersions(), configuration.release());
    } catch (IllegalStateException exception) {
      throw new AnalysisEnvironmentException("The system compiler cannot analyze Java release " + configuration.release().feature(),
          exception);
    }
    return compiler;
  }

  @SafeVarargs
  private static List<DiagnosticEmission> combineEmissions(List<DiagnosticEmission>... diagnosticGroups) {
    var emissions = new ArrayList<DiagnosticEmission>();
    for (var diagnosticGroup : diagnosticGroups) {
      for (var emission : diagnosticGroup) {
        var existing = emissions.stream().filter(value -> value.address().equals(emission.address())).findFirst();
        if (existing.isEmpty()) {
          emissions.add(emission);
        } else if (!existing.orElseThrow().equals(emission)) {
          throw new IllegalArgumentException("One creating address cannot identify different transported events");
        }
      }
    }
    return List.copyOf(emissions);
  }
}
