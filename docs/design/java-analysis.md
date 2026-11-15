# Java Analysis Design

**Status:** these contracts govern separate source production, source qualification, CFG derivation, application coordination, and independent AST retention. [Acceptance ownership](#model-acceptance-and-verification) preserves the review scopes and evidence boundaries. This document is not a new execution or independent recertification.

Aquila Graph uses the JDK compiler to capture declarations and source evidence, reconciles language coverage, and lowers the accepted concrete method bodies into project-owned control-flow graphs. The supported release is Java 25. The [analysis contract](../contracts/analysis.md) owns completion, publication eligibility, and command-line results.[1][2]

## Architecture and ownership

| Area | Responsibility |
| --- | --- |
| `identity.api` | Own immutable captured source/environment/software values and qualified AST/body/occurrence/graph identity values, closing domain operations over Java base. |
| `source.api` | Own immutable declarations, source occurrences, typed facts, source outcomes and public reader contracts. |
| `source.admission` / `source.retention` | Qualify copied production through frontend-only admission; encode/decode retained values through a repository-only codec. Neither route is exported to CFG or application readers. |
| `frontend` | Capture inputs and compiler facts, own compiler resources, translate actual source structure, and invoke source admission. |
| `cfg.api` and `cfg.internal` | Expose independent derivation and immutable graph values; privately own admission, lowering plans, evaluation, construction and graph validation. |
| `application` and `application.port` | Coordinate domain ports, qualify requests, retain independent results, and apply exact publication/query contracts through lazy storage ports. |
| `repository.neo4j` | Encode, store and admit exact retained AST results separately from the explicit legacy completed-run projection. |
| `cli` and composition root | Preserve command-line behavior and wire concrete producer, CFG and repository adapters. |

`ProgramRepresentation` references the identical admitted `AstOutcome` and an optional `CfgOutcome`. Completed-run storage projects declarations and graphs from those native outcomes; full AST retention has its own storage contract. Source facts retain actual written distinctions; graphs own evaluation, transfers and generated provenance. The [responsibility boundaries](#responsibility-boundaries) and [target contracts](#target-ownership-and-contracts) define those crossings.[1][3]

## Compiler lifecycle

The path facade captures UTF-8 source content before compilation. The pipeline captures the environment, checks compiler/release compatibility, and uses controlled invocation options including disabled annotation processing. Empty capture avoids opening a compiler session. Nonempty capture acquires and closes the session, then recaptures the environment before returning; a detected change prevents a returned analysis input.[2]

Compiler sessions and constructor witnesses own their file-manager wrappers and returned closeable loaders. Partial acquisition attempts cleanup of accessible resources. Primary and suppressed cleanup failures remain distinguishable; successful closure is not inferred from a close attempt. Witnesses use the public compiler API and separate evidence from the written source tree.[2][4]

Compiler diagnostic provenance is resolved by membership in the captured input URI catalog. A diagnostic from an external dependency, including source inside a classpath archive, retains its compiler message and classification without acquiring a filesystem or inventing a source range in the snapshot.[2]

Constructor witness type comparisons keep recursive assumptions, capture bindings, and copied declarations provisional until the entire comparison succeeds. A failed comparison does not contribute correspondences to later comparisons or type restoration; earlier accepted correspondences remain available.[4]

## Declarations and source evidence

The frontend retains compilation-unit/package/import/module structure, canonical type and member identities, executable headers, fields, enum constants, record components, synthesized declarations, and written type-use occurrences. Written declarations are distinguished from compiler-generated counterparts, and absent source positions do not produce fabricated ranges.[3][4]

Type evidence distinguishes not applicable, unresolved, erroneous, unsupported, and resolved outcomes. Recursive type structure retains arguments, enclosing types, bounds, arrays, wildcards, intersections, unions, and scoped capture binders/references where required. Body decisions retain compiler-selected uses, invocation arity, casts, concatenation, and enum-constructor context separately from syntax spelling.[3][4]

Annotations retain written form, occurrence order, selected annotation/element references, exact structural targets, explicit values, and declaration defaults. Explicit use-site values and defaults remain separate. Recursive arrays and nested annotations are typed values. Written repeatable annotations remain individual source occurrences; a compiler container is not presented as newly written syntax. Record propagation and type-use mirror correlation must match the independently captured source obligations.[4]

## Language coverage and readiness

Coverage is release-bound. Compiler tree kinds, type kinds, and declaration accessors have explicit policies rather than a catch-all promise of Java support. Source-only obligations are captured before attribution; attributed evidence supplies a separate stage. Encountered facts, policy entries, valid absence, diagnostic provenance, and retained source tuples must agree before coverage admission.[4][5]

A successful graph does not establish source completeness. A body can be structurally lowerable while missing required compiler evidence, or fully accounted while containing a diagnosed unsupported construct. Neutral input values validate coverage and body-decision correlations before lowering. Translation events carry producer-assigned diagnostic slots through transport; equal diagnostic values do not erase their independent origins.[3][5]

## Supported source profiles

The versioned `AstProfile` selects capabilities and qualification for
non-preview Java 25 source. `JavaTranslationPolicy` supplies the built-in
producer revision independently of that selection. The CLI uses `JAVA_SOURCE_BOUNDED`.
Independent callers select a supported profile in their `AstProductionRequest`.
See [AnalyzeCommandArguments](../../src/main/java/org.aquilagraph.cli/org/aquilagraph/cli/AnalyzeCommandArguments.java)
and the [format and provenance contract](ast-value-format.md#format-source-profile-and-producer-revision).[2][15]

| Source profile | Representation contract |
| --- | --- |
| `JAVA_SOURCE_BOUNDED` | Initial bounded non-preview Java 25 source contract. |
| `JAVA_SOURCE_EXPANDED` | Expanded executable source forms, independently qualified from graph support. |
| `JAVA_SOURCE_INITIALIZATION` | Expanded syntax in methods, fields, and static/instance initializer blocks. |
| `JAVA_SOURCE_NESTED` | Initialization capabilities plus local and anonymous type declarations. |
| `JAVA_SOURCE_ENUM_INITIALIZATION` | Nested declarations plus enum arguments and constant-specific bodies. |
| `JAVA_SOURCE_DECLARATIONS` | Enum initialization plus declaration fidelity, including standalone body declarations. |
| `JAVA_SOURCE_UNITS` | Declaration fidelity plus captured compilation-unit envelopes, including legal units without types. |
| `JAVA_SOURCE_FIDELITY` | The unit profile's supported forms plus independent proof for applicable normalized written-expression regions. |
| `JAVA_SOURCE_MODULE_SERVICES` | The fidelity profile plus required resolved targets for applicable written module service and provider operands. |

These profiles require their selected source forms, required semantic facts, and independently reconciled coverage. An accepted compiler input alone does not establish a complete source result. `SourceRequirements` owns source qualification; `CfgAdmission` separately decides whether a requested concrete method is lowerable. Expanded source support for loops, lambdas, references, initialization, and other forms does not add a corresponding CFG lowering contract.[6][9][15]

Use the [roadmap](../roadmap.md) and its owning epic checkpoints for recorded source-expansion acceptance and remaining obligations. Historical bounded-profile exclusions do not describe every currently supported source profile.

## Control flow and evaluation

`ControlFlowGraphBuilder` delegates syntax lowering and graph construction. Graphs own immutable nodes, values, transfers, and selected invocation facts. The engine produces a canonical method analysis only when lowering returns a graph, and compares analyzed method keys with all required concrete methods before completion.[1][6]

Admitted lowering preserves evaluation order and explicit conversion steps. Selected receiver/argument uses and invocation arity control conversions and varargs packing. Static access and construction retain their class-initialization triggers; object construction orders initialization and allocation before arguments. Reference casts retain whether a runtime reference check is required, and primitive conversion/unboxing remain separate steps.[6]

Assignment-location evaluation separates target evaluation from value conversion and storage. Array target checks, field receiver effects, short-circuit branches, conditional values, string concatenation, switch-expression yields, and exception transfers retain their own ordering and value relationships. An unsupported decision or unavailable required semantic fact blocks the graph rather than silently approximating it.[6]

Exception edges describe the modeled routes for the accepted operations and bounded handler profile. They do not establish arbitrary nested-handler coverage, successful resource cleanup, or every possible JVM/runtime outcome. See [CFG admission, construction, and result](#cfg-admission-construction-and-result) for the governing contract.

## Verification and limits

Source inspection and diagrams describe contracts; executable evidence comes from the relevant unit, packaged-process, and Community database controls. Use the existing [build targets](../build-quality.md#commands). A documentation revision does not constitute a new runtime result.[7]

No analyzed program execution, annotation processing, bytecode input, whole-program analysis or universal Java 25 completeness is claimed. Explicit retention and query preserve independently complete results in the [active retained format](ast-value-format.md). Directly supplied values retain their assurance limits. Source admission derives source qualification, CFG owns derivation qualification, and application owns request and publication decisions.[1][2][3]

## Separation decision

**Target design decision:** retain Java frontend production and CFG construction as distinct contexts, redefine the existing syntax/declaration responsibilities as one independently consumable **Source Model**, extract the narrowly defined **Source Identity** value contract, and move request qualification into **Analysis Application**. Retain **Analysis Repository** as a storage adapter context. Split the mixed `model` package by responsibility; do not introduce an intermediate AST, normalization context, deployment service, or release boundary.

The six logical contexts are Source Identity, Source Model, Java Frontend, Control Flow, Analysis Application, and Analysis Repository. A context is a cohesive owner of concepts, invariants, and contracts, not an assertion that a Java package or Maven artifact already implements it. CLI remains an adapter to application contracts; the composition root wires implementations. The package and compilation-partition boundaries below are implemented; their configured gates and current execution limits are recorded separately from independent review acceptance.

### Responsibility boundaries

The retained responsibilities and their current source entry points are:

| Retained obligation | Current source locator | Required behavior |
| --- | --- | --- |
| Frontend returns without deriving CFG. | `AstProducer.produce`; `SourceComputation.analyze`.[1][8] | The producer returns an independently admitted `AstOutcome`; computation invokes `CfgDeriver` only when requested. |
| Body admission is consumer-specific. | `SourceRequirements.assess`; `CfgAdmission.assess`.[9] | Source qualification contains no readiness/graph registry; CFG owns its blockers and profile policy. |
| One aggregate retains source bodies and five fact families. | `JavaAstAggregate`; `MethodSyntax.evidence`; `AstValueAdmission`.[3][9] | Required source facts and optional compiler classifications remain separate immutable slots. |
| Completion belongs to each context. | `AnalysisQualification.assess`; `CompletedRunPublicationEligibility.accepts`.[1] | Independent source/CFG/request completion and the legacy full-body publication gate are distinct. |
| Results retain the source aggregate independently of graphs. | `ProgramRepresentation`; `ExistingSourceDerivation`; `AstRetention`; `AstQuery`.[1][3] | Existing-source derivation and explicit AST retention/query require no new source production. |
| Completed-run publication remains explicit. | `PublicationCoordinator.apply`; `Neo4jAnalysisRunMapper.map`.[1][10] | Completed-run eligibility, CLI presentation, and storage replacement retain distinct owners; qualified run identity follows the current application contract. |

`TranslationResult.emissions` carries immutable `DiagnosticEmissions`; `DiagnosticEmissionCollector` allocates creating-site addresses and combines returned batches without reassigning them. Equal diagnostic values remain separate events when their addresses differ. Translation results contain the produced value and diagnostics only: tree/type observations and declaration-property encounters come from their dedicated observers, and `JavaSourceCoverageReconciler` checks them against independently captured obligations. The [roadmap](../roadmap.md) routes current expansion acceptance to its owning checkpoints.[2][5][11]

### Compared decompositions

These are design judgments against the same criteria, not a claim of a uniquely optimal decomposition or a prescription from UML or a design book.

| Criterion | One AST context including producer | Separate Source Model and Java Frontend (selected) | Separate normalization/semantic bridge |
| --- | --- | --- | --- |
| Cohesion and ownership | Can cohere around Java source, but mixes compiler resource ownership with immutable result ownership. | Producer owns compiler acquisition/corroboration; model owns retained source facts and intrinsic validity. | Would need a new identified semantic responsibility; none is required by this inventory. |
| Dependency closure | Defensible only with a physically enforced public value partition. | Source Model API closes over itself, Source Identity, and Java base; frontend internals are unreachable. | Risks duplicating AST/types/evidence or exporting the same producer internals. |
| Public contracts and independent consumption | Possible with exported values, private compiler code, and no CFG readiness. | `AstProducer.produce` and `AstResult` are distinct; CFG and storage use only the latter. | An unexplained transformation contract provides no extra acceptance benefit. |
| Lifecycle and retention | Must separate result lifetime from resource lifetime within one context. | Compiler closes before production return; immutable AST outlives it and crosses storage independently. | Adds lifecycle/identity crossings without an identified requirement. |
| Compatibility and enforcement | Fewer context boundaries but still requires all completion/result/schema migrations. | Requires ownership/package migration and import/export gates; preserves source identity algorithms. | Adds compatibility work and additional enforcement obligations. |

The selected separation is justified by existing consumer-neutral data and private compiler machinery, plus the requirement for later AST consumption without that machinery. A combined AST context is a valid alternative if its export boundary is enforced; the selected model makes that boundary an explicit architectural context. The bridge alternative is rejected for this task because it lacks an independent responsibility. Responsibility allocation and interactions inform the design (Wazlawick §9.1–9.2); the specific Aquila decomposition is our decision.[12]

## Target ownership and contracts

| Context | Owned concepts and invariants | Provided contracts | Required contracts |
| --- | --- | --- | --- |
| Source Identity (`identity.api`) | Captured sources, environment/configuration/dependencies, source spans, framed identifiers, `SoftwareVersion`; canonical ordering and supplied-value assurance limits. | Immutable values/factories; `AstIdentity` binding and qualified source references. | Java base domain/operational dependencies, with the designated nullness metadata below. No declarations, syntax, graph kinds, or completion. |
| Source Model (`source.api`, qualified `source.admission` / `source.retention`, private `source.internal`) | `AstOutcome`, `AstResult`, one `JavaAstAggregate`, declarations, occurrence containment, source/type/annotation facts, coverage, AST diagnostics, semantic evidence, intrinsic validity. | `AstProducer.produce(AstProductionRequest): AstOutcome`; immutable `AstResult` queries; copied-value admission to frontend; retained-value codec to repository. | Source Identity and Java base. |
| Java Frontend (`frontend`) | Discovery, compiler/session/witness resources, written and attributed inventories, exact compiler-path correlation, source translation, evidence production and diagnostic events. | Realizes `AstProducer`; returns only Source Model API values. | Source Model read/value-admission API and Source Identity; compiler APIs remain private. |
| Control Flow (`cfg.api`, private `cfg.internal`) | CFG profile/eligibility, evidence requirements, lowering plans, graphs/nodes/values/transfers, graph diagnostics and validation. | `CfgDeriver.derive(AstResult, CfgRequest): CfgOutcome`; graph queries. | Source Model read API and Source Identity. No frontend or builder access. |
| Analysis Application (`application`, `application.port`, qualified internal repository support) | Request selection, independent computation/derivation, `AnalysisRun`/`ProgramRepresentation` references, overall qualification, publication intent/status, retention/query, and generic failure contracts. | `AnalysisCapabilities` composes `AnalysisComputation.analyze`, `ExistingSourceDerivation.deriveExisting`, `AstRetention.retainAst`, `AstQuery.findAst`, and `AnalysisUseCase.execute`. | Source Model production/read API, CFG API, Source Identity, application-owned storage ports; no concrete frontend, CLI, or repository dependency. |
| Analysis Repository (`repository.neo4j`) | Mapping, schema compatibility, immutable projection, exact constraint inspection, transactions, decoding, storage resources. | Realizes application-owned completed-run and AST retention/query ports. | Public application storage contracts, Source Model, CFG, Source Identity, driver API. No producer or lowerer internals. |

`AstProducer` is owned by Source Model because it specifies production of that domain result, including profile qualification; the frontend implements it. Source Model does not call or import the frontend. Public value contracts are legitimate dependencies: a CFG's reference to a source declaration/type does not by itself blur ownership. Immutable values can be shared by outcomes and readers; UML composition is reserved for exclusively graph-owned nodes/values/transfers, not shared AST results.[13]

### AST result and intrinsic validity

`AstProductionRequest` supplies captured input or a source root, compiler configuration, and an explicit `AstProfile`. A returned `AstOutcome` contains that declared profile, input/environment provenance when captured, ordered AST diagnostics, coverage qualification, a completion state, and zero or one `AstResult`. An available result and its outcome share the same profile/qualification evidence; unavailable outcomes retain diagnosed coverage gaps without fabricating an aggregate. `AstResult` contains `AstIdentity`, canonical `JavaProgram`, the single sealed `JavaAstAggregate`, semantic evidence, and profile/coverage metadata. It retains no compiler tree, mirror, path, file manager, callback into a session, or CFG value.

The AST owns written occurrences and containment. Declarations own canonical
symbols and source ownership. Semantic edges distinguish owned occurrences
from qualified external declaration references. The aggregate retains bodyless
declarations without body roots. A published written body reuses its owned
source occurrence. A written body withheld for a missing position can retain
an auxiliary execution registration with no written body occurrence.
The [body origin contract](ast-value-format.md#method-body-origin) defines
that provenance and the available references. Compiler-inserted execution
facts keep explicit provenance outside written source-node queries. CFG
lowering plans own evaluation topology.[3][19]

**Intrinsic validity** requires unique qualified occurrence identities; source membership and bounded same-source ranges; acyclic, closed containment; contiguous role-local positions; root order matching the snapshot; exact declaration/body association; valid typed semantic endpoints; consistent supplied facts; and independent coverage/event-slot correlations. Sealing rejects foreign, duplicate, contradictory, fabricated, or structurally invalid values (`AstContractException` for public admission). A failed seal exposes no valid aggregate; its builder is abandoned. Missing or erroneous attribution can be represented by typed evidence states and does not itself violate these structural invariants. Compiler semantic errors do prevent complete qualification. Direct construction validates consistency, not authenticity; real compiler correlation remains the producer's duty.

The declared AST profile owns required source forms, descendant roles, declaration surfaces, written type/annotation structure, provenance, and required semantic evidence. The initial target profile is `java-source-bounded/1`, defined below by source representation and attribution obligations. It does **not** promise all Java 25 forms. A new source form enters the profile only with explicit ownership/coverage/evidence obligations and a changed profile version. CFG policy cannot narrow or expand that source profile.

| AST completion | Result and qualification |
| --- | --- |
| `COMPLETE` | Intrinsically valid result; every required source context in the declared profile is represented and independently reconciled; no AST error; every AST-profile-required semantic fact available. Empty capture remains an unavailable analysis outcome, not a completed product. |
| `INCOMPLETE` | Intrinsically valid, queryable result with explicit supported retained facts and source/evidence gaps, unsupported source-profile forms, or compiler errors. Accounting for a diagnosed gap does not make the source complete. |
| `UNAVAILABLE` | No valid aggregate can be established, including empty/no accepted declarations or a withheld required root. Captured provenance and truthful diagnostics remain available when there is a normal diagnosed return. |

For the initial migration, preserve current whole-root withholding when a required unit/declaration root cannot be correlated; do not add partial-root publication as an incidental change. Within an established aggregate, typed gaps remain observable. Missing evidence optional to the AST profile can coexist with `COMPLETE`; missing evidence required by that profile yields `INCOMPLETE`. Neither rule mentions a graph, its profile, or whether a lowerer runs.

### Initial source profile

**Target design decision:** `java-source-bounded/1` is a non-preview Java 25 source profile. `SourceRequirements` is its closed, versioned source-form/role and fact-slot policy, not a call into a graph support registry. The following tables retain initial-profile qualification; the [roadmap](../roadmap.md#source-model-delivery-status) links subsequent accepted expanded-profile delivery without changing this historical profile. The initial migration's projection/profile and five decision producers are migration evidence, not authority to classify all their former graph restrictions as source invalidity.[4][6][9]

| Source surface | Required representation and disposition of the current restriction |
| --- | --- |
| Units, packages, imports, modules, class/interface/enum/record/annotation declarations, members and executable headers | Retain canonical declarations, written versus synthesized ownership, hierarchy/generic/parameter order, recursive type structure, bodyless declarations, source positions where present, and independently captured coverage. Existing uncorrelated required-root withholding remains `UNAVAILABLE`. |
| Written type uses and annotations | Retain actual written type/argument roles, exact declaration/type sites, explicit annotation pairs/values and separate defaults, recursive arrays/nested annotations, record propagation and independent mirror correlations under the retained obligations below. Any actual capability gap remains diagnosed and prevents `COMPLETE`; paused annotation reassessment is not a new claim of universal support. |
| Blocks, local declarations, expression statements, return, throw, if/else, try/catch and yield | Retain ordered actual statements, declaration/type/initializer roles, optional written return value, mandatory throw/value and conditional/body roles, and actual try bodies/catch formals/bodies. Nested represented try/catch structure is source structure; handler-lowering restrictions are exclusively CFG policy. Resources/finally remain outside this initial source representation profile. |
| Arrow switch expressions | Retain selector, ordered actual cases, default/literal/enum constant labels, expression/block bodies and explicit yield roles. Missing required selected enum labels/types are source gaps. Switch statements, colon cases, patterns, null labels and guards remain outside the initial representation profile. A compatible CFG profile may independently refuse represented switches. |
| Calls, nonanonymous object creation, member/array access and length, identifiers, literals, parentheses, casts, simple assignment, conditional expressions | Retain their actual written qualifiers, enclosing expression, target, index, arguments, cast/local types and branch roles, with optional source children distinguished from missing required ones. Simple assignment is a source form; value-versus-location selection and restrictions on reached evaluation are removed from source qualification and belong to CFG. |
| Unary and binary expressions | Initial unary forms are unary plus, unary minus, logical complement and bitwise complement. Initial binary forms are multiplication/division/remainder, addition/subtraction, shifts, ordered/equality comparisons, bitwise AND/XOR/OR and conditional AND/OR. Preserve operator spelling and ordered operands without inserting conversion/evaluation nodes. Increment/decrement and compound assignment remain outside the initial representation profile. |
| Remaining current exclusions | Loops, labels/break/continue, assert, synchronization, lambdas, method references, anonymous class bodies and broader pattern forms remain diagnosed source-profile gaps because complete source roles/evidence are not specified by this initial bounded profile. Field/initializer-block execution and enum constant-specific bodies retain their existing capability gaps; declarations remain facts, while complete initialization representation requires the outstanding ownership work. These exclusions are representational scope decisions, not claims that Java rejects the forms or that no future CFG can derive them. |

Within an otherwise established aggregate, an outside-profile form retains its truthful source-kind/range/provenance and an explicit gap; available supported facts remain queryable. Do not manufacture absent descendants or report that a diagnosed unsupported subtree is completely represented. Required retained roles are reconciled against independent written expectations. Source-only type/annotation children cannot trigger a CFG-unsupported AST diagnostic. Empty capture retains initial provenance and `UNAVAILABLE`, without compiler acquisition or post-work recapture; nonempty production requires normal work, owned closure and unchanged-environment verification before normal qualification.

Every applicable required source slot must be `KNOWN` with a consistent value. `NOT_APPLICABLE` is permitted only when its source variant has no such obligation, such as no explicit receiver or a bodyless declaration; it cannot replace missing attribution. Required `MISSING` or `ERRONEOUS` slots yield `INCOMPLETE`. Optional slots remain independently keyed and truthfully accounted in coverage; `MISSING` does not erase known required slots. Any actual compiler/source error still prevents `COMPLETE`, including an error attached to optional evidence; optionality does not authorize downgrading diagnostics.

| Selected-fact family | AST-required slots in `java-source-bounded/1` | Optional AST evidence | Initial CFG prerequisite when the corresponding operation is reached |
| --- | --- | --- | --- |
| Use/conversion | Attributed source-expression type, written target type or selected assignment/parameter/return target where applicable; exact owned use/child position and source/target consistency. | Selected conversion-step classification, checked wrapper target and runtime reference-cast-check classification, including known false. | Known conversion sequence and any required wrapper/cast-check classification; missing optional classification refuses CFG, not an otherwise complete AST. |
| Invocation | Selected method or constructor reference, invocation semantics, written argument order and receiver or enclosing roles where applicable, and the selected target type for each argument. | The independent applicability slot, including fixed or variable mode, formal count, and variable-arity array, erased array and element types. | Known applicability when the call form requires it and use facts for applicable arguments. CFG owns operand order, allocation, and packing. |
| Static member | Selected member with its declaring owner, qualifying source type where applicable, and access kind. | The independent constant-variable classification slot for field reads. | Known declaring owner and, for field reads, known constant-variable classification. CFG derives initialization effects. |
| String expression | Attributed result and operand types, PLUS, and operand roles. | Separate operand String classifications and compile-time constant classification. | Known classification slots for the reached String concatenation. |
| Enum constructor | Canonical containing enum/constructor, selected written delegation target/form when present, actual ordered source arguments, and provenance for every retained compiler-inserted fact. | Corroborated inserted superclass invocation context beyond written declarations/arguments. It remains typed unavailable if not established, without fabricating source arguments. | Known inserted-call facts required for the enum binding plan. Incoming name/ordinal values, one binding consumer and arity adaptation are CFG decisions. |

Required types, selected references and annotation/type structure are qualified
from the available source facts. For example, a known call target with
complete required source facts can yield AST `COMPLETE` when applicability is
missing. The CFG profile can return `INSUFFICIENT_EVIDENCE` when it requires
that applicability. A missing selected call target or required argument
target makes the AST `INCOMPLETE`. Missing reference-cast classification
can also refuse CFG while the source stays complete. Contradictory supplied
known facts reject intrinsic admission. The [evidence ownership table](#semantic-evidence-and-cfg-policy)
identifies the current types for these facts.[6][9]

### Qualified construction and enclosing type composition

Semantic production `javac-public/14` preserves two distinct contracts: selecting the
constructor in the original attributed environment, and retaining the recursive type
of its actual enclosing instance. A generic field declaration is not its substituted
member type. Capturing a field's result after substitution also cannot recover a capture
already lost inside a nested type argument or array. The compiler operations and language
rules are specified by
[`Types.asMemberOf`](https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/util/Types.html#asMemberOf(javax.lang.model.type.DeclaredType,javax.lang.model.element.Element)),
[capture conversion](https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.10), and
[class instance creation](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.9).

When the original instantiated constructor signature has no unresolved method variables,
[`ConstructorCorrelationPreparer`](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/ConstructorCorrelationPreparer.java)
uses that signature directly.
[`ConstructorWitnessCorrelation.attributedSelection`](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/ConstructorWitnessCorrelation.java)
copies its applicability and conversions with the original enclosing type variables.
Otherwise, the private witness uses the original enclosing expression through an instance
receiver projection on the lexical enclosing declaration. This preserves compiler capture
and lexical method variables without rendering captured types as source names. Source and
binary declarations use the same projection contract. Constructor arguments retain strict
source, annotation and capture correspondence. Receiver comparison uses capture-converted
Java types in an independent comparison scope; constructor-result comparison uses Java type
structure with its original capture identities. These semantic comparisons do not require
type-use annotations to survive method substitution. They cannot populate the strict
annotation-comparison cache. Original annotation occurrences remain retained source facts.

For a qualified anonymous creation, the proof branch is inserted into the enclosing
expression and completes abruptly; the original qualifier is its only normally completing
branch. The original creation, arguments, anonymous body, target context and statement-expression
form remain in place. Proof arguments and original arguments cannot both complete on one
path. Witness declarations and dependency overlays belong only to the private compiler
acquisition; they do not become retained source or mutate dependency bytes.
When the copied qualifier itself owns a declaration, its witness insertions are composed
inside that copy before replacing the outer occurrence. Correlation checks the exact
projected text and maps its local declarations independently of compiler binary ordinals.
Restoring a copied nominal type preserves its actual arguments; semantic capture mappings
cannot copy unrelated class-argument annotations into a selected constructor target.

The current [retained format](ast-value-format.md) preserves `Declared.enclosingOwnerType`. `enclosingType`
continues to hold the original instance, including a variable or captured variable. The
optional field holds its parametrized lexical-owner view, with an independent complete
capture scope. It cannot override a directly declared instance or identify another lexical
owner. Aggregate admission checks the written qualifier and available source bounds, including
inherited substitutions and capture renumbering. Unavailable external hierarchy facts remain
unavailable; this intrinsic check does not reconstruct a compiler or prove an absent classpath.
The representation and admission owners are
[`JavaTypeStructure`](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/api/JavaTypeStructure.java) and
[`EnclosingTypeAdmission`](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/internal/EnclosingTypeAdmission.java).

The existing source profiles retain their meanings under the current retained
format. Prior envelopes are refused under the
[retired-format policy](ast-value-format.md#read-admission-and-retired-formats).
Readmission checks the captured enclosing-owner view and its identity.
Public producer, supplied-value and retention obligations are exercised by
[`EnclosingConstructorCompositionTest`](../../src/test/java/org/aquilagraph/frontend/internal/EnclosingConstructorCompositionTest.java),
[`EnclosingTypeAdmissionTest`](../../src/test/java/org/aquilagraph/frontend/internal/EnclosingTypeAdmissionTest.java),
[`QualifiedConstructorCompositionTest`](../../src/test/java/org/aquilagraph/frontend/internal/QualifiedConstructorCompositionTest.java), and
[`HistoricalAstCompatibilityTest`](../../src/test/java/org/aquilagraph/repository/internal/neo4j/HistoricalAstCompatibilityTest.java).

#### Outstanding composition requirements

The following boundaries remain unresolved in semantic production `javac-public/14`.
They are current requirements, not completed capabilities. Existing isolated test success
does not establish their completion.

- **Anonymous enclosing owners with diamond.** Valid construction through an anonymous
  owner created with diamond must retain constructor applicability and argument conversions,
  including when the constructed member is itself anonymous. For example,
  `new Parent<>() { class Box { <T> Box(T value) {} } }.new Box(1)` currently reports
  `frontend.constructor-witness-rejected`; an explicit `Parent<String>` owner completes.
  [Receiver projection](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/ConstructorReceiverProjection.java)
  emits a public method into that owner. Its declaration must satisfy the enclosing
  context's language rules while preserving the original receiver and inference context.
  [JLS 25 section 15.9.5](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.9.5)
  requires non-private methods declared in a diamond anonymous class to override a supertype
  method. The existing
  [composition regressions](../../src/test/java/org/aquilagraph/frontend/internal/EnclosingConstructorCompositionTest.java)
  cover inline anonymous owners without diamond on the owner.

- **Parameterized ancestors in binary enclosing chains.** Source and binary declarations
  must preserve the complete enclosing chain through intermediate types with no type
  parameters of their own. With `Outer<A>.Middle.Box`, where `Middle` is nongeneric and
  `Box` has a generic constructor, `middle.new Box(1)` currently completes with source
  declarations but reports `frontend.missing-constructor-correlation` with binary
  declarations. Ordinary and anonymous member constructions reproduce this difference
  with `Outer<Number>.Middle`; original dependency bytes remain unchanged.
  [Binary signature rendering](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/ConstructorTypeRenderer.java)
  must retain the parameterized ancestor even when the immediate enclosing type has no
  arguments. The current
  [binary receiver regression](../../src/test/java/org/aquilagraph/frontend/internal/QualifiedReceiverProjectionTest.java)
  exercises only one enclosing level.

- **Raw source inheritance during admission.** Available raw source hierarchy must have a
  defined erased owner view. For `Child<A> extends Outer<A>` and `<O extends Child>`,
  public aggregate admission currently accepts replacing the raw `Outer` owner view
  with `Outer<String>`. The same replacement under `Child<Integer>` rejects.
  [Enclosing type admission](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/internal/EnclosingTypeAdmission.java)
  must distinguish raw inheritance, parameterized substitution, and unavailable hierarchy.
  A parameter-count mismatch for an otherwise known raw source declaration cannot
  convert its determined superclass into unknown evidence.
  [JLS 25 section 4.8](https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.8)
  defines the erased supertypes of a raw type. The existing
  [owner admission regressions](../../src/test/java/org/aquilagraph/frontend/internal/EnclosingTypeAdmissionTest.java)
  cover parameterized inherited bounds.

- **Every retained recursive type carrier.** Enclosing-owner consistency must apply to
  types stored within syntax values as well as each node's own type and declared bounds.
  For `supply(() -> outer.new Box(1))` under `<O extends Outer<Integer>>`, public
  aggregate admission currently accepts changing only
  [`SyntaxValue.Lambda.returnType`](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/api/SyntaxValue.java)
  to carry owner `Outer<String>`. The node type, creation type, source bounds, children,
  and execution evidence remain unchanged. The shared admission traversal must reach
  all applicable recursive type carriers and preserve their independent capture scopes.
  This observation establishes an aggregate-admission gap; it does not establish
  acceptance by every later result or persistence boundary.
  [Lambda regressions](../../src/test/java/org/aquilagraph/frontend/internal/LambdaAstTest.java)
  already distinguish unavailable descriptor return types, but do not reject this
  contradictory resolved enclosing-owner view.

These construction and structural composition obligations require shared contract corrections and discriminating public producer or supplied-value
regressions. Rejecting valid raw inputs, omitting retained types, weakening diagnostics,
or adding spelling-specific exceptions cannot satisfy them.

### Module service and provider identities

**Profile contract.** Resolved service and provider declaration identities are required source facts for `COMPLETE` in `java-source-expanded/8`. Their qualification belongs to Source. The [retained format guide](ast-value-format.md#format-source-profile-and-producer-revision) owns current encoding and production provenance.[16][18][20]

The delivered profile must preserve both the written operands and their compiler-attributed targets in the captured compilation environment:

| Written occurrence | Required retained target |
| --- | --- |
| Service operand of `uses` | Identity of the service type denoted by `ModuleElement.UsesDirective.getService()`. |
| Service operand of `provides` | Identity of the service type denoted by `ModuleElement.ProvidesDirective.getService()`. |
| Each provider operand in `with` | Identity of the corresponding declared provider type denoted by `ModuleElement.ProvidesDirective.getImplementations()`. |

Each target remains associated with its exact written occurrence, source origin, role, and provider position. Preserve written spelling and provider order independently of compiler enumeration order; a resolved target must not replace the source name or reorder its siblings. Imported/simple and qualified names must refer to the actual selected declaration rather than a declaration guessed from spelling. Modular compilation units permit imports and simple names inside directives.[16]

Identities must distinguish declarations in the captured modular environment, including distinct types with the same canonical name in different modules. Reuse the project's qualified identity contract where it expresses that distinction; any necessary extension requires explicit compatibility treatment. A name string alone must not claim an unambiguous declaration identity. External targets remain references, not fabricated owned declarations or source occurrences. Public results expose immutable Source Model values, never live compiler elements, trees, mirrors, paths, or callbacks.[17][18]

The provider target is the type named in `with`. A provider with a `provider()` method need not itself be a subtype of the service; the reference must not be replaced by that method's return type or interpreted as a Java `implements` relationship. Retaining the declared target does not require invoking the factory or predicting the concrete runtime object.[16]

For `java-source-expanded/8`, every applicable service/provider identity is required for `COMPLETE`. A missing or erroneous target prevents `COMPLETE`; an intrinsically valid aggregate remains queryable as `INCOMPLETE` with its available facts and truthful diagnostics. Existing required-root withholding and abrupt-failure rules remain unchanged. Foreign, contradictory, fabricated, or inconsistent supplied references fail intrinsic admission. Absence of a directive creates no corresponding obligation; a present but unresolved operand cannot be relabeled `NOT_APPLICABLE`. Qualification is source-owned and independent of CFG selection, admission, construction, or success.[18][20]

Queries after compiler closure must preserve the same target identities, written associations, ordering, and evidence states. Applicable codec and actual completed-AST save/query paths must preserve those values and their qualification. Existing opt-in publication and incomplete-AST ineligibility remain in force; no new CLI behavior, automatic saving, or storage backend follows from this decision.[18]

**Profile and format history.** This capability was introduced with `javac-public/11` and `java-source/20`. All supported source profiles keep their declared qualification boundaries in the active format. Stored envelopes from the earlier delivery keep their bytes and identities and follow the [retired-format lookup policy](ast-value-format.md#read-admission-and-retired-formats). Reproduction is a new analysis with its own qualified identity. Existing profile defaults and CLI behavior remain in force.[18][19][20]

**Excluded scope:** This capability does not discover all possible providers, choose a provider for a consumer, execute `ServiceLoader` or `provider()`, infer instantiated runtime classes, index `META-INF/services`, expand CFG coverage, or introduce whole-program analysis. It retains the attributed declarations corresponding to the written module operands.[18]

**Verification obligations.** Verification covers exact public targets and associations for imported, simple and qualified operands, module identity distinctions, written provider ordering, constructor-based and provider-method forms, truthful required-fact failures, compiler-free queries, active codec and retention preservation, and refusal of retired formats. Prior acceptance retains its recorded scope. The [development verification boundary](../build-quality.md#development-verification-scope) governs new work and reserves real-environment verification for an explicit decision.[18][19][20]

### Semantic evidence and CFG policy

Each family is split by meaning. A slot retains an
[`EvidenceValue<T>`](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/api/EvidenceValue.java)
in state `KNOWN`, `MISSING`, `ERRONEOUS`, or `NOT_APPLICABLE`, with creating-site
provenance. Only `KNOWN` carries a value. An `ERRONEOUS` slot can retain its
originating frontend error if one is available. Optional components of a known
payload follow that fact's variant. Independently corroborated facts have
separate slots, so a missing selection can coexist with a known source type or
constant classification. Contradictory known facts reject intrinsic admission.
The [retained evidence contract](ast-value-format.md#executable-evidence)
describes the owning execution scopes, occurrence keys and direct-child roles.

| Fact family | Current Source Model owners | CFG-owned decisions and prerequisites |
| --- | --- | --- |
| Child use | `ChildUseEvidence` retains source and selected target types, a `ConversionEvidence` slot, and a separate reference-check slot. The conversion payload carries the selected checked wrapper, unboxing, widening and boxing type components where applicable. | `CfgAdmission` determines required evidence. `UseConversionLowerer` uses the facts to construct casts, unboxing, boxing, and associated effects. |
| Invocation | The syntax occurrence retains its selected semantic reference and its invocation value with `InvocationSemantics` for the mode, qualifier and form. `InvocationEvidence` retains only its independent applicability slot. Known `InvocationApplicability` retains fixed/variable mode, formal count and applicable array, erased-array and element types. Argument target types belong to the corresponding `ChildUseEvidence`. | CFG checks the call facts and owns operand order and varargs allocation, packing, and stores. |
| Static member | `StaticMemberEvidence` retains the selected member and access kind, with independent declaring-owner and constant-variable slots. The qualifying type and declaring owner can differ. | CFG chooses initialization effects, placement, and exceptional routes. A required declaring-owner gap affects source qualification, and an optional constant-classification gap can leave source complete while CFG refuses the body. |
| String expression | `StringExpressionEvidence` retains the attributed String result type and separate left-operand, right-operand, and constant-expression facts. | `StringOperandPlan` derives the converted side using admitted operand facts. The lowerer owns conversion and concatenation steps and their order. |
| Constructor context | `ConstructorEvidence` retains the owning type, selected constructor and delegation form. Its independent `ConstructorContext` corroboration carries the superclass parameter types and any compiler-inserted invocation provenance. Written arguments remain syntax children. | `EnumBindingPlan` supplies the runtime name/ordinal bindings. CFG owns incoming values, context operand order, and inserted-call adaptation. |

`ExecutionDecisionValidation.validate` checks owned slots and correlations with
the selected references, declarations, and actual child occurrences. Source
value constructors check conversion shape and mutually exclusive components.
`SourceRequirements` decides which evidence gaps affect the source profile.
`CfgAdmission` separately checks evidence and construct support for the
selected body, including the supported cardinality of incoming enum binding
consumers. [Source fact and CFG references](#references) identify these owners,
and the [format guide](ast-value-format.md#executable-evidence) owns the ordered
retained fields.[6][9]

The Source module exports qualified entry points for two consumers. The frontend
finalizes copied facts through
[`AstProductionAdmission.outcome`](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/admission/AstProductionAdmission.java),
which takes `SyntaxAnalysisInput`, `AstProfile`, and the semantic-production
revision and returns an `AstOutcome`. The repository restores available results
through [`AstCodec.decode`](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/retention/AstCodec.java),
which takes payload bytes and the expected `AstIdentity` and returns an
`AstResult`. Source owns the shared qualification and canonical identity
derivation. The codec performs internal readmission and rejects inconsistent
values, identity, or completion with `AstContractException`. CFG consumes the
public `AstResult` and its queries.[16][19]

The uniform `AstProducer.produce(AstProductionRequest)` port uses the Source
Model's `AnalysisInputException` and `AnalysisEnvironmentException`. Its
captured-snapshot branch translates environment `IOException` into
`AnalysisEnvironmentException`. The direct
`JavaFrontend.produce(snapshot, configuration, profile)` overload retains
checked `IOException`. `JavaFrontend` owns the path and snapshot failure
translation and associated cause and cleanup evidence.[2][8]

### CFG admission, construction, and result

`CfgRequest` names the exact `AstIdentity`, ordered selected canonical method keys, and `CfgProfile` (including its graph-model version). Unknown/duplicate keys, foreign identity, and incompatible source-model version/release/profile return request-level `REJECTED` with distinct diagnostics and no graphs. The input is an immutable intrinsically admitted `AstResult`; malformed copied values are rejected by Source Model admission before they become that input. CFG checks compatibility and graph/source associations rather than rerunning a private source validator. No opportunistic key-only rebinding is allowed. An incompatible profile differs from an unsupported construct within a compatible profile.

`CfgAdmission.assess(ast, methodKey, profile): CfgAdmissionResult` checks intrinsic AST compatibility and the selected method's retained source/evidence. It has no producer, compiler, or builder callback. It decides applicability before construction; it reads the AST and produces a CFG-owned immutable `LoweringPlan` only for admitted bodies. Bodyless declarations return `NOT_APPLICABLE`. A source-profile gap yields `SOURCE_UNAVAILABLE`; a represented construct outside the CFG profile yields `UNSUPPORTED`; absent required CFG evidence yields `INSUFFICIENT_EVIDENCE`; erroneous required evidence yields `ERRONEOUS_EVIDENCE`. Report all observed blockers in deterministic source order, preserving their distinct causes, rather than hiding them behind one readiness precedence. Contradictory evidence is invalid input, not missing evidence.

Every refused method retains `blockers: List<CfgBlocker>` with each observed kind, qualified slot/occurrence and diagnostic. If several kinds coexist, the summary state is the first present kind in `SOURCE_UNAVAILABLE`, `UNSUPPORTED`, `ERRONEOUS_EVIDENCE`, `INSUFFICIENT_EVIDENCE` order; clients can inspect all blockers. This summary never discards causes or changes AST validity. Do not diagnose hypothetical evidence from an unavailable subtree. Admitted/bodyless outcomes have no blockers; `AVAILABLE` alone carries a graph, and `FAILED` alone carries a construction failure. CFG request `PARTIAL` includes refused or failed methods; application qualification distinguishes construction failure from refusal.

Every selected-declaration outcome retains exact `AstIdentity` and canonical method key. Its `BodyRef` is optional: `NOT_APPLICABLE` has none, and source-unavailable outcomes cannot invent body provenance. An `AVAILABLE` graph requires the exact retained body reference; construction failure after admission retains its admitted body. Request/profile identity remains present on `CfgOutcome` even for rejection or an empty selection.

Source-only children remain queryable in AST, but CFG traversal selects executable roles explicitly and does not treat type-use/annotation children as unsupported runtime instructions. The plan contains graph-specific conversions, evaluation steps and implicit constructor context with qualified source/provenance references. It is temporary construction state, not a second source model or a retained normalization IR.

`ControlFlowGraphBuilder.build(ast, plan): MethodCfgOutcome` uses the admitted immutable plan; plan identity, profile and canonical method must match the supplied AST. CFG owns the construction obligation and validates one entry/exit, graph-owned unique nodes/values/edges, full operand/definition membership, owned edge endpoints, contiguous operand order, target-tagged yields, selected invocation fields only on INVOKE/NEW_OBJECT, and every graph-to-source reference. A graph is exposed only after validation.

| Per-declaration state | Meaning |
| --- | --- |
| `NOT_REQUESTED` | No derivation selected for this body; synthesized only in the application result coverage view, never mistaken for a missing graph. |
| `NOT_APPLICABLE` | Selected canonical declaration has no body; declaration remains available and no graph is required. |
| `AVAILABLE` | Admitted construction produced a validated graph for the exact source identity and profile. |
| `SOURCE_UNAVAILABLE` / `UNSUPPORTED` / `INSUFFICIENT_EVIDENCE` / `ERRONEOUS_EVIDENCE` | Distinct admission refusals; no graph or fabricated facts. |
| `FAILED` | Admitted construction failed through a declared recoverable construction failure; retain actual cause and CFG diagnostic, expose no invalid graph. |

`CfgOutcome` retains the exact immutable `CfgRequest`, including ordered selection, identity and profile, even for rejection or an empty selection. Identity/profile queries project that request; they are not independent mutable copies. Its request state is `REJECTED`, `COMPLETE`, or `PARTIAL`, with ordered per-selected-declaration outcomes and CFG diagnostics. A rejected request has no method graphs and reports its actual invalid selection; it does not silently normalize duplicate/unknown keys. `COMPLETE` means every selected body is `AVAILABLE`, every selected bodyless declaration is `NOT_APPLICABLE`, and no CFG error. An empty selection is a complete empty derivation request; it does not claim any graph was constructed. `PARTIAL` retains valid graphs from other methods. AST `INCOMPLETE` does not universally veto CFG: unaffected selected bodies may be admitted, but this never upgrades AST completion. `MethodSyntax` becomes the read-only body/fact view, with no readiness operation and no guarantee that all CFG-required facts exist.

### Public CFG values and queries

These are retained observable contracts of the former aggregate view, with graph ownership now qualified by `derivationKey`. Graphs defensively copy ordered operation, value and edge lists; all returned lists are immutable and contain no null elements. The public node variants remain entry, operation and exit. Every node, value and edge has its unique local key and owning graph key; that graph key equals the enclosing derivation key. `BodyRef` determines the canonical method and exact AST. Origins are ordered contributing `SourceSpan` values in that source identity; generated operations use explicit body/role provenance and never invent a written occurrence or span.

| Public value or operation | Retained contract |
| --- | --- |
| `ControlFlowGraph.operationNodes`, `values`, `edges` | Retain deterministic construction order; operation order is not an assertion of a single execution path. One entry and one exit are distinct from the operation list. |
| `nodes()` / `nodeCount()` | Return entry, operation nodes in their list order, then exit; count equals operation count plus two. |
| `successors(node)` / `predecessors(node)` | Require a nonnull node with this graph key; reject foreign graph keys as invalid arguments. Match the supplied graph/node keys, not Java object identity or display names. Follow stored edge order and retain one adjacency result per matching edge, including repeated endpoints for distinct transfers. A same-graph unknown node key has empty adjacency. Returned endpoints are this graph's canonical node values. |
| `FlowValue` | Unique value key, owning graph key, role and optional display name, literal representation and compiler-neutral static type. Roles are `PARAMETER`, `IMPLICIT_PARAMETER`, `LOCAL`, `TEMPORARY`, `CONSTANT`, `EXCEPTION`, `RECEIVER`, `SYMBOL`. Absence of optional attributes is explicit; no parser reconstructs type or semantics from display text. Implicit incoming values do not create AST formals. |
| `OperationNode` | Kind, uninterpreted human-readable `detail`, ordered operands with contiguous positions, optional defined owned value, ordered source semantic references, distinct possible-exception categories in their declared order, ordered origins, and exact `GraphSourceRef`. Operand/definition membership compares the full canonical value, not its key alone. |
| Selected invocation | Present exactly for `INVOKE` and `NEW_OBJECT`; absent for every other kind. Retain selected invocation mode/form, qualifying type and canonical selected target where available, separately from executable operand and enum-context binding order. It is a Source Model fact reference, not a second compiler selection. |
| `CfgEdge` | Unique edge key, owning graph key, owned source/target node keys, transfer kind and optional discriminator. Kinds remain `NORMAL`, `TRUE_BRANCH`, `FALSE_BRANCH`, `SWITCH_ALTERNATIVE`, `RETURN`, `EXCEPTIONAL`. Distinct possible transfers remain distinct edges; edges do not establish path feasibility. Exceptional discriminators name the originating category, not a catch type. |
| `CLASS_INITIALIZATION` payload | Exactly one resolved `TYPE` semantic reference: selected declaring-type key and nonblank erased name, with no type alternatives or inferred assignability list. No value operands, definition or invocation payload. The human-readable detail is not the selected target. |

The initial CFG operation-kind set remains `READ`, `RECEIVER`, `WRITE`, `CONSTANT`, `UNARY`, `BINARY`, `CAST`, `BOX`, `UNBOX`, `STRING_CONVERSION`, `STRING_CONCAT`, `ENCLOSING_INSTANCE_CHECK`, `CLASS_INITIALIZATION`, `NEW_ALLOCATION`, `ARRAY_TARGET_CHECK`, `VARARGS_ARRAY`, `ARRAY_ACCESS`, `ARRAY_LENGTH`, `MEMBER_ACCESS`, `INVOKE`, `NEW_OBJECT`, `DECISION`, `SWITCH_SELECTOR`, `MERGE`, `CATCH`, `RETURN`, `THROW`, `NO_OP`. A new kind requires a versioned graph contract, not interpretation of `detail`. Possible-exception categories retain the bounded semantics in the evaluation contract; their absence does not claim universal runtime immunity. Public supplied-value validation establishes internal consistency, not compiler authenticity or complete Java semantic coverage.[6][14]

The legacy mapper continues to require typed value attributes, node order, origins, semantic references, invocation fields and exception/discriminator payloads. Its adapter must project these retained fields under the legacy completed-run gate and legacy identity format; it may not infer them from descriptions or omit them when switching to qualified target values. Target graph storage remains a separate optional policy, with its versioned namespace; this contract does not choose automatic graph retention.[14]

### Retained source and resource obligations

The compact target views retain these detailed contracts from the superseded views. They are obligations, not a claim that every requirement is fully implemented. Current defining source remains in references 2–6; disagreements are classified in the baseline section.

- Declarations retain written versus synthesized provenance, type/member/parameter and type-owned generic identity, nested/local/anonymous lexical ownership, hierarchy/bound order and record component/accessor/field/canonical-formal distinctions. Canonical method association compares the complete declaration value, not key alone. External hierarchy/reference targets need not be owned declarations; opaque keys do not prove ownership by parsing.
- Occurrence roots follow snapshot order; written nodes/identity/range tuples follow structural preorder. Child edges retain role-local positions; semantic edges retain source/relation/typed-target ordering. Body-key set queries have no iteration-order promise; method-view lists follow canonical declaration order. Source occurrence lookup and nonwritten execution provenance never alias merely because local identifiers coincide.
- Recursive type variants preserve declared, local declared, variable, captured/capture-reference, array, wildcard, intersection and union distinctions. Wildcard upper/lower choices, ordered bounds/alternatives and enclosing lexical/instance types remain explicit. Capture binders have deterministic first-encounter ordinals with no unbound/duplicate/noncontiguous references; display/erasure text does not reconstruct missing structure. `NotApplicable`, `Unresolved`, `Erroneous`, `NoType`, `Unsupported`, `StructureUnavailable` and resolved types remain distinct evidence states.
- Annotations retain one physical written occurrence, written form and ordered explicit pairs/headers; explicit values and declaration defaults are separate. Recursive scalar/nested/array values preserve repetitions and empty arrays. Shorthand does not invent a name node; omitted defaults and synthesized repeatable containers do not become written use-site occurrences. Exact declaration/type sites, shared-field sibling order, record propagation and independently captured mirror obligations remain required. Explicit counterparts cannot be relabeled implicit.
- Written declaration/type sites remain source-only structure: creation/invocation arguments, cast targets and local types attach under their actual source roles. Diamond has no invented written type arguments, although separate attributed types may retain inferred arguments. Compiler insertion does not fabricate source ranges. Source/auxiliary provenance must be corroborated against the exact attributed executable and original tree/path/body instances before neutral return.
- Coverage is not derived from translated output. Preserve written and attributed expectation stages, source-owner/context addresses, complete known policy pairs, valid owner-bound absent/empty states, positive retained facts and diagnosed gaps/unmatched facts. Undeclared/ownerless/fabricated support rejects admission. Creating-site event identity/order survives nested transport; equal-valued diagnostics from different events stay distinct. Complete accounting of failures is not complete source qualification.
- Compiler session acquisition owns accessible resources until transfer. Managers track actual returned closeable loaders, close loaders in reverse encounter order then their delegate, attempt all cleanup and preserve effective primary/suppressed failures. Constructor witnesses use private in-memory source/binary evidence with original options and separately owned wrappers/delegates; borrowed original resources are not transferred. Exact owner/type/argument/selected-constructor correspondence precedes copied facts. Missing proof is unavailable; witness I/O/cleanup retains actual failure semantics. No compiler-internal APIs, generation stage, analyzed-program execution or annotation processing is introduced.

### Preserved evaluation obligations

The accepted CFG profile retains the current bounded semantic obligations; moving ownership does not relax them. LEFT precedes RIGHT; short-circuit branches skip unused evaluation and conversion effects. Selected CAST/UNBOX/BOX steps retain checked-reference classification, constant narrowing, primitive widening and reached-use placement. Switch BODY/YIELD values convert before their matching MERGE; target-tagged yields propagate without connecting to later unreachable statements, and nested switches consume their own yields.

Assignment location evaluation retains ARRAY, INDEX and selected index conversion, raw RHS, array target null/bounds check, selected RHS conversion, then WRITE without an old-value read; reference stores may have their own array-store effect. Field receiver effects remain at their declared use point. Static expression qualifiers are evaluated without consumption as instance receivers. Constant-variable reads retain member access/value flow while omitting initialization. Triggering reads initialize before access; writes after RHS conversion; calls after argument evaluation/packing. Creation checks any enclosing instance, initializes and allocates before arguments; the final constructor consumes the same allocated value. Qualified-super delegation checks its enclosing instance before arguments without allocating a new object.

Variable arity allocates the erased array before trailing argument expressions and stores each converted element in order, including zero trailing arguments. Enum context operands remain separate from source arguments and varargs counts. String conversion follows operand evaluation; compiler-proven constant expressions omit only their local conversion/concatenation effects. Integer division/remainder may contribute an arithmetic route; floating division/remainder does not add one of its own.

Exception routing distinguishes exact classes, subtype bounds, null possibilities and unknown categories. Ordered handlers remove only definitely handled possibilities; possible residual routes continue outward or to exit. Exceptional discriminators retain the originating category rather than the catch type. Missing required catch attribution refuses admission. Scope/handler/switch state restores on normal and abrupt lowering. No universal JVM effects, resource-cleanup success or arbitrary nested-handler support is added. These are retained design obligations traced to the current lowerers/construction, not newly executed behavior.[6]

## Target dependency closure and enforcement

The CFG input contract is the **Source Model public `AstResult`**, not `JavaFrontend`, `JavaAnalysisPipeline`, mutable aggregate construction, or a frontend-owned execution projection. Its transitive domain/operational closure is `source.api` (declarations, recursive types, semantic references, source occurrences/edges, annotation/type-use values, evidence states, AST diagnostics, coverage/profile metadata), `identity.api` (snapshots/configuration/spans/identifiers), and Java base immutable collections/value types. The designated nullness metadata below is also retained and inspected. None of those types returns CFG values, frontend classes, application envelopes, repository types, `Supplier`/visitor callbacks into another context, or live compiler objects. Builders accept copied Source Model values and stay outside the read contract.

**Target enforcement decision — nullness metadata:** preserve the existing [NullAway/JSpecify quality contract](../build-quality.md#responsibility-boundaries) and [pinned provided JSpecify dependency](../../pom.xml). `org.jspecify.annotations.NullMarked` and `org.jspecify.annotations.Nullable` are permitted solely as nullness annotation metadata, including package and type-use annotation descriptors retained in bytecode. They are additional inspected metadata dependencies, not Java-base classes or domain/operational dependencies. They cannot appear as ordinary field, parameter, return, generic-bound or exception value types, runtime class literals, factories or callbacks. No other external annotation dependency is permitted by this decision, and it does not authorize `NullUnmarked` or a nullness suppression.

Import and API/bytecode gates must inspect every dependency, including annotations and package metadata, and distinguish the two named annotation uses from ordinary type/code references. They must reject other external metadata and all forbidden domain/operational references rather than omitting annotation inspection. Isolated producer/reader/CFG quality compilation includes the pinned provided annotation definitions and all configured nullness rules; identity domain operations must also work with only Java base at runtime. No compiler, graph, application or repository value becomes reachable through this metadata. This treatment applies consistently to each boundary below and preserves explicit package marking, null-flow analysis and the required physical domain partitions; it changes no configured quality rule or severity.

| Boundary | Permitted dependency | Forbidden dependency / migration enforcement |
| --- | --- | --- |
| Source Identity | Java base domain/operational dependencies; designated nullness metadata | All project contexts. Extract only snapshot/identity/span/configuration semantics; disallow other project imports. |
| Source Model | Source Identity, Java base; designated nullness metadata | CFG, frontend, application and repository, including nested/generic/exception/factory return types. Disallow imports and qualified references, keep validators/internal indexes unexported. |
| Java Frontend | Source Model API/value-admission API, Source Identity, supported public compiler APIs | CFG, repository, application coordination and mutable Source Model internals. Replace application-owned `SourceAnalyzer`/input-environment exceptions with Source Model production contract/exceptions; CLI translation uses an application adapter. |
| CFG | Source Model **read** API, Source Identity, Java base; designated nullness metadata | Frontend, source construction/internal validators, application and repository. Move immutable CFG values out of mixed `model`, make lowerers/plans private, and forbid all producer/construction imports. |
| Application | Source Model/CFG public API, identity, its storage ports | Concrete producer/lowerer/storage packages. Inject `AstProducer` and `CfgDeriver`; only composition root selects concrete classes. |
| Repository | Application storage ports, qualified observation support, public retained values, and qualified `source.retention` codec; Neo4j driver | Producer/lowerer/source-builder and `source.admission` internals. `AstCodec.decode` performs intrinsic readmission inside Source Model; the adapter does not set completion flags. Completed-run mapping consumes public graph values. |

The current [import-control.xml](../../config/checkstyle/import-control.xml) and seven named modules enforce the declared dependency direction. The [build guide](../build-quality.md#product-compilation-partitions) owns the compilation/output contract. [Source exports](../../src/main/java/org.aquilagraph.source/module-info.java) expose reader values generally, copied-value admission only to frontend, and retention only to repository. [Application exports](../../src/main/java/org.aquilagraph.application/module-info.java) expose API/SPI generally and observation support only to repository. CFG and application cannot access source construction; application consumes public graph values without graph admission/plans. Source and CFG implementation packages remain encapsulated. These descriptors and the configured checks establish the intended boundaries, not a new execution result.

[ModuleDependencyBoundaryTest](../../src/test/java/org/aquilagraph/architecture/ModuleDependencyBoundaryTest.java) inspects module classfiles, public signatures, generic/annotation references, and permitted/forbidden consumer compilation. [IdentityBoundaryTest](../../src/test/java/org/aquilagraph/architecture/IdentityBoundaryTest.java) checks identity classfile closure. [ModuleBoundaryProcessIT](../../src/test/java/org/aquilagraph/architecture/ModuleBoundaryProcessIT.java) and [IdentityRuntimeProcessIT](../../src/test/java/org/aquilagraph/architecture/IdentityRuntimeProcessIT.java) own separate delivered-module and base-only process observations under the [integration scope](../build-quality.md#development-verification-scope). Listing these checks does not claim they ran for this documentation change, or establish domain conformance or independent acceptance.

## Target coordination, completion, and failures

Production retains the requested `CfgSelection` and binds effective keys to
the available result's exact identity. `METHODS` preserves the caller's
ordered keys. The CLI requests `ALL_BODIES` with an empty requested list.
Production resolves its effective keys from the available AST's declarations
with bodies in canonical method order. Unavailable production returns a
rejected run with an `UNBOUND` selection and makes no CFG request. Direct
`deriveExisting` keeps the caller's identity-bound request on either available
or unavailable input. The [selection identity](source-snapshots.md#application-selection-and-cfg-request-identity)
owns the framed variants, and the [analysis contract](../contracts/analysis.md#target-independent-analysis-contracts)
owns operation behavior.[1]

`AnalysisCapabilities` composes independent public interfaces with package-private implementation owners. `SourceComputation` implements `AnalysisComputation.analyze(AnalysisComputationRequest)` using injected `AstProducer` and `CfgDeriver`, without storage. `EstablishedSourceDerivation` implements `ExistingSourceDerivation.deriveExisting(AstOutcome, CfgRequest)` using only the derivation port; unavailable source yields a rejected run. Both owners share `CfgInvocation` for exact request validation and declared abrupt derivation translation. `StandaloneAstRetention` and `RetainedAstQuery` implement independently composable retention and lookup with only an AST repository factory.[1][8]

`AnalysisRequest` contains computation plus publication intent. `AnalysisExecutionCoordinator` implements `AnalysisUseCase.execute`, invokes computation, and applies intent only after its normal return. `PublicationCoordinator` is the sole shared policy owner for standalone AST and coordinated publication; `OwnedRepository` tracks transferred-resource ownership and available replacement observations. Coordinated publication retains the complete run even when saving only its AST. `AnalysisQualification` owns overall completion, while `Neo4jAnalysisRunMapper` owns the stored completed-run projection. No public forwarding facade or independent completed-run publication input is required by this surface. The application does not own AST consistency, CFG admission/lowering, graph validation, or compiler evidence production. The [analysis contract](../contracts/analysis.md#inputs-and-boundaries) owns the exact composition and operation signatures.[1][10]

`ProgramRepresentation` is redefined as references to `AstOutcome` plus the optional `CfgOutcome`; it does not duplicate declarations or aggregate bodies. `AnalysisRun` owns request identity, this representation and overall outcome. Every nonselected canonical declaration receives `NOT_REQUESTED` in the application coverage query; selected bodyless declarations remain `NOT_APPLICABLE`. Per-context diagnostic streams are retained separately. The application-owned [canonical occurrence contract](../contracts/analysis.md#canonical-diagnostic-occurrences) composes AST events, CFG events in canonical method order, then application events, and resolves native qualification references to those exact occurrences. The retained assessment owns the immutable catalog; CLI and repository consumers do not reconstruct the composition. Equal diagnostic values from different creating-site addresses are never merged. Repository diagnostics belong to publication, not AST/CFG completion.

| Overall request outcome | Application qualification |
| --- | --- |
| `REJECTED` | No AST result or invalid/foreign/incompatible requested identity/selection. A previously established AST outcome remains attached even when the CFG request is rejected. |
| `COMPLETE` | AST is `COMPLETE`; CFG is not requested, or its requested selection is `COMPLETE`; no application error. Bodyless/not-requested bodies impose no hidden graph requirement. |
| `PARTIAL` | AST is available but `INCOMPLETE`, or requested CFG has an admission refusal while retaining available method graphs. |
| `FAILED` | A returned declared construction failure or application operation failure prevents completion; retain established AST and every independently validated CFG result. |

Apply qualification in that table's explicit precedence: invalid request/no AST or rejected CFG gives `REJECTED`; otherwise any returned construction/application failure gives `FAILED`; otherwise incomplete AST or partial requested CFG gives `PARTIAL`; otherwise `COMPLETE`. Thus a missing source fact plus a later returned construction failure cannot accidentally become a successful or merely refused request. Abrupt failures do not pass through this returned-outcome qualification.

Publication is a separate `PublicationOutcome`, with intent, optional subject identity, status (`NOT_REQUESTED`, `NOT_ELIGIBLE`, `SAVED`, `FAILED`) and effective failure/commit knowledge. An unavailable AST has no AST-result identity; ineligible/no-attempt reporting must not fabricate one. Attempted eligible saves always identify their exact subject. The initial target retains the legacy completed-run save policy and introduces the separate explicit completed-AST policy defined in the [analysis contract](../contracts/analysis.md#target-publication-and-retention). Neither outcome rewrites AST or CFG completion.

Both publication intents return `FAILED` only for an effective `RepositoryException` with present replacement knowledge. Other runtime failures and absent save observations use `AnalysisPublicationException`, retaining the computed subject and exact carrier/cause chain. The generic extensible carrier belongs to `application.port`; application observation boundaries and adapter-specific phases belong to their respective subtypes. Absent knowledge is distinct from an observed `UNKNOWN` commit. Fatal primary errors remain abrupt; later cleanup errors retain their suppressed position. The [repository crossing](../contracts/analysis.md#target-repository-failure-crossing) is the canonical definition of failure classification, subject identity, resource transfer, and query propagation. No exception exposed by Source Model or CFG refers to these application envelopes.[1]

Returned domain refusal differs from abrupt failure. A declared recoverable construction failure returns per-method `FAILED` with its actual cause; an unexpected effective runtime failure remains abrupt. `CfgDerivationException` carries request identity, completed method outcomes and cause; the coordinator wraps that declared family in `AnalysisDerivationException` carrying the established `AstOutcome` and CFG progress. It does not manufacture normal completion, promote a suppressed cause, or swallow `Error`. Direct CFG callers already own their AST argument; abrupt failure cannot mutate it. Undeclared failures retain their actual family and do not promise a complete normal report. Compiler production failures before normal close/recapture return no established `AstOutcome`; source/root diagnosed refusals can return `UNAVAILABLE`. Preserve snapshot checked I/O and path-facade named failure translation through adapters during migration.[2]

## Target identity and retention crossings

The [source snapshot contract](source-snapshots.md) defines source, environment and software identity. `AstIdentity` qualifies the software signature with the source-model format, selected source profile, semantic-production revision and canonical content digest. The complete tuple derives the framed result key. The [retained format](ast-value-format.md) owns current encoding and read admission.

Every `BodyRef` binds `AstIdentity`, canonical method key and written-body occurrence or explicit nonwritten provenance. `GraphSourceRef` binds that body and, for source-correlated operations, a qualified occurrence; generated operations retain body-level provenance and no fabricated span. A graph's derivation identity includes AST result identity, CFG-profile/graph-model version, selected method and graph-content identity. Graph node/value/edge local role algorithms remain stable inside that qualified derivation. Do not trust method key, matching display name or a same-looking node ID alone: current method signatures can recur across snapshots, and current source/auxiliary node IDs can coincide without aliasing.[6][9]

The application owns `AstRetentionRepository.saveComplete(AstOutcome)` and `AstQueryRepository.find(AstIdentity)`. The `retainAst` use case stores a `COMPLETE` AST on an explicit `SAVE_COMPLETE_AST` request, independently of CFG. Each repository is acquired with its exact operation and subject context. The [failure crossing](../contracts/analysis.md#target-repository-failure-crossing) defines phase and commit knowledge. An `INCOMPLETE` AST remains queryable in the returned outcome and is ineligible for retention. `findAst` returns distinct `FOUND`, `NOT_FOUND`, and `INCOMPATIBLE` results and propagates query failures. It reads only the supported source representation and leaves CFG unloaded. The [retired-format policy](ast-value-format.md#read-admission-and-retired-formats) governs incompatible source envelopes.

Independent retention requires AST format, mapping, decoder, schema/constraint inspection, qualified source references, lossless fact/annotation/evidence/coverage/diagnostic ordering and actual storage round-trip observations. The [repository design](analysis-repository.md#target-ast-retention) and [schema migration](../repository/neo4j-schema.md#target-ast-retention-schema) define the chosen crossing. Legacy runs cannot reconstruct an AST from graphs; recapture/reproduction is a new operation, not decoding. Legacy and target projections must coexist without same-run deletion removing independently retained ASTs.

## Responsibility traceability and migration

The retained contracts above govern expansion. This matrix identifies their owners and current entry points without repeating the retired package-migration instructions. Each review must follow the complete affected contracts and consumers, not treat these entry points as an exhaustive call graph or acceptance evidence.

| Owner | Owning contract and current entry points | Required verification boundary |
| --- | --- | --- |
| Source Identity | [Snapshot and qualified identities](source-snapshots.md); [AstIdentity](../../src/main/java/org.aquilagraph.identity/org/aquilagraph/identity/api/AstIdentity.java); [captured-value encoding](captured-value-encoding.md). | Preserve supplied-value validation, canonical content and order, exact qualification, foreign identity rejection, and runtime dependency closure. |
| Source Model | [Intrinsic validity](#ast-result-and-intrinsic-validity), [initial source profile](#initial-source-profile), and [AstResult](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/api/AstResult.java). | Preserve one canonical aggregate, source ownership, independently keyed facts, coverage and diagnostic occurrences, required/optional evidence, and reader/admission separation. |
| Java Frontend | [Compiler lifecycle](#compiler-lifecycle), [retained resource obligations](#retained-source-and-resource-obligations), and [JavaFrontend](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaFrontend.java). | Real compiler production, independent source expectations, source-context support, exact fact binding, written/inserted provenance, no live compiler escape, and truthful primary/suppressed cleanup failures. |
| Control Flow | [Semantic evidence and policy](#semantic-evidence-and-cfg-policy), [admission and construction](#cfg-admission-construction-and-result), [public graph contracts](#public-cfg-values-and-queries), [evaluation obligations](#preserved-evaluation-obligations), and [CfgDeriver](../../src/main/java/org.aquilagraph.cfg/org/aquilagraph/cfg/api/CfgDeriver.java). | Consume an existing AST independently; preserve ordered outcomes, lowering and exceptions, immutable graph invariants, query behavior, source references, and retained progress across refusal/failure. |
| Analysis Application | [Coordination and qualification](#target-coordination-completion-and-failures), [AnalysisCapabilities](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisCapabilities.java), [SourceComputation](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/SourceComputation.java), [EstablishedSourceDerivation](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/EstablishedSourceDerivation.java), and [PublicationCoordinator](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/PublicationCoordinator.java). | Exact request/selection identity, independent inputs/results, computation without storage, no production/publication in derive-existing, lazy storage eligibility, retained causes, CLI presentation, and no domain policy hidden in coordination. |
| Analysis Repository | [Repository contract](analysis-repository.md), [retained value format](ast-value-format.md), [schema](../repository/neo4j-schema.md), and [Neo4jAstResultRepository](../../src/main/java/org.aquilagraph.repository/org/aquilagraph/repository/internal/neo4j/Neo4jAstResultRepository.java). | Actual complete-AST save/query without CFG, lossless admitted decoding, compatible/incompatible/absent distinctions, separate replacement namespaces, and phase/commit/cleanup semantics. |

All existing graph evaluation controls and [quality gates](../build-quality.md) remain requirements. Public signatures, exceptions, factories, callbacks, transitive values, and every producer/decoder construction path belong to the dependency review. Supplied fixtures establish only their supplied conditions; compiler, packaged-process, and storage claims require the corresponding observations. Do not introduce permanent mixed-responsibility aliases or duplicate AST ownership to avoid migrating consumers.

## Semantic walkthroughs

These walkthroughs define the observable contract. Current executable observations are listed below; model statements and passing checks remain distinct from complete implementation acceptance. All queries preserve immutable values, canonical source/order constraints and separate diagnostic ownership.

| Scenario | Operation, input and preconditions | Result and diagnostics | Invariants / model elements |
| --- | --- | --- | --- |
| 1. AST without CFG | `AstProducer.produce(request)`; captured root/configuration and declared AST profile; nonempty compiler work/copy/closure/recapture normal. Empty capture opens no compiler and returns unavailable provenance. Then queries when result exists. | `AstOutcome` qualifies source alone; no CFG acquisition. Application coverage reports `NOT_REQUESTED`. Abrupt work/cleanup/verification failure establishes no normal outcome and preserves its effective family. | [Source production and qualification](#ast-result-and-intrinsic-validity); [compiler lifecycle](#compiler-lifecycle). |
| 2. Existing compatible AST | Direct `CfgDeriver.derive(ast, request)` or application `deriveExisting(outcome, request)` with exact identity, known selected keys and supported source-model version. | Direct call returns `CfgOutcome`; application returns `AnalysisRun`. Ordered method outcomes and CFG diagnostics; no producer/session or publication call. Rejected request retains its exact selection and established AST. | CFG input closure and immutable AST; [CFG derivation](#cfg-admission-construction-and-result); [coordinated terminals](#target-coordination-completion-and-failures). |
| 3. AST supports, CFG refuses | An AST-profile-supported arrow switch expression is retained completely. Illustrative compatible CFG profile excludes switch expressions; `CfgAdmission.assess` sees its actual source kind. This is a modeled profile choice, not a claimed existing lowerer. | AST stays `COMPLETE`; method `UNSUPPORTED` with occurrence/profile diagnostic; coordinated request `PARTIAL`. Source outside its own profile would instead be AST `INCOMPLETE`. | Independent profiles, no AST invalidation; source/CFG and derivation. |
| 4. Missing/erroneous evidence | Under `java-source-bounded/1`, known selected call target/types but missing applicability, or known cast source/target but missing runtime-check classification. Compare missing selected target/catch type and actual compiler errors. | Missing optional classification alone can retain AST `COMPLETE` while CFG returns `INSUFFICIENT_EVIDENCE`; missing required source target/type or compiler error yields AST `INCOMPLETE`. CFG-required erroneous evidence is a distinct refusal. Contradictory known facts reject admission; no invented selection. | [Initial fact requirements](#initial-source-profile), evidence table, AST completion and CFG admission; source/CFG. |
| 5. Bodyless/partial graphs | Request selects a canonical abstract/native declaration plus two concrete methods; one admitted, one refused. | `NOT_APPLICABLE`, `AVAILABLE`, refusal in selection order; CFG `PARTIAL`. Other unselected methods are `NOT_REQUESTED`. AST/declarations remain. | Exact method membership, graph-only body requirement; application/CFG and coordinated run. |
| 6. Later construction failure | Established AST passed to admitted builder; construction experiences a declared failure after an earlier method graph succeeded. | Method `FAILED`, earlier validated graph retained; request `FAILED`; AST unchanged. Abrupt declared CFG exception carries progress; application exception retains AST/cause. Undeclared failures do not become successful returns. | No invalid graph publication; [coordinated outcomes](#target-coordination-completion-and-failures). |
| 7. Independent retained AST query | Explicit eligible `retainAst` then later `findAst(identity)` with separate exact operation contexts; storage save/close succeeded. | Supported present facts pass admission and yield `FOUND` without CFG. Absence or unsupported version bypass decode and yield `NOT_FOUND`/`INCOMPATIBLE` only after normal close. Acquisition/read/decode/close failure propagates the query carrier and returns no lookup or publication outcome. | Lossless AST codec, independent storage scope and [failure crossing](../contracts/analysis.md#target-repository-failure-crossing); [retention](analysis-repository.md#target-ast-retention). |
| 8. Foreign/incompatible association | `CfgDeriver.derive` or graph admission receives another snapshot/environment/profile/format identity despite matching method or occurrence key. | Request `REJECTED`/invalid graph admission with identity diagnostic; no graph association. AST argument remains available. | Exact `AstIdentity`/`BodyRef`/`GraphSourceRef`; identity/CFG. |
| 9. Publication failure | Analyses already computed; explicit eligible completed-run or AST save fails during acquisition/validation/mapping/schema/replace/close. | Shared carrier preserves exact operation/subject, optional replacement knowledge, effective primary, and suppressed causes. Owner-specific subtype retains boundary detail. Declared primary plus present knowledge returns `FAILED`; other runtime exits use the abrupt application envelope. No analysis completion rewrite. | [Publication](analysis-repository.md#publication-phases-and-failures), [phase mapping](analysis-repository.md#target-phase-and-commit-mapping). |

## Model acceptance and verification

**Acceptance ownership:** Implementation acceptance requires evidence for the declared scope across the affected contexts and walkthroughs, including their applicable alternatives. The separation's declared review boundaries remain Source Identity, Source Model/producer, Control Flow, and Analysis Application/storage, plus complete integration. New or affected scopes require fresh paired reviews; each reviewer independently assesses the entire declared scope and applicable criteria. Preserve valid prior acceptance and carry inherited contracts and consumers into each scope. Keep the candidate fixed during review, resolve findings, and re-review affected criteria after corrections. Scope-definition acceptance does not accept implementation. Apply the [development verification scope](../build-quality.md#development-verification-scope); reviews do not authorize deferred real-environment campaigns.

Historical model verdicts, test totals, and resource-closure claims without accessible records are not current acceptance evidence. Establish the exact candidate and relevant observations before reusing a result; retain operational records under ignored build output. The [build quality guide](../build-quality.md#responsibility-boundaries) defines the applicable checks. Source inspection identifies implemented structures but does not establish executed gates, deployed storage behavior, or complete independent acceptance.

## References

1. [AnalysisCapabilities](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisCapabilities.java), independent inputs and composition; [SourceComputation](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/SourceComputation.java), `analyze`; [AnalysisExecutionCoordinator](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisExecutionCoordinator.java), `execute`; [AnalysisQualification](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/AnalysisQualification.java), `assess`; [CompletedRunPublicationEligibility](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/CompletedRunPublicationEligibility.java), `accepts`; [PublicationCoordinator](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/PublicationCoordinator.java), shared policy; [application contracts](../contracts/analysis.md#target-repository-failure-crossing), public failure and ownership boundaries.
2. [JavaFrontend](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaFrontend.java), overloads; [JavaAnalysisPipeline](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaAnalysisPipeline.java), lifecycle; [JavaCompilerInvocation](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaCompilerInvocation.java), controlled options; [JavaRelease](../../src/main/java/org.aquilagraph.identity/org/aquilagraph/identity/api/JavaRelease.java), supported releases; [SnapshotSourceCatalog](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/SnapshotSourceCatalog.java), `find`; [CompilerDiagnosticTranslator](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/CompilerDiagnosticTranslator.java), `origins`.
3. [JavaAstAggregate](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/api/JavaAstAggregate.java), sealing and method views; [SyntaxAnalysisInput](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/admission/SyntaxAnalysisInput.java), neutral admission; [ProgramRepresentation](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/ProgramRepresentation.java), durable result; [SyntaxType](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/api/SyntaxType.java), type outcomes.
4. [JavaProgramTranslator](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaProgramTranslator.java), declarations; [JavaTypeUseTranslator](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaTypeUseTranslator.java), written type uses; [JavaAnnotationCompleteness](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaAnnotationCompleteness.java), annotation reconciliation; [ConstructorCorrelationPreparer](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/ConstructorCorrelationPreparer.java), `prepare` and `attribute`; [ConstructorWitnessCorrelation](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/ConstructorWitnessCorrelation.java), `attributedSelection` and `correlate`; [ConstructorTypeCorrespondence](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/ConstructorTypeCorrespondence.java), `MatchAttempt` and `originalType`; [CompilerFileManager](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/CompilerFileManager.java), resource ownership.
5. [JavaLanguageFeatureCoverage](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaLanguageFeatureCoverage.java), policies; [JavaSourceCoverageCapture](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaSourceCoverageCapture.java), written obligations; [JavaSourceCoverageReconciler](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaSourceCoverageReconciler.java), reconciliation; [DiagnosticEmissions](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/DiagnosticEmissions.java), event transport.
6. [JavaExecutableSyntaxProfile](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaExecutableSyntaxProfile.java), source projection; [CfgAdmission](../../src/main/java/org.aquilagraph.cfg/org/aquilagraph/cfg/internal/CfgAdmission.java), consumer readiness; [ControlFlowGraphBuilder](../../src/main/java/org.aquilagraph.cfg/org/aquilagraph/cfg/internal/ControlFlowGraphBuilder.java), build; [ExpressionGraphLowerer](../../src/main/java/org.aquilagraph.cfg/org/aquilagraph/cfg/internal/ExpressionGraphLowerer.java), expressions; [StatementGraphLowerer](../../src/main/java/org.aquilagraph.cfg/org/aquilagraph/cfg/internal/StatementGraphLowerer.java), statements; [UseConversionLowerer](../../src/main/java/org.aquilagraph.cfg/org/aquilagraph/cfg/internal/UseConversionLowerer.java), conversions; [CallArgumentLowerer](../../src/main/java/org.aquilagraph.cfg/org/aquilagraph/cfg/internal/CallArgumentLowerer.java), arguments; [ControlFlowGraphConstruction](../../src/main/java/org.aquilagraph.cfg/org/aquilagraph/cfg/internal/ControlFlowGraphConstruction.java), graph identities and transfers.
7. [AquilaGraphProcessIT](../../src/test/java/org/aquilagraph/AquilaGraphProcessIT.java), packaged command outcomes; [Neo4j integration tests](../../src/test/java/org/aquilagraph/repository/internal/neo4j/Neo4jAnalysisRunRepositoryNeo4jIT.java), database observations; [build quality](../build-quality.md), gate responsibilities.
8. [AstProducer](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/api/AstProducer.java), `produce`; [EstablishedSourceDerivation](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/EstablishedSourceDerivation.java), `deriveExisting`; [CfgInvocation](../../src/main/java/org.aquilagraph.application/org/aquilagraph/application/CfgInvocation.java), exact request validation and declared failure translation.
9. [MethodSyntax](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/api/MethodSyntax.java), copied fact queries; [CfgAdmission](../../src/main/java/org.aquilagraph.cfg/org/aquilagraph/cfg/internal/CfgAdmission.java), consumer prerequisites; [ExecutionDecisionValidation](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/internal/ExecutionDecisionValidation.java), consistency and missing-fact checks; [aggregate validator](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/internal/JavaAstAggregateBuilder.java), `validate`; [JavaBodyDecisionProducer](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaBodyDecisionProducer.java), selection/corroboration; [enum producer](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaConstructorEvidenceProducer.java), `produce`.
10. [Neo4jAnalysisRunMapper](../../src/main/java/org.aquilagraph.repository/org/aquilagraph/repository/internal/neo4j/Neo4jAnalysisRunMapper.java), `map`; [repository design](analysis-repository.md), existing phases.
11. [TranslationResult](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/TranslationResult.java), `emissions`; [DiagnosticEmissionCollector](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/DiagnosticEmissionCollector.java), creating addresses and batch composition; [DiagnosticEmissions](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/DiagnosticEmissions.java), immutable ordered events.
12. Wazlawick, *Object-Oriented Analysis and Design for Information Systems* (2014), §9.1–9.2, PDF pp. 284–289, responsibility allocation and design interactions; [chapter DOI](https://doi.org/10.1016/B978-0-12-418673-6.00009-0). Applied here as design guidance, not a mandated decomposition.
13. OMG UML 2.5.1, §7.7 Dependencies, printed pp. 37–39/PDF pp. 79–81; §10.4 Interfaces, printed pp. 170–173/PDF pp. 212–215; §11.5 association semantics/notation, printed pp. 199–204/PDF pp. 241–246; §11.6 Components, printed pp. 208–212/PDF pp. 250–254; §17.2 Interactions and §17.6 Fragments, PDF pp. 608–611, 623–628. [Official specification](https://www.omg.org/spec/UML/2.5.1/PDF). UML dependencies do not themselves enforce runtime boundaries.
14. [ControlFlowGraph](../../src/main/java/org.aquilagraph.cfg/org/aquilagraph/cfg/api/ControlFlowGraph.java), construction and public queries; [FlowValue](../../src/main/java/org.aquilagraph.cfg/org/aquilagraph/cfg/api/FlowValue.java), record components; [OperationNode](../../src/main/java/org.aquilagraph.cfg/org/aquilagraph/cfg/api/OperationNode.java), payload and initialization validation; [ValueKind](../../src/main/java/org.aquilagraph.cfg/org/aquilagraph/cfg/api/ValueKind.java) and [OperationKind](../../src/main/java/org.aquilagraph.cfg/org/aquilagraph/cfg/api/OperationKind.java), closed baseline roles; [Neo4jControlFlowMapper](../../src/main/java/org.aquilagraph.repository/org/aquilagraph/repository/internal/neo4j/Neo4jControlFlowMapper.java), `mapGraph` and `mapNode`. Removed aggregate model at baseline `517d0156ab294e9e6a65ba53373934ee8deab12d`, lines 24–87 and 191–249, is available in Git.
15. [AstProfile](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/api/AstProfile.java), supported versions and capability predicates; [JavaTranslationPolicy](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaTranslationPolicy.java), `forProfile` and `legacyAnalysis`; [SourceRequirements](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/internal/SourceRequirements.java), `assess` and required source facts.
16. Oracle Java SE 25, [JLS 7.3](https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.3), imports in modular compilation units; [JLS 7.7.3](https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.7.3) and [JLS 7.7.4](https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.7.4), service operands and provider-method semantics; [UsesDirective](https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.UsesDirective.html) and [ProvidesDirective](https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.ProvidesDirective.html), resolved declaration endpoints. These establish Java semantics, not delivery by this project.
17. Oracle Java SE 25, [Elements](https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/util/Elements.html), `getAllTypeElements` and `getModuleOf`: distinct type elements may have the same canonical name in different modules.
18. [Intrinsic AST qualification](#ast-result-and-intrinsic-validity), independent source completion and required facts; [qualified source identity](source-snapshots.md#target-qualified-analysis-identity), retained content, external references, and compatibility; [publication and retention](../contracts/analysis.md#target-publication-and-retention), completion-based eligibility; [build responsibilities](../build-quality.md#responsibility-boundaries) and [acceptance ownership](#model-acceptance-and-verification), applicable verification duties.
19. [Retained AST value format](ast-value-format.md), active envelope, read admission and retired-format policy, and [source identity](source-snapshots.md#target-qualified-analysis-identity), qualified result content.
20. [AstProfile](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/api/AstProfile.java), supported profile versions and capability predicates; [JavaTranslationPolicy](../../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaTranslationPolicy.java), profile-derived translation policy; [SourceRequirements](../../src/main/java/org.aquilagraph.source/org/aquilagraph/source/internal/SourceRequirements.java), required source facts. These source anchors identify implementation responsibilities, not a new runtime acceptance result.
