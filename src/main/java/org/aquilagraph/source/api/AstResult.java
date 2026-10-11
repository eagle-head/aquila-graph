package org.aquilagraph.source.api;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.BodyRef;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.NonwrittenBodyProvenance;
import org.aquilagraph.identity.api.SourceOccurrence;

/** Intrinsically admitted, content-qualified source result with one canonical aggregate. */
public final class AstResult {
  /** Version of the retained source value schema. */
  public static final String SOURCE_MODEL_VERSION = "java-source/15";
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

  private final AstIdentity qualifiedIdentity;
  private final AstProfile sourceProfile;
  private final AstCompletion sourceCompletion;
  private final SyntaxAnalysisInput facts;
  private final Map<SyntaxNode, SourceOccurrence> writtenOccurrences;

  AstResult(AstIdentity identity, AstProfile profile, AstCompletion completion, SyntaxAnalysisInput facts) {
    qualifiedIdentity = Objects.requireNonNull(identity, "identity");
    sourceProfile = Objects.requireNonNull(profile, "profile");
    sourceCompletion = Objects.requireNonNull(completion, "completion");
    this.facts = Objects.requireNonNull(facts, "facts");
    var occurrences = new IdentityHashMap<SyntaxNode, SourceOccurrence>();
    facts.aggregate().ifPresent(
        aggregate -> aggregate.nodes().forEach(node -> occurrences.put(node, new SourceOccurrence(qualifiedIdentity, node.nodeId()))));
    writtenOccurrences = Collections.unmodifiableMap(occurrences);
    if (completion == AstCompletion.UNAVAILABLE || facts.aggregate().isEmpty() || !supportsSourceModelVersion(identity.sourceModelVersion())
        || !identity.astProfileVersion().equals(profile.version())) {
      throw new IllegalArgumentException("An admitted AST result requires a qualified available aggregate");
    }
  }

  /**
   * Tests whether a retained schema can be admitted without changing its qualified identity.
   *
   * @param version
   *          exact stored source schema version
   * @return whether the current reader supports the schema
   */
  public static boolean supportsSourceModelVersion(String version) {
    return SOURCE_MODEL_VERSION.equals(version) || INITIALIZATION_SOURCE_MODEL_VERSION.equals(version)
        || CLASS_LITERAL_SOURCE_MODEL_VERSION.equals(version) || EXCEPTION_SOURCE_MODEL_VERSION.equals(version)
        || SWITCH_SOURCE_MODEL_VERSION.equals(version) || PATTERN_SOURCE_MODEL_VERSION.equals(version)
        || REFERENCE_SOURCE_MODEL_VERSION.equals(version) || LAMBDA_SOURCE_MODEL_VERSION.equals(version)
        || ASSERTION_SOURCE_MODEL_VERSION.equals(version) || ITERATION_SOURCE_MODEL_VERSION.equals(version)
        || ARRAY_SOURCE_MODEL_VERSION.equals(version) || UPDATE_SOURCE_MODEL_VERSION.equals(version)
        || PREVIOUS_SOURCE_MODEL_VERSION.equals(version) || LEGACY_SOURCE_MODEL_VERSION.equals(version);
  }

  /**
   * Returns exact content-qualified source identity.
   *
   * @return exact content-qualified source identity
   */
  public AstIdentity identity() {
    return qualifiedIdentity;
  }

  /**
   * Returns source representation profile.
   *
   * @return source representation profile
   */
  public AstProfile profile() {
    return sourceProfile;
  }

  /**
   * Returns source-only qualification.
   *
   * @return source-only qualification
   */
  public AstCompletion completion() {
    return sourceCompletion;
  }

  /**
   * Returns exact captured source values.
   *
   * @return exact captured source values
   */
  public InputSnapshot snapshot() {
    return facts.snapshot();
  }

  /**
   * Returns exact captured compiler and dependency environment.
   *
   * @return exact captured compiler and dependency environment
   */
  public AnalysisEnvironmentSnapshot environment() {
    return facts.environmentSnapshot();
  }

  /**
   * Returns canonical declarations without graph requirements.
   *
   * @return canonical declarations without graph requirements
   */
  public JavaProgram program() {
    return facts.program();
  }

  /**
   * Returns the single immutable source occurrence owner.
   *
   * @return the single immutable source occurrence owner
   */
  public JavaAstAggregate aggregate() {
    return facts.aggregate().orElseThrow();
  }

  /**
   * Returns ordered source diagnostic occurrences.
   *
   * @return ordered source diagnostic occurrences
   */
  public List<AnalysisDiagnostic> diagnostics() {
    return facts.diagnostics();
  }

  /**
   * Returns complete independently reconciled source coverage.
   *
   * @return complete independently reconciled source coverage
   */
  public LanguageFeatureCoverageResult coverage() {
    return facts.languageFeatureCoverageResult();
  }

  /**
   * Resolves exact canonical declaration membership.
   *
   * @param methodKey
   *          canonical method key
   * @return owned declaration, or empty for an unknown key
   */
  public Optional<JavaMethod> method(String methodKey) {
    requireMethodKey(methodKey);
    return program().types().stream().flatMap(type -> type.methods().stream()).filter(method -> method.methodKey().equals(methodKey))
        .findFirst();
  }

  /**
   * Binds a retained body to its exact admitted source result and declaration.
   *
   * @param methodKey
   *          canonical method key
   * @return qualified owned body, absent for an unknown or bodyless declaration
   */
  public Optional<BodyRef> bodyRef(String methodKey) {
    var declaration = method(methodKey);
    var syntax = aggregate().methodSyntax(methodKey);
    if (declaration.isEmpty() || syntax.isEmpty()) {
      return Optional.empty();
    }
    var body = syntax.orElseThrow();
    var origin = aggregate().methodBodyOrigin(methodKey).orElseThrow();
    if (origin == MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION) {
      return Optional.empty();
    }
    if (origin == MethodBodyOrigin.SOURCE_AST) {
      return Optional.of(new BodyRef(qualifiedIdentity, methodKey, Optional.of(body.root().nodeId()), Optional.empty()));
    }
    return Optional.of(new BodyRef(qualifiedIdentity, methodKey, Optional.empty(),
        Optional.of(new NonwrittenBodyProvenance(origin.name(), body.root().sourceFileKey(), declaration.orElseThrow().ownerTypeKey()))));
  }

  /**
   * Resolves a source occurrence only inside its exact qualified result.
   *
   * @param occurrence
   *          qualified source occurrence
   * @return identical owned node, or empty for an unknown local key
   * @throws IllegalArgumentException
   *           if the result identity is foreign
   */
  public Optional<SyntaxNode> node(SourceOccurrence occurrence) {
    Objects.requireNonNull(occurrence, "occurrence");
    if (!qualifiedIdentity.equals(occurrence.astIdentity())) {
      throw new IllegalArgumentException("Source occurrence belongs to another AST result");
    }
    return aggregate().node(occurrence.nodeKey());
  }

  /**
   * Qualifies an identical canonical written node owned by this result.
   *
   * @param node
   *          actual retained node instance
   * @return qualified occurrence, empty for an auxiliary node or an unowned copy
   */
  public Optional<SourceOccurrence> occurrence(SyntaxNode node) {
    Objects.requireNonNull(node, "node");
    return Optional.ofNullable(writtenOccurrences.get(node));
  }

  /**
   * Returns an immutable body and its retained facts without graph admission.
   *
   * @param methodKey
   *          canonical method key
   * @return owned body, absent for an unknown or bodyless declaration
   */
  public Optional<MethodSyntax> body(String methodKey) {
    requireMethodKey(methodKey);
    return method(methodKey).isPresent() ? aggregate().methodSyntax(methodKey) : Optional.empty();
  }

  private static void requireMethodKey(String methodKey) {
    if (Objects.requireNonNull(methodKey, "methodKey").isBlank()) {
      throw new IllegalArgumentException("A source method query requires a nonblank canonical key");
    }
  }
}
