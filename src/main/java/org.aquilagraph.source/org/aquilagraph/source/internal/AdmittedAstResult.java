package org.aquilagraph.source.internal;

import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.SourceQualification;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.MethodSyntax;
import org.aquilagraph.source.api.SyntaxNode;
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
public final class AdmittedAstResult implements AstResult {
  private final AstIdentity qualifiedIdentity;
  private final AstProfile sourceProfile;
  private final SourceQualification sourceQualification;
  private final InputSnapshot capturedInput;
  private final AnalysisEnvironmentSnapshot capturedEnvironment;
  private final JavaProgram declarations;
  private final JavaAstAggregate sourceAggregate;
  private final List<AnalysisDiagnostic> sourceDiagnostics;
  private final LanguageFeatureCoverageResult sourceCoverage;
  private final Map<SyntaxNode, SourceOccurrence> writtenOccurrences;

  /**
   * Owns an admitted source result and indexes its occurrence membership.
   *
   * @param identity
   *          identity
   * @param profile
   *          selected source profile
   * @param qualification
   *          admitted source assessment
   * @param snapshot
   *          snapshot
   * @param environment
   *          environment
   * @param program
   *          program
   * @param aggregate
   *          aggregate
   * @param diagnostics
   *          diagnostics
   * @param coverage
   *          coverage
   */
  public AdmittedAstResult(AstIdentity identity, AstProfile profile, SourceQualification qualification, InputSnapshot snapshot,
      AnalysisEnvironmentSnapshot environment, JavaProgram program, JavaAstAggregate aggregate, List<AnalysisDiagnostic> diagnostics,
      LanguageFeatureCoverageResult coverage) {
    qualifiedIdentity = Objects.requireNonNull(identity, "identity");
    sourceProfile = Objects.requireNonNull(profile, "profile");
    sourceQualification = Objects.requireNonNull(qualification, "qualification");
    var completion = qualification.completion();
    if (!profile.equals(qualification.profile())) {
      throw new IllegalArgumentException("Source assessment must retain its original profile");
    }
    capturedInput = Objects.requireNonNull(snapshot, "snapshot");
    capturedEnvironment = Objects.requireNonNull(environment, "environment");
    declarations = Objects.requireNonNull(program, "program");
    sourceAggregate = Objects.requireNonNull(aggregate, "aggregate");
    sourceDiagnostics = List.copyOf(diagnostics);
    sourceCoverage = Objects.requireNonNull(coverage, "coverage");
    var occurrences = new IdentityHashMap<SyntaxNode, SourceOccurrence>();
    aggregate.nodes().forEach(node -> occurrences.put(node, new SourceOccurrence(qualifiedIdentity, node.nodeId())));
    writtenOccurrences = Collections.unmodifiableMap(occurrences);
    if (completion == AstCompletion.UNAVAILABLE || !AstResult.supportsSourceModelVersion(identity.sourceModelVersion())
        || !identity.astProfileVersion().equals(profile.version())) {
      throw new IllegalArgumentException("An admitted AST result requires a qualified available aggregate");
    }
  }

  /**
   * Returns exact content-qualified source identity.
   *
   * @return exact content-qualified source identity
   */
  @Override
  public AstIdentity identity() {
    return qualifiedIdentity;
  }

  /**
   * Returns source representation profile.
   *
   * @return source representation profile
   */
  @Override
  public AstProfile profile() {
    return sourceProfile;
  }

  /**
   * Returns source-only qualification.
   *
   * @return source-only qualification
   */
  @Override
  public SourceQualification qualification() {
    return sourceQualification;
  }

  /**
   * Returns exact captured source values.
   *
   * @return exact captured source values
   */
  @Override
  public InputSnapshot snapshot() {
    return capturedInput;
  }

  /**
   * Returns exact captured compiler and dependency environment.
   *
   * @return exact captured compiler and dependency environment
   */
  @Override
  public AnalysisEnvironmentSnapshot environment() {
    return capturedEnvironment;
  }

  /**
   * Returns canonical declarations without graph requirements.
   *
   * @return canonical declarations without graph requirements
   */
  @Override
  public JavaProgram program() {
    return declarations;
  }

  /**
   * Returns the single immutable source occurrence owner.
   *
   * @return the single immutable source occurrence owner
   */
  @Override
  public JavaAstAggregate aggregate() {
    return sourceAggregate;
  }

  /**
   * Returns ordered source diagnostic occurrences.
   *
   * @return ordered source diagnostic occurrences
   */
  @Override
  public List<AnalysisDiagnostic> diagnostics() {
    return sourceDiagnostics;
  }

  /**
   * Returns complete independently reconciled source coverage.
   *
   * @return complete independently reconciled source coverage
   */
  @Override
  public LanguageFeatureCoverageResult coverage() {
    return sourceCoverage;
  }

  /**
   * Resolves exact canonical declaration membership.
   *
   * @param methodKey
   *          canonical method key
   * @return owned declaration, or empty for an unknown key
   */
  @Override
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
  @Override
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
  @Override
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
  @Override
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
  @Override
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
