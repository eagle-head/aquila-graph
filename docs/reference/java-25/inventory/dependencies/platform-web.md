# Published platform: Web specifications

[Dependency inventory](../dependencies.md)

This register transcribes the specification titles, target URLs, and every referring API locator in the
published JSR 400 final platform External Specifications index. It records declared specification
relationships; the referring API contract determines which clauses apply.

Target URLs and edition labels are retained as cited. An unversioned address is not silently replaced by a
newer edition. Each record maps the cited source and its origins; external content remains at its publisher.

Records: 7 specification entries; 12 referring API locators.

[Source index][index]

## Cascading Style Sheets, level 1

Specification: [Cascading Style Sheets, level 1][spec-1].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class javax.swing.text.html.CSS][spec-1-ref-1]

## Cascading Style Sheets Level 2 Revision 2 (CSS 2.2) Specification

Specification: [Cascading Style Sheets Level 2 Revision 2 (CSS 2.2) Specification][spec-2].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [javax.swing.JEditorPane.W3C_LENGTH_UNITS][spec-2-ref-1]

## Date and Time Formats

Specification: [Date and Time Formats][spec-3].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class java.util.Formatter][spec-3-ref-1]
- [java.nio.file.attribute.FileTime.toString()][spec-3-ref-2]

## Extensible Markup Language (XML) 1.0 (Fifth Edition)

Specification: [Extensible Markup Language (XML) 1.0 (Fifth Edition)][spec-4].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [java.util.Properties.loadFromXML(InputStream)][spec-4-ref-1]
- [java.util.Properties.storeToXML(OutputStream, String, Charset)][spec-4-ref-2]
- [java.util.Properties.storeToXML(OutputStream, String, String)][spec-4-ref-3]

## HTML 3.2 Reference Specification

Specification: [HTML 3.2 Reference Specification][spec-5].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [package javax.swing.text.html][spec-5-ref-1]

## HTML 4.01 Specification

Specification: [HTML 4.01 Specification][spec-6].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class java.net.URLEncoder][spec-6-ref-1]
- [java.net.URLDecoder.decode(String, Charset)][spec-6-ref-2]
- [java.net.URLEncoder.encode(String, Charset)][spec-6-ref-3]

## XML Schema Part 2: Datatypes Second Edition

Specification: [XML Schema Part 2: Datatypes Second Edition][spec-7].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [java.nio.file.attribute.FileTime.toString()][spec-7-ref-1]

## References

The links below preserve the source index targets and declaration anchors.

[index]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/external-specs.html
[spec-1]: https://www.w3.org/TR/REC-CSS1
[spec-1-ref-1]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/java.desktop/javax/swing/text/html/CSS.html#CascadingStyleSheets,level1
[spec-2]: https://www.w3.org/TR/CSS22
[spec-2-ref-1]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/java.desktop/javax/swing/JEditorPane.html#CascadingStyleSheetsLevel2Revision2(CSS2.2)Specification
[spec-3]: https://www.w3.org/TR/NOTE-datetime
[spec-3-ref-1]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/java.base/java/util/Formatter.html#DateandTimeFormats
[spec-3-ref-2]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/java.base/java/nio/file/attribute/FileTime.html#DateandTimeFormats
[spec-4]: https://www.w3.org/TR/xml
[spec-4-ref-1]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/java.base/java/util/Properties.html#ExtensibleMarkupLanguage(XML)1.0(FifthEdition)
[spec-4-ref-2]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/java.base/java/util/Properties.html#ExtensibleMarkupLanguage(XML)1.0(FifthEdition)-2
[spec-4-ref-3]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/java.base/java/util/Properties.html#ExtensibleMarkupLanguage(XML)1.0(FifthEdition)-1
[spec-5]: https://www.w3.org/TR/REC-html32.html
[spec-5-ref-1]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/java.desktop/javax/swing/text/html/package-summary.html#HTML3.2ReferenceSpecification
[spec-6]: https://www.w3.org/TR/html4
[spec-6-ref-1]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/java.base/java/net/URLEncoder.html#HTML4.01Specification
[spec-6-ref-2]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/java.base/java/net/URLDecoder.html#HTML4.01Specification
[spec-6-ref-3]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/java.base/java/net/URLEncoder.html#HTML4.01Specification-1
[spec-7]: https://www.w3.org/TR/xmlschema-2
[spec-7-ref-1]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/java.base/java/nio/file/attribute/FileTime.html#XMLSchemaPart2:DatatypesSecondEdition
