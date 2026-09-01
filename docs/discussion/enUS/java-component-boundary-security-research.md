# Minimum Draft for Discussion with Professor Marcelo Eler

> **Purpose:** validate a candidate research problem and its fit with ECO-CHAINS before committing to a solution, architecture, implementation plan, or claim of novelty.

## 1. Evidence Boundary

This draft starts from the two local PDFs and uses the Aquila Graph master document only as internal design context. It does not treat Aquila Graph's current architecture, implementation profile, hypotheses, or reference systems as evidence that the proposed research problem exists or that a particular solution is correct. The master document itself distinguishes reference study from adoption and requires architectural choices to be supported by evidence rather than inherited from one external model ([Aquila Graph, Sections 1 and 2.2](D:/Projects/aquila-graph/studies/PROJECT.md)).

No named external analysis tool, graph model, database, or machine-learning technique defines the questions or the expected result in this draft. Those are possible alternatives to compare later if the research problem is validated.

## 2. Documentary Starting Point

The ECO-CHAINS proposal defines eco-responsible software as including security alongside reliability, energy use, and hardware obsolescence. It describes a Software Supply Chain as spanning software production, distribution, maintenance, suppliers, components, and delivery channels; it also states that the chain can affect software quality and that relevant information is scattered, heterogeneous, or inaccessible ([ECO-CHAINS, Section 1.1, PDF p. 1](D:/Projects/aquila-graph/aquila-graph/pdf/supply-chains-for-eco-responsible-software.pdf#page=1)).

The proposal specifically states that external artifacts incorporate their providers' decisions and that including these artifacts can affect qualities such as security. It therefore identifies dependency management and the impact of development decisions on the final product as relevant concerns ([ECO-CHAINS, Section 1.1, PDF p. 2](D:/Projects/aquila-graph/aquila-graph/pdf/supply-chains-for-eco-responsible-software.pdf#page=2)). Its objectives expressly include Software Supply Chain modeling, persistence and querying, information extraction from heterogeneous or limited sources, security-threat modeling and detection, and analysis of local development decisions ([ECO-CHAINS, Section 1.4, PDF p. 5](D:/Projects/aquila-graph/aquila-graph/pdf/supply-chains-for-eco-responsible-software.pdf#page=5)).

The local study of Python web-framework versions reports that dependency changes can coincide with substantial changes in an application quality outcome ([How Green Is Your Upgrade?, Abstract, PDF p. 1](D:/Projects/aquila-graph/aquila-graph/pdf/how-green-is-your-upgrade-carbon-emission-trends-in-python-web-frameworks.pdf#page=1)). In particular, it reports a relationship between Starlette changes and FastAPI's measured energy profile, while also limiting the result to synthetic workloads, a specific Python environment, and energy-related outcomes ([Sections 5.4.2–5.4.3, PDF p. 10](D:/Projects/aquila-graph/aquila-graph/pdf/how-green-is-your-upgrade-carbon-emission-trends-in-python-web-frameworks.pdf#page=10)).

**Inference:** the Python study provides a methodological precedent for examining a consumer together with its dependencies rather than treating each component only in isolation. It does **not** establish a Java security problem, a security effect, or a general causal relationship between dependency use and security. Establishing the relevant security literature and scientific gap remains necessary after this initial discussion.

## 3. Provisional Topic

**Attribution of security-relevant paths across application-owned and third-party components in Java applications distributed as JVM bytecode.**

This wording identifies the object of study without presupposing that the result must be a graph, that static and dynamic evidence must be fused, or that a particular tool or architecture is required.

## 4. Candidate Research Problem

The bounded uncertainty is whether analyzing application-owned bytecode and third-party bytecode only as separate units loses information needed to characterize security-relevant paths that cross component boundaries.

For this discussion, the following terms are provisional operational distinctions rather than established findings:

- **presence:** a component or bytecode element is included in the analyzed distribution or resolved classpath;
- **static reachability:** an analysis constructs a possible path under its declared model and assumptions;
- **dynamic observation:** an event is recorded in a specified execution, workload, environment, and observation window;
- **exploitability:** a concrete scenario connects attacker influence, a reachable path, insufficient controls, and impact.

The research must not infer reachability from presence, general possibility from one observation, impossibility from non-observation, or exploitability from a tool candidate alone. These distinctions are candidate analytical safeguards derived from the Aquila Graph design context, not conclusions established by the local PDFs ([Aquila Graph, Sections 4 and 6](D:/Projects/aquila-graph/studies/PROJECT.md)).

## 5. Provisional Research Questions

### RQ1 — Component contribution

**How do application-owned and third-party components participate, separately and jointly, in security-relevant paths in a bounded corpus of Java applications distributed as JVM bytecode?**

### RQ2 — Value of composed analysis

**What security-relevant information, if any, is added by a composed analysis when compared with separate analyses of application-owned and third-party bytecode under the same scope and assumptions?**

### RQ3 — Evidence interpretation

**Which provenance, context, and coverage information is necessary to distinguish component presence, static reachability, dynamic observation, and a supported exploitability assessment?**

RQ2 is deliberately refutable: a valid result may be that composed analysis provides no material advantage for the selected corpus, path classes, or analysis configuration.

## 6. Proposed Fit with ECO-CHAINS

**Supported fit:** the primary connection is to ECO-CHAINS' objective of security-threat modeling and detection ([ECO-CHAINS, Section 1.4, PDF p. 5](D:/Projects/aquila-graph/aquila-graph/pdf/supply-chains-for-eco-responsible-software.pdf#page=5)). Dependency management also supplies the proposal's rationale for studying how provider decisions may affect a consuming product ([Section 1.1, PDF p. 2](D:/Projects/aquila-graph/aquila-graph/pdf/supply-chains-for-eco-responsible-software.pdf#page=2)).

**Provisional fit:** information extraction, persistence, and querying become relevant only if the study demonstrates a need to preserve and compare evidence across artifacts, analyses, or executions. They are possible enabling capabilities, not the scientific contribution by themselves.

**Scope boundary:** this draft does not address energy consumption or hardware obsolescence as outcomes. The Python study remains an analogy about dependency-mediated measurement, not evidence for the proposed security questions.

## 7. Initial Study Scope

Included in the initial scope:

- applications written in Java and distributed as JVM bytecode;
- application-owned and third-party bytecode, including direct and transitive dependencies when their artifacts and provenance are available;
- owned applications, synthetic fixtures, or authorized study targets;
- a small, controlled corpus with an explicit ownership boundary and dependency inventory;
- a static comparison among application-only, dependency-only, and composed analysis;
- security-relevant calls or data-flow paths selected through an explicit study protocol.

Excluded from the initial scope:

- claims about all JVM languages, compiler profiles, frameworks, or application domains;
- proof that vulnerabilities are absent;
- complete coverage of all possible behavior;
- production instrumentation or analysis of an unauthorized target;
- energy consumption and hardware-obsolescence measurement;
- selection of a graph database, external analysis platform, machine-learning model, or product architecture;
- claims that Aquila Graph already validates the research problem or solves it.

The exact Java, compiler, and class-file versions should be an experimental control chosen after the study corpus and feasibility constraints are known. Aquila Graph's current Java 25, `javac` 25, and class-file 69.0 implementation profile is an engineering constraint in the master document, not automatically the scientific boundary of this proposed study ([Aquila Graph, Section 7.2](D:/Projects/aquila-graph/studies/PROJECT.md)).

## 8. Incremental Inquiry

### Stage 0 — Validate the gap and constructs

- review the security literature needed to determine whether the proposed gap is novel and material;
- define application-owned code, third-party code, component boundary, security-relevant path, and the unit of analysis;
- select path classes, a bounded corpus, comparison baselines, and observable outcomes;
- identify evidence that would support, weaken, or refute the proposed value of composed analysis.

### Stage 1 — Controlled static comparison

- inventory artifacts and assign component provenance under a documented rule;
- analyze application-owned bytecode and third-party bytecode separately;
- analyze the same inputs together with unchanged analysis rules;
- compare within-component and cross-component paths, unresolved boundaries, and attribution gaps;
- report results with explicit assumptions and residual coverage limits.

### Stage 2 — Conditional dynamic study

Proceed only if Stage 1 identifies questions for which execution evidence is a discriminating observation:

- define controlled workloads and observation boundaries;
- record observed behavior without treating non-observation as impossibility;
- evaluate whether observations confirm, reclassify, or expose gaps in the static results.

### Stage 3 — Conditional representation and tooling

Consider a temporal or hybrid graph, persistent evidence store, visualization, or learning technique only after prior stages establish a requirement that the candidate solution can be compared against. The ECO-CHAINS methodology allows theory building, prototypes, observation, and experimentation, but does not require this specific architecture ([ECO-CHAINS, Section 1.5, PDF p. 5](D:/Projects/aquila-graph/aquila-graph/pdf/supply-chains-for-eco-responsible-software.pdf#page=5)).

## 9. Candidate Contribution

At this stage, the defensible intended contribution is a **bounded empirical characterization and comparison method** for attributing security-relevant paths across application and dependency boundaries. A platform, hybrid graph, or automated vulnerability detector is not yet the research contribution.

**Inference:** if the comparison identifies repeatable cross-boundary information that separate analyses omit, the later contribution may include an evidence model or analysis technique for preserving that information. If it does not, the problem or scope should be revised rather than preserving the expected architecture.

## 10. Points for Validation with the Professor

1. Is the component-boundary question a suitable and sufficiently specific security contribution to ECO-CHAINS?
2. Which prior security literature must be examined before a novelty claim can be made?
3. Is the distinction between application-owned and third-party bytecode scientifically meaningful, and how should it be operationalized for shaded, generated, bundled, or framework-managed code?
4. Is the three-way static comparison—application only, dependencies only, and composed inputs—a sufficient first empirical study?
5. Which security-relevant path classes and study corpus would provide a discriminating first evaluation?
6. Should the scientific scope adopt the existing Java 25 engineering profile or select a different bounded profile based on the corpus and research question?
7. What result would constitute a meaningful contribution if composed analysis shows only a small or null effect?

## References

- Eler, Marcelo Medeiros, and Gerson Sunyé. *Supply Chains for Eco-Responsible Software (ECO-CHAINS)*. IRP proposal, August 28, 2026. Local PDF, especially Sections 1.1 and 1.3–1.5, PDF pp. 1–5: [source](D:/Projects/aquila-graph/aquila-graph/pdf/supply-chains-for-eco-responsible-software.pdf).
- Amorim, Fernando Karchiloff Gouveia de, et al. *How Green Is Your Upgrade? Carbon Emission Trends in Python Web Frameworks*. SBES 2026. Local PDF, especially the abstract and Sections 5.4.2–5.4.3, PDF pp. 1 and 10: [source](D:/Projects/aquila-graph/aquila-graph/pdf/how-green-is-your-upgrade-carbon-emission-trends-in-python-web-frameworks.pdf).
- *Aquila Graph — Project Master Document*, version 0.4.0, August 22, 2026. Internal design context, especially Sections 1, 2.2, 4–8, and 31: [source](D:/Projects/aquila-graph/studies/PROJECT.md).
