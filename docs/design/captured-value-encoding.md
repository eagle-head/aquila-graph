# Canonical captured-value encoding

`CapturedValueEncoding` implements `captured-values/1` for the captured component of qualified source content. It does not replace the existing `SourceFile`, `InputSnapshot`, `CompilerConfiguration`, environment or software identity algorithms. It does not authenticate caller-supplied content, labels, counts or fingerprints. The separate [AST value format](ast-value-format.md) defines full fact encoding and intrinsic storage decoding through `AstCodec`; Source profile qualification belongs to `AstResultAdmission` and `SourceRequirements`.

## Primitive representation

Every integer is big-endian. A string is a nonnegative 32-bit Java character count followed by that many 16-bit UTF-16 code units. There is no Unicode normalization or replacement of unpaired surrogates. A sequence starts with its nonnegative 32-bit count. A byte sequence has a 32-bit count followed by its bytes. Boolean/presence fields use one byte, `00` or `01`. Signed integer values retain 32 or 64 bits. Floating-point values retain all raw IEEE 754 bits, including signed zero and distinct NaN payloads. An enclosing tagged value supplies the primitive field meanings; these primitives are not interchangeable untyped values.

`CanonicalValueWriter` starts with the framed encoding-version string. Each returned byte array is independent of its buffer. Its digest is SHA-256 over the complete bytes written, with the requested namespace prefixed to the lowercase hexadecimal digest. Existing `StableId.sha256` still derives the specified qualified result key from its ordered qualification tuple using its unchanged algorithm.

Text emission fills a bounded byte buffer with raw big-endian UTF-16 code units and appends each chunk to the output in one write. Chunk boundaries are not encoded and do not alter the character count, surrogate preservation, payload bytes, or digests. The [writer compatibility test](../../src/test/java/org/aquilagraph/identity/api/CanonicalValueWriterTest.java) uses `DataOutputStream.writeChars` as an independent byte oracle across chunk boundaries.

`CanonicalValueReader` copies the supplied payload, verifies the exact framed format tag,
and reads those primitive representations without normalization. String and byte lengths
must fit the remaining payload before allocation; negative counts, truncated scalars,
unknown Boolean tags, and unread trailing data are rejected. Collection decoders use its
remaining-byte count to bound their own tagged values. Primitive framing does not admit
an AST, validate fact ownership, or decode the complete retained source format.

## Captured component field order

The stream begins with framed `captured-values/1`, followed by the sections below in this exact order. Presence is explicit even when a present sequence is empty. Each list preserves its contract order and every repeated position.

| Section | Ordered fields when present |
| --- | --- |
| `input` | Presence; snapshot digest algorithm; supplied snapshot digest; source count; each source's supplied key, canonical relative path, supplied content digest and complete decoded content in canonical source order. |
| `environment` | Presence; environment digest algorithm and derived environment digest; configured Java release feature; counted configured class paths; counted configured module paths; counted caller options; existing configuration canonical form; compiler implementation, vendor and version; counted actual analysis options; framed `class-path` and counted dependencies; framed `module-path` and counted dependencies. |
| Each dependency | Closed kind tag `missing`, `file` or `directory`; signed 64-bit byte count; signed 32-bit entry count; supplied fingerprint. |
| `software` | Presence when both captures are present; signature algorithm; source signature; environment signature; derived software signature. The represented signatures are derived from the retained capture labels; they do not certify those labels or establish source-profile eligibility. |

Configured path elements encode the exact retained normalized `Path.toString()` value. The absolute input root and each separate dependency `location` are excluded from this canonical component. Configured paths and path-bearing option strings remain included. The complete retained storage representation must still preserve the excluded provenance; canonical exclusion is not a storage omission.

Available and unavailable source subjects must use these same captured fields. Full AST canonical encoding must additionally retain declaration/occurrence/body/evidence/coverage/diagnostic distinctions, normalize unordered inputs, preserve meaningful sequence positions, and substitute validated source-local roles for result-derived references before qualification. No CFG, request outcome, publication state or storage attempt belongs in canonical AST content. This component alone does not establish those larger contracts.

## Qualified values and implementation boundaries

`AstIdentity` validates all six nonblank components and checks the supplied result key against the complete qualification tuple. `fromContent` derives that key. `BodyRef` carries the exact identity and canonical method plus exactly one written occurrence or explicit `NonwrittenBodyProvenance`; these values do not authenticate occurrence/declaration membership. Source admission must resolve and validate that membership against the exact aggregate.

The identity package compiles from its explicit source root with `--limit-modules java.base`, provided JSpecify definitions, and the original Error Prone/NullAway arguments. Its compiled partition is assembled into the ordinary application class output before consumers compile. Import control permits only Java types, identity values and the two declared nullness annotations. Full classfile closure and base-only runtime verification remain required implementation gates; isolated compilation and import checking do not substitute for them.

## References

- [Source identity contracts](source-snapshots.md#target-qualified-analysis-identity), full capture fields, exclusion limits, qualified identities and constructor assurance limits.
- [Canonical writer](../../src/main/java/org/aquilagraph/identity/api/CanonicalValueWriter.java), primitive field encoding and digest.
- [Captured component encoder](../../src/main/java/org/aquilagraph/identity/api/CapturedValueEncoding.java), exact field order and closed dependency tags.
- [Qualified identity controls](../../src/test/java/org/aquilagraph/identity/api/QualifiedIdentityTest.java), literal byte oracles, supplied-label discrimination, repeated dependency positions and origin exclusivity. These tests establish only their executable observations.
- [Build configuration](../../pom.xml), `compile-source-identity` and `assemble-source-identity`; [import boundary](../../config/checkstyle/import-control.xml), identity scope.
- [Retained AST format obligations](../repository/neo4j-schema.md#target-ast-retention-schema), complete storage and admitted decoding remain separate requirements.
