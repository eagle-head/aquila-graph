# Java Analysis Design

## Implemented Boundary

Aquila Graph analyzes a recursively discovered Java 25 source root without
executing the target and without emitting target bytecode. `JavaSourceLoader`
reads each source once as UTF-8 and captures its content in the input snapshot.
`JavaFrontend` is the stable public facade: it delegates source discovery to the
loader and snapshot analysis to `JavaCompilerAnalyzer`. The analyzer owns one
compiler session, including compiler acquisition, environment capture and
verification, file-manager lifetime, `JavacTask.parse()`, `JavacTask.analyze()`,
and result assembly.[1][2]

Inside that session, `JavaCompilerInvocation` derives managed compiler options,
and `SnapshotSourceCatalog` is the single correlation point between captured
`SourceFile` values, in-memory `JavaFileObject` values, compiler URIs, and source
ownership. `CompilerDiagnosticTranslator` converts compiler diagnostics;
`JavaProgramTranslator` converts compilation units, types, methods, and
parameters; `JavaSyntaxTranslator` recursively converts one method body;
`JavaSyntaxClassifier` classifies syntax independently from traversal; and
`JavaTreeSemantics` alone queries compiler elements, types, and source positions.
All compiler-owned objects remain package-internal and are translated into
project-owned records before the facade returns. Target source files are not
reopened after snapshot discovery. Source access and UTF-8 decoding failures
remain checked `IOException` values, with source-path context and the original
cause preserved, through the frontend and application service.[1][2][16]

`JavaSourceLoader` orders normalized relative paths, hashes each captured UTF-8
source with SHA-256, and derives a length-framed SHA-256 snapshot identity from
ordered path/digest pairs. The captured `SourceFile.content` is therefore the
authoritative compiler input and persisted source evidence for that run.[3][4]

Before compiler analysis, `JavaCompilerAnalyzer` captures an immutable
`AnalysisEnvironmentSnapshot` through `AnalysisEnvironmentCapture`. It records
the compiler implementation class,
JVM vendor and runtime version, location-independent analysis options, and the
ordered class-path and module-path dependencies. Regular files are fingerprinted
from their bytes; directories are fingerprinted from ordered relative entry
names, file sizes, and file bytes; a missing configured entry has an explicit
fingerprint. The frontend captures the environment again after analysis and
rejects the result if any captured value changed in the interval. It always
passes an explicit class path, including the empty class path, so compiler
analysis cannot inherit the analyzer process class path implicitly.[2][14]

The analysis-run key combines the input snapshot digest, the environment digest,
and analyzer/model versions. The environment digest includes compiler identity,
analysis options, dependency roles, order, kinds, and content fingerprints, but
excludes dependency locations. Locations remain provenance metadata. Options
that could add an uncaptured compiler input location are reserved to Aquila
Graph and must instead use the captured configuration fields.[8][14][15]

## Accepted Source Profile

The first profile is deliberately smaller than Java 25. It accepts the
calculator and the following constructs when compiler analysis resolves their
required semantics:

- top-level class, interface, enum, record, and annotation declarations;
- constructors and methods, including parameters and compiler-generated
  declarations when javac exposes them; abstract and native declarations have
  no body analysis;
- blocks, local declarations, expression statements, `return`, explicit
  `throw`, `if`/`else`, and `try` with `catch` clauses;
- arrow-style switch expressions and their cases/yields;
- method invocation, object construction, member selection, array length and
  array access, identifiers, literals, parentheses, casts, simple assignment,
  conditional expressions, `&&`, `||`, unary `+`, unary `-`, `!`, `~`, and the
  represented binary operators.

Unsupported syntax is retained as `SyntaxKind.UNSUPPORTED`, emits a frontend
error, marks the containing body unsupported, and prevents a completed run.
Notably unsupported are loops, switch statements, labeled control, `break`,
`continue`, `assert`, synchronized statements, lambdas, method references,
compound assignment, increment/decrement, try-with-resources, and `finally`.
Statement-group switch fall-through and unrestricted nested exception handling
are not part of this profile.[2][5]

## Graph-Native Semantic Lowering and Control Flow

Compiler-derived syntax remains distinct from the control-flow graph because it
preserves source structure and provenance. There is no separate normalized-body
aggregate. `ControlFlowGraphBuilder` is a small public facade. It delegates one
accepted method to `SyntaxGraphLowerer`, which owns recursive syntax dispatch
and delegates syntax-family semantics to separate statement and expression
lowerers. `ControlFlowGraphConstruction` owns graph identity, mutable graph
state, fragment composition, nested exception targets, and final immutable
assembly. `FlowValueScope` separately owns lexical value visibility. Together
these collaborators visit operands left to right and lower supported syntax
directly into graph-owned operation nodes, flow values, and typed edges.
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
value, semantic references, possible exceptions, and source origins. Temporary
endpoints and pending edges exist only inside the builder while the immutable
graph is assembled; they are neither public model concepts nor persisted
entities. This responsibility distribution keeps the graph invariants with the
object that owns them and avoids coupling `MethodAnalysis` to two parallel
representations of the same lowered behavior.[5][6][12]

Entry and exit never receive fabricated origins. An exit may be unreachable.
The CFG is intraprocedural: invocation operations are not expanded into
callees.[5][6]

`JavaProgram` is the canonical owner of translated `JavaMethod` values and
requires method keys to be unique across the whole program. A
`ProgramRepresentation` accepts a `MethodAnalysis` only when its complete
`sourceMethod` value equals the canonical program method for that key, and it
accepts at most one analysis for each program method. Consequently, equal keys
cannot conceal divergent names, signatures, bodies, parameters, or source
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
| member access and array length | `NullPointerException` |
| array access | `NullPointerException`, `ArrayIndexOutOfBoundsException` |
| integer division and remainder | `ArithmeticException` |
| cast | `ClassCastException` |
| switch selector | `NullPointerException`, `MatchException` |
| invocation | `NullPointerException` plus `unchecked-invocation` |
| object construction | `unchecked-constructor` |
| explicit throw | the resolved/static thrown type when available |

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
java -jar target/aquila-graph-0.1.0-SNAPSHOT.jar <source-root> [--persist]
```

The command reports the run ID, completion, diagnostics, source/type/method
counts, CFG operation/node/edge counts, and repository
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
   [JavaCompilerAnalyzer](../../src/main/java/org/aquilagraph/frontend/JavaCompilerAnalyzer.java), compiler-session coordination;
   [SnapshotSourceCatalog](../../src/main/java/org/aquilagraph/frontend/SnapshotSourceCatalog.java), captured-source correlation;
   [CompilerDiagnosticTranslator](../../src/main/java/org/aquilagraph/frontend/CompilerDiagnosticTranslator.java), compiler diagnostics;
   [JavaProgramTranslator](../../src/main/java/org/aquilagraph/frontend/JavaProgramTranslator.java), declaration translation;
   [JavaSyntaxTranslator](../../src/main/java/org/aquilagraph/frontend/JavaSyntaxTranslator.java), method-body traversal;
   [JavaSyntaxClassifier](../../src/main/java/org/aquilagraph/frontend/JavaSyntaxClassifier.java), syntax classification; and
   [JavaTreeSemantics](../../src/main/java/org/aquilagraph/frontend/JavaTreeSemantics.java), compiler semantic queries.
3. [InputSnapshot](../../src/main/java/org/aquilagraph/model/InputSnapshot.java), record invariants.
4. [StableId](../../src/main/java/org/aquilagraph/model/StableId.java), `sha256`.
5. [ControlFlowGraphBuilder](../../src/main/java/org/aquilagraph/core/ControlFlowGraphBuilder.java), public facade; [SyntaxGraphLowerer](../../src/main/java/org/aquilagraph/core/SyntaxGraphLowerer.java), recursive dispatch; [StatementGraphLowerer](../../src/main/java/org/aquilagraph/core/StatementGraphLowerer.java) and [ExpressionGraphLowerer](../../src/main/java/org/aquilagraph/core/ExpressionGraphLowerer.java), syntax-family translation; [ControlFlowGraphConstruction](../../src/main/java/org/aquilagraph/core/ControlFlowGraphConstruction.java), graph construction and exception routing; and [FlowValueScope](../../src/main/java/org/aquilagraph/core/FlowValueScope.java), lexical bindings.
6. [ControlFlowGraph](../../src/main/java/org/aquilagraph/model/ControlFlowGraph.java), aggregate invariants; [OperationNode](../../src/main/java/org/aquilagraph/model/OperationNode.java) and [FlowValue](../../src/main/java/org/aquilagraph/model/FlowValue.java), graph-owned semantics.
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
    cohesion; §9.6, PDF pp. 316–319, delegation and coupling.
13. OMG, [*Unified Modeling Language 2.5.1*](https://www.omg.org/spec/UML/2.5.1/PDF),
    §§11.4–11.6, classes, associations, composition, and components; §17.8,
    sequence diagrams.
14. [AnalysisEnvironmentCapture](../../src/main/java/org/aquilagraph/frontend/AnalysisEnvironmentCapture.java), `capture`; [AnalysisEnvironmentSnapshot](../../src/main/java/org/aquilagraph/model/AnalysisEnvironmentSnapshot.java) and [DependencySnapshot](../../src/main/java/org/aquilagraph/model/DependencySnapshot.java), immutable environment identity.
15. [CompilerConfiguration](../../src/main/java/org/aquilagraph/model/CompilerConfiguration.java), managed compiler options.
16. [AnalysisApplicationService](../../src/main/java/org/aquilagraph/application/AnalysisApplicationService.java), `execute`; [AnalysisExecution](../../src/main/java/org/aquilagraph/application/AnalysisExecution.java), repository outcome invariants; [AnalysisRunRepository](../../src/main/java/org/aquilagraph/repository/AnalysisRunRepository.java), technology-neutral repository port.
17. [AquilaGraphApplication](../../src/main/java/org/aquilagraph/AquilaGraphApplication.java), composition root and Neo4j adapter selection.
18. [JavaProgram](../../src/main/java/org/aquilagraph/model/JavaProgram.java), program-wide method-key uniqueness; [ProgramRepresentation](../../src/main/java/org/aquilagraph/model/ProgramRepresentation.java), canonical method-analysis membership.
