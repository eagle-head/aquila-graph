# Publication identities and source conflicts

[Source map](README.md) · [Scope](scope.md) · [Full inventory](inventory/README.md)

## Publication identities

| Publication | Edition rule | Consultation route |
| --- | --- | --- |
| Java SE platform | JSR 400 Final Release, September 3, 2025 | [Final specification][platform] |
| Platform API | Final Annex 1; 21 platform modules | [Final API][platform-api] |
| Oracle combined API | Java SE/JDK 25, observed October 30, 2026; includes additional JDK modules | [Oracle API][oracle-api] |
| Platform API differences | `jdk-24-ga` compared with `jdk-25+36`; preserve old/new sides | [Annex 2][differences] |
| Language and VM | Java SE 25; final annex PDF identities and Oracle HTML identities remain separate | [Specifications](inventory/specifications.md) |
| Primitive-pattern preview | Three published edition identities; do not silently merge their changes | [Preview map](preview.md) |
| Annotation-processing component | JSR 269 Maintenance Review 19 as incorporated by JSR 400 | [Component revision](inventory/specifications.md#platform-specification-and-component-revision) |
| Oracle guides | Release 25 plus each guide's publication identifier and date | [Guide editions](inventory/guides.md) |
| External standards | Use the edition named by the referring contract; do not infer the latest edition | [Dependency register](inventory/dependencies.md) |

The official source is the consultation target. A link to an entire type or chapter includes its
subordinate contracts as reading scope, without claiming that the map reproduces their text.

## Known source inconsistencies

### Parameter-annotation attribute reference

The second directly-present-annotation condition in [AnnotatedConstruct][annotation] names
`RuntimeVisibleParameterAnnotations` while linking JVMS 4.7.17. [JVMS 4.7.17][invisible] defines
`RuntimeInvisibleAnnotations`; [JVMS 4.7.18][parameter] defines the named parameter-annotation attribute.
Both source locators remain visible; the map does not silently rewrite Oracle's reference.

### Transitive java.base preview notice

The [Oracle java.se module page][java-se] labels `requires transitive java.base` as preview.
[JLS 7.7.1][requires], [JVMS 4.7.25][module-attribute], and [JLS 1.5.2][language-preview] provide
the final language/VM rules and preview enumeration.

**Inference:** the module-page notice conflicts with those final specifications. Treat it as a source
discrepancy, not a second language-preview specification.

### External standard identity conflicts

The [textual dependency register](inventory/dependencies/textual-references.md) retains unnamed
editions, older algorithm-specific Unicode editions, PKCS version distinctions, and the ISO OECF
identifier conflict. These are mapped source questions, not permission to invent an edition.

## References

[platform]: https://cr.openjdk.org/~iris/se/25/latestSpec/
[platform-api]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/index.html
[oracle-api]: https://docs.oracle.com/en/java/javase/25/docs/api/index.html
[differences]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/index.html
[annotation]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/AnnotatedConstruct.html#directlypresent
[invisible]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.17
[parameter]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.18
[java-se]: https://docs.oracle.com/en/java/javase/25/docs/api/java.se/module-summary.html
[requires]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.7.1
[module-attribute]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.25
[language-preview]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-1.html#jls-1.5.2
