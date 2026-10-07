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

Unsupported constructs block completion. The analyzer does not execute the analyzed program, run annotation processors, provide whole-program analysis, or accept bytecode as an input. Completed-run publication and explicit AST retention have separate [contracts](docs/contracts/analysis.md#target-publication-and-retention). The [Java AST epics](docs/epics/java-ast-epics.md) define the current expansion round; the [roadmap](docs/roadmap.md) retains open prerequisites.

## Project documentation

| Subject | Document |
| --- | --- |
| Analysis inputs, completion, publication, and failures | [Analysis contract](docs/contracts/analysis.md) |
| Java frontend, source profile, coverage, and graph semantics | [Java analysis design](docs/design/java-analysis.md) |
| Captured source and environment identity | [Source snapshots](docs/design/source-snapshots.md) |
| Retained AST and captured-value formats | [AST value format](docs/design/ast-value-format.md) · [Captured-value encoding](docs/design/captured-value-encoding.md) |
| Repository responsibilities and lifecycle | [Repository design](docs/design/analysis-repository.md) |
| Neo4j labels, relationships, keys, and constraints | [Stored schema](docs/repository/neo4j-schema.md) |
| Build, tests, and quality requirements | [Build quality](docs/build-quality.md) |
| Models by question and responsibility | [Diagram index](diagrams/README.md) |
| Examples and local database operations | [Analysis targets](analysis-targets/README.md) · [Neo4j runbook](deployment/neo4j/README.md) |
| Current expansion requirements | [Java AST epics](docs/epics/java-ast-epics.md) |
| Executable construct implementation tasks | [Task index and dependencies](docs/tasks/java-executable-constructs/README.md) |
| Open prerequisites and scope boundaries | [Roadmap](docs/roadmap.md) |
| Paired review and acceptance procedure | [Independent review protocol](docs/review-protocol.md) |

## References

- [SourceAnalysisRunner](src/main/java/org/aquilagraph/application/SourceAnalysisRunner.java), independent port coordination; [AnalysisQualification](src/main/java/org/aquilagraph/application/AnalysisQualification.java), request completion; [CompletedRunPublicationService](src/main/java/org/aquilagraph/application/CompletedRunPublicationService.java), publication eligibility and closure.
- [AnalyzeCommandParser](src/main/java/org/aquilagraph/cli/AnalyzeCommandParser.java), `parse`; [AnalyzeCommandPresenter](src/main/java/org/aquilagraph/cli/AnalyzeCommandPresenter.java), outcome presentation.
- [JavaRelease](src/main/java/org/aquilagraph/identity/api/JavaRelease.java), supported feature releases; [project build](pom.xml), toolchain and packaging configuration.
- [Neo4jAnalysisRunMapper](src/main/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunMapper.java), durable publication surface.
