# Analysis Repository Design

The application owns the repository port and publication eligibility. The Neo4j adapter owns mapping, schema inspection, replacement, and database resources. The [analysis contract](../contracts/analysis.md#publication-eligibility) defines caller outcomes; the [schema](../repository/neo4j-schema.md) defines the stored surface.[1][2]

## Responsibilities and dependencies

| Owner | Responsibility |
| --- | --- |
| `AnalysisApplicationService`, `AstRetentionService`, and `CompletedRunPublicationService` | Coordinate independent computation and publication, retain intent, acquire eligible storage lazily, and close before returning `SAVED`. |
| `application.port` | Own independent AST ports, legacy completed-run ports, publication coordination, and repository failure contracts independently of Neo4j. |
| Composition root | Supply the concrete adapter and connection settings. |
| `Neo4jAnalysisRunRepository` | Own driver and sessions, exact constraint inspection, managed replacement, and exception translation in its declared regions. |
| `Neo4jAnalysisRunMapper` and focused mappers | Convert a completed run into a projection without accessing the driver. |
| `Neo4jProjectionBuilder` and projection values | Confine mutation to one mapping operation and validate identities, properties, ordering, and endpoints before publication. |
| Direct repository caller | Supply a truthful completed run and close the repository it opened. |

Application and CLI code depend on application-owned ports rather than the concrete storage package. Adapter dependencies and the remaining permitted package directions are enforced by the [build architecture policy](../build-quality.md#architecture-policy).[1][2]

## Publication phases and failures

`save` first maps the run outside its database-exception translation region. The mapper refuses null or noncompleted input. The adapter then prepares schema in one session, inspects both required named constraints, and closes that session before replacement. Definition inspection checks name, uniqueness type, node entity type, label, and ordered properties.[2][3]

Replacement opens a separate session and executes a managed write callback. Each attempt deletes existing `AnalysisEntity` nodes for the supplied `runId`, creates the replacement nodes, and creates their relationships. The immutable projection is created before the callback, so driver replay does not remap the input.[2]

| Phase | Failure or partial-effect boundary |
| --- | --- |
| Driver acquisition | If later setup fails after returning an accessible driver, attempt driver close and preserve the original failure with cleanup evidence. |
| Mapping and projection validation | No schema or replacement effect has started. Preserve the actual throwable family. |
| Schema creation and inspection | Constraint effects may remain after a later failure. Missing, incompatible, or unreadable required definitions prevent replacement. |
| Schema-session close | Close must finish normally before opening the replacement session. |
| Managed replacement | Delete and recreate belong to one transaction per attempt. An established noncommit preserves the old projection; missing commit acknowledgement leaves whole-run commit unknown. |
| Publication-session close | A failure after known commit does not undo the committed projection. |
| Repository close | The application attempts close before returning `SAVED`; close failure does not prove rollback or resource release. |

`Neo4jException` from the adapter's schema/replacement region becomes `RepositoryException`. The application retains the completed run for an effective `RepositoryException`. Mapping exceptions outside that region, unmatched runtime failures, and errors are not given an invented successful result or a universal CLI translation.[1][2]

The replacement contract provides no additional simultaneous-publisher isolation, public commit-state result, or automatic legacy-data migration. Required behavior and observed results remain distinct: the source scopes establish operation ordering, while deployed database effects require the relevant integration observation.[2][4]

## Stored projection and verification scope

Publication retains run/version identity, captured input and environment, declarations, diagnostics, source origins, method analyses, and CFG entities. The completed-run projection excludes the full source aggregate and its annotation/type-use trees; the separate AST projection retains them under its own explicit contract. Stored names and identity algorithms are compatibility contracts, not implementation conveniences.[3][4]

Neo4j Community is the supported verification baseline. Selective role-based denial of constraint inspection is outside that baseline and remains unexecuted; this scope does not waive `SHOW CONSTRAINTS`, definition validation, tests, or quality rules. Deployment and credentials are defined in the [runbook](../../deployment/neo4j/README.md#supported-verification-scope).

## Target AST retention

Existing completed-run storage and independent completed-AST storage have separate adapters within the same Neo4j context. `Neo4jAstResultRepository` implements the single-operation AST port; `Neo4jAstResultCodec` retains its complete envelope and materialized closed-schema fields. Required Community integration observations are defined below; current acceptance remains separate. The [analysis contract](../contracts/analysis.md#target-publication-and-retention) owns eligibility. Storage does not decide AST source-profile completion or CFG applicability. The [repository class view](../../diagrams/class/analysis-repository.puml) references those canonical domain definitions.

Application-owned `AstResultRepository` provides `saveComplete(AstOutcome): void`, `find(AstIdentity): AstLookup`, and `close`; `AstResultRepositoryFactory.open(RepositoryOperationContext)` acquires it lazily for one exact `SAVE_AST` or `QUERY_AST` subject. The [application port crossing](../contracts/analysis.md#target-repository-failure-crossing) owns context, failure carrier and outward classification. `saveComplete` requires an intrinsically validated, truthfully qualified `COMPLETE` outcome with exact identity. Reject invalid/incomplete/unavailable or mismatched input before database work. Direct callers remain responsible for production authenticity; mapper validation does not manufacture compiler evidence. `find` returns `NOT_FOUND` only when the exact subject is absent, `INCOMPATIBLE` for an unsupported stored version, and `FOUND` only after supported stored facts decode and pass intrinsic admission and exact identity/completion validation. Absence and incompatibility bypass decode. Read/decoding contract failures and driver failures remain observable, not absent results.

`Neo4jAstResultCodec` maps and decodes the entire public AST result: source/input/environment provenance, canonical declarations, written/implicit distinctions, source nodes/ordered child roles, typed semantic targets, recursive types/annotations/defaults, body ownership, evidence states/values, coverage expectations/support/gaps and ordered diagnostic events. Its `decode(storedFacts, identity): AstOutcome` returns the validated complete outcome; the repository prepares `AstLookup.FOUND(outcome)` from that return, and owns the absent/incompatible branches that bypass decoding. It passes copied facts and qualification evidence through `AstResultAdmission.admit`, the restricted Source Model value-admission facade, without a frontend/compiler, mutable source builder or CFG builder. Admission derives and checks identity/completion rather than trusting serialized labels. Reference values are serialized under their canonical owners; no mirror, callback, builder state or graph-lowering plan is persisted as AST. A lossless codec and decoder are mandatory; a source-node subset cannot claim to implement this contract.

Mapping/validation precedes schema work. Prepare and inspect the AST format's required constraints in a separate session, close it normally, then replace one `astResultKey` scope in one managed write attempt using an immutable projection. Driver retry reuses that projection. Apply the current resource/effect rules: schema effects may remain; established noncommit preserves prior subject; acknowledgement loss leaves whole-subject commit unknown; known commit survives later close failure. `SAVED` requires save and enclosing close normal return. `find` closes its read session before a normal result; close failure prevents successful lookup return. Simultaneous-publisher isolation and automatic migrations are not added.

Application publication failure envelopes retain the already computed AST/CFG/request outcomes, effective primary and suppressed causes, subject identity, phase, and available commit knowledge. `PublicationOutcome` is separate from analysis completion. The explicit `retainAst` operation is usable after CFG refusal/failure; it does not happen automatically. The existing CLI `--persist` still follows the legacy completed-run path until an explicit interface change is implemented.

### Target phase and commit mapping

The AST adapter maintains operation-local progress for the application-owned context and emits `RepositoryOperationException` for runtime failures. It exposes no driver/session objects. The table defines the phase mapping used by the adapter; isolated failure tests and actual database observations establish different properties.

| Phase | Observation and subject-replacement knowledge on failure |
| --- | --- |
| `ACQUISITION` | Factory owns partial driver/setup cleanup until transfer; `NOT_STARTED`. Preserve primary and cleanup causes. |
| `VALIDATION` / `MAPPING` | Validate operation/identity/completion and encode immutable projection before schema work; `NOT_STARTED`. Actual argument/admission/mapping family is the effective primary. |
| `SCHEMA` / `SCHEMA_SESSION_CLOSE` | Create/inspect exact constraints and close the schema session before replacement; `NOT_STARTED`. Constraint effects may remain. |
| `REPLACEMENT` | Record that replacement started before invoking the managed write. Use `KNOWN_NONCOMMIT` only on established noncommit, `KNOWN_COMMIT` only on acknowledged whole-subject commit, otherwise `UNKNOWN`. An exception alone does not establish rollback. |
| `PUBLICATION_SESSION_CLOSE` | Carry the replacement observation; normal acknowledged save followed by close failure remains `KNOWN_COMMIT`. |
| `READ` / `DECODE` / `READ_SESSION_CLOSE` | Query exact subject; decode only supported present facts; close the read session before returning any lookup; always `NOT_STARTED`. Decode/admission/identity failures preserve their actual family. |
| `REPOSITORY_CLOSE` | Attempt enclosing close after work, carrying the operation's prior knowledge unchanged. A failed close does not prove resource release. |

The driver-exception translation region for the new adapter covers schema, replacement/read and their session cleanup, plus driver close. Acquisition setup, argument/admission and codec failures are outside it. Within the declared region only an effective `Neo4jException` becomes the existing application-port `RepositoryException`; unmatched runtime failures are not relabeled. The operation carrier wraps either family afterward. Legacy regions remain unchanged. If work fails, all accessible session cleanup is attempted; suppress its phase-bearing carriers on the work carrier, preserving the original primary and its existing chain. The application adds enclosing-close evidence in the same way. Fatal errors retain the fatal primary and applicable cleanup evidence without a promised normal result.

Retry reuses the immutable projection and never treats callback return as commit acknowledgement. An inconclusive attempt retains `UNKNOWN` unless later authoritative whole-operation acknowledgement establishes the final replacement; a later failed/noncommitted attempt alone cannot erase earlier uncertainty. A normal save requires that final acknowledgement and all internal closes. Direct AST-port users receive the same carrier and single-operation lifetime contract; the application does not infer internal phases from throwable names.

`RepositoryOperationBoundary` observes an owned supplier synchronously using `FutureTask.run` on the calling thread, then reads its terminal result through the typed `ExecutionException` boundary. It creates no executor or background thread and completes before the enclosing resource scope continues. This represents the complete runtime-failure contract without a direct generic exception catch, which the production PMD policy forbids. The observation unwraps the exact original runtime primary into its phase-bearing carrier; a declared carrier retains its creating phase and exact context, and an original fatal `Error` is rethrown unchanged. Existing primary and suppressed chains survive. Driver translation is a separate narrowly supplied operation and applies only in the adapter's declared driver regions. A completed observation does not clear an existing interrupt flag. Try-with-resources independently appends accessible cleanup carriers in encounter order. The [boundary implementation](../../src/main/java/org/aquilagraph/application/port/RepositoryOperationBoundary.java) owns this mechanism; it is not commit evidence and does not manufacture a normal result from a failure.

### Retention compatibility obligations

Use the distinct [AST schema namespace](../repository/neo4j-schema.md#target-ast-retention-schema); legacy `AnalysisEntity` run deletion must never delete AST-retention entities or query references. Version target AST and CFG formats and codecs; reject unsupported formats as incompatible. Graph-to-source references are qualified by `AstIdentity` and body/occurrence, with no dangling or cross-result association. Retaining a graph is optional and does not qualify AST retention. AST lookup loads no CFG; querying a legacy run cannot imply an AST exists.

Required observations include actual Community database save/close and subsequent query of a complete AST with no graph; round-trip equality of all serialized distinctions and ordering; refused/failed CFG followed by explicit AST save; unknown version and foreign graph rejection; phase-specific mapping/schema/transaction/close failures; same legacy-run replacement preserving separately retained AST. Supplied fixtures alone cannot establish compiler fidelity or persistence.

The [Community integration cases](../../src/test/java/org/aquilagraph/repository/neo4j/Neo4jAstResultRepositoryNeo4jIT.java) and [isolated driver tests](../../src/test/java/org/aquilagraph/repository/neo4j/Neo4jAstResultRepositoryTest.java) are verification entry points, not evidence that a current run passed. Preserve the distinction between actual server save/query/schema effects and supplied failure transport. Complete independent implementation acceptance remains [pending](java-analysis.md#model-acceptance-and-verification); historical test totals and unavailable execution logs are not carried forward as current results.

## References

1. [AnalysisApplicationService](../../src/main/java/org/aquilagraph/application/AnalysisApplicationService.java), `execute`; [AnalysisRunRepository](../../src/main/java/org/aquilagraph/application/port/AnalysisRunRepository.java), save/close contract; [composition root](../../src/main/java/org/aquilagraph/AquilaGraphApplication.java).
2. [Neo4jAnalysisRunRepository](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunRepository.java), acquisition, `save`, `ensureSchema`, `saveProjection`, and `close`; [import policy](../../config/checkstyle/import-control.xml).
3. [Neo4jSchema](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jSchema.java), `requireIdentityConstraints`; [Neo4jAnalysisRunMapper](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunMapper.java), `map`; [Neo4jProjectionBuilder](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jProjectionBuilder.java), validation; [Neo4jGraphProjection](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jGraphProjection.java), immutable result.
4. [Neo4j schema](../repository/neo4j-schema.md), publication surface and compatibility; [repository integration tests](../../src/test/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunRepositoryNeo4jIT.java), executable observation cases.
