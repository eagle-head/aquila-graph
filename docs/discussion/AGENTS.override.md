# Research Discussion Independence

## Scope

These instructions apply to `docs/discussion` and all descendants.

This directory contains working material for discussions with professors and for the formulation, challenge, and refinement of research ideas. It is not a product-design, architecture, implementation-planning, or roadmap area.

## Independence from Aquila Graph

- Keep every research problem, question, hypothesis, objective, scope, method, and candidate contribution independent from Aquila Graph.
- Do not mention Aquila Graph in new or materially revised discussion content unless the user explicitly requests a bounded comparison with it.
- Do not use Aquila Graph code, documentation, diagrams, architecture, hypotheses, terminology, technology choices, implementation profile, constraints, schemas, backlog, status, or roadmap as evidence, motivation, a premise, expected result, research boundary, candidate contribution, or solution requirement.
- Do not infer research relevance from this directory's location inside the Aquila Graph repository. Repository proximity is not evidence.
- Do not cite `studies/PROJECT.md`, product diagrams, source code, or other Aquila Graph artifacts to establish or justify a research claim.
- Do not shape a research problem so that it validates, requires, reproduces, or preserves a pre-existing Aquila Graph design.

The only permitted exception is an explicit user request to compare a research idea with Aquila Graph. Keep that comparison in a clearly separated section, treat product material only as the subject of the comparison, and do not allow it to define or validate the underlying research problem.

## Research Framing

- Begin with the question posed by the user or professor and with independently relevant literature, specifications, records, or other cited sources.
- Identify the bounded uncertainty, its significance, the unit of analysis, material alternatives, scope, and the evidence that could distinguish among possible answers.
- Preserve the possibility of a null, contrary, or scope-limited result. Do not write toward a predetermined architecture or preferred outcome.
- Separate source statements, observations, inferences, hypotheses, research questions, methodological requirements, engineering requirements, tools, and candidate solutions.
- Keep the research problem focused on the knowledge gap. Place analysis techniques, graphs, storage, visualization, datasets, Machine Learning, platforms, and other mechanisms in methodology or conditional alternatives unless one of them is independently the object of a supported research question.
- Treat the difficulty of building or evaluating a tool as distinct from whether the tool is itself a research contribution.
- Use ECO-CHAINS or another program description only to establish documented thematic fit. Do not treat program fit as evidence that a scientific gap exists, is material, or is original.

## Review and Completion Checks

- When modifying an existing discussion document, remove product-derived assumptions from every affected passage instead of preserving them indirectly through renamed concepts.
- Before completing work in this tree, search the changed content for `Aquila Graph`, internal product-document links, implementation versions, architecture commitments, and other product-specific premises.
- If the available independent evidence does not establish a claim, label it `Inference:` or state the evidence gap explicitly.
- Preserve any more specific language rules supplied by a nested instruction file; language localization must not weaken this research-independence policy.

## References

- OpenAI Docs, [Custom instructions with AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md).
- [Repository evidence and inference policy](D:/Projects/aquila-graph/aquila-graph/AGENTS.md#evidence-and-inference).
