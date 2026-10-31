# java.xml.crypto API differences

[Differences index](../differences.md)

This is a document inventory of the Java SE 25 platform Annex 2 comparison between
`jdk-24-ga` and `jdk-25+36`. Each document can contain old, new, removed, unchanged, or
changed material. Its existence does not make all displayed declarations current Java 25 APIs.
The final API inventory owns current declaration membership; this inventory owns the
annotated comparison source identities. All listed comparison documents are **Source mapped**.

| Document path within module | Source role | Official source |
| --- | --- | --- |
| `javax/xml/crypto/AlgorithmMethod.html` | Declaration comparison | [Document][difference-javax-xml-crypto-AlgorithmMethod] |
| `javax/xml/crypto/Data.html` | Declaration comparison | [Document][difference-javax-xml-crypto-Data] |
| `javax/xml/crypto/KeySelector.Purpose.html` | Declaration comparison | [Document][difference-javax-xml-crypto-KeySelector.Purpose] |
| `javax/xml/crypto/KeySelector.html` | Declaration comparison | [Document][difference-javax-xml-crypto-KeySelector] |
| `javax/xml/crypto/KeySelectorException.html` | Declaration comparison | [Document][difference-javax-xml-crypto-KeySelectorException] |
| `javax/xml/crypto/KeySelectorResult.html` | Declaration comparison | [Document][difference-javax-xml-crypto-KeySelectorResult] |
| `javax/xml/crypto/MarshalException.html` | Declaration comparison | [Document][difference-javax-xml-crypto-MarshalException] |
| `javax/xml/crypto/NoSuchMechanismException.html` | Declaration comparison | [Document][difference-javax-xml-crypto-NoSuchMechanismException] |
| `javax/xml/crypto/NodeSetData.html` | Declaration comparison | [Document][difference-javax-xml-crypto-NodeSetData] |
| `javax/xml/crypto/OctetStreamData.html` | Declaration comparison | [Document][difference-javax-xml-crypto-OctetStreamData] |
| `javax/xml/crypto/URIDereferencer.html` | Declaration comparison | [Document][difference-javax-xml-crypto-URIDereferencer] |
| `javax/xml/crypto/URIReference.html` | Declaration comparison | [Document][difference-javax-xml-crypto-URIReference] |
| `javax/xml/crypto/URIReferenceException.html` | Declaration comparison | [Document][difference-javax-xml-crypto-URIReferenceException] |
| `javax/xml/crypto/XMLCryptoContext.html` | Declaration comparison | [Document][difference-javax-xml-crypto-XMLCryptoContext] |
| `javax/xml/crypto/XMLStructure.html` | Declaration comparison | [Document][difference-javax-xml-crypto-XMLStructure] |
| `javax/xml/crypto/dom/DOMCryptoContext.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dom-DOMCryptoContext] |
| `javax/xml/crypto/dom/DOMStructure.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dom-DOMStructure] |
| `javax/xml/crypto/dom/DOMURIReference.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dom-DOMURIReference] |
| `javax/xml/crypto/dom/package-summary.html` | Package comparison | [Document][difference-javax-xml-crypto-dom-package-summary] |
| `javax/xml/crypto/dsig/CanonicalizationMethod.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-CanonicalizationMethod] |
| `javax/xml/crypto/dsig/DigestMethod.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-DigestMethod] |
| `javax/xml/crypto/dsig/Manifest.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-Manifest] |
| `javax/xml/crypto/dsig/Reference.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-Reference] |
| `javax/xml/crypto/dsig/SignatureMethod.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-SignatureMethod] |
| `javax/xml/crypto/dsig/SignatureProperties.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-SignatureProperties] |
| `javax/xml/crypto/dsig/SignatureProperty.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-SignatureProperty] |
| `javax/xml/crypto/dsig/SignedInfo.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-SignedInfo] |
| `javax/xml/crypto/dsig/Transform.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-Transform] |
| `javax/xml/crypto/dsig/TransformException.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-TransformException] |
| `javax/xml/crypto/dsig/TransformService.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-TransformService] |
| `javax/xml/crypto/dsig/XMLObject.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-XMLObject] |
| `javax/xml/crypto/dsig/XMLSignContext.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-XMLSignContext] |
| `javax/xml/crypto/dsig/XMLSignature.SignatureValue.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-XMLSignature.SignatureValue] |
| `javax/xml/crypto/dsig/XMLSignature.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-XMLSignature] |
| `javax/xml/crypto/dsig/XMLSignatureException.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-XMLSignatureException] |
| `javax/xml/crypto/dsig/XMLSignatureFactory.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-XMLSignatureFactory] |
| `javax/xml/crypto/dsig/XMLValidateContext.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-XMLValidateContext] |
| `javax/xml/crypto/dsig/dom/DOMSignContext.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-dom-DOMSignContext] |
| `javax/xml/crypto/dsig/dom/DOMValidateContext.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-dom-DOMValidateContext] |
| `javax/xml/crypto/dsig/dom/package-summary.html` | Package comparison | [Document][difference-javax-xml-crypto-dsig-dom-package-summary] |
| `javax/xml/crypto/dsig/keyinfo/KeyInfo.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-keyinfo-KeyInfo] |
| `javax/xml/crypto/dsig/keyinfo/KeyInfoFactory.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-keyinfo-KeyInfoFactory] |
| `javax/xml/crypto/dsig/keyinfo/KeyName.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-keyinfo-KeyName] |
| `javax/xml/crypto/dsig/keyinfo/KeyValue.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-keyinfo-KeyValue] |
| `javax/xml/crypto/dsig/keyinfo/PGPData.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-keyinfo-PGPData] |
| `javax/xml/crypto/dsig/keyinfo/RetrievalMethod.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-keyinfo-RetrievalMethod] |
| `javax/xml/crypto/dsig/keyinfo/X509Data.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-keyinfo-X509Data] |
| `javax/xml/crypto/dsig/keyinfo/X509IssuerSerial.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-keyinfo-X509IssuerSerial] |
| `javax/xml/crypto/dsig/keyinfo/package-summary.html` | Package comparison | [Document][difference-javax-xml-crypto-dsig-keyinfo-package-summary] |
| `javax/xml/crypto/dsig/package-summary.html` | Package comparison | [Document][difference-javax-xml-crypto-dsig-package-summary] |
| `javax/xml/crypto/dsig/spec/C14NMethodParameterSpec.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-spec-C14NMethodParameterSpec] |
| `javax/xml/crypto/dsig/spec/DigestMethodParameterSpec.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-spec-DigestMethodParameterSpec] |
| `javax/xml/crypto/dsig/spec/ExcC14NParameterSpec.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-spec-ExcC14NParameterSpec] |
| `javax/xml/crypto/dsig/spec/HMACParameterSpec.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-spec-HMACParameterSpec] |
| `javax/xml/crypto/dsig/spec/RSAPSSParameterSpec.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-spec-RSAPSSParameterSpec] |
| `javax/xml/crypto/dsig/spec/SignatureMethodParameterSpec.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-spec-SignatureMethodParameterSpec] |
| `javax/xml/crypto/dsig/spec/TransformParameterSpec.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-spec-TransformParameterSpec] |
| `javax/xml/crypto/dsig/spec/XPathFilter2ParameterSpec.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-spec-XPathFilter2ParameterSpec] |
| `javax/xml/crypto/dsig/spec/XPathFilterParameterSpec.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-spec-XPathFilterParameterSpec] |
| `javax/xml/crypto/dsig/spec/XPathType.Filter.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-spec-XPathType.Filter] |
| `javax/xml/crypto/dsig/spec/XPathType.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-spec-XPathType] |
| `javax/xml/crypto/dsig/spec/XSLTTransformParameterSpec.html` | Declaration comparison | [Document][difference-javax-xml-crypto-dsig-spec-XSLTTransformParameterSpec] |
| `javax/xml/crypto/dsig/spec/package-summary.html` | Package comparison | [Document][difference-javax-xml-crypto-dsig-spec-package-summary] |
| `javax/xml/crypto/package-summary.html` | Package comparison | [Document][difference-javax-xml-crypto-package-summary] |
| `module-summary.html` | Module comparison | [Document][difference-module-summary] |

## References

- [JSR 400 Annex 2][map-link-1].
- Every document row links to its exact source in that publication.

[difference-javax-xml-crypto-AlgorithmMethod]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/AlgorithmMethod.html
[difference-javax-xml-crypto-Data]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/Data.html
[difference-javax-xml-crypto-KeySelector.Purpose]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/KeySelector.Purpose.html
[difference-javax-xml-crypto-KeySelector]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/KeySelector.html
[difference-javax-xml-crypto-KeySelectorException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/KeySelectorException.html
[difference-javax-xml-crypto-KeySelectorResult]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/KeySelectorResult.html
[difference-javax-xml-crypto-MarshalException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/MarshalException.html
[difference-javax-xml-crypto-NoSuchMechanismException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/NoSuchMechanismException.html
[difference-javax-xml-crypto-NodeSetData]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/NodeSetData.html
[difference-javax-xml-crypto-OctetStreamData]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/OctetStreamData.html
[difference-javax-xml-crypto-URIDereferencer]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/URIDereferencer.html
[difference-javax-xml-crypto-URIReference]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/URIReference.html
[difference-javax-xml-crypto-URIReferenceException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/URIReferenceException.html
[difference-javax-xml-crypto-XMLCryptoContext]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/XMLCryptoContext.html
[difference-javax-xml-crypto-XMLStructure]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/XMLStructure.html
[difference-javax-xml-crypto-dom-DOMCryptoContext]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dom/DOMCryptoContext.html
[difference-javax-xml-crypto-dom-DOMStructure]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dom/DOMStructure.html
[difference-javax-xml-crypto-dom-DOMURIReference]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dom/DOMURIReference.html
[difference-javax-xml-crypto-dom-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dom/package-summary.html
[difference-javax-xml-crypto-dsig-CanonicalizationMethod]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/CanonicalizationMethod.html
[difference-javax-xml-crypto-dsig-DigestMethod]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/DigestMethod.html
[difference-javax-xml-crypto-dsig-Manifest]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/Manifest.html
[difference-javax-xml-crypto-dsig-Reference]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/Reference.html
[difference-javax-xml-crypto-dsig-SignatureMethod]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/SignatureMethod.html
[difference-javax-xml-crypto-dsig-SignatureProperties]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/SignatureProperties.html
[difference-javax-xml-crypto-dsig-SignatureProperty]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/SignatureProperty.html
[difference-javax-xml-crypto-dsig-SignedInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/SignedInfo.html
[difference-javax-xml-crypto-dsig-Transform]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/Transform.html
[difference-javax-xml-crypto-dsig-TransformException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/TransformException.html
[difference-javax-xml-crypto-dsig-TransformService]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/TransformService.html
[difference-javax-xml-crypto-dsig-XMLObject]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/XMLObject.html
[difference-javax-xml-crypto-dsig-XMLSignContext]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/XMLSignContext.html
[difference-javax-xml-crypto-dsig-XMLSignature.SignatureValue]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/XMLSignature.SignatureValue.html
[difference-javax-xml-crypto-dsig-XMLSignature]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/XMLSignature.html
[difference-javax-xml-crypto-dsig-XMLSignatureException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/XMLSignatureException.html
[difference-javax-xml-crypto-dsig-XMLSignatureFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/XMLSignatureFactory.html
[difference-javax-xml-crypto-dsig-XMLValidateContext]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/XMLValidateContext.html
[difference-javax-xml-crypto-dsig-dom-DOMSignContext]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/dom/DOMSignContext.html
[difference-javax-xml-crypto-dsig-dom-DOMValidateContext]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/dom/DOMValidateContext.html
[difference-javax-xml-crypto-dsig-dom-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/dom/package-summary.html
[difference-javax-xml-crypto-dsig-keyinfo-KeyInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/keyinfo/KeyInfo.html
[difference-javax-xml-crypto-dsig-keyinfo-KeyInfoFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/keyinfo/KeyInfoFactory.html
[difference-javax-xml-crypto-dsig-keyinfo-KeyName]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/keyinfo/KeyName.html
[difference-javax-xml-crypto-dsig-keyinfo-KeyValue]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/keyinfo/KeyValue.html
[difference-javax-xml-crypto-dsig-keyinfo-PGPData]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/keyinfo/PGPData.html
[difference-javax-xml-crypto-dsig-keyinfo-RetrievalMethod]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/keyinfo/RetrievalMethod.html
[difference-javax-xml-crypto-dsig-keyinfo-X509Data]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/keyinfo/X509Data.html
[difference-javax-xml-crypto-dsig-keyinfo-X509IssuerSerial]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/keyinfo/X509IssuerSerial.html
[difference-javax-xml-crypto-dsig-keyinfo-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/keyinfo/package-summary.html
[difference-javax-xml-crypto-dsig-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/package-summary.html
[difference-javax-xml-crypto-dsig-spec-C14NMethodParameterSpec]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/spec/C14NMethodParameterSpec.html
[difference-javax-xml-crypto-dsig-spec-DigestMethodParameterSpec]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/spec/DigestMethodParameterSpec.html
[difference-javax-xml-crypto-dsig-spec-ExcC14NParameterSpec]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/spec/ExcC14NParameterSpec.html
[difference-javax-xml-crypto-dsig-spec-HMACParameterSpec]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/spec/HMACParameterSpec.html
[difference-javax-xml-crypto-dsig-spec-RSAPSSParameterSpec]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/spec/RSAPSSParameterSpec.html
[difference-javax-xml-crypto-dsig-spec-SignatureMethodParameterSpec]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/spec/SignatureMethodParameterSpec.html
[difference-javax-xml-crypto-dsig-spec-TransformParameterSpec]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/spec/TransformParameterSpec.html
[difference-javax-xml-crypto-dsig-spec-XPathFilter2ParameterSpec]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/spec/XPathFilter2ParameterSpec.html
[difference-javax-xml-crypto-dsig-spec-XPathFilterParameterSpec]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/spec/XPathFilterParameterSpec.html
[difference-javax-xml-crypto-dsig-spec-XPathType.Filter]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/spec/XPathType.Filter.html
[difference-javax-xml-crypto-dsig-spec-XPathType]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/spec/XPathType.html
[difference-javax-xml-crypto-dsig-spec-XSLTTransformParameterSpec]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/spec/XSLTTransformParameterSpec.html
[difference-javax-xml-crypto-dsig-spec-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/dsig/spec/package-summary.html
[difference-javax-xml-crypto-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/javax/xml/crypto/package-summary.html
[difference-module-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml.crypto/module-summary.html

[map-link-1]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/index.html
