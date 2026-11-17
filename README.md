# Aquila Graph

Aquila Graph analyzes Java source and produces a program representation and a control-flow graph for each accepted concrete method body. It can publish completed analysis runs to Neo4j. The current language release is Java 25, with the bounded source profile described in the [Java analysis design](docs/design/java-analysis.md#supported-source-profiles).

For direct Java consumption through one Maven dependency, see the [library guide](docs/library.md). The CLI remains available and uses the same configured analysis session.

For stage metrics, structured logs, traces, JFR correlation, and the local Docker stack, see the [observability guide](docs/observability.md).

## Build and run

Use JDK 25 and the checked-in Maven Wrapper. Toolchain requirements and all build targets are defined in the [build quality guide](docs/build-quality.md#required-toolchain).

From the repository root:

| Purpose | Windows PowerShell | Linux |
| --- | --- | --- |
| Run unit tests | `.\Invoke-Build.ps1 test` | `make test` |
| Package and run process integration tests | `.\Invoke-Build.ps1 integration-test` | `make integration-test` |
| Run all integration tests with a disposable Neo4j | `.\Invoke-Build.ps1 integration-test-neo4j` | `make integration-test-neo4j` |
| Run static quality gates | `.\Invoke-Build.ps1 quality` | `make quality` |

Analyze the included calculator without executing its application:

```text
java -jar target/aquila-graph-0.1.0-SNAPSHOT.jar analysis-targets/calculator/src/main/java --release 25
```

The command accepts one source root, optional `--release 25`, and optional `--persist`. Java 25 is the default. Persistence requires the credentials and database setup in the [Neo4j runbook](deployment/neo4j/README.md).

Database integration tests manage their own memory-limited container and discard its volumes by default. Docker must be available; no separately configured database or password is required. See [integration database ownership](deployment/neo4j/testing.md) for resource budgets, explicit preservation, and recovery.

## Results and limits

The command reports source, declaration, and graph counts on standard output and diagnostics on standard error. Normal exit codes are `0` for a completed analysis without a publication failure or observed output failure, `1` for a partial or failed analysis or a named environment failure, `2` for rejected usage, rejected input, or a returned `REJECTED` analysis, `3` for a returned publication failure, and `4` for semantic success followed by observed output failure. Semantic exits `1`, `2`, and `3` retain precedence over output failure. The [analysis contract](docs/contracts/analysis.md#command-line-results) defines the exact boundaries.

Unsupported constructs block completion. The analyzer does not execute the analyzed program, run annotation processors, provide whole-program analysis, or accept bytecode as an input. Completed-run publication and explicit AST retention have separate [contracts](docs/contracts/analysis.md#target-publication-and-retention). The [roadmap](docs/roadmap.md) describes development priorities and scope boundaries.

## Project documentation

| Subject | Document |
| --- | --- |
| Library API, packaging, configuration, and lifecycle | [Library](docs/library.md) |
| Analysis inputs, completion, publication, and failures | [Analysis contract](docs/contracts/analysis.md) |
| CLI fields, quoting, channel ordering, and exit behavior | [Command-line reporting contract](docs/contracts/command-line-reporting.md) |
| Java frontend, source profile, coverage, and graph semantics | [Java analysis design](docs/design/java-analysis.md) |
| Captured source and environment identity | [Source snapshots](docs/design/source-snapshots.md) |
| Retained AST and captured-value formats | [AST value format](docs/design/ast-value-format.md) · [Captured-value encoding](docs/design/captured-value-encoding.md) |
| Repository responsibilities and lifecycle | [Repository design](docs/design/analysis-repository.md) |
| Neo4j labels, relationships, keys, and constraints | [Stored schema](docs/repository/neo4j-schema.md) |
| Build, tests, and quality requirements | [Build quality](docs/build-quality.md) |
| Examples and local database operations | [Analysis targets](analysis-targets/README.md) · [Neo4j runbook](deployment/neo4j/README.md) |
| Open prerequisites and scope boundaries | [Roadmap](docs/roadmap.md) |
| Java 25 specifications and compiler APIs | [Official source map](docs/reference/java-25/README.md) |

## References

- [AnalysisCapabilities](src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisCapabilities.java), independent computation, derivation, retention, query, and execution inputs; [AnalysisQualification](src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisQualification.java), request completion; [PublicationCoordinator](src/main/java/org.aquilagraph.application/org/aquilagraph/application/PublicationCoordinator.java), shared publication eligibility and closure.
- [AnalyzeCommandParser](src/main/java/org.aquilagraph.cli/org/aquilagraph/cli/AnalyzeCommandParser.java), `parse`; [CommandRunner](src/main/java/org.aquilagraph.cli/org/aquilagraph/cli/CommandRunner.java), `run`; [CommandInvocation](src/main/java/org.aquilagraph.cli/org/aquilagraph/cli/CommandInvocation.java), retained outcome and emission observations; [CommandExitPolicy](src/main/java/org.aquilagraph.cli/org/aquilagraph/cli/CommandExitPolicy.java), exit classification.
- [JavaRelease](src/main/java/org.aquilagraph.identity/org/aquilagraph/identity/api/JavaRelease.java), supported feature releases; [project build](pom.xml), toolchain and packaging configuration.
- [Neo4jAnalysisRunMapper](src/main/java/org.aquilagraph.repository/org/aquilagraph/repository/internal/neo4j/Neo4jAnalysisRunMapper.java), durable publication surface.
