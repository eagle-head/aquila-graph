# Platform, language, VM, and tool source inventory

[Reference contents](../README.md) · [Source catalog](../sources.md)

This inventory enumerates source documents, not Java features or implementation coverage.
Its source boundary is the [Oracle Java 25 specifications catalog][oracle-catalog], the
[final Java SE 25 platform specification][platform], their multipart narrative documents,
and the complete [JDK 25 tool index][tool-index]. The [API inventory](api/README.md),
[API differences inventory](api/differences.md), and guide inventory own their respective
document trees. Edition: Java SE/JDK 25, with final-platform and Oracle publication identities kept separate.

## Reading the inventory

- **Source mapped:** the source identity and official consultation link are recorded; the body remains external.
- **Edition variant:** another published edition or location exists; equivalence is not assumed.
- **Normative:** the document specifies behavior within its stated scope. This label does not
  turn its examples, rationale, historical discussion, or explicitly informative passages into rules.
- **Conditional:** the contract applies when an optional interface, preview, or platform-specific
  facility is supported or enabled. Optional implementation does not mean optional inventory entry.
- **Informative:** navigation, explanation, or change history; not a substitute for a contract.

Source URLs are release-specific. The JSR 400 final specification identifies the platform release;
the Oracle `/25/` documentation can carry later Java 25 maintenance updates. These editions must
remain distinguishable when sources are consulted.

## Platform specification and component revision

The [JSR 400 final specification][platform] is the platform root. It incorporates API and narrative
specifications and identifies the component revision. JEPs linked from its feature descriptions
are explicitly informative. Its original publication identifies September 3, 2025; the API
differences compare `jdk-24-ga` and `jdk-25+36`. All entries below provide official consultation routes.

| Platform section | Official locator | Role |
| --- | --- | --- |
| 1. Summary | [Section 1][platform-summary] | Release orientation |
| 2. Structure | [Section 2][platform-structure] | Normative incorporation boundaries |
| 3. Definitions | [Section 3][platform-definitions] | Platform terminology |
| 4. Component JSR Specifications | [Section 4][platform-components] | Normative component revision |
| 5. Features | [Section 5][platform-features] | Feature descriptions and platform-wide requirements |
| 6. APIs previewed | [Section 6][platform-previews] | Preview API boundary and enablement |
| 7. Modules | [Section 7][platform-modules] | Standard modules and subset requirements |
| 8. APIs removed | [Section 8][platform-removed] | Release removals |
| 9. APIs proposed for removal | [Section 9][platform-removal] | Removal proposals |

| Annex | Official source | Role and downstream source |
| --- | --- | --- |
| 1 | [Complete API specification][annex-api] | Final Java SE 25 API edition |
| 2 | [API specification differences][annex-diffs] | Complete release comparison; not a substitute for Annex 1 |
| 3 | [Language and VM specifications][annex-language] | JLS, JVMS, and preview documents |
| 4 | [JAR specification][annex-jar] | Normative archive contract |
| 5 | [JAWT specification and guide][annex-jawt] | Native integration; retain its explicit applicability limits |
| 6 | [JDWP][annex-jdwp] | Conditional debugger protocol |
| 7 | [JNI][annex-jni] | Conditional native interface |
| 8 | [Serialization][annex-serialization] | Normative serialization contract |
| 9 | [RMI][annex-rmi] | Normative remote invocation contract |
| 10 | [Security algorithm names][annex-security] | Names and required algorithm support |
| 11 | [JVM TI][annex-jvmti] | Conditional VM tool interface |

The component incorporated by section 4 is **JSR 269 Maintenance Review 19**, not whichever
revision is newest today. Its [revision history][processing-history], [Java SE 25 change
summary][processing-summary], and [issue list][processing-issues] identify that revision.
The operative API contracts are in `javax.annotation.processing` and `javax.lang.model.*`.
Both processing and language-model packages have explicit entries in the API inventory.
The history and issue list are provenance and change navigation, not replacement API specifications.

## Language and virtual-machine documents

The language and VM chapter tables below enumerate documents. Their complete section hierarchy is
already available in the [language contents](../language/README.md) and
[VM contents](../virtual-machine/README.md). Each chapter includes its subordinate rules, examples,
notes, and tables; individual table rows below do not limit chapter coverage to the title.

### Java Language Specification

| Official document | Role | Mapping |
| --- | --- | --- |
| [The Java® Language Specification][jls-index] | Navigation | Source mapped |
| [Chapter 1. Introduction][jls-jls-1] | Normative | Source mapped |
| [Chapter 2. Grammars][jls-jls-2] | Normative | Source mapped |
| [Chapter 3. Lexical Structure][jls-jls-3] | Normative | Source mapped |
| [Chapter 4. Types, Values, and Variables][jls-jls-4] | Normative | Source mapped |
| [Chapter 5. Conversions and Contexts][jls-jls-5] | Normative | Source mapped |
| [Chapter 6. Names][jls-jls-6] | Normative | Source mapped |
| [Chapter 7. Packages and Modules][jls-jls-7] | Normative | Source mapped |
| [Chapter 8. Classes][jls-jls-8] | Normative | Source mapped |
| [Chapter 9. Interfaces][jls-jls-9] | Normative | Source mapped |
| [Chapter 10. Arrays][jls-jls-10] | Normative | Source mapped |
| [Chapter 11. Exceptions][jls-jls-11] | Normative | Source mapped |
| [Chapter 12. Execution][jls-jls-12] | Normative | Source mapped |
| [Chapter 13. Binary Compatibility][jls-jls-13] | Normative | Source mapped |
| [Chapter 14. Blocks, Statements, and Patterns][jls-jls-14] | Normative | Source mapped |
| [Chapter 15. Expressions][jls-jls-15] | Normative | Source mapped |
| [Chapter 16. Definite Assignment][jls-jls-16] | Normative | Source mapped |
| [Chapter 17. Threads and Locks][jls-jls-17] | Normative | Source mapped |
| [Chapter 18. Type Inference][jls-jls-18] | Normative | Source mapped |
| [Chapter 19. Syntax][jls-jls-19] | Normative | Source mapped |
| [Legal Notice][jls-spec-frontmatter] | Legal | Source mapped |
| [Appendix A. Limited License Grant][jls-spec-license] | Legal | Source mapped |

The [official PDF][jls-pdf] is an alternate representation, not another feature set.
Use the PDF or HTML at the publisher; both identities remain explicit.

### Java Virtual Machine Specification

| Official document | Role | Mapping |
| --- | --- | --- |
| [The Java® Virtual Machine Specification][jvms-index] | Navigation | Source mapped |
| [Chapter 1. Introduction][jvms-jvms-1] | Normative | Source mapped |
| [Chapter 2. The Structure of the Java Virtual Machine][jvms-jvms-2] | Normative | Source mapped |
| [Chapter 3. Compiling for the Java Virtual Machine][jvms-jvms-3] | Normative | Source mapped |
| [Chapter 4. The `class` File Format][jvms-jvms-4] | Normative | Source mapped |
| [Chapter 5. Loading, Linking, and Initializing][jvms-jvms-5] | Normative | Source mapped |
| [Chapter 6. The Java Virtual Machine Instruction Set][jvms-jvms-6] | Normative | Source mapped |
| [Chapter 7. Opcode Mnemonics by Opcode][jvms-jvms-7] | Normative | Source mapped |
| [Legal Notice][jvms-spec-frontmatter] | Legal | Source mapped |
| [Appendix A. Limited License Grant][jvms-spec-license] | Legal | Source mapped |

The [official PDF][jvms-pdf] is an alternate representation, not another feature set.
Use the PDF or HTML at the publisher; both identities remain explicit.

### Preview editions

| Source edition | Official source | Mapping |
| --- | --- | --- |
| Third preview, `25+36-LTS-3489` | [JSR 400 publication][preview-platform] | Source mapped |
| Third preview, `25-ea+33-LTS-3475` | [JLS-hosted document][preview-jls] | Source mapped |
| Third preview, `25.0.4+7-LTS-189` | [JDK specifications document][preview-jdk] | Edition variant; no equivalence claim |

These three publication variants identify one named Java 25 language preview, not three features.
The JLS-hosted edition retains a draft notice. The JDK-hosted edition is the destination of the
current Oracle specifications catalog. The JSR 400 variant is linked from Annex 3. All notices and versions must remain distinguishable;
one publication URL must not silently replace another. The final JLS [preview enumeration][language-preview-status]
and final JVMS [preview enumeration][vm-preview-status] establish the language/VM preview boundary.

## Final-publication narrative payloads

The annex landing pages above point to this separate final-publication tree. The following
42 HTML documents were resolved through its narrative links. Their corresponding current
Oracle documents are listed in the family sections below; matching paths do not establish
identical content. All documents below are mapped as this separate publication edition. Two additional [JLS][platform-jls-diffs] and
[JVMS][platform-jvms-diffs] PDFs are the
normative final-publication editions incorporated by [Annex 3][annex-language]. Their change
markings do not make these documents merely informative. They are distinct publication identities;
equivalence with the Oracle HTML/PDF editions is not presumed.

| Source document path | Final-publication source |
| --- | --- |
| `AWT_Native_Interface.html` | [Official document][platform-source-AWT_Native_Interface] |
| `jar/jar.html` | [Official document][platform-source-jar-jar] |
| `jdwp/jdwp-protocol.html` | [Official document][platform-source-jdwp-jdwp-protocol] |
| `jdwp/jdwp-spec.html` | [Official document][platform-source-jdwp-jdwp-spec] |
| `jdwp/jdwp-transport.html` | [Official document][platform-source-jdwp-jdwp-transport] |
| `jni/design.html` | [Official document][platform-source-jni-design] |
| `jni/functions.html` | [Official document][platform-source-jni-functions] |
| `jni/index.html` | [Official document][platform-source-jni-index] |
| `jni/interface-pointer.html` | [Official document][platform-source-jni-interface-pointer] |
| `jni/intro.html` | [Official document][platform-source-jni-intro] |
| `jni/invocation.html` | [Official document][platform-source-jni-invocation] |
| `jni/types.html` | [Official document][platform-source-jni-types] |
| `jpda/architecture.html` | [Official document][platform-source-jpda-architecture] |
| `jpda/conninv.html` | [Official document][platform-source-jpda-conninv] |
| `jpda/jpda.html` | [Official document][platform-source-jpda-jpda] |
| `jpda/jpda_spis.html` | [Official document][platform-source-jpda-jpda_spis] |
| `jvmti.html` | [Official document][platform-source-jvmti] |
| `primitive-types-in-patterns-instanceof-switch-jls.html` | [Official document][platform-source-primitive-types-in-patterns-instanceof-switch-jls] |
| `rmi/activation.html` | [Official document][platform-source-rmi-activation] |
| `rmi/arch.html` | [Official document][platform-source-rmi-arch] |
| `rmi/client.html` | [Official document][platform-source-rmi-client] |
| `rmi/dgc.html` | [Official document][platform-source-rmi-dgc] |
| `rmi/exceptions.html` | [Official document][platform-source-rmi-exceptions] |
| `rmi/index.html` | [Official document][platform-source-rmi-index] |
| `rmi/intro.html` | [Official document][platform-source-rmi-intro] |
| `rmi/objmodel.html` | [Official document][platform-source-rmi-objmodel] |
| `rmi/properties.html` | [Official document][platform-source-rmi-properties] |
| `rmi/protocol.html` | [Official document][platform-source-rmi-protocol] |
| `rmi/registry.html` | [Official document][platform-source-rmi-registry] |
| `rmi/server.html` | [Official document][platform-source-rmi-server] |
| `rmi/stubs.html` | [Official document][platform-source-rmi-stubs] |
| `security/standard-names.html` | [Official document][platform-source-security-standard-names] |
| `serialization/class.html` | [Official document][platform-source-serialization-class] |
| `serialization/examples.html` | [Official document][platform-source-serialization-examples] |
| `serialization/exceptions.html` | [Official document][platform-source-serialization-exceptions] |
| `serialization/index.html` | [Official document][platform-source-serialization-index] |
| `serialization/input.html` | [Official document][platform-source-serialization-input] |
| `serialization/output.html` | [Official document][platform-source-serialization-output] |
| `serialization/protocol.html` | [Official document][platform-source-serialization-protocol] |
| `serialization/security.html` | [Official document][platform-source-serialization-security] |
| `serialization/serial-arch.html` | [Official document][platform-source-serialization-serial-arch] |
| `serialization/version.html` | [Official document][platform-source-serialization-version] |

Two navigation destinations emitted by this publication returned HTTP 404 during discovery:
[the specifications index][platform-specs-navigation] and [the tools index][platform-tools-navigation].
Their status is **Unresolved identity**; they are not silently replaced by current Oracle pages.
The annex landing pages still identify the substantive payloads listed here.

## Narrative specifications and their documents

Every HTML document reachable within the Oracle release-25 `docs/specs/` tree from its catalog is
listed below or in the tool/preview sections. The graph contains 82 distinct HTML document URLs:
36 in `man/` including its index, 13 RMI, 10 serialization, 7 JNI, 4 JPDA, 3 Javadoc, 3 JDWP,
and six other documents. This is a document-address observation, not proof that all semantic
dependencies have been interpreted.

### Root catalog

- [Java SE and JDK 25 specifications catalog][oracle-catalog] — informative navigation, **Source mapped**.
- [Java SE and JDK 25 API specification][oracle-api] — normative API collection with informative
  navigation; all indexed declarations are mapped in the [API inventory](api/README.md).
- [JDK 25 tool index][tool-index] — informative navigation; **Source mapped**.

### JAR

The [JAR specification][jar] is **Source mapped**. It includes archive structure, manifests,
multiversion archives, signing, class paths, and sealing. Its reference-implementation-only
attributes must retain that label. The API dependencies include `java.util.jar`,
`java.lang.module.ModuleFinder`, and class loading. Its name-value syntax references RFC 1521
and RFC 1522; these are distinct from a blanket adoption of every MIME rule.

### Java AWT Native Interface

The [JAWT specification and guide][jawt] is **Source mapped**. The document contains its platform-independent
API header, platform-dependent portions, and examples. Its `jawt.h` appendix says the interfaces
are not part of the Java SE specification and are not required of a VM. Inclusion as platform
Annex 5 must not erase this explicit applicability limit.

### Java Debug Wire Protocol

JDWP is optional according to its [overview][jdwp-spec] and the [java.se module contract][java-se].
The protocol details and transport interface are separate documents. Each is **Source mapped**.

| Official document | Mapping |
| --- | --- |
| [Java Debug Wire Protocol Details][source-jdwp-jdwp-protocol] | Source mapped |
| [Java Debug Wire Protocol][source-jdwp-jdwp-spec] | Source mapped |
| [Java Debug Wire Protocol Transport Interface (jdwpTransport)][source-jdwp-jdwp-transport] | Source mapped |

### Java Native Interface

JNI is listed as optional by the [java.se module contract][java-se]. All documents below are
**Source mapped**. The textual figure description is part of the source family and must not be lost
by selecting only numbered chapters. JNI uses the JLS/JVMS type model and modified UTF-8 rules.

| Official document | Mapping |
| --- | --- |
| [Java Native Interface Specification: 2 - Design Overview][source-jni-design] | Source mapped |
| [Java Native Interface Specification: 4 - JNI Functions][source-jni-functions] | Source mapped |
| [Java Native Interface Specification: Contents][source-jni-index] | Source mapped |
| [Description of Figure Interface Pointer][source-jni-interface-pointer] | Source mapped |
| [Java Native Interface Specification: 1 - Introduction][source-jni-intro] | Source mapped |
| [Java Native Interface Specification: 5 - The Invocation API][source-jni-invocation] | Source mapped |
| [Java Native Interface Specification: 3 - JNI Types and Data Structures][source-jni-types] | Source mapped |

### Java Platform Debugger Architecture

JPDA provides architectural and implementation context for JVM TI, JDWP, and JDI; its constituent
interface specifications own their respective contracts. All four documents are **Source mapped**.
References to old packaging or removed facilities in these texts remain source claims, not
evidence that those facilities are current Java 25 requirements.

| Official document | Mapping |
| --- | --- |
| [Structure Overview][source-jpda-architecture] | Source mapped |
| [Connection and Invocation Details][source-jpda-conninv] | Source mapped |
| [Java Platform Debugger Architecture][source-jpda-jpda] | Source mapped |
| [Java Platform Debugger Architecture Service Provider Interfaces][source-jpda-jpda_spis] | Source mapped |

### Java Object Serialization

This normative source family and all its appendices are **Source mapped**. Its dependencies include
`java.io` stream contracts, per-class serialized forms, field and method descriptors from the JVMS,
and the JLS model of classes, enums, and records. The full protocol and evolution rules remain
required even when the language chapters already describe the corresponding declarations.

| Official document | Mapping |
| --- | --- |
| [Java Object Serialization Specification: 4 - Class Descriptors][source-serialization-class] | Source mapped |
| [Java Object Serialization Specification: C - Example of Serializable Fields][source-serialization-examples] | Source mapped |
| [Java Object Serialization Specification: B - Exceptions In Object Serialization][source-serialization-exceptions] | Source mapped |
| [Java Object Serialization Specification: Contents][source-serialization-index] | Source mapped |
| [Java Object Serialization Specification: 3 - Object Input Classes][source-serialization-input] | Source mapped |
| [Java Object Serialization Specification: 2 - Object Output Classes][source-serialization-output] | Source mapped |
| [Java Object Serialization Specification: 6 - Object Serialization Stream Protocol][source-serialization-protocol] | Source mapped |
| [Java Object Serialization Specification: A - Security in Object Serialization][source-serialization-security] | Source mapped |
| [Java Object Serialization Specification: 1 - System Architecture][source-serialization-serial-arch] | Source mapped |
| [Java Object Serialization Specification: 5 - Versioning of Serializable Objects][source-serialization-version] | Source mapped |

### Java Remote Method Invocation

This normative source family is **Source mapped**. It depends on the RMI APIs, object serialization,
class loading, and the referenced transport rules. The source retains historical sections:
[chapter 7][rmi-activation] explicitly states that activation has been removed. Its presence
in the contents is not a current requirement to implement activation.

| Official document | Mapping |
| --- | --- |
| [Java Remote Method Invocation: 7 - Remote Object Activation][source-rmi-activation] | Source mapped |
| [Java Remote Method Invocation: 3 - RMI System Overview][source-rmi-arch] | Source mapped |
| [Java Remote Method Invocation: 4 - Client Interfaces][source-rmi-client] | Source mapped |
| [Java Remote Method Invocation: 9 - Garbage Collector Interfaces][source-rmi-dgc] | Source mapped |
| [Java Remote Method Invocation: A - Exceptions In RMI][source-rmi-exceptions] | Source mapped |
| [Java Remote Method Invocation Specification][source-rmi-index] | Source mapped |
| [Java Remote Method Invocation: 1 - Introduction][source-rmi-intro] | Source mapped |
| [Java Remote Method Invocation: 2 - Distributed Object Model][source-rmi-objmodel] | Source mapped |
| [Java Remote Method Invocation: B - Properties In RMI][source-rmi-properties] | Source mapped |
| [Java Remote Method Invocation: 10 - RMI Wire Protocol][source-rmi-protocol] | Source mapped |
| [Java Remote Method Invocation: 6 - Registry Interfaces][source-rmi-registry] | Source mapped |
| [Java Remote Method Invocation: 5 - Server Interfaces][source-rmi-server] | Source mapped |
| [Java Remote Method Invocation: 8 - Stub/Skeleton Interfaces][source-rmi-stubs] | Source mapped |

### Security standard algorithm names

The [security standard names specification][security-names] is **Source mapped**. It contains names,
parameters, algorithm requirements, and references to external algorithm/protocol standards.
Its section on implementation requirements distinguishes mandatory support from the wider names
catalog. External standards include IETF RFCs, NIST FIPS and Special Publications, W3C specifications,
IANA registries, and other identified authorities; preserve the exact edition or dated URI that
the Java contract names. The external dependency inventory owns those individual records.

### Javadoc

The three documents below are **Source mapped**. The comment specification defines the standard doclet's
input; output and search are separate contracts. The input grammar depends on JLS declarations,
HTML, CommonMark 0.31.2, and the GFM table extension. Links to rendering libraries used only as
examples do not make those libraries required Java specifications.

| Official document | Mapping |
| --- | --- |
| [JavaDoc Documentation Comment Specification for the Standard Doclet (JDK 25)][source-javadoc-doc-comment-spec] | Source mapped |
| [JavaDoc Search Specification (JDK 25)][source-javadoc-javadoc-search-spec] | Source mapped |
| [JavaDoc Output Generated by the Standard Doclet (JDK 25)][source-javadoc-std-doclet-output] | Source mapped |

### JVM Tool Interface

The [JVM TI 25.0 specification][jvmti] is **Source mapped**. Its internal contents cover introductory
contracts, functions, events, data types, constants, and change history. It is optional for
Java SE according to [java.se][java-se]. JNI types, references, and modified UTF-8 and the
JVMS execution model are substantive dependencies. Optional capabilities and phase/thread
restrictions in its function contracts must be read at the source.

## JDK tool documents

All tool documents are **Source mapped** and specify JDK behavior rather than universal Java SE command
names. The official index has 36 tool entries and 35 distinct command-document URLs:
`javaw` is a fragment within the `java` document. Keep the Windows-only classification from
the index, plus any additional platform conditions stated inside each command specification.

| Tool | Index platform classification | Official document |
| --- | --- | --- |
| `jar` | All platforms | [Specification][tool-jar] |
| `jarsigner` | All platforms | [Specification][tool-jarsigner] |
| `java` | All platforms | [Specification][tool-java] |
| `javac` | All platforms | [Specification][tool-javac] |
| `javadoc` | All platforms | [Specification][tool-javadoc] |
| `javap` | All platforms | [Specification][tool-javap] |
| `jcmd` | All platforms | [Specification][tool-jcmd] |
| `jconsole` | All platforms | [Specification][tool-jconsole] |
| `jdb` | All platforms | [Specification][tool-jdb] |
| `jdeprscan` | All platforms | [Specification][tool-jdeprscan] |
| `jdeps` | All platforms | [Specification][tool-jdeps] |
| `jfr` | All platforms | [Specification][tool-jfr] |
| `jhsdb` | All platforms | [Specification][tool-jhsdb] |
| `jinfo` | All platforms | [Specification][tool-jinfo] |
| `jlink` | All platforms | [Specification][tool-jlink] |
| `jmap` | All platforms | [Specification][tool-jmap] |
| `jmod` | All platforms | [Specification][tool-jmod] |
| `jnativescan` | All platforms | [Specification][tool-jnativescan] |
| `jpackage` | All platforms | [Specification][tool-jpackage] |
| `jps` | All platforms | [Specification][tool-jps] |
| `jrunscript` | All platforms | [Specification][tool-jrunscript] |
| `jshell` | All platforms | [Specification][tool-jshell] |
| `jstack` | All platforms | [Specification][tool-jstack] |
| `jstat` | All platforms | [Specification][tool-jstat] |
| `jstatd` | All platforms | [Specification][tool-jstatd] |
| `jwebserver` | All platforms | [Specification][tool-jwebserver] |
| `keytool` | All platforms | [Specification][tool-keytool] |
| `rmiregistry` | All platforms | [Specification][tool-rmiregistry] |
| `serialver` | All platforms | [Specification][tool-serialver] |
| `jabswitch` | Windows only | [Specification][tool-jabswitch] |
| `jaccessinspector` | Windows only | [Specification][tool-jaccessinspector] |
| `jaccesswalker` | Windows only | [Specification][tool-jaccesswalker] |
| `javaw` | Windows only | [Specification][tool-javaw] |
| `kinit` | Windows only | [Specification][tool-kinit] |
| `klist` | Windows only | [Specification][tool-klist] |
| `ktab` | Windows only | [Specification][tool-ktab] |

Tool-to-language relationships are dependencies, not replacements: `javac` depends on the JLS,
JVMS, compiler APIs, annotation processing, and the module/file environment; `java` depends on
launching, module resolution, native access, and runtime contracts; `javadoc` depends on its
comment/doclet contracts; `jar` and `jarsigner` depend on JAR and cryptographic specifications;
`jdb` and debugger tools depend on JPDA; `serialver` depends on serialization. Tool references to
older JDK manuals are retained as historical source links and do not enlarge the release-25
normative boundary automatically.

## Notices, licenses, and source interpretation

The JLS/JVMS tables include their notices and limited licenses. The narrative specifications link
to [documentation copyright][narrative-copyright], the [Java 25 specification license][spec-license],
and the [documentation redistribution policy][redistribution]. These are **Source mapped** for the
narrative collection; the existing [API copyright][map-link-1] and
[API document license][map-link-2] are different source URLs and must not be assumed
to replace them. Legal documents govern use and retention; they are not Java syntax features.

## Verification boundaries

The narrative and tool source families and their child documents have explicit entries above.
The API publication and Annex 2 comparison are enumerated in the linked API inventories.
Parent/child discovery was checked against the source contents and tool index. Source retrieval
and URL enumeration are aids to this inventory, not a semantic proof.
The tables record source routes; they do not imply that every source has been reviewed rule by rule. They also do not make all external
citations normative: the importing passage and
the external dependency inventory must preserve that distinction.

Consult [coverage](../coverage.md) for catalog reconciliation and remaining source uncertainties.

## References

Each inventory row links directly to the official document or section that identifies it.
The root authorities are the Oracle specifications/tool indexes and JSR 400; the local catalog
establishes mapping status. This inventory does not assess project implementation coverage.

[jls-index]: https://docs.oracle.com/javase/specs/jls/se25/html/index.html
[jls-jls-1]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-1.html
[jls-jls-2]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-2.html
[jls-jls-3]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html
[jls-jls-4]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html
[jls-jls-5]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html
[jls-jls-6]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html
[jls-jls-7]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html
[jls-jls-8]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html
[jls-jls-9]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html
[jls-jls-10]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html
[jls-jls-11]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-11.html
[jls-jls-12]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html
[jls-jls-13]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html
[jls-jls-14]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html
[jls-jls-15]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html
[jls-jls-16]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html
[jls-jls-17]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html
[jls-jls-18]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html
[jls-jls-19]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-19.html
[jls-spec-frontmatter]: https://docs.oracle.com/javase/specs/jls/se25/html/spec-frontmatter.html
[jls-spec-license]: https://docs.oracle.com/javase/specs/jls/se25/html/spec-license.html
[jls-pdf]: https://docs.oracle.com/javase/specs/jls/se25/jls25.pdf
[jvms-index]: https://docs.oracle.com/javase/specs/jvms/se25/html/index.html
[jvms-jvms-1]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-1.html
[jvms-jvms-2]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html
[jvms-jvms-3]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-3.html
[jvms-jvms-4]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html
[jvms-jvms-5]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html
[jvms-jvms-6]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html
[jvms-jvms-7]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-7.html
[jvms-spec-frontmatter]: https://docs.oracle.com/javase/specs/jvms/se25/html/spec-frontmatter.html
[jvms-spec-license]: https://docs.oracle.com/javase/specs/jvms/se25/html/spec-license.html
[jvms-pdf]: https://docs.oracle.com/javase/specs/jvms/se25/jvms25.pdf
[source-jdwp-jdwp-protocol]: https://docs.oracle.com/en/java/javase/25/docs/specs/jdwp/jdwp-protocol.html
[source-jdwp-jdwp-spec]: https://docs.oracle.com/en/java/javase/25/docs/specs/jdwp/jdwp-spec.html
[source-jdwp-jdwp-transport]: https://docs.oracle.com/en/java/javase/25/docs/specs/jdwp/jdwp-transport.html
[source-jni-design]: https://docs.oracle.com/en/java/javase/25/docs/specs/jni/design.html
[source-jni-functions]: https://docs.oracle.com/en/java/javase/25/docs/specs/jni/functions.html
[source-jni-index]: https://docs.oracle.com/en/java/javase/25/docs/specs/jni/index.html
[source-jni-interface-pointer]: https://docs.oracle.com/en/java/javase/25/docs/specs/jni/interface-pointer.html
[source-jni-intro]: https://docs.oracle.com/en/java/javase/25/docs/specs/jni/intro.html
[source-jni-invocation]: https://docs.oracle.com/en/java/javase/25/docs/specs/jni/invocation.html
[source-jni-types]: https://docs.oracle.com/en/java/javase/25/docs/specs/jni/types.html
[source-jpda-architecture]: https://docs.oracle.com/en/java/javase/25/docs/specs/jpda/architecture.html
[source-jpda-conninv]: https://docs.oracle.com/en/java/javase/25/docs/specs/jpda/conninv.html
[source-jpda-jpda]: https://docs.oracle.com/en/java/javase/25/docs/specs/jpda/jpda.html
[source-jpda-jpda_spis]: https://docs.oracle.com/en/java/javase/25/docs/specs/jpda/jpda_spis.html
[source-serialization-class]: https://docs.oracle.com/en/java/javase/25/docs/specs/serialization/class.html
[source-serialization-examples]: https://docs.oracle.com/en/java/javase/25/docs/specs/serialization/examples.html
[source-serialization-exceptions]: https://docs.oracle.com/en/java/javase/25/docs/specs/serialization/exceptions.html
[source-serialization-index]: https://docs.oracle.com/en/java/javase/25/docs/specs/serialization/index.html
[source-serialization-input]: https://docs.oracle.com/en/java/javase/25/docs/specs/serialization/input.html
[source-serialization-output]: https://docs.oracle.com/en/java/javase/25/docs/specs/serialization/output.html
[source-serialization-protocol]: https://docs.oracle.com/en/java/javase/25/docs/specs/serialization/protocol.html
[source-serialization-security]: https://docs.oracle.com/en/java/javase/25/docs/specs/serialization/security.html
[source-serialization-serial-arch]: https://docs.oracle.com/en/java/javase/25/docs/specs/serialization/serial-arch.html
[source-serialization-version]: https://docs.oracle.com/en/java/javase/25/docs/specs/serialization/version.html
[source-rmi-activation]: https://docs.oracle.com/en/java/javase/25/docs/specs/rmi/activation.html
[source-rmi-arch]: https://docs.oracle.com/en/java/javase/25/docs/specs/rmi/arch.html
[source-rmi-client]: https://docs.oracle.com/en/java/javase/25/docs/specs/rmi/client.html
[source-rmi-dgc]: https://docs.oracle.com/en/java/javase/25/docs/specs/rmi/dgc.html
[source-rmi-exceptions]: https://docs.oracle.com/en/java/javase/25/docs/specs/rmi/exceptions.html
[source-rmi-index]: https://docs.oracle.com/en/java/javase/25/docs/specs/rmi/index.html
[source-rmi-intro]: https://docs.oracle.com/en/java/javase/25/docs/specs/rmi/intro.html
[source-rmi-objmodel]: https://docs.oracle.com/en/java/javase/25/docs/specs/rmi/objmodel.html
[source-rmi-properties]: https://docs.oracle.com/en/java/javase/25/docs/specs/rmi/properties.html
[source-rmi-protocol]: https://docs.oracle.com/en/java/javase/25/docs/specs/rmi/protocol.html
[source-rmi-registry]: https://docs.oracle.com/en/java/javase/25/docs/specs/rmi/registry.html
[source-rmi-server]: https://docs.oracle.com/en/java/javase/25/docs/specs/rmi/server.html
[source-rmi-stubs]: https://docs.oracle.com/en/java/javase/25/docs/specs/rmi/stubs.html
[source-javadoc-doc-comment-spec]: https://docs.oracle.com/en/java/javase/25/docs/specs/javadoc/doc-comment-spec.html
[source-javadoc-javadoc-search-spec]: https://docs.oracle.com/en/java/javase/25/docs/specs/javadoc/javadoc-search-spec.html
[source-javadoc-std-doclet-output]: https://docs.oracle.com/en/java/javase/25/docs/specs/javadoc/std-doclet-output.html
[tool-jar]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jar.html
[tool-jarsigner]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jarsigner.html
[tool-java]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html
[tool-javac]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/javac.html
[tool-javadoc]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/javadoc.html
[tool-javap]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/javap.html
[tool-jcmd]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html
[tool-jconsole]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jconsole.html
[tool-jdb]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jdb.html
[tool-jdeprscan]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jdeprscan.html
[tool-jdeps]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jdeps.html
[tool-jfr]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jfr.html
[tool-jhsdb]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jhsdb.html
[tool-jinfo]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jinfo.html
[tool-jlink]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jlink.html
[tool-jmap]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jmap.html
[tool-jmod]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jmod.html
[tool-jnativescan]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jnativescan.html
[tool-jpackage]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html
[tool-jps]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jps.html
[tool-jrunscript]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jrunscript.html
[tool-jshell]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jshell.html
[tool-jstack]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jstack.html
[tool-jstat]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jstat.html
[tool-jstatd]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jstatd.html
[tool-jwebserver]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jwebserver.html
[tool-keytool]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/keytool.html
[tool-rmiregistry]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/rmiregistry.html
[tool-serialver]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/serialver.html
[tool-jabswitch]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jabswitch.html
[tool-jaccessinspector]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jaccessinspector.html
[tool-jaccesswalker]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jaccesswalker.html
[tool-javaw]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html#javaw
[tool-kinit]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/kinit.html
[tool-klist]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/klist.html
[tool-ktab]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/ktab.html
[oracle-catalog]: https://docs.oracle.com/en/java/javase/25/docs/specs/index.html
[platform]: https://cr.openjdk.org/~iris/se/25/latestSpec/
[tool-index]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/index.html
[annex-api]: https://cr.openjdk.org/~iris/se/25/latestSpec/api/index.html
[annex-diffs]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/index.html
[processing-history]: https://jcp.org/aboutJava/communityprocess/maintenance/jsr269/269ChangeLog.html
[processing-summary]: https://jcp.org/aboutJava/communityprocess/maintenance/jsr269/JavaSE25-269MR-summary.html
[processing-issues]: https://jcp.org/aboutJava/communityprocess/maintenance/jsr269/JavaSE25-269MR-issue-list.txt
[preview-jls]: https://docs.oracle.com/javase/specs/jls/se25/preview/specs/primitive-types-in-patterns-instanceof-switch-jls.html
[preview-jdk]: https://docs.oracle.com/en/java/javase/25/docs/specs/primitive-types-in-patterns-instanceof-switch-jls.html
[language-preview-status]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-1.html#jls-1.5.2
[vm-preview-status]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-1.html#jvms-1.5.2
[oracle-api]: https://docs.oracle.com/en/java/javase/25/docs/api/index.html
[jar]: https://docs.oracle.com/en/java/javase/25/docs/specs/jar/jar.html
[jawt]: https://docs.oracle.com/en/java/javase/25/docs/specs/AWT_Native_Interface.html
[jdwp-spec]: https://docs.oracle.com/en/java/javase/25/docs/specs/jdwp/jdwp-spec.html
[java-se]: https://docs.oracle.com/en/java/javase/25/docs/api/java.se/module-summary.html
[rmi-activation]: https://docs.oracle.com/en/java/javase/25/docs/specs/rmi/activation.html
[security-names]: https://docs.oracle.com/en/java/javase/25/docs/specs/security/standard-names.html
[jvmti]: https://docs.oracle.com/en/java/javase/25/docs/specs/jvmti.html
[narrative-copyright]: https://docs.oracle.com/en/java/javase/25/docs/legal/copyright.html
[spec-license]: https://www.oracle.com/java/javase/terms/license/java25speclicense.html
[redistribution]: https://www.oracle.com/technetwork/java/redist-137594.html
[platform-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/#Summary
[platform-structure]: https://cr.openjdk.org/~iris/se/25/latestSpec/#Structure
[platform-definitions]: https://cr.openjdk.org/~iris/se/25/latestSpec/#Definitions
[platform-components]: https://cr.openjdk.org/~iris/se/25/latestSpec/#Component-JSR-Specifications
[platform-features]: https://cr.openjdk.org/~iris/se/25/latestSpec/#Features
[platform-previews]: https://cr.openjdk.org/~iris/se/25/latestSpec/#APIs-previewed
[platform-modules]: https://cr.openjdk.org/~iris/se/25/latestSpec/#Modules
[platform-removed]: https://cr.openjdk.org/~iris/se/25/latestSpec/#APIs-removed
[platform-removal]: https://cr.openjdk.org/~iris/se/25/latestSpec/#APIs-proposed-for-removal
[annex-language]: https://cr.openjdk.org/~iris/se/25/latestSpec/java-se-25-annex-3.html
[annex-jar]: https://cr.openjdk.org/~iris/se/25/latestSpec/java-se-25-annex-4.html
[annex-jawt]: https://cr.openjdk.org/~iris/se/25/latestSpec/java-se-25-annex-5.html
[annex-jdwp]: https://cr.openjdk.org/~iris/se/25/latestSpec/java-se-25-annex-6.html
[annex-jni]: https://cr.openjdk.org/~iris/se/25/latestSpec/java-se-25-annex-7.html
[annex-serialization]: https://cr.openjdk.org/~iris/se/25/latestSpec/java-se-25-annex-8.html
[annex-rmi]: https://cr.openjdk.org/~iris/se/25/latestSpec/java-se-25-annex-9.html
[annex-security]: https://cr.openjdk.org/~iris/se/25/latestSpec/java-se-25-annex-10.html
[annex-jvmti]: https://cr.openjdk.org/~iris/se/25/latestSpec/java-se-25-annex-11.html

[preview-platform]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/primitive-types-in-patterns-instanceof-switch-jls.html
[platform-jls-diffs]: https://cr.openjdk.org/~iris/se/25/latestSpec/java-se-25-jls-fr-diffs.pdf
[platform-jvms-diffs]: https://cr.openjdk.org/~iris/se/25/latestSpec/java-se-25-jvms-fr-diffs.pdf
[platform-specs-navigation]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/index.html
[platform-tools-navigation]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/man/index.html
[platform-source-AWT_Native_Interface]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/AWT_Native_Interface.html
[platform-source-jar-jar]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/jar/jar.html
[platform-source-jdwp-jdwp-protocol]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/jdwp/jdwp-protocol.html
[platform-source-jdwp-jdwp-spec]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/jdwp/jdwp-spec.html
[platform-source-jdwp-jdwp-transport]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/jdwp/jdwp-transport.html
[platform-source-jni-design]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/jni/design.html
[platform-source-jni-functions]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/jni/functions.html
[platform-source-jni-index]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/jni/index.html
[platform-source-jni-interface-pointer]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/jni/interface-pointer.html
[platform-source-jni-intro]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/jni/intro.html
[platform-source-jni-invocation]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/jni/invocation.html
[platform-source-jni-types]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/jni/types.html
[platform-source-jpda-architecture]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/jpda/architecture.html
[platform-source-jpda-conninv]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/jpda/conninv.html
[platform-source-jpda-jpda]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/jpda/jpda.html
[platform-source-jpda-jpda_spis]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/jpda/jpda_spis.html
[platform-source-jvmti]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/jvmti.html
[platform-source-primitive-types-in-patterns-instanceof-switch-jls]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/primitive-types-in-patterns-instanceof-switch-jls.html
[platform-source-rmi-activation]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/rmi/activation.html
[platform-source-rmi-arch]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/rmi/arch.html
[platform-source-rmi-client]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/rmi/client.html
[platform-source-rmi-dgc]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/rmi/dgc.html
[platform-source-rmi-exceptions]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/rmi/exceptions.html
[platform-source-rmi-index]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/rmi/index.html
[platform-source-rmi-intro]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/rmi/intro.html
[platform-source-rmi-objmodel]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/rmi/objmodel.html
[platform-source-rmi-properties]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/rmi/properties.html
[platform-source-rmi-protocol]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/rmi/protocol.html
[platform-source-rmi-registry]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/rmi/registry.html
[platform-source-rmi-server]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/rmi/server.html
[platform-source-rmi-stubs]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/rmi/stubs.html
[platform-source-security-standard-names]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/security/standard-names.html
[platform-source-serialization-class]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/serialization/class.html
[platform-source-serialization-examples]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/serialization/examples.html
[platform-source-serialization-exceptions]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/serialization/exceptions.html
[platform-source-serialization-index]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/serialization/index.html
[platform-source-serialization-input]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/serialization/input.html
[platform-source-serialization-output]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/serialization/output.html
[platform-source-serialization-protocol]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/serialization/protocol.html
[platform-source-serialization-security]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/serialization/security.html
[platform-source-serialization-serial-arch]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/serialization/serial-arch.html
[platform-source-serialization-version]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/serialization/version.html

[map-link-1]: https://docs.oracle.com/en/java/javase/25/docs/legal/copyright.html
[map-link-2]: https://download.oracle.com/otndocs/jcp/java_se-25-final-spec/license.html
