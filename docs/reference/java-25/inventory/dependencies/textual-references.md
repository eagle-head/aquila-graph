# Dependencies named in specification text

[Dependency inventory](../dependencies.md) · [Scope](../../scope.md)

Hyperlinks do not identify every external dependency. This supplement records standards, editions, data sets, and algorithms
named in Oracle's Java 25 prose when the reference has no direct link, has only a publisher link, or needs an edition distinction
that the link alone cannot express. The originating passage, rather than a search result or a name count, supports each entry.

The entries describe the current Oracle edition. A corresponding final Java SE 25 page is identified explicitly when inspected;
the current Oracle API and the final platform API remain separate editions. See the [API edition comparison](../api/differences.md).

## Interpretation and source identity

- **Contract reference:** the passage uses the named source to define an algorithm, representation, data value, or required behavior.
- **Context:** the passage uses the source for explanation, examples, history, or a qualified comparison. This does not incorporate
  the entire external publication into Java SE 25.
- **Unresolved identity:** the name, edition, or intended publication cannot be established from the citing passage. Preserve the
  ambiguity; do not substitute the most recent publication from the same standards organization.
- **Source mapped:** the dependency name and referring passage are recorded; a missing edition or publisher URL remains explicit.

Some targets already occur in the hyperlink registers. Such an entry adds a source relationship or an edition constraint; it is
not automatically a new unique publication. The statuses above distinguish source identification from edition resolution.

## Smart cards, cryptography, and encoded security structures

### ISO/IEC 7816-4

**Contract reference; unresolved edition.** [CommandAPDU][command-apdu] defines its command structure by this standard and
supports both short and extended length forms. [CardChannel.transmit(CommandAPDU)][card-transmit] uses it for T=0 response
processing, including additional response retrieval and correction of the expected response length. The
[package description][smartcard-package] identifies the API as JSR 268 and associates its APDU model with ISO/IEC 7816-4.
Neither passage selects a dated edition. A contemporary ISO catalog entry cannot determine the edition intended by Java 25.

### ANSI X9.62 and ANSI X9.63

**Contract references; unresolved editions.** The [signature algorithm names][signature-names] identify ECDSA by ANSI X9.62.
The [key agreement names][keyagreement-names] identify ECDH by ANSI X9.63. The corresponding final platform passages also name
[X9.62][platform-signature-names] and [X9.63][platform-keyagreement-names]. Neither standard is assigned an edition in these
passages. Their names and algorithm roles must remain explicit even when an explanatory RFC is linked nearby.

### PKCS #3, version 1.4, November 1993

**Contract reference; source mapped.** The [key agreement names][keyagreement-names] and
[javax.crypto.spec package specification][crypto-spec] identify this edition of the Diffie-Hellman Key-Agreement Standard.
[DHParameterSpec][dh-parameters] delegates its parameter model to PKCS #3. Keep the named edition rather than treating an
unversioned PKCS publisher or archive link as a complete identity.

### PKCS #8, versions 1.2 and 2.0

**Distinct edition references; source mapped.** The [java.security package specification][security-package] names
Private-Key Information Syntax Standard, version 1.2, November 1993. The preview [PEMEncoder description][pem-encoder] explicitly
names PKCS #8 2.0 for `OneAsymmetricKey`. [PKCS8EncodedKeySpec][pkcs8-spec] uses that structure and links RFC 5958. These are
different cited identities; the package bibliography does not erase the newer structure-specific reference.

The [KeyStore.setKeyEntry contract][keystore-key] separately requires `EncryptedPrivateKeyInfo` under the stated JKS condition.
This entry records that conditional reference without claiming all keystore implementations have the same format requirement.

### PKCS #1, #7, #10, #11, and #12

The following references need their own source relationships even where other pages link a publication in the same family:

- **PKCS #1:** [OAEPParameterSpec][oaep] explicitly uses version 2.2. The [security interfaces package][security-interfaces]
  identifies version 2.2 with RFC 8017. This source identity is resolved.
- **PKCS #7:** the [JAR signature block description][jar-block] and
  [CertificateFactorySpi.engineGenerateCertificates][certificates] refer to the container and `SignedData` representation.
  Neither cited passage supplies a version. Preserve the JAR and certificate-factory relationships separately.
- **PKCS #10:** the [keytool certificate request command][keytool-request] selects the CSR format without naming an edition.
  This is a JDK tool contract, not an additional Java SE API requirement.
- **PKCS #11:** the [keystore names][keystore-names] name token-backed storage. The
  [secret-key names][secretkey-names] also refer to `CKK_GENERIC_SECRET_TYPE`. These passages do not select a PKCS #11 edition.
- **PKCS #12:** [PBEKeySpec][pbe-keys] distinguishes password-character consumption from PKCS #5. This comparison matters to
  interpreting the API's input contract, but does not by itself establish a version of either whole standard.

### X.509 (2000), defect report 279, and Technical Corrigendum 1

**Contract reference; source mapped.** The [PkiPath encoding description][certpath-encodings] identifies defect report
279 against X.509 (2000), incorporated into Technical Corrigendum 1, also designated DTC 2 in the source. The
[final platform passage][platform-certpath-encodings] preserves the same identifiers. A general ITU site link does not identify
this amendment chain. Preserve the year, defect report, and corrigendum designation together.

### X.501, ASN.1, BER, and DER

**Contract references; unresolved editions.** [X509CertSelector.setIssuer(byte[])][x501-issuer] names X.501 for a DER-encoded
distinguished name. [Control.getEncodedValue()][ldap-control] requires an ASN.1 BER-encoded LDAP control value.
[Certificate.getEncoded()][certificate-encoding] uses DER as the X.509 example. These passages identify representations but
do not select dated ASN.1 or encoding-rule publications. Do not invent a specific X.680/X.690 edition from the abbreviation alone.
More specific linked protocol definitions elsewhere must be reconciled with these named relationships when interpreting the dependency.

## Imaging, color, and sound

### ISO 12232 and ISO 12233

**Contract references; unresolved editions.** [ExifTIFFTagSet.TAG_ISO_SPEED_RATINGS][iso-speed] associates the photographic
sensitivity tag with ISO 12232. [TAG_SPATIAL_FREQUENCY_RESPONSE][spatial-response] associates the spatial-frequency-response
tag with ISO 12233. The member descriptions do not select editions. The Exif source identity and these named subordinate
standards are separate inventory obligations.

### ISO 14254 as written in TAG_OECF

**Unresolved identity.** Oracle's [TAG_OECF description][oecf] names ISO 14254 while describing an optoelectric conversion
function. The [ISO 14254:2018 catalog][iso-14254] describes soil acidity, while the
[ISO 14524:2009 catalog][iso-14524] describes camera opto-electronic conversion functions.

**Inference:** the digits in the Java API reference may be transposed. The inventory preserves Oracle's actual identifier and
records the conflict. It does not silently replace ISO 14254 with ISO 14524, select the 2009 edition, or claim the discrepancy is
resolved. Resolution requires an authoritative correction or the applicable Exif specification's precise normative reference.

### ISO/IEC 10918-1

**Contract reference; unresolved edition.** The [TIFF metadata specification, Reading Compressed Exif Images][tiff-exif]
describes a compressed Exif image using baseline DCT JPEG under ISO/IEC 10918-1. No dated edition is selected there. The
section's APP1 and embedded TIFF requirements remain in the Java document; the JPEG standard is an additional named dependency.

### ICC.1:2001-12

**Contract reference; source mapped.** [ICC_Profile][icc-profile] names *File Format for Color Profiles*,
ICC.1:2001-12, as the basis of the represented profile data. The page links the ICC home page, which is insufficient to preserve
the named edition by itself. An inventory of that home-page URL must retain this edition-specific relationship.

### Standard MIDI Files 1.0 and Complete MIDI 1.0 Detailed Specification

**Format reference and explanatory context; source mapped.** [MetaMessage][midi-meta] and [MidiMessage][midi-message]
name Standard MIDI Files 1.0 as part of the Complete MIDI 1.0 Detailed Specification. Their MIDI Association link is generic.
The class descriptions distinguish file meta-events from wire-protocol messages; neither a current MIDI 2.0 publication nor
the publisher home page substitutes for the cited 1.0 material.

The [Sequence timing fields][midi-timing] also name SMPTE-based timing modes and specify their rates locally. No SMPTE document
number or edition is supplied there. Record this as an unresolved source identity, not an invented incorporation of every
SMPTE time-code standard.

## Character data, locale registries, and time

### Unicode 3.2 for IDN

**Contract reference; source mapped.** [IDN][idn] bases its internationalized-domain-name conversion on Unicode 3.2,
including the treatment of unassigned code points. This older version is an algorithm-specific data dependency. It must not
be replaced with the Unicode version used by `Character` in Java 25.

### Unicode 4.0, section 3.9, for UTF-8 background

**Context; source mapped if retained in the reference.** [DataInput][data-input] and the
[JNI modified UTF-8 section][jni-utf8] cite Unicode Standard version 4.0, section 3.9, for further information on UTF-8.
The [final platform JNI section][platform-jni-utf8] does the same. Their modified UTF-8 rules are stated locally; the citation
does not make all Unicode 4.0 behavior the Java 25 character contract. Preserve its explicit edition and its explanatory role.

### ISO/IEC 10646-1 and charset identifiers

**Contract references; unresolved editions.** [Charset][charset] identifies ISO 10646-1 as the basis for UCS transformation
formats, alongside its more specific RFC and Unicode links. The same standard-charsets section names ISO646-US and ISO-8859-1.
These format identifiers remain relevant even when they are plain text. Do not assume every illustrative charset name in the
class description denotes a mandatory Java charset or an independently incorporated standards edition.

### ISO 639, ISO 15924, ISO 3166, UN M.49, and ISO 4217

**Data and representation references; unresolved publication or data revisions.** [Locale's field definitions][locale]
name ISO 639 language codes, ISO 15924 script codes, ISO 3166 regions, and UN M.49 numeric areas. They also describe Java's
canonicalization and permitted syntax. [Locale.getScript()][locale-script] and
[Locale.Builder.setScript(String)][locale-builder-script] retain the specific script-code relationship.
[Currency][currency] names ISO 4217 for currency codes and associated values, with Java-specific override behavior.

A named registry is not a fixed data snapshot. Source interpretation must distinguish the standards publication, the applicable
registry data revision, and the additional Java rules. The BCP 47/IANA and CLDR references elsewhere in the inventory do not
justify silently assigning one shared edition to all five identities.

### ISO 8601:2000(E), section 5.5.3.2

**Contract reference; source mapped.** [DatatypeFactory.newDuration(long)][duration] explicitly identifies the
alternative format in this edition and section. Related duration factory methods retain the same reference. A generic ISO 8601
link, an ISO 8601-1:2019 catalog entry, or W3C's explanatory date/time note is not an interchangeable edition identity.

### ISO 8601:1988

**Contract reference with an explicit qualification; source mapped.** The
[XMLGregorianCalendar field mapping][xml-calendar] names ISO-8601-1988 when describing year values and XML Schema errata.
The same passage explains Java's treatment of year zero and its difference from XML Schema 1.0 validation. Preserve that
qualification rather than treating the citation as unconditional adoption of every rule in the 1988 standard.

### Undated ISO 8601 references in java.time

**Contract references; unresolved edition.** [LocalDate][local-date] uses the ISO-8601 calendar system and defines the
proleptic Gregorian interpretation locally. This does not resolve the edition for every other occurrence of ISO 8601 in
`java.time`, nor does it merge the explicitly dated XML datatype references above into a single current edition.

## Numeric algorithms and qualified compatibility references

### fdlibm 5.3

**Contract reference; source mapped.** [StrictMath][strictmath] selects fdlibm version 5.3 for the listed functions,
including the choice of IEEE core routines where alternatives exist. It also specifies transformations needed to interpret
the C algorithms under Java arithmetic. The generic Netlib directory link alone does not preserve that version and selection.
The external algorithm texts are source dependencies even though they are not a conventional numbered standards publication.

### IEEE 754-2008, IEEE 754-2019, and IEEE 802

- **IEEE 754-2008:** [StrictMath.fma(double,double,double)][fma] compares the operation to `fusedMultiplyAdd` in this edition.
  Record the explicit edition and the locally stated arithmetic rules; a page-level IEEE 754 link does not erase the reference.
- **IEEE 754-2019:** [MathContext.DECIMAL128][decimal128] identifies its precision with the corresponding decimal format.
  This is a format relationship, not a claim that every `BigDecimal` operation implements the entire IEEE standard.
- **IEEE 802:** [UUID.node()][uuid-node] describes the node field's intended address interpretation. The edition is unstated;
  record this format context alongside the more specific UUID RFC reference rather than inventing a particular 802 edition.

### ANSI X3.274-1996 and amendment AM 1-2000, section 7.4

**Qualified compatibility context.** [BigDecimal][bigdecimal] compares selected arithmetic modes with these publications
and explicitly gives the Java class specification precedence where they differ. Preserve both the base standard and amendment,
but do not label either an unconditional normative dependency of all `BigDecimal` behavior.

## RFC relationships expressed without direct publication links

These inspected passages demonstrate why a per-document link list is not a complete relationship inventory. RFC numbers identify
publications, but the source's role and any cited section remain significant. Existing IETF target entries must retain these
additional origins; they are not counted again as distinct publications here. Consult the source passages for the applicable provisions.

| RFC | Exact Java source | Relationship or boundary |
| --- | --- | --- |
| 1122, section 4.2.4.2 | [Socket traffic class][socket-tos] | Context for whether a TOS change is accepted. |
| 1323 | [ServerSocket receive buffer][server-window] | Window scaling and the pre-bind buffer-size condition. |
| 1349 | [DatagramSocket traffic class][datagram-tos] | IPv4 type-of-service interpretation. |
| 1421 | [keytool import commands][keytool-import] | Printable certificate encoding accepted by the JDK tool. |
| 1738 | [ObjectFactory][object-factory] | URL interpretation in the object-factory contract, with related RFCs. |
| 1766 | [Context.LANGUAGE][context-language] | Language-tag syntax for the naming environment property. |
| 1964 | [GSSManager][gss-manager] | Required availability of the named Kerberos V5 GSS mechanism. |
| 2104 | [Mac][mac] | HMAC algorithm definition. |
| 2222 | [SaslClient.unwrap][sasl-unwrap] | SASL buffer handling excludes the protocol length field. |
| 2253 | [KeyInfoFactory.newX509Data][x509-data] | Distinguished-name representation and required attribute keywords. |
| 2254 | [DirContext.search][directory-search] | LDAP search-filter expression syntax. |
| 2307 | [JndiLoginModule][jndi-login] | Directory schema used for user information. |
| 2396 | [DOMCryptoContext.setBaseURI][xml-base-uri] | URI syntax required for the XML signature base URI. |
| 2560, section 2.3 | [PKIXRevocationChecker.Option.SOFT_FAIL][ocsp-soft-fail] | Named OCSP failure conditions. |
| 2732 | [network properties][network-properties] | Literal IPv6 formatting in the described configuration values. |
| 2743 | [GSSName.export][gss-export] | Exported-name header; mechanism specifications define the trailer. |
| 2818 and 2830 | [X509ExtendedTrustManager][trust-manager] | Endpoint-identification definitions for HTTPS and LDAPS. |
| 2960, section 3.3.10 | [SendFailedNotification.errorCode][sctp-error] | SCTP protocol error value; JDK-specific API. |
| 2965, sections 3.3 and 7 | [CookiePolicy][cookie-policy] | Cookie acceptance policy context and definition. |
| 3161 | [jarsigner options][jarsigner-options] | Time-stamp protocol used by the JDK tool. |
| 3961, section 8 | [InquireType.KRB5_GET_SESSION_KEY][kerberos-key] | Encryption-type value in the returned key description. |
| 4120, section 5.2.3 | [InquireType.KRB5_GET_AUTHTIME][kerberos-time] | KerberosTime representation; JDK-specific API. |
| 4647 | [jlink include-locales][jlink-locales] | Locale matching used by the JDK tool. |
| 4648 and 2045 | [Base64.Decoder][base64-decoder] | Base64 decoding scheme; retain variant-specific Java rules. |
| 5280 | [PKIXReason][pkix-reason] | Certificate-path validity reasons. |
| 5929 | [network properties][network-properties] | TLS server-end-point channel binding. |
| 6960 | [ExtendedSSLSession.getStatusResponses][ocsp-responses] | DER-encoded OCSPResponse representation. |
| 7748 | [XECPrivateKey][xec-private] | Scalar encoding and pruning requirements. |
| 8089 | [network properties][network-properties] | Context for nonlocal file URL processing. |

## References retained as context or excluded as false matches

These distinctions prevent a broader search from manufacturing normative dependencies:

- The [Character description][character] includes a historical version table. Previous Unicode versions in that table are
  history; the current character-data rule and the independent IDN 3.2 dependency have different roles.
- The [SSLParameters ALPN example][alpn-example] includes an RFC 8701 GREASE value. This is example context, not a requirement
  that every API client implement the whole GREASE RFC.
- The [GSSContext stream methods][gss-stream] describe removal from RFC 8353 and earlier RFC 5653 history. Retain the historical
  relationship without using an older specification to override the Java 25 deprecation guidance.
- The [CertStore service attributes][service-attributes] use RFC2587 as an example of a specification-name value. That example
  alone does not require every CertStore implementation to implement that schema.
- The [ResourceBundle.Control locale examples][bundle-control] use `POSIX` as a locale variant. That token does not incorporate
  the POSIX standard. In contrast, [SymbolLookup.libraryLookup][library-lookup] and [Console.isTerminal][console-terminal]
  describe platform-specific native operation relationships; their applicable operating-system contract remains a separate,
  unresolved edition dependency rather than a universal Java SE POSIX requirement.
- The [Formatter description][formatter] compares its date/time conversions with POSIX `strftime` and documents differences.
  Preserve the comparison; do not substitute the POSIX function specification for the Java formatting rules.
- The [X509Certificate description][x509-certificate] attributes work to ANSI X9. An organization or committee name is not an
  exact standards-document identity.
- Strings such as `ISO 1989-01-06` in calendar examples are formatted dates, not standards numbers. Charset examples likewise
  do not establish mandatory support merely because their names resemble standards identifiers.

## Mapping boundary and source limitations

This supplement records explicit dependencies that hyperlink-only capture would miss or underspecify.
Under the [finite stopping rule](../../scope.md#inclusion-and-stopping-rules), each record ends at the
named dependency and its originating passage, retaining any stated edition and available publisher link.
An edition or URL not specified by the source is recorded as such. It is not filled with an invented identity.

The unspecified editions and OECF identity conflict remain source limitations for later consultation.
Their registration fulfills the mapping obligation; resolving their normative meaning or recursively
inventorying external standards is not a prerequisite for [map closure](../../coverage.md#closure-status).
A concrete explicit reference omitted from this map remains a defect to correct. Formatting and link
checks establish artifact properties, not the correctness of an interpretation.

## References

The links in each entry identify the source passage that names the dependency. ISO catalog links below are used only for the
OECF identity conflict; they are not substituted for missing normative editions.

[command-apdu]: https://docs.oracle.com/en/java/javase/25/docs/api/java.smartcardio/javax/smartcardio/CommandAPDU.html#class-description
[card-transmit]: https://docs.oracle.com/en/java/javase/25/docs/api/java.smartcardio/javax/smartcardio/CardChannel.html#transmit(javax.smartcardio.CommandAPDU)
[smartcard-package]: https://docs.oracle.com/en/java/javase/25/docs/api/java.smartcardio/javax/smartcardio/package-summary.html#package-description
[signature-names]: https://docs.oracle.com/en/java/javase/25/docs/specs/security/standard-names.html#signature-algorithms
[keyagreement-names]: https://docs.oracle.com/en/java/javase/25/docs/specs/security/standard-names.html#keyagreement-algorithms
[platform-signature-names]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/security/standard-names.html#signature-algorithms
[platform-keyagreement-names]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/security/standard-names.html#keyagreement-algorithms
[crypto-spec]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/spec/package-summary.html#package-description
[dh-parameters]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/spec/DHParameterSpec.html#class-description
[security-package]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/package-summary.html#package-description
[pem-encoder]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/PEMEncoder.html#class-description
[pkcs8-spec]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/spec/PKCS8EncodedKeySpec.html#class-description
[keystore-key]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyStore.html#setKeyEntry(java.lang.String,byte[],java.security.cert.Certificate[])
[oaep]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/spec/OAEPParameterSpec.html#class-description
[security-interfaces]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/interfaces/package-summary.html#package-description
[jar-block]: https://docs.oracle.com/en/java/javase/25/docs/specs/jar/jar.html#the-meta-inf-directory
[certificates]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertificateFactorySpi.html#engineGenerateCertificates(java.io.InputStream)
[keytool-request]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/keytool.html#commands-for-generating-a-certificate-request
[keystore-names]: https://docs.oracle.com/en/java/javase/25/docs/specs/security/standard-names.html#keystore-types
[secretkey-names]: https://docs.oracle.com/en/java/javase/25/docs/specs/security/standard-names.html#secretkey-algorithms
[pbe-keys]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/spec/PBEKeySpec.html#class-description
[certpath-encodings]: https://docs.oracle.com/en/java/javase/25/docs/specs/security/standard-names.html#certpath-encodings
[platform-certpath-encodings]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/security/standard-names.html#certpath-encodings
[x501-issuer]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/X509CertSelector.html#setIssuer(byte[])
[ldap-control]: https://docs.oracle.com/en/java/javase/25/docs/api/java.naming/javax/naming/ldap/Control.html#getEncodedValue()
[certificate-encoding]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/Certificate.html#getEncoded()
[iso-speed]: https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/javax/imageio/plugins/tiff/ExifTIFFTagSet.html#TAG_ISO_SPEED_RATINGS
[spatial-response]: https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/javax/imageio/plugins/tiff/ExifTIFFTagSet.html#TAG_SPATIAL_FREQUENCY_RESPONSE
[oecf]: https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/javax/imageio/plugins/tiff/ExifTIFFTagSet.html#TAG_OECF
[iso-14254]: https://www.iso.org/standard/60567.html
[iso-14524]: https://www.iso.org/standard/43527.html
[tiff-exif]: https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/javax/imageio/metadata/doc-files/tiff_metadata.html
[icc-profile]: https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/java/awt/color/ICC_Profile.html#class-description
[midi-meta]: https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/javax/sound/midi/MetaMessage.html#class-description
[midi-message]: https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/javax/sound/midi/MidiMessage.html#class-description
[midi-timing]: https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/javax/sound/midi/Sequence.html#SMPTE_30DROP
[idn]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/IDN.html#class-description
[data-input]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/io/DataInput.html#class-description
[jni-utf8]: https://docs.oracle.com/en/java/javase/25/docs/specs/jni/types.html#modified-utf-8-strings
[platform-jni-utf8]: https://cr.openjdk.org/~iris/se/25/latestSpec/specs/jni/types.html#modified-utf-8-strings
[charset]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/charset/Charset.html#standard
[locale]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Locale.html#class-description
[locale-script]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Locale.html#getScript()
[locale-builder-script]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Locale.Builder.html#setScript(java.lang.String)
[currency]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Currency.html#class-description
[duration]: https://docs.oracle.com/en/java/javase/25/docs/api/java.xml/javax/xml/datatype/DatatypeFactory.html#newDuration(long)
[xml-calendar]: https://docs.oracle.com/en/java/javase/25/docs/api/java.xml/javax/xml/datatype/XMLGregorianCalendar.html#class-description
[local-date]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/LocalDate.html#class-description
[strictmath]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/StrictMath.html#class-description
[fma]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/StrictMath.html#fma(double,double,double)
[decimal128]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/MathContext.html#DECIMAL128
[uuid-node]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/UUID.html#node()
[bigdecimal]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html#class-description
[socket-tos]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/Socket.html#setTrafficClass(int)
[server-window]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/ServerSocket.html#setReceiveBufferSize(int)
[datagram-tos]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/DatagramSocket.html#setTrafficClass(int)
[keytool-import]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/keytool.html#commands-for-creating-or-adding-data-to-the-keystore
[object-factory]: https://docs.oracle.com/en/java/javase/25/docs/api/java.naming/javax/naming/spi/ObjectFactory.html#class-description
[context-language]: https://docs.oracle.com/en/java/javase/25/docs/api/java.naming/javax/naming/Context.html#LANGUAGE
[gss-manager]: https://docs.oracle.com/en/java/javase/25/docs/api/java.security.jgss/org/ietf/jgss/GSSManager.html#class-description
[mac]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/Mac.html#class-description
[sasl-unwrap]: https://docs.oracle.com/en/java/javase/25/docs/api/java.security.sasl/javax/security/sasl/SaslClient.html#unwrap(byte[],int,int)
[x509-data]: https://docs.oracle.com/en/java/javase/25/docs/api/java.xml.crypto/javax/xml/crypto/dsig/keyinfo/KeyInfoFactory.html#newX509Data(java.util.List)
[directory-search]: https://docs.oracle.com/en/java/javase/25/docs/api/java.naming/javax/naming/directory/DirContext.html#search(javax.naming.Name,java.lang.String,javax.naming.directory.SearchControls)
[jndi-login]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.security.auth/com/sun/security/auth/module/JndiLoginModule.html#class-description
[xml-base-uri]: https://docs.oracle.com/en/java/javase/25/docs/api/java.xml.crypto/javax/xml/crypto/dom/DOMCryptoContext.html#setBaseURI(java.lang.String)
[ocsp-soft-fail]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/PKIXRevocationChecker.Option.html#SOFT_FAIL
[network-properties]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/doc-files/net-properties.html
[gss-export]: https://docs.oracle.com/en/java/javase/25/docs/api/java.security.jgss/org/ietf/jgss/GSSName.html#export()
[trust-manager]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/X509ExtendedTrustManager.html#class-description
[sctp-error]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.sctp/com/sun/nio/sctp/SendFailedNotification.html#errorCode()
[cookie-policy]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/CookiePolicy.html#class-description
[jarsigner-options]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jarsigner.html#options-for-jarsigner
[kerberos-key]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.security.jgss/com/sun/security/jgss/InquireType.html#KRB5_GET_SESSION_KEY
[kerberos-time]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.security.jgss/com/sun/security/jgss/InquireType.html#KRB5_GET_AUTHTIME
[jlink-locales]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jlink.html#plugin-include-locales
[base64-decoder]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Base64.Decoder.html#class-description
[pkix-reason]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/PKIXReason.html#class-description
[ocsp-responses]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/ExtendedSSLSession.html#getStatusResponses()
[xec-private]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/interfaces/XECPrivateKey.html#class-description
[character]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html#class-description
[alpn-example]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLParameters.html#setApplicationProtocols(java.lang.String[])
[gss-stream]: https://docs.oracle.com/en/java/javase/25/docs/api/java.security.jgss/org/ietf/jgss/GSSContext.html#acceptSecContext(java.io.InputStream,java.io.OutputStream)
[service-attributes]: https://docs.oracle.com/en/java/javase/25/docs/specs/security/standard-names.html#service-attributes
[bundle-control]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/ResourceBundle.Control.html#getCandidateLocales(java.lang.String,java.util.Locale)
[library-lookup]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/SymbolLookup.html#libraryLookup(java.lang.String,java.lang.foreign.Arena)
[console-terminal]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/io/Console.html#isTerminal()
[formatter]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Formatter.html#class-description
[x509-certificate]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/X509Certificate.html#class-description
