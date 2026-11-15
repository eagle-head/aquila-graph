# Analysis Contract

This document defines the shared contract for Java source analysis and optional publication. Detailed Java semantics belong to the [Java analysis design](../design/java-analysis.md); stored identities and properties belong to the [Neo4j schema](../repository/neo4j-schema.md). These are implementation contracts, not a report that any particular deployment or input has passed verification.

**Status:** the Java/CLI surface coordinates independent source and CFG results through the native application contracts. The sections below define production, derivation, retention, and query obligations. This document performs no new runtime verification or independent recertification.[1][3]

## Inputs and boundaries

`AnalysisComputationRequest` contains an `AstProductionRequest` and an optional `CfgSelection`; source input, compiler configuration, and AST profile remain owned by the source request. `AnalysisRequest` combines this computation request with an independent publication intent. `AnalysisComputation.analyze` produces source and optionally derives CFG without storage; `AnalysisUseCase.execute` applies `DO_NOT_SAVE`, `SAVE_COMPLETED`, or `SAVE_COMPLETE_AST` only after normal computation. Storage intent is not part of computed request identity. The existing CLI requests all bodies with the bounded profiles and exposes only no-save or completed-run publication.[1][2][3][5]

`AnalysisCapabilities` is the public composition boundary. Its factories return five independent input interfaces: `AnalysisComputation`, `ExistingSourceDerivation`, `AstRetention`, `AstQuery`, and `AnalysisUseCase`. Computation receives `AstProducer` and `CfgDeriver`. Existing-source derivation receives only `CfgDeriver`. Retention receives `AstRetentionRepositoryFactory`, and query receives `AstQueryRepositoryFactory`. Coordinated execution receives `AnalysisComputation`, `AstRetentionRepositoryFactory`, and `AnalysisRunRepositoryFactory`. Repository acquisition is deferred until an eligible save or an explicit lookup. The implementation owners are package-private.[1][3][7]

The producer returned by `JavaFrontends.standard()` translates `IOException` and `IllegalArgumentException` from source capture to `AnalysisInputException`, and analysis-environment `IOException` to `AnalysisEnvironmentException`. Its public `AstProducer.produce(AstProductionRequest)` accepts either a root or a captured snapshot through the same request contract. Lazy traversal `UncheckedIOException` retains its actual family and cause. The concrete frontend's checked-I/O snapshot overload is internal to the frontend module, not another public application input. Invalid direct arguments and failures outside the declared translation regions retain their actual families; the CLI does not promise a controlled result for every failure.[2][5] The [source traversal controls](../../src/test/java/org/aquilagraph/frontend/internal/JavaSourceTraversalFailureTest.java) describe the preserved lazy-traversal boundary.

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

`AnalysisExecution` retains the computed run and independent publication outcome. A repository failure cannot rewrite analysis completion. One internal `PublicationCoordinator` applies both save policies. An effective `RepositoryException` with an authoritative replacement observation returns publication `FAILED`; another runtime failure, or a save failure without that observation, throws `AnalysisPublicationException` with the computed subject and original failure evidence. The [repository crossing](#target-repository-failure-crossing) defines this shared classification.[3][6]

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

| Outcome | Prepared standard output | Prepared standard error | Exit |
| --- | --- | --- | ---: |
| Usage rejection | None | Rejection message, then usage | 2 |
| Named input failure | None | `Input rejected: {quotedDescription}`, failure reference and catalog | 2 |
| Named environment failure | None | `Analysis failed: {quotedDescription}`, failure reference and catalog | 1 |
| Returned `COMPLETE` run without observed output failure | Summary, then publication fields | Ordered diagnostics and qualification reasons | 0 |
| Returned `PARTIAL` or `FAILED` run | Summary, then publication fields | Ordered diagnostics and qualification reasons | 1 |
| Returned `REJECTED` run | Summary, then publication fields | Ordered diagnostics and qualification reasons | 2 |
| Returned publication `FAILED` | Retained completed-run summary and publication fields, including `Repository: FAILED` | Ordered diagnostics and qualification reasons, then `Repository save failed: {quotedDescription}`, failure reference and catalog | 3 |
| Observed output failure after semantic success | Prepared summary and publication fields | Prepared diagnostics and qualification reasons | 4 |

The [structured CLI report](command-line-reporting.md#successor-text-contract) defines the fields, order, and quoting for each prepared channel payload. [Emission and exit classification](command-line-reporting.md#exit-and-process-boundary) retain semantic exits `1`, `2`, and `3` even when output failure is observed; only semantic success changes from `0` to `4`. Publication `FAILED` retains precedence over the run's normal completion exit code. Abrupt publication failures are not converted into that returned CLI outcome.[5]

`AnalyzeCommand.execute` establishes the operational outcome independently of output. `CommandRunner.run` borrows result and diagnostic `CommandDestination` values, executes once, prepares both payloads before emission, and returns `CommandInvocation` with the original outcome and independent channel observations. `CommandInvocation.exitCode()` applies `CommandExitPolicy`; the process entry requests `System.exit` only for a normally returned nonzero code. Destinations are neither closed nor retried. These observations promise neither successful external output delivery nor ordering between separately observed streams. Abrupt presentation does not return an ordinary invocation and may leave earlier output without a complete report.[5]

`AnalysisRun.diagnostics()` presents source occurrences, CFG diagnostics in canonical method order (or rejected-request diagnostics), then application diagnostics without value deduplication. Diagnostic compatibility, including the earlier parenthesized-assignment boundary, still requires explicit verification before claiming unchanged historical CLI behavior. Removed compatibility adapters are not evidence of that preservation.[1][5]

## Target independent analysis contracts

The [selected decomposition](../design/java-analysis.md#separation-decision) owns responsibilities. Public operations below define required behavior; complete implementation conformance requires separate acceptance evidence. `AstProducer` belongs to Source Model; `CfgDeriver` belongs to Control Flow. Neither requires the other's implementation.

| Operation | Preconditions and input | Postcondition / observable failure |
| --- | --- | --- |
| `AstProducer.produce(AstProductionRequest): AstOutcome` | Root or captured snapshot, compiler configuration, declared AST profile; no CFG collaborator. | Nonempty production returns normally only after normal compiler work/closure/environment verification. Empty capture opens no compiler and returns `UNAVAILABLE` with initial provenance. Normal outcomes expose immutable source result/provenance/diagnostics; diagnosed required-root withholding is `UNAVAILABLE`. Production I/O/environment/unmatched failures preserve their family and cause. |
| `AstResult` node/declaration/body/evidence queries | Intrinsically admitted source result. | Queries work after compiler closure and without CFG; unknown body/occurrence queries are absent, malformed query arguments reject. Evidence states remain explicit; no query computes CFG readiness. |
| `CfgDeriver.derive(AstResult, CfgRequest): CfgOutcome` | Exact source identity, declared compatible versions, ordered unique method selection; source profile/evidence visible through read API. | Rejected request or ordered per-method admission/construction outcomes; AST unchanged. No frontend acquisition or mutable builder. |
| `AnalysisComputation.analyze(AnalysisComputationRequest): AnalysisRun` | Source production request and optional immutable `CfgSelection`, with ordered explicit keys or `ALL_BODIES` scope and a complete CFG profile. No publication intent or repository collaborator. | Available production binds selection to its exact result identity before CFG derivation. Explicit keys remain unchanged; `ALL_BODIES` selects canonical produced bodies. Unavailable production retains the unbound selection and makes no CFG call. Returns independent AST/CFG results and overall qualification without publication. |
| `ExistingSourceDerivation.deriveExisting(AstOutcome, CfgRequest): AnalysisRun` | Previously established outcome and explicit request; available AST and exact compatible identity permit derivation. | Returns the computed run without production or publication, retaining AST on refusal/failure. Unavailable AST rejects CFG; no invented source result. Retention is a separate operation. |
| `AstRetention.retainAst(AstPublicationRequest): PublicationOutcome` | Explicit `SAVE_COMPLETE_AST` or `DO_NOT_SAVE`; exact completed AST outcome for eligibility. | Lazy repository open/save/close, independent of CFG/request completion; abrupt publication failure retains the standalone AST subject. |
| `AstQuery.findAst(AstIdentity): AstLookup` | Requested qualified identity; lazy operation-scoped repository acquisition. | After normal read/decode/owned closure and returned-identity validation, `FOUND` validated source result, `NOT_FOUND`, or `INCOMPATIBLE`. Query failure carries requested identity and effective causes, returns no lookup and creates no publication outcome. No CFG derivation or graph loading occurs. |
| `AnalysisUseCase.execute(AnalysisRequest): AnalysisExecution` | Independent computation request and explicit publication intent. | Calls computation, then applies publication only on normal return. Retains the complete `AnalysisRun` as the coordinated publication subject for either save intent. Returns that run and a separate publication outcome. |

### Target outcome relations

For production, the application retains the requested scope and ordered keys separately from the effective method-key list. This preserves an unbound request when production is unavailable and preserves invalid explicit selections through CFG admission. An absent `CfgSelection` yields `NOT_REQUESTED`. An explicit empty list is a request. Production binds only an established result identity. `deriveExisting` keeps the caller's requested identity even when the supplied outcome has no result. The [selection identity contract](../design/source-snapshots.md#application-selection-and-cfg-request-identity) defines the framed variants.

AST presence and completion follow [intrinsic source qualification](../design/java-analysis.md#ast-result-and-intrinsic-validity). `COMPLETE` and `INCOMPLETE` have an admitted queryable `AstResult`; `UNAVAILABLE` has none. Invalid supplied structure is rejected by admission, not represented by a fabricated valid AST. Missing source-profile-required facts and compiler errors prevent `COMPLETE`; facts optional to that profile do not. CFG availability never determines AST completion.

The [module service and provider identity contract](../design/java-analysis.md#module-service-and-provider-identities) makes the three resolved module endpoint families required for `COMPLETE` in `java-source-expanded/8`. That section owns their source associations, qualification, and immutable queries. The [retained-format contract](../design/ast-value-format.md#format-source-profile-and-producer-revision) defines the active encoding, independent of profile and producer revision.[9]

CFG request state is `REJECTED`, `COMPLETE`, or `PARTIAL`. Selected bodyless declarations yield `NOT_APPLICABLE`; selected concrete methods yield `AVAILABLE`, `SOURCE_UNAVAILABLE`, `UNSUPPORTED`, `INSUFFICIENT_EVIDENCE`, `ERRONEOUS_EVIDENCE`, or `FAILED`. Application coverage assigns `NOT_REQUESTED` only to nonselected declarations. These cases are distinguishable through public queries rather than an empty optional graph alone. Ordered blockers retain separate identities/causes; complete CFG means all selected bodies available and no CFG error. Available graphs for unaffected bodies may coexist with AST incompleteness and never upgrade it.

Overall request state is `REJECTED`, `COMPLETE`, `PARTIAL`, or `FAILED`, derived by the application from the requested operations. AST-only completion needs complete AST, no graph. Selected bodyless declarations impose no graph obligation. Refused requested CFG produces a partial request; declared construction failure produces a failed request, retaining AST and previously validated method graphs. Rejected CFG identity/selection produces a rejected request with any prior AST still attached. See [coordination qualification](../design/java-analysis.md#target-coordination-completion-and-failures) for exact rules.

AST diagnostics, CFG diagnostics, application diagnostics and publication failures retain their creating owner and order. Presentation can compose those streams without redefining domain validity or value-deduplicating events. `CfgDerivationException` is an abrupt declared failure carrying completed CFG progress; `AnalysisDerivationException` retains that cause and the established AST. Other effective failures remain abrupt; fatal errors are not converted into successful partial results.

## Target publication and retention

**Explicit target decision:** preserve the legacy `SAVE_COMPLETED` combined-run contract and add a separate opt-in completed-AST retention contract. This decision permits an independently complete AST to be saved when CFG is absent, refused, or failed. It does not admit incomplete AST publication, automatically save results, add CLI flags, or select a new backend.

| Subject / intent | Eligibility | Storage result |
| --- | --- | --- |
| Combined run / `SAVE_COMPLETED` | `AnalysisRun.isPublishable()` requires complete source and exact qualified graph coverage for every required concrete method. Overall `COMPLETE` alone is insufficient. | Publication table above; no AST projection implied. |
| Target AST / `DO_NOT_SAVE` | No attempt. | `NOT_REQUESTED`, regardless of AST state. |
| Target AST / `SAVE_COMPLETE_AST`, AST `COMPLETE` | Eligible irrespective of CFG/request outcome. | Acquire the retention capability, validate the exact complete subject, map and validate its projection, then replace the stored AST. `SAVED` requires acknowledged replacement and normal internal and enclosing closure. |
| Target AST / `SAVE_COMPLETE_AST`, AST `INCOMPLETE` or `UNAVAILABLE` | Ineligible; do not open repository. | `NOT_ELIGIBLE`; in-memory established facts still available. |
| Target eligible subject / attempted operation fails | Failure does not rewrite computed analysis outcomes. | `FAILED` when within declared returned publication contract; named abrupt publication exception otherwise. Retain effective primary, suppressed failures, and phase-specific commit knowledge. |

`PublicationOutcome` retains intent, status, exact subject identity, replacement knowledge, and an optional declared failure. `SAVE_COMPLETED` identifies the computed request key through `PublicationSubjectIdentity.CompletedRun`; AST intent identifies an available `AstIdentity` through `PublicationSubjectIdentity.Ast`. An unavailable AST has no fabricated AST identity. `NOT_REQUESTED` and `NOT_ELIGIBLE` carry `NOT_STARTED`; `SAVED` requires `KNOWN_COMMIT`; `FAILED` requires the exact attempted subject and a `PublicationFailure` with matching observed knowledge. Unknown acknowledgement is not success. A close failure after known commit retains that fact but prevents `SAVED`. Independent explicit AST retention remains usable after CFG refusal or failure because its eligibility derives from the established AST alone.[3][6]

Both save intents use the same outward failure contract. A carrier with an effective application-port `RepositoryException` and present replacement knowledge returns `FAILED`. Any other runtime primary, or absent save knowledge, crosses as `AnalysisPublicationException`, retaining the exact computed subject, operation carrier, actual cause, and suppressed chain. Mapping exceptions are not relabeled as driver failures. Classification uses the effective primary, never a suppressed cleanup failure or the carrier's wrapper type. A fatal primary `Error` remains abrupt in its actual family. A later cleanup error remains suppressed on an already established primary; it does not replace that primary. Query failures remain failures of `findAst`, never `NOT_FOUND` or a publication attempt.[3][6]

`ComputedPublicationSubject` is an application-owned closed choice between an AST subject referencing `AstOutcome` and a coordinated subject referencing `AnalysisRun`. It retains existing immutable results rather than making either domain type inherit an application type. Exactly one subject variant is present in the exception; attempted subject identity must match that value.

### Qualification explanations

`AstResult.qualification()` and `AstOutcome.qualification()` expose the same
immutable `SourceQualification` for an available result. `completion()` derives
from that assessment. Source admission establishes all blocking reasons from the
actual admitted input, its original profile, and production revision. Empty
capture retains an absent revision and compiler environment. Reasons retain typed
categories, evidence roles, input-local subjects, available origins, exact source
diagnostic list slots and emission addresses. A diagnostic evidence value without
an established event association remains separately labeled evidence. Repeated
values and keys do not identify or merge events. Reasons are ordered by assessment
rule, captured file, and canonical occurrence; complete source has no blocking
reason. These are derived queries, excluded from retained encoding and identity.
See [SourceQualification](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/api/SourceQualification.java),
[SourceQualificationReason](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/api/SourceQualificationReason.java),
and [SourceRequirements.assess](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/internal/SourceRequirements.java).

`AnalysisRun.create` assesses exact source/CFG owners and application diagnostic
occurrences once. The run retains `RequestQualification`, and `completion()`
derives from it. Its constructor checks the source subject, selection, context
correspondence, and assessed diagnostic list. Typed request reasons retain source,
rejected selection, refused/failed method, native method error, or exact
application diagnostic slots. An error on an `AVAILABLE` graph retains `FAILED`
request qualification; native errors on a bodyless `NOT_APPLICABLE` method retain
`PARTIAL`. Both retain exact method diagnostic slots without changing the method
state or restricting previously admitted values. All independent causes survive precedence
`REJECTED`, `FAILED`, `PARTIAL`, `COMPLETE`; method blockers and abrupt derivation
failures retain their existing domain owners. See
[AnalysisQualification](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisQualification.java),
[RequestQualificationReason](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/RequestQualificationReason.java),
and [AnalysisRun](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisRun.java).

### Canonical diagnostic occurrences

`AnalysisRun.diagnostics()` returns the assessment-owned `AnalysisDiagnostics` contract.
It composes source events, rejected CFG-request events or method events in canonical
source declaration order, then application events. Each `AnalysisDiagnosticOccurrence`
retains its typed native owner and zero-based local slot; its diagnostic value is read
from that owner. Equal values, equal keys, and repeated references remain separate
occurrences. `values()` is a projection of this one sequence for value-only consumers.

The catalog resolves source qualification reasons, request qualification reasons,
CFG outcomes, method outcomes, and exact blocker slots to the same occurrence objects.
Source and CFG retain authority over their assessments and native associations. Source
reason evidence without an established diagnostic slot remains evidence and receives
no invented occurrence. A source-level request reason relates to its source outcome's
native event stream; its nested source reasons retain their more specific associations.

Occurrences and reason/context lookups are scoped to the retained assessment by object
identity, not diagnostic equality or key matching. A foreign occurrence, reason, or
context is rejected even when its value is equal. An `AnalysisRun` accepting an equal
representation retains the assessment's actual context owners and application slots.
The catalog is derived immutable state and does not add a global identifier, change
AST/CFG content identity, or introduce a persisted format.

The CLI consumes the catalog and performs only display numbering and formatting of
its relationships. It has no source counts, method offsets, or diagnostic aggregation
policy. Completed-run eligibility and the Neo4j mapper consume the catalog's values;
the existing ordered `HAS_DIAGNOSTIC` relationships remain the persistence contract.

Sources: [AnalysisDiagnostics](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisDiagnostics.java),
[AnalysisDiagnosticOccurrence](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisDiagnosticOccurrence.java),
[AnalysisRun](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisRun.java),
and [Neo4jEvidenceMapper](../../src/main/java/org.aquilagraph.repository/org/aquilagraph/repository/internal/neo4j/Neo4jEvidenceMapper.java).

### Target repository failure crossing

The application-owned factories take `RepositoryOperationContext`, which carries an operation kind and a typed `PublicationSubjectIdentity`. `AstRetentionRepositoryFactory.open(context)` takes `SAVE_AST` and the exact `AstIdentity` and returns an `AstRetentionRepository` for one `saveComplete` attempt. `AstQueryRepositoryFactory.open(context)` takes `QUERY_AST` and the exact `AstIdentity` and returns an `AstQueryRepository` for one `find` attempt. Each attempt must match the acquired identity and consumes the operation even if subject validation fails.[6][7]

`AnalysisRunRepositoryFactory.open(context)` takes `SAVE_COMPLETED_RUN` and the computed request key. Direct completed-run callers may save successive runs. The Neo4j adapter establishes an exact context for each supplied nonnull run before mapping. A null argument is rejected before that transition. Driver close carries the latest established context and observation, using the acquisition context if no save established one. The coordinated use case acquires, saves once, and closes. The factory owns cleanup of accessible resources until a successful transfer. The application closes every repository it acquires, including after work failure.[4][6][7]

`application.port.RepositoryOperationException` is the public, extensible, technology-neutral runtime carrier for both repository families. It retains context, `Optional<CommitKnowledge>`, and the exact `effectivePrimary: RuntimeException`. The common contract has no database phase enumeration. `ApplicationRepositoryOperationException` identifies application-observed `ACQUISITION`, `VALIDATION`, `OPERATION`, or `CLOSURE`. The Neo4j adapter's subtype owns its internal phase vocabulary. The adapter applies its exception translation in the declared acquisition and driver-work regions before creating its carrier. Mapping and unmatched runtime failures retain their actual family. Distinct observed cleanup failures are suppressed on the primary in encounter order, retaining their own observations and causes. If cleanup throws the same observed throwable object, it remains the primary with no self-suppression. A close-only failure becomes primary. A carrier from a different operation is rejected.[6][7]

`PublicationFailure` admits only a save carrier with an effective `RepositoryException` and present replacement knowledge. It retains that carrier and its complete cause/suppressed evidence. `AnalysisPublicationException.failure()` retains the carrier, while `getCause()` exposes the exact effective primary; its subject remains the standalone `AstOutcome` or the full coordinated `AnalysisRun`. Adapter-specific detail remains inspectable through the carrier subtype, as defined by the [repository phase mapping](../design/analysis-repository.md#target-phase-and-commit-mapping). A fatal primary remains abrupt in its actual family; applicable cleanup still runs, without a fabricated normal result.[3][6]

`CommitKnowledge` concerns replacement of the named AST or completed-run subject, not schema changes: `NOT_STARTED` means replacement has not started; `KNOWN_NONCOMMIT` requires an established noncommit; `UNKNOWN` records an attempted replacement without conclusive acknowledgement; `KNOWN_COMMIT` requires acknowledged commit. Absent carrier knowledge means the save exit supplied no authoritative observation; it must not be filled with `UNKNOWN` or `NOT_STARTED`. `OwnedRepository` clears its prior knowledge before entering a save, adopts a returned carrier's observation, and records `KNOWN_COMMIT` on normal save return. A normal `save` or `saveComplete` acknowledges replacement and internal cleanup; `SAVED` additionally requires normal enclosing close. Cleanup preserves prior known commit or uncertainty rather than inventing rollback or success.[6][7]

`AstQuery.findAst` propagates `RepositoryOperationException` for query acquisition/read/decode, returned-identity validation, or owned-close failures, retaining requested identity and cause/suppressed evidence. `QUERY_AST` always has present `NOT_STARTED` knowledge. It has no computed publication subject or `PublicationOutcome`, and returns no lookup on failure. Every normal lookup, including `NOT_FOUND` or `INCOMPATIBLE`, requires normal read-session and enclosing repository closure. Neither query failure nor codec rejection is absence.[6][7]

The public API and extension contracts are exported from `application` and `application.port`. `application.internal.repository.RepositoryOperationBoundary` is implementation support, exported only to the repository module. External adapters extend the public failure contract without acquiring access to that helper. The application module depends on identity/source/CFG contracts and has no dependency on the concrete frontend, CLI, or storage implementation.[6][8]

An AST-only request does not become completed-run-save eligible through its independent `COMPLETE` state. `SAVE_COMPLETED` uses the shared coordinator with the distinct coverage/error/exact required-graph gate; it never falls back to AST storage. Independent retention uses `SAVE_COMPLETE_AST`. Compatibility checks must preserve AST-only completed-run ineligibility and existing CLI completed/incomplete outcomes.[1][3]

The [retained-format contract](../design/ast-value-format.md) defines the single active source encoding and the distinction between format, profile, and semantic-production revision. The [repository design](../design/analysis-repository.md#target-ast-retention) and [retention schema](../repository/neo4j-schema.md#target-ast-retention-schema) govern lookup and metadata-only refusal of retired formats. Actual durable publication and retrieval require separate storage observations. CLI compatibility requires executable evidence for command grammar, printed completion labels, output order, and exit codes.[5][9][10]

## References

1. [AnalysisCapabilities](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisCapabilities.java), five composition factories; [SourceComputation](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/SourceComputation.java), `analyze`; [EstablishedSourceDerivation](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/EstablishedSourceDerivation.java), `deriveExisting`; [AnalysisRun](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisRun.java), `create`, `diagnostics`, and `isPublishable`; [AnalysisQualification](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisQualification.java), `assess`.
2. [AnalysisComputationRequest](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisComputationRequest.java) and [AnalysisRequest](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisRequest.java), request boundaries; [AstProductionRequest](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/api/AstProductionRequest.java), source ownership; [JavaFrontends](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/JavaFrontends.java), public producer composition; [JavaFrontend](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaFrontend.java), `produce` translation regions; [CompilerConfiguration](../../src/main/java/org.aquilagraph.identity/org/aquilagraph/identity/api/CompilerConfiguration.java), validation.
3. [AnalysisExecutionCoordinator](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisExecutionCoordinator.java), `execute`; [AnalysisExecution](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisExecution.java), exact publication subject checks; [PublicationCoordinator](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/PublicationCoordinator.java), `apply` and `publish`; [PublicationOutcome](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/PublicationOutcome.java), admission; [AnalysisPublicationException](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisPublicationException.java), subject, carrier, and cause.
4. [Neo4jAnalysisRunRepository](../../src/main/java/org.aquilagraph.repository/org/aquilagraph/repository/internal/neo4j/Neo4jAnalysisRunRepository.java), `save`, `replace`, and `close`; [repository design](../design/analysis-repository.md#publication-phases-and-failures).
5. [AnalyzeCommandArguments](../../src/main/java/org.aquilagraph.cli/org/aquilagraph/cli/AnalyzeCommandArguments.java), `toAnalysisRequest`; [AnalyzeCommandParser](../../src/main/java/org.aquilagraph.cli/org/aquilagraph/cli/AnalyzeCommandParser.java), `parse` and `ParseState`; [AnalyzeCommandExecutor](../../src/main/java/org.aquilagraph.cli/org/aquilagraph/cli/AnalyzeCommandExecutor.java), `execute`; [AnalyzeCommand](../../src/main/java/org.aquilagraph.cli/org/aquilagraph/cli/AnalyzeCommand.java), `execute` and `prepare`; [CommandDocumentComposer](../../src/main/java/org.aquilagraph.cli/org/aquilagraph/cli/CommandDocumentComposer.java), `compose`; [CommandRunner](../../src/main/java/org.aquilagraph.cli/org/aquilagraph/cli/CommandRunner.java), `run`; [CommandOutputEmitter](../../src/main/java/org.aquilagraph.cli/org/aquilagraph/cli/CommandOutputEmitter.java), `emit`; [CommandInvocation](../../src/main/java/org.aquilagraph.cli/org/aquilagraph/cli/CommandInvocation.java), `exitCode`; [CommandExitPolicy](../../src/main/java/org.aquilagraph.cli/org/aquilagraph/cli/CommandExitPolicy.java), `classify`; [AquilaGraphApplication](../../src/main/java/org.aquilagraph.cli/org/aquilagraph/AquilaGraphApplication.java), `main`.
6. [RepositoryOperationException](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/port/RepositoryOperationException.java), extension contract; [ApplicationRepositoryOperationException](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/ApplicationRepositoryOperationException.java), application boundaries; [OwnedRepository](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/OwnedRepository.java), `use`, `save`, and `observe`; [PublicationFailure](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/PublicationFailure.java), declared failure admission; [RetainedAstQuery](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/RetainedAstQuery.java), exact lookup validation; [RepositoryOperationBoundary](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/internal/repository/RepositoryOperationBoundary.java), exact carrier context and cleanup.
7. [AST retention factory](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/port/AstRetentionRepositoryFactory.java) and [retention port](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/port/AstRetentionRepository.java), one `saveComplete` attempt; [AST query factory](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/port/AstQueryRepositoryFactory.java) and [query port](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/port/AstQueryRepository.java), one `find` attempt; [completed-run factory](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/port/AnalysisRunRepositoryFactory.java) and [completed-run port](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/port/AnalysisRunRepository.java), contextual acquisition and sequential saves; [repository design](../design/analysis-repository.md#target-phase-and-commit-mapping), adapter detail.
8. [Application module](../../src/main/java/org.aquilagraph.application/module-info.java), dependencies, API/SPI exports, and qualified support export; [repository module](../../src/main/java/org.aquilagraph.repository/module-info.java), adapter dependency direction.
9. [AstCodec](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/retention/AstCodec.java), `encode`, `materialize`, and `decode`; [AstResult](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/api/AstResult.java), `supportsSourceModelVersion`.
10. [Development verification scope](../build-quality.md#development-verification-scope), isolated evidence and deferred real-environment verification; [repository verification scope](../design/analysis-repository.md#stored-projection-and-verification-scope), required storage observations.
