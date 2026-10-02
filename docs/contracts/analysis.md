# Analysis Contract

This document defines the shared contract for Java source analysis and optional publication. Detailed Java semantics belong to the [Java analysis design](../design/java-analysis.md); stored identities and properties belong to the [Neo4j schema](../repository/neo4j-schema.md). These are implementation contracts, not a report that any particular deployment or input has passed verification.

## Inputs and boundaries

An analysis request supplies a source root, a supported compiler configuration, and either `DO_NOT_SAVE` or `SAVE_COMPLETED`. The frontend captures source content and the compiler environment before returning its project-owned input. The engine constructs the durable representation and classifies completion. The application decides whether to open a repository, and the command-line adapter presents the outcome.[1][2]

The public path facade translates source discovery and read failures to `AnalysisInputException`, and analysis-environment I/O failures to `AnalysisEnvironmentException`. Its snapshot overload retains checked `IOException`. Invalid direct Java arguments and failures outside the declared translation regions retain their actual throwable families; the CLI does not promise a controlled result for every possible failure.[2][5]

## Completion

| Result | Contract |
| --- | --- |
| `REJECTED` | No captured Java sources or no accepted Java types. No `ProgramRepresentation` is attached. |
| `INCOMPLETE` | A representation exists, but blocking coverage, an error diagnostic, or missing analysis of a concrete method body prevents completion. Partial accepted facts and diagnostics remain available. |
| `COMPLETED` | Coverage has no blocking observation, diagnostics contain no error, and analyzed method keys equal the required concrete-method keys. |

`AnalysisEngine.analyze` owns production qualification. `AnalysisRun` construction validates representation presence against completion but does not independently repeat that full qualification. A direct caller supplying a completed run remains responsible for its truthfulness.[1]

## Publication eligibility

| Completion | Save policy | Repository result |
| --- | --- | --- |
| `REJECTED` | `DO_NOT_SAVE` | `NOT_ELIGIBLE`; repository is not opened. |
| `REJECTED` | `SAVE_COMPLETED` | `NOT_ELIGIBLE`; repository is not opened. |
| `INCOMPLETE` | `DO_NOT_SAVE` | `NOT_ELIGIBLE`; repository is not opened. |
| `INCOMPLETE` | `SAVE_COMPLETED` | `NOT_ELIGIBLE`; repository is not opened. |
| `COMPLETED` | `DO_NOT_SAVE` | `NOT_REQUESTED`; repository is not opened. |
| `COMPLETED` | `SAVE_COMPLETED` | Open, save, and close the repository; return `SAVED` only after normal completion. |

The save intent is retained in `AnalysisExecution`. A repository failure does not change the analysis completion already computed. The application associates the completed run with an effective `RepositoryException` through `AnalysisRepositoryException`.[3]

## Publication and failure effects

The repository maps and validates the supplied completed run, prepares and inspects schema constraints in a separate session, and replaces the same-run projection in one managed write transaction per attempt. Repository publication excludes the transient source aggregate.[4]

| Failed phase | Required interpretation |
| --- | --- |
| Mapping or projection validation | No schema or replacement work has started. Mapping is outside the adapter's save-time `Neo4jException` translation region. |
| Constraint preparation, metadata inspection, or schema-session closure | Replacement has not started. Earlier schema changes may remain. |
| Replacement before established commit | An exception alone does not establish rollback or commit. An established noncommit retains the previous projection. |
| Commit acknowledgement unavailable | Whole-run commit is unknown; do not report partial publication as success. |
| Publication-session close after known commit | The close failure does not undo the committed replacement. |
| Repository close after save returns | The close failure prevents a `SAVED` execution from being returned; it does not establish rollback. |

Resource scopes retain a primary failure and its suppressed cleanup failures. A close failure without an earlier primary prevents normal return. The enclosing translation region sees the effective primary throwable; a suppressed `RepositoryException` does not replace an unrelated primary failure. A close attempt is not proof of successful resource release.[3][4]

## Command line results

```text
java -jar target/aquila-graph-0.1.0-SNAPSHOT.jar <source-root> [--release <feature>] [--persist]
```

Admit exactly one root and at most one of each option. Missing, malformed, overflowing, unsupported, or duplicate release values, duplicate persistence, unknown options, wrong root count, and invalid platform paths produce usage rejection before analysis. Direct Java null arguments and unmatched failures remain abrupt.[5]

| Outcome | Standard output | Standard error | Exit |
| --- | --- | --- | ---: |
| Usage rejection | None | Rejection message, then usage | 2 |
| Named input failure | None | `Input rejected: {description}` | 2 |
| Named environment failure | None | `Analysis failed: {description}` | 1 |
| Returned `COMPLETED` run | Summary, then repository status | Ordered diagnostics | 0 |
| Returned `INCOMPLETE` run | Summary, then repository status | Ordered diagnostics | 1 |
| Returned `REJECTED` run | Summary, then repository status | Ordered diagnostics | 2 |
| Named repository failure | Retained completed-run summary | Ordered diagnostics, then `Repository save failed: {description}` | 3 |

A summary prints `Run`, `Software version`, `Completion`, `Java release`, and `Java source files` in that order. Representation presence adds `Java types`, `Java methods`, `Analyzed method bodies`, `CFG operations`, `CFG nodes`, and `CFG edges`. Each diagnostic is printed as `{severity} {stage} {code}: {message}`. Normal execution then prints `Repository: {status}`; repository failure prints no status line.[5]

`AnalyzeCommand.run` borrows its streams and returns an integer. The process entry requests `System.exit` only for a normally returned nonzero result. These calls promise neither successful external output delivery nor ordering between separately observed streams. Abrupt presentation may leave earlier output without a complete report.[5]

## References

1. [AnalysisEngine](../../src/main/java/org/aquilagraph/core/AnalysisEngine.java), `analyze`; [AnalysisRun](../../src/main/java/org/aquilagraph/model/AnalysisRun.java), constructor and `isComplete`.
2. [AnalysisRequest](../../src/main/java/org/aquilagraph/application/AnalysisRequest.java), constructor; [JavaFrontend](../../src/main/java/org/aquilagraph/frontend/JavaFrontend.java), both `analyze` overloads; [CompilerConfiguration](../../src/main/java/org/aquilagraph/model/CompilerConfiguration.java), validation.
3. [AnalysisApplicationService](../../src/main/java/org/aquilagraph/application/AnalysisApplicationService.java), `execute`; [AnalysisExecution](../../src/main/java/org/aquilagraph/application/AnalysisExecution.java), factories; [RepositoryStatus](../../src/main/java/org/aquilagraph/application/RepositoryStatus.java), compatibility; [AnalysisRepositoryException](../../src/main/java/org/aquilagraph/application/AnalysisRepositoryException.java), retained run and cause.
4. [Neo4jAnalysisRunRepository](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunRepository.java), `save`, `ensureSchema`, `saveProjection`, and `close`; [repository design](../design/analysis-repository.md#publication-phases-and-failures).
5. [AnalyzeCommandParser](../../src/main/java/org/aquilagraph/cli/AnalyzeCommandParser.java), `parse` and `ParseState`; [AnalyzeCommandExecutor](../../src/main/java/org/aquilagraph/cli/AnalyzeCommandExecutor.java), `execute`; [AnalyzeCommandPresenter](../../src/main/java/org/aquilagraph/cli/AnalyzeCommandPresenter.java), presentation methods; [AnalyzeCommand](../../src/main/java/org/aquilagraph/cli/AnalyzeCommand.java), `run`; [AquilaGraphApplication](../../src/main/java/org/aquilagraph/AquilaGraphApplication.java), `main`.
