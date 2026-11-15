# Neo4j Repository Schema

This document defines the current stored projection: labels, relationships, properties, ordering, and identity constraints. The [repository design](../design/analysis-repository.md) owns operation phases and resource lifetimes; the [analysis contract](../contracts/analysis.md) owns publication eligibility and caller outcomes. Source definitions do not establish the contents of a particular deployed database.[1][2]

## Publication Contract

The completed-run mapper accepts an `AnalysisRun` only when `isPublishable()` holds and constructs an immutable projection before database work. Constraint creation and inspection use a separate schema session. Same-run deletion and replacement use one managed write transaction per attempt; retry consumes the same projection. The application closes its returned repository before reporting `SAVED`.[1][2][4]

Every node in the completed-run projection has `AnalysisEntity` and one domain label, plus `runId` and `key`. Its relationships also carry `runId` and `key`. Domain keys may recur across runs without creating cross-run relationships. This projection excludes the full source aggregate; independent AST storage uses the separate namespace below. No simultaneous-publisher isolation guarantee is added.[1][2]

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
The [run mapper](../../src/main/java/org.aquilagraph.repository/org/aquilagraph/repository/internal/neo4j/Neo4jAnalysisRunMapper.java) defines this direction. Its focused mapping collaborators define exact node properties and key sources.[2]

The project-owned `JavaAstAggregate` retains source occurrences and canonical body structure. `ProgramRepresentation` and `AnalysisRun` retain the independent AST outcome, while the completed-run `AnalysisEntity` projection stores declarations and selected graphs without serializing the full aggregate. `AnalysisRun` and `Program` nodes carry `astResultKey`; the run also stores application `completion` and `operation`. These references do not imply that an independently retained AST exists. Semantic references and source spans needed by graph consumers are persisted through `HAS_REFERENCE` and `HAS_ORIGIN`.[2][4]

Current `ProgramRepresentation` and `AnalysisRun` retain the independent
`AstOutcome`. Complete qualified ASTs are explicitly retainable and queryable
through the [independent AST retention schema](#target-ast-retention-schema),
without requiring CFG publication. The bounded capability is implemented;
complete independent migration acceptance remains pending, and broader source
requirements remain in the [roadmap](../roadmap.md#remaining-source-model-work).[7]

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
`Neo4jAnalysisRunMapper` uses `AnalysisRequestIdentity.key()` as `runId`, after enforcing `AnalysisRun.isPublishable()`. The request key qualifies the operation, source subject, and selection; it is not the software-version signature. Republishing the same exact request identity replaces that completed-run projection. Previously stored identity formats must not be silently reinterpreted; migrating them requires an explicit compatibility decision.[1][2][3][4]

For each source, `Neo4jInputMapper` uses `SourceFile.canonicalRelativePath()` for both the stored `relativePath` and the `HAS_SOURCE` discriminator. The source-owned text preserves case and uses `/` separators. The relationship type, endpoints, and hash framing above still apply. A previous native path that differs from the canonical text supplies a different discriminator to the key hash. An explicit republication of the same exact request identity replaces that run's `AnalysisEntity` projection and regenerates its links with canonical keys. Other stored runs retain their existing keys until their own replacement. Actual old-to-new storage effects require [real persistence verification](../build-quality.md#development-verification-scope), which remains deferred.[1][2][3][8]

The adapter creates and verifies these named uniqueness constraints before replacement:

```cypher
CREATE CONSTRAINT analysis_entity_identity IF NOT EXISTS
FOR (entity:AnalysisEntity) REQUIRE (entity.runId, entity.key) IS UNIQUE;

CREATE CONSTRAINT analysis_run_identity IF NOT EXISTS
FOR (run:AnalysisRun) REQUIRE run.runId IS UNIQUE;
```

`Neo4jIdentityConstraints` uses the same immutable definitions to create constraints, select `SHOW CONSTRAINTS` metadata by name, and verify exactly one matching definition per required name. Matching requires `NODE_PROPERTY_UNIQUENESS`, entity type `NODE`, the exact label, and the ordered property list. Missing, repeated, or incompatible definitions, metadata failures, or schema-session closure failures prevent replacement. Previously applied schema effects may remain.[1]

## Failure and Deployment Behavior

An established noncommit retains the old projection. Missing commit acknowledgement leaves whole-run commit unknown. A close failure after known commit does not undo the replacement. Neither an exception description nor a cleanup attempt establishes rollback or resource release. These boundaries are defined in the [publication phase contract](../design/analysis-repository.md#publication-phases-and-failures).[1]

Neo4j Community is the supported verification baseline. Credentials, health checks, startup, shutdown, and deliberate volume removal are documented in the [runbook](../../deployment/neo4j/README.md). This schema describes the adapter's source surface rather than an inspection of a live deployment.[6]

## Target AST retention schema

The independent completed-AST adapter uses the namespace below. Existing labels and constraints above remain the legacy completed-run format. Focused database verification is recorded in the repository design. Eligibility belongs to the [analysis contract](../contracts/analysis.md#target-publication-and-retention); mapping/query phases belong to the [repository design](../design/analysis-repository.md#target-ast-retention).

Each target retained AST entity has label `AstEntity`, a domain label, `astResultKey` and `key`; AST relationships carry the same scope and stable relationship key. The root `AstResult` stores the qualified identity components, canonical content digest, format/profile/semantic-production versions, completion, and software signature. It references captured input/environment and its canonical source files under that scope. Do not use `AnalysisEntity` on AST-retention entities: legacy same-run deletion must not remove them.

An admitted `Neo4jAstProjection` has exactly one node with the domain label `AstResult`, and its `key` equals the projection's `astResultKey`. Projection admission and `root()` use the same `AstResult` label predicate. The [repository design](../design/analysis-repository.md#target-ast-retention) defines how lookup discovers scoped roots and reaches projection admission.[9]

| Target domain family | Required retained distinctions / relationships |
| --- | --- |
| `AstResult`, `AstInput`, `AstEnvironment`, `AstSourceFile` | Complete snapshots/configuration/dependencies/content and assurance/provenance; `HAS_AST_INPUT`, `HAS_AST_ENVIRONMENT`, ordered dependency links, canonical source identities. |
| `AstDeclaration` | Tagged canonical declaration variants for types/members/generics/components/synthesized facts, exact external/owned references, modifiers, hierarchy, parameters and counterpart identities; ordered `DECLARES_AST`. |
| `AstOccurrence`, `AstType`, `AstAnnotationValue` | Full tagged source values, recursive types/capture scope and annotation values/defaults; source-kind/path/source identity, optional bounded ranges, explicit written/implicit provenance; ordered `AST_CHILD` with role and position; typed `AST_SEMANTIC` targets. |
| `AstBody`, `AstEvidence` | Canonical executable/body-origin association without synthetic evaluation topology; keyed typed evidence variants, `KNOWN`/`MISSING`/`ERRONEOUS`/`NOT_APPLICABLE`, all selected facts and provenance; `HAS_AST_BODY`, ordered `HAS_AST_EVIDENCE`. |
| `AstCoverage`, `AstDiagnostic` | Both expectation stages, policies, source-owner addresses, positive/negative/gap/unmatched support and diagnostic creating addresses; ordered `HAS_AST_COVERAGE`/`HAS_AST_DIAGNOSTIC`, exact event-slot links. |

The tagged encoding preserves every field and variant defined by the selected source-model version, including canonical types, ordering, uniqueness, and negative states. The source codec and its version-aware value schemas define the field encoding. Adding retained tags or changing field interpretation requires a new source-model storage format and qualified identity. Authenticating compiler provenance requires evidence beyond supplied values or stored completion claims.[7][9]

Required AST uniqueness definitions are `ast_entity_identity` on `(AstEntity.astResultKey, AstEntity.key)` and `ast_result_identity` on `AstResult.astResultKey`. The same exact constraint inspection applies, and the schema session must close before replacement. In each managed write attempt the adapter acquires the exact subject and incrementally reconciles the immutable projection for its `astResultKey`. It retains exact nodes, removes stale nodes, updates changed nodes, and creates missing nodes. After node reconciliation it retains exact relationships and removes stale, misplaced, and duplicate links before creating missing links. The [repository design](../design/analysis-repository.md#exact-ast-replacement) defines the comparison, batching, and retry boundaries.[1][7]

### Complete retained value materialization

`Neo4jAstResultCodec` stores the full source envelope as Base64 in the result root's `payload` property. `AstCodec.encode` uses the result's `AstIdentity.sourceModelVersion()` as the envelope format, and the root's `formatVersion` and `sourceModelVersion` properties record that same value. Supported historical results use their own version. The root also stores the remaining `AstIdentity` components and the `COMPLETE` qualification required for retention. `AstCodec.materialize` uses the same source version to emit the closed schema's fields as ordered value nodes and component links. `AstValue` is the auxiliary label for scalar, collection, optional, identity, and structural values outside the domain families above.[7][9]

Value keys are `StableId.sha256("ast-value", astResultKey, decimalEncounterOrdinal)`. Each value node stores its closed `tag` and a `long` encounter `ordinal`. A scalar stores `scalarEncoding`, the Base64 encoding of its full tagged `source-value/1` value. This preserves UTF-16 code units, numeric widths, raw floating-point bits, enum variants, and paths without relying on database display conversions. An owned source occurrence also stores its exact `sourceNodeKey`; repeated references to the same canonical source object resolve to the same value node. Other repeated value occurrences have distinct ordered slots.

Links store exact `field` and `long` `position` properties. Their keys are `StableId.sha256("ast-field", astResultKey, sourceKey, targetKey, relationshipType, field, decimalPosition)`. `HAS_AST_VALUES` links the result root to the complete `SyntaxAnalysisInput` materialization. Closed family relationships in the table identify matching fields; `AST_FIELD` retains all other named components. Lists and sets use ordered `item` links, with sets ordered by their retained tagged byte encoding. Optional presence is explicit in `optional/present` and `optional/absent` tags. Schema field order and repeated list positions remain visible through these links.

When lookup reaches the codec, `AstCodec.decode` recomputes qualification through intrinsic source admission and compares it with completion encoded in the envelope. `Neo4jAstResultCodec` rematerializes the admitted result and checks the root's `completion` property as part of exact node and relationship equality by key. That comparison includes labels, tags, properties, scope, endpoints, field names, and positions. An invalid envelope or altered materialization fails admission. A nonblank unsupported format yields `INCOMPATIBLE` before full materialization loading and decoding. The [repository design](../design/analysis-repository.md#target-ast-retention) defines absence, profile checks, and failure precedence. Lookup is compiler-independent and leaves CFG unloaded. Actual database effects require separate verification.[7][9]

Target CFG persistence, when implemented, uses a separate derivation scope qualified by `derivationKey` and stores exact `astResultKey`, canonical method/body provenance, optional occurrence keys and graph/profile versions. Association validation rejects foreign/incompatible identity before mapping and after decoding. AST query does not traverse CFG. Do not silently add a graph-retention policy as part of this AST decision. Retained graph references require an existing compatible AST subject; AST replacement must preserve its content identity or reject the replacement. Deletion of referenced ASTs requires a separately defined operation, which this task does not introduce.

The AST namespace has its own label and relationship allowlists and validates exact endpoints. The adapter uses the source codec's canonical encoding and the shared constraint creation and inspection mechanism. Completed-run `AnalysisRun` entities reference the source result. Full AST retrieval requires an independently retained AST. An explicit production operation can create a qualified AST from captured sources and environment where reproducible. Preserve older stored identities and separate projection namespaces. The repository design defines required database round trips and schema-failure observations. Actual storage verification remains subject to the [deferred integration scope](../build-quality.md#development-verification-scope), and the roadmap retains the broader source-profile requirements.[1][4][7][9]

## References

1. [Neo4jAnalysisRunRepository](../../src/main/java/org.aquilagraph.repository/org/aquilagraph/repository/internal/neo4j/Neo4jAnalysisRunRepository.java), `save` and `replace`; [Neo4jIdentityConstraints](../../src/main/java/org.aquilagraph.repository/org/aquilagraph/repository/internal/neo4j/Neo4jIdentityConstraints.java), `ANALYSIS_RUN`, `AST_RESULT`, `ensure`, and `requireExactDefinition`; [Neo4jSchema](../../src/main/java/org.aquilagraph.repository/org/aquilagraph/repository/internal/neo4j/Neo4jSchema.java), completed-run label/type allowlists.
2. [Neo4jAnalysisRunMapper](../../src/main/java/org.aquilagraph.repository/org/aquilagraph/repository/internal/neo4j/Neo4jAnalysisRunMapper.java), `map`, and its focused mapping collaborators; [Neo4jProjectionBuilder](../../src/main/java/org.aquilagraph.repository/org/aquilagraph/repository/internal/neo4j/Neo4jProjectionBuilder.java), ordered properties and relationship keys; [Neo4jGraphProjection](../../src/main/java/org.aquilagraph.repository/org/aquilagraph/repository/internal/neo4j/Neo4jGraphProjection.java), identity and endpoint validation.
3. [StableId](../../src/main/java/org.aquilagraph.identity/org/aquilagraph/identity/api/StableId.java), `sha256`; [source and environment identities](../design/source-snapshots.md#compiler-environment-and-software-version).
4. [AnalysisRequestIdentity](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisRequestIdentity.java), `key`; [ProgramRepresentation](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/ProgramRepresentation.java) and [AnalysisRun](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisRun.java), native outcomes and admission; [CompletedRunPublicationEligibility](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/CompletedRunPublicationEligibility.java), `assess`; [PublicationCoordinator](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/PublicationCoordinator.java), distinct subject gates, shared publication, and closure.
5. [AquilaGraphApplication](../../src/main/java/org.aquilagraph.cli/org/aquilagraph/AquilaGraphApplication.java), concrete composition; [analysis contract](../contracts/analysis.md), caller outcomes.
6. [Compose deployment](../../deployment/neo4j/compose.yaml) and [Neo4j runbook](../../deployment/neo4j/README.md), configured database and operations.
7. [ProgramRepresentation](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/ProgramRepresentation.java) and [AnalysisRun](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisRun.java), independent domain outcomes; [Neo4jAstResultRepository](../../src/main/java/org.aquilagraph.repository/org/aquilagraph/repository/internal/neo4j/Neo4jAstResultRepository.java), `saveComplete` and `find`; [Neo4jAstResultCodec](../../src/main/java/org.aquilagraph.repository/org/aquilagraph/repository/internal/neo4j/Neo4jAstResultCodec.java), complete projection and validated decoding; [bounded implementation and remaining requirements](../roadmap.md#remaining-source-model-work).
8. [Neo4jInputMapper](../../src/main/java/org.aquilagraph.repository/org/aquilagraph/repository/internal/neo4j/Neo4jInputMapper.java), `map`; [SourceFile](../../src/main/java/org.aquilagraph.identity/org/aquilagraph/identity/api/SourceFile.java), `canonicalRelativePath`; [canonical source identity](../design/source-snapshots.md#canonical-sources); [stable keys and constraints](#stable-keys-and-constraints), explicit replacement of stored projections.
9. [AstCodec](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/retention/AstCodec.java), `encode`, `materialize`, and `decode`; [AstResult](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/api/AstResult.java), `supportsSourceModelVersion`; [AstValueSchema](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/internal/AstValueSchema.java), `Shape.retainedFields` and `retainedComponents`; [RetainedValueCodec](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/internal/RetainedValueCodec.java) and [AstValueProjector](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/internal/AstValueProjector.java), tagged field encoding and materialization; [Neo4jStoredAstAdmission](../../src/main/java/org.aquilagraph.repository/org/aquilagraph/repository/internal/neo4j/Neo4jStoredAstAdmission.java), `requiresMaterialization` and `admit`; [Neo4jAstProjection](../../src/main/java/org.aquilagraph.repository/org/aquilagraph/repository/internal/neo4j/Neo4jAstProjection.java), constructor, `isRoot`, and `root`.
