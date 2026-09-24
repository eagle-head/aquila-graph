# Java Analysis Design

## Implemented Boundary

Aquila Graph analyzes a recursively discovered Java source root for a selected,
supported Java language release without executing the target and without
emitting target bytecode. `JavaRelease` owns the set of language releases whose
semantics Aquila Graph currently supports; that set contains Java 25.
`CompilerConfiguration` carries this typed release with the class path, module
path, and caller options. `JavaSourceLoader`
reads each source once as UTF-8 and captures its content in the input snapshot.
`JavaFrontend` is the stable public facade: it delegates source discovery to the
loader and snapshot analysis to `JavaAnalysisPipeline`. The pipeline coordinates
one run without owning compiler resources. `CompilerSession` owns exactly one
standard file manager and `JavacTask`, performs `parse()` and `analyze()`, and
closes the file manager before environment verification and result publication.
Before a compiler session starts, `JavaCompilerReleaseSupport` verifies that the
active system compiler advertises the selected release. The composition root
injects Java 25 as the CLI default rather than embedding a release constant in
the analysis pipeline.[1][2][15][17][19]

Inside that session, `JavaCompilerInvocation` derives managed compiler options,
and `SnapshotSourceCatalog` is the single correlation point between captured
`SourceFile` values, in-memory `JavaFileObject` values, compiler URIs, and source
ownership. `CompilerDiagnosticTranslator` converts compiler diagnostics;
`JavaProgramTranslator`, `JavaTypeTranslator`, and `JavaMethodTranslator` own
their respective declaration levels. `JavaTypeTranslator` classifies every
compiler-reported class-body member: it translates fields, enum constants,
methods, constructors, and nested types; treats empty declarations as inert;
and emits a source-located error for initializers or any other unsupported
member. It also projects compiler-derived type modifiers, exact nested-member
ownership, an optional direct superclass, ordered direct interfaces, ordered
permitted subclasses, and ordered type parameters with their compiler-resolved
bounds. `JavaTypeReference` retains the compiler-derived identity and the
canonical declaration key when one is available, including for declarations
outside the analyzed source root. Type, type-parameter, and declaration-header
type-use annotations are outside the current bounded profile; each occurrence
therefore produces a deterministic, source-located unsupported diagnostic
instead of being discarded. `DeclarationIdentityRegistry` assigns canonical keys
to declarations, including owner-and-ordinal type-parameter keys, while compiler
elements remain available. `JavaMethodBodyTranslator` recursively
converts one method body into project-owned `SyntaxNode` values; and
`MethodBodySyntaxProfile` maps each compiler-tree form through visitor dispatch
to a typed `SyntaxKind`, typed `SyntaxValue`, role-bearing `SyntaxChild` values,
stable structural paths, and contextual value or assignment-location use.
Unsupported forms are represented by their node kind rather than parallel
acceptance state. `SemanticResolver` projects compiler elements, independent
expression types, declaration references, type hierarchies, and invocation
qualifying types and modes, while
`JavaSourceEvidence` independently projects
source positions. Translation results carry diagnostics and contextual
declaration-property observations explicitly, and recursive body assembly uses
immutable mappings without retaining per-method state.
`JavaDeclarationPropertyObserver` creates each property diagnostic together with
the exact source-owned accessor occurrences represented by that diagnostic.
`JavaProgram`, `JavaType`, and `JavaMethod` retain declaration facts only,
including exact type ownership and whether a method body exists;
`JavaAstContributionTranslator` assembles one source-rooted contribution per
captured compilation unit while attribution is available. Each root retains its
ordinary, compact, or modular form from attributed compiler-tree structure and
source positions. Ordinary roots own a written package declaration when present;
all three forms own ordered import occurrences. Compact roots own written class
members directly, without presenting the implicitly declared wrapper class as a
written type. A compact root spans the complete captured source because javac
does not provide an end position for its implicit class or compilation unit.
Package declarations own ordered annotation attachments. Package and import
names come from compiler-tree name components, while import values separately
retain static, module, and on-demand form. Annotation value structure remains
outside this increment and continues to produce its own capability diagnostic.
Modular roots own exactly one written module declaration with its qualified name,
open state, annotation attachments, and ordered directive occurrences. The five
directive values retain qualified operands, `requires` flags, optional ordered
`exports` and `opens` targets, and ordered `provides` implementations. Written
module and directive ranges are required; malformed or unavailable facts emit
source-located diagnostics and prevent a complete source aggregate.[2][25]
Written top-level and member type occurrences retain one of the five exact
declaration kinds, a simple name, separate source-written and attributed modifier
sets, and ordered annotation, type-parameter, superclass, interface, permitted
subclass, and body-member attachment roles. Each attachment has its own source
range and structural owner. Type parameters are named
`TYPE_PARAMETER_DECLARATION` occurrences with owner-and-ordinal identities,
written annotations, and ordered bound attachments. Header type uses are
`TYPE_USE` occurrences with their exact compiler-tree source kinds; their
recursive content belongs to AST-E04. Static and
instance initializer blocks are distinct `STATIC_INITIALIZER_BLOCK` and
`INSTANCE_INITIALIZER_BLOCK` member occurrences with written source ranges; their statement content
remains diagnosed until initialization ownership is completed. Empty class-body
semicolons are semantically inert. The frontend
uses the pre-attribution written-tree inventory and the attributed declaration
origin together so compiler-inserted constructors and members do not become
source children. A record component that javac also exposes as a field-shaped
`ClassTree.getMembers()` tree belongs to the record header, so it is not a body
member child. Each written component is a `RECORD_COMPONENT_DECLARATION` child
with an owner-and-ordinal key, ordered annotation and type attachments, attributed
type, varargs state, and source range. `JavaType.recordComponents` retains the
same declaration identities with explicit accessor, backing-field, and canonical
parameter correlations. Ordinary fields and enum constants have separate typed
source kinds and retain their written modifiers, declared type, initializer
presence, and enum argument-list, argument-count, or constant-body presence.
An explicit empty enum argument list remains distinct from an omitted list. Full initializer and
annotation content remains at its later feature boundary. The direct
`TypeElement.getEnclosedElements()` inventory is reconciled with detailed member
aggregates; remaining compiler-exposed declarations become stable-keyed
`JavaSynthesizedDeclaration` facts outside source AST edges. Member edges and source-body registrations are published
only after the complete enclosing type is retained. A missing required header
range or a mismatch between a written type and its attributed declaration
withholds that type and emits a blocking frontend error. The canonical `JavaType`
declaration and its hierarchy references remain separate from these source
occurrences; this increment changes no Neo4j AST projection.[2][28][29]
Direct compiler member keys must equal the union of detailed member keys and
typed synthesized keys. An unmatched key emits a blocking frontend error;
synthesized declarations are ordered by canonical key because compiler order
for implicit members is unspecified. A record source contribution requires its
correlated semantic component inventory, including when translated directly.
An unmatched `EXPLICIT` non-method member blocks publication even when its
source tree is unavailable. Method provenance uses the pre-attribution
written-tree inventory: each written method must resolve to a direct compiler
element and canonical key. An unmatched compiler method reported `EXPLICIT`
with no source tree enters the synthesized inventory only when that
written-method inventory is complete and its key is absent from it.
Incomplete correlation emits a blocking diagnostic. This source-first rule
also handles javac's `EXPLICIT` origin for implicit enum and record methods.[30]
Duplicate translated type or member keys block the aggregate and retain compiler
errors as diagnostics. Enum delimiter evidence follows Java's Unicode-escape
eligibility before scanning comments and punctuation.[30]
`JavaMethod` and `JavaParameter` retain written provenance only when a pre-attribution
tree and an explicit compiler declaration agree; implicit canonical formals
retain stable keys without source origins or source AST children.[2][29]
`MethodSyntax` derives CFG readiness from an explicit set of method-body syntax
categories. A source-only occurrence cannot become lowerable merely because it
has a typed AST category; unresolved or unsupported body content remains blocked.
`JavaProgramTranslator`
seals those contributions in snapshot order as one `JavaAstAggregate`.
`ParsedTreeInventory` captures tree identity immediately after parse and before
attribution. The body and contribution translators use that identity to
distinguish written constructor calls from compiler-inserted calls independently
of source positions. Missing positions on written calls therefore retain their
blocking diagnostics. Diagnostic identity for omitted declarations uses the
original sibling ordinal, while published child positions remain contiguous.
Position diagnostics include the captured relative source path and the typed
occurrence path even when no source range can be attached.
`SyntaxAnalysisInput` owns that aggregate and carries the per-input, release-bound
`LanguageFeatureCoverageResult` across the frontend-to-core boundary. Its
`MethodSyntax` accessors derive views from aggregate-owned method bodies; no
independent method tree is retained. Written source occurrences have validated
source ranges and belong to the ordered source AST. Compiler-inserted default
constructors and implicit `super()` calls are excluded from that source graph.
Their core-facing body trees remain aggregate-owned execution projections when
needed to preserve the existing CFG input. A body identical to its source AST
occurrence is reused by identity. Missing or invalid positions on required
written occurrences emit blocking, specific frontend diagnostics. If a required
compilation-unit root cannot be published, the input retains diagnostics but
exposes neither a partial aggregate nor program declarations.
All compiler-owned objects remain
package-internal and are translated into project-owned records before the facade
returns. Target source files are not reopened after snapshot discovery. Source
access and UTF-8 decoding failures remain checked
`IOException` values, with source-path context and the original cause preserved,
through the frontend and application service.
[1][2][16][22]

`JavaSourceLoader` orders normalized relative paths, hashes each captured UTF-8
source with SHA-256, and derives a length-framed SHA-256 snapshot identity from
ordered path/digest pairs. The captured `SourceFile.content` is therefore the
authoritative compiler input and persisted source evidence for that run.[3][4]

Before compiler analysis, `JavaAnalysisPipeline` captures an immutable
`AnalysisEnvironmentSnapshot` through `AnalysisEnvironmentCapture`. It records
the compiler implementation class,
JVM vendor and runtime version, location-independent analysis options, and the
ordered class-path and module-path dependencies. `CompilerDependencyCapture`
owns dependency enumeration and content identity. Regular files are fingerprinted
from their bytes; directories are fingerprinted from ordered relative entry
names, file sizes, and file bytes; a missing configured entry has an explicit
fingerprint. `AnalysisEnvironmentCapture` verifies the complete environment again
after the compiler session closes and rejects the result if any captured value
changed in the interval. The frontend always passes an explicit class path,
including the empty class path, so compiler analysis cannot inherit the analyzer
process class path implicitly.[2][14]

The analysis-run key is derived from the captured software target's source and
environment signatures, not from an analyzer or stored-model version. The
environment digest includes compiler identity,
analysis options, dependency roles, order, kinds, and content fingerprints, but
excludes dependency locations. Locations remain provenance metadata. Options
that could add an uncaptured compiler input location are reserved to Aquila
Graph and must instead use the captured configuration fields.[8][14][15]

The analyzer implementation revision is managed in Git and its published version
belongs to the build artifact. Neither is embedded in `AnalysisRun` or its key.
Reanalyzing the same captured source and environment with a different analyzer
build produces the same run key. Publishing that completed run replaces the
current projection at that key, including any changed derived CFG identities;
it does not retain parallel projections by analyzer build. Projections already
stored under the former version-bearing run-key scheme have different keys and
are not removed or migrated automatically.[5][8]

## Release-Bound Language Coverage Contract

`LanguageFeatureCoverage` is the compiler-neutral contract for classifying Java
language coverage. Its version is the typed `JavaRelease` selected by
`CompilerConfiguration`. `LanguageFeatureCoverageFactory` obtains that release from
the same configuration instance that `JavaCompilerInvocation` uses to derive the
compiler's `--release` option; the coverage contract does not probe the runtime or
carry an independent release literal.[2][15][23]

Each exact `LanguageFeature` belongs to the compiler-tree-kind, compiler-type-kind,
or declaration-property category and has one `LanguageFeaturePolicy`:

- `REPRESENTED` names the project-owned aggregate boundary that retains the fact;
- `IRRELEVANT` carries a nonblank semantic-scope rationale and direct source
  locator; or
- `DIAGNOSED` carries one stable diagnostic code and is completion-blocking when
  the feature is encountered.

Classification construction rejects missing or conflicting policy metadata. A
coverage contract rejects duplicate features before sorting classifications into
deterministic order, owns an immutable copy, and resolves features only by exact
identity. It has no wildcard, default, or unknown fallback lookup.[23]

`JavaCompilerKindCoverage` now owns the Java 25 compiler-kind catalog behind the
frontend boundary. Its declarations contain one exact compiler-neutral feature
identity and one policy for every `Tree.Kind`. Construction resolves every declared
identity against the running Java 25 enum and compares the resulting set in both
directions with `EnumSet.allOf(Tree.Kind.class)`. Missing, duplicate, unrecognized,
catch-all, or policyless entries are rejected before the catalog can be used. The
catalog has no wildcard, default, or future-kind fallback. `REPRESENTED` entries name
the current `JavaProgram`, `JavaAstAggregate`, or aggregate-derived `MethodSyntax`
boundary retaining the fact;
`DIAGNOSED` entries carry deterministic kind-specific diagnostic codes for later
encounter accounting; and the implementation-reserved `OTHER` kind is the sole
`IRRELEVANT` entry, with an official API locator and semantic-scope rationale.[23][24]
These policies classify the compiler-kind identity itself. They do not claim that
every accessor or source form sharing that kind is already modeled: existing
occurrence-specific diagnostics still reject unsupported forms, while the separate
declaration-property catalog owns accessor completeness.[23][24]

`JavaTypeKindCoverage` owns a separate Java 25 catalog for every attributed
`TypeKind`. Its exact identifiers are checked against the configured JDK enum in
both directions; duplicate, missing, unknown, catch-all, and policyless entries
are rejected. Primitive, void, null, declared, array, type-variable, wildcard,
intersection, union, and executable outcomes are represented as exact
source-owned kind observations. `ERROR`, `NONE`, `PACKAGE`, `MODULE`, and `OTHER`
retain distinct completion-blocking policies when encountered as required
declaration-header types. `ERROR` retains an erroneous-compiler-state outcome;
the other four use unsupported-type outcomes. `SemanticResolver` returns a closed
`JavaTypeAttribution` state: resolved kind, no-type kind, erroneous,
unsupported, or unavailable. A no-type or reserved kind cannot construct a
resolved state or a successful `JavaTypeIdentity` fallback.
`JavaTypeKindObserver` queries attribution for written declaration-header type
trees after `analyze()`, records
the exact kind and source span in the same occurrence/observation manifest, and
emits an error with the nearest available source span when required attribution is absent. Source-tree
kind and attributed type kind remain separate feature categories. The current
header type-use nodes remain leaf boundaries, and valid E04 tree forms still
retain their existing kind diagnostics until recursive translation is implemented.
The catalog does not claim recursive type-use completeness.[23][26][31]

`JavaDeclarationPropertyCoverage` owns the Java 25 declaration-property catalog.
Its exact identities include the declaring interface and raw parameter types for
every non-static public accessor inherited or declared by `CompilationUnitTree`,
`ClassTree`, `MethodTree`, `VariableTree`, `TypeElement`, and
`ExecutableElement`. Construction derives that accessor universe from the configured
JDK 25 APIs and compares it in both directions with the explicit catalog, so missing,
duplicate, unrecognized, extra, catch-all, and policyless entries cannot silently
enter the contract. Inherited visitor, element-identity, annotation-proxy lookup,
and line-map protocols are explicitly irrelevant with direct API locators. Package
and import declarations are represented as source-owned AST occurrences, including
the package name, annotation attachment order, import order, imported name, and
source form. Modular roots and their directive children represent
`CompilationUnitTree.getModule()`; annotation value content retains its separate
diagnosed policy.[23][24][25]

The declaration-property catalog represents method type parameters, written and attributed thrown types,
receiver syntax, attributed receiver types, and variable arity in source-owned
executable headers. Formal parameters carry compiler-correlated declaration keys
and written versus compiler-derived provenance;
explicit receivers carry an executable owner key because they do not declare
variables. The attributed receiver-type outcome remains separate from optional
written receiver syntax. A resolved receiver identifies its declaring type for an
instance method or its immediately enclosing type for an inner constructor.
Ordered attributed thrown types correspond one-to-one with written `throws`
attachments; an unavailable attribution prevents publication of the declaration.
Unrepresented annotation content and defaults remain connected to source-located
frontend errors. Those errors flow through the existing completion
projection and therefore prevent `COMPLETED` without using CFG construction as the
declaration-coverage oracle. Written receiver names and qualifiers are distinct from
formal parameters. Local declaration modifiers and annotation content outside
their later feature boundary also produce source-located capability errors. The
catalog classifies `VariableTree.getModifiers()` and
`VariableTree.getInitializer()` as represented for written declaration
attachments and initializer presence. Parameter annotation content, local
declaration modifiers, and initializer expression content retain specific
diagnostics until their later owners represent them. Written and attributed
executable modifiers remain distinct in the source AST; attributed modifiers, including
implicit interface method modifiers, are also retained in `JavaMethod`. Compiler errors
retain their distinct `compiler.err.*` codes.[2][23][25][29]

The executable value boundary requires a return-type attachment for ordinary methods,
a body and no return type for constructors, and limits default-value presence to
annotation-interface elements. These kind-specific constraints apply even when an
AST value is constructed outside the frontend. Type owners accept compact constructors
only on records, annotation elements only on annotation interfaces, and type
parameters only on classes, interfaces, and records. Every explicit receiver owns a
written name-expression child, including unqualified `this`; missing evidence
withholds its executable declaration with a source-located error. Invalid compiler
headers are likewise diagnosed rather than published as impossible AST values.
Executable values carry the canonical type owner separately from the executable key;
the aggregate checks both owner identity and source form. Receiver values use the
written name `this`, with an unqualified name expression for methods and a
single-name `.this` expression for inner constructors.[29]

Formal declarations, explicit receivers, and written receiver names have distinct
source-only `SyntaxKind` categories: `PARAMETER_DECLARATION`, `RECEIVER_PARAMETER`,
and `RECEIVER_NAME`. Their exact `JavaSourceKind`, typed value, structural role,
and source range remain separate facts. `COMPILATION_UNIT` is reserved for unit,
package, import, annotation-attachment, module, and directive structure. These
header categories are outside the positive method-body lowering set. A
`RECEIVER_NAME` node itself requires either the identifier `this` or one
Java 25 type identifier followed by `.this`; its parent cannot repair an
invalid standalone name.[29]

Compiler-identified compact record constructors retain a distinct source executable form,
source body, and canonical key. Their implicitly declared semantic parameters remain in
`JavaMethod` but have no fabricated source `PARAMETER` children; record-component
declarations remain assigned to the record-header increment. Type declarations carry
their canonical key so their type-parameter children must match owner and ordinal.[28][29]

`JavaLanguageFeatureCoverage` validates all three closed ledgers and combines them into
one deterministic contract for the configured release. The contract identity hashes
the release, exact feature set, policies, and policy evidence; the per-input result
rejects any reduced, augmented, or reclassified substitute. `JavaCompilerKindDiagnostics`
then scans every parsed compilation unit in compiler traversal order before compiler
attribution can inject generated trees. Every source tree is
looked up by its exact kind identity; a missing classification fails the frontend
contract, a `DIAGNOSED` classification emits its catalog code with the available
source span, and `REPRESENTED` or `IRRELEVANT` classifications emit no kind error.
Each compiler-kind occurrence is recorded in a source-context manifest before its
observation retains the exact classification, source owner, available origins, typed
outcome, and inseparable catalog diagnostic when diagnosed. Declaration-property
occurrences and observations are emitted by the translator that encounters the
specific accessor context. One diagnostic may therefore own multiple exact accessor
occurrences, while variable annotations are accounted for by
`VariableTree.getModifiers()` rather than an unrelated element surface. The combined
contract and all diagnostics are returned in
`SyntaxAnalysisInput` before the existing completion projection runs. Construction
rejects any partial or two-sided removal because the complete ordered occurrence
manifest must equal the retained observation inventory. Construction also rejects a
coverage diagnostic omitted from either boundary value, an observation whose
classification differs from the active contract, an observation owned by a source
outside the input snapshot, a capability diagnostic without a contextual feature
observation, or a nonempty snapshot that lacks exactly one compilation-unit
observation per captured source file.[2][23][24][25][26][27]

The observable root causes remain distinct through `AnalysisOutcomeKind`.
Each producer assigns the outcome while it owns the relevant compiler or frontend
context; the model does not reconstruct the cause from a diagnostic-code prefix.
Compiler-reported invalid source retains its exact compiler code, including a stable
fallback when the compiler supplies no code; an `ERRONEOUS` compiler tree has the exact
`frontend.unsupported-tree-kind.erroneous` capability code; deliberate irrelevance
is an inspectable non-error policy with rationale and source locator; other valid but
unsupported kinds and declaration properties retain their catalog codes at the
`FRONTEND` stage; attributed error types and erroneous references retain the
`ERRONEOUS_COMPILER_STATE` outcome; and unavailable method lowering retains a separate
`control-flow.*` code at the `CONTROL_FLOW` stage. Feature observations also expose
represented and irrelevant outcomes without an error. These distinctions may all project
to `INCOMPLETE`, but no generic completion status replaces their typed diagnostic or
policy evidence.[8][23][24][25][26][27]

`COMPILATION_UNIT` is represented by one validated aggregate root for each
captured source, including an empty source. The root has an explicit ordinary,
compact, or modular form. Package and import accessors are represented by source-owned
occurrences and ordered structural edges. Modular roots own one typed module
declaration and its ordered typed directives. Package and module annotations
have source-owned attachment nodes,
while their type and value details retain the separate annotation capability
diagnostic. Coverage values and AST occurrences are not yet
persisted, compiler API types remain confined to `frontend`, and no Neo4j schema
change is required. The focused class, sequence, and component views show the
coverage scanner, contextual declaration-property observation, source aggregate
construction, and application analysis-run collaboration.[2][8][9][23][24][26]

## Accepted Source Profile

The first profile is deliberately smaller than Java 25. It accepts the
calculator and the following constructs when compiler analysis resolves their
required semantics:

- top-level and nested class, interface, enum, record, and annotation
  declarations;
- constructors and methods, including parameters and compiler-generated
  declarations when javac exposes them; abstract and native declarations have
  no body analysis;
- field and enum-constant declarations as durable program facts; their
  initializers and class or instance initializer blocks are reported explicitly
  because executable initializer lowering is not yet supported;
- blocks, local declarations, expression statements, `return`, explicit
  `throw`, `if`/`else`, and `try` with `catch` clauses;
- arrow-style switch expressions whose cases use literal, enum-constant, or
  `default` labels, together with their yielded expressions;
- method invocation, object construction, member selection, array length and
  array access, identifiers, literals, parentheses, casts, simple assignment,
  conditional expressions, `&&`, `||`, unary `+`, unary `-`, `!`, `~`, and the
  represented binary operators.

Unsupported method-body syntax is retained as `SyntaxKind.UNSUPPORTED`, emits a
frontend error, marks the containing body unsupported, and prevents a completed
run. A typed source-only occurrence in a method-body view also blocks CFG
readiness until that view has an explicit lowering contract.
Notably unsupported are loops, switch statements, labeled control, `break`,
`continue`, `assert`, synchronized statements, lambdas, method references,
compound assignment, increment/decrement, try-with-resources, `finally`, and
anonymous class bodies. An ordinary field whose initializer contains an
anonymous class reports
`frontend.unsupported-anonymous-class-field-initializer`; an enum
constant-specific class body reports `frontend.unsupported-enum-constant-body`.
Type declaration, type-parameter, and declaration-header type-use annotations
report `frontend.unsupported-type-declaration-annotation`,
`frontend.unsupported-type-parameter-annotation`, and
`frontend.unsupported-type-use-annotation`, respectively. Switch statement
groups, patterns, guards, `null`
labels, and constant-expression labels other than literals or enum constants
are also rejected as complete constructs. They remain outside the profile until
their full CFG semantics can be represented without discarding source
information. Unrestricted nested exception handling is likewise not part of
this profile.[2][5]

Declaration content outside this profile reports stable capability-specific frontend
errors for annotation values, defaults, local variable modifiers, and initializer
subtrees. `TypeElement.getRecordComponents()` and `getEnclosedElements()` now have
represented policies, backed by ordered components and reconciled direct member
inventory. The declaration-property regression suite verifies the remaining
diagnostics and represented policies. Package, import, module, type, executable,
type-parameter, field, enum-constant, and record-component headers have positive
aggregate tuple tests.
Type-use and annotation-value content retain their own diagnostic boundaries.[2][25][29]

## Graph-Native Semantic Lowering and Control Flow

The core is Java-specific: it consumes a declaration-only `JavaMethod` together
with an aggregate-derived `MethodSyntax` view, while remaining independent of
compiler-owned `javac` objects. The source AST is an in-memory aggregate at this
boundary, not the analysis IR or a member of `ProgramRepresentation`. `SyntaxNode` keeps
stable node IDs, zero-or-one direct source origin, typed structural values, and
zero-or-one semantic declaration reference only where symbol identity is
relevant. Independently, every value-bearing node records a `SyntaxType`
outcome: a resolved `JavaTypeIdentity` with category, display form, erasure, and
stable key; an unresolved outcome; or an erroneous compiler-type outcome. This
prevents expression typing from depending on the presence of an `Element` and
keeps unavailable semantics distinguishable from syntax that is outside the
supported profile.
Each child is labeled with a `SyntaxRole`, so lowerers request `CONDITION`,
`THEN_BRANCH`, `ELSE_BRANCH`, `INITIALIZER`, `OPERAND`, and other roles without
interpreting list positions or display text. Node construction validates the
value and type contracts, allowed roles, role-local positions, and required
cardinalities for its kind; it also enforces unique tree ownership. Physical
child-list order is therefore not an evaluation-order contract. A
`MethodSyntax` derives exactly one `SyntaxReadiness`: `LOWERABLE`,
`UNSUPPORTED`, `UNRESOLVED_SEMANTICS`, or `ERRONEOUS_SEMANTICS`.

`SyntaxValue.Invocation` carries the shared model-layer `InvocationForm` and
compiler-derived `InvocationSemantics`; missing semantics prevents lowerable
readiness. The resolver selects the qualifying class or interface from the
actual invocation form in accordance with JLS 13.1: an `Object` declaration is
qualified by `Object`; a simple name uses the innermost lexical type of which
the selected method is a member; a type name uses that named type; an expression
uses its erased compile-time type, with an intersection using the erasure of its
first bound; and `super` or qualified `super` uses its dedicated superclass or
interface rule. Mode is `STATIC` for a static compile-time declaration, `SUPER`
for either super form, `INTERFACE` when the resulting qualifying type is an
interface, and `VIRTUAL` otherwise. Constructor forms use `CONSTRUCTOR`. This
classification does not infer mode from the element that originally declared
an inherited method.[20][21]

`ControlFlowGraphBuilder` is a small public facade. It verifies the method/syntax
identity pair and requires `LOWERABLE` readiness, then delegates to
`SyntaxGraphLowerer`, which owns recursive syntax dispatch and delegates
syntax-family semantics to separate statement and expression lowerers.
`AssignmentLocationLowerer` separately evaluates variable, field, and array
storage locations without reading the value already stored there, while
`FieldReceiverLowerer` shares the receiver contract used by field reads and
field writes. These
lowerers branch on `SyntaxKind`, `SyntaxRole`, and
`SyntaxOperator`; `SyntaxValue` supplies declaration, symbol, literal, type,
typed switch-case form and labels, and unsupported-syntax data without parsing
a generic detail string. A rule case requires exactly one `BODY` and no
`STATEMENT` child; the statement-group representation remains structurally
validated even though the accepted frontend profile currently rejects it.
`ControlFlowGraphConstruction` owns graph identity, mutable graph state, fragment
composition, nested exception targets, and final immutable assembly.
`FlowValueScope` separately owns lexical value visibility. Together these
collaborators visit operands in Java evaluation order and lower ready syntax directly
into graph-owned operation nodes, flow values, and typed edges. Resolved syntax
types propagate to `FlowValue` as `JavaTypeIdentity`; semantic references remain
responsible for symbol and exception-hierarchy evidence rather than serving as
an indirect type channel. Invocation lowering copies the mode, qualifying
`JavaTypeReference`, and receiver form into graph-owned `InvocationSemantics`.
`OperationNode` requires that value exactly for `INVOKE` and `NEW_OBJECT` and
forbids it for unrelated kinds. The selected compile-time target remains a
separate `SemanticReference`; no consumer parses `detail`, a name, or a key to
recover invocation semantics.
These elements preserve conditional evaluation of `&&`, `||`,
conditional expressions, branches, switch alternatives, returns, throws, and
exceptional flow.[5][6]

Exception targets form an outer-linked chain. A known thrown type transfers to
the first matching handler in source order; when the current `try` does not
match, resolution continues through enclosing targets before reaching method
exit. Unknown invocation and constructor exceptions conservatively retain all
reachable handler and exit alternatives. Every block and catch body uses a
lexical value scope that is restored after lowering. Known explicit throws use
the qualified type names and transitive assignability hierarchy captured from
the compiler, including user-defined exception classes. Matching never falls
back to simple class names, so unrelated homonymous exception types remain
distinct. Multi-catch alternatives are retained separately from their common
supertype, and an unresolved handler is modeled as a conservative possible
target rather than inferred from display text.[2][5]

`ControlFlowGraph` is the aggregate root for one method's analysis. It owns one
entry, one exit, ordered operation nodes, ordered flow values, and edges;
`OperationNode` owns its operation kind, ordered operands, optional defined
value, optional kind-constrained invocation semantics, semantic references,
possible exceptions, and source origins. Temporary
endpoints and pending edges exist only inside the builder while the immutable
graph is assembled; they are neither public model concepts nor persisted
entities. This responsibility distribution keeps the graph invariants with the
object that owns them and avoids coupling `MethodAnalysis` to two parallel
representations of the same lowered behavior.[5][6][12]

Entry and exit never receive fabricated origins. An exit may be unreachable.
The CFG is intraprocedural: invocation operations are not expanded into
callees.[5][6]

`JavaProgram` is the canonical owner of translated types, type parameters,
fields, and methods. Canonical type, type-parameter, field, and method keys are
unique across the whole program. Every nested type's enclosing reference must
identify another canonical type in the same program, match that declaration's
compiler-derived identity, and form no ownership cycle. Superclass, interface,
permit, and bound references may instead identify declarations outside the
analyzed root while retaining their type identity and available canonical key;
method signatures use erased parameter types, parameters use their ordinal in
the owning executable, and source locals use their executable owner, declaration
kind, and exact compiler source range. A
`ProgramRepresentation` accepts a `MethodAnalysis` only when its complete
`sourceMethod` value equals the canonical program method for that key, and it
accepts at most one analysis for each program method. Consequently, equal keys
cannot conceal divergent names, signatures, body-presence facts, parameters, or source
origins inside the same aggregate.[8][18]

## Application Orchestration

`SourceAnalysisRunner` composes the source-analysis port and unchanged
`AnalysisEngine` behind the caller-facing `AnalysisRunAnalyzer` operation.
`AnalysisApplicationService` accepts an `AnalysisRequest`, invokes that operation,
applies the completed-run publication rule, opens and closes the configured repository
only when publication was requested, and returns an `AnalysisExecution`. The
execution distinguishes repository storage that was not requested, was not
attempted because analysis did not complete, and completed successfully.[16]

`AnalysisRunRepository` and `AnalysisRunRepositoryFactory` are
technology-neutral repository ports. `Neo4jAnalysisRunRepository` implements
the repository port, while
`AquilaGraphApplication` is the composition root that reads Neo4j settings and
selects the adapter. `AnalyzeCommand` is limited to parsing CLI arguments,
presenting the application result, and mapping it to an exit code. A repository
failure retains the completed `AnalysisRun` and original cause in
`AnalysisRepositoryException`, allowing another delivery interface to reuse the
same use case without reproducing orchestration or depending on Neo4j
configuration.[9][16][17]

### Exception policy

The model is conservative and profile-specific:

| Operation family | Possible exceptional transfer |
| --- | --- |
| instance field read and array length | `NullPointerException` |
| array read | `NullPointerException`, `ArrayIndexOutOfBoundsException` |
| instance field assignment | receiver evaluation, then right operand, then `NullPointerException` on the write |
| array assignment | array, index, and right operand evaluation, then `NullPointerException` or `ArrayIndexOutOfBoundsException`; reference arrays may also produce `ArrayStoreException` |
| integer division and remainder | `ArithmeticException` |
| cast | `ClassCastException` |
| switch selector | `NullPointerException`, `MatchException` |
| invocation | `NullPointerException` plus `unchecked-invocation` |
| object construction | `unchecked-constructor` |
| explicit throw | the resolved/static thrown type when available |

An expression-qualified static field or method still evaluates and discards its
qualifier before the static access or invocation. Invocation syntax and the
lowered operation retain the distinction among implicit, `this`, `super`,
qualified-super, expression, type, and constructor forms; selectors used only
to name a declaration are not lowered as value reads. Simple assignment
similarly retains a dedicated storage location so the CFG never invents an
old-value read.[2][5][7][20][21]

Known explicit exceptions route to the first catch whose qualified declared
type appears in the compiler-derived transitive assignability hierarchy. Known implicit JVM
runtime effects carry their documented `RuntimeException`, `Exception`, and
`Throwable` hierarchy. Unknown invocation and construction effects route to
every in-scope handler and to the exit. These edges mean “possible transfer”;
they are not a path-feasibility or whole-program exception proof.[2][5][7]

## Completion Contract

- `COMPLETED`: every encountered language feature is either represented or
  deliberately irrelevant, the frontend/core diagnostics contain no errors, and every
  compiler-visible concrete method body has exactly one `MethodAnalysis` with a
  CFG.
- `INCOMPLETE`: a source model exists, but an error or missing method analysis
  prevents full acceptance. Partial project-owned artifacts and diagnostics are
  retained.
- `REJECTED`: no Java sources or no accepted Java type exists. No
  `ProgramRepresentation` is attached.

Only completed runs are eligible for repository storage. A repository failure is a
separate application outcome and never changes the already computed completion;
the CLI maps that outcome to its repository exit code. Completion consumes the
frontend's coverage diagnostics, but CFG success is not evidence of language-feature
coverage: catalog classification and encounter accounting happen before lowering,
and control-flow diagnostics retain their separate stage.[8][9][16][26]

## CLI Contract

```text
java -jar target/aquila-graph-0.1.0-SNAPSHOT.jar <source-root> [--release <feature>] [--persist]
```

Omitting `--release` selects the Java 25 default. An explicit release is resolved
through `JavaRelease`; unsupported, missing, malformed, or duplicate release
arguments are rejected before analysis. The command reports the selected Java
release, run ID, completion, diagnostics, source/type/method counts, CFG
operation/node/edge counts, and repository
outcome. Exit codes are `0` completed, `1` incomplete analysis, `2` rejected
input or usage, and `3` completed analysis followed by repository failure. Exit
code `2` includes an invalid source root and source access or UTF-8 decoding
failures detected during snapshot discovery.[1][9]

## OpenJDK Research Provenance

The normal/abrupt-flow organization was informed by OpenJDK 25 Updates,
repository `openjdk/jdk25u`, commit
`49411542a96435cd403500592fbb586395fbaed5`, file
`src/jdk.compiler/share/classes/com/sun/tools/javac/comp/Flow.java`, especially
`AliveAnalyzer.visitIf`, `visitTry`, `visitSwitchExpression`, `visitReturn`, and
`visitThrow`.[10] No OpenJDK source was copied. Aquila Graph independently
implements its bounded model and has no runtime dependency on
`com.sun.tools.javac.*` or other non-exported internals.[2][5]

## References

1. [JavaSourceLoader](../../src/main/java/org/aquilagraph/frontend/JavaSourceLoader.java), `discover`.
2. [JavaFrontend](../../src/main/java/org/aquilagraph/frontend/JavaFrontend.java), public facade;
   [JavaAnalysisPipeline](../../src/main/java/org/aquilagraph/frontend/JavaAnalysisPipeline.java), analysis-run coordination;
   [CompilerSession](../../src/main/java/org/aquilagraph/frontend/CompilerSession.java), compiler-resource ownership;
   [SnapshotSourceCatalog](../../src/main/java/org/aquilagraph/frontend/SnapshotSourceCatalog.java), captured-source correlation;
   [CompilerDiagnosticTranslator](../../src/main/java/org/aquilagraph/frontend/CompilerDiagnosticTranslator.java), compiler diagnostics;
   [JavaProgramTranslator](../../src/main/java/org/aquilagraph/frontend/JavaProgramTranslator.java), declaration translation;
   [JavaAstContributionTranslator](../../src/main/java/org/aquilagraph/frontend/JavaAstContributionTranslator.java), per-source AST construction;
   [JavaModuleTranslator](../../src/main/java/org/aquilagraph/frontend/JavaModuleTranslator.java), written module and directive occurrences;
   [JavaTypeTranslator](../../src/main/java/org/aquilagraph/frontend/JavaTypeTranslator.java), exhaustive class-member classification;
   [DeclarationIdentityRegistry](../../src/main/java/org/aquilagraph/frontend/DeclarationIdentityRegistry.java), canonical declaration keys;
   [JavaMethodBodyTranslator](../../src/main/java/org/aquilagraph/frontend/JavaMethodBodyTranslator.java), method-body traversal;
   [MethodBodySyntaxProfile](../../src/main/java/org/aquilagraph/frontend/MethodBodySyntaxProfile.java), contextual syntax mapping; and
   [SemanticResolver](../../src/main/java/org/aquilagraph/frontend/SemanticResolver.java), compiler semantic queries;
   [JavaSourceEvidence](../../src/main/java/org/aquilagraph/frontend/JavaSourceEvidence.java), source-position projection;
   [SyntaxAnalysisInput](../../src/main/java/org/aquilagraph/syntax/SyntaxAnalysisInput.java), durable in-memory frontend-to-core boundary; and
   [JavaAstAggregate](../../src/main/java/org/aquilagraph/syntax/JavaAstAggregate.java), sealed source occurrences and aggregate-owned method bodies; and
   [syntax package](../../src/main/java/org/aquilagraph/syntax), typed syntax evidence contracts.
3. [InputSnapshot](../../src/main/java/org/aquilagraph/model/InputSnapshot.java), record invariants.
4. [StableId](../../src/main/java/org/aquilagraph/model/StableId.java), `sha256`.
5. [ControlFlowGraphBuilder](../../src/main/java/org/aquilagraph/core/ControlFlowGraphBuilder.java), public facade; [SyntaxGraphLowerer](../../src/main/java/org/aquilagraph/core/SyntaxGraphLowerer.java), recursive dispatch; [StatementGraphLowerer](../../src/main/java/org/aquilagraph/core/StatementGraphLowerer.java), [ExpressionGraphLowerer](../../src/main/java/org/aquilagraph/core/ExpressionGraphLowerer.java), [AssignmentLocationLowerer](../../src/main/java/org/aquilagraph/core/AssignmentLocationLowerer.java), and [FieldReceiverLowerer](../../src/main/java/org/aquilagraph/core/FieldReceiverLowerer.java), direct graph lowering; [ControlFlowGraphConstruction](../../src/main/java/org/aquilagraph/core/ControlFlowGraphConstruction.java), graph construction and exception routing; and [FlowValueScope](../../src/main/java/org/aquilagraph/core/FlowValueScope.java), lexical bindings.
6. [ControlFlowGraph](../../src/main/java/org/aquilagraph/model/ControlFlowGraph.java), aggregate invariants;
   [OperationNode](../../src/main/java/org/aquilagraph/model/OperationNode.java),
   [InvocationSemantics](../../src/main/java/org/aquilagraph/model/InvocationSemantics.java), and
   [FlowValue](../../src/main/java/org/aquilagraph/model/FlowValue.java), graph-owned semantics.
7. *The Java Language Specification, Java SE 25 Edition*, §§14.1, 14.17,
   14.18, 14.20, 15.6, 15.7, 15.23, 15.24, and 15.28; local copy
   [the-java-language-specification.pdf](../../books/the-java-language-specification.pdf).
8. [AnalysisEngine](../../src/main/java/org/aquilagraph/core/AnalysisEngine.java), `analyze`.
9. [AnalyzeCommand](../../src/main/java/org/aquilagraph/cli/AnalyzeCommand.java), exit constants and `run`.
10. OpenJDK 25 Updates,
    [`Flow.java` at the pinned commit](https://github.com/openjdk/jdk25u/blob/49411542a96435cd403500592fbb586395fbaed5/src/jdk.compiler/share/classes/com/sun/tools/javac/comp/Flow.java).
11. Møller and Schwartzbach, *Static Program Analysis*, §§2.3–2.5; local copy
    [static-program-analysis.pdf](../../books/static-program-analysis.pdf#page=22).
12. Raul Sidnei Wazlawick, *Object-Oriented Analysis and Design for Information
    Systems* (2014), §9.2, PDF pp. 286–289, responsibility distribution and
    cohesion; §9.6, PDF pp. 316–319, delegation and coupling; and §13.2.2.9,
    PDF pp. 441–442, transient associations.
13. OMG, [*Unified Modeling Language 2.5.1*](https://www.omg.org/spec/UML/2.5.1/PDF),
    §§11.4–11.6, classes, associations, composition, and components; §17.8,
    sequence diagrams.
14. [AnalysisEnvironmentCapture](../../src/main/java/org/aquilagraph/frontend/AnalysisEnvironmentCapture.java), capture and verification;
    [CompilerDependencyCapture](../../src/main/java/org/aquilagraph/frontend/CompilerDependencyCapture.java), dependency-content identity;
    [AnalysisEnvironmentSnapshot](../../src/main/java/org/aquilagraph/model/AnalysisEnvironmentSnapshot.java) and
    [DependencySnapshot](../../src/main/java/org/aquilagraph/model/DependencySnapshot.java), immutable environment identity.
15. [JavaRelease](../../src/main/java/org/aquilagraph/model/JavaRelease.java), supported release values;
    [CompilerConfiguration](../../src/main/java/org/aquilagraph/model/CompilerConfiguration.java), managed compiler inputs; and
    [JavaCompilerReleaseSupport](../../src/main/java/org/aquilagraph/frontend/JavaCompilerReleaseSupport.java), system compiler capability validation.
16. [AnalysisApplicationService](../../src/main/java/org/aquilagraph/application/AnalysisApplicationService.java), `execute`;
    [SourceAnalysisRunner](../../src/main/java/org/aquilagraph/application/SourceAnalysisRunner.java), frontend/core composition;
    [AnalysisRunAnalyzer](../../src/main/java/org/aquilagraph/application/port/AnalysisRunAnalyzer.java), caller-facing run operation;
    [AnalysisExecution](../../src/main/java/org/aquilagraph/application/AnalysisExecution.java), repository outcome invariants; and
    [AnalysisRunRepository](../../src/main/java/org/aquilagraph/application/port/AnalysisRunRepository.java), technology-neutral repository port.
17. [AquilaGraphApplication](../../src/main/java/org/aquilagraph/AquilaGraphApplication.java), composition root and Neo4j adapter selection.
18. [JavaProgram](../../src/main/java/org/aquilagraph/model/JavaProgram.java), program-wide method-key uniqueness; [ProgramRepresentation](../../src/main/java/org/aquilagraph/model/ProgramRepresentation.java), canonical method-analysis membership.
19. Oracle, [Java SE 25 `JavaCompiler` API](https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/tools/JavaCompiler.html),
    compiler task, diagnostic collector, standard file manager, and explicit close pattern.
20. Oracle, *The Java Language Specification, Java SE 25 Edition*,
    [§13.1](https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.1),
    invocation qualifying types.
21. Oracle, *The Java Language Specification, Java SE 25 Edition*,
    [§15.12.3](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12.3),
    compile-time invocation modes.
22. Oracle, Java SE 25
    [`ClassTree`](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/tree/ClassTree.html)
    and
    [`TypeElement`](https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/TypeElement.html)
    APIs, declaration-header syntax and compiler-resolved type relationships.
23. [LanguageFeatureCoverage](../../src/main/java/org/aquilagraph/model/LanguageFeatureCoverage.java), immutable release-bound coverage contract;
    [LanguageFeatureClassification](../../src/main/java/org/aquilagraph/model/LanguageFeatureClassification.java), exclusive policy metadata;
    [LanguageFeature](../../src/main/java/org/aquilagraph/model/LanguageFeature.java), exact feature identity;
    [LanguageFeaturePolicy](../../src/main/java/org/aquilagraph/model/LanguageFeaturePolicy.java), completion-blocking policy semantics; and
    [LanguageFeatureCoverageFactory](../../src/main/java/org/aquilagraph/frontend/LanguageFeatureCoverageFactory.java), compiler-configuration release binding.
24. [JavaCompilerKindCoverage](../../src/main/java/org/aquilagraph/frontend/JavaCompilerKindCoverage.java), explicit Java 25 kind catalog and
    runtime-enum equality validation; Oracle, Java SE 25
    [`Tree.Kind`](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/tree/Tree.Kind.html), complete enum universe and
    implementation-reserved `OTHER` kind.
25. [JavaDeclarationPropertyCoverage](../../src/main/java/org/aquilagraph/frontend/JavaDeclarationPropertyCoverage.java), exact Java 25
    declaration-accessor catalog and runtime-universe validation;
    [JavaDeclarationPropertyCoverageTest](../../src/test/java/org/aquilagraph/frontend/JavaDeclarationPropertyCoverageTest.java), complete-set
    and policy-integrity regressions;
    [JavaDeclarationCompletenessRegressionTest](../../src/test/java/org/aquilagraph/frontend/JavaDeclarationCompletenessRegressionTest.java),
    valid-source completion regressions; Oracle, Java SE 25
    [`CompilationUnitTree`](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/tree/CompilationUnitTree.html),
    [`ClassTree`](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/tree/ClassTree.html),
    [`MethodTree`](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/tree/MethodTree.html),
    [`VariableTree`](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/tree/VariableTree.html),
    [`TypeElement`](https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/TypeElement.html), and
    [`ExecutableElement`](https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ExecutableElement.html)
    APIs, complete declaration-tree and element accessor surfaces.
26. [JavaLanguageFeatureCoverage](../../src/main/java/org/aquilagraph/frontend/JavaLanguageFeatureCoverage.java), combined release contract;
    [JavaCompilerKindDiagnostics](../../src/main/java/org/aquilagraph/frontend/JavaCompilerKindDiagnostics.java), exact encountered-kind accounting;
    [JavaDeclarationPropertyObserver](../../src/main/java/org/aquilagraph/frontend/JavaDeclarationPropertyObserver.java), contextual property accounting;
    [JavaLanguageCompletionContractTest](../../src/test/java/org/aquilagraph/frontend/JavaLanguageCompletionContractTest.java), black-box
    root-cause, completion, and determinism regressions; Oracle, Java SE 25
    [`TreeScanner`](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html), recursive child traversal; and
    [`JavacTask`](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/JavacTask.html), distinct parse and analysis phases.
27. [LanguageFeatureCoverageResult](../../src/main/java/org/aquilagraph/model/LanguageFeatureCoverageResult.java), per-input coverage outcome;
    [LanguageFeatureObservation](../../src/main/java/org/aquilagraph/model/LanguageFeatureObservation.java), exact source-owned occurrences;
    [AnalysisOutcomeKind](../../src/main/java/org/aquilagraph/model/AnalysisOutcomeKind.java), typed root-cause vocabulary; and
    [AnalysisDiagnostic](../../src/main/java/org/aquilagraph/model/AnalysisDiagnostic.java), stable diagnostics and typed outcome projection;
    Oracle, Java SE 25 [`Diagnostic`](https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/tools/Diagnostic.html),
    diagnostic kind and implementation-dependent optional code.
28. [SyntaxValue.TypeDeclaration and TypeParameterDeclaration](../../src/main/java/org/aquilagraph/syntax/SyntaxValue.java),
    written and attributed type facts and owner-keyed type-parameter attachments;
    [SyntaxKind](../../src/main/java/org/aquilagraph/syntax/SyntaxKind.java), distinct source-only categories;
    [SyntaxNode](../../src/main/java/org/aquilagraph/syntax/SyntaxNode.java), type-header child invariants;
    [MethodSyntax](../../src/main/java/org/aquilagraph/syntax/MethodSyntax.java), explicit CFG-readiness category boundary;
    [JavaTypeDeclarationAstTest](../../src/test/java/org/aquilagraph/frontend/JavaTypeDeclarationAstTest.java), five-kind and
    ordered-header observations; Oracle, Java SE 25
     [ClassTree](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/tree/ClassTree.html),
     declaration header and member accessors, and
     [JLS 25, §8.10.1](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.10.1),
     record components in declaration headers, and
    [Elements.getOrigin](https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/util/Elements.html#getOrigin(javax.lang.model.element.Element)),
    compiler declaration provenance.
29. [SyntaxValue.ExecutableDeclaration, ParameterDeclaration, and ReceiverParameter](../../src/main/java/org/aquilagraph/syntax/SyntaxValue.java),
    distinct source executable, formal-declaration, and receiver-occurrence forms
    with exact header cardinalities, a separately attributed receiver type, and
    ordered attributed thrown types;
    [SyntaxNode](../../src/main/java/org/aquilagraph/syntax/SyntaxNode.java) and
    [SyntaxValueSupport](../../src/main/java/org/aquilagraph/syntax/SyntaxValueSupport.java),
    standalone receiver-name and Java 25 type-identifier validation;
    [SyntaxNodeTest](../../src/test/java/org/aquilagraph/syntax/SyntaxNodeTest.java),
    public-construction cases for identifier, keyword, literal, and type-identifier forms;
    [JavaAstContributionTranslator](../../src/main/java/org/aquilagraph/frontend/JavaAstContributionTranslator.java),
    compiler-correlated header construction;
    [JavaExecutableDeclarationAstTest](../../src/test/java/org/aquilagraph/frontend/JavaExecutableDeclarationAstTest.java),
    ordered header and identity observations; Oracle, Java SE 25
    [MethodTree](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/tree/MethodTree.html) and
    [ExecutableElement.getReceiverType](https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ExecutableElement.html#getReceiverType()) and
    [getThrownTypes](https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ExecutableElement.html#getThrownTypes()),
    written source headers and attributed receiver and thrown-type facts;
    [JLS 25 §§3.8–3.9](https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.8),
    Java identifiers and keywords; [JLS 25 §8.4](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4),
    [§8.8](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.8), and
    [§9.6.2](https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.6.2),
    kind-specific method result, constructor body, and annotation default rules.
30. [JavaEnumConstantSourceEvidence](../../src/main/java/org/aquilagraph/frontend/JavaEnumConstantSourceEvidence.java),
    Unicode-aware enum delimiter evidence;
    [JavaTypeTranslator](../../src/main/java/org/aquilagraph/frontend/JavaTypeTranslator.java),
    direct declaration reconciliation;
    [JavaVariableDeclarationAstTest](../../src/test/java/org/aquilagraph/frontend/JavaVariableDeclarationAstTest.java),
    enum, record, duplicate-type, and compiler inventory observations;
    [JLS 25 §3.3](https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.3),
    Unicode-escape eligibility;
    [§8.9.3](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.9.3) and
    [§8.10.3](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.10.3),
    implicit enum and record methods.
31. [JavaTypeKindCoverage](../../src/main/java/org/aquilagraph/frontend/JavaTypeKindCoverage.java), closed attributed-kind catalog;
    [JavaTypeAttribution](../../src/main/java/org/aquilagraph/frontend/JavaTypeAttribution.java), closed compiler outcome states;
    [JavaTypeKindObserver](../../src/main/java/org/aquilagraph/frontend/JavaTypeKindObserver.java), written-header attribution observations;
    [JavaTypeKindCoverageTest](../../src/test/java/org/aquilagraph/frontend/JavaTypeKindCoverageTest.java), exact-set, rejection,
    and public-frontend evidence; Oracle, Java SE 25
    [`TypeKind`](https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html)
    and [`TypeMirror`](https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeMirror.html),
    exact kind inventory and attribution boundary.
