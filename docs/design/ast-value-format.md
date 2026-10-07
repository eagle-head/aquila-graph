# Retained AST value format

`java-source/2` is the supported binary retained source format. It stores the entire
admitted [AstResult](../../src/main/java/org/aquilagraph/source/api/AstResult.java),
including captured values, canonical declarations, the source aggregate, all body
registrations and independently retained fact families, coverage expectations and
outcomes, and ordered diagnostic events. Its executable entry point is
[AstCodec](../../src/main/java/org/aquilagraph/source/admission/AstCodec.java).

## Envelope and primitive fields

The stream begins with the framed `java-source/2` tag, followed by the exact AST
profile version, semantic production revision, source completion tag, full
`AstIdentity`, and complete `SyntaxAnalysisInput` values. Each string and scalar
uses the [canonical primitive format](captured-value-encoding.md#primitive-representation).
Storage preserves the captured absolute input root and dependency locations.

Tagged primitive values are `text`, `flag`, `int8`, `int16`, `int32`, `int64`,
`char16`, `float32`, `float64`, `decimal`, and `path`. Narrow integral tags reject
out-of-range values. Decimal values preserve their textual value and scale; binary
floating-point values preserve raw bits. Paths retain their captured path text.
`optional` carries a presence flag and exactly one tagged value when present.
`list` and `set` carry a count and that many tagged values. Lists retain every
position, including equal repeated values. Sets sort by unsigned lexicographic
comparison of each independently framed encoded value; duplicate stored entries
reject decoding. Collection counts must fit the remaining payload before
allocation. Unknown tags, malformed counts, unfinished references, and trailing
bytes reject decoding.

## Closed component schemas

[AstValueSchema](../../src/main/java/org/aquilagraph/source/admission/AstValueSchema.java)
declares each permitted record or value class by a compiled class literal, its
stable tag, and its exact ordered public component names. Each ordinary value
carries its tag, field count, and ordered pairs of field name and tagged value.
Every record's declared fields must match that schema exactly. Enum values carry
the registered enum tag and a closed constant name. The decoder never resolves a
class name supplied by stored data or accesses a private constructor or field.
Reflection invokes only the explicitly registered public getters and constructors;
the original constructor cause is retained.

Invocation facts carry `invocation-evidence` with one `applicability` component.
Its `EvidenceValue` preserves the independent state, known payload when present,
exact method and occurrence origin, optional source range, and explanation. Missing
or erroneous applicability does not remove the invocation's separately retained
target or source types. This changes the body fact schema from `java-source/1`;
that older version is unsupported and must not be decoded under version 2.

`child-use-evidence` retains the required method, consuming occurrence, direct child
role and position, source type, and selected target separately from its `conversion`
and `referenceCheck` evidence values. Each classification has an exact role-local
slot origin. Missing or erroneous conversion classification preserves both required
types. The known `conversion-evidence` payload contains the selected conversion type
fields; reference-check classification is exclusively the independent Boolean slot.
No Source fact type selects the uses required by a CFG profile.

The known invocation payload is `invocation-applicability`, with the closed
`invocation-applicability-mode` enum. `static-member-evidence` retains the selected
member, source access kind, declaring type, and an independent `constantVariable`
`EvidenceValue<Boolean>`. Known false is retained as a value. Missing or erroneous
classification retains the member and owner; writes and method invocations use
`NOT_APPLICABLE` because they do not read a constant variable. Initialization
effects are selected exclusively by CFG.

`string-expression-evidence` carries its result type and three independent
`EvidenceValue<Boolean>` fields: `leftIsString`, `rightIsString`, and
`compileTimeConstant`. Each field has its own exact slot origin and state. The
source format contains no runtime conversion-side choice; `StringOperandPlan`
derives that choice privately in CFG after admission of the required facts.

`constructor-evidence` retains the containing method and declaration, selected
constructor key and source delegation form separately from its `corroboration`
value. The known `constructor-context` payload contains actual ordered public
superclass parameter types and optional `compiler-inserted-invocation` provenance.
That insertion payload contains the captured source key and compiler address,
selected constructor reference, public invocation semantics, and an independent
`InvocationEvidence` applicability slot. Missing or
erroneous corroboration preserves the required selection. Incoming runtime enum
bindings and their supported shape and cardinality belong to private CFG plans.
Public compiler applicability that cannot be established for an inserted enum
superclass call remains `MISSING`; the private enum binding plan supplies its
runtime adaptation. The frontend does not label that adaptation as compiler
applicability.

A written constructor retains the identical body occurrence owned by the source
declaration. An inserted superclass call is anchored by constructor evidence at
that body and retains its actual attributed compiler address. CFG operations
created from the insertion carry a generated role and the exact body association,
with no written occurrence or fabricated span.

`method-body-origin` distinguishes `SOURCE_AST`, `COMPILER_ATTRIBUTED_BODY`, and
`UNAVAILABLE_SOURCE_POSITION`. The compiler-attributed variant retains actual
copied trees and fact provenance for nonwritten declarations. Its `BodyRef` uses
nonwritten provenance instead of claiming a written source occurrence. Written
constructors use `SOURCE_AST`; the format does not retain a second execution
projection for those bodies.

Each body registration also carries ordered `attributedOccurrences`. Every
`attributed-body-occurrence` stores its method key, retained node key, captured
source key, exact source kind, and actual attributed `compilerPath`. The frontend
copies those addresses from independently correlated live body projections before
closing the compiler. Admission requires unique actual body occurrences and
matching ownership and kinds. These addresses describe compiler attribution;
they carry no graph operation, binding, or evaluation policy. The explicit legacy
graph view uses the copied addresses with the unchanged occurrence/key formulas.

Special tagged values have the following complete field order, implemented in
[RetainedValueCodec](../../src/main/java/org/aquilagraph/source/admission/RetainedValueCodec.java):

| Tag | Ordered contents |
| --- | --- |
| `source-node` | Contiguous encounter ordinal; nine counted values: source key, structural path, exact source kind, syntax kind, type, syntax value, children, semantic reference, direct origin. |
| `source-node-reference` | An earlier completely decoded occurrence ordinal. Equal-looking auxiliary and written occurrences are not merged. |
| `source-aggregate` | Complete `CopiedAstValues`: captured input, per-source contributions, body registrations and facts, member orders, written associations, and declaration types. |
| `source-coverage-contract` | Release and complete ordered classifications. |
| `source-coverage-result` | Coverage contract and complete independent validation evidence. |
| `syntax-value/annotation` | Seven counted values: form, argument headers, location, applicable targets, closest-type admissibility, argument failure code, repeatable container key. |

Occurrence references preserve the exact object sharing required by aggregate
admission. Body references to source occurrences resolve to that same decoded
owner; auxiliary occurrence sharing remains distinct. Aggregate decoding goes
through copied-value admission. Coverage decoding re-establishes its independently
captured expectations, support, unmatched facts and ordered creating-site event
correlations. Declaration, syntax, type, annotation, default, negative evidence,
and diagnostic constructors retain their intrinsic validation.

## Qualification and canonical content

Canonical content begins with `canonical-java-source/1`, then the format, profile,
semantic revision, full `captured-values/1` bytes and complete tagged source input.
Canonical input omits only every `InputSnapshot.sourceRoot` and each separate
`DependencySnapshot.location`. Set ordering and structural child role/position
ordering, including repeated coverage projections of those children, normalize
unordered inputs. Body fact families follow canonical role/position preorder.
Meaningful list positions and diagnostic event order remain encoded.
Storage still preserves the omitted provenance and physical child list order.
The `ast-content` digest qualifies the result using the unchanged `AstIdentity`
tuple algorithm. Source completion is independently computed from admitted facts.

Supported present decoding re-admits the complete source input, derives its source
completion and canonical identity again, and compares both against the envelope
and exact requested identity. A stored label cannot certify completion. Absence
and unknown format handling belong to the repository lookup operation and precede
supported present decoding. The codec requires no producer, compiler, or CFG.

## References

- [Source identity](source-snapshots.md#target-qualified-analysis-identity), canonical captured values and qualification.
- [Canonical body evidence controls](../../src/test/java/org/aquilagraph/source/admission/CanonicalBodyEvidenceTest.java), consistent copied cross-role permutations, qualified identity equivalence, physical storage order, and rejection of changed attachment roles, positions, or identities.
- [Source profile](java-analysis.md#initial-source-profile), required versus optional source evidence.
- [AST retention schema](../repository/neo4j-schema.md#target-ast-retention-schema), independent storage scope and full-value requirement.
- [Atomic result admission](../../src/main/java/org/aquilagraph/source/admission/AstResultAdmission.java), content identity and source qualification.
- [Production and codec controls](../../src/test/java/org/aquilagraph/frontend/AstProductionTest.java), actual public-compiler and decoder observations when executed.
