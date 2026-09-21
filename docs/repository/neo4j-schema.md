# Neo4j Repository Schema

## Publication Contract

`Neo4jAnalysisRunRepository` accepts only `COMPLETED` `AnalysisRun` values. The
mapper first converts project-owned records to Neo4j-specific nodes and
relationships. The mapper coordinator is stateless; every `map` invocation
creates an isolated builder for the run identifier, nodes, and relationships.
The repository creates constraints, then stores one run in a
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
`JavaType`, `JavaTypeParameter`, `JavaTypeReference`, `JavaField`, `JavaMethod`,
`JavaParameter`, `SemanticReference`,
`MethodAnalysis`, `ControlFlowGraph`, `FlowValue`,
`EntryNode`, `OperationNode`, `ExitNode`, `Diagnostic`, and `SourceOrigin`.[1]

The compiler-derived syntax tree is deliberately absent from this schema. It is
transient source-structure evidence used while constructing the CFG, not the
persisted analysis representation. `JavaMethod.hasBody` records whether the
declaration has a body without retaining that body. Semantic references and
source spans needed after lowering are copied to their owning `OperationNode`
entities and persisted through `HAS_REFERENCE` and `HAS_ORIGIN`.[2][9]

Each `JavaType` stores its qualified name, declaration kind,
compiler-derived modifiers, and source-declaration state. Type declaration
relationships point to occurrence-specific `JavaTypeReference` nodes through
`ENCLOSED_BY`, `HAS_DIRECT_SUPERCLASS`, ordered `HAS_DIRECT_INTERFACE`, and
ordered `PERMITS_SUBCLASS` relationships. A reference stores the complete
compiler-derived identity as `type`, `typeKey`, `typeErasure`, and
`typeCategory`, plus `declarationKey` when a canonical declaration key is
available. Consequently, external hierarchy targets remain typed without
requiring a `JavaType` node in the analyzed program. `JavaTypeParameter` nodes
store stable owner-and-ordinal keys, names, source-declaration state, and source
origins; ordered `DECLARES_TYPE_PARAMETER` and `HAS_TYPE_BOUND` relationships
retain declaration and multiple-bound order.[2][9]

Each resolved `SemanticReference` stores its compiler-reported `typeName`, its
ordered `typeAlternativeNames`, and its transitive `assignableToTypeNames`.
The alternatives preserve union types such as Java multi-catch declarations;
the hierarchy supports exception-handler selection without introducing
compiler-owned objects into the persisted model.[2]

`JavaField`, `JavaParameter`, and typed `FlowValue` nodes persist their compiler-derived type
identity directly as `staticType`, `staticTypeKey`, `staticTypeErasure`, and
`staticTypeCategory`. These properties retain display, stable content identity,
nominal erasure, and compiler-independent category without persisting the syntax
tree or relying on a semantic reference to recover an expression type.
`JavaField` additionally stores its name, sorted modifiers, enum-constant flag,
source-declaration flag, owning type key, and available declaration origins.[2][9]

Every `INVOKE` and `NEW_OBJECT` `OperationNode` stores `invocationMode`,
`invocationForm`, and qualifying-type identity properties
`qualifyingType`, `qualifyingTypeKey`, `qualifyingTypeErasure`, and
`qualifyingTypeCategory`; `qualifyingTypeDeclarationKey` is present when the
compiler supplied a canonical declaration key. Unrelated operation kinds have
none of those properties. The compile-time target remains independently
projected through `HAS_REFERENCE`, and `detail` remains human-readable rather
than an encoded semantic channel.[2][9]

Source content and its SHA-256 digest are stored on `SourceFile`. Exact available
offsets are stored on `SourceOrigin`, connected to the owning entity by
`HAS_ORIGIN` and to its source by `IN_SOURCE`. Missing compiler positions do not
produce origin nodes.[2]

Compiler implementation, vendor, runtime version, analysis options, digest
algorithm, and environment digest are stored on `AnalysisEnvironmentSnapshot`.
The selected `JavaRelease` remains a typed model value in memory and is stored
as its numeric feature-release property on `CompilerConfiguration`.
Each ordered class-path or module-path `DependencySnapshot` stores its kind,
content fingerprint, byte and entry counts, and configured location. The
location is provenance metadata; the environment digest is derived from the
compiler identity, options, and ordered dependency fingerprints without the
location.[2][3]

## Relationship Types

Ownership and traceability use `HAS_INPUT`, `HAS_ENVIRONMENT`,
`HAS_CONFIGURATION`, `HAS_CLASS_PATH_ENTRY`, `HAS_MODULE_PATH_ENTRY`,
`HAS_SOURCE`, `HAS_PROGRAM`, `HAS_DIAGNOSTIC`, `DECLARES_TYPE`,
`ENCLOSED_BY`, `HAS_DIRECT_SUPERCLASS`, `HAS_DIRECT_INTERFACE`,
`PERMITS_SUBCLASS`, `DECLARES_TYPE_PARAMETER`, `HAS_TYPE_BOUND`,
`DECLARES_FIELD`, `DECLARES_METHOD`, `DECLARES_PARAMETER`, `HAS_REFERENCE`, `HAS_ORIGIN`,
`IN_SOURCE`, `HAS_METHOD_ANALYSIS`,
`ANALYZES_METHOD`, `HAS_CFG`, `HAS_VALUE`, `HAS_NODE`, `USES_VALUE`, and
`DEFINES_VALUE`.[2]

`ControlFlowGraph` directly owns its `FlowValue`, `EntryNode`, `OperationNode`,
and `ExitNode` entities. Operation properties are stored on `OperationNode`;
there is no separately persisted syntax or operation layer.[2]

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

Analysis completion is computed before repository access. Connection,
constraint, or transaction failure becomes a technology-neutral
`RepositoryException`; the application service associates it with the completed
in-memory run. The CLI maps that application failure to repository exit code
`3`. Incomplete and rejected runs do not open the repository and are not
stored.[1][4][5]

The `repository` module owns the `AnalysisRunRepository` port and its
technology-free factory contract. `Neo4jAnalysisRunRepository` is one adapter,
and the composition root selects it and maps environment values to
`Neo4jConnectionSettings`; neither the application service nor the CLI depends
on those Neo4j settings.[1][4][5]

The local Java process connects through `neo4j://localhost:7687` to the pinned
Compose-managed Neo4j Community container. Port `7474` is exposed only for local
Browser use, and `/data` is backed by the named
`aquila-graph-neo4j-data` volume.[5]

## References

1. [Neo4jAnalysisRunRepository](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunRepository.java), `save` and `ensureSchema`.
2. [Neo4jAnalysisRunMapper](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunMapper.java), `map`, and its focused mapping collaborators.
3. [StableId](../../src/main/java/org/aquilagraph/model/StableId.java), `sha256`.
4. [AnalysisApplicationService](../../src/main/java/org/aquilagraph/application/AnalysisApplicationService.java), repository lifecycle and storage eligibility; [AnalysisRunRepository](../../src/main/java/org/aquilagraph/repository/AnalysisRunRepository.java), repository port.
5. [AquilaGraphApplication](../../src/main/java/org/aquilagraph/AquilaGraphApplication.java), Neo4j adapter composition; [AnalyzeCommand](../../src/main/java/org/aquilagraph/cli/AnalyzeCommand.java), result and failure mapping.
6. [Compose deployment](../../deployment/neo4j/compose.yaml) and [operations](../../deployment/neo4j/README.md).
7. Neo4j Java Driver Manual, [transactions](https://neo4j.com/docs/java-manual/current/transactions/).
8. Neo4j Cypher Manual, [constraints](https://neo4j.com/docs/cypher-manual/current/constraints/).
9. [SyntaxAnalysisInput](../../src/main/java/org/aquilagraph/syntax/SyntaxAnalysisInput.java), transient syntax ownership;
   [JavaType](../../src/main/java/org/aquilagraph/model/JavaType.java), durable declaration relationships;
   [OperationNode](../../src/main/java/org/aquilagraph/model/OperationNode.java), retained invocation semantics, semantic references, and source origins;
   [Neo4jProgramMapper](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jProgramMapper.java), declaration projection; and
   [Neo4jControlFlowMapper](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jControlFlowMapper.java), CFG projection.
