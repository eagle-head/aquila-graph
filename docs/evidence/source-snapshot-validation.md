# Source Snapshot Validation

## Scope and design evidence

This record covers the canonical source-order correction and its identity compatibility
on Windows with the repository's Java 25 toolchain. The pre-existing uncommitted annotation
work is preserved. The [design](../design/source-snapshots.md) allocates canonical source
representation/identity to `SourceFile`, collection order/digest to `InputSnapshot`, and
filesystem discovery/UTF-8 reading to `JavaSourceLoader`.

Local sources were used before implementation: verified Wazlawick §9.2, physical PDF
pp. 286–289; the Java engineering and testing references listed in the design; and the
installed JDK source archive's `Path`, `String`, and `WindowsPath` comparison contracts.
No web lookup, dependency upgrade, or new runtime abstraction was needed.

## Changed contracts and executable witnesses

| Boundary | Changed symbols | Behavioral witnesses |
| --- | --- | --- |
| Source value | `SourceFile.fromContent`, `canonicalRelativePath`, normalized-path validation and content hashing | `SourceFileTest`: fixed content digest/key, normalized path, nested/non-ASCII spelling, case/content distinctions, invalid ownership. |
| Source collection | `InputSnapshot.SOURCE_ORDER`, validating constructor, `fromSources` | `InputSnapshotTest`: literal canonical order, reversed encounter order, exact digest compatibility, root relocation, defensive collection ownership, direct-constructor order rejection, duplicate key/path rejection, empty/singleton and changed-content/path partitions. |
| Filesystem entry | `JavaSourceLoader.discover`, `readSource` | `JavaSourceLoaderTest`: root-level uppercase/lowercase special files, nested separator/punctuation and non-ASCII order, fixed source/snapshot identities, existing malformed UTF-8 and missing-root outcomes. |
| Annotation integration | Original root-level companion layouts; source-key selection in `addUnwrittenAnnotation` | `JavaAnnotationCompletionTest.givenAnnotationFreePackageSourceWithAnExtraFact_whenReconciled_thenCapturedSourceOwnsTheBlockingOutcome` and the corresponding module test retain exact diagnostics and COMPLETED-to-INCOMPLETE assertions. |

Fixed digest expectations were calculated independently with Python 3.13 `hashlib.sha256`:
UTF-8 content bytes for the content digest, followed by four-byte unsigned big-endian
UTF-8 length framing for each namespace and identity component, matching the existing
documented `StableId` contract. Expected order is a literal sequence, never a call to the
production comparator. No test expectation invokes the factory being tested to calculate
an expected hash.

## Executed results

All test JVM commands used
`-DargLine=-Djava.io.tmpdir=D:/Projects/aquila-graph/aquila-graph/.tmp/test-runtime`.
The logs below retain failures as failures.

| Check | Observed result | Evidence |
| --- | --- | --- |
| Red-first discovery and restored package/module controls | Exit 1: 23 tests, three errors from the original ordering exception. | [Red log](../../.tmp/verification/source-snapshot-red.log) |
| Initial corrected domain and integration controls | Exit 1: 34 tests, one assertion failure. Canonical order exposed first-match package-owner selection in the fixture; production discovery and all domain tests passed. | [Development log](../../.tmp/verification/source-snapshot-focused.log) |
| Corrected source-specific fixture ownership | Exit 0: 34 tests, no failures/errors/skips. The fixture identifies the requested source before selecting its package/module node; assertions remain intact. | [Focused log](../../.tmp/verification/source-snapshot-focused-final.log) |
| `.\Invoke-Build.ps1 test` (repository entry point) | Exit 0: 598 tests, no failures/errors/skips, including the subsequently added filesystem identity regression. | [Full log](../../.tmp/verification/source-snapshot-full.log) |
| `.\Invoke-Build.ps1 quality` | Exit 0. Formatting, import/type-reference checks, compiler/nullness, Checkstyle, PMD/CPD, SpotBugs, dependency/license and Javadoc checks completed. This target skips tests by configuration. | [Quality log](../../.tmp/verification/source-snapshot-quality.log) |
| `.\Invoke-Build.ps1 integration-test-neo4j` | Exit 0: three packaged-process tests and one real local Neo4j test, no failures/errors/skips. | [Integration log](../../.tmp/verification/source-snapshot-integration.log), [report inventory](../../.tmp/verification/source-snapshot-test-inventory.log) |
| PlantUML syntax, both updated class sources | Exit 0 for each. | [Frontend](../../.tmp/verification/source-snapshot-frontend-check.log), [model](../../.tmp/verification/source-snapshot-model-check.log) |
| CodeGraph synchronization/status | Exit 0; three production files synchronized; index current with 203 Java files. | [Sync](../../.tmp/verification/source-snapshot-codegraph-sync.log), [status](../../.tmp/verification/source-snapshot-codegraph-status.log) |
| `git diff --check` | Exit 0, empty output. | [Diff check](../../.tmp/verification/source-snapshot-diff-check.log) |

The unchanged PMD auxiliary-classpath advisory remains visible in the quality log;
no warning suppression, exclusion, threshold change, or weakened assertion was introduced.
The local Neo4j service was started through the repository Compose configuration and
reported healthy. Its password was passed through the test process environment without
printing it. Integration controls use their existing run-scoped cleanup.
After testing, Compose was stopped with `down` without volume removal, returning
the service to its initially inactive state; [cleanup log](../../.tmp/verification/source-snapshot-neo4j-stop.log).

## Documentation, modeling, and limits

The Java analysis design, source snapshot design, class companions, navigation index,
and both class diagram sources describe the same responsibilities. Component dependencies
remain frontend-to-model, and the public analysis sequence still obtains an `InputSnapshot`
from discovery; those view structures do not change. Production `core`, repository mappers,
and the Neo4j schema have no diff from this increment.

PlantUML's verified local guide 1.2025.0 was read for class members, visibility, static
members, labeled dependencies, and notes (§§3.2–3.3, 3.5–3.7, 3.9; PDF pp. 63–70),
SHA-256 `1455bd5f63603ede0545f66d67de2162cce7b7b80e03e98d20e674e6c272a8a6`.
Syntax acceptance does not establish model semantics or visual quality. Runtime validation
here is Windows-specific; the platform-independent contract and deterministic expectations
do not constitute an executed Linux/macOS test result. No exhaustive filesystem, concurrency,
performance, processor-environment, or new AST-persistence claim is made.

The changed diagram deliverables are PlantUML sources. The existing SVG previews have not
been regenerated during this reopening; no rendering or visual-layout assessment was requested.

Independent review and administrative closeout remain pending. Explicit authorization for
the two reviewer subagents required by the task protocol was requested through the chat's
input tool; no reply has been received at this point. The current execution rule requires
explicit subagent authorization. A self-review is not relabeled as independent review.
The read-only [review brief](../../.tmp/verification/source-snapshot-review-brief.md) is ready.
This record does not yet authorize a DONE transition.

## References

- [Source snapshot design](../design/source-snapshots.md), contracts and local reading record.
- [Reopened task](../../.tmp/java-ast-epics/ast-e05-annotation-occurrences-and-values-tasks.md), Source snapshot ordering authorized reopening and §8 review protocol.
- [Build quality contract](../build-quality.md), test groups and gate responsibilities.
- Production/test source links are listed in the design; exact execution logs are linked in the result table.
