# JDK 25 guides, release documents, and related resources

[Source map](../README.md)

This inventory covers the guide families exposed by the Oracle [JDK 25 documentation home][home]
and [Guides catalog][books], their HTML source documents, alternate PDFs, and the linked Oracle
JDK 25 release-document collection. Source filenames and URLs identify documents; counts do not
represent language features or normative requirements.

The sources were observed on October 30, 2026. Their release is 25, but several guide publication
revisions are from 2026. This inventory therefore describes the current release-25 documentation,
not a frozen copy of the September 2025 general-availability documentation. Each family catalog
retains its source publication identifier and date.

## Role and mapping status

These guides describe Oracle JDK usage, implementation behavior, compatibility, and explanatory
material. They supplement the language, VM, API, and tool contracts. An implementation-specific
instruction is not automatically a universal Java SE requirement; a guide reference to a normative
contract must retain that contract's authority and edition.

All guide and release entries link to the official bodies. Source records and relationships
provide navigation; the original full texts remain at the publisher.
The linked guide-family catalogs contain the exact Oracle title page, table of contents, PDF,
individual HTML document URLs, and auxiliary image-description documents.

## Guide families

The official Guides catalog contains 17 guide families. Its tables of contents identify 361 distinct
same-guide HTML documents. Links in their bodies identify 51 additional figure-description HTML
documents. These 412 HTML documents are listed individually in the catalogs below. The 17 TOCs
are source indexes; the 17 PDFs are alternate presentations, not 17 additional subject families.
The [figure and downloadable-asset catalog](guides/assets.md) additionally records 99 content-asset
targets across the guide families, with their referring source locations. These assets accompany
the HTML bodies and are not interchangeable with their text descriptions.

| Guide family | Publication identifier | TOC documents | Figure descriptions |
| --- | --- | ---: | ---: |
| [Migration](guides/migrate.md) | `G35926-01` | 25 | 0 |
| [Installation](guides/install.md) | `G31368-03` | 9 | 0 |
| [Flight Recorder API](guides/jfapi.md) | `G29149-01` | 27 | 0 |
| [Internationalization](guides/intl.md) | `G35937-01` | 7 | 0 |
| [Core Libraries](guides/core.md) | `G29144-03` | 101 | 2 |
| [RMI API guide](guides/rmi.md) | `G35931-01` | 1 | 0 |
| [Garbage Collection Tuning](guides/gctuning.md) | `G33936-03` | 12 | 7 |
| [Virtual Machine guide](guides/vm.md) | `G35928-01` | 17 | 6 |
| [Language Updates](guides/language.md) | `G29147-02` | 21 | 0 |
| [JMX](guides/jmx.md) | `G34653-02` | 26 | 0 |
| [Monitoring and Management](guides/management.md) | `G35930-01` | 7 | 0 |
| [Security](guides/security.md) | `G29146-07` | 46 | 24 |
| [JShell](guides/jshell.md) | `G40987-01` | 9 | 0 |
| [JavaDoc guide](guides/javadoc.md) | `G35935-01` | 6 | 1 |
| [Packaging Tool](guides/jpackage.md) | `G29150-01` | 8 | 0 |
| [Troubleshooting](guides/troubleshoot.md) | `G35709-03` | 30 | 10 |
| [Accessibility](guides/access.md) | `G29151-01` | 9 | 1 |

The RMI guide has a single body document with several sections; it is distinct from the RMI
specification listed in the official [specifications catalog][specifications]. The VM guide is
likewise distinct from the Java Virtual Machine Specification. Tool manuals have their own
inventory in [Specifications](specifications.md); JShell, JavaDoc, and Packaging guides are
additional source families rather than alternate names for those manuals.

## Guide reference relationships

Each source body was used to register outbound document references, preserving the target URL,
its fragment, and the originating source-page locator. The lists below contain 1,597 distinct
targets counted separately within each guide. A target shared by two guides appears in both
relationship lists; this is not a globally unique document count.

Relationship type: **observed source reference**. This records what the guide links to. It does not
classify every target as a normative dependency or establish that the target's contents are part
of Java SE 25. A source-reference locator is evidence of the relationship; destination availability
and normative force are separate properties.

| Guide | Outbound targets, including fragments |
| --- | ---: |
| [Migration references](guides/references/migrate.md) | 500 |
| [Installation references](guides/references/install.md) | 8 |
| [Flight Recorder API references](guides/references/jfapi.md) | 5 |
| [Internationalization references](guides/references/intl.md) | 59 |
| [Core Libraries references](guides/references/core.md) | 263 |
| [RMI guide references](guides/references/rmi.md) | 6 |
| [GC Tuning references](guides/references/gctuning.md) | 12 |
| [VM guide references](guides/references/vm.md) | 14 |
| [Language Updates references](guides/references/language.md) | 142 |
| [JMX references](guides/references/jmx.md) | 21 |
| [Monitoring and Management references](guides/references/management.md) | 28 |
| [Security references](guides/references/security.md) | 411 |
| [JShell references](guides/references/jshell.md) | 6 |
| [JavaDoc guide references](guides/references/javadoc.md) | 45 |
| [Packaging Tool references](guides/references/jpackage.md) | 5 |
| [Troubleshooting references](guides/references/troubleshoot.md) | 56 |
| [Accessibility references](guides/references/access.md) | 16 |

References into the release-25 API, specifications, or other guides belong to the corresponding
source inventory as well as this relationship list. Cross-release URLs, non-Oracle targets, and
Oracle topic-lookup URLs remain explicit; they must not be silently replaced with the latest
edition. The [dependency inventory](dependencies.md) owns the distinction between identified
specification dependencies, contextual references, and unresolved identities.

## Release and compatibility documents

The [release-source catalog](guides/releases.md) lists all 12 distinct documents reached through
the Oracle release-25 note indexes and readme using their release-specific document links. It
includes the release index, consolidated notes, initial release notes, update notes, and readme.
GA/BPR and bug-fix sections remain within their owning documents.

The homepage's [What's New][new-features] link identifies the new-feature section of the initial
release notes; it is not another document. The [Version-String Format][version-format] page belongs
to the Installation guide. Compatibility, removals, and deprecations also have source pages in
the [Migration guide catalog](guides/migrate.md). Their presence describes the release's history
and behavior; removed features must not become current Java 25 grammar merely because a migration
guide discusses them.

## Related resources and edition boundaries

The homepage also links the following sources. Their status is **Context** unless a particular
release-25 contract explicitly incorporates a specific part. A contextual link does not authorize
replacing a release-specific API or language specification with a project overview.

| Linked resource | Edition and boundary |
| --- | --- |
| [Secure Coding Guidelines][secure-coding] | Document version 11.0, June 2025; guidance across Java SE versions. |
| [Java Tutorials / dev.java][tutorials] | Unversioned educational portal; the current homepage points here. |
| [JDK HTTP Client project][http-client] | Unversioned OpenJDK project page; release-25 API contracts remain separate. |
| [Modular JDK / Project Jigsaw][jigsaw] | Unversioned project material; not a substitute for Java 25 module rules. |
| [JavaFX 25][javafx] | Separately published JavaFX documentation, not automatically part of Java SE 25. |
| [Java Components][components] | Directory of separately documented Java components. |
| [JDK downloads][downloads] | Distribution and acquisition page, not a specification of language behavior. |
| [Related Resources][related] | Navigation to training, subscriptions, community, blogs, and other components. |

The OpenJDK HTTP Client and Jigsaw addresses above preserve the destinations published by the
Oracle homepage. Retrieval of their current `openjdk.org` counterparts returned HTTP 403 during
this review; no claim about their current contents is made from that failed retrieval.

## References

- [Oracle JDK 25 documentation home][home].
- [Oracle Guides catalog, including HTML/PDF pairs][books].
- [Oracle release-note index][release-index] and [consolidated notes][consolidated].
- Each family catalog links its owning title page, full TOC, PDF, and individual source documents.
- Each outbound-reference catalog identifies the originating source page and target reference.

[home]: https://docs.oracle.com/en/java/javase/25/
[books]: https://docs.oracle.com/en/java/javase/25/books.html
[specifications]: https://docs.oracle.com/en/java/javase/25/docs/specs/index.html
[release-index]: https://www.oracle.com/java/technologies/javase/25u-relnotes.html
[consolidated]: https://www.oracle.com/java/technologies/javase/25all-relnotes.html
[new-features]: https://www.oracle.com/java/technologies/javase/25-relnote-issues.html#NewFeature
[version-format]: https://docs.oracle.com/en/java/javase/25/install/version-string-format.html
[secure-coding]: https://www.oracle.com/java/technologies/javase/seccodeguide.html
[tutorials]: https://dev.java/
[http-client]: http://openjdk.java.net/groups/net/httpclient/
[jigsaw]: http://openjdk.java.net/projects/jigsaw/
[javafx]: https://docs.oracle.com/en/java/java-components/javafx/25/index.html
[components]: https://docs.oracle.com/javacomponents/index.html
[downloads]: https://www.oracle.com/java/technologies/downloads/
[related]: https://docs.oracle.com/en/java/javase/25/related-resources.html
