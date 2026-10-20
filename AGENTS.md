# AGENTS.md

## Instruction Precedence

- Follow all active global Codex instructions unless a more specific instruction within this repository explicitly overrides them.
- Local instructions supplement global instructions by default. The absence of a local rule does not override a global rule.
- A nested `AGENTS.md` or `AGENTS.override.md` may override repository-level rules only within its directory tree.

## Scope

These instructions apply to the entire directory tree rooted at this file. More specific `AGENTS.md` or `AGENTS.override.md` files in descendant directories may add to or override these rules for their own scope.

## CodeGraph Location

- The existing CodeGraph index is located at `D:\Projects\aquila-graph\aquila-graph\src\main\java\org\aquilagraph\.codegraph`; use `D:\Projects\aquila-graph\aquila-graph\src\main\java\org\aquilagraph` as the CodeGraph project path.
- Before initializing CodeGraph or reporting that no index exists, search the entire repository tree for directories named `.codegraph`.

## Language

- Write all new or materially revised repository content stored outside chat in United States English.
- This rule applies to source code identifiers, code comments, documentation, diagrams, configuration, schemas, tests, fixtures, commit messages, logs, generated text, and other artifacts stored in the repository.
- Chat and other conversational responses have no fixed language. Respond in the language and regional variety used by the user unless the user requests another language. Preserve established technical terms when translating them would be inaccurate, ambiguous, or unnatural.
- Preserve non-English text in repository content only when it is a load-bearing external value, such as an exact protocol field, quoted source passage, legal name, or compatibility fixture. Explain the exception in United States English within the relevant repository content.

## Platform-Specific Build Commands

- When showing repository build commands, use `make <target>` for Linux and macOS and `.\Invoke-Build.ps1 <target>` for Windows.
- Use the [build quality guide](docs/build-quality.md#commands) for the available targets and their validation responsibilities.

## Validation After Removals

- After any removal, run every configured test group against the final implementation before declaring the work complete. Follow the [complete test validation procedure](docs/build-quality.md#validation-after-removals).

## Diagram Paths and Filenames

- Treat every diagram path as one semantic identifier composed of its directories and filename.
- Use directories to identify the diagram category or modeling scope, and use the filename only for the distinct subject represented within that context.
- Do not repeat in a filename information already expressed by its directory path. In particular, omit redundant terms such as `diagram`, `use-case`, `component`, or another diagram category when the containing directories already provide that meaning.
- Choose the destination directory before naming a new or renamed diagram, then evaluate the filename in the context of the complete target path.
- Example: use `diagrams/use-case/aquila-graph-priority.puml`, not `diagrams/use-case/aquila-graph-priority-use-case-diagram.puml`.

## Diagram Navigation

- Before creating, selecting, reviewing, or modifying a diagram, consult the [diagram index](D:/Projects/aquila-graph/aquila-graph/diagrams/README.md).
- Use the routing triggers in the index to identify the appropriate view, its model, and its companion documentation.
- Update the index in the same change whenever a diagram is added, renamed, moved, or removed, or when its routing triggers or scope change materially.
- Keep the index limited to navigation and scope discrimination. Record model-specific explanations in the companion documentation located with the diagram.

## Repository Content

- Keep versioned documentation limited to current contracts, design decisions, build and deployment instructions, navigation, and outstanding product requirements.
- Keep one canonical source for each diagram. Put generated previews and exports under ignored build output such as `target/diagrams/`.
- Use Git history for previous source and model versions. Do not commit source snapshots, copied repository trees, review packets, session logs, or repeated historical exports.
- When removing or consolidating documentation, preserve applicable contracts and outstanding requirements in their owning documents and update inbound links.

## Evidence and Inference

- Support every substantive factual claim in user-facing responses and project documentation with a verifiable reference.
- Prefer primary sources. For local evidence, identify the exact file and the narrowest useful locator, such as a line, section, symbol, or page. For external evidence, provide a stable direct link to the source.
- End every substantive user-facing response with a `References` section containing the sources and verification evidence used.
- Never fabricate a citation, locator, command result, test result, or source relationship.
- Clearly label any claim that cannot be directly verified as `Inference:`. State the verified observations on which the inference depends and do not present the inference as established fact.
- Distinguish source statements, observed system behavior, and interpretation. A source establishes what the source says; it does not automatically establish actual system behavior.

## Evidence-Type Separation

- Match the verification method to the claim being made.
- Validate textual claims through source inspection, citation accuracy, and explicit argument. Do not create unit tests, integration tests, fixtures, benchmarks, executable scripts, hashes, or runtime checks to claim that prose is semantically true, complete, or well supported.
- Validate executable behavior through applicable code inspection, contracts, builds, static analysis, tests, and runtime observation. Do not treat prose, diagrams, plans, citations, or documentation review as proof that code behaves correctly.
- A mechanical tool may check only the property it actually observes. For example, a Markdown checker may establish formatting compliance, and a diagram parser may establish whether the diagram can be parsed; neither establishes the truth of the document or model.
- Do not add verifiable references, citations, source URLs, bibliography entries, or evidence narratives to comments in executable source code, tests, or scripts.
- Keep executable comments limited to local intent, invariants, constraints, and non-obvious implementation behavior.
- Record references and evidence for implementation decisions in auxiliary documentation, such as ADRs, design notes, evidence records, traceability documents, or research notes. Link the auxiliary document to the relevant durable component or decision without copying its bibliography into executable comments.

## References

- OpenAI Docs, [Custom instructions with AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md).
