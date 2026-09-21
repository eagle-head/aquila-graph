# Static Analysis Foundation Validation

Validation date: September 19, 2026. Environment: Temurin Java `25.0.4.1`,
Apache Maven `3.9.16`, Docker Engine `29.7.2`, and Docker Compose `5.5.0`.

## Repository Refactoring Validation

Revalidation date: September 20, 2026.

- `.\mvnw.cmd -q verify`: build success; 43 unit tests and 2 packaged-process
  integration tests passed with no failures, errors, or skips.
- `.\mvnw.cmd -q -Pneo4j-integration verify`: build success; the same default
  gates plus the real Neo4j integration test passed. The database test covered
  repeated saves, run isolation, constraints, typed relationships, source
  origins, rollback, and cleanup of its exact run identifiers.[1]
- The bundled PlantUML `1.2025.0` runtime rendered the repository class,
  component, sequence, and updated program-representation views successfully.
  The repository, component, and sequence PNGs were visually inspected.
- A fresh CodeGraph index covered 92 production Java files, 1,120 symbols, and
  2,103 edges without indexing errors.

The packaged output recorded below predates the repository-contract refactoring.
The current CLI reports `Repository: SAVED` and intentionally omits Neo4j node
and relationship counts from its technology-neutral application result.[6]

## Syntax Boundary Validation

Validation date: September 21, 2026.

- `.\Invoke-Build.ps1 test`:
  build success; 157 tests passed with no
  failures, errors, or skips. The focused boundary tests cover controlled
  syntax-node construction, contextual invocation and receiver forms,
  declaration/reference key alignment, distinct deterministic diagnostics,
  exhaustive class-member classification, nested types, assignment-location
  evaluation order, field receiver evaluation, and rejection of unresolved or
  unsupported semantics before completed analysis. New durable-model coverage
  verifies inheritance, interface relationships, sealed permits, nested
  ownership, external supertypes, stable type-parameter identities and multiple
  bounds, annotation rejection, anonymous-class diagnostic taxonomy, all
  required invocation modes and qualifying forms, model invariants, and Neo4j
  projection.[7][8][9][10][11]
- `.\Invoke-Build.ps1 quality`:
  build success; formatting, Checkstyle, PMD,
  duplicate-code analysis, SpotBugs, dependency analysis, license checks, and
  public Javadoc validation passed. This target intentionally skips test
  execution; the preceding test gate supplies behavioral evidence.
- The bundled PlantUML `1.2025.0` runtime passed syntax checks and produced SVG
  renders for the updated Java frontend, program representation, CFG, and
  analysis-run sequence sources. The render result establishes renderer
  acceptance only; no visible-layout claim is made for this change.[2]
- The final CodeGraph sync reported the index up to date for 145 Java files,
  with 2,028 nodes and 4,304 edges. A follow-up exploration traced invocation
  classification through `SyntaxValue.Invocation`, direct graph lowering,
  `OperationNode`, and the Neo4j mapper.
- The Neo4j mapper unit test verifies that syntax nodes and syntax relationships
  are absent while method body presence, operation semantic references,
  operation origins, invocation mode/form/qualifying type, declaration
  hierarchy and ownership, type parameters and bounds, and
  parameter/flow-value type identities remain. The real
  Neo4j integration profile also passed: 148 unit tests, three packaged-process
  integration tests, and one database integration test completed with no
  failures, errors, or skips. The database integration test includes durable
  field properties and `DECLARES_FIELD` projection. The database container was
  stopped afterward and its named data volume was preserved.[1][9][12]

## Baseline

- `.\mvnw.cmd test`: 7 tests passed before implementation.
- `.\mvnw.cmd -f analysis-targets\calculator\pom.xml test`: 67 tests passed.
- `docker version` and `docker compose version`: both available; no software was installed.

## Final Build Gates

- `.\mvnw.cmd clean verify`: build success; 24 unit tests and 1 packaged-process
  integration test passed with no failures or skips.
- `.\mvnw.cmd -f analysis-targets\calculator\pom.xml clean verify`: build
  success; 67 tests passed with no failures or skips.
- `.\mvnw.cmd clean verify -Pneo4j-integration`: build success; 24 unit tests,
  the packaged-process test, and the real Neo4j integration test passed. The
  database test verified idempotency, isolation, explicit constraints,
  transaction rollback, typed CFG relationships, source origins, and exact-run
  cleanup without volume deletion.[1]

## Packaged Calculator Analysis

The packaged command was executed as a separate Java process with `--persist`.
It exited `0`, wrote zero bytes to stderr, and reported:

```text
Run: analysis-run:12d4061a1bc46ef0068b4684e929d4815c3581eaa60c7c5eeb17b030e3897673
Completion: COMPLETED
Java source files: 3
Java types: 3
Java methods: 12
Analyzed method bodies: 12
CFG operations: 179
CFG nodes: 203
CFG edges: 293
Persistence: COMPLETED (1376 nodes, 2464 relationships)
```

A follow-up Cypher query returned 1,376 run-scoped nodes, 2,464 run-scoped
relationships, 12 `JavaMethod` nodes, 179 graph-owned `OperationNode` nodes,
207 `FlowValue` nodes, and 12 `ControlFlowGraph` nodes. No separate operation
representation label was persisted. The relationship set included `NORMAL_FLOW`,
`TRUE_FLOW`, `FALSE_FLOW`, `SWITCH_FLOW`, `RETURN_FLOW`, and
`EXCEPTIONAL_FLOW`. `SHOW CONSTRAINTS` returned both
`analysis_entity_identity` and `analysis_run_identity` as node-property
uniqueness constraints.

Compose reported the pinned `neo4j:2026.08.1` container healthy before the
integration gate. Normal shutdown used `docker compose ... down`; subsequent
inspection confirmed that `aquila-graph-neo4j-data` remained present.

## Diagram Validation

The changed component, sequence, program-representation class, and focused CFG
class PlantUML sources passed the bundled PlantUML `1.2025.0` syntax check. SVG
and PNG renders completed, and the corresponding PNG renders were visually
inspected at original resolution. The class model was split into aggregate and
focused CFG views so public operations and ownership invariants remain legible.[2]

PlantUML guide SHA-256:
`1455bd5f63603ede0545f66d67de2162cce7b7b80e03e98d20e674e6c272a8a6`.
Runtime JAR SHA-256:
`6f38f70455d08438979451c2257cd5d58647c6460094bb829bc2a12878d47331`.
Relevant guide reading covered sequence diagrams (PDF pp. 2–13), class diagrams
(pp. 62–69), activity/control-flow notation (pp. 123–139), component diagrams
(pp. 165–182), and common titles/legends (pp. 442–445).

## OOP Responsibility Check

The first graph-native implementation placed 1,052 lines in
`ControlFlowGraphBuilder`. After responsibility separation, the public builder
has 32 lines; statement translation has 288, expression translation 371,
per-method coordination 307, and graph assembly 205. These counts are a size
observation, not proof of design quality. The responsibility evidence is the
separation between public validation, syntax-family translation, method-scoped
coordination, and graph identity/assembly, with behavior preserved by the build
and storage gates above.[5]

## References

1. [Neo4j integration test](../../src/test/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunRepositoryNeo4jIT.java).
2. [Diagram index](../../diagrams/README.md) and the linked PlantUML/SVG artifacts.
3. [Root Maven configuration](../../pom.xml), unit/process/integration lifecycle.
4. [Calculator analysis test](../../src/test/java/org/aquilagraph/CalculatorAnalysisTest.java).
5. [Graph-construction classes](../../src/main/java/org/aquilagraph/core/ControlFlowGraphBuilder.java) and [focused class model](../../diagrams/class/control-flow-graph.puml).
6. [Analysis repository design](../design/analysis-repository.md) and [AnalyzeCommand](../../src/main/java/org/aquilagraph/cli/AnalyzeCommand.java), repository outcome reporting.
7. [Syntax boundary tests](../../src/test/java/org/aquilagraph/syntax/SyntaxNodeTest.java) and
   [frontend boundary tests](../../src/test/java/org/aquilagraph/frontend/JavaFrontendTest.java).
8. [Control-flow construction tests](../../src/test/java/org/aquilagraph/core/ControlFlowGraphBuilderTest.java).
9. [Neo4j projection mapper tests](../../src/test/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunMapperTest.java).
10. [Java AST boundary regression tests](../../src/test/java/org/aquilagraph/frontend/JavaAstBoundaryRegressionTest.java).
11. [Direct graph-lowering regression tests](../../src/test/java/org/aquilagraph/core/JavaGraphLoweringRegressionTest.java).
12. [Packaged-process integration tests](../../src/test/java/org/aquilagraph/AquilaGraphProcessIT.java).
