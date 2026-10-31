# java.security.jgss API differences

[Differences index](../differences.md)

This is a document inventory of the Java SE 25 platform Annex 2 comparison between
`jdk-24-ga` and `jdk-25+36`. Each document can contain old, new, removed, unchanged, or
changed material. Its existence does not make all displayed declarations current Java 25 APIs.
The final API inventory owns current declaration membership; this inventory owns the
annotated comparison source identities. All listed comparison documents are **Source mapped**.

| Document path within module | Source role | Official source |
| --- | --- | --- |
| `javax/security/auth/kerberos/DelegationPermission.html` | Declaration comparison | [Document][difference-javax-security-auth-kerberos-DelegationPermission] |
| `javax/security/auth/kerberos/EncryptionKey.html` | Declaration comparison | [Document][difference-javax-security-auth-kerberos-EncryptionKey] |
| `javax/security/auth/kerberos/KerberosCredMessage.html` | Declaration comparison | [Document][difference-javax-security-auth-kerberos-KerberosCredMessage] |
| `javax/security/auth/kerberos/KerberosKey.html` | Declaration comparison | [Document][difference-javax-security-auth-kerberos-KerberosKey] |
| `javax/security/auth/kerberos/KerberosPrincipal.html` | Declaration comparison | [Document][difference-javax-security-auth-kerberos-KerberosPrincipal] |
| `javax/security/auth/kerberos/KerberosTicket.html` | Declaration comparison | [Document][difference-javax-security-auth-kerberos-KerberosTicket] |
| `javax/security/auth/kerberos/KeyTab.html` | Declaration comparison | [Document][difference-javax-security-auth-kerberos-KeyTab] |
| `javax/security/auth/kerberos/ServicePermission.html` | Declaration comparison | [Document][difference-javax-security-auth-kerberos-ServicePermission] |
| `javax/security/auth/kerberos/package-summary.html` | Package comparison | [Document][difference-javax-security-auth-kerberos-package-summary] |
| `module-summary.html` | Module comparison | [Document][difference-module-summary] |

## Unavailable referenced documents

These addresses were emitted by the comparison publication and remain explicitly
**Unresolved identity**. A failed retrieval does not establish that a declaration
or requirement is absent from Java 25.

| Referenced document path | Retrieval result | Destination | Example referring page |
| --- | --- | --- | --- |
| `org/ietf/jgss/GSSException.html` | `The requested URL returned error: 404` | [Destination][unavailable-org-ietf-jgss-GSSException] | [Source][unavailable-org-ietf-jgss-GSSException-origin] |

## References

- [JSR 400 Annex 2][map-link-1].
- Every document row links to its exact source in that publication.

[difference-javax-security-auth-kerberos-DelegationPermission]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.security.jgss/javax/security/auth/kerberos/DelegationPermission.html
[difference-javax-security-auth-kerberos-EncryptionKey]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.security.jgss/javax/security/auth/kerberos/EncryptionKey.html
[difference-javax-security-auth-kerberos-KerberosCredMessage]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.security.jgss/javax/security/auth/kerberos/KerberosCredMessage.html
[difference-javax-security-auth-kerberos-KerberosKey]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.security.jgss/javax/security/auth/kerberos/KerberosKey.html
[difference-javax-security-auth-kerberos-KerberosPrincipal]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.security.jgss/javax/security/auth/kerberos/KerberosPrincipal.html
[difference-javax-security-auth-kerberos-KerberosTicket]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.security.jgss/javax/security/auth/kerberos/KerberosTicket.html
[difference-javax-security-auth-kerberos-KeyTab]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.security.jgss/javax/security/auth/kerberos/KeyTab.html
[difference-javax-security-auth-kerberos-ServicePermission]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.security.jgss/javax/security/auth/kerberos/ServicePermission.html
[difference-javax-security-auth-kerberos-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.security.jgss/javax/security/auth/kerberos/package-summary.html
[difference-module-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.security.jgss/module-summary.html
[unavailable-org-ietf-jgss-GSSException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.security.jgss/org/ietf/jgss/GSSException.html
[unavailable-org-ietf-jgss-GSSException-origin]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.base/java/lang/Exception.html

[map-link-1]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/index.html
