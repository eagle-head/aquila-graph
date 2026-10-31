# Java 25 source and dependency inventory

[Source map](../README.md) · [Scope and acceptance](../scope.md) · [Publication identities](../sources.md)

This inventory maps official source documents and documentary relationships. Full texts remain at
their publishers. It records consultation routes, versions, contexts, and unresolved identities;
it does not compare sources with project implementation. See [coverage](../coverage.md) for the
official populations and the limits of the completeness claim.

## Inventory contents

| Inventory | Responsibility |
| --- | --- |
| [Platform and narrative specifications](specifications.md) | JSR 400, component revision, JLS, JVMS, previews, narrative annexes, tools. |
| [Public APIs](api/README.md) | Every indexed module, package, type, and the auxiliary API documents. |
| [API publication differences](api/differences.md) | Explicit source records for the final platform's Annex 2 comparison. |
| [Documentary dependencies](dependencies.md) | Declared external specifications and source-origin reference relationships. |
| [Guides and release documents](guides.md) | Oracle release-25 guide families, all child documents, and release information. |
| [Specification supporting resources](specification-assets.md) | Figures, alternate PDFs, and other resources cited by specifications. |
| [API supporting resources](api/assets.md) | Figures, structured data, examples, and presentation assets referenced by APIs. |
| [Guide supporting resources](guides/assets.md) | Figures and other content assets belonging to guide documents. |

## Authoritative publication roots

The inventory starts with the [JSR 400 final specification][platform], its incorporated component
revision and annexes, the [Oracle specifications catalog][specs], the [complete API publication][api],
the [tool index][tools], and the [guide catalog][guides]. Their child inventories preserve the
actual source identities rather than using a hand-selected feature list as the source universe.

The final platform publication and the current Oracle publication are distinct. The former
identifies the final Java SE 25 baseline; the latter is the release-25 documentation observed on
October 30, 2026, including later publication revisions and JDK-specific material. Exact versions
and source differences remain attached to their records. The [scope](../scope.md#authority-and-editions)
defines their roles and precedence.

## Enumeration units

| Source population | Enumerated units | Owning inventory |
| --- | --- | --- |
| Final platform API declarations | 21 modules, 182 packages, 4,293 public type pages | [API](api/README.md) |
| Combined Oracle Java SE/JDK API declarations | 58 modules, 229 packages, 4,979 public type pages | [API](api/README.md) |
| Current Oracle API HTML bodies | 5,332 module, package, type, shared, and auxiliary pages | [API](api/README.md) |
| Final platform API HTML bodies | 4,562 module, package, type, shared, and auxiliary pages | [Final API](api/platform-auxiliary.md) |
| Final platform API comparison | 4,412 retrieved pages and 26 unavailable referenced addresses | [Annex 2](api/differences.md) |
| Current narrative and command publication | 82 HTML documents | [Specifications](specifications.md) |
| Final-publication narrative trees | 42 HTML documents | [Specifications](specifications.md) |
| Language and VM | 19 JLS and seven JVMS chapters, plus indexes, legal material, PDFs, and preview variants | [Specifications](specifications.md) |
| Tool entries | 36 entries referring to 35 distinct command pages | [Specifications](specifications.md) |
| Guide publication | 17 families; 361 TOC-listed HTML documents and 51 figure-description documents | [Guides](guides.md) |
| Release/readme collection | 12 source documents | [Release sources](guides/releases.md) |
| Declared API external specifications | 109 entries and 484 referring API locators | [Dependencies](dependencies.md) |

These populations overlap. For example, the final platform's declarations occur within the
combined Oracle API inventory; command pages are included in the 82-document narrative/tool
population. Shared targets can be cited by many sources. The counts must not be added to produce
a purported number of independent features, requirements, or unique documents across all editions.

Module/package/type records identify complete source pages, including their member sections.
Chapter and guide records identify their complete document contents, not merely their headings.
The JLS and JVMS have detailed links to official sections in the
[language contents](../language/README.md) and [VM contents](../virtual-machine/README.md).
Auxiliary document files, serialized forms, constants, and source-defined conditions remain
explicitly in scope even when a class index does not list them.

## Dependency records

The [dependency inventory](dependencies.md) preserves both the official External Specifications
index and additional references found in source prose. Each relationship points back to its
originating page/declaration/section and retains its exact destination. The separate guide
reference registers preserve their own source origins without duplicating those records here.

Relationships have different meanings: an explicit specification declaration, a reference that
delegates a rule, an auxiliary/containing contract, or informative context. A source link alone
does not determine which provisions of an external standard apply. The [scope's stopping and
completion rules](../scope.md#inclusion-and-stopping-rules) require that distinction to remain visible.

The [finite stopping rule](../scope.md#inclusion-and-stopping-rules) maps the official publication and
records its direct dependencies with their origins. The external target is the stopping point;
its own reference chain is not another inventory obligation. Source ambiguities remain recorded
without blocking the [map's closure](../coverage.md#closure-status). Interpretation of external
clauses belongs to the activity that needs them.

## Mapping and edition states

The [scope](../scope.md#source-and-dependency-inventory-model) defines **Source mapped**,
**Edition variant**, **Context**, and **Unresolved identity**. Mapping records a source route;
it never certifies an interpretation or changes an unavailable target into an accessible one.
All API, tool, guide, and supporting-resource records are links rather than local contract bodies.

Multiple publication identities are preserved for the primitive-pattern preview. The current
Oracle preview, the final-platform preview, and the earlier local source must not be silently
treated as one identical text. Their versions and source identities appear in
[the preview inventory](specifications.md).

The [known source inconsistencies](../sources.md#known-source-inconsistencies) retain conflicting
Oracle references and notices with their opposing specification locators. These discrepancies
must not be converted into invented features or silently corrected quotations.

Two final-publication navigation destinations returned HTTP 404 during inspection and are retained
as unresolved navigation records in the specification inventory. This does not turn successfully
retrieved substantive annexes into missing specifications. Transient transport failures are not
evidence that a document does not exist.

The [final API auxiliary inventory](api/platform-auxiliary.md) separately records two unavailable
provider-module references from `java.naming` to JDK modules outside that publication's module set.
The [Annex 2 inventory](api/differences.md) owns unavailable links in the comparison publication.
Their original addresses and source origins remain visible; matching names in another publication
are not treated as proof that the unavailable destination was inspected.

## Using the map

Open the original source to read its full rules, exceptions, examples, tables, and definitions.
Keep its edition and referring passage attached to any later conclusion. A short context in this
map explains where to look; it is not an abridged replacement for the source.

## References

- [Java SE 25 final platform specification][platform].
- [Oracle specifications][specs], [API][api], [tools][tools], and [guides][guides].
- [Reference scope](../scope.md) and the source-specific inventories linked above.

[platform]: https://cr.openjdk.org/~iris/se/25/latestSpec/
[specs]: https://docs.oracle.com/en/java/javase/25/docs/specs/index.html
[api]: https://docs.oracle.com/en/java/javase/25/docs/api/index.html
[tools]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/index.html
[guides]: https://docs.oracle.com/en/java/javase/25/books.html
