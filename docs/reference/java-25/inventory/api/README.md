# Java 25 API source inventory

[Source inventory](../README.md) · [Scope](../../scope.md) · [Publication identities](../../sources.md)

## Publication boundary

This inventory explicitly enumerates every public module, package, and type in the Oracle release-25
API indexes, plus linked auxiliary HTML documents, generated navigation, and supporting assets.
It preserves final JSR 400 API source identities separately where the declaration is in that baseline.
Every entry is a consultation link; no API contract body is reproduced.

The public type inventory includes nested types, interfaces, enum classes, annotation interfaces,
record classes, and exceptions. Each type record owns all member sections on its source page.

The inventories were reconciled against these source indexes:

- [Oracle modules][source-1].
- [Oracle packages][source-2].
- [Oracle types][source-3].
- [Module search index][source-4].
- [Package search index][source-5].
- [Type search index][source-6].
- [Final platform API][source-7].

The names of all 4293 final-platform types occur in the corresponding Oracle
package inventories. This is a declaration-name observation, not proof of unchanged member contracts.

| Publication | Modules | Packages | Type documents |
| --- | --- | --- | --- |
| Final platform Annex 1 | 21 | 182 | 4293 |
| Oracle Java SE/JDK 25 | 58 | 229 | 4979 |

The Oracle HTML source inventory contains 5332 retrieved pages: module, package, type,
global, and auxiliary documents. The 5437 generated hierarchy/use pages are recorded as
navigation links in their owning package/type entries. They were not treated as additional normative
contracts. The 179 supporting asset targets and two legal source
identities are listed separately. These counts describe documentary records, not Java features.

## Modules

Module membership in final Annex 1 determines the platform classification. The additional
`java.smartcardio` module is not classified as mandatory Java SE merely from its prefix.

- [java.base](modules/java.base.md): Java SE; 58 packages; 1635 type documents.
- [java.compiler](modules/java.compiler.md): Java SE; 6 packages; 146 type documents.
- [java.datatransfer](modules/java.datatransfer.md): Java SE; 1 package; 12 type documents.
- [java.desktop](modules/java.desktop.md): Java SE; 51 packages; 1692 type documents.
- [java.instrument](modules/java.instrument.md): Java SE; 1 package; 6 type documents.
- [java.logging](modules/java.logging.md): Java SE; 1 package; 17 type documents.
- [java.management](modules/java.management.md): Java SE; 9 packages; 191 type documents.
- [java.management.rmi](modules/java.management.rmi.md): Java SE; 1 package; 9 type documents.
- [java.naming](modules/java.naming.md): Java SE; 6 packages; 102 type documents.
- [java.net.http](modules/java.net.http.md): Java SE; 1 package; 22 type documents.
- [java.prefs](modules/java.prefs.md): Java SE; 1 package; 9 type documents.
- [java.rmi](modules/java.rmi.md): Java SE; 5 packages; 55 type documents.
- [java.scripting](modules/java.scripting.md): Java SE; 1 package; 12 type documents.
- [java.se](modules/java.se.md): Java SE; 0 packages; 0 type documents.
- [java.security.jgss](modules/java.security.jgss.md): Java SE; 2 packages; 16 type documents.
- [java.security.sasl](modules/java.security.sasl.md): Java SE; 1 package; 10 type documents.
- [java.smartcardio](modules/java.smartcardio.md): additional JCP API; 1 package; 13 type documents.
- [java.sql](modules/java.sql.md): Java SE; 2 packages; 74 type documents.
- [java.sql.rowset](modules/java.sql.rowset.md): Java SE; 3 packages; 30 type documents.
- [java.transaction.xa](modules/java.transaction.xa.md): Java SE; 1 package; 3 type documents.
- [java.xml](modules/java.xml.md): Java SE; 25 packages; 194 type documents.
- [java.xml.crypto](modules/java.xml.crypto.md): Java SE; 6 packages; 58 type documents.
- [jdk.accessibility](modules/jdk.accessibility.md): JDK; 1 package; 9 type documents.
- [jdk.attach](modules/jdk.attach.md): JDK; 2 packages; 8 type documents.
- [jdk.charsets](modules/jdk.charsets.md): JDK; 0 packages; 0 type documents.
- [jdk.compiler](modules/jdk.compiler.md): JDK; 4 packages; 146 type documents.
- [jdk.crypto.cryptoki](modules/jdk.crypto.cryptoki.md): JDK; 0 packages; 0 type documents.
- [jdk.dynalink](modules/jdk.dynalink.md): JDK; 5 packages; 37 type documents.
- [jdk.editpad](modules/jdk.editpad.md): JDK; 0 packages; 0 type documents.
- [jdk.hotspot.agent](modules/jdk.hotspot.agent.md): JDK; 0 packages; 0 type documents.
- [jdk.httpserver](modules/jdk.httpserver.md): JDK; 2 packages; 23 type documents.
- [jdk.incubator.vector](modules/jdk.incubator.vector.md): JDK incubator; 1 package; 22 type documents.
- [jdk.jartool](modules/jdk.jartool.md): JDK; 1 package; 3 type documents.
- [jdk.javadoc](modules/jdk.javadoc.md): JDK; 1 package; 9 type documents.
- [jdk.jcmd](modules/jdk.jcmd.md): JDK; 0 packages; 0 type documents.
- [jdk.jconsole](modules/jdk.jconsole.md): JDK; 1 package; 3 type documents.
- [jdk.jdeps](modules/jdk.jdeps.md): JDK; 0 packages; 0 type documents.
- [jdk.jdi](modules/jdk.jdi.md): JDK; 5 packages; 131 type documents.
- [jdk.jdwp.agent](modules/jdk.jdwp.agent.md): JDK; 0 packages; 0 type documents.
- [jdk.jfr](modules/jdk.jfr.md): JDK; 2 packages; 53 type documents.
- [jdk.jlink](modules/jdk.jlink.md): JDK; 0 packages; 0 type documents.
- [jdk.jpackage](modules/jdk.jpackage.md): JDK; 0 packages; 0 type documents.
- [jdk.jshell](modules/jdk.jshell.md): JDK; 4 packages; 61 type documents.
- [jdk.jsobject](modules/jdk.jsobject.md): JDK; 1 package; 2 type documents.
- [jdk.jstatd](modules/jdk.jstatd.md): JDK; 0 packages; 0 type documents.
- [jdk.localedata](modules/jdk.localedata.md): JDK; 0 packages; 0 type documents.
- [jdk.management](modules/jdk.management.md): JDK; 2 packages; 13 type documents.
- [jdk.management.agent](modules/jdk.management.agent.md): JDK; 0 packages; 0 type documents.
- [jdk.management.jfr](modules/jdk.management.jfr.md): JDK; 1 package; 6 type documents.
- [jdk.naming.dns](modules/jdk.naming.dns.md): JDK; 0 packages; 0 type documents.
- [jdk.naming.rmi](modules/jdk.naming.rmi.md): JDK; 0 packages; 0 type documents.
- [jdk.net](modules/jdk.net.md): JDK; 2 packages; 6 type documents.
- [jdk.nio.mapmode](modules/jdk.nio.mapmode.md): JDK; 1 package; 1 type document.
- [jdk.sctp](modules/jdk.sctp.md): JDK; 1 package; 21 type documents.
- [jdk.security.auth](modules/jdk.security.auth.md): JDK; 4 packages; 24 type documents.
- [jdk.security.jgss](modules/jdk.security.jgss.md): JDK; 1 package; 6 type documents.
- [jdk.xml.dom](modules/jdk.xml.dom.md): JDK; 4 packages; 89 type documents.
- [jdk.zipfs](modules/jdk.zipfs.md): JDK; 0 packages; 0 type documents.

## Shared and auxiliary sources

- [Global API documents and existing legal identities](auxiliary.md).
- [Supporting figures, examples, and structured-data resources](assets.md).
- [Final-publication auxiliary source identities](platform-auxiliary.md).
- [Annex 2 API differences](differences.md).
- [Explicit specification dependencies and source references](../dependencies.md).

Package inventories include their own auxiliary `doc-files` pages. Serialized forms, constants,
preview/restricted/deprecated declarations, and system-property indexes are not silently omitted.

## Interpretation limits

Internal API links connect sources already owned by this module/package/type inventory. External
specification and prose references are owned by the dependency inventory. Source enumeration does
not decide the normative reach of an external standard or establish implementation behavior.
Preserving both publication addresses does not claim byte identity between editions.

## References

[source-1]: https://docs.oracle.com/en/java/javase/25/docs/api/index.html
[source-2]: https://docs.oracle.com/en/java/javase/25/docs/api/allpackages-index.html
[source-3]: https://docs.oracle.com/en/java/javase/25/docs/api/allclasses-index.html
[source-4]: https://docs.oracle.com/en/java/javase/25/docs/api/module-search-index.js
[source-5]: https://docs.oracle.com/en/java/javase/25/docs/api/package-search-index.js
[source-6]: https://docs.oracle.com/en/java/javase/25/docs/api/type-search-index.js
[source-7]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/index.html
