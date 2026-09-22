package org.aquilagraph.syntax;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.AnalysisEnvironmentSnapshot;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.JavaProgram;
import org.aquilagraph.model.LanguageFeature;
import org.aquilagraph.model.LanguageFeatureCategory;
import org.aquilagraph.model.LanguageFeatureCoverage;
import org.aquilagraph.model.LanguageFeatureCoverageResult;

/**
 * Frontend boundary input passed to semantic analysis.
 *
 * <p>
 * This boundary container is transient. Its method syntax contains durable compiler-neutral in-memory occurrence values; aggregate
 * ownership is introduced by later foundation tasks.
 *
 * @param snapshot
 *          analyzed source snapshot
 * @param environmentSnapshot
 *          captured compiler environment
 * @param program
 *          declaration-only Java program
 * @param methodSyntax
 *          syntax evidence for methods that declare bodies
 * @param diagnostics
 *          frontend diagnostics
 * @param languageFeatureCoverageResult
 *          release-bound contract and source-owned outcomes produced by the frontend
 */
public record SyntaxAnalysisInput(InputSnapshot snapshot, AnalysisEnvironmentSnapshot environmentSnapshot, JavaProgram program,
    List<MethodSyntax> methodSyntax, List<AnalysisDiagnostic> diagnostics, LanguageFeatureCoverageResult languageFeatureCoverageResult) {
  private static final long EXPECTED_COMPILATION_UNIT_OBSERVATIONS = 1L;

  /** Validates and defensively copies boundary data. */
  public SyntaxAnalysisInput {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(environmentSnapshot, "environmentSnapshot");
    Objects.requireNonNull(program, "program");
    methodSyntax = List.copyOf(Objects.requireNonNull(methodSyntax, "methodSyntax"));
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    Objects.requireNonNull(languageFeatureCoverageResult, "languageFeatureCoverageResult");
    if (languageFeatureCoverageResult.coverage().release() != environmentSnapshot.compilerConfiguration().release()) {
      throw new IllegalArgumentException("Language-feature coverage release must match the compiler configuration release");
    }
    validateCoverageResult(snapshot, diagnostics, languageFeatureCoverageResult);
    validateMethodSyntax(program, methodSyntax);
  }

  /**
   * Returns the release-bound language-feature contract used for this input.
   *
   * @return immutable feature contract
   */
  public LanguageFeatureCoverage languageFeatureCoverage() {
    return languageFeatureCoverageResult.coverage();
  }

  /**
   * Returns the compiler configuration used by the frontend.
   *
   * @return compiler configuration
   */
  public CompilerConfiguration compilerConfiguration() {
    return environmentSnapshot.compilerConfiguration();
  }

  /**
   * Finds durable in-memory syntax evidence by stable method identity.
   *
   * @param methodKey
   *          method identity
   * @return matching syntax evidence when the method declares a body
   */
  public Optional<MethodSyntax> methodSyntax(String methodKey) {
    Objects.requireNonNull(methodKey, "methodKey");
    return methodSyntax.stream().filter(syntax -> syntax.methodKey().equals(methodKey)).findFirst();
  }

  private static void validateMethodSyntax(JavaProgram program, List<MethodSyntax> methodSyntax) {
    Map<String, JavaMethod> methods = program.methods().stream()
        .collect(Collectors.toUnmodifiableMap(JavaMethod::methodKey, Function.identity()));
    var syntaxMethodKeys = new HashSet<String>();
    for (var syntax : methodSyntax) {
      var method = methods.get(Objects.requireNonNull(syntax, "methodSyntax entry").methodKey());
      if (method == null || !method.hasBody() || !syntaxMethodKeys.add(syntax.methodKey())) {
        throw new IllegalArgumentException("Method syntax must uniquely describe a program method body");
      }
    }
    var declaredBodyKeys = methods.values().stream().filter(JavaMethod::hasBody).map(JavaMethod::methodKey).collect(Collectors.toSet());
    if (!syntaxMethodKeys.equals(declaredBodyKeys)) {
      throw new IllegalArgumentException("Every declared method body requires syntax evidence");
    }
  }

  private static void validateCoverageResult(InputSnapshot snapshot, List<AnalysisDiagnostic> diagnostics,
      LanguageFeatureCoverageResult coverageResult) {
    validateObservationSources(snapshot, coverageResult);
    for (var capabilityDiagnostic : coverageResult.capabilityDiagnostics()) {
      if (!diagnostics.contains(capabilityDiagnostic)) {
        throw new IllegalArgumentException("Coverage capability diagnostics must be retained in frontend diagnostics");
      }
    }
    for (var diagnostic : diagnostics) {
      if (coverageResult.isCapabilityDiagnostic(diagnostic) && !coverageResult.capabilityDiagnostics().contains(diagnostic)) {
        throw new IllegalArgumentException("Frontend capability diagnostics must be retained in the coverage result");
      }
    }
    validateCompilationUnitObservations(snapshot, coverageResult);
  }

  private static void validateObservationSources(InputSnapshot snapshot, LanguageFeatureCoverageResult coverageResult) {
    var sourceFileKeys = new HashSet<String>();
    snapshot.sourceFiles().forEach(sourceFile -> sourceFileKeys.add(sourceFile.sourceFileKey()));
    for (var observation : coverageResult.observations()) {
      if (!sourceFileKeys.contains(observation.sourceFileKey())) {
        throw new IllegalArgumentException("Language feature observations must belong to the input snapshot");
      }
    }
  }

  private static void validateCompilationUnitObservations(InputSnapshot snapshot, LanguageFeatureCoverageResult coverageResult) {
    if (snapshot.sourceFiles().isEmpty()) {
      return;
    }
    var compilationUnitFeature = new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, "COMPILATION_UNIT");
    var classification = coverageResult.coverage().classificationOf(compilationUnitFeature)
        .orElseThrow(() -> new IllegalArgumentException("Nonempty Java input requires COMPILATION_UNIT coverage"));
    for (var sourceFile : snapshot.sourceFiles()) {
      var count = coverageResult.observations().stream()
          .filter(observation -> observation.sourceFileKey().equals(sourceFile.sourceFileKey())
              && observation.classification().equals(classification))
          .count();
      if (count != EXPECTED_COMPILATION_UNIT_OBSERVATIONS) {
        throw new IllegalArgumentException("Every source file requires exactly one COMPILATION_UNIT coverage observation");
      }
    }
  }
}
