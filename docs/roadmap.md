# Development Roadmap

This roadmap summarizes development priorities and preserved product boundaries.
It does not assign delivery dates or certify implementation acceptance. The
linked contracts own the detailed requirements.

<a id="remaining-source-model-work"></a>

## Source model delivery status

Preserve independent AST qualification and CFG derivation, exact source identity,
written ordering, and immutable queries after compiler closure. The
[Java analysis design](design/java-analysis.md#qualified-construction-and-enclosing-type-composition)
retains the outstanding constructor-evidence and recursive-type consistency
obligations. Changes must preserve valid raw inputs and reject contradictory
supplied evidence without weakening diagnostics.

The [module service and provider identity contract](design/java-analysis.md#module-service-and-provider-identities)
defines required endpoint facts for `java-source-expanded/8`. That profile
retains its qualification rules in the [active retained format](design/ast-value-format.md),
which also defines refusal of retired envelopes. Prior stored results retain
their original bytes and identities.

## Frontend maintainability

Keep profile-derived configuration immutable, translation state local to its
unit, and declaration, initialization, and executable responsibilities cohesive.
Preserve explicit diagnostic ownership, local outcomes, written attachments, and
all affected consumers when improving the frontend. The
[architecture and ownership contract](design/java-analysis.md#architecture-and-ownership)
and [compiler lifecycle](design/java-analysis.md#compiler-lifecycle) define the
shared boundaries.

## Verification priorities

Strengthen isolated regression evidence for written source values, expression
types, constructor correlation, declaration ownership, and their composition.
Preserve existing acceptance within its recorded scope. The
[development verification scope](build-quality.md#development-verification-scope)
keeps real-environment campaigns deferred until an explicit decision; source
inspection and isolated checks do not establish real database or process behavior.

## Conditions for expansion

Evolve environment and software signatures through explicit compatibility
decisions for effective dependency identities. Path portability is a separate
scope. The [source snapshot contract](design/source-snapshots.md) owns captured
sources and environment identity.

Preserve required versus optional source facts, exact identities and ordering,
public graph contracts, truthful failures, and distinct publication gates.
Completed-AST retention remains opt-in. Incomplete-AST retention, automatic saving,
new CLI behavior, a new storage backend, bytecode input, other languages or Java
releases, preview features, and whole-program analysis require separate
requirements. Existing quality gates remain in force.

## References

- [Java analysis design](design/java-analysis.md), source profiles, construction obligations, and ownership.
- [Analysis contract](contracts/analysis.md#target-publication-and-retention), independent outcomes and publication gates.
- [Source snapshots](design/source-snapshots.md), captured sources and environment identity.
- [Build quality](build-quality.md#development-verification-scope), active verification and deferred integrations.
- [Neo4j schema](repository/neo4j-schema.md#target-ast-retention-schema), retained AST identity and format.
