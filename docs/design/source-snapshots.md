# Source Snapshot Identity and Ordering

## Decision and scope

A captured source set has one platform-independent order: the case-sensitive
lexicographic order of normalized root-relative path strings with `/` separators.
This preserves the ordering and identity material already used by source discovery.
The snapshot must validate that same order instead of the native `Path` order.[1][2]

The defect is an ownership disagreement: `JavaSourceLoader` creates canonical
strings for sorting and hashing, while `InputSnapshot` validates `Path.compareTo`.
The installed JDK documents path comparison as provider/platform-specific, and its
Windows implementation folds case. Its `String.compareTo` contract preserves case.[3]
The recorded package/module tests reached this disagreement before parsing.[4]

## Domain responsibilities and Java design

| Object | Responsibility | Collaborations |
| --- | --- | --- |
| `SourceFile` | Own immutable source content, normalized relative path, canonical path query, and content-derived source identity. `fromContent(Path, String)` constructs a captured value. | JDK UTF-8/SHA-256 and the existing `StableId` framing contract. |
| `InputSnapshot` | Own the immutable ordered source collection, its ordering invariant and aggregate digest. `fromSources(Path, List<SourceFile>)` orders captured values and computes their digest. | One private `Comparator<SourceFile>` derived from `SourceFile.canonicalRelativePath`, shared by factory and constructor validation. |
| `JavaSourceLoader` | Discover Java files, read UTF-8 content, and preserve I/O failure causes. | Delegate source creation and snapshot construction to their domain owners. |

Design judgment: apply Wazlawick's allocation of derived knowledge and coordination
to the objects that own the relevant information (§9.2, PDF pp. 286–289).[5]
This is an extrapolation from information-systems design to a Java source analyzer.
Existing records remain immutable domain values with behavior; no adapter, wrapper,
strategy hierarchy, or platform branch is required. The comparator belongs to the
collection invariant; `SourceFile` does not implement `Comparable`, because ordering
by path alone is not its complete record equality. Java-specific guidance favors
named factories, explicit invariants, and one canonical model for identities.[6]

## Operation contracts

- `SourceFile.fromContent`: normalize and validate a root-relative path, capture
  non-null decoded content, compute the existing raw UTF-8 SHA-256 content digest,
  and derive the existing framed source key from canonical path and content digest.
- `SourceFile.canonicalRelativePath`: expose the existing canonical spelling without
  changing case or depending on default locale. This query does not access disk.
- `InputSnapshot.fromSources`: accept captured values in any encounter order,
  defensively produce canonical order, and derive the existing framed snapshot
  digest from ordered `(canonical path, content digest)` pairs. Do not mutate the
  caller's list. Reject duplicate source keys and duplicate canonical paths.
- The public `InputSnapshot` constructor remains a validation boundary for already
  ordered facts: reject a noncanonical order instead of silently repairing it.
- `JavaSourceLoader.discover`: return the canonical snapshot for every discovered
  Java file, independently of traversal order. Preserve malformed UTF-8 and missing
  root errors and their causes. File-system traversal and reading remain frontend work.

## Compatibility and boundaries

Keep the existing record components, public constructors, source-key namespace,
snapshot namespace, length framing, UTF-8 content bytes, and SHA-256 algorithms.
For the same canonical source paths and content, source keys and snapshot digest
remain unchanged. Absolute checkout roots are not digest inputs. Previously rejected
mixed-case source sets gain the same canonical order already intended by discovery.
Native paths remain available for actual filesystem/compiler access; their native
comparison no longer defines snapshot validity. No case folding or filesystem-alias
deduplication is introduced. Cross-provider record equality is not promised.

The change does not publish annotation AST data, change CFG construction, or change
the Neo4j schema. Existing stored identities must remain compatible; exact digest
regressions and the broader suites check the affected boundaries.[1][2]

## Behavioral obligations

| Obligation | Observable evidence |
| --- | --- |
| Original Windows failure | Public discovery with `Sample.java`, `package-info.java`, and `module-info.java`; restore both annotation completion controls to their original root-level companion layout. |
| Portable order | Literal expected order including uppercase/lowercase, a directory separator versus punctuation, and non-ASCII names; no expected order computed by the production comparator. |
| Collection invariant | Constructor accepts canonical order and rejects reversed order, duplicate canonical paths, and duplicate source keys; empty and singleton boundaries remain valid. |
| Identity compatibility | Fixed independently calculated content/source/snapshot digest values; changed content/path changes identity and reordered input preserves it. |
| Ownership | Factories and constructors do not retain mutable input collections; rejected input is not silently sorted by the validating constructor. |
| Integration | Existing annotation, compiler, CFG, process and repository tests retain their behavioral assertions. |

Tests exercise public contracts with real files or immutable domain values. The
red-first witness establishes the actual failure; passing assertions establish only
the exercised partitions, not exhaustive filesystem behavior.[7] Timing, concurrency,
fuzzing and remote service failure injection do not discriminate this ordering defect.

## References

1. [JavaSourceLoader](../../src/main/java/org/aquilagraph/frontend/JavaSourceLoader.java), `discover` and `readSource`.
2. [SourceFile](../../src/main/java/org/aquilagraph/model/SourceFile.java), [InputSnapshot](../../src/main/java/org/aquilagraph/model/InputSnapshot.java), and [StableId](../../src/main/java/org/aquilagraph/model/StableId.java).
3. Installed Temurin 25.0.4+101.0.LTS `lib/src.zip`: `java.base/java/nio/file/Path.java`, `compareTo` contract, lines 1000–1020; `java.base/java/lang/String.java`, `compareTo`, lines 2072–2113; `java.base/sun/nio/fs/WindowsPath.java`, `compareTo`, lines 785–805. The internal implementation was read as platform evidence only; production code imports no internal API.
4. [Original failing execution](../../.tmp/verification/annotation-completion-review-corrections-focused-second.log), package/module failures; [historical disposition](../../.tmp/java-ast-epics/ast-e05-annotation-occurrences-and-values-tasks.md), Source snapshot ordering reopening supersedes the earlier deferral.
5. Wazlawick, *Object-Oriented Analysis and Design for Information Systems* (2014), §9.2, physical PDF pp. 286–289. Local source verified: SHA-256 `d4cf6fe75c7a42d470091bc2b0023d0b1117682fe6a407d544464f76c3f47aee`, 469 pages, 26,449,687 bytes. The complete subsection was read before this decision.
6. Local `java-engineering` skill, `references/style-and-design.md` (Encapsulation and object design; Canonical and content-addressed outputs), `references/language-and-api.md` (Equality, hashing, ordering, and identity), and `references/build-testing-quality.md`.
7. Local `comprehensive-testing` skill, `references/writing-tests.md` (behavioral seam, observable oracles, partitions, red-first verification).
