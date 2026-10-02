# Source Snapshot Identity and Ordering

Source capture produces immutable values with a canonical order and content-derived identity. The factories derive identities; public constructors validate supplied values within their declared bounds. Capture and identity are distinct from language acceptance and run completion.[1][2]

## Canonical sources

`SourceFile.fromContent` normalizes a root-relative path, rejects absolute or escaping paths, captures decoded UTF-8 content, computes its SHA-256 digest, and derives the framed source key from canonical path and content digest. `canonicalRelativePath` preserves case and uses `/` separators without reading the filesystem.[1]

`InputSnapshot.fromSources` sorts a copy by case-sensitive lexicographic canonical path and derives the snapshot digest from ordered path/digest pairs. Duplicate keys or canonical paths are rejected. Empty and singleton snapshots are valid. The absolute checkout root is not a snapshot-digest component.[1]

## Supplied values and discovery

| Boundary | Validation and responsibility |
| --- | --- |
| `SourceFile` constructor | Nonnull fields and a normalized, relative, non-escaping path. It retains the supplied key, digest, and content without authenticating them or requiring a `.java` suffix. |
| `InputSnapshot` constructor | Normalized absolute root, copied list, unique source keys, and strictly increasing canonical paths. It rejects incorrect ordering rather than repairing it. It does not check root existence or rederive supplied identities. |
| `InputSnapshot.fromSources` | Canonical ordering and snapshot derivation from the supplied source facts; it does not rederive each source content digest. |
| `JavaSourceLoader.discover` | Directory admission, recursive discovery of regular `.java` files, UTF-8 reads, path-aware I/O causes, and traversal closure. Partial capture is not returned after failure. |
| `JavaFrontend` path facade | Source-capture failures become `AnalysisInputException`; analysis-environment I/O becomes `AnalysisEnvironmentException`. The snapshot overload retains checked I/O. |

These validation limits matter for callers that construct records directly. Production capture derives the content identities; accepting a supplied record does not certify its provenance.[1][2]

## Compiler environment and software version

The environment snapshot records the compiler implementation, runtime and vendor, actual analysis options, compiler configuration, and ordered dependency snapshots. Dependency capture includes content fingerprints and entry/byte counts. The pipeline recaptures the environment before returning and rejects detected changes.[3]

The production environment digest includes compiler identity, ordered options, dependency roles and fingerprints. Separate dependency-location metadata is excluded, but an option containing a location remains an input. The stored compiler-configuration key includes its configured paths. Relocating a checkout therefore does not change the source digest, while relocating configured options can change the environment digest.[3]

`SoftwareVersion` combines source and environment signatures. The production run key derives from that combined signature and excludes the analyzer build version. The schema preserves these identities; changing an identity algorithm or migrating old stored projections requires an explicit compatibility decision.[4]

## References

1. [SourceFile](../../src/main/java/org/aquilagraph/model/SourceFile.java), constructor, `fromContent`, and `canonicalRelativePath`; [InputSnapshot](../../src/main/java/org/aquilagraph/model/InputSnapshot.java), constructor and `fromSources`; [StableId](../../src/main/java/org/aquilagraph/model/StableId.java), framing.
2. [JavaSourceLoader](../../src/main/java/org/aquilagraph/frontend/JavaSourceLoader.java), `discover` and `readSource`; [JavaFrontend](../../src/main/java/org/aquilagraph/frontend/JavaFrontend.java), overload boundaries.
3. [AnalysisEnvironmentCapture](../../src/main/java/org/aquilagraph/frontend/AnalysisEnvironmentCapture.java), capture and `verifyUnchanged`; [CompilerDependencyCapture](../../src/main/java/org/aquilagraph/frontend/CompilerDependencyCapture.java), dependency fingerprints; [AnalysisEnvironmentSnapshot](../../src/main/java/org/aquilagraph/model/AnalysisEnvironmentSnapshot.java), derivation; [CompilerConfiguration](../../src/main/java/org/aquilagraph/model/CompilerConfiguration.java), `canonicalForm`; [JavaAnalysisPipeline](../../src/main/java/org/aquilagraph/frontend/JavaAnalysisPipeline.java), capture and return lifecycle.
4. [SoftwareVersion](../../src/main/java/org/aquilagraph/model/SoftwareVersion.java), `from` and `signature`; [AnalysisEngine](../../src/main/java/org/aquilagraph/core/AnalysisEngine.java), run identity; [Neo4j schema](../repository/neo4j-schema.md#stable-keys-and-constraints).
