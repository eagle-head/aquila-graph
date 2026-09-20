# Analysis Repository Design

## Decision

The analysis-storage boundary is the logical `repository` module. Its public
contract is `AnalysisRunRepository`, which stores a completed `AnalysisRun` and
owns repository-resource shutdown. The application depends on that contract and
its lazy factory; it does not depend on a database driver, graph projection,
storage schema, or storage-specific metrics.[1][2]

Neo4j is one realization in `repository.neo4j`. A future implementation may
realize the same repository contract without reusing Neo4j configuration,
projection records, mapping collaborators, or transaction code. The composition
root remains the only class that selects the concrete repository.[1][3]

This boundary follows responsibility distribution and delegation: the application
service coordinates the use case, the repository owns saving, and the Neo4j
adapter delegates aggregate mapping to collaborators that have direct knowledge
of each source aggregate. Storage technology remains separate from analysis
and delivery logic.[6]

## Responsibilities

| Type | Responsibility |
| --- | --- |
| `AnalysisRunRepository` | Define the technology-neutral `save` and lifecycle contract. |
| `AnalysisRunRepositoryFactory` | Open a repository only when a completed run must be saved. |
| `RepositoryException` | Report repository-access failure without exposing driver exceptions as the application contract. |
| `Neo4jAnalysisRunRepository` | Own Neo4j driver lifecycle, schema preparation, and the replacement transaction. |
| `Neo4jAnalysisRunMapper` | Validate a completed run and coordinate one isolated mapping operation. |
| `Neo4jInputMapper` | Map input snapshot and captured source files. |
| `Neo4jEnvironmentMapper` | Map compiler configuration and dependency snapshots. |
| `Neo4jProgramMapper` | Map program declarations and method bodies. |
| `Neo4jControlFlowMapper` | Map method analyses, values, CFG nodes, and transfer relationships. |
| `Neo4jEvidenceMapper` | Map diagnostics, syntax, semantic references, and source origins. |
| `Neo4jProjectionBuilder` | Own mutable node and relationship accumulation for exactly one run. |

## Dependency Rules

1. `application` may depend on `repository`, but not on `repository.neo4j`.
2. `cli`, `core`, `frontend`, and `model` may not depend on any repository package.
3. Only `repository.neo4j` may import the Neo4j driver.
4. `repository.neo4j` may depend on `repository` and `model`; it may not depend on
   `application`, `cli`, `core`, or `frontend`.
5. The composition root may depend on the repository contract and the selected
   Neo4j realization.[1][3][4]

## Operation Contracts

### `AnalysisRunRepository.save(run)`

- Precondition: `run` is non-null and has completion `COMPLETED`.
- Postcondition on success: the repository contains the complete saved form for
  `run.runId()` according to the selected implementation.
- Neo4j realization: any prior projection with the same run identifier is replaced
  atomically by the complete new projection.[3][5]
- Failure: repository or driver failures are reported as `RepositoryException`.
  The application associates that failure with the already completed run through
  `AnalysisRepositoryException`; analysis completion is unchanged.[2][3]

The contract returns no node count, relationship count, query language value, or
driver object. Those values describe one implementation rather than the result
owed by every repository.[1][2]

## Invariants

- A repository save is never attempted for an incomplete or rejected run.[2]
- A successful `AnalysisExecution` with status `SAVED` always contains a completed
  run; `NOT_ATTEMPTED` always contains a non-completed run.[2]
- Every Neo4j projection contains unique node and relationship keys, and every
  relationship endpoint belongs to the same projection.[3]
- Every Neo4j node and relationship carries the mapped run identifier.[3][5]
- A `Neo4jAnalysisRunMapper` invocation creates a new builder, so concurrent calls
  cannot share mutable projection state.[3]

## References

1. [Analysis repository class model](../../diagrams/class/analysis-repository.puml).
2. [AnalysisApplicationService](../../src/main/java/org/aquilagraph/application/AnalysisApplicationService.java), `execute`; [AnalysisExecution](../../src/main/java/org/aquilagraph/application/AnalysisExecution.java), repository-status invariants.
3. [Neo4jAnalysisRunRepository](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunRepository.java), `save`; [Neo4jAnalysisRunMapper](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunMapper.java), `map`.
4. [Import-control architecture](../../config/checkstyle/import-control.xml).
5. [Neo4j schema](../repository/neo4j-schema.md).
6. Raul Sidnei Wazlawick, *Object-Oriented Analysis and Design for Information
   Systems* (2014), §9.2, PDF pp. 286–289, responsibility distribution; §9.6,
   PDF pp. 316–319, delegation and low coupling; and §13.1, PDF pp. 426–427,
   separation of persistence from domain and interface logic.
7. OMG, *Unified Modeling Language 2.5.1*, §10.4.3, PDF pp. 213–214,
   interfaces as contracts; and §11.6.3, PDF pp. 250–252, replaceable components
   and their provided and required interfaces.
