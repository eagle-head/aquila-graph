# Aquila Graph

| Field | Value |
| --- | --- |
| Document role | General View of the System / Executive Summary |
| Status | Inception draft for stakeholder review |
| Scope state | Proposed; not yet approved |
| Delivery dates and budget | Not established |
| Architecture and implementation | Not selected by this document |

## Purpose of This Document

This document establishes the preliminary vision and scope boundary for Aquila Graph. In
Wazlawick's terms, it is the short, free-format General View of the System used during Inception
as the basis for scope agreement and subsequent planning. Detailed requirements, use cases,
conceptual models, detailed architecture, schedules, and risk treatments belong in separate
artifacts.

The repository is establishing its own project baseline. Earlier project records and the existing
diagrams may provide questions and candidate ideas, but they do not establish current scope,
delivery status, architecture, technology choices, or completed decisions. Each such item requires
review against this General View before it can become authoritative here.

The candidate statements below have not yet been validated through stakeholder elicitation. They
are deliberately framed for acceptance, rejection, or revision rather than presented as discovered
requirements.

## Proposed Vision and Motivation

Aquila Graph is intended to help security researchers and engineers investigate programs
distributed as JVM bytecode. The working proposition is that relating statically modeled behavior
to behavior observed during authorized executions can produce a more useful investigation record
when the origin, context, coverage gaps, and uncertainty of every contribution remain visible.

This proposition is a project direction to evaluate, not a demonstrated product result. The project
must establish whether intended stakeholders need both forms of information and whether a graph is
the appropriate representation for their priority workflows.

## Proposed Scope

### Candidate Inclusions

- Accept a declared and versioned subset of JVM bytecode artifacts as analysis input.
- Represent relevant program structure and possible behavior without requiring source code when
  the selected input profile permits it.
- Admit runtime observations only for an approved use case and an owned or otherwise authorized
  execution environment.
- Relate modeled and observed information without erasing their different origins or treating a
  missing observation as proof that behavior is impossible.
- Support reviewable queries or reports for security-relevant investigation workflows.
- State the assessed input boundary, provenance, coverage gaps, unresolved conditions, and result
  limitations.

### Candidate Exclusions

- Proving the absence of vulnerabilities or complete program behavior.
- Claiming unrestricted support for every JVM version, compiler, language, library, or execution
  environment.
- Treating a statistical or probabilistic score alone as a confirmed security conclusion.
- Observing software without the owner or operator's authorization.
- Presenting exploratory diagrams, prototypes, or historical results as evidence that the current
  product has been implemented or validated.

### Not Yet Decided

- The first supported JVM, class-file, compiler, and source-language profile.
- Whether runtime observation is required in the first product increment.
- Implementation language, module boundaries, deployment topology, and observation mechanisms.
- Graph representation, storage technology, query interface, and distribution model.
- Whether datasets, machine learning, or production instrumentation belong in the project scope.
- Release sequence, delivery dates, budget, license, and contributor model.

## Candidate Stakeholders

The following roles are candidates for stakeholder validation; listing them does not establish that
their needs have been elicited or approved:

- security researchers investigating program behavior and evidence;
- security engineers or analysts reviewing security-relevant paths; and
- owners or operators responsible for any application observed at runtime.

Before scope approval, the project must identify the primary stakeholder, the concrete job that
stakeholder needs to perform, the observable result expected from Aquila Graph, and the authority
responsible for accepting that result.

## Proposed Principal Deliverables

1. A bounded software capability that accepts the approved input profile and produces a versioned,
   queryable representation with explicit provenance and limitations.
2. If runtime observation remains in scope, a bounded capability that admits authorized observations
   while preserving their execution context and their distinction from modeled possibilities.
3. A reviewable result for at least one approved, high-priority stakeholder workflow.
4. The data contracts, usage documentation, and evaluation evidence needed to reproduce and
   interpret that result within its declared scope.

No delivery date or release commitment is made by this draft.

## Acceptance Criteria to Establish

Wazlawick permits acceptance criteria in the General View and says that, when included, they must
be quantifiable and cover schedule, budget, and quality. The historical material lists measurement
dimensions but does not supply an independently approved acceptance baseline for this repository.
Both the dimensions and their values therefore remain candidates for stakeholder validation:

| Candidate dimension | Question to resolve |
| --- | --- |
| Stakeholder outcome | Name the priority workflow, expected result, and observable success measure. |
| Functional boundary | Fix representative accepted and rejected inputs and their expected outcomes. |
| Analysis correctness | Define independent oracles, tolerated residuals, and pass thresholds. |
| Evidence transparency | Define required provenance, coverage, loss, and limitation reporting. |
| Repeatability | Define the environment, repeated-run procedure, and tolerated variation. |
| Resource behavior | Define workloads and limits for time, memory, storage, and any observation overhead. |
| Delivery and budget | Define an indicative delivery horizon and the available resource envelope. |

Until the acceptance dimensions and values are approved, this document cannot support a claim that
the project or a product increment has met its acceptance baseline.

## Constraints and Premises

- Runtime execution and observation must remain within owned or explicitly authorized environments.
- Product claims must remain bounded by the declared input profile, workload, environment, and
  evidence actually available.
- Historical phase status, backlog identifiers, implementation profiles, and architecture decisions
  are not current project authority.
- JVM bytecode, combined static and runtime information, and a graph representation are working
  premises that still require stakeholder and feasibility validation.

## Candidate Principal Risks

- the project boundary may remain too broad to produce a useful first result;
- the proposed combination may not improve the priority stakeholder workflow;
- identities or semantics may not reconcile reliably across modeled and observed information;
- runtime observation may impose unacceptable operational or data-handling costs; and
- missing quantitative acceptance criteria may allow implementation activity without demonstrating
  stakeholder value.

These are risk candidates for validation, not conclusions about an implemented system.

## Open Questions

1. Who is the primary stakeholder, and what is the first complete workflow Aquila Graph must
   support?
2. Is the primary purpose a software product, a research platform, or a deliberately bounded
   combination of both?
3. What exact JVM bytecode input profile is required for the first useful increment?
4. Is runtime observation essential to that increment, and what authorization and data boundaries
   apply?
5. If runtime observation is retained, what data may be collected, minimized, retained, and
   accessed, and what behavioral or resource impact is acceptable?
6. What result may Aquila Graph legitimately produce: organized evidence, a prioritized lead, or a
   stronger security assessment?
7. Which measurable dimensions and thresholds determine acceptance?
8. Which product form and license serve the intended users?

## Next Inception Artifacts

After this General View is reviewed and its scope is approved, the project should develop separate
high-level use cases, supplementary requirements, a preliminary conceptual model, a risk register,
and an initial schedule. Existing [diagram views](D:/Projects/aquila-graph/aquila-graph/diagrams/README.md) must be reviewed against the
approved scope before they are treated as requirements or architecture authority.

## References

- Raul Sidnei Wazlawick, *Object-Oriented Analysis and Design for Information Systems: Modeling
  with UML, OCL, and IFML* (2014), §1.4, PDF pp. 27–28 / printed pp. 4–5; §2.2,
  PDF pp. 32–35 / printed pp. 9–12; and §3.1, PDF pp. 52–53 / printed pp. 29–30.
  [Chapter 2 DOI](https://doi.org/10.1016/B978-0-12-418673-6.00002-8).
- [Diagram index](D:/Projects/aquila-graph/aquila-graph/diagrams/README.md), current repository navigation and view inventory.
- [Priority use-case companion](D:/Projects/aquila-graph/aquila-graph/diagrams/use-case/README.md), existing detailed view requiring
  clean-slate reconciliation with this General View.
- [Platform component companion](D:/Projects/aquila-graph/aquila-graph/diagrams/component/README.md), existing detailed view requiring
  clean-slate reconciliation with this General View.
- [Historical project document](D:/Projects/aquila-graph/studies/PROJECT.md), §§1, 3–7, 10–13, 16, 18, 24, 26, and 31.
  These sections were inspected only as a discovery inventory; their status, decisions, roadmap,
  and conclusions are not authority for this repository.
