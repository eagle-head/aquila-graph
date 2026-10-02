# Analysis Repository Design

The application owns the repository port and publication eligibility. The Neo4j adapter owns mapping, schema inspection, replacement, and database resources. The [analysis contract](../contracts/analysis.md#publication-eligibility) defines caller outcomes; the [schema](../repository/neo4j-schema.md) defines the stored surface.[1][2]

## Responsibilities and dependencies

| Owner | Responsibility |
| --- | --- |
| `AnalysisApplicationService` | Compute a run, retain save intent, open lazily for requested completed runs, save, and close before returning `SAVED`. |
| `application.port` | Own `AnalysisRunRepository`, its factory, and `RepositoryException` independently of Neo4j. |
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

Publication retains run/version identity, captured input and environment, declarations, diagnostics, source origins, method analyses, and CFG entities. The source aggregate and its annotation/type-use trees are transient and excluded from the stored projection. Stored names and identity algorithms are compatibility contracts, not implementation conveniences.[3][4]

Neo4j Community is the supported verification baseline. Selective role-based denial of constraint inspection is outside that baseline and remains unexecuted; this scope does not waive `SHOW CONSTRAINTS`, definition validation, tests, or quality rules. Deployment and credentials are defined in the [runbook](../../deployment/neo4j/README.md#supported-verification-scope).

## References

1. [AnalysisApplicationService](../../src/main/java/org/aquilagraph/application/AnalysisApplicationService.java), `execute`; [AnalysisRunRepository](../../src/main/java/org/aquilagraph/application/port/AnalysisRunRepository.java), save/close contract; [composition root](../../src/main/java/org/aquilagraph/AquilaGraphApplication.java).
2. [Neo4jAnalysisRunRepository](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunRepository.java), acquisition, `save`, `ensureSchema`, `saveProjection`, and `close`; [import policy](../../config/checkstyle/import-control.xml).
3. [Neo4jSchema](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jSchema.java), `requireIdentityConstraints`; [Neo4jAnalysisRunMapper](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunMapper.java), `map`; [Neo4jProjectionBuilder](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jProjectionBuilder.java), validation; [Neo4jGraphProjection](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jGraphProjection.java), immutable result.
4. [Neo4j schema](../repository/neo4j-schema.md), publication surface and compatibility; [repository integration tests](../../src/test/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunRepositoryNeo4jIT.java), executable observation cases.
