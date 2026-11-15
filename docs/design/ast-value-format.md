# Retained AST value format

`java-source/23` is the sole format emitted and admitted by the current retained
source codec. It stores the entire admitted
[AstResult](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/api/AstResult.java),
including captured values, canonical declarations, the source aggregate, all body
registrations and independently retained fact families, coverage expectations and
outcomes, and ordered diagnostic events. The source module exports
[AstCodec](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/retention/AstCodec.java)
to the repository module for encoding, materialization, and intrinsic readmission.
The codec represents available `COMPLETE` and `INCOMPLETE` results.
The application admits only `COMPLETE` ASTs for independent retention.

## Format, source profile, and producer revision

The source format defines the retained representation. The nine current
[source profiles](java-analysis.md#supported-source-profiles) are
`java-source-bounded/1` and `java-source-expanded/1` through `java-source-expanded/8`.
Each keeps its source capability and qualification policy, including its
compilation-unit range requirements. Source admission applies the selected
profile's current rules. CFG qualification has its own profile and graph model.

The built-in frontend supplies `javac-public/14`. Source admission retains the
supplied nonblank semantic-production revision as provenance and identity input.
The field preserves the revision independently of the profile and of the
format. [AstProfile](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/api/AstProfile.java),
[AstProductionAdmission](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/admission/AstProductionAdmission.java),
and [JavaTranslationPolicy](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaTranslationPolicy.java)
own these boundaries.

## Envelope and primitive fields

The stream begins with the framed `java-source/23` tag, followed in order by the
AST profile version, semantic-production revision, source completion tag, and
the tagged `AstIdentity` and `SyntaxAnalysisInput` values. Each string and scalar
uses the [canonical primitive format](captured-value-encoding.md#primitive-representation).

Tagged primitive values are `text`, `flag`, `int8`, `int16`, `int32`, `int64`,
`char16`, `float32`, `float64`, and `decimal`. The `int8`, `int16`, and `char16`
tags read a 32-bit value and check the tagged range. Decimal values retain their
scale through the string representation. Binary floating-point values retain
raw bits. `optional/present` carries one tagged value and `optional/absent`
carries none. `list` and `set` carry a count and that many tagged values. Lists
retain every position, including equal repeated values. The writer orders sets
by unsigned lexicographic comparison of independently framed encoded elements.
The reader rejects duplicate set elements, malformed counts, unknown tags,
unfinished references, and trailing bytes.

### Captured locations

The `captured-location` shape records the ordered components `syntax`, `rootKind`,
`rootComponents`, `components`, and `spelling`. Its decoder parses the spelling
using the supplied syntax and checks every other component and the normalized
spelling for agreement. The payload retains captured source roots and relative
source names, configured class/module paths, and separate dependency locations.
Equal-looking POSIX and Windows locations keep distinct syntax. The
[captured-location contract](source-snapshots.md#captured-locations) owns value
equality, containment, normalization, and the boundary for native I/O.

## Closed component schemas

[AstValueSchema](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/internal/AstValueSchema.java)
owns closed tags, component names, types and order, canonical omission and
ordering rules, and reconstruction. The
[binary codec](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/internal/RetainedValueCodec.java)
and [value projector](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/internal/AstValueProjector.java)
apply the same shapes to their respective stream and graph operations. Ordinary
composite values carry their tag, a field count, and ordered pairs of field name
and tagged value. Enum values carry the registered enum tag and a closed constant
name. Reconstruction selects only registered types, public constructors or
explicit factory paths. When a reflected constructor fails, the original cause
is retained.

### Executable evidence

`scopeKey` identifies the owning execution scope. Depending on the fact, the
owner may be a method, field initializer, enum constant, or initializer block.
The `evidence-origin` shape retains `scopeKey`, `nodeKey`, `slot`, and optional
`sourceRange`, in that order. This identifies the precise occurrence and evidence
slot within the scope.

The `evidence-value` shape retains `state`, `knownValue`, `origin`,
`explanation`, and `diagnostic`, in that order. Only `KNOWN` carries a value.
Other states require a nonblank explanation. An optional diagnostic records an
originating frontend error for `ERRONEOUS` evidence.

| Tag | Ordered components |
| --- | --- |
| `invocation-evidence` | `applicability` |
| `invocation-applicability` | `scopeKey`, `consumerNodeId`, `mode`, `formalCount`, `varargArrayType`, `erasedArrayType`, `elementType` |
| `child-use-evidence` | `scopeKey`, `consumerNodeId`, `role`, `position`, `sourceType`, `selectedTarget`, `conversion`, `referenceCheck` |
| `static-member-evidence` | `scopeKey`, `accessNodeId`, `selectedMemberKey`, `kind`, `declaringOwner`, `constantVariable` |

The `applicability` slot is an `EvidenceValue<InvocationApplicability>` with a
closed `invocation-applicability-mode` value in its known payload. Missing or
erroneous applicability preserves the separately retained target and source
types. Child use retains both required types independently of its role-local
`EvidenceValue<ConversionEvidence>` and `EvidenceValue<Boolean>` slots for
conversion and reference checking. CFG selects the evidence required for its
profile.

Static access retains the selected member independently of
`declaringOwner`, an `EvidenceValue<JavaTypeReference>` with its own origin
and availability. `constantVariable` is a separate `EvidenceValue<Boolean>`.
A known false value remains present. Constant-variable classification applies
to field reads. Writes and method invocations carry `NOT_APPLICABLE` in that
slot. CFG selects initialization effects.

`string-expression-evidence` carries its execution scope, binary occurrence,
result type, and three independent `EvidenceValue<Boolean>` fields
`leftIsString`, `rightIsString`, and `compileTimeConstant`. Each field has its
own exact slot origin and state. CFG's `StringOperandPlan` derives the runtime
conversion side after admission of the required facts.

`constructor-evidence` retains the scope, invocation occurrence, owner type,
selected constructor key, source delegation form, and `corroboration` slot.
Its known `constructor-context` payload contains the ordered public superclass
parameter types and optional `compiler-inserted-invocation` provenance.
That insertion payload contains the captured source key and compiler address,
selected constructor reference, public invocation semantics, and an independent
`InvocationEvidence` applicability slot. Missing or
erroneous corroboration preserves the required selection. Incoming runtime enum
bindings and their supported shape and cardinality belong to private CFG plans.
Public compiler applicability that cannot be established for an inserted enum
superclass call remains `MISSING`; the private enum binding plan supplies its
runtime adaptation. The frontend does not label that adaptation as compiler
applicability.

An admitted inserted superclass call retains its actual attributed compiler
address in the constructor evidence. CFG operations created from the insertion
carry a generated role and the exact body association.

### Method body origin

Body registrations record a [`MethodBodyOrigin`](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/api/MethodBodyOrigin.java).
Available source can retain these registrations even when some bodies are
withheld from the written tree. The [producer](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaBodySourceCorrelation.java)
and [ownership validation](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/internal/ExecutionOwnership.java)
distinguish the three variants. Reference queries use the canonical declaration
key and its body registration.

| Origin | Body and reference |
| --- | --- |
| `SOURCE_AST` | A published written body, including a published constructor body, shares the identical body occurrence owned by its source declaration. Its `BodyRef` names that occurrence. |
| `COMPILER_ATTRIBUTED_BODY` | A compiler-attributed body without a written body occurrence retains separate copied execution facts. Its `BodyRef` uses nonwritten provenance. |
| `UNAVAILABLE_SOURCE_POSITION` | A written body withheld because a required source position is unavailable can retain a separate execution registration with this origin. The written block and `BodyRef` are absent. |

[`AdmittedAstResult.bodyRef`](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/internal/AdmittedAstResult.java)
exposes the applicable reference. One withheld-body case is a written
constructor whose explicit `super()` call lacks a required source position.
The [source-position control](../../src/test/java/org/aquilagraph/frontend/internal/JavaAstSourcePositionTest.java)
covers its registration and diagnostic.

Body and declaration-execution registrations carry ordered
`attributedOccurrences`. Every
`attributed-body-occurrence` stores its scope key, retained node key, captured
source key, exact source kind, and actual attributed `compilerPath`. The frontend
copies those addresses from independently correlated live executable projections
before closing the compiler. Admission requires unique actual occurrences and
matching ownership and kinds. The addresses describe compiler attribution.

The following special shapes illustrate the handling of occurrence slots and
factory-based reconstruction. The schema registry defines their exact names.

| Tag | Ordered contents |
| --- | --- |
| `source-node` | Contiguous encounter ordinal; nine counted values: source key, structural path, exact source kind, syntax kind, type, syntax value, children, semantic reference, direct origin. |
| `source-node-reference` | An earlier completely decoded occurrence ordinal. Equal-looking auxiliary and written occurrences are not merged. |
| `source-aggregate` | One `copiedValues` component containing the captured input, per-source contributions, body registrations and facts, member orders, written associations, declaration types, and declaration execution scopes. |
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

[CanonicalAstEncoding](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/internal/CanonicalAstEncoding.java)
frames `canonical-java-source/1`, the format, profile and semantic revision, then
writes the tagged source input in canonical mode. The independent
[`captured-values/3` frame](captured-value-encoding.md) is an Identity API.
The AST digest uses the registered tagged shapes directly.

Canonical input omits each `InputSnapshot.sourceRoot` and each separate
`DependencySnapshot.location`. Configured class/module locations and path-bearing
options remain identity inputs. Canonical encoding sorts syntax and coverage
children by role and position where the schema designates those lists as
unordered. It preserves meaningful list positions, occurrence sharing, and
diagnostic event order. The storage form retains the omitted provenance and
physical child lists. The resulting `ast-content` digest is one component of
the [qualified AST identity](source-snapshots.md#target-qualified-analysis-identity),
along with the software signature, source-model format, source profile, and
semantic-production revision.

## Read admission and retired formats

The codec rejects a requested format other than `java-source/23` before opening
the payload. For the active format it checks the frame and exact requested
identity, reads the typed values, and re-admits them. The derived identity and
completion must match the envelope. Readmission works after compiler closure and
requires only the retained source and Identity contracts.

Repository lookup classifies a present root with a nonblank unsupported format
as `INCOMPATIBLE` before reading payload or materialization. This includes the
retired `java-source/2` through `java-source/22` formats. A missing root with no
scoped rows yields `NOT_FOUND`. Read, decode, or owned-close failure fails the
query. The [repository lookup contract](analysis-repository.md#target-ast-retention)
owns the profile checks and error precedence. Retired data keeps its stored
identity and bytes. Producing a current result is a separate analysis under a
current qualified identity.

## References

- [Source identity](source-snapshots.md#target-qualified-analysis-identity), canonical captured values and qualification.
- [Canonical body evidence controls](../../src/test/java/org/aquilagraph/source/internal/CanonicalBodyEvidenceTest.java), consistent copied cross-role permutations, qualified identity equivalence, physical storage order, and rejection of changed attachment roles, positions, or identities.
- [Source profile](java-analysis.md#initial-source-profile), required versus optional source evidence.
- [AST retention schema](../repository/neo4j-schema.md#target-ast-retention-schema), independent storage scope and full-value requirement.
- [Atomic result admission](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/internal/AstResultAdmission.java), content identity and source qualification.
- [Codec controls](../../src/test/java/org/aquilagraph/source/internal/AstCodecContractTest.java) and [retired-format controls](../../src/test/java/org/aquilagraph/repository/internal/neo4j/HistoricalAstCompatibilityTest.java), executable verification entry points.
