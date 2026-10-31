# Source-map coverage and closure

[Source map](README.md) · [Scope](scope.md) · [Publication identities](sources.md)

## Meaning of completeness

The claim assessed here is **documentary mapping**: whether the official catalog entries have
consultation routes, edition identities, and owning registers. It is not a claim that the map
reproduces every document, resolves every external clause, or establishes AST implementation coverage.

The catalog populations below were checked against the official indexes observed October 30, 2026.
They provide the finite denominator for source enumeration. The
[stopping rule](scope.md#inclusion-and-stopping-rules) ends dependency mapping at the recorded external
identity and originating passage. Interpretation of external standards is a separate activity.

## Closure status

**Status: closed for the fixed publication baseline and direct-reference map described below.**
Known mapping defects at closure: none. The catalog reconciliation has no unmapped entries, and the
registered source destinations and dependency origins are retained in their owning inventories.

| Acceptance responsibility | Disposition | Evidence route |
| --- | --- | --- |
| Publication editions and official roots | Fixed | [Identities](sources.md), [root catalog](#official-publication-roots) |
| Official catalog entries and subsidiary documents | Mapped | [Catalog reconciliation](#catalog-reconciliation), [subsidiary collections](#sources-beyond-the-primary-catalogs) |
| Declared and source-body dependencies | Registered with origins | [Dependency registers](inventory/dependencies.md) |
| Dependencies named without hyperlinks | Registered with source wording and edition limits | [Textual references](inventory/dependencies/textual-references.md) |
| Unavailable or ambiguous source identities | Recorded publication limitations | [Limitations](#recorded-publication-limitations) |
| Formatting and local navigation | Checked separately from source interpretation | [Map navigation](README.md) |

Closure is the project's documentary acceptance decision under the finite scope. It does not certify
every interpretation of the linked publications. The recorded limitations remain visible and do not
constitute an unfinished requirement to reproduce documents or expand external reference chains.
A concrete missing explicit reference still requires correction under the
[reopening rule](scope.md#reopening-the-map).

## Official publication roots

| Official root | Edition and scope | Map owner |
| --- | --- | --- |
| [JSR 400 final publication][platform] | September 3, 2025; sections, component JSR, annexes | [Specifications](inventory/specifications.md) |
| [Oracle specifications][specs] | Java SE/JDK 25 narrative source catalog | [Specifications](inventory/specifications.md) |
| [Oracle API][api] | Combined release-25 API, including JDK modules | [API](inventory/api/README.md) |
| [Final-platform API][final-api] | JSR 400 Annex 1 | [API editions](inventory/api/README.md#publication-boundary) |
| [Final API differences][differences] | Annex 2, Java 24 to Java 25 | [Differences](inventory/api/differences.md) |
| [JLS contents][jls] and [JVMS contents][jvms] | Java SE 25 chapters, sections, appendices | [JLS](language/README.md), [JVMS](virtual-machine/README.md) |
| [JDK tool index][tools] | Release 25 commands, including Windows-only entries | [Tools](inventory/specifications.md#jdk-tool-documents) |
| [Guide catalog][guides] and [documentation home][home] | Release 25 guide families and related resources | [Guides](inventory/guides.md) |
| [JDK 25 release collection][releases] | Release/update-specific notes and referenced readme documents | [Release sources](inventory/guides/releases.md) |
| [Oracle external specifications][external] | Declared specification relationships and API origins | [Dependencies](inventory/dependencies.md) |
| [Final API external specifications][final-external] | Separate Annex 1 dependency index | [Dependencies](inventory/dependencies.md) |

## Catalog reconciliation

The official indexes, rather than a selected list of language features, define these populations.
The two API editions overlap; do not add their counts. A page record includes all member sections in
that official page. The JLS/JVMS section indexes provide finer navigation without reproducing clauses.

| Official population | Entries represented | Consultation route |
| --- | --- | --- |
| JSR 400 | Nine sections, eleven annexes, JSR 269 MR 19 | [Platform records](inventory/specifications.md) |
| Oracle API | 58 modules, 229 packages, 4,979 type pages | [Module/package/type records](inventory/api/README.md) |
| Final-platform API | 21 modules, 182 packages, 4,293 type pages | [Separate final identities](inventory/api/README.md) |
| Oracle declared external specifications | 109 entries and 484 referring locators | [Declared relationships](inventory/dependencies.md#declared-external-specifications) |
| Final declared external specifications | 106 entries and 474 referring locators | [Declared relationships](inventory/dependencies.md#declared-external-specifications) |
| JLS contents | 19 chapters; 599 distinct contents destinations including fragments | [JLS navigation](language/README.md) |
| JVMS contents | Seven chapters; 446 distinct contents destinations including fragments | [JVMS navigation](virtual-machine/README.md) |
| Tools | 36 entries, 35 command pages; javaw shares the java page | [Tool records](inventory/specifications.md#jdk-tool-documents) |
| Guides | 17 families, 361 TOC-listed HTML documents | [Guide catalogs](inventory/guides.md#guide-families) |

These cataloged populations have no unmapped entries in the reconciliation. This is a bounded
enumeration result, not a percentage of Java rules. Publication-root links additionally retain
historical comparisons, alternate directory addresses, figures, and portal navigation in the
[publication reference register](inventory/dependencies/publication-references.md).

## Sources beyond the primary catalogs

The inventories also retain the subsidiary documents and relationships discovered during the
source review. The earlier reference records were preserved during reorganization, including exact
query strings, fragments, unavailable addresses, and source-origin relationships.

| Source collection | Mapped material | Owner |
| --- | --- | --- |
| Oracle API bodies | 5,332 module/package/type/shared/auxiliary pages | [API](inventory/api/README.md) |
| Final API bodies | 4,562 pages; separate publication identity | [Final auxiliaries](inventory/api/platform-auxiliary.md) |
| API comparison | 4,412 retrieved pages and 26 unavailable published addresses | [Differences](inventory/api/differences.md) |
| Narrative sources | 82 Oracle and 42 final-publication HTML documents | [Specifications](inventory/specifications.md) |
| Guide companions | 51 figure-description documents and 99 content-asset references | [Guides](inventory/guides.md) |
| API references beyond the declared index | Source-body targets with referring declarations and anchors | [Dependency registers](inventory/dependencies.md) |
| Standards named in prose | Named editions, algorithms, registries, and unresolved identities | [Textual references](inventory/dependencies/textual-references.md) |

Supporting resources and navigation have their own records. They are not additional Java features.
The narrative and guide records keep publication-defined titles, editions, child documents, and
dependency contexts; document counts do not establish that every normative relationship is understood.

## Recorded publication limitations

These records describe the sources, not omitted map entries. They remain relevant to later use of
the documents without blocking the finite mapping decision.

- **Publisher-unavailable references:** the final narrative tree has two published HTTP 404
  navigation targets; final API references include two HTTP 404 provider-module targets; Annex 2 has
  26 HTTP 404 targets. Their precise addresses and origins remain in the owning inventories. These
  observations describe access during the source review, not permanent server behavior.
- **Additional contextual project source:** the OpenJDK JDK 25 project page returned HTTP 403 during
  this catalog review. Its route is recorded in [publication references](inventory/dependencies/publication-references.md),
  but its outgoing links were not verified through that route.
- **Edition ambiguities:** some external references name a standard without a dated edition, or use
  an unversioned publisher URL. [Textual references](inventory/dependencies/textual-references.md)
  preserve the referring passage and what remains unspecified.
- **Source conflicts:** the annotation-attribute reference, java.base preview notice, and OECF
  standard identifier retain their opposing locators in [source identities](sources.md).

## Interpretation outside map acceptance

The map does not inventory the complete bibliographies or dependency chains of external standards.
Those targets terminate the direct-reference records. Determining which external clauses and further
dependencies are needed for a particular Java rule belongs to the analysis using that rule. This is
outside map acceptance, not a remaining mapping task or a claim that such dependencies do not exist.

## Maintaining the map

Correct concrete omissions or incorrect references in the affected source family. For a requested
edition or scope update, use the official root catalogs and record the revised publication boundary.
Preserve the original URL alongside any verified replacement, and state why an edition is applicable.
An explicitly referenced source omitted from the fixed baseline is a defect to correct; a link found
only by following an external bibliography does not automatically expand the map. Follow the
[reopening rule](scope.md#reopening-the-map). Formatting and link checks remain artifact checks.

## References

[platform]: https://cr.openjdk.org/~iris/se/25/latestSpec/
[specs]: https://docs.oracle.com/en/java/javase/25/docs/specs/index.html
[api]: https://docs.oracle.com/en/java/javase/25/docs/api/index.html
[final-api]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/index.html
[differences]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/index.html
[jls]: https://docs.oracle.com/javase/specs/jls/se25/html/index.html
[jvms]: https://docs.oracle.com/javase/specs/jvms/se25/html/index.html
[tools]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/index.html
[guides]: https://docs.oracle.com/en/java/javase/25/books.html
[home]: https://docs.oracle.com/en/java/javase/25/
[releases]: https://www.oracle.com/java/technologies/javase/25u-relnotes.html
[external]: https://docs.oracle.com/en/java/javase/25/docs/api/external-specs.html
[final-external]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/external-specs.html
