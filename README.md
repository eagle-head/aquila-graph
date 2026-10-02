# Aquila Graph

Aquila Graph analyzes Java source and produces a program representation and a control-flow graph for each accepted concrete method body. It can publish completed analysis runs to Neo4j. The current language release is Java 25, with the bounded source profile described in the [Java analysis design](docs/design/java-analysis.md#supported-source-profile).

## Build and run

Use JDK 25 and the checked-in Maven Wrapper. Toolchain requirements and all build targets are defined in the [build quality guide](docs/build-quality.md#required-toolchain).

From the repository root:

| Purpose | Windows PowerShell | Linux and macOS |
| --- | --- | --- |
| Run unit tests | `.\Invoke-Build.ps1 test` | `make test` |
| Package and run process integration tests | `.\Invoke-Build.ps1 integration-test` | `make integration-test` |
| Run static quality gates | `.\Invoke-Build.ps1 quality` | `make quality` |

Analyze the included calculator without executing its application:

```text
java -jar target/aquila-graph-0.1.0-SNAPSHOT.jar analysis-targets/calculator/src/main/java --release 25
```

The command accepts one source root, optional `--release 25`, and optional `--persist`. Java 25 is the default. Persistence requires the credentials and database setup in the [Neo4j runbook](deployment/neo4j/README.md).

## Results and limits

The command reports source, declaration, and graph counts on standard output and diagnostics on standard error. Exit codes are `0` for completed analysis, `1` for incomplete analysis or a named environment failure, `2` for rejected input or usage, and `3` for a named repository failure. The [analysis contract](docs/contracts/analysis.md#command-line-results) defines the exact boundaries.

Unsupported constructs block completion. The analyzer does not execute the analyzed program, run annotation processors, provide whole-program analysis, or accept bytecode as an input. The source aggregate is transient and is not currently published to Neo4j; the durable result contains declarations and accepted method graphs. Expansion beyond that boundary is tracked in the [roadmap](docs/roadmap.md).

## Project documentation

| Subject | Document |
| --- | --- |
| Analysis inputs, completion, publication, and failures | [Analysis contract](docs/contracts/analysis.md) |
| Java frontend, source profile, coverage, and graph semantics | [Java analysis design](docs/design/java-analysis.md) |
| Captured source and environment identity | [Source snapshots](docs/design/source-snapshots.md) |
| Repository responsibilities and lifecycle | [Repository design](docs/design/analysis-repository.md) |
| Neo4j labels, relationships, keys, and constraints | [Stored schema](docs/repository/neo4j-schema.md) |
| Build, tests, and quality requirements | [Build quality](docs/build-quality.md) |
| Models by question and responsibility | [Diagram index](diagrams/README.md) |
| Examples and local database operations | [Analysis targets](analysis-targets/README.md) · [Neo4j runbook](deployment/neo4j/README.md) |
| Remaining development scope | [Roadmap](docs/roadmap.md) |

## References

- [AnalysisEngine](src/main/java/org/aquilagraph/core/AnalysisEngine.java), `analyze`, program assembly and completion.
- [AnalyzeCommandParser](src/main/java/org/aquilagraph/cli/AnalyzeCommandParser.java), `parse`; [AnalyzeCommandPresenter](src/main/java/org/aquilagraph/cli/AnalyzeCommandPresenter.java), outcome presentation.
- [JavaRelease](src/main/java/org/aquilagraph/model/JavaRelease.java), supported feature releases; [project build](pom.xml), toolchain and packaging configuration.
- [Neo4jAnalysisRunMapper](src/main/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunMapper.java), durable publication surface.
