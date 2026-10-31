# Java 25 official source map

This map locates the official Java 25 publications and their documentary dependencies. It contains
links, edition identities, short consultation contexts, and reference origins. Read rules, exceptions,
examples, tables, and formal definitions in the linked originals; this directory does not reproduce them.

**Publication baseline:** Java SE 25 Final Release Specification (JSR 400), September 3, 2025.
**Oracle documentation observation:** October 30, 2026. Release-25 maintenance publications remain
distinct from the final platform edition. See [publication identities](sources.md).

**Mapping status:** [closed for the fixed publication baseline](coverage.md#closure-status).
Direct dependencies end at their recorded source identity and referring passage. Unspecified editions,
unavailable publisher links, and source conflicts remain visible without requiring an unbounded
inventory of external standards. Concrete mapping errors remain subject to correction.

## Start here

| Consultation need | Source map |
| --- | --- |
| What is included, and what does completeness mean? | [Scope and acceptance](scope.md) |
| Which official catalogs were reconciled, and is the map closed? | [Coverage and closure](coverage.md) |
| All publication families | [Source inventory](inventory/README.md) |
| Language rules, sections, and examples | [JLS section index](language/README.md) |
| Grammar production definitions | [Grammar index](grammar.md) |
| VM, class files, verification, and instructions | [JVMS section index](virtual-machine/README.md) |
| Java 25 preview editions | [Preview source identities](preview.md) |
| Platform annexes, component JSR, and JDK tools | [Specification inventory](inventory/specifications.md) |
| Modules, packages, public types, and auxiliary API contracts | [API inventory](inventory/api/README.md) |
| External standards and referring passages | [Dependencies](inventory/dependencies.md) |
| Guides, release notes, and supporting resources | [Guide inventory](inventory/guides.md) |
| Conflicting source statements and edition boundaries | [Publication identities](sources.md) |

## Compiler and language-model navigation

| Contract family | Type and member links |
| --- | --- |
| Source trees | [com.sun.source.tree](compiler/source-trees.md) |
| Documentation trees | [com.sun.source.doctree](compiler/documentation-trees.md) |
| Tree access and utilities | [com.sun.source.util](compiler/compiler-utilities.md) |
| Language model | [javax.lang.model](compiler/language-model.md) |
| Declaration elements | [javax.lang.model.element](compiler/elements.md) |
| Types | [javax.lang.model.type](compiler/types.md) |
| Model utilities | [javax.lang.model.util](compiler/model-utilities.md) |

These navigation routes do not restrict the full API inventory to compiler packages. They also do not
establish implementation coverage. A source is mapped when its identity, consultation route, and origin
are recorded; its body does not need to be copied locally.

## References

- [Java SE 25 final specification][map-link-1].
- [Oracle specifications][map-link-2].
- [Oracle API][map-link-3].
- [Oracle guides][map-link-4].

[map-link-1]: https://cr.openjdk.org/~iris/se/25/latestSpec/
[map-link-2]: https://docs.oracle.com/en/java/javase/25/docs/specs/index.html
[map-link-3]: https://docs.oracle.com/en/java/javase/25/docs/api/index.html
[map-link-4]: https://docs.oracle.com/en/java/javase/25/books.html
