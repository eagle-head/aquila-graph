# Analysis Contract

This document defines the shared contract for Java source analysis and optional publication. Detailed Java semantics belong to the [Java analysis design](../design/java-analysis.md); stored identities and properties belong to the [Neo4j schema](../repository/neo4j-schema.md). These are implementation contracts, not a report that any particular deployment or input has passed verification.

**Status:** the Java/CLI surface coordinates independent source and CFG results through the native application contracts. The sections below define production, derivation, retention, and query obligations. Complete independent implementation acceptance remains pending; this document does not certify runtime behavior.[1][3]

## Inputs and boundaries

`AnalysisRequest` supplies a source input, compiler configuration, AST profile, optional CFG selection, and publication intent. `SourceAnalysisRunner` coordinates production and requested derivation; `AnalysisQualification` combines their independent outcomes. The application applies `DO_NOT_SAVE`, `SAVE_COMPLETED`, or `SAVE_COMPLETE_AST` after computation. The existing CLI requests all bodies with the bounded profiles and exposes only no-save or completed-run publication.[1][2][3][5]

The public path facade translates `IOException` and `IllegalArgumentException` from source capture to `AnalysisInputException`, and analysis-environment `IOException` to `AnalysisEnvironmentException`. Lazy source-traversal `UncheckedIOException` retains its original family and cause; traversal resources still close. Its snapshot overload retains checked `IOException`. Invalid direct Java arguments and failures outside the declared translation regions retain their actual throwable families; the CLI does not promise a controlled result for every possible failure.[2][5] The [source traversal failure controls](../../src/test/java/org/aquilagraph/frontend/JavaSourceTraversalFailureTest.java), particularly `givenOpenedSourceWalkFailsDuringLazyIteration_whenLoaderAndPathEntryRun_thenUncheckedFailureIsNotTranslatedAndEachWalkCloses`, specify that preserved boundary.

## Completion

`AnalysisRun` retains its request identity, independent source and optional CFG outcomes, application completion, and application diagnostics. Its constructor verifies qualification and exact selection; it does not accept an arbitrary supplied completion label. The [outcome relations](#target-outcome-relations) are the single definition of `REJECTED`, `FAILED`, `PARTIAL`, and `COMPLETE`.[1]

Completed-run publication additionally requires `AnalysisRun.isPublishable()`: complete source and CFG, captured environment, no error diagnostics or blocking source coverage, exact coverage of all required concrete-method keys, and qualified graph identities/body references. Independent AST completion alone does not satisfy that gate.[1]

## Publication eligibility

| Subject / intent | Repository result |
| --- | --- |
| Any computed run / `DO_NOT_SAVE` | `NOT_REQUESTED`; no repository acquisition. |
| Ineligible combined run / `SAVE_COMPLETED` | `NOT_ELIGIBLE`; no repository acquisition. |
| Publishable combined run / `SAVE_COMPLETED` | Open, save, and close; return `SAVED` only after normal completion. |
| Independently complete AST / `SAVE_COMPLETE_AST` | Apply the separate [AST retention gate](#target-publication-and-retention), irrespective of CFG eligibility. |

`AnalysisExecution` retains the computed run and independent publication outcome. A repository failure cannot rewrite analysis completion. `CompletedRunPublicationService` transports an effective `RepositoryException` through `AnalysisRepositoryException`, retaining the computed run and cause.[3]

## Publication and failure effects

The repository maps and validates the supplied completed run, prepares and inspects schema constraints in a separate session, and replaces the same-run projection in one managed write transaction per attempt. This completed-run projection excludes the full source aggregate; independent AST retention uses a separate projection.[4]

| Failed phase | Required interpretation |
| --- | --- |
| Mapping or projection validation | No schema or replacement work has started. Mapping is outside the adapter's save-time `Neo4jException` translation region. |
| Constraint preparation, metadata inspection, or schema-session closure | Replacement has not started. Earlier schema changes may remain. |
| Replacement before established commit | An exception alone does not establish rollback or commit. An established noncommit retains the previous projection. |
| Commit acknowledgement unavailable | Whole-run commit is unknown; do not report partial publication as success. |
| Publication-session close after known commit | The close failure does not undo the committed replacement. |
| Repository close after save returns | The close failure prevents a `SAVED` execution from being returned; it does not establish rollback. |

Resource scopes retain a primary failure and its suppressed cleanup failures. A close failure without an earlier primary prevents normal return. The enclosing translation region sees the effective primary throwable; a suppressed `RepositoryException` does not replace an unrelated primary failure. A close attempt is not proof of successful resource release.[3][4]

## Command line results

```text
java -jar target/aquila-graph-0.1.0-SNAPSHOT.jar <source-root> [--release <feature>] [--persist]
```

Admit exactly one root and at most one of each option. Missing, malformed, overflowing, unsupported, or duplicate release values, duplicate persistence, unknown options, wrong root count, and invalid platform paths produce usage rejection before analysis. Direct Java null arguments and unmatched failures remain abrupt.[5]

| Outcome | Standard output | Standard error | Exit |
| --- | --- | --- | ---: |
| Usage rejection | None | Rejection message, then usage | 2 |
| Named input failure | None | `Input rejected: {description}` | 2 |
| Named environment failure | None | `Analysis failed: {description}` | 1 |
| Returned `COMPLETE` run; printed `COMPLETED` | Summary, then repository status | Ordered diagnostics | 0 |
| Returned `PARTIAL` or `FAILED` run; printed `INCOMPLETE` | Summary, then repository status | Ordered diagnostics | 1 |
| Returned `REJECTED` run | Summary, then repository status | Ordered diagnostics | 2 |
| Named repository failure | Retained completed-run summary | Ordered diagnostics, then `Repository save failed: {description}` | 3 |

A summary prints `Run`, `Software version`, `Completion`, `Java release`, and `Java source files` in that order. Representation presence adds `Java types`, `Java methods`, `Analyzed method bodies`, `CFG operations`, `CFG nodes`, and `CFG edges`. Each diagnostic is printed as `{severity} {stage} {code}: {message}`. Normal execution then prints `Repository: {status}`; repository failure prints no status line.[5]

`AnalyzeCommand.run` borrows its streams and returns an integer. The process entry requests `System.exit` only for a normally returned nonzero result. These calls promise neither successful external output delivery nor ordering between separately observed streams. Abrupt presentation may leave earlier output without a complete report.[5]

`AnalysisRun.diagnostics()` presents source occurrences, CFG diagnostics in canonical method order (or rejected-request diagnostics), then application diagnostics without value deduplication. Diagnostic compatibility, including the earlier parenthesized-assignment boundary, still requires explicit verification before claiming unchanged historical CLI behavior. Removed compatibility adapters are not evidence of that preservation.[1][5]

## Target independent analysis contracts

The [selected decomposition](../design/java-analysis.md#separation-decision) owns responsibilities. Public operations below define required behavior; complete implementation conformance requires separate acceptance evidence. `AstProducer` belongs to Source Model; `CfgDeriver` belongs to Control Flow. Neither requires the other's implementation.

| Operation | Preconditions and input | Postcondition / observable failure |
| --- | --- | --- |
| `AstProducer.produce(AstProductionRequest): AstOutcome` | Root or captured snapshot, compiler configuration, declared AST profile; no CFG collaborator. | Nonempty production returns normally only after normal compiler work/closure/environment verification. Empty capture opens no compiler and returns `UNAVAILABLE` with initial provenance. Normal outcomes expose immutable source result/provenance/diagnostics; diagnosed required-root withholding is `UNAVAILABLE`. Production I/O/environment/unmatched failures preserve their family and cause. |
| `AstResult` node/declaration/body/evidence queries | Intrinsically admitted source result. | Queries work after compiler closure and without CFG; unknown body/occurrence queries are absent, malformed query arguments reject. Evidence states remain explicit; no query computes CFG readiness. |
| `CfgDeriver.derive(AstResult, CfgRequest): CfgOutcome` | Exact source identity, declared compatible versions, ordered unique method selection; source profile/evidence visible through read API. | Rejected request or ordered per-method admission/construction outcomes; AST unchanged. No frontend acquisition or mutable builder. |
| `AnalysisApplicationService.execute(AnalysisRequest): AnalysisExecution` | Explicit AST profile, optional immutable `CfgSelection` with ordered keys and complete CFG profile, publication intent. The preproduction selection has no expected AST identity. | Available production binds the unchanged selection to its exact result identity before CFG derivation. Unavailable production rejects while retaining the original selection with `UNBOUND` identity; no CFG call. References established AST and optional CFG outcomes plus overall qualification; separate publication result. |
| `deriveExisting(AstOutcome, CfgRequest): AnalysisRun` | Previously established outcome and explicit request; available AST and exact compatible identity permit derivation. | Returns the computed run without production or publication, retaining AST on refusal/failure. Unavailable AST rejects CFG; no invented source result. Retention is a separate operation. |
| `retainAst(AstPublicationRequest): PublicationOutcome` | Explicit `SAVE_COMPLETE_AST` or `DO_NOT_SAVE`; exact completed AST outcome for eligibility. | Lazy repository open/save/close, independent of CFG/request completion; actual publication failure retains computed subject. |
| `findAst(AstIdentity): AstLookup` | Requested qualified identity; lazy operation-scoped repository acquisition. | After normal read/decode/owned closure, `FOUND` validated source result, `NOT_FOUND`, or `INCOMPATIBLE`. Query failure carries requested identity and effective causes, returns no lookup and creates no publication outcome. No CFG is loaded or required. |

### Target outcome relations

The application retains the exact requested selection descriptor even when production is unavailable or CFG admission rejects duplicate/unknown keys. Absence of `CfgSelection` means `NOT_REQUESTED`; an empty present selection is requested. Production binds only an established result identity. `deriveExisting` preserves the caller's mandatory requested identity even when the supplied outcome has no result. The [qualified identity contract](../design/source-snapshots.md#target-qualified-analysis-identity) defines all descriptor variants and digest inputs.

AST presence and completion follow [intrinsic source qualification](../design/java-analysis.md#ast-result-and-intrinsic-validity). `COMPLETE` and `INCOMPLETE` have an admitted queryable `AstResult`; `UNAVAILABLE` has none. Invalid supplied structure is rejected by admission, not represented by a fabricated valid AST. Missing source-profile-required facts and compiler errors prevent `COMPLETE`; facts optional to that profile do not. CFG availability never determines AST completion.

The [module service and provider identity contract](../design/java-analysis.md#module-service-and-provider-identities) makes the three resolved module endpoint families required for `COMPLETE` in an explicit successor expanded profile. That section owns their source associations, qualification, immutable queries, identity/retention compatibility, and delivery boundary. The decision is established; implementation and validation remain pending. Existing profiles and retained outcomes keep their original meaning, and this documentation change introduces no runtime capability or publication eligibility.

CFG request state is `REJECTED`, `COMPLETE`, or `PARTIAL`. Selected bodyless declarations yield `NOT_APPLICABLE`; selected concrete methods yield `AVAILABLE`, `SOURCE_UNAVAILABLE`, `UNSUPPORTED`, `INSUFFICIENT_EVIDENCE`, `ERRONEOUS_EVIDENCE`, or `FAILED`. Application coverage assigns `NOT_REQUESTED` only to nonselected declarations. These cases are distinguishable through public queries rather than an empty optional graph alone. Ordered blockers retain separate identities/causes; complete CFG means all selected bodies available and no CFG error. Available graphs for unaffected bodies may coexist with AST incompleteness and never upgrade it.

Overall request state is `REJECTED`, `COMPLETE`, `PARTIAL`, or `FAILED`, derived by the application from the requested operations. AST-only completion needs complete AST, no graph. Selected bodyless declarations impose no graph obligation. Refused requested CFG produces a partial request; declared construction failure produces a failed request, retaining AST and previously validated method graphs. Rejected CFG identity/selection produces a rejected request with any prior AST still attached. See [coordination qualification](../design/java-analysis.md#target-coordination-completion-and-failures) for exact rules.

AST diagnostics, CFG diagnostics, application diagnostics and publication failures retain their creating owner and order. Presentation can compose those streams without redefining domain validity or value-deduplicating events. `CfgDerivationException` is an abrupt declared failure carrying completed CFG progress; `AnalysisDerivationException` retains that cause and the established AST. Other effective failures remain abrupt; fatal errors are not converted into successful partial results.

## Target publication and retention

**Explicit target decision:** preserve the legacy `SAVE_COMPLETED` combined-run contract and add a separate opt-in completed-AST retention contract. This decision permits an independently complete AST to be saved when CFG is absent, refused, or failed. It does not admit incomplete AST publication, automatically save results, add CLI flags, or select a new backend.

| Subject / intent | Eligibility | Storage result |
| --- | --- | --- |
| Combined run / `SAVE_COMPLETED` | `AnalysisRun.isPublishable()` requires complete source and exact qualified graph coverage for every required concrete method. Overall `COMPLETE` alone is insufficient. | Publication table above; no AST projection implied. |
| Target AST / `DO_NOT_SAVE` | No attempt. | `NOT_REQUESTED`, regardless of AST state. |
| Target AST / `SAVE_COMPLETE_AST`, AST `COMPLETE` | Eligible irrespective of CFG/request outcome. | Open, map, validate, save, close; `SAVED` only after normal completion. |
| Target AST / `SAVE_COMPLETE_AST`, AST `INCOMPLETE` or `UNAVAILABLE` | Ineligible; do not open repository. | `NOT_ELIGIBLE`; in-memory established facts still available. |
| Target eligible subject / attempted operation fails | Failure does not rewrite computed analysis outcomes. | `FAILED` when within declared returned publication contract; named abrupt publication exception otherwise. Retain effective primary, suppressed failures, and phase-specific commit knowledge. |

Target `PublicationOutcome` retains subject identity when established, intent, status and failure/commit knowledge (`NOT_STARTED`, `KNOWN_NONCOMMIT`, `UNKNOWN`, `KNOWN_COMMIT` as applicable); an unavailable AST does not acquire a fabricated result identity, and every attempted eligible save has one. Unknown acknowledgement is not success. Mapping exceptions and unmatched failures must remain visible with their actual family, not be universally relabeled as driver errors. A close failure after known commit keeps that fact but prevents `SAVED`. Independent explicit AST retention may be invoked after a failed coordinated request because its eligibility derives from the established AST alone.

For the new AST publication operations, an effective application-port `RepositoryException` returns publication `FAILED`. Other effective runtime failures from acquisition, mapping, schema/replacement or close remain abrupt through application-owned `AnalysisPublicationException`, retaining the exact computed `AstOutcome` or `AnalysisRun`, original cause and suppressed chain, phase and available commit knowledge. Classification uses the effective primary defined by the port crossing below, never a suppressed cleanup failure or the carrier's wrapper type. This is a context envelope, not a claim that mapping failure is a driver exception. Fatal `Error` remains its actual family without a promised complete report. The `SAVE_COMPLETED` service retains its distinct `AnalysisRepositoryException` translation scope and unmatched-failure behavior. Query failures remain failures of `findAst`, never `NOT_FOUND` or a publication attempt.

`ComputedPublicationSubject` is an application-owned closed choice between an AST subject referencing `AstOutcome` and a coordinated subject referencing `AnalysisRun`. It retains existing immutable results rather than making either domain type inherit an application type. Exactly one subject variant is present in the exception; attempted subject identity must match that value.

### Target repository failure crossing

The new application-owned AST port uses `AstResultRepositoryFactory.open(RepositoryOperationContext)`. The context contains `SAVE_AST` or `QUERY_AST` and the exact requested `AstIdentity`. The returned repository is owned by that single operation: `saveComplete` or `find` must match the context's kind and identity; no second subject or operation is accepted. The application closes every successfully acquired repository, including after work failure. Acquisition failures own cleanup of any accessible resources not transferred. This changes only the new AST port; the legacy factory and completed-run port keep their declared contracts.

`RepositoryOperationException` is the technology-neutral runtime carrier exposed by new AST factory/save/find/close operations. It retains the operation context, failure phase, conservative `CommitKnowledge`, and `effectivePrimary: RuntimeException` as its exact cause. The adapter first applies only its declared driver-to-`RepositoryException` translation region, then wraps the resulting effective primary; mapping and unmatched runtime failures retain their actual family as the cause. The carrier never substitutes a cleanup cause for the primary. Preexisting cause/suppressed chains remain intact. Cleanup carriers are suppressed on the primary carrier in encounter order and each retains its own phase, knowledge and actual cause; enclosing repository-close failure is suppressed there by the application. If work returned normally, close failure becomes the primary carrier. No failure is discarded because another failure is already present.

`PublicationFailure` retains this entire carrier, its effective primary, all nested cause/suppressed evidence, the primary phase, and operation-wide commit knowledge. `AnalysisPublicationException.cause` is the exact effective primary and its failure field retains the carrier, so cleanup evidence is not lost by unwrapping. A returned publication `FAILED` has the same failure information. Phase vocabulary is `ACQUISITION`, `VALIDATION`, `MAPPING`, `SCHEMA`, `SCHEMA_SESSION_CLOSE`, `REPLACEMENT`, `PUBLICATION_SESSION_CLOSE`, `READ`, `DECODE`, `READ_SESSION_CLOSE`, and `REPOSITORY_CLOSE`. The [repository phase mapping](../design/analysis-repository.md#target-phase-and-commit-mapping) defines the producer of each observation. Fatal errors remain abrupt in their actual family; applicable cleanup still runs, with no fabricated carrier or normal result for the fatal primary.

`CommitKnowledge` concerns replacement of the named AST subject, not schema changes: `NOT_STARTED` means no subject replacement started; `KNOWN_NONCOMMIT` requires an established noncommit; `UNKNOWN` means replacement was attempted without conclusive acknowledgement; `KNOWN_COMMIT` requires acknowledged commit. Schema effects and cleanup failures remain separately observable. Cleanup cannot downgrade a known commit to noncommit, or turn unknown acknowledgement into rollback or success. The repository carries forward its observed operation state through close; a normal `saveComplete` establishes acknowledged commit and normal internal session closure. `SAVED` additionally requires normal enclosing close.

`findAst` propagates `RepositoryOperationException` for query acquisition/read/decode/session-close/enclosing-close failures, retaining the requested identity and cause/suppressed chain. `QUERY_AST` always has `NOT_STARTED` subject-replacement knowledge. It has no computed publication subject or `PublicationOutcome`, and returns no lookup on failure. A normal lookup, including `NOT_FOUND` or `INCOMPATIBLE`, requires normal read-session and enclosing repository closure. Neither query failure nor a codec rejection is absence.

An AST-only request does not become legacy-save eligible through its independent `COMPLETE` state. `SAVE_COMPLETED` requires the combined-run publication service and the coverage/error/exact required-graph gate; it never falls back to AST storage. The independent subject uses `SAVE_COMPLETE_AST`. Future compatibility checks must observe AST-only legacy ineligibility and unchanged existing CLI completed/incomplete outcomes.

Retention and queries require the [repository contract](../design/analysis-repository.md#target-ast-retention) and [schema migration](../repository/neo4j-schema.md#target-ast-retention-schema). The model does not prove durable AST publication or retrieval. Command grammar, printed completion labels, output order, and exit codes above remain compatibility obligations; independently accepted behavior must be supported by current executable observations.

## References

1. [SourceAnalysisRunner](../../src/main/java/org/aquilagraph/application/SourceAnalysisRunner.java), `analyze` and `deriveExisting`; [AnalysisRun](../../src/main/java/org/aquilagraph/application/AnalysisRun.java), constructor, `diagnostics`, and `isPublishable`; [AnalysisQualification](../../src/main/java/org/aquilagraph/application/AnalysisQualification.java), `assess`; [CompletedRunPublicationEligibility](../../src/main/java/org/aquilagraph/application/CompletedRunPublicationEligibility.java), `accepts`.
2. [AnalysisRequest](../../src/main/java/org/aquilagraph/application/AnalysisRequest.java), constructor; [JavaFrontend](../../src/main/java/org/aquilagraph/frontend/JavaFrontend.java), both `analyze` overloads; [CompilerConfiguration](../../src/main/java/org/aquilagraph/identity/api/CompilerConfiguration.java), validation.
3. [AnalysisApplicationService](../../src/main/java/org/aquilagraph/application/AnalysisApplicationService.java), `execute`; [AnalysisExecution](../../src/main/java/org/aquilagraph/application/AnalysisExecution.java), constructor; [PublicationStatus](../../src/main/java/org/aquilagraph/application/PublicationStatus.java); [CompletedRunPublicationService](../../src/main/java/org/aquilagraph/application/CompletedRunPublicationService.java), `publish`; [AnalysisRepositoryException](../../src/main/java/org/aquilagraph/application/AnalysisRepositoryException.java), retained run and cause.
4. [Neo4jAnalysisRunRepository](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunRepository.java), `save`, `ensureSchema`, `saveProjection`, and `close`; [repository design](../design/analysis-repository.md#publication-phases-and-failures).
5. [AnalyzeCommandArguments](../../src/main/java/org/aquilagraph/cli/AnalyzeCommandArguments.java), `toAnalysisRequest`; [AnalyzeCommandParser](../../src/main/java/org/aquilagraph/cli/AnalyzeCommandParser.java), `parse` and `ParseState`; [AnalyzeCommandExecutor](../../src/main/java/org/aquilagraph/cli/AnalyzeCommandExecutor.java), `execute`; [AnalyzeCommandPresenter](../../src/main/java/org/aquilagraph/cli/AnalyzeCommandPresenter.java), presentation methods; [AnalyzeCommand](../../src/main/java/org/aquilagraph/cli/AnalyzeCommand.java), `run`; [AquilaGraphApplication](../../src/main/java/org/aquilagraph/AquilaGraphApplication.java), `main`.
