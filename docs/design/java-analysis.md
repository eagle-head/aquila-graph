# Java Analysis Design

Aquila Graph uses the JDK compiler to capture declarations and source evidence, reconciles language coverage, and lowers the accepted concrete method bodies into project-owned control-flow graphs. The supported release is Java 25. The [analysis contract](../contracts/analysis.md) owns completion, publication eligibility, and command-line results.[1][2]

## Architecture and ownership

| Area | Responsibility |
| --- | --- |
| `application` and `application.port` | Coordinate analysis and optional storage through application-owned ports. |
| `frontend` | Discover immutable source input, capture compiler/dependency facts, manage compiler resources, translate declarations and syntax, and reconcile evidence. |
| `syntax` | Own transient source occurrences, ordered child roles, typed semantic edges, and selected body decisions without retaining compiler objects. |
| `model` | Own captured identities, canonical declarations, diagnostics, durable results, and immutable CFG values. |
| `core` | Lower admitted method syntax, construct graph identities and transfers, associate graphs with canonical methods, and classify completion. |
| `repository.neo4j` | Map completed results and publish the selected durable surface. |
| `cli` and composition root | Parse arguments, invoke the application, present results, and supply concrete dependencies. |

Source syntax and CFG structure have different responsibilities: written source distinctions remain in the aggregate, while graphs express admitted evaluation and transfer semantics. The durable `ProgramRepresentation` contains declarations and method analyses, not the transient aggregate. [Program views](../../diagrams/class/program/README.md) and [pipeline views](../../diagrams/component/pipeline/README.md) show the crossings.[1][3]

## Compiler lifecycle

The path facade captures UTF-8 source content before compilation. The pipeline captures the environment, checks compiler/release compatibility, and uses controlled invocation options including disabled annotation processing. Empty capture avoids opening a compiler session. Nonempty capture acquires and closes the session, then recaptures the environment before returning; a detected change prevents a returned analysis input.[2]

Compiler sessions and constructor witnesses own their file-manager wrappers and returned closeable loaders. Partial acquisition attempts cleanup of accessible resources. Primary and suppressed cleanup failures remain distinguishable; successful closure is not inferred from a close attempt. Witnesses use the public compiler API and separate evidence from the written source tree.[2][4]

## Declarations and source evidence

The frontend retains compilation-unit/package/import/module structure, canonical type and member identities, executable headers, fields, enum constants, record components, synthesized declarations, and written type-use occurrences. Written declarations are distinguished from compiler-generated counterparts, and absent source positions do not produce fabricated ranges.[3][4]

Type evidence distinguishes not applicable, unresolved, erroneous, unsupported, and resolved outcomes. Recursive type structure retains arguments, enclosing types, bounds, arrays, wildcards, intersections, unions, and scoped capture binders/references where required. Body decisions retain compiler-selected uses, invocation arity, casts, concatenation, and enum-constructor context separately from syntax spelling.[3][4]

Annotations retain written form, occurrence order, selected annotation/element references, exact structural targets, explicit values, and declaration defaults. Explicit use-site values and defaults remain separate. Recursive arrays and nested annotations are typed values. Written repeatable annotations remain individual source occurrences; a compiler container is not presented as newly written syntax. Record propagation and type-use mirror correlation must match the independently captured source obligations.[4]

## Language coverage and readiness

Coverage is release-bound. Compiler tree kinds, type kinds, and declaration accessors have explicit policies rather than a catch-all promise of Java support. Source-only obligations are captured before attribution; attributed evidence supplies a separate stage. Encountered facts, policy entries, valid absence, diagnostic provenance, and retained source tuples must agree before coverage admission.[4][5]

A successful graph does not establish source completeness. A body can be structurally lowerable while missing required compiler evidence, or fully accounted while containing a diagnosed unsupported construct. Neutral input values validate coverage and body-decision correlations before lowering. Translation events carry producer-assigned diagnostic slots through transport; equal diagnostic values do not erase their independent origins.[3][5]

## Supported source profile

The accepted profile is smaller than Java 25. Subject to resolved required semantics, it includes:[4][6]

- Top-level and nested class, interface, enum, record, and annotation declarations; constructors and methods; abstract/native declarations without body graphs.
- Declaration headers and supported type-use/annotation structure. Fields and enum constants remain durable facts, while executable initialization stays outside the graph profile.
- Blocks, supported local declarations, expression statements, `return`, explicit `throw`, `if`/`else`, and bounded `try`/`catch`.
- Arrow switch expressions with supported literal, enum, and default labels and their yielded expressions.
- Calls, object construction, member selection, array length/access, identifiers, literals, parentheses, casts, simple assignment, conditional expressions, short-circuit Boolean operators, supported unary operations, and represented binary operations.

Unsupported syntax is retained with an unsupported outcome and blocking diagnostics. In particular, loops, switch statements, labels and loop transfers, `assert`, synchronization, lambdas, method references, compound assignment, increment/decrement, try-with-resources, `finally`, anonymous class bodies, and broader pattern/guard forms do not qualify as completed method bodies. Source-only body occurrences also require an explicit lowering contract before they become CFG-ready.[4][6]

Field initializers, initializer blocks, enum constant-specific bodies, and source-local annotation/type sites retain their capability-specific diagnostic boundaries. Compiler acceptance does not expand this profile. Remaining source-model work is listed in the [roadmap](../roadmap.md).

## Control flow and evaluation

`ControlFlowGraphBuilder` delegates syntax lowering and graph construction. Graphs own immutable nodes, values, transfers, and selected invocation facts. The engine produces a canonical method analysis only when lowering returns a graph, and compares analyzed method keys with all required concrete methods before completion.[1][6]

Admitted lowering preserves evaluation order and explicit conversion steps. Selected receiver/argument uses and invocation arity control conversions and varargs packing. Static access and construction retain their class-initialization triggers; object construction orders initialization and allocation before arguments. Reference casts retain whether a runtime reference check is required, and primitive conversion/unboxing remain separate steps.[6]

Assignment-location evaluation separates target evaluation from value conversion and storage. Array target checks, field receiver effects, short-circuit branches, conditional values, string concatenation, switch-expression yields, and exception transfers retain their own ordering and value relationships. An unsupported decision or unavailable required semantic fact blocks the graph rather than silently approximating it.[6]

Exception edges describe the modeled routes for the accepted operations and bounded handler profile. They do not establish arbitrary nested-handler coverage, successful resource cleanup, or every possible JVM/runtime outcome. See the [control-flow companion](../../diagrams/class/control-flow/README.md) for the defining views and source owners.

## Verification and limits

Source inspection and diagrams describe contracts; executable evidence comes from the relevant unit, packaged-process, and Community database controls. Use the existing [build targets](../build-quality.md#commands). A documentation revision does not constitute a new runtime result.[7]

No analyzed program execution, annotation processing, bytecode input, whole-program analysis, universal Java 25 completeness, or durable source-aggregate publication is claimed. Directly supplied domain records retain their constructor validation limits; production qualification is owned by the engine and frontend, not inferred from supplied completion labels.[1][2][3]

## References

1. [AnalysisEngine](../../src/main/java/org/aquilagraph/core/AnalysisEngine.java), `analyze`; [AnalysisApplicationService](../../src/main/java/org/aquilagraph/application/AnalysisApplicationService.java), `execute`.
2. [JavaFrontend](../../src/main/java/org/aquilagraph/frontend/JavaFrontend.java), overloads; [JavaAnalysisPipeline](../../src/main/java/org/aquilagraph/frontend/JavaAnalysisPipeline.java), lifecycle; [JavaCompilerInvocation](../../src/main/java/org/aquilagraph/frontend/JavaCompilerInvocation.java), controlled options; [JavaRelease](../../src/main/java/org/aquilagraph/model/JavaRelease.java), supported releases.
3. [JavaAstAggregate](../../src/main/java/org/aquilagraph/syntax/JavaAstAggregate.java), sealing and method views; [SyntaxAnalysisInput](../../src/main/java/org/aquilagraph/syntax/SyntaxAnalysisInput.java), neutral admission; [ProgramRepresentation](../../src/main/java/org/aquilagraph/model/ProgramRepresentation.java), durable result; [SyntaxType](../../src/main/java/org/aquilagraph/syntax/SyntaxType.java), type outcomes.
4. [JavaProgramTranslator](../../src/main/java/org/aquilagraph/frontend/JavaProgramTranslator.java), declarations; [JavaTypeUseTranslator](../../src/main/java/org/aquilagraph/frontend/JavaTypeUseTranslator.java), written type uses; [JavaAnnotationCompleteness](../../src/main/java/org/aquilagraph/frontend/JavaAnnotationCompleteness.java), annotation reconciliation; [JavaConstructorSelection](../../src/main/java/org/aquilagraph/frontend/JavaConstructorSelection.java), witnesses; [CompilerFileManager](../../src/main/java/org/aquilagraph/frontend/CompilerFileManager.java), resource ownership.
5. [JavaLanguageFeatureCoverage](../../src/main/java/org/aquilagraph/frontend/JavaLanguageFeatureCoverage.java), policies; [JavaSourceCoverageCapture](../../src/main/java/org/aquilagraph/frontend/JavaSourceCoverageCapture.java), written obligations; [JavaSourceCoverageReconciler](../../src/main/java/org/aquilagraph/frontend/JavaSourceCoverageReconciler.java), reconciliation; [DiagnosticEmissionTransport](../../src/main/java/org/aquilagraph/frontend/DiagnosticEmissionTransport.java), event transport.
6. [MethodBodySyntaxProfile](../../src/main/java/org/aquilagraph/frontend/MethodBodySyntaxProfile.java), readiness; [ControlFlowGraphBuilder](../../src/main/java/org/aquilagraph/core/ControlFlowGraphBuilder.java), build; [ExpressionGraphLowerer](../../src/main/java/org/aquilagraph/core/ExpressionGraphLowerer.java), expressions; [StatementGraphLowerer](../../src/main/java/org/aquilagraph/core/StatementGraphLowerer.java), statements; [UseConversionLowerer](../../src/main/java/org/aquilagraph/core/UseConversionLowerer.java), conversions; [CallArgumentLowerer](../../src/main/java/org/aquilagraph/core/CallArgumentLowerer.java), arguments; [ControlFlowGraphConstruction](../../src/main/java/org/aquilagraph/core/ControlFlowGraphConstruction.java), graph identities and transfers.
7. [AquilaGraphProcessIT](../../src/test/java/org/aquilagraph/AquilaGraphProcessIT.java), packaged command outcomes; [Neo4j integration tests](../../src/test/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunRepositoryNeo4jIT.java), database observations; [build quality](../build-quality.md), gate responsibilities.
