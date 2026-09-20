# Static Analysis Foundation Validation

Validation date: September 19, 2026. Environment: Temurin Java `25.0.4.1`,
Apache Maven `3.9.16`, Docker Engine `29.7.2`, and Docker Compose `5.5.0`.

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
207 `FlowValue` nodes, and 12 `ControlFlowGraph` nodes. No persisted label began
with `Normalized`. The relationship set included `NORMAL_FLOW`,
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
and persistence gates above.[5]

## References

1. [Neo4j integration test](../../src/test/java/org/aquilagraph/persistence/Neo4jAnalysisRepositoryNeo4jIT.java).
2. [Diagram index](../../diagrams/README.md) and the linked PlantUML/SVG artifacts.
3. [Root Maven configuration](../../pom.xml), unit/process/integration lifecycle.
4. [Calculator analysis test](../../src/test/java/org/aquilagraph/CalculatorAnalysisTest.java).
5. [Graph-construction classes](../../src/main/java/org/aquilagraph/core/ControlFlowGraphBuilder.java) and [focused class model](../../diagrams/class/control-flow-graph.puml).
