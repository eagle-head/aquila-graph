# Java SE 25 API specification differences

[Source inventory](../README.md) · [Platform and narrative sources](../specifications.md)

The [final Java SE 25 platform specification][platform] incorporates an annotated API
comparison as Annex 2. Its [official root][differences] identifies the compared editions as
`jdk-24-ga` and `jdk-25+36`. This inventory records that publication separately from the final
API specification and the later Oracle release-25 API documentation.

The comparison retains additions, removals, changes, and unchanged material. A page can
therefore document a Java 24 declaration that is absent from Java 25. Do not promote old-side
text or removed declarations into current Java 25 obligations. An unchanged marker describes
the comparison produced by its publisher; it does not prove identity with later Oracle revisions.

The root and module inventories below link every discovered child HTML document with its source role. Referenced destinations that could not
be
retrieved are retained as **Unresolved identity** rather than silently omitted.

The source graph contains 4412 retrieved HTML documents, including its root, and
26 unavailable referenced document addresses. These are document-address counts, not
feature or requirement counts. The graph follows actual publication hyperlinks; Javadoc
search-index filenames are not assumed for this distinct comparison format.

| Module | Retrieved comparison documents | Unavailable references | Inventory |
| --- | ---: | ---: | --- |
| `java.base` | 1706 | 6 | [Documents](differences/java.base.md) |
| `java.compiler` | 153 | 0 | [Documents](differences/java.compiler.md) |
| `java.datatransfer` | 14 | 0 | [Documents](differences/java.datatransfer.md) |
| `java.desktop` | 1759 | 7 | [Documents](differences/java.desktop.md) |
| `java.instrument` | 8 | 0 | [Documents](differences/java.instrument.md) |
| `java.logging` | 19 | 0 | [Documents](differences/java.logging.md) |
| `java.management` | 201 | 0 | [Documents](differences/java.management.md) |
| `java.management.rmi` | 11 | 0 | [Documents](differences/java.management.rmi.md) |
| `java.naming` | 109 | 0 | [Documents](differences/java.naming.md) |
| `java.net.http` | 24 | 0 | [Documents](differences/java.net.http.md) |
| `java.prefs` | 11 | 0 | [Documents](differences/java.prefs.md) |
| `java.rmi` | 61 | 0 | [Documents](differences/java.rmi.md) |
| `java.scripting` | 14 | 0 | [Documents](differences/java.scripting.md) |
| `java.se` | 1 | 0 | [Documents](differences/java.se.md) |
| `java.security.jgss` | 10 | 1 | [Documents](differences/java.security.jgss.md) |
| `java.security.sasl` | 12 | 0 | [Documents](differences/java.security.sasl.md) |
| `java.sql` | 77 | 0 | [Documents](differences/java.sql.md) |
| `java.sql.rowset` | 34 | 0 | [Documents](differences/java.sql.rowset.md) |
| `java.transaction.xa` | 5 | 0 | [Documents](differences/java.transaction.xa.md) |
| `java.xml` | 117 | 10 | [Documents](differences/java.xml.md) |
| `java.xml.crypto` | 65 | 0 | [Documents](differences/java.xml.crypto.md) |

## Unavailable publication-wide references

These published destinations returned HTTP 404 and remain **Unresolved identity**.
They are not silently substituted with the final API publication's corresponding pages.

| Referenced document | Retrieval result | Destination | Example referring page |
| --- | --- | --- | --- |
| `serialized-form.html` | HTTP 404 | [Destination][unavailable-serialized-form] | [Source][unavailable-serialized-form-origin] |
| `constant-values.html` | HTTP 404 | [Destination][unavailable-constant-values] | [Source][unavailable-constant-values-origin] |

## Interpretation and completion

Source discovery establishes that a document or reference occurs in the official publication.
Source-map completion follows the enumeration and identity criteria in
[the scope](../../scope.md). This inventory does not assert that every external dependency has
been semantically classified or that any project implements the compared APIs.

## References

- [JSR 400 final specification, structure and Annex 2][platform].
- [Annotated API comparison, editions and module contents][differences].

[platform]: https://cr.openjdk.org/~iris/se/25/latestSpec/#Structure
[differences]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/index.html

[unavailable-serialized-form]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/serialized-form.html
[unavailable-serialized-form-origin]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.base/java/io/File.html
[unavailable-constant-values]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/constant-values.html
[unavailable-constant-values-origin]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.base/java/lang/Double.html
