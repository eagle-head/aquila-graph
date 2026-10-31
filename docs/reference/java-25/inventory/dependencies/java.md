# Java narrative specifications

[Dependency inventory](../dependencies.md)

This register transcribes the specification titles, target URLs, and every referring API locator in the Oracle
Java SE 25 and JDK 25 External Specifications index. It records declared specification relationships; the
referring API contract determines which clauses apply.

Target URLs and edition labels are retained as cited. An unversioned address is not silently replaced by a
newer edition. Each record maps the cited source and its origins; external content remains at its publisher.

Records: 7 specification entries; 216 referring API locators.

[Source index][index]

## Documentation Comment Specification for the Standard Doclet

Specification: [Documentation Comment Specification for the Standard Doclet][spec-1].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class jdk.javadoc.doclet.StandardDoclet][spec-1-ref-1]
- [module jdk.javadoc][spec-1-ref-2]
- [package com.sun.source.doctree][spec-1-ref-3]

## JAR File Specification

Specification: [JAR File Specification][spec-2].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class java.util.jar.Attributes][spec-2-ref-1]
- [class java.util.jar.Attributes.Name][spec-2-ref-2]
- [class java.util.jar.Manifest][spec-2-ref-3]
- [package java.util.jar][spec-2-ref-4]

## Java Native Interface Specification

Specification: [Java Native Interface Specification][spec-3].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class java.lang.Runtime][spec-3-ref-1]
- [class java.lang.reflect.AccessibleObject][spec-3-ref-2]
- [java.lang.Runtime.load(String)][spec-3-ref-3]
- [java.lang.Runtime.loadLibrary(String)][spec-3-ref-4]
- [java.lang.System.load(String)][spec-3-ref-5]
- [java.lang.System.loadLibrary(String)][spec-3-ref-6]
- [java.lang.Thread.setName(String)][spec-3-ref-7]
- [java.lang.reflect.AccessibleObject.canAccess(Object)][spec-3-ref-8]
- [java.lang.reflect.AccessibleObject.setAccessible(boolean)][spec-3-ref-9]
- [java.lang.reflect.AccessibleObject.trySetAccessible()][spec-3-ref-10]
- [package java.lang.foreign][spec-3-ref-11]

## Java Object Serialization Specification

Specification: [Java Object Serialization Specification][spec-4].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [annotation interface java.io.Serial][spec-4-ref-1]
- [class java.io.ObjectInputStream][spec-4-ref-2]
- [class java.io.ObjectOutputStream][spec-4-ref-3]
- [class java.io.ObjectStreamClass][spec-4-ref-4]
- [class java.lang.Enum][spec-4-ref-5]
- [class java.lang.Record][spec-4-ref-6]
- [class java.security.KeyRep][spec-4-ref-7]
- [interface java.io.Serializable][spec-4-ref-8]
- [interface java.security.Key][spec-4-ref-9]
- [java.io.ObjectOutputStream.writeClassDescriptor(ObjectStreamClass)][spec-4-ref-10]
- [package java.io][spec-4-ref-11]

## Java Security Standard Algorithm Names

Specification: [Java Security Standard Algorithm Names][spec-5].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class java.security.AlgorithmParameterGenerator][spec-5-ref-1]
- [class java.security.AlgorithmParameters][spec-5-ref-2]
- [class java.security.DrbgParameters][spec-5-ref-3]
- [class java.security.KeyFactory][spec-5-ref-4]
- [class java.security.KeyPairGenerator][spec-5-ref-5]
- [class java.security.KeyStore][spec-5-ref-6]
- [class java.security.MessageDigest][spec-5-ref-7]
- [class java.security.SecureRandom][spec-5-ref-8]
- [class java.security.SecureRandomSpi][spec-5-ref-9]
- [class java.security.Signature][spec-5-ref-10]
- [class java.security.cert.CertPath][spec-5-ref-11]
- [class java.security.cert.CertPathBuilder][spec-5-ref-12]
- [class java.security.cert.CertPathValidator][spec-5-ref-13]
- [class java.security.cert.CertStore][spec-5-ref-14]
- [class java.security.cert.CertificateFactory][spec-5-ref-15]
- [class java.security.spec.NamedParameterSpec][spec-5-ref-16]
- [class javax.crypto.Cipher][spec-5-ref-17]
- [class javax.crypto.KeyAgreement][spec-5-ref-18]
- [class javax.crypto.KeyGenerator][spec-5-ref-19]
- [class javax.crypto.Mac][spec-5-ref-20]
- [class javax.crypto.SecretKeyFactory][spec-5-ref-21]
- [class javax.net.ssl.SSLContext][spec-5-ref-22]
- [class javax.net.ssl.TrustManagerFactory][spec-5-ref-23]
- [class javax.security.auth.login.Configuration][spec-5-ref-24]
- [class javax.xml.crypto.dsig.TransformService][spec-5-ref-25]
- [class javax.xml.crypto.dsig.XMLSignatureFactory][spec-5-ref-26]
- [class javax.xml.crypto.dsig.keyinfo.KeyInfoFactory][spec-5-ref-27]
- [java.security.AlgorithmParameterGenerator.getInstance(String)][spec-5-ref-28]
- [java.security.AlgorithmParameterGenerator.getInstance(String, Provider)][spec-5-ref-29]
- [java.security.AlgorithmParameterGenerator.getInstance(String, String)][spec-5-ref-30]
- [java.security.AlgorithmParameters.getInstance(String)][spec-5-ref-31]
- [java.security.AlgorithmParameters.getInstance(String, Provider)][spec-5-ref-32]
- [java.security.AlgorithmParameters.getInstance(String, String)][spec-5-ref-33]
- [java.security.Key.getAlgorithm()][spec-5-ref-34]
- [java.security.KeyFactory.getInstance(String)][spec-5-ref-35]
- [java.security.KeyFactory.getInstance(String, Provider)][spec-5-ref-36]
- [java.security.KeyFactory.getInstance(String, String)][spec-5-ref-37]
- [java.security.KeyPairGenerator.KeyPairGenerator(String)][spec-5-ref-38]
- [java.security.KeyPairGenerator.getAlgorithm()][spec-5-ref-39]
- [java.security.KeyPairGenerator.getInstance(String)][spec-5-ref-40]
- [java.security.KeyPairGenerator.getInstance(String, Provider)][spec-5-ref-41]
- [java.security.KeyPairGenerator.getInstance(String, String)][spec-5-ref-42]
- [java.security.KeyStore.PasswordProtection.PasswordProtection(char\[\], String,
AlgorithmParameterSpec)][spec-5-ref-43]
- [java.security.KeyStore.getInstance(String)][spec-5-ref-44]
- [java.security.KeyStore.getInstance(String, Provider)][spec-5-ref-45]
- [java.security.KeyStore.getInstance(String, String)][spec-5-ref-46]
- [java.security.MessageDigest.MessageDigest(String)][spec-5-ref-47]
- [java.security.MessageDigest.getAlgorithm()][spec-5-ref-48]
- [java.security.MessageDigest.getInstance(String)][spec-5-ref-49]
- [java.security.MessageDigest.getInstance(String, Provider)][spec-5-ref-50]
- [java.security.MessageDigest.getInstance(String, String)][spec-5-ref-51]
- [java.security.SecureRandom.SecureRandom()][spec-5-ref-52]
- [java.security.SecureRandom.SecureRandom(byte\[\])][spec-5-ref-53]
- [java.security.SecureRandom.getInstance(String)][spec-5-ref-54]
- [java.security.SecureRandom.getInstance(String, Provider)][spec-5-ref-55]
- [java.security.SecureRandom.getInstance(String, SecureRandomParameters)][spec-5-ref-56]
- [java.security.SecureRandom.getInstance(String, SecureRandomParameters, Provider)][spec-5-ref-57]
- [java.security.SecureRandom.getInstance(String, SecureRandomParameters, String)][spec-5-ref-58]
- [java.security.SecureRandom.getInstance(String, String)][spec-5-ref-59]
- [java.security.Security.getAlgorithmProperty(String, String)][spec-5-ref-60]
- [java.security.Security.getProviders(Map)][spec-5-ref-61]
- [java.security.Security.getProviders(String)][spec-5-ref-62]
- [java.security.Signature.Signature(String)][spec-5-ref-63]
- [java.security.Signature.getInstance(String)][spec-5-ref-64]
- [java.security.Signature.getInstance(String, Provider)][spec-5-ref-65]
- [java.security.Signature.getInstance(String, String)][spec-5-ref-66]
- [java.security.cert.CRL.CRL(String)][spec-5-ref-67]
- [java.security.cert.CertPathBuilder.getInstance(String)][spec-5-ref-68]
- [java.security.cert.CertPathBuilder.getInstance(String, Provider)][spec-5-ref-69]
- [java.security.cert.CertPathBuilder.getInstance(String, String)][spec-5-ref-70]
- [java.security.cert.CertPathValidator.getInstance(String)][spec-5-ref-71]
- [java.security.cert.CertPathValidator.getInstance(String, Provider)][spec-5-ref-72]
- [java.security.cert.CertPathValidator.getInstance(String, String)][spec-5-ref-73]
- [java.security.cert.CertStore.getInstance(String, CertStoreParameters)][spec-5-ref-74]
- [java.security.cert.CertStore.getInstance(String, CertStoreParameters, Provider)][spec-5-ref-75]
- [java.security.cert.CertStore.getInstance(String, CertStoreParameters, String)][spec-5-ref-76]
- [java.security.cert.Certificate.Certificate(String)][spec-5-ref-77]
- [java.security.cert.CertificateFactory.generateCertPath(InputStream, String)][spec-5-ref-78]
- [java.security.cert.CertificateFactory.getCertPathEncodings()][spec-5-ref-79]
- [java.security.cert.CertificateFactory.getInstance(String)][spec-5-ref-80]
- [java.security.cert.CertificateFactory.getInstance(String, Provider)][spec-5-ref-81]
- [java.security.cert.CertificateFactory.getInstance(String, String)][spec-5-ref-82]
- [java.security.cert.CertificateFactorySpi.engineGetCertPathEncodings()][spec-5-ref-83]
- [java.security.spec.ECGenParameterSpec.ECGenParameterSpec(String)][spec-5-ref-84]
- [java.security.spec.EncodedKeySpec.EncodedKeySpec(byte\[\], String)][spec-5-ref-85]
- [java.security.spec.EncodedKeySpec.EncodedKeySpec(byte\[\], String)][spec-5-ref-86]
- [java.security.spec.NamedParameterSpec.NamedParameterSpec(String)][spec-5-ref-87]
- [java.security.spec.PKCS8EncodedKeySpec.PKCS8EncodedKeySpec(byte\[\], String)][spec-5-ref-88]
- [java.security.spec.PKCS8EncodedKeySpec.PKCS8EncodedKeySpec(byte\[\], String)][spec-5-ref-89]
- [java.security.spec.PSSParameterSpec.PSSParameterSpec(String, String, AlgorithmParameterSpec, int,
int)][spec-5-ref-90]
- [java.security.spec.X509EncodedKeySpec.X509EncodedKeySpec(byte\[\], String)][spec-5-ref-91]
- [java.security.spec.X509EncodedKeySpec.X509EncodedKeySpec(byte\[\], String)][spec-5-ref-92]
- [javax.crypto.Cipher.getInstance(String)][spec-5-ref-93]
- [javax.crypto.Cipher.getInstance(String, Provider)][spec-5-ref-94]
- [javax.crypto.Cipher.getInstance(String, String)][spec-5-ref-95]
- [javax.crypto.EncryptedPrivateKeyInfo.EncryptedPrivateKeyInfo(String, byte\[\])][spec-5-ref-96]
- [javax.crypto.EncryptedPrivateKeyInfo.getAlgName()][spec-5-ref-97]
- [javax.crypto.ExemptionMechanism.getInstance(String)][spec-5-ref-98]
- [javax.crypto.ExemptionMechanism.getInstance(String, Provider)][spec-5-ref-99]
- [javax.crypto.ExemptionMechanism.getInstance(String, String)][spec-5-ref-100]
- [javax.crypto.KDF.deriveKey(String, AlgorithmParameterSpec)][spec-5-ref-101]
- [javax.crypto.KDF.getInstance(String)][spec-5-ref-102]
- [javax.crypto.KDF.getInstance(String, KDFParameters)][spec-5-ref-103]
- [javax.crypto.KDF.getInstance(String, KDFParameters, Provider)][spec-5-ref-104]
- [javax.crypto.KDF.getInstance(String, KDFParameters, String)][spec-5-ref-105]
- [javax.crypto.KDF.getInstance(String, Provider)][spec-5-ref-106]
- [javax.crypto.KDF.getInstance(String, String)][spec-5-ref-107]
- [javax.crypto.KDFSpi.engineDeriveKey(String, AlgorithmParameterSpec)][spec-5-ref-108]
- [javax.crypto.KEM.Decapsulator.decapsulate(byte\[\], int, int, String)][spec-5-ref-109]
- [javax.crypto.KEM.Encapsulator.encapsulate(int, int, String)][spec-5-ref-110]
- [javax.crypto.KEM.getInstance(String)][spec-5-ref-111]
- [javax.crypto.KEM.getInstance(String, Provider)][spec-5-ref-112]
- [javax.crypto.KEM.getInstance(String, String)][spec-5-ref-113]
- [javax.crypto.KEMSpi.DecapsulatorSpi.engineDecapsulate(byte\[\], int, int, String)][spec-5-ref-114]
- [javax.crypto.KEMSpi.EncapsulatorSpi.engineEncapsulate(int, int, String)][spec-5-ref-115]
- [javax.crypto.KeyAgreement.generateSecret(String)][spec-5-ref-116]
- [javax.crypto.KeyAgreement.getInstance(String)][spec-5-ref-117]
- [javax.crypto.KeyAgreement.getInstance(String, Provider)][spec-5-ref-118]
- [javax.crypto.KeyAgreement.getInstance(String, String)][spec-5-ref-119]
- [javax.crypto.KeyAgreementSpi.engineGenerateSecret(String)][spec-5-ref-120]
- [javax.crypto.KeyGenerator.getInstance(String)][spec-5-ref-121]
- [javax.crypto.KeyGenerator.getInstance(String, Provider)][spec-5-ref-122]
- [javax.crypto.KeyGenerator.getInstance(String, String)][spec-5-ref-123]
- [javax.crypto.Mac.getInstance(String)][spec-5-ref-124]
- [javax.crypto.Mac.getInstance(String, Provider)][spec-5-ref-125]
- [javax.crypto.Mac.getInstance(String, String)][spec-5-ref-126]
- [javax.crypto.SecretKeyFactory.getInstance(String)][spec-5-ref-127]
- [javax.crypto.SecretKeyFactory.getInstance(String, Provider)][spec-5-ref-128]
- [javax.crypto.SecretKeyFactory.getInstance(String, String)][spec-5-ref-129]
- [javax.crypto.spec.SecretKeySpec.SecretKeySpec(byte\[\], String)][spec-5-ref-130]
- [javax.crypto.spec.SecretKeySpec.SecretKeySpec(byte\[\], int, int, String)][spec-5-ref-131]
- [javax.net.ssl.ExtendedSSLSession.getLocalSupportedSignatureAlgorithms()][spec-5-ref-132]
- [javax.net.ssl.ExtendedSSLSession.getPeerSupportedSignatureAlgorithms()][spec-5-ref-133]
- [javax.net.ssl.KeyManagerFactory.getInstance(String)][spec-5-ref-134]
- [javax.net.ssl.KeyManagerFactory.getInstance(String, Provider)][spec-5-ref-135]
- [javax.net.ssl.KeyManagerFactory.getInstance(String, String)][spec-5-ref-136]
- [javax.net.ssl.SSLContext.getInstance(String)][spec-5-ref-137]
- [javax.net.ssl.SSLContext.getInstance(String, Provider)][spec-5-ref-138]
- [javax.net.ssl.SSLContext.getInstance(String, String)][spec-5-ref-139]
- [javax.net.ssl.SSLEngine.getEnabledCipherSuites()][spec-5-ref-140]
- [javax.net.ssl.SSLEngine.getSupportedCipherSuites()][spec-5-ref-141]
- [javax.net.ssl.SSLEngine.setEnabledCipherSuites(String\[\])][spec-5-ref-142]
- [javax.net.ssl.SSLParameters.SSLParameters(String\[\])][spec-5-ref-143]
- [javax.net.ssl.SSLParameters.SSLParameters(String\[\], String\[\])][spec-5-ref-144]
- [javax.net.ssl.SSLParameters.getCipherSuites()][spec-5-ref-145]
- [javax.net.ssl.SSLParameters.getNamedGroups()][spec-5-ref-146]
- [javax.net.ssl.SSLParameters.getSignatureSchemes()][spec-5-ref-147]
- [javax.net.ssl.SSLParameters.setCipherSuites(String\[\])][spec-5-ref-148]
- [javax.net.ssl.SSLParameters.setEndpointIdentificationAlgorithm(String)][spec-5-ref-149]
- [javax.net.ssl.SSLParameters.setNamedGroups(String\[\])][spec-5-ref-150]
- [javax.net.ssl.SSLParameters.setSignatureSchemes(String\[\])][spec-5-ref-151]
- [javax.net.ssl.SSLServerSocket.getEnabledCipherSuites()][spec-5-ref-152]
- [javax.net.ssl.SSLServerSocket.getSupportedCipherSuites()][spec-5-ref-153]
- [javax.net.ssl.SSLServerSocket.setEnabledCipherSuites(String\[\])][spec-5-ref-154]
- [javax.net.ssl.SSLServerSocketFactory.getDefaultCipherSuites()][spec-5-ref-155]
- [javax.net.ssl.SSLServerSocketFactory.getSupportedCipherSuites()][spec-5-ref-156]
- [javax.net.ssl.SSLSocket.getEnabledCipherSuites()][spec-5-ref-157]
- [javax.net.ssl.SSLSocket.getSupportedCipherSuites()][spec-5-ref-158]
- [javax.net.ssl.SSLSocket.setEnabledCipherSuites(String\[\])][spec-5-ref-159]
- [javax.net.ssl.SSLSocketFactory.getDefaultCipherSuites()][spec-5-ref-160]
- [javax.net.ssl.SSLSocketFactory.getSupportedCipherSuites()][spec-5-ref-161]
- [javax.net.ssl.TrustManagerFactory.getInstance(String)][spec-5-ref-162]
- [javax.net.ssl.TrustManagerFactory.getInstance(String, Provider)][spec-5-ref-163]
- [javax.net.ssl.TrustManagerFactory.getInstance(String, String)][spec-5-ref-164]
- [javax.security.auth.login.Configuration.getInstance(String, Configuration.Parameters)][spec-5-ref-165]
- [javax.security.auth.login.Configuration.getInstance(String, Configuration.Parameters,
Provider)][spec-5-ref-166]
- [javax.security.auth.login.Configuration.getInstance(String, Configuration.Parameters,
String)][spec-5-ref-167]
- [javax.xml.crypto.dsig.TransformService.getInstance(String, String)][spec-5-ref-168]
- [javax.xml.crypto.dsig.TransformService.getInstance(String, String, Provider)][spec-5-ref-169]
- [javax.xml.crypto.dsig.TransformService.getInstance(String, String, String)][spec-5-ref-170]
- [javax.xml.crypto.dsig.XMLSignatureFactory.getInstance(String)][spec-5-ref-171]
- [javax.xml.crypto.dsig.XMLSignatureFactory.getInstance(String, Provider)][spec-5-ref-172]
- [javax.xml.crypto.dsig.XMLSignatureFactory.getInstance(String, String)][spec-5-ref-173]
- [javax.xml.crypto.dsig.keyinfo.KeyInfoFactory.getInstance(String)][spec-5-ref-174]
- [javax.xml.crypto.dsig.keyinfo.KeyInfoFactory.getInstance(String, Provider)][spec-5-ref-175]
- [javax.xml.crypto.dsig.keyinfo.KeyInfoFactory.getInstance(String, String)][spec-5-ref-176]
- [jdk.security.jarsigner.JarSigner.Builder.digestAlgorithm(String)][spec-5-ref-177]
- [jdk.security.jarsigner.JarSigner.Builder.digestAlgorithm(String, Provider)][spec-5-ref-178]
- [jdk.security.jarsigner.JarSigner.Builder.signatureAlgorithm(String)][spec-5-ref-179]
- [jdk.security.jarsigner.JarSigner.Builder.signatureAlgorithm(String, Provider)][spec-5-ref-180]
- [package java.security][spec-5-ref-181]
- [package java.security.cert][spec-5-ref-182]
- [package javax.crypto][spec-5-ref-183]
- [package javax.net.ssl][spec-5-ref-184]
- [package javax.security.auth.login][spec-5-ref-185]

## JVM Tool Interface

Specification: [JVM Tool Interface][spec-6].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [java.lang.management.ThreadInfo.isSuspended()][spec-6-ref-1]

## The Java AWT Native Interface Specification and Guide

Specification: [The Java AWT Native Interface Specification and Guide][spec-7].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [package java.awt][spec-7-ref-1]

## References

The links below preserve the source index targets and declaration anchors.

[index]: https://docs.oracle.com/en/java/javase/25/docs/api/external-specs.html
[spec-1]: https://docs.oracle.com/en/java/javase/25/docs/specs/javadoc/doc-comment-spec.html
[spec-1-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.javadoc/jdk/javadoc/doclet/StandardDoclet.html#DocumentationCommentSpecificationfortheStandardDoclet
[spec-1-ref-2]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.javadoc/module-summary.html#DocumentationCommentSpecificationfortheStandardDoclet
[spec-1-ref-3]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/package-summary.html#DocumentationCommentSpecificationfortheStandardDoclet
[spec-2]: https://docs.oracle.com/en/java/javase/25/docs/specs/jar/jar.html
[spec-2-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/jar/Attributes.html#JARFileSpecification
[spec-2-ref-2]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/jar/Attributes.Name.html#JARFileSpecification
[spec-2-ref-3]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/jar/Manifest.html#JARFileSpecification
[spec-2-ref-4]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/jar/package-summary.html#JARFileSpecification
[spec-3]: https://docs.oracle.com/en/java/javase/25/docs/specs/jni/index.html
[spec-3-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Runtime.html#JavaNativeInterfaceSpecification
[spec-3-ref-2]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/reflect/AccessibleObject.html#JavaNativeInterfaceSpecification
[spec-3-ref-3]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Runtime.html#JavaNativeInterfaceSpecification-1
[spec-3-ref-4]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Runtime.html#JavaNativeInterfaceSpecification-2
[spec-3-ref-5]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/System.html#JavaNativeInterfaceSpecification
[spec-3-ref-6]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/System.html#JavaNativeInterfaceSpecification-1
[spec-3-ref-7]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Thread.html#JavaNativeInterfaceSpecification
[spec-3-ref-8]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/reflect/AccessibleObject.html#JavaNativeInterfaceSpecification-3
[spec-3-ref-9]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/reflect/AccessibleObject.html#JavaNativeInterfaceSpecification-1
[spec-3-ref-10]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/reflect/AccessibleObject.html#JavaNativeInterfaceSpecification-2
[spec-3-ref-11]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/package-summary.html#JavaNativeInterfaceSpecification
[spec-4]: https://docs.oracle.com/en/java/javase/25/docs/specs/serialization/index.html
[spec-4-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/io/Serial.html#JavaObjectSerializationSpecification
[spec-4-ref-2]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/io/ObjectInputStream.html#JavaObjectSerializationSpecification
[spec-4-ref-3]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/io/ObjectOutputStream.html#JavaObjectSerializationSpecification
[spec-4-ref-4]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/io/ObjectStreamClass.html#JavaObjectSerializationSpecification
[spec-4-ref-5]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Enum.html#JavaObjectSerializationSpecification
[spec-4-ref-6]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Record.html#JavaObjectSerializationSpecification
[spec-4-ref-7]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyRep.html#JavaObjectSerializationSpecification
[spec-4-ref-8]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/io/Serializable.html#JavaObjectSerializationSpecification
[spec-4-ref-9]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Key.html#JavaObjectSerializationSpecification
[spec-4-ref-10]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/io/ObjectOutputStream.html#JavaObjectSerializationSpecification-1
[spec-4-ref-11]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/io/package-summary.html#JavaObjectSerializationSpecification
[spec-5]: https://docs.oracle.com/en/java/javase/25/docs/specs/security/standard-names.html
[spec-5-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/AlgorithmParameterGenerator.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-2]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/AlgorithmParameters.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-3]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/DrbgParameters.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-4]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyFactory.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-5]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyPairGenerator.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-6]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyStore.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-7]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/MessageDigest.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-8]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureRandom.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-9]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureRandomSpi.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-10]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Signature.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-11]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertPath.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-12]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertPathBuilder.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-13]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertPathValidator.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-14]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertStore.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-15]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertificateFactory.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-16]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/spec/NamedParameterSpec.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-17]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/Cipher.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-18]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KeyAgreement.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-19]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KeyGenerator.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-20]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/Mac.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-21]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/SecretKeyFactory.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-22]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLContext.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-23]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/TrustManagerFactory.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-24]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/security/auth/login/Configuration.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-25]: https://docs.oracle.com/en/java/javase/25/docs/api/java.xml.crypto/javax/xml/crypto/dsig/TransformService.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-26]: https://docs.oracle.com/en/java/javase/25/docs/api/java.xml.crypto/javax/xml/crypto/dsig/XMLSignatureFactory.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-27]: https://docs.oracle.com/en/java/javase/25/docs/api/java.xml.crypto/javax/xml/crypto/dsig/keyinfo/KeyInfoFactory.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-28]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/AlgorithmParameterGenerator.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-29]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/AlgorithmParameterGenerator.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-30]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/AlgorithmParameterGenerator.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-31]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/AlgorithmParameters.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-32]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/AlgorithmParameters.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-33]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/AlgorithmParameters.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-34]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Key.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-35]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyFactory.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-36]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyFactory.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-37]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyFactory.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-38]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyPairGenerator.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-39]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyPairGenerator.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-40]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyPairGenerator.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-41]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyPairGenerator.html#JavaSecurityStandardAlgorithmNames-5
[spec-5-ref-42]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyPairGenerator.html#JavaSecurityStandardAlgorithmNames-4
[spec-5-ref-43]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyStore.PasswordProtection.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-44]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyStore.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-45]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyStore.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-46]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyStore.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-47]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/MessageDigest.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-48]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/MessageDigest.html#JavaSecurityStandardAlgorithmNames-5
[spec-5-ref-49]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/MessageDigest.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-50]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/MessageDigest.html#JavaSecurityStandardAlgorithmNames-4
[spec-5-ref-51]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/MessageDigest.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-52]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureRandom.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-53]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureRandom.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-54]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureRandom.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-55]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureRandom.html#JavaSecurityStandardAlgorithmNames-5
[spec-5-ref-56]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureRandom.html#JavaSecurityStandardAlgorithmNames-6
[spec-5-ref-57]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureRandom.html#JavaSecurityStandardAlgorithmNames-8
[spec-5-ref-58]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureRandom.html#JavaSecurityStandardAlgorithmNames-7
[spec-5-ref-59]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureRandom.html#JavaSecurityStandardAlgorithmNames-4
[spec-5-ref-60]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Security.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-61]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Security.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-62]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Security.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-63]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Signature.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-64]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Signature.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-65]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Signature.html#JavaSecurityStandardAlgorithmNames-4
[spec-5-ref-66]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Signature.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-67]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CRL.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-68]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertPathBuilder.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-69]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertPathBuilder.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-70]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertPathBuilder.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-71]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertPathValidator.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-72]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertPathValidator.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-73]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertPathValidator.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-74]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertStore.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-75]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertStore.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-76]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertStore.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-77]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/Certificate.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-78]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertificateFactory.html#JavaSecurityStandardAlgorithmNames-5
[spec-5-ref-79]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertificateFactory.html#JavaSecurityStandardAlgorithmNames-4
[spec-5-ref-80]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertificateFactory.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-81]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertificateFactory.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-82]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertificateFactory.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-83]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/CertificateFactorySpi.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-84]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/spec/ECGenParameterSpec.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-85]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/spec/EncodedKeySpec.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-86]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/spec/EncodedKeySpec.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-87]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/spec/NamedParameterSpec.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-88]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/spec/PKCS8EncodedKeySpec.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-89]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/spec/PKCS8EncodedKeySpec.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-90]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/spec/PSSParameterSpec.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-91]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/spec/X509EncodedKeySpec.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-92]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/spec/X509EncodedKeySpec.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-93]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/Cipher.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-94]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/Cipher.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-95]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/Cipher.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-96]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/EncryptedPrivateKeyInfo.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-97]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/EncryptedPrivateKeyInfo.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-98]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/ExemptionMechanism.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-99]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/ExemptionMechanism.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-100]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/ExemptionMechanism.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-101]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KDF.html#JavaSecurityStandardAlgorithmNames-6
[spec-5-ref-102]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KDF.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-103]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KDF.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-104]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KDF.html#JavaSecurityStandardAlgorithmNames-5
[spec-5-ref-105]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KDF.html#JavaSecurityStandardAlgorithmNames-4
[spec-5-ref-106]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KDF.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-107]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KDF.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-108]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KDFSpi.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-109]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KEM.Decapsulator.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-110]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KEM.Encapsulator.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-111]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KEM.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-112]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KEM.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-113]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KEM.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-114]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KEMSpi.DecapsulatorSpi.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-115]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KEMSpi.EncapsulatorSpi.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-116]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KeyAgreement.html#JavaSecurityStandardAlgorithmNames-4
[spec-5-ref-117]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KeyAgreement.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-118]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KeyAgreement.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-119]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KeyAgreement.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-120]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KeyAgreementSpi.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-121]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KeyGenerator.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-122]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KeyGenerator.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-123]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KeyGenerator.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-124]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/Mac.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-125]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/Mac.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-126]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/Mac.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-127]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/SecretKeyFactory.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-128]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/SecretKeyFactory.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-129]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/SecretKeyFactory.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-130]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/spec/SecretKeySpec.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-131]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/spec/SecretKeySpec.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-132]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/ExtendedSSLSession.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-133]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/ExtendedSSLSession.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-134]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/KeyManagerFactory.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-135]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/KeyManagerFactory.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-136]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/KeyManagerFactory.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-137]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLContext.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-138]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLContext.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-139]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLContext.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-140]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLEngine.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-141]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLEngine.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-142]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLEngine.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-143]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLParameters.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-144]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLParameters.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-145]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLParameters.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-146]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLParameters.html#JavaSecurityStandardAlgorithmNames-7
[spec-5-ref-147]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLParameters.html#JavaSecurityStandardAlgorithmNames-5
[spec-5-ref-148]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLParameters.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-149]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLParameters.html#JavaSecurityStandardAlgorithmNames-4
[spec-5-ref-150]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLParameters.html#JavaSecurityStandardAlgorithmNames-8
[spec-5-ref-151]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLParameters.html#JavaSecurityStandardAlgorithmNames-6
[spec-5-ref-152]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLServerSocket.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-153]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLServerSocket.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-154]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLServerSocket.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-155]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLServerSocketFactory.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-156]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLServerSocketFactory.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-157]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLSocket.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-158]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLSocket.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-159]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLSocket.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-160]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLSocketFactory.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-161]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/SSLSocketFactory.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-162]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/TrustManagerFactory.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-163]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/TrustManagerFactory.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-164]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/TrustManagerFactory.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-165]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/security/auth/login/Configuration.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-166]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/security/auth/login/Configuration.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-167]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/security/auth/login/Configuration.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-168]: https://docs.oracle.com/en/java/javase/25/docs/api/java.xml.crypto/javax/xml/crypto/dsig/TransformService.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-169]: https://docs.oracle.com/en/java/javase/25/docs/api/java.xml.crypto/javax/xml/crypto/dsig/TransformService.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-170]: https://docs.oracle.com/en/java/javase/25/docs/api/java.xml.crypto/javax/xml/crypto/dsig/TransformService.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-171]: https://docs.oracle.com/en/java/javase/25/docs/api/java.xml.crypto/javax/xml/crypto/dsig/XMLSignatureFactory.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-172]: https://docs.oracle.com/en/java/javase/25/docs/api/java.xml.crypto/javax/xml/crypto/dsig/XMLSignatureFactory.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-173]: https://docs.oracle.com/en/java/javase/25/docs/api/java.xml.crypto/javax/xml/crypto/dsig/XMLSignatureFactory.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-174]: https://docs.oracle.com/en/java/javase/25/docs/api/java.xml.crypto/javax/xml/crypto/dsig/keyinfo/KeyInfoFactory.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-175]: https://docs.oracle.com/en/java/javase/25/docs/api/java.xml.crypto/javax/xml/crypto/dsig/keyinfo/KeyInfoFactory.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-176]: https://docs.oracle.com/en/java/javase/25/docs/api/java.xml.crypto/javax/xml/crypto/dsig/keyinfo/KeyInfoFactory.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-177]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jartool/jdk/security/jarsigner/JarSigner.Builder.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-178]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jartool/jdk/security/jarsigner/JarSigner.Builder.html#JavaSecurityStandardAlgorithmNames-1
[spec-5-ref-179]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jartool/jdk/security/jarsigner/JarSigner.Builder.html#JavaSecurityStandardAlgorithmNames-2
[spec-5-ref-180]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jartool/jdk/security/jarsigner/JarSigner.Builder.html#JavaSecurityStandardAlgorithmNames-3
[spec-5-ref-181]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/package-summary.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-182]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/cert/package-summary.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-183]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/package-summary.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-184]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/net/ssl/package-summary.html#JavaSecurityStandardAlgorithmNames
[spec-5-ref-185]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/security/auth/login/package-summary.html#JavaSecurityStandardAlgorithmNames
[spec-6]: https://docs.oracle.com/en/java/javase/25/docs/specs/jvmti.html
[spec-6-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.management/java/lang/management/ThreadInfo.html#JVMToolInterface
[spec-7]: https://docs.oracle.com/en/java/javase/25/docs/specs/AWT_Native_Interface.html
[spec-7-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/java/awt/package-summary.html#TheJavaAWTNativeInterfaceSpecificationandGuide
