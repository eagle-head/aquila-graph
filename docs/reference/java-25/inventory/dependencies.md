# Java 25 documentary dependencies

[Source map](../README.md) · [Scope](../scope.md) · [Specification inventory](specifications.md)

This inventory records dependencies between published documents and the Java passages that cite
them. It separates explicit External Specifications declarations from ordinary source references,
contractual incorporations, contextual material, and unresolved edition identities. A recorded
link is not automatically an incorporation of every provision of its target.

The source boundary is the official release-25 API collection, language and VM chapters, preview
specification, narrative specifications, tools, and guides registered in this inventory. The
current Oracle publication and the published JSR 400 copy remain distinct editions. Sources were
consulted on October 30, 2026. This inventory registers sources and relations; it does not add their
full text to this map or compare them with a project implementation.

## Publication-root relationships

The [publication reference register](dependencies/publication-references.md) retains links from
JSR 400 itself and the Oracle documentation/release portals. It includes historical comparisons,
supporting assets, and navigation separately from the API and narrative reference collections.
Corporate and service links are Context, not additional Java requirements.

## Declared external specifications

The [Oracle External Specifications index][external-index] declares 109 specification entries
with 484 referring API locators. Every entry and locator is preserved in these registers:

| Register | Specification entries | API locators |
| --- | ---: | ---: |
| [IETF specifications](dependencies/ietf.md) | 76 | 203 |
| [Unicode specifications](dependencies/unicode.md) | 10 | 36 |
| [Java narrative specifications](dependencies/java.md) | 7 | 216 |
| [Web specifications](dependencies/web.md) | 8 | 14 |
| [Other standards and registries](dependencies/other-standards.md) | 8 | 15 |

Each record includes the exact source title, target URL, and referring declaration anchors.
This is the complete declared index, including older standards and unversioned URLs. The
Java API help describes this page as an index of external specifications; it does not promise
to index every dependency expressed in ordinary prose. [Source: API Help][api-help].

The [published platform's own declared index][platform-external-index] has 106 entries and 474
referring API locators. Its records remain separate from the current combined Oracle collection:

| Published platform register | Specification entries | API locators |
| --- | ---: | ---: |
| [IETF specifications](dependencies/platform-ietf.md) | 75 | 202 |
| [Unicode specifications](dependencies/platform-unicode.md) | 10 | 36 |
| [Java narrative specifications](dependencies/platform-java.md) | 6 | 209 |
| [Web specifications](dependencies/platform-web.md) | 7 | 12 |
| [Other standards and registries](dependencies/platform-other-standards.md) | 8 | 15 |

## Current Oracle API source references

The complete API source inventory also records ordinary outbound references. The following
registers preserve 1,263 distinct targets and 4,300 distinct target/source/anchor/label relations
from the 5,332 current API HTML documents. They include the declared-index references as well as
additional links; the counts must not be added to the preceding table as disjoint populations.

| Register | Targets | Relations |
| --- | ---: | ---: |
| [Language and VM specifications](dependencies/api-language-and-vm.md) | 509 | 1,611 |
| [Java narrative specifications](dependencies/api-java-specifications.md) | 101 | 531 |
| [Java guides](dependencies/api-java-guides.md) | 20 | 67 |
| [IETF specifications](dependencies/api-ietf.md) | 194 | 689 |
| [Web and XML sources](dependencies/api-web-and-xml.md) | 202 | 821 |
| [Unicode and registries](dependencies/api-unicode-and-registries.md) | 53 | 162 |
| [Other Java sources](dependencies/api-java-context.md) | 135 | 333 |
| [Other external authorities](dependencies/api-other-authorities.md) | 49 | 86 |

Each origin link retains its nearest identified source anchor. Identical target/source/anchor/label
occurrences are combined. Distinct sections, labels, URLs, and editions remain separate. Neither
HTTP/HTTPS variants nor historical/current addresses are silently merged.

Six recurring footer pairs are recorded here once instead of once per API page:

- [Report a bug or suggest an enhancement][bug-report]: service navigation; **Context**.
- [Java SE Documentation][documentation-home]: collection navigation; **Context**.
- [Other versions][other-versions]: release navigation; **Context**.
- [Copyright][copyright]: legal material; consultation remains at the official source.
- [License terms][license]: legal material; authority remains the linked Oracle terms.
- [Documentation redistribution policy][redistribution]: legal material; authority remains the linked Oracle policy.

Only these exact label/URL footer pairs are removed from the repeated API reference registers.
Other external references, including historical, informative, and potentially normative links,
remain visible. This registration process establishes the observed link inventory; it does not
prove the semantic completeness or correctness of the linked documents.

## Published platform API source references

The [published JSR 400 final platform API][platform-api] is a separate source identity. Its
4,562 successfully retrieved HTML documents expose 1,155 distinct outbound targets and 3,962
distinct target/source/anchor/label relations. These records retain the final-publication origins
instead of replacing them with similarly named pages in the current combined Oracle API:

| Register | Targets | Relations |
| --- | ---: | ---: |
| [Language and VM specifications](dependencies/platform-api-language-and-vm.md) | 457 | 1,473 |
| [Java narrative specifications](dependencies/platform-api-java-specifications.md) | 71 | 468 |
| [Java guides](dependencies/platform-api-java-guides.md) | 17 | 62 |
| [IETF specifications](dependencies/platform-api-ietf.md) | 185 | 669 |
| [Web and XML sources](dependencies/platform-api-web-and-xml.md) | 192 | 718 |
| [Unicode and registries](dependencies/platform-api-unicode-and-registries.md) | 53 | 162 |
| [Other Java sources](dependencies/platform-api-java-context.md) | 131 | 325 |
| [Other external authorities](dependencies/platform-api-other-authorities.md) | 49 | 85 |

Five recurring footer pairs are registered once: bug reporting, Java SE Documentation, copyright,
license terms, and redistribution policy. The final-publication [copyright source][platform-copyright]
has its own identity; the other four targets match the corresponding addresses listed above.
The final-publication footer does not contain the current Oracle collection's Other versions link.

The two API collections overlap, but their source documents and reference sets are not asserted
to be identical. Differences in collection membership, edition, and auxiliary destinations are
recorded in the [API inventory](api/README.md). Counts from the two editions are not a count of
independent normative requirements.

## Annotated API-difference references

[JSR 400 Annex 2][api-differences] publishes an annotated comparison of `jdk-24-ga` and `jdk-25+36`.
Its 4,412 successfully retrieved HTML pages expose 587 outbound targets and 1,715 distinct
target/source/heading/label relations. These are **Context** records: a reference in removed or
changed text does not automatically establish a Java 25 requirement.

| Annotated-difference register | Targets | Relations |
| --- | ---: | ---: |
| [Language and VM specifications](dependencies/differences-api-language-and-vm.md) | 416 | 1,154 |
| [Java narrative specifications](dependencies/differences-api-java-specifications.md) | 47 | 239 |
| [Java guides](dependencies/differences-api-java-guides.md) | 18 | 82 |
| [IETF specifications](dependencies/differences-api-ietf.md) | 50 | 69 |
| [Web and XML sources](dependencies/differences-api-web-and-xml.md) | 10 | 15 |
| [Unicode and registries](dependencies/differences-api-unicode-and-registries.md) | 16 | 28 |
| [Other Java sources](dependencies/differences-api-java-context.md) | 21 | 70 |
| [Other external authorities](dependencies/differences-api-other-authorities.md) | 9 | 58 |

The records retain the source's annotated link labels, including old/new destination descriptions,
and name the nearest heading. Changed footer links are retained in this collection. Some published
URLs contain literal `&amp;` after HTML interpretation because the source double-escapes an
ampersand; the [ObjectInputFilter comparison][filter-differences] provides an example. The inventory
preserves the observed URL rather than silently applying another decoding step. Intended corrected
destinations require separate resolution. Retrieval failures and internal destination inconsistencies
belong to the [annotated API source inventory](api/differences.md).

## Narrative, language, and VM references

These registers preserve outward references from all 82 current narrative/tool HTML documents,
42 narrative documents in the published JSR 400 copy, all 19 JLS chapters, all seven JVMS chapters,
and the separately published preview document:

- [Technical standards and format references](dependencies/narrative-standards.md): 128 targets,
  338 distinct target/source-section/label relations.
- [Context and cross-release references](dependencies/narrative-context.md): 44 targets,
  56 distinct target/source-section/label relations.

Links into the current Java 25 API, guides, language/VM specifications, and JSR 400 publication
tree belong to their source inventories. External target editions and origin sections are retained
in these registers. Guide-origin relations are preserved separately in the
[guide reference registers](guides.md#guide-reference-relationships).

## Standards named without hyperlinks

The [textual reference register](dependencies/textual-references.md) records standards identified
in source prose without a corresponding hyperlink. Each entry preserves the referring passage
and any edition actually specified. This complements the link registers: the presence or absence
of a hyperlink does not determine whether a source delegates a rule to another authority.

## Direct obligations for the language and AST reference

The following dependencies are material to the original language/AST objective. Their role is
established by the referring contracts, rather than by the mere presence of a hyperlink.
Each item supplies a consultation route and the reason for following it.

### Lexical classification and numeric semantics

- **JLS 3.1 → Character → Unicode 16.0.** The JLS delegates the Unicode version to `Character`;
  its conformance section identifies version 16.0 and the Unicode Character Database.
  [JLS 3.1][jls-unicode], [Character][character], and the [Unicode 16.0 component index][unicode16]
  identify the edition and its constituent data. The target includes the applicable Unicode
  character data, not a replacement by whichever release is currently newest.
- **JLS 3.8 → Character identifier methods.** Identifier-start, identifier-part, and ignorable
  character rules depend on those API contracts. [JLS 3.8][jls-identifiers] identifies the delegated API contract; consult it with the cited
  character data.
- **JLS 4.2.3 and JVMS 2.3.2 → IEEE 754-2019.** The Java specifications identify the floating-point
  formats and their governing edition. [JLS 4.2.3][jls-floating], [JVMS 2.3.2][jvms-floating],
  and the [IEEE source recorded by the API][ieee] establish this dependency. A bibliography entry
  is not a substitute for the applicable standard's provisions or for Java's stated qualifications.

The [Unicode declared-specification register](dependencies/unicode.md) includes a historical
Unicode 3.1.0 reference and several unversioned reports. These do not replace the current
`Character` version declaration. Each referring method determines the applicable property or
rule; Java identifier predicates must not be replaced indiscriminately by Unicode identifier
predicates.

### Documentation-comment syntax and interpretation

- **The doctree package → Documentation Comment Specification for the Standard Doclet.** This
  dependency is declared in the [official package contract][doctree]. The separate specification
  owns comment association, tags, references, inheritance, and snippet processing. It is a
  standard-doclet contract, not a requirement that every custom doclet use the same syntax.
- **Markdown comments → CommonMark 0.31.2 and GFM tables.** Oracle pins the
  [CommonMark source][commonmark] through its versioned URL, but cites an unversioned
  [GFM table-extension address][gfm-tables]. The GFM page identifies version 0.29-gfm at inspection;
  this is an observed target edition, not an explicit version pin in the Java contract. Java
  specifies table syntax and its own additions, rather than every GFM extension.
  [Origin: Markdown documentation comments][markdown-comments].
- **HTML comment content → HTML Living Standard; DocTrees entities → HTML 5.2.** The
  [documentation-comment HTML section][html-comments] and
  [DocTrees.getCharacters][doc-characters] cite different HTML authorities. Retain that distinction
  rather than replacing both targets with one latest HTML specification.
- **Snippet processing → String operations.** The [snippet clauses][snippet] refer to
  `String.stripIndent` and `String.replaceAll`; their release-25 API contracts are dependencies
  of the specified transformations. `@value` formatting refers to `java.util.Formatter`.

The [narrative reference register](dependencies/narrative-standards.md) retains the exact
CommonMark fragment links and the source-defined HTML and GFM addresses. Rendering libraries
mentioned as examples, such as Prism and Mermaid, are contextual references, not Java language
requirements.

### Compiler, processing, and documentation-tool contracts

- **Language model → annotation-processing environment.** The [java.compiler module][java-compiler]
  includes `javax.annotation.processing` and `javax.tools` as well as the language-model packages.
  [Processor][processor] owns processing-round and annotation-discovery rules;
  [Filer][filer] owns generated-file restrictions. Their complete contracts remain at the linked sources.
- **Compiler tree access → JavaCompiler and jdk.compiler.** The [module contract][jdk-compiler]
  connects `JavaCompiler.CompilationTask` with `JavacTask` and specifies API invocation restrictions.
  It delegates the full command-line option contract to [javac][javac].
- **Source diagnostics → javax.tools.Diagnostic.** [Diagnostic][diagnostic] owns offset,
  line/column, and unavailable-position conventions; those are not supplied solely by node APIs.
- **Documentation processing → jdk.javadoc.doclet.** The [doclet package][doclet] defines specified,
  selected, and included elements; [DocletEnvironment][doclet-environment] supplies the model/tree
  context. These source records belong to the documented JDK tool API layer.

### Serialized forms of the selected language-model types

The following seven selected classes refer to additional serialized-form contracts. Their type
pages and their serialized-form target sections have separate official locators:

- [UnknownEntityException][serial-unknown-entity].
- [UnknownAnnotationValueException][serial-unknown-annotation].
- [UnknownDirectiveException][serial-unknown-directive].
- [UnknownElementException][serial-unknown-element].
- [UnknownTypeException][serial-unknown-type].
- [MirroredTypeException][serial-mirrored-type].
- [MirroredTypesException][serial-mirrored-types].

These are contracts of already selected types, even though they introduce no Java grammar
production. The serialized-form page belongs to the source inventory as an auxiliary API
document, rather than being discarded as navigation.

## Edition and classification obligations

An unversioned target has an unresolved edition when neither the referring contract nor the
publisher identifies the applicable version. Preserve the source's URL and any edition wording.
**Edition variant** records distinct publications; it does not authorize substitution by the newest.

A source-body link establishes a relationship, not the exact normative scope of its destination.
Where a Java rule incorporates an external clause, its necessary definitions and dependencies also
matter. Historical references, website navigation, and illustrative libraries have different roles.

For map acceptance, stop when the explicitly referenced identity, originating passage, stated edition,
and consultation route or source-provided limitation are recorded. An unspecified edition stays
unspecified; it is not a requirement to guess or select the newest standard. The external source's
bibliography does not open another inventory campaign. This is the project's
[finite stopping rule](../scope.md#inclusion-and-stopping-rules), not a claim that external rules lack dependencies.

The [closure record](../coverage.md#closure-status) separates mapping defects from publication
limitations. A missing explicit reference must be corrected; resolving the normative meaning of every
external clause is outside this map's acceptance criteria.

## References

The detailed registers cite each target and each originating passage. The principal authorities
and the direct language/AST dependency locators used above are listed below.

[external-index]: https://docs.oracle.com/en/java/javase/25/docs/api/external-specs.html
[platform-api]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/index.html
[platform-external-index]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/external-specs.html
[platform-copyright]: https://cr.openjdk.org/~iris/se/25/latestSpec/legal/copyright.html
[api-differences]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/index.html
[filter-differences]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.base/java/io/ObjectInputFilter.html
[api-help]: https://docs.oracle.com/en/java/javase/25/docs/api/help-doc.html#external-specs
[bug-report]: https://bugreport.java.com/bugreport/
[documentation-home]: https://docs.oracle.com/pls/topic/lookup?ctx=javase25&id=homepage
[other-versions]: https://docs.oracle.com/en/java/javase/index.html
[copyright]: https://docs.oracle.com/en/java/javase/25/docs/legal/copyright.html
[license]: https://www.oracle.com/java/javase/terms/license/java25speclicense.html
[redistribution]: https://www.oracle.com/technetwork/java/redist-137594.html
[jls-unicode]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.1
[jls-identifiers]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.8
[character]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html
[unicode16]: https://www.unicode.org/versions/Unicode16.0.0/
[jls-floating]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.2.3
[jvms-floating]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.3.2
[ieee]: https://standards.ieee.org/ieee/754/6210/
[doctree]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/package-summary.html
[commonmark]: https://spec.commonmark.org/0.31.2/
[gfm-tables]: https://github.github.com/gfm/#tables-extension-
[markdown-comments]: https://docs.oracle.com/en/java/javase/25/docs/specs/javadoc/doc-comment-spec.html#markdown-documentation-comments
[html-comments]: https://docs.oracle.com/en/java/javase/25/docs/specs/javadoc/doc-comment-spec.html#html-content
[doc-characters]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html#getCharacters(com.sun.source.doctree.EntityTree)
[snippet]: https://docs.oracle.com/en/java/javase/25/docs/specs/javadoc/doc-comment-spec.html#snippet
[java-compiler]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/module-summary.html
[processor]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/annotation/processing/Processor.html
[filer]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/annotation/processing/Filer.html
[jdk-compiler]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/module-summary.html
[javac]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/javac.html
[diagnostic]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/tools/Diagnostic.html
[doclet]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.javadoc/jdk/javadoc/doclet/package-summary.html
[doclet-environment]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.javadoc/jdk/javadoc/doclet/DocletEnvironment.html
[serial-unknown-entity]: https://docs.oracle.com/en/java/javase/25/docs/api/serialized-form.html#javax.lang.model.UnknownEntityException
[serial-unknown-annotation]: https://docs.oracle.com/en/java/javase/25/docs/api/serialized-form.html#javax.lang.model.element.UnknownAnnotationValueException
[serial-unknown-directive]: https://docs.oracle.com/en/java/javase/25/docs/api/serialized-form.html#javax.lang.model.element.UnknownDirectiveException
[serial-unknown-element]: https://docs.oracle.com/en/java/javase/25/docs/api/serialized-form.html#javax.lang.model.element.UnknownElementException
[serial-unknown-type]: https://docs.oracle.com/en/java/javase/25/docs/api/serialized-form.html#javax.lang.model.type.UnknownTypeException
[serial-mirrored-type]: https://docs.oracle.com/en/java/javase/25/docs/api/serialized-form.html#javax.lang.model.type.MirroredTypeException
[serial-mirrored-types]: https://docs.oracle.com/en/java/javase/25/docs/api/serialized-form.html#javax.lang.model.type.MirroredTypesException
