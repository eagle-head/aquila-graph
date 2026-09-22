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
converts one method body into transient `MethodSyntax`; and
`MethodBodySyntaxProfile` maps each compiler-tree form through visitor dispatch
to a typed `SyntaxKind`, typed `SyntaxValue`, role-bearing `SyntaxChild` values,
stable structural paths, and contextual value or assignment-location use.
Unsupported forms are represented by their node kind rather than parallel
acceptance state. `SemanticResolver` projects compiler elements, independent
expression types, declaration references, type hierarchies, and invocation
qualifying types and modes, while
`JavaSourceEvidence` independently projects
source positions. Translation results carry their diagnostics explicitly, and
recursive body assembly uses immutable mappings without retaining per-method
state. `JavaProgram`, `JavaType`, and `JavaMethod` retain declaration facts only,
including exact type ownership and whether a method body exists;
`SyntaxAnalysisInput` carries the corresponding syntax trees
only across the frontend-to-core boundary. All compiler-owned objects remain
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

The analysis-run key combines the input snapshot digest, the environment digest,
and analyzer/model versions. The environment digest includes compiler identity,
analysis options, dependency roles, order, kinds, and content fingerprints, but
excludes dependency locations. Locations remain provenance metadata. Options
that could add an uncaptured compiler input location are reserved to Aquila
Graph and must instead use the captured configuration fields.[8][14][15]

## Release-Bound Language Coverage Contract

`LanguageFeatureCoverage` is the compiler-neutral contract for classifying Java
language coverage. Its version is the typed `JavaRelease` selected by
`CompilerConfiguration`. `LanguageFeatureCoverageFactory` obtains that release from
the same configuration instance that `JavaCompilerInvocation` uses to derive the
compiler's `--release` option; the coverage contract does not probe the runtime or
carry an independent release literal.[2][15][23]

Each exact `LanguageFeature` belongs to either the compiler-tree-kind or
declaration-property category and has one `LanguageFeaturePolicy`:

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
the current `JavaProgram` or `MethodSyntax` boundary retaining the fact;
`DIAGNOSED` entries carry deterministic kind-specific diagnostic codes for later
encounter accounting; and the implementation-reserved `OTHER` kind is the sole
`IRRELEVANT` entry, with an official API locator and semantic-scope rationale.[23][24]
These policies classify the compiler-kind identity itself. They do not claim that
every accessor or source form sharing that kind is already modeled: existing
occurrence-specific diagnostics still reject unsupported forms, while the separate
declaration-property catalog owns accessor completeness.[23][24]

`JavaDeclarationPropertyCoverage` owns the Java 25 declaration-property catalog.
Its exact identities include the declaring interface and raw parameter types for
every non-static public accessor inherited or declared by `CompilationUnitTree`,
`ClassTree`, `MethodTree`, `VariableTree`, `TypeElement`, and
`ExecutableElement`. Construction derives that accessor universe from the configured
JDK 25 APIs and compares it in both directions with the explicit catalog, so missing,
duplicate, unrecognized, extra, catch-all, and policyless entries cannot silently
enter the contract. Inherited visitor, element-identity, annotation-proxy lookup,
and line-map protocols are explicitly irrelevant with direct API locators. Import
declarations are diagnosed because javac uses them for attribution while the current
semantic graph does not retain their provenance.[23][24][25]

The declaration-property catalog's diagnosed policies for method type parameters,
thrown types, receiver parameters, variable arity, declaration annotations,
annotation defaults, record components, package declarations, and module declarations
are connected to source-located frontend errors. Those errors flow through the
existing completion projection and therefore prevent `COMPLETED` without using CFG
construction as the declaration-coverage oracle. Unsupported import provenance,
qualified receiver names, variable modifiers or initializers that lack a current
representation boundary, local declaration annotations, and compiler-mandated
enclosed declarations also produce source-located capability errors. The catalog
therefore classifies `VariableTree.getModifiers()` and
`VariableTree.getInitializer()` conservatively as diagnosed: field modifiers and
local initializer syntax remain retained where supported, while any context that
would discard either fact must emit the catalog's diagnostic. Attributed executable
modifiers, including implicit interface method modifiers, are retained in
`JavaMethod`. Compiler errors retain their distinct `compiler.err.*` codes.[2][23][25]

The compiler-kind catalog remains a staging boundary. Its diagnosed policies are not
yet connected to encountered-source accounting; that integration is a later
increment. Coverage values are not persisted, compiler API types remain confined to
`frontend`, and the accepted source profile and completion contract below remain the
implemented runtime behavior.[2][9][23][24][25]

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

Unsupported syntax is retained as `SyntaxKind.UNSUPPORTED`, emits a frontend
error, marks the containing body unsupported, and prevents a completed run.
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

Declaration facts outside this profile report stable capability-specific frontend
errors: `frontend.unsupported-method-type-parameters`,
`frontend.unsupported-thrown-types`, `frontend.unsupported-receiver-parameter`,
`frontend.unsupported-varargs`, `frontend.unsupported-declaration-annotations`,
`frontend.unsupported-annotation-default`, `frontend.unsupported-record-components`,
`frontend.unsupported-package-declaration`, `frontend.unsupported-module-declaration`,
`frontend.unsupported-import-declaration`, `frontend.unsupported-variable-modifiers`,
`frontend.unsupported-variable-initializer`,
`frontend.unsupported-variable-name-expression`, and
`frontend.unsupported-enclosed-declarations`. The declaration-property regression
suite uses valid Java 25 sources and verifies both the expected diagnostic and a
non-completed analysis outcome for every named fact, plus the contextual variable,
import, implicit-modifier, and compiler-mandated-member boundaries.[2][25]

## Graph-Native Semantic Lowering and Control Flow

The core is Java-specific: it consumes a declaration-only `JavaMethod` together
with its transient `MethodSyntax`, while remaining independent of compiler-owned
`javac` objects. The syntax tree is source-structure evidence at this boundary,
not the analysis IR or a member of `ProgramRepresentation`. `SyntaxNode` keeps
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

`AnalysisApplicationService` owns the complete application use case. It accepts
an `AnalysisRequest`, invokes the frontend and analysis engine, applies the
completed-run publication rule, opens and closes the configured repository only
when publication was requested, and returns an `AnalysisExecution`. The
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

- `COMPLETED`: the frontend/core diagnostics contain no errors and every
  compiler-visible concrete method body has exactly one `MethodAnalysis` with a
  CFG.
- `INCOMPLETE`: a source model exists, but an error or missing method analysis
  prevents full acceptance. Partial project-owned artifacts and diagnostics are
  retained.
- `REJECTED`: no Java sources or no accepted Java type exists. No
  `ProgramRepresentation` is attached.

Only completed runs are eligible for repository storage. A repository failure is a
separate application outcome and never changes the already computed completion;
the CLI maps that outcome to its repository exit code.[8][9][16]

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
   [JavaTypeTranslator](../../src/main/java/org/aquilagraph/frontend/JavaTypeTranslator.java), exhaustive class-member classification;
   [DeclarationIdentityRegistry](../../src/main/java/org/aquilagraph/frontend/DeclarationIdentityRegistry.java), canonical declaration keys;
   [JavaMethodBodyTranslator](../../src/main/java/org/aquilagraph/frontend/JavaMethodBodyTranslator.java), method-body traversal;
   [MethodBodySyntaxProfile](../../src/main/java/org/aquilagraph/frontend/MethodBodySyntaxProfile.java), contextual syntax mapping; and
   [SemanticResolver](../../src/main/java/org/aquilagraph/frontend/SemanticResolver.java), compiler semantic queries;
   [JavaSourceEvidence](../../src/main/java/org/aquilagraph/frontend/JavaSourceEvidence.java), source-position projection;
   [SyntaxAnalysisInput](../../src/main/java/org/aquilagraph/syntax/SyntaxAnalysisInput.java), transient frontend-to-core boundary; and
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
16. [AnalysisApplicationService](../../src/main/java/org/aquilagraph/application/AnalysisApplicationService.java), `execute`; [AnalysisExecution](../../src/main/java/org/aquilagraph/application/AnalysisExecution.java), repository outcome invariants; [AnalysisRunRepository](../../src/main/java/org/aquilagraph/repository/AnalysisRunRepository.java), technology-neutral repository port.
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
