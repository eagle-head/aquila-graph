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
  /** Compatible format introducing module service and provider declaration targets. */
  public static final String MODULE_SERVICE_MODEL_VERSION = "java-source/20";
  /** Version of the retained source value schema. */
  public static final String SOURCE_MODEL_VERSION = "java-source/22";
  /** Retained format with explicit compiler-effective environment identity. */
  public static final String ENVIRONMENT_IDENTITY_MODEL_VERSION = "java-source/21";
  /** Compatible format retaining independently proven written expressions. */
  public static final String WRITTEN_EXPRESSION_MODEL_VERSION = "java-source/19";
  /** Compatible format introducing complete captured compilation-unit envelopes. */
  public static final String SOURCE_ENVELOPE_MODEL_VERSION = "java-source/18";
  /** Compatible format introducing exact empty body declarations. */
  public static final String DECLARATION_SOURCE_MODEL_VERSION = "java-source/17";
  /** Compatible format introducing enum constant initialization. */
  public static final String ENUM_SOURCE_MODEL_VERSION = "java-source/16";
  /** Compatible format introducing local and anonymous declarations. */
  public static final String NESTED_SOURCE_MODEL_VERSION = "java-source/15";
  /** Retained declaration initialization format. */
  public static final String INITIALIZATION_SOURCE_MODEL_VERSION = "java-source/14";
  /** Compatible method-only source format preceding declaration execution scopes. */
  public static final String CLASS_LITERAL_SOURCE_MODEL_VERSION = "java-source/13";
  /** Compatible exception and resource source format preceding executable class literals. */
  public static final String EXCEPTION_SOURCE_MODEL_VERSION = "java-source/12";
  /** Compatible switch source format preceding complete exception and resource forms. */
  public static final String SWITCH_SOURCE_MODEL_VERSION = "java-source/11";
  /** Compatible pattern source format preceding expanded switch forms. */
  public static final String PATTERN_SOURCE_MODEL_VERSION = "java-source/10";
  /** Compatible reference source format preceding instance-test and pattern values. */
  public static final String REFERENCE_SOURCE_MODEL_VERSION = "java-source/9";
  /** Compatible lambda source format preceding reference values. */
  public static final String LAMBDA_SOURCE_MODEL_VERSION = "java-source/8";
  /** Compatible assertion and monitor source format. */
  public static final String ASSERTION_SOURCE_MODEL_VERSION = "java-source/7";
  /** Compatible retained schema with iteration and no assertion or monitor values. */
  public static final String ITERATION_SOURCE_MODEL_VERSION = "java-source/6";
  /** Compatible retained schema with executable arrays and no iteration values. */
  public static final String ARRAY_SOURCE_MODEL_VERSION = "java-source/5";
  /** Compatible retained schema with update expressions and no executable array values. */
  public static final String UPDATE_SOURCE_MODEL_VERSION = "java-source/4";
  /** Earlier source schema with copied local bindings and no update-expression values. */
  public static final String PREVIOUS_SOURCE_MODEL_VERSION = "java-source/3";
  /** Compatible retained schema predating copied local binding envelopes. */
  public static final String LEGACY_SOURCE_MODEL_VERSION = "java-source/2";

  /**
   * Tests whether a retained schema can be admitted without changing its qualified identity.
   *
   * @param version
   *          exact stored source schema version
   * @return whether the current reader supports the schema
   */
  public static boolean supportsSourceModelVersion(String version) {
    if (SOURCE_MODEL_VERSION.equals(version) || ENVIRONMENT_IDENTITY_MODEL_VERSION.equals(version)) {
      return true;
    }
    return MODULE_SERVICE_MODEL_VERSION.equals(version) || WRITTEN_EXPRESSION_MODEL_VERSION.equals(version)
        || SOURCE_ENVELOPE_MODEL_VERSION.equals(version) || DECLARATION_SOURCE_MODEL_VERSION.equals(version)
        || ENUM_SOURCE_MODEL_VERSION.equals(version) || NESTED_SOURCE_MODEL_VERSION.equals(version)
        || INITIALIZATION_SOURCE_MODEL_VERSION.equals(version) || CLASS_LITERAL_SOURCE_MODEL_VERSION.equals(version)
        || EXCEPTION_SOURCE_MODEL_VERSION.equals(version) || SWITCH_SOURCE_MODEL_VERSION.equals(version)
        || PATTERN_SOURCE_MODEL_VERSION.equals(version) || REFERENCE_SOURCE_MODEL_VERSION.equals(version)
        || LAMBDA_SOURCE_MODEL_VERSION.equals(version) || ASSERTION_SOURCE_MODEL_VERSION.equals(version)
        || ITERATION_SOURCE_MODEL_VERSION.equals(version) || ARRAY_SOURCE_MODEL_VERSION.equals(version)
        || UPDATE_SOURCE_MODEL_VERSION.equals(version) || PREVIOUS_SOURCE_MODEL_VERSION.equals(version)
        || LEGACY_SOURCE_MODEL_VERSION.equals(version);
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
