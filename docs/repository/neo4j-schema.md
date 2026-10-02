# Neo4j Repository Schema

This document defines the current stored projection: labels, relationships, properties, ordering, and identity constraints. The [repository design](../design/analysis-repository.md) owns operation phases and resource lifetimes; the [analysis contract](../contracts/analysis.md) owns publication eligibility and caller outcomes. Source definitions do not establish the contents of a particular deployed database.[1][2]

## Publication Contract

The mapper accepts a completed `AnalysisRun` and constructs an immutable projection before database work. Constraint creation and inspection use a separate schema session. Same-run deletion and replacement use one managed write transaction per attempt; retry consumes the same projection. The application closes its returned repository before reporting `SAVED`.[1][2][4]

Every persisted node has `AnalysisEntity` and one domain label, plus `runId` and `key`. Relationships also carry `runId` and `key`. Domain keys may recur across runs without creating cross-run relationships. The transient source aggregate is not included in publication, and no simultaneous-publisher isolation guarantee is added.[1][2]

## Node Labels

`AnalysisRun`, `SoftwareVersion`, `InputSnapshot`, `AnalysisEnvironmentSnapshot`,
`CompilerConfiguration`, `DependencySnapshot`, `SourceFile`, `Program`,
`JavaType`, `JavaTypeParameter`, `JavaTypeReference`, `JavaField`,
`JavaRecordComponent`, `JavaSynthesizedDeclaration`, `JavaMethod`,
`JavaParameter`, `SemanticReference`,
`MethodAnalysis`, `ControlFlowGraph`, `FlowValue`,
`EntryNode`, `OperationNode`, `ExitNode`, `Diagnostic`, and `SourceOrigin`.[1]

`AnalysisRun` points to `SoftwareVersion` through `ANALYZED_SOFTWARE`.
`SoftwareVersion` stores `signatureAlgorithm`, `signature`,
`sourceSignature`, and `environmentSignature`, and owns `HAS_INPUT` and
`HAS_ENVIRONMENT`. The run separately owns `HAS_DIAGNOSTIC` and `HAS_PROGRAM`.
The [run mapper](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunMapper.java) defines this direction. Its focused mapping collaborators define exact node properties and key sources.[2]

The sealed, project-owned `JavaAstAggregate` retains validated source occurrences
and aggregate-owned method bodies in `SyntaxAnalysisInput`. It is deliberately
absent from this schema and from `ProgramRepresentation` and `AnalysisRun`; AST
projection and publication remain deferred in the [roadmap](../roadmap.md#remaining-source-model-work). Aggregate-derived
`MethodSyntax` views supply the current CFG construction. `JavaMethod.hasBody`
records whether the
declaration has a body without retaining that body. Semantic references and
source spans needed after lowering are copied to their owning `OperationNode`
entities and persisted through `HAS_REFERENCE` and `HAS_ORIGIN`.[2]

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
retain declaration and multiple-bound order.[2]

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
source-declaration flag, and available declaration origins. Its owning type is
represented by the directed `DECLARES_FIELD` relationship, not an owning-type
node property.[2]
`JavaParameter` also stores `sourceDeclared`: written formals retain source origins,
while implicit canonical record formals retain their canonical parameter keys
without source origins.[2]

`JavaRecordComponent` has an independent owner-and-ordinal key, name, position,
varargs state, sorted written and attributed modifiers, and the same four typed
`staticType` properties as `JavaField`. Available `accessorKey`, `backingFieldKey`,
and `canonicalParameterKey` properties identify compiler-derived counterparts.
`DECLARES_RECORD_COMPONENT` retains header order, and `HAS_ORIGIN` points to the
written header range. `JavaSynthesizedDeclaration` stores a stable canonical key,
compiler declaration kind, and `sourceDeclared=false`; ordered
`DECLARES_SYNTHESIZED` connects it to its declaring type in supplied list order,
stored as an integer relationship `position`, without presenting it as source
AST syntax. Detailed compiler-derived fields and methods remain in their
existing typed node families.[2]
Declaration keys are disjoint across type, type-parameter, field, component,
method, and synthesized node families before projection.[2]

Every `INVOKE` and `NEW_OBJECT` `OperationNode` stores `invocationMode`,
`invocationForm`, and qualifying-type identity properties
`qualifyingType`, `qualifyingTypeKey`, `qualifyingTypeErasure`, and
`qualifyingTypeCategory`; `qualifyingTypeDeclarationKey` is present when the
compiler supplied a canonical declaration key. Unrelated operation kinds have
none of those properties. The compile-time target remains independently
projected through `HAS_REFERENCE`, and `detail` remains human-readable rather
than an encoded semantic channel.[2]

Source content and its supplied digest are stored on `SourceFile`. Production
capture derives the SHA-256 digest from the captured content; direct construction
can supply a digest under caller assurance rather than production requalification.
Exact available offsets are stored on `SourceOrigin`, connected to the owning entity by
`HAS_ORIGIN` and to its source by `IN_SOURCE`. Missing compiler positions do not
produce origin nodes.[2]

Compiler implementation, vendor, runtime version, analysis options, digest
algorithm, and environment digest are stored on `AnalysisEnvironmentSnapshot`.
The selected `JavaRelease` remains a typed model value in memory and is stored
as its numeric feature-release property on `CompilerConfiguration`.
Each ordered class-path or module-path `DependencySnapshot` stores its kind,
content fingerprint, byte and entry counts, and configured location. The
location is provenance metadata. Production capture derives the environment
digest from the compiler identity, options, and ordered dependency fingerprints
without the separate dependency location fields. Ordered option text remains a
digest input and can itself contain a location. A directly constructed completed
run can carry supplied snapshot values under caller assurance.[2][3]

## Relationship Types

Ownership and traceability use `ANALYZED_SOFTWARE`, `HAS_INPUT`, `HAS_ENVIRONMENT`,
`HAS_CONFIGURATION`, `HAS_CLASS_PATH_ENTRY`, `HAS_MODULE_PATH_ENTRY`,
`HAS_SOURCE`, `HAS_PROGRAM`, `HAS_DIAGNOSTIC`, `DECLARES_TYPE`,
`ENCLOSED_BY`, `HAS_DIRECT_SUPERCLASS`, `HAS_DIRECT_INTERFACE`,
`PERMITS_SUBCLASS`, `DECLARES_TYPE_PARAMETER`, `HAS_TYPE_BOUND`,
`DECLARES_FIELD`, `DECLARES_RECORD_COMPONENT`, `DECLARES_SYNTHESIZED`,
`DECLARES_METHOD`, `DECLARES_PARAMETER`, `HAS_REFERENCE`, `HAS_ORIGIN`,
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

The stored `CompilerConfiguration` key derives from `canonicalForm()` and
includes configured paths; the production-derived environment digest excludes
dependency location fields while retaining ordered options and dependency
role/kind/fingerprint and list-count facts. An option that embeds a location can therefore
change the digest on relocation. A directly supplied digest retains the
caller-assurance limit. A dependency key
includes environment, role, position and fingerprint. A relationship key is
`sha256("relationship", type, sourceKey, targetKey, discriminator)`.
Explicit `position` belongs to ordered declaration, dependency, diagnostic,
reference, origin and graph-membership relationships as emitted by their
mappers. `HAS_SOURCE` and CFG transfer edges have discriminators but no stored
position, so mapper iteration does not promise database query order for them
(see the mapping source in reference 2).
Production `AnalysisEngine` derives `runId` from the source/environment software-version signature. It does not include an analyzer build or stored-model version. A directly constructed completed run can supply a nonnull identity under caller assurance; repository mapping does not requalify production completion or rederive that identity. Republishing the same ID replaces its projection. Removing legacy identities requires an explicit data migration.[1][2][3][4]

The adapter creates and verifies these named uniqueness constraints before replacement:

```cypher
CREATE CONSTRAINT analysis_entity_identity IF NOT EXISTS
FOR (entity:AnalysisEntity) REQUIRE (entity.runId, entity.key) IS UNIQUE;

CREATE CONSTRAINT analysis_run_identity IF NOT EXISTS
FOR (run:AnalysisRun) REQUIRE run.runId IS UNIQUE;
```

`ensureSchema` reads `SHOW CONSTRAINTS` metadata and `Neo4jSchema.requireIdentityConstraints` verifies each exact name, `NODE_PROPERTY_UNIQUENESS` type, `NODE` entity type, label, and ordered property list. Missing or incompatible definitions, metadata failures, or schema-session closure failures prevent replacement. Previously applied schema effects may remain.[1]

## Failure and Deployment Behavior

An established noncommit retains the old projection. Missing commit acknowledgement leaves whole-run commit unknown. A close failure after known commit does not undo the replacement. Neither an exception description nor a cleanup attempt establishes rollback or resource release. These boundaries are defined in the [publication phase contract](../design/analysis-repository.md#publication-phases-and-failures).[1]

Neo4j Community is the supported verification baseline. Credentials, health checks, startup, shutdown, and deliberate volume removal are documented in the [runbook](../../deployment/neo4j/README.md). This schema describes the adapter's source surface rather than an inspection of a live deployment.[6]

## References

1. [Neo4jAnalysisRunRepository](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunRepository.java), `save`, `ensureSchema`, and `saveProjection`; [Neo4jSchema](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jSchema.java), label/type allowlists and `requireIdentityConstraints`.
2. [Neo4jAnalysisRunMapper](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunMapper.java), `map`, and its focused mapping collaborators; [Neo4jProjectionBuilder](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jProjectionBuilder.java), ordered properties and relationship keys; [Neo4jGraphProjection](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jGraphProjection.java), identity and endpoint validation.
3. [StableId](../../src/main/java/org/aquilagraph/model/StableId.java), `sha256`; [source and environment identities](../design/source-snapshots.md#compiler-environment-and-software-version).
4. [AnalysisEngine](../../src/main/java/org/aquilagraph/core/AnalysisEngine.java), run derivation; [AnalysisRun](../../src/main/java/org/aquilagraph/model/AnalysisRun.java), constructor limits; [AnalysisApplicationService](../../src/main/java/org/aquilagraph/application/AnalysisApplicationService.java), publication eligibility and closure.
5. [AquilaGraphApplication](../../src/main/java/org/aquilagraph/AquilaGraphApplication.java), concrete composition; [analysis contract](../contracts/analysis.md), caller outcomes.
6. [Compose deployment](../../deployment/neo4j/compose.yaml) and [Neo4j runbook](../../deployment/neo4j/README.md), configured database and operations.
