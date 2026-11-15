# Source Snapshot Identity and Ordering

Source capture produces immutable values with a canonical order and content-derived identity. The factories derive identities; public constructors validate supplied values within their declared bounds. Capture and identity are distinct from language acceptance and run completion.[1][2]

## Captured locations

`CapturedLocation` represents POSIX or Windows syntax, a root kind, root
components, normalized path components, and preserved normalized spelling. A
value may be rootless, POSIX absolute, Windows drive absolute, or Windows UNC.
Windows UNC accepts either separator. Windows drive-relative paths, incomplete
UNC roots, and root-relative paths without a drive or share are rejected.
Parsing removes dot segments and rejects traversal above the root or outside a
rootless name. It preserves case and surviving separator spelling.

Value equality compares both syntax and normalized spelling. Containment
compares case-sensitive root and path components within the same syntax and
root kind. Resolution appends a rootless value of the same syntax. A source
file's canonical relative name joins its components with `/` and preserves
case. These operations use captured values independently of the host.

Native discovery captures paths at the frontend boundary.
`SnapshotSourceCatalog` supplies syntax-qualified URIs and retained source
text to the compiler. For native compiler dependency access, `NativeLocations`
requires an absolute location of the host syntax and checks that native
conversion preserves the root and components. Retained queries read the
captured values after compiler closure. [CapturedLocation](../../src/main/java/org.aquilagraph.identity/org/aquilagraph/identity/api/CapturedLocation.java),
[NativeLocations](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/NativeLocations.java),
and [SnapshotSourceCatalog](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/SnapshotSourceCatalog.java)
own these boundaries.

## Canonical sources

`SourceFile.fromContent` takes a captured relative location and decoded text.
It derives the content digest from the UTF-8 encoding and frames the canonical
relative name and content digest to derive the source key.[1]

`InputSnapshot.fromSources` sorts a copy by case-sensitive lexicographic canonical path and derives the snapshot digest from ordered path/digest pairs. Duplicate keys or canonical paths are rejected. Empty and singleton snapshots are valid. The absolute checkout root is not a snapshot-digest component.[1]

## Supplied values and discovery

| Boundary | Validation and responsibility |
| --- | --- |
| `SourceFile` constructor | Nonnull fields and a rootless captured location. The supplied key, digest, and content retain caller provenance. |
| `InputSnapshot` constructor | Absolute captured root, copied list, file locations of the root's syntax, unique source keys, and strictly increasing canonical paths. Supplied digest labels remain caller assertions. |
| `InputSnapshot.fromSources` | Canonical ordering and snapshot derivation from the supplied source facts; it does not rederive each source content digest. |
| `JavaSourceLoader.discover` | Directory admission, recursive discovery of regular `.java` files, UTF-8 reads, path-aware I/O causes, and traversal closure. Partial capture is not returned after failure. |
| `JavaFrontend` path facade | Source-capture `IOException` and `IllegalArgumentException` become `AnalysisInputException`; analysis-environment `IOException` becomes `AnalysisEnvironmentException`. Lazy source-traversal `UncheckedIOException` retains its family and cause. The snapshot overload retains checked I/O. |

These validation limits matter for callers that construct records directly. Production capture derives the content identities; accepting a supplied record does not certify its provenance.[1][2]

## Compiler environment and software version

The environment snapshot records the compiler implementation, runtime and vendor,
actual analysis options, compiler configuration, and separate ordered class-path
and module-path dependency snapshots. Both sequences retain repeated entries.
Each captured dependency must match the configured location at its role and
position. Dependency capture includes interpretation state, module candidates,
content fingerprints, and entry/byte counts. For nonempty compiler work the
pipeline recaptures the environment before returning and rejects detected
changes. Empty capture returns without acquiring a compiler session.[3]

The `analysis-environment/2` digest includes the release, compiler identity,
ordered actual options, dependency roles, kinds, fingerprints, interpretation
states, and module lookup names, optional descriptor names and name origins.
Separate dependency-location provenance and module member locations are excluded
from this digest. Option text remains significant. The configuration's separate
`compiler-configuration/2` frame includes its configured locations and caller
options. The checkout root is excluded from the source snapshot digest.[3]

`SoftwareVersion` combines source and environment signatures. `AnalysisRequestIdentity.key()` separately derives the application request key from the operation, qualified source subject, and exact selection descriptor. A software signature alone does not identify the current analysis request. Changing either identity algorithm or migrating older stored projections requires an explicit compatibility decision.[4]

## Target qualified analysis identity

Result qualification distinguishes source-model formats, source profiles and
semantic-production revisions for an exact represented source. The
[analysis design](java-analysis.md#target-identity-and-retention-crossings)
assigns ownership across source, CFG, and application.

The [AST canonical encoding](ast-value-format.md#qualification-and-canonical-content)
uses registered shapes for the represented snapshot, source, environment,
configuration, and dependencies. Locations carry explicit syntax, root and
spelling. Dependency shapes retain role-separated lists and module semantics.
The tagged source input encodes retained values directly, with their supplied
labels and constructor assurance limits. It excludes absolute checkout-root
and separate dependency-location provenance. Configured locations and path-bearing
options remain identity inputs. The binary and graph forms retain the excluded
provenance. The separate [Identity capture API](captured-value-encoding.md)
defines its own value frame.[1][3][4]

`AstIdentity` retains the software signature, source-model format version, AST-profile version, semantic-production revision and canonical AST-content digest. Define `astResultKey = StableId.sha256("ast-result", softwareSignature, sourceModelVersion, astProfileVersion, semanticProductionRevision, canonicalContentDigest)`. Each component has explicit framing; no opaque key is parsed to recover ownership. The canonical digest covers the full canonical captured values above, declarations, occurrence facts/ranges/edges, body provenance, evidence states/values, profile coverage and ordered creating-site diagnostics, including failed/negative/gap states. Supplied source/environment/software identities do not substitute for encoding retained content under the constructor assurance limits above. Use a versioned canonical encoding with distinct variant tags, framed fields, explicit absence and preserved ordered positions. AST qualification is derived from those facts; supplied labels cannot certify completion. Reordered unordered input must normalize to the same encoding; semantically relevant ordering stays encoded. Diagnostic codes, causes, addresses, ordered slots and retained messages are content and therefore digest inputs.

`BodyRef` contains exact `AstIdentity`, canonical method key and source-body occurrence or nonwritten-body provenance. CFG owns `GraphSourceRef`, containing a required `BodyRef`, optional typed `SourceOccurrence`, and required nonblank generated role. Body and occurrence must carry the same AST identity; generated steps retain body-level provenance instead of fabricating a span. Resolve any occurrence against the source aggregate for that identity, not an auxiliary namespace. Declaration/type references may name external symbols; they must never imply that those declarations are owned AST occurrences.

Canonical AST content encodes local source/semantic roles, facts and event addresses before result qualification. It excludes `astResultKey`, the digest field itself and references derived from that result key; encode those references by their source-local role during hashing, then qualify them with the derived identity. This avoids a circular content definition. Result-qualification labels are derived and checked against encoded facts, not trusted digest inputs. No CFG profile, graph, request/publication status or storage attempt enters the AST digest.

`derivationKey = StableId.sha256("cfg-derivation", astResultKey, cfgProfileVersion, graphModelVersion, methodKey, canonicalGraphContentDigest)`. Graph content includes every retained public value/operation/edge attribute, typed operands/definitions, selected references/invocation, detail, exception categories, diagnostics/provenance and source associations in canonical order; identifiers that derive from this key must be excluded or represented as local roles during digest derivation to avoid circular hashing. Existing operation/value/transfer local role semantics are retained inside this qualified graph identity. The [public graph contract](java-analysis.md#public-cfg-values-and-queries) owns payload and query ordering.

Application request identity derives from the operation, a framed source subject,
and a framed selection descriptor. The operation distinguishes AST-only execution,
production with derivation, and derivation of existing source. The `AST` subject
contains the exact `astResultKey`. The `UNAVAILABLE` subject contains the requested
profile and a source-owned canonical digest. For unavailable production with copied
input, [source admission](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/internal/AstResultAdmission.java)
uses the tagged source input, including its retained coverage and diagnostics.
For an empty capture, [the empty-source frame](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/internal/CanonicalAstEncoding.java)
retains the captured snapshot, explicit absence of an environment, requested
configuration, empty coverage and empty diagnostic list. An unavailable subject
has no AST result identity. Requests with the same represented inputs can share
a content key across attempts.[4]

### Application selection and CFG request identity

Production carries a [`CfgSelection`](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/CfgSelection.java)
with a scope, the requested ordered keys, and a CFG profile. `METHODS` keeps
the caller's keys, including invalid entries. `ALL_BODIES` has an empty
requested list and resolves the produced declarations that have bodies in
canonical method order. The CLI requests `ALL_BODIES`. Available production
binds the effective keys to the exact produced AST for derivation. With
unavailable source the descriptor is unbound, the effective `ALL_BODIES` list
is empty, and the application makes no CFG request. [`SourceComputation`](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/SourceComputation.java),
[`AnalyzeCommandArguments`](../../src/main/java/org.aquilagraph.cli/org/aquilagraph/cli/AnalyzeCommandArguments.java),
and the [application contract](../contracts/analysis.md#target-independent-analysis-contracts)
define this crossing.

In `AnalysisRequestIdentity.key()`, the `analysis-selection/2` frame starts
with a selection variant. Each counted list preserves its order and entries.

| Variant | Remaining fields in frame order |
| --- | --- |
| `NOT_REQUESTED` | None. An explicitly requested empty list uses a selection variant. |
| `PRODUCTION_SELECTION` | `BOUND` with the exact produced `AstIdentity`, or `UNBOUND` with no identity fields. Then scope, counted requested method keys, CFG profile version, graph model version, and counted effective method keys. |
| `EXISTING_SELECTION` | Exact caller-requested `AstIdentity`, counted requested method keys, CFG profile version, and graph model version. These fields survive unavailable supplied source. |

[`CfgRequest.identityKey()`](../../src/main/java/org.aquilagraph.cfg/org/aquilagraph/cfg/api/CfgRequest.java)
separately frames the exact AST identity, ordered method-key list, CFG profile
and graph model in `cfg-request/1`. Computed completion, graph results and
publication effects are absent as independent fields in both request keys.

Foreign snapshot/environment/result/profile/format associations reject, even when method signatures, coordinates or node keys happen to match. Decoding revalidates source membership, qualified references, canonical order and intrinsic consistency before returning `FOUND`. Current direct-record assurance limits remain visible: consistent supplied values do not authenticate compiler production.

This changes result/graph namespaces and adds versions to retained identities. Legacy `runId` and graph IDs remain interpretable only under their legacy format; storage must not silently treat them as qualified identities or overwrite them through same-run replacement. Backfill cannot derive an AST from a legacy CFG. Reproduction from captured input is a separate analysis with new qualified result identity. The [canonical AST codec](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/retention/AstCodec.java) and [graph encoding](../../src/main/java/org.aquilagraph.cfg/org/aquilagraph/cfg/internal/CanonicalGraphEncoding.java) own separate retained-value encodings; the [completed-run mapper](../../src/main/java/org.aquilagraph.repository/org/aquilagraph/repository/internal/neo4j/Neo4jAnalysisRunMapper.java) uses the current application request key. Preserve complete ordered diagnostic values, including repeated events, and explicitly supported compatibility metadata. [Pending independent acceptance](java-analysis.md#model-acceptance-and-verification) identifies the observation limits.

## References

1. [SourceFile](../../src/main/java/org.aquilagraph.identity/org/aquilagraph/identity/api/SourceFile.java), constructor, `fromContent`, and `canonicalRelativePath`; [InputSnapshot](../../src/main/java/org.aquilagraph.identity/org/aquilagraph/identity/api/InputSnapshot.java), constructor and `fromSources`; [StableId](../../src/main/java/org.aquilagraph.identity/org/aquilagraph/identity/api/StableId.java), framing.
2. [JavaSourceLoader](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaSourceLoader.java), `discover` and `readSource`; [JavaFrontend](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaFrontend.java), overload boundaries.
3. [AnalysisEnvironmentCapture](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/AnalysisEnvironmentCapture.java), capture and `verifyUnchanged`; [CompilerDependencyCapture](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/CompilerDependencyCapture.java), dependency fingerprints; [AnalysisEnvironmentSnapshot](../../src/main/java/org.aquilagraph.identity/org/aquilagraph/identity/api/AnalysisEnvironmentSnapshot.java), derivation; [DependencySnapshot](../../src/main/java/org.aquilagraph.identity/org/aquilagraph/identity/api/DependencySnapshot.java), constructor assurance limits; [CompilerConfiguration](../../src/main/java/org.aquilagraph.identity/org/aquilagraph/identity/api/CompilerConfiguration.java), `canonicalForm`; [JavaAnalysisPipeline](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaAnalysisPipeline.java), capture and return lifecycle.
4. [SoftwareVersion](../../src/main/java/org.aquilagraph.identity/org/aquilagraph/identity/api/SoftwareVersion.java), `from` and `signature`; [AnalysisRequestIdentity](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisRequestIdentity.java), `key` and `sourceSubject`; [Neo4j schema](../repository/neo4j-schema.md#stable-keys-and-constraints).
