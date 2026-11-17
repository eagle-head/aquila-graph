package org.aquilagraph.frontend.internal;

import org.aquilagraph.source.admission.AstProductionAdmission;
import org.aquilagraph.source.admission.CoverageAdmission;

import java.io.IOException;
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
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AstOutcome;
import org.jspecify.annotations.Nullable;
import org.aquilagraph.frontend.AnalysisObserver;

final class JavaAnalysisPipeline {
  private final AnalysisEnvironmentCapture environmentCapture;
  private final Supplier<@Nullable JavaCompiler> compilerSupplier;
  private final AnalysisObserver observer;

  JavaAnalysisPipeline(AnalysisEnvironmentCapture environmentCapture) {
    this(environmentCapture, ToolProvider::getSystemJavaCompiler);
  }

  JavaAnalysisPipeline(AnalysisEnvironmentCapture environmentCapture, Supplier<@Nullable JavaCompiler> compilerSupplier) {
    this(environmentCapture, compilerSupplier, AnalysisObserver.none());
  }

  JavaAnalysisPipeline(AnalysisEnvironmentCapture environmentCapture, Supplier<@Nullable JavaCompiler> compilerSupplier,
      AnalysisObserver observer) {
    this.environmentCapture = Objects.requireNonNull(environmentCapture, "environmentCapture");
    this.compilerSupplier = Objects.requireNonNull(compilerSupplier, "compilerSupplier");
    this.observer = Objects.requireNonNull(observer, "observer");
  }

  AstOutcome produce(InputSnapshot snapshot, CompilerConfiguration configuration, JavaTranslationPolicy policy) throws IOException {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(policy, "policy");
    if (snapshot.sourceFiles().isEmpty()) {
      var contract = JavaLanguageFeatureCoverage.fromConfiguration(configuration);
      var written = JavaSourceCoverageCapture.empty(snapshot, contract);
      var coverage = CoverageAdmission.fromEvidence(contract, written, new AttributedCoverageExpectations(written, List.of()), List.of(),
          List.of(), List.of());
      return AstProductionAdmission.empty(snapshot, configuration, policy.profile(), coverage);
    }
    var input = analyze(snapshot, configuration, policy);
    return JavaAnalysisStage.measure(observer, "outcome-admission", snapshot, policy.profile(),
        () -> AstProductionAdmission.outcome(input, policy.profile(), policy.semanticRevision()));
  }

  SyntaxAnalysisInput analyze(InputSnapshot snapshot, CompilerConfiguration configuration, JavaTranslationPolicy policy)
      throws IOException {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(configuration, "configuration");
    var compiler = systemCompiler(configuration);
    var invocation = JavaCompilerInvocation.from(configuration);
    var environment = JavaAnalysisStage.measure(observer, "environment-capture", snapshot, policy.profile(),
        () -> environmentCapture.capture(compiler, configuration, invocation.analysisOptions()));
    var languageFeatureCoverage = JavaLanguageFeatureCoverage.fromConfiguration(configuration);
    if (snapshot.sourceFiles().isEmpty()) {
      var written = JavaSourceCoverageCapture.empty(snapshot, languageFeatureCoverage);
      var coverageResult = CoverageAdmission.fromEvidence(languageFeatureCoverage, written,
          new AttributedCoverageExpectations(written, List.of()), List.of(), List.of(), List.of());
      return new SyntaxAnalysisInput(snapshot, environment, new JavaProgram(List.of()),
          Optional.of(AstValueAdmission.admit(CopiedAstValues.empty(snapshot))), List.of(), coverageResult);
    }

    var sources = new SnapshotSourceCatalog(snapshot);
    SyntaxAnalysisInput result;
    try (var session = openCompilerSession(compiler, invocation, sources)) {
      var parsedSource = JavaAnalysisStage.measure(observer, "parse", snapshot, policy.profile(), session::parse);
      var coverageCapture = JavaAnalysisStage.measure(observer, "written-inventory", snapshot, policy.profile(),
          () -> JavaSourceCoverageCapture.capture(snapshot, parsedSource, sources, languageFeatureCoverage, policy));
      var propertyProducer = new JavaDeclarationPropertyEncounterProducer(parsedSource.units(), sources, parsedSource.sourceEvidence(),
          languageFeatureCoverage, policy);
      var writtenProperties = propertyProducer.observeWritten();
      var annotationInventory = JavaAnnotationSourceInventory.capture(parsedSource, sources);
      var kindEvidence = new JavaCompilerKindDiagnostics(sources, parsedSource.sourceEvidence(), languageFeatureCoverage, policy)
          .observe(parsedSource.units());
      var compilation = JavaAnalysisStage.measure(observer, "compiler-attribution", snapshot, policy.profile(),
          () -> session.analyze(parsedSource, policy));
      var attributedExpectations = JavaAnalysisStage.measure(observer, "attributed-inventory", snapshot, policy.profile(),
          () -> coverageCapture.captureAttributed(compilation.semantics()));
      var attributedProperties = propertyProducer.observeAttributed(compilation.semantics());
      var typeEvidence = new JavaTypeKindObserver(sources, compilation.semantics(), compilation.sourceEvidence(),
          compilation.treeInventory(), languageFeatureCoverage, policy).observe(compilation.units());
      var translator = new JavaProgramTranslator(sources, compilation.semantics(), compilation.sourceEvidence(),
          compilation.treeInventory(), policy);
      var translation = JavaAnalysisStage.measure(observer, "source-translation", snapshot, policy.profile(),
          () -> translator.translate(snapshot, compilation.units(),
              compilation.diagnostics().stream().anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)));
      var annotationCompleteness = new JavaAnnotationCompleteness(annotationInventory, compilation.semantics(), translation.diagnostics());
      var typeUseEvents = JavaTypeUseCompleteness.reconcile(snapshot, kindEvidence.observations(), translation.value().aggregate());
      var emissions = DiagnosticEmissions.combine(new CompilerDiagnosticTranslator(sources).translate(compilation.diagnostics()),
          kindEvidence.emissions(), typeEvidence.emissions(), translation.emissions(), typeUseEvents,
          annotationCompleteness.reconcile(translation.value().aggregate()));
      var reconciled = JavaAnalysisStage.measure(observer, "coverage-reconciliation", snapshot, policy.profile(),
          () -> new JavaSourceCoverageReconciler(coverageCapture.inventory(), attributedExpectations, translation.value().program(),
              translation.value().aggregate(), emissions)
              .reconcile(languageFeatureCoverage, kindEvidence, typeEvidence, writtenProperties, attributedProperties));
      result = JavaAnalysisStage.measure(observer, "syntax-admission", snapshot, policy.profile(),
          () -> new SyntaxAnalysisInput(snapshot, environment, translation.value().program(), translation.value().aggregate(),
              reconciled.diagnostics(), reconciled.coverageResult()));
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

}
