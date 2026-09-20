# Static Analysis Foundation Milestone

## Delivered Increment

The Chapter 2 foundation is executable for the documented Java 25 source
profile. The root application discovers and content-identifies sources, uses the
public JDK Compiler API for parsing and semantic analysis, translates to an
immutable project-owned representation, lowers supported concrete method
bodies directly into intraprocedural CFG aggregates, reports explicit completion and
diagnostics, and can persist completed runs in Compose-managed Neo4j.[1][2][3]

The standalone calculator remains an independent Maven project under
`analysis-targets`; it is neither copied into the application source tree nor
registered as a root module. It is the executable acceptance target for the
first source profile.[4][5]

## Implemented Packages

- `frontend`: ordered source discovery, SHA-256 snapshot identity, public
  compiler invocation, and translation of syntax/semantics/origins;
- `model`: immutable source, program, diagnostic, and graph-owned CFG data;
- `core`: semantic lowering into CFGs, completion, and run assembly;
- `repository`: technology-neutral storage contracts plus the transactional
  `repository.neo4j` adapter;
- `cli`: process arguments, counts, diagnostics, exit codes, and repository
  outcome;
- `org.aquilagraph.AquilaGraphApplication`: composition root.

The root remains one Java 25 Maven artifact. Aquila Graph is not containerized;
only Neo4j Community runs in Docker.[3][6]

## Evidence Boundary

The first increment establishes behavior only for the declared accepted source
profile and conservative exception policy. It does not claim unrestricted Java
coverage, path feasibility, whole-program exception precision, production
scalability, or a soundness proof for all Java programs.[2]

Unit tests require no database. The `neo4j-integration` Maven profile requires
the healthy Compose-managed database and verifies real persistence,
idempotency, run isolation, constraints, rollback, typed flow, origins, and
cleanup of its exact test run IDs without deleting the shared volume.[7]

## Deferred Capabilities

Type Analysis and every Chapter 3+ analysis remain deferred. The next justified
capability is to widen the source profile only when a concrete target requires
it, beginning with a precise contract for one currently unsupported control
construct. Call graphs, interprocedural flow, lattice solvers, points-to
analysis, bytecode ingestion, REST, Spring, and plugin frameworks are not part
of this milestone.[2]

## References

1. Møller and Schwartzbach, *Static Program Analysis*, §§2.3–2.5; local copy
   [static-program-analysis.pdf](../../books/static-program-analysis.pdf#page=22).
2. [Java analysis design](../design/java-analysis.md).
3. [Java analysis pipeline](../../diagrams/component/README.md).
4. [Analysis targets](../../analysis-targets/README.md).
5. [Calculator target](../../analysis-targets/calculator/README.md).
6. [Neo4j deployment](../../deployment/neo4j/README.md).
7. [Neo4j integration test](../../src/test/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunRepositoryNeo4jIT.java).
