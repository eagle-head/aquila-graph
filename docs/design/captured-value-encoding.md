# Canonical captured-value encoding

`CapturedValueEncoding.encode` exposes the `captured-values/3` value frame in
the Identity module. It records captured inputs with their supplied values and
derived signatures. Those signatures carry the assurance limits of the input
constructors. [Source snapshot identity](source-snapshots.md) defines the identity
algorithms. The [AST value format](ast-value-format.md) uses its own tagged
source shapes for canonical AST content, storage, and intrinsic readmission.

## Primitive representation

Every integer is big-endian. A string is a nonnegative 32-bit Java character count followed by that many 16-bit UTF-16 code units. There is no Unicode normalization or replacement of unpaired surrogates. A sequence starts with its nonnegative 32-bit count. A byte sequence has a 32-bit count followed by its bytes. Boolean/presence fields use one byte, `00` or `01`. Signed integer values retain 32 or 64 bits. Floating-point values retain all raw IEEE 754 bits, including signed zero and distinct NaN payloads. An enclosing tagged value supplies the primitive field meanings; these primitives are not interchangeable untyped values.

`CanonicalValueWriter` starts with the framed encoding-version string. Each returned byte array is independent of its buffer. Its digest is SHA-256 over the complete bytes written, with the requested namespace prefixed to the lowercase hexadecimal digest. Existing `StableId.sha256` still derives the specified qualified result key from its ordered qualification tuple using its unchanged algorithm.

Text emission fills a bounded byte buffer with raw big-endian UTF-16 code units and appends each chunk to the output in one write. Chunk boundaries are absent from the encoding. The [writer compatibility test](../../src/test/java/org/aquilagraph/identity/api/CanonicalValueWriterTest.java) uses `DataOutputStream.writeChars` as an independent byte oracle across chunk boundaries.

`CanonicalValueReader` copies the supplied payload, verifies the exact framed format tag,
and reads those primitive representations without normalization. String and byte lengths
must fit the remaining payload before allocation; negative counts, truncated scalars,
unknown Boolean tags, and unread trailing data are rejected. Collection decoders use its
remaining-byte count to bound their own tagged values. Primitive framing does not admit
an AST, validate fact ownership, or decode the complete retained source format.

## Captured component field order

The stream begins with framed `captured-values/3`, followed by the sections below
in this order. Each section starts with its name and presence flag. A present
empty sequence retains its zero count. Lists preserve order and repeated positions.

| Section | Ordered fields when present |
| --- | --- |
| `input` | Snapshot digest algorithm, supplied snapshot digest, source count, then each source's key, canonical relative name, captured relative location, content digest, and decoded content in canonical source order. |
| `environment` | Environment digest algorithm, derived environment digest, `analysis-environment/2` identity scheme, configured Java release feature, counted configured class locations, counted configured module locations, counted caller options, compiler-configuration canonical form, compiler implementation, vendor and version, counted actual analysis options, framed `class-path` and counted dependencies, then framed `module-path` and counted dependencies. |
| Each dependency | Kind tag `missing`, `file` or `directory`, 64-bit byte count, 32-bit entry count, fingerprint, dependency interpretation state, and counted module candidates. Each candidate carries member, lookup name, descriptor-name presence and optional name, then name origin. |
| `software` | Present when both captures are present. Carries signature algorithm, source signature, environment signature, and the derived software signature. |

Each encoded location carries its syntax name, root-kind name, counted root
components, counted normalized path components, and normalized spelling. The
`compiler-configuration/2` frame uses the same location representation. The
absolute input root and separate dependency `location` fields are excluded
from the canonical capture frame. Configured locations and path-bearing options
remain included. The retained AST storage form preserves the excluded provenance.

Source qualification applies the [AST tagged schema](ast-value-format.md#qualification-and-canonical-content)
for both admitted content and unavailable subjects. The application retains
the source-owned unavailable digest when it constructs a request identity.

## Qualified values and implementation boundaries

`AstIdentity` validates all six nonblank components and checks the supplied result key against the complete qualification tuple. `fromContent` derives that key. `BodyRef` carries the exact identity and canonical method plus exactly one written occurrence or explicit `NonwrittenBodyProvenance`; these values do not authenticate occurrence/declaration membership. Source admission must resolve and validate that membership against the exact aggregate.

The [Identity module](../../src/main/java/org.aquilagraph.identity/module-info.java)
exports its API and uses static JSpecify metadata. The
[build contract](../build-quality.md#product-compilation-partitions) owns
compilation and dependency-boundary checks.

## References

- [Source identity contracts](source-snapshots.md#target-qualified-analysis-identity), full capture fields, exclusion limits, qualified identities and constructor assurance limits.
- [Canonical writer](../../src/main/java/org.aquilagraph.identity/org/aquilagraph/identity/api/CanonicalValueWriter.java), primitive field encoding and digest.
- [Captured component encoder](../../src/main/java/org.aquilagraph.identity/org/aquilagraph/identity/api/CapturedValueEncoding.java) and [compiler configuration](../../src/main/java/org.aquilagraph.identity/org/aquilagraph/identity/api/CompilerConfiguration.java), exact framing and field order.
- [Qualified identity controls](../../src/test/java/org/aquilagraph/identity/api/QualifiedIdentityTest.java), literal byte oracles, supplied-label discrimination, repeated dependency positions and origin exclusivity. These tests establish only their executable observations.
- [Build configuration](../../pom.xml), `compile-product-modules`, and [import boundary](../../config/checkstyle/import-control.xml), identity scope.
- [Retained AST format obligations](../repository/neo4j-schema.md#target-ast-retention-schema), complete storage and admitted decoding remain separate requirements.
