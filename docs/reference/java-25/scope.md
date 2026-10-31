# Java 25 source-map scope and acceptance

[Source map](README.md) · [Publication identities](sources.md) · [Coverage](coverage.md)

## Purpose

Maintain a navigable map of official Java 25 sources and their documentary dependencies. The map
contains identifiers, edition constraints, exact links, referring locators, and short contexts.
Original publications remain authoritative. Local transcriptions, condensed replacement specifications,
implementation comparisons, and commits are outside this task.

## Authority and editions

- **Final platform:** [JSR 400 Final Release Specification][platform], September 3, 2025, with its
  eleven annexes and JSR 269 Maintenance Review 19 component revision.
- **Oracle Java SE/JDK 25:** the release-25 specifications, API, tools, and guide catalogs observed
  October 30, 2026. A release-numbered URL is not a frozen general-availability edition.
- **Other authorities:** the publisher of each standard, data set, or specification referenced by
  a Java source. Preserve the edition named by that source, including older editions.
- **Context:** JEPs, release explanations, historical references, and implementation guides retain
  their roles. JSR 400 section 5 explicitly identifies its JEP links as non-normative.

The [publication register](sources.md) records edition variants and source conflicts. A newer target,
matching title, or matching declaration name does not establish equivalence between publications.

## Included domains

| Source family | Included records | Owner |
| --- | --- | --- |
| Platform | Nine JSR 400 sections, eleven annexes, component revision | [Specifications](inventory/specifications.md) |
| Language and VM | JLS, JVMS, preview variants, chapter/section navigation, legal and PDF sources | [Specifications](inventory/specifications.md) |
| Public APIs | All indexed modules, packages, types, and page-owned members in both API editions | [API](inventory/api/README.md) |
| Auxiliary API material | Serialized forms, constants, doc-files, indexes, figures, and data resources | [API](inventory/api/README.md) |
| API changes | Final-platform Annex 2 comparison, including unavailable published targets | [Differences](inventory/api/differences.md) |
| Narrative specifications and tools | Multipart specification trees and all entries in the JDK 25 tool index | [Specifications](inventory/specifications.md) |
| Guides and releases | All families in the Oracle guide catalog, child documents, release information | [Guides](inventory/guides.md) |
| Dependencies | Declared specification indexes, source-body references, and standards named in prose | [Dependencies](inventory/dependencies.md) |

All members, rules, exceptions, examples, and notes belong to the linked source page even when they
have no separate map row. A type page is the enumeration unit for API contracts; a member index is
additional navigation. The JLS/JVMS indexes also provide section and example links. Source units are
not counts of Java features, AST nodes, or requirements.

## Source and dependency inventory model

Each record supplies, directly or through its owning family:

1. Published title or exact document/declaration identifier.
2. Authority, release, publication edition where stated, and exact published URL where supplied.
   If the source supplies no target URL, retain its literal identifier and the referring passage.
3. Parent catalog or referring passage establishing why it is included.
4. Short consultation context and relationship role.
5. Edition ambiguity, conflicting source identity, or unavailable destination when present.

**Source mapped** means that a consultation route is recorded. It does not mean its text was copied,
its claims were verified, or the complete normative meaning was resolved. **Edition variant** preserves
a distinct publication. **Context** identifies informative material. **Unresolved identity** preserves
an ambiguous edition or target. Availability is a separate property: a broken published URL remains a
mapped reference with its referring origin, rather than disappearing from the map.

## Inclusion and stopping rules

The following finite boundary is the project acceptance decision for this map. The official sources
identify the publications and their references; they do not prescribe this local documentation workflow.

1. **Fix the publication baseline.** Use the editions and observation date under
   [Authority and editions](#authority-and-editions) and the named
   [official roots](coverage.md#official-publication-roots). Later web revisions do not silently change this baseline.
2. **Enumerate the official publication.** Include every catalog entry, its constituent documents,
   and meaningful auxiliary resources in the publication family. Generated navigation and presentation
   assets remain distinguishable from substantive contracts. A child chapter continues the official
   publication; it is not an unrelated external dependency.
3. **Map direct documentary dependencies.** For references made by those sources, retain the referring
   document/section, target title or identifier, published URL where supplied, edition where stated,
   and short context. Include standards named in prose without a hyperlink. Use **not specified by
   the source** for an unstated edition or publisher locator; do not guess a newer edition.
4. **Stop at the external reference.** Recording that identity and origin completes the mapping
   obligation for the dependency. Do not recursively inventory its bibliography, links, or standards
   dependencies. If the Java publication explicitly incorporates an older section, map that exact
   section; this does not import the entire older release into the source population.
5. **Keep interpretation separate.** Determining the exact provisions needed to apply an external
   rule belongs to the analysis that uses it. Such work does not keep this source map indefinitely open.

For example, a Java source's reference to a particular Unicode edition requires that edition and the
originating passage in the map. It does not require cataloging everything referenced by the Unicode
publication. JSR 400 section 7's incorporation of named Java SE 9 module subsections requires links to
those subsections, not a new inventory of all Java SE 9 documentation. See the
[publication reference register](inventory/dependencies/publication-references.md).

## Completion criteria

1. Reconcile every selected official catalog entry with a named map owner; retain auxiliary material.
2. Preserve exact destinations and source origins through reorganization, including query strings,
   fragments, nested type names, historical editions, and unavailable publisher references.
3. Keep release/edition information explicit or inherited from an unambiguous family declaration.
4. Register direct dependencies with the fields above, including source-provided uncertainty.
5. Provide consultation contexts without substituting abridged prose for the official contract.
6. Check Markdown formatting and local navigation independently of documentary completeness.

Completion is recorded in [coverage](coverage.md#closure-status). A missing catalog entry, lost source
origin, changed destination, omitted explicit dependency, or invented edition is a **mapping defect**
and must be corrected. These defects cannot be relabeled as publication limitations to close the map.

An unavailable published target, an edition not stated by the source, or a disagreement between
official sources is a **recorded publication limitation** when its origin, exact published identity,
and observed uncertainty are retained. It does not block map completion. It may constrain a later
interpretation; no claim of source accessibility, edition resolution, or correctness follows from closure.

An assertion of 100% must name its finite denominator: catalog entries in the fixed publication
baseline. It does not mean all conceivable implicit dependencies or implementation coverage.
Full-text copying and transitive normative interpretation are not acceptance requirements.

## Reopening the map

Reopen the affected records for a concrete omission or incorrect reference evidenced by a source in
the fixed baseline, or when an explicit scope/edition update is requested. An omission is corrected
under the existing scope; it does not require a new permission request. A newly encountered external
bibliography, an unresolved source ambiguity already recorded, or a hypothetical dependency is not
an automatic reason to restart the inventory. Retain the other reconciled source families.

## References

- [Final Java SE 25 specification][platform], especially sections 2, 4, and 5.
- [Oracle specifications][map-link-1].
- [API source catalogs](inventory/api/README.md#publication-boundary).
- [Guide source catalogs](inventory/guides.md#guide-families).

[platform]: https://cr.openjdk.org/~iris/se/25/latestSpec/

[map-link-1]: https://docs.oracle.com/en/java/javase/25/docs/specs/index.html
