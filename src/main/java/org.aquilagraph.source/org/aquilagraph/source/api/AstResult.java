package org.aquilagraph.source.api;

import java.util.List;
import java.util.Optional;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.BodyRef;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceOccurrence;

import org.aquilagraph.source.internal.AdmittedAstResult;
/** Intrinsically admitted, content-qualified source result with one canonical aggregate. */
public sealed interface AstResult permits AdmittedAstResult {
  /** Version of the retained source value schema. */
  public static final String SOURCE_MODEL_VERSION = "java-source/24";

  /**
   * Tests whether a retained schema can be admitted without changing its qualified identity.
   *
   * @param version
   *          exact stored source schema version
   * @return whether the current reader supports the schema
   */
  public static boolean supportsSourceModelVersion(String version) {
    return SOURCE_MODEL_VERSION.equals(version);
  }

  /**
   * Returns exact content-qualified source identity.
   *
   * @return exact content-qualified source identity
   */
  AstIdentity identity();

  /**
   * Returns source representation profile.
   *
   * @return source representation profile
   */
  AstProfile profile();

  /**
   * Returns source-only qualification.
   *
   * @return source-only qualification
   */
  default AstCompletion completion() {
    return qualification().completion();
  }

  /**
   * Returns the admitted source decision and ordered blocking explanations.
   *
   * @return immutable assessment readable without compiler or CFG acquisition
   */
  SourceQualification qualification();

  /**
   * Returns exact captured source values.
   *
   * @return exact captured source values
   */
  InputSnapshot snapshot();

  /**
   * Returns exact captured compiler and dependency environment.
   *
   * @return exact captured compiler and dependency environment
   */
  AnalysisEnvironmentSnapshot environment();

  /**
   * Returns canonical declarations without graph requirements.
   *
   * @return canonical declarations without graph requirements
   */
  JavaProgram program();

  /**
   * Returns the single immutable source occurrence owner.
   *
   * @return the single immutable source occurrence owner
   */
  JavaAstAggregate aggregate();

  /**
   * Returns ordered source diagnostic occurrences.
   *
   * @return ordered source diagnostic occurrences
   */
  List<AnalysisDiagnostic> diagnostics();

  /**
   * Returns complete independently reconciled source coverage.
   *
   * @return complete independently reconciled source coverage
   */
  LanguageFeatureCoverageResult coverage();

  /**
   * Resolves exact canonical declaration membership.
   *
   * @param methodKey
   *          canonical method key
   * @return owned declaration, or empty for an unknown key
   */
  Optional<JavaMethod> method(String methodKey);

  /**
   * Binds a retained body to its exact admitted source result and declaration.
   *
   * @param methodKey
   *          canonical method key
   * @return qualified owned body, absent for an unknown or bodyless declaration
   */
  Optional<BodyRef> bodyRef(String methodKey);

  /**
   * Resolves a source occurrence only inside its exact qualified result.
   *
   * @param occurrence
   *          qualified source occurrence
   * @return identical owned node, or empty for an unknown local key
   * @throws IllegalArgumentException
   *           if the result identity is foreign
   */
  Optional<SyntaxNode> node(SourceOccurrence occurrence);

  /**
   * Qualifies an identical canonical written node owned by this result.
   *
   * @param node
   *          actual retained node instance
   * @return qualified occurrence, empty for an auxiliary node or an unowned copy
   */
  Optional<SourceOccurrence> occurrence(SyntaxNode node);

  /**
   * Returns an immutable body and its retained facts without graph admission.
   *
   * @param methodKey
   *          canonical method key
   * @return owned body, absent for an unknown or bodyless declaration
   */
  Optional<MethodSyntax> body(String methodKey);
}
