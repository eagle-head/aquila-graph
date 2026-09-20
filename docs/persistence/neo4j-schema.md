# Neo4j Persistence Schema

## Publication Contract

`Neo4jAnalysisRepository` accepts only `COMPLETED` `AnalysisRun` values. The
projector first converts project-owned records to driver-independent projected
nodes and relationships. The projector itself is stateless; every `project`
invocation creates an isolated context for the run identifier, nodes, and
relationships. The repository creates constraints, then publishes one run in a
single managed write transaction: it deletes any existing projection with the
same `runId`, creates the complete replacement, and commits. A failed transaction
rolls the deletion and replacement back together.[1][2]

Every persisted node has labels `AnalysisEntity` and one domain label, plus
`runId` and `key` properties. Every relationship also has `runId` and `key`.
This permits stable domain keys to recur in different analysis runs without
cross-run relationships. Repeating the same run replaces it deterministically,
which makes publication idempotent at the run boundary.[1][2]

## Node Labels

`AnalysisRun`, `InputSnapshot`, `AnalysisEnvironmentSnapshot`,
`CompilerConfiguration`, `DependencySnapshot`, `SourceFile`, `Program`,
`JavaType`, `JavaMethod`, `JavaParameter`, `MethodBody`, `SyntaxNode`,
`SemanticReference`, `MethodAnalysis`, `ControlFlowGraph`, `FlowValue`,
`EntryNode`, `OperationNode`, `ExitNode`, `Diagnostic`, and `SourceOrigin`.[1]

Source content and its SHA-256 digest are stored on `SourceFile`. Exact available
offsets are stored on `SourceOrigin`, connected to the owning entity by
`HAS_ORIGIN` and to its source by `IN_SOURCE`. Missing compiler positions do not
produce origin nodes.[2]

Compiler implementation, vendor, runtime version, analysis options, digest
algorithm, and environment digest are stored on `AnalysisEnvironmentSnapshot`.
Each ordered class-path or module-path `DependencySnapshot` stores its kind,
content fingerprint, byte and entry counts, and configured location. The
location is provenance metadata; the environment digest is derived from the
compiler identity, options, and ordered dependency fingerprints without the
location.[2][3]

## Relationship Types

Ownership and traceability use `HAS_INPUT`, `HAS_ENVIRONMENT`,
`HAS_CONFIGURATION`, `HAS_CLASS_PATH_ENTRY`, `HAS_MODULE_PATH_ENTRY`,
`HAS_SOURCE`, `HAS_PROGRAM`, `HAS_DIAGNOSTIC`, `DECLARES_TYPE`,
`DECLARES_METHOD`, `DECLARES_PARAMETER`, `HAS_BODY`, `HAS_SYNTAX_ROOT`, `CHILD`,
`HAS_REFERENCE`, `HAS_ORIGIN`, `IN_SOURCE`, `HAS_METHOD_ANALYSIS`,
`ANALYZES_METHOD`, `HAS_CFG`, `HAS_VALUE`, `HAS_NODE`, `USES_VALUE`, and
`DEFINES_VALUE`.[2]

`ControlFlowGraph` directly owns its `FlowValue`, `EntryNode`, `OperationNode`,
and `ExitNode` entities. Operation properties are stored on `OperationNode`;
there is no separately persisted normalized body or operation layer.[2]

CFG transfer kinds map to relationship types `NORMAL_FLOW`, `TRUE_FLOW`,
`FALSE_FLOW`, `SWITCH_FLOW`, `RETURN_FLOW`, and `EXCEPTIONAL_FLOW`.
Discriminators retain switch labels and exception categories where present.[2]

## Stable Keys and Constraints

Domain keys are length-framed SHA-256 identifiers derived from stable source,
symbol, structural-path, operation, graph, or relationship identity. Ordered
positions are stored as properties where order is semantically relevant.[2][3]

The repository explicitly creates:

```cypher
CREATE CONSTRAINT analysis_entity_identity IF NOT EXISTS
FOR (entity:AnalysisEntity) REQUIRE (entity.runId, entity.key) IS UNIQUE;

CREATE CONSTRAINT analysis_run_identity IF NOT EXISTS
FOR (run:AnalysisRun) REQUIRE run.runId IS UNIQUE;
```

Constraint establishment is a schema operation outside publication. The
delete-and-recreate publication itself is one transaction. Driver and session
resources are closed by their owners; managed transactions and consumed results
are scoped to their calls.[1]

## Failure and Deployment Behavior

Analysis completion is computed before repository access. Connection, mapping,
constraint, or transaction failure is wrapped by the application service with
the completed in-memory run and original cause. The CLI maps that application
failure to persistence exit code `3`. Incomplete and rejected runs do not open
the repository and are not published.[1][4][5]

The application layer owns the `AnalysisRepository` port and its technology-free
factory contract. `Neo4jAnalysisRepository` is the persistence adapter, and the
composition root selects it and maps environment values to
`Neo4jConnectionSettings`; neither the application service nor the CLI depends
on those Neo4j settings.[1][4][5]

The local Java process connects through `neo4j://localhost:7687` to the pinned
Compose-managed Neo4j Community container. Port `7474` is exposed only for local
Browser use, and `/data` is backed by the named
`aquila-graph-neo4j-data` volume.[5]

## References

1. [Neo4jAnalysisRepository](../../src/main/java/org/aquilagraph/persistence/Neo4jAnalysisRepository.java), `persist` and `ensureSchema`.
2. [AnalysisRunProjector](../../src/main/java/org/aquilagraph/persistence/AnalysisRunProjector.java), `project`.
3. [StableId](../../src/main/java/org/aquilagraph/model/StableId.java), `sha256`.
4. [AnalysisApplicationService](../../src/main/java/org/aquilagraph/application/AnalysisApplicationService.java), repository lifecycle and publication eligibility; [AnalysisRepository](../../src/main/java/org/aquilagraph/application/AnalysisRepository.java), persistence port.
5. [AquilaGraphApplication](../../src/main/java/org/aquilagraph/AquilaGraphApplication.java), Neo4j adapter composition; [AnalyzeCommand](../../src/main/java/org/aquilagraph/cli/AnalyzeCommand.java), result and failure mapping.
6. [Compose deployment](../../deployment/neo4j/compose.yaml) and [operations](../../deployment/neo4j/README.md).
7. Neo4j Java Driver Manual, [transactions](https://neo4j.com/docs/java-manual/current/transactions/).
8. Neo4j Cypher Manual, [constraints](https://neo4j.com/docs/cypher-manual/current/constraints/).
