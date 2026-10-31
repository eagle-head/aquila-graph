# java.xml API differences

[Differences index](../differences.md)

This is a document inventory of the Java SE 25 platform Annex 2 comparison between
`jdk-24-ga` and `jdk-25+36`. Each document can contain old, new, removed, unchanged, or
changed material. Its existence does not make all displayed declarations current Java 25 APIs.
The final API inventory owns current declaration membership; this inventory owns the
annotated comparison source identities. All listed comparison documents are **Source mapped**.

| Document path within module | Source role | Official source |
| --- | --- | --- |
| `javax/xml/XMLConstants.html` | Declaration comparison | [Document][difference-javax-xml-XMLConstants] |
| `javax/xml/catalog/Catalog.html` | Declaration comparison | [Document][difference-javax-xml-catalog-Catalog] |
| `javax/xml/catalog/CatalogException.html` | Declaration comparison | [Document][difference-javax-xml-catalog-CatalogException] |
| `javax/xml/catalog/CatalogFeatures.Builder.html` | Declaration comparison | [Document][difference-javax-xml-catalog-CatalogFeatures.Builder] |
| `javax/xml/catalog/CatalogFeatures.Feature.html` | Declaration comparison | [Document][difference-javax-xml-catalog-CatalogFeatures.Feature] |
| `javax/xml/catalog/CatalogFeatures.html` | Declaration comparison | [Document][difference-javax-xml-catalog-CatalogFeatures] |
| `javax/xml/catalog/CatalogManager.html` | Declaration comparison | [Document][difference-javax-xml-catalog-CatalogManager] |
| `javax/xml/catalog/CatalogResolver.NotFoundAction.html` | Declaration comparison | [Document][difference-javax-xml-catalog-CatalogResolver.NotFoundAction] |
| `javax/xml/catalog/CatalogResolver.html` | Declaration comparison | [Document][difference-javax-xml-catalog-CatalogResolver] |
| `javax/xml/catalog/package-summary.html` | Package comparison | [Document][difference-javax-xml-catalog-package-summary] |
| `javax/xml/datatype/DatatypeConfigurationException.html` | Declaration comparison | [Document][difference-javax-xml-datatype-DatatypeConfigurationException] |
| `javax/xml/datatype/DatatypeConstants.Field.html` | Declaration comparison | [Document][difference-javax-xml-datatype-DatatypeConstants.Field] |
| `javax/xml/datatype/DatatypeConstants.html` | Declaration comparison | [Document][difference-javax-xml-datatype-DatatypeConstants] |
| `javax/xml/datatype/DatatypeFactory.html` | Declaration comparison | [Document][difference-javax-xml-datatype-DatatypeFactory] |
| `javax/xml/datatype/Duration.html` | Declaration comparison | [Document][difference-javax-xml-datatype-Duration] |
| `javax/xml/datatype/XMLGregorianCalendar.html` | Declaration comparison | [Document][difference-javax-xml-datatype-XMLGregorianCalendar] |
| `javax/xml/datatype/package-summary.html` | Package comparison | [Document][difference-javax-xml-datatype-package-summary] |
| `javax/xml/namespace/NamespaceContext.html` | Declaration comparison | [Document][difference-javax-xml-namespace-NamespaceContext] |
| `javax/xml/namespace/QName.html` | Declaration comparison | [Document][difference-javax-xml-namespace-QName] |
| `javax/xml/namespace/package-summary.html` | Package comparison | [Document][difference-javax-xml-namespace-package-summary] |
| `javax/xml/package-summary.html` | Package comparison | [Document][difference-javax-xml-package-summary] |
| `javax/xml/parsers/DocumentBuilder.html` | Declaration comparison | [Document][difference-javax-xml-parsers-DocumentBuilder] |
| `javax/xml/parsers/DocumentBuilderFactory.html` | Declaration comparison | [Document][difference-javax-xml-parsers-DocumentBuilderFactory] |
| `javax/xml/parsers/FactoryConfigurationError.html` | Declaration comparison | [Document][difference-javax-xml-parsers-FactoryConfigurationError] |
| `javax/xml/parsers/ParserConfigurationException.html` | Declaration comparison | [Document][difference-javax-xml-parsers-ParserConfigurationException] |
| `javax/xml/parsers/SAXParser.html` | Declaration comparison | [Document][difference-javax-xml-parsers-SAXParser] |
| `javax/xml/parsers/SAXParserFactory.html` | Declaration comparison | [Document][difference-javax-xml-parsers-SAXParserFactory] |
| `javax/xml/parsers/package-summary.html` | Package comparison | [Document][difference-javax-xml-parsers-package-summary] |
| `javax/xml/stream/EventFilter.html` | Declaration comparison | [Document][difference-javax-xml-stream-EventFilter] |
| `javax/xml/stream/FactoryConfigurationError.html` | Declaration comparison | [Document][difference-javax-xml-stream-FactoryConfigurationError] |
| `javax/xml/stream/Location.html` | Declaration comparison | [Document][difference-javax-xml-stream-Location] |
| `javax/xml/stream/StreamFilter.html` | Declaration comparison | [Document][difference-javax-xml-stream-StreamFilter] |
| `javax/xml/stream/XMLEventFactory.html` | Declaration comparison | [Document][difference-javax-xml-stream-XMLEventFactory] |
| `javax/xml/stream/XMLEventReader.html` | Declaration comparison | [Document][difference-javax-xml-stream-XMLEventReader] |
| `javax/xml/stream/XMLEventWriter.html` | Declaration comparison | [Document][difference-javax-xml-stream-XMLEventWriter] |
| `javax/xml/stream/XMLInputFactory.html` | Declaration comparison | [Document][difference-javax-xml-stream-XMLInputFactory] |
| `javax/xml/stream/XMLOutputFactory.html` | Declaration comparison | [Document][difference-javax-xml-stream-XMLOutputFactory] |
| `javax/xml/stream/XMLReporter.html` | Declaration comparison | [Document][difference-javax-xml-stream-XMLReporter] |
| `javax/xml/stream/XMLResolver.html` | Declaration comparison | [Document][difference-javax-xml-stream-XMLResolver] |
| `javax/xml/stream/XMLStreamConstants.html` | Declaration comparison | [Document][difference-javax-xml-stream-XMLStreamConstants] |
| `javax/xml/stream/XMLStreamException.html` | Declaration comparison | [Document][difference-javax-xml-stream-XMLStreamException] |
| `javax/xml/stream/XMLStreamReader.html` | Declaration comparison | [Document][difference-javax-xml-stream-XMLStreamReader] |
| `javax/xml/stream/XMLStreamWriter.html` | Declaration comparison | [Document][difference-javax-xml-stream-XMLStreamWriter] |
| `javax/xml/stream/events/Attribute.html` | Declaration comparison | [Document][difference-javax-xml-stream-events-Attribute] |
| `javax/xml/stream/events/Characters.html` | Declaration comparison | [Document][difference-javax-xml-stream-events-Characters] |
| `javax/xml/stream/events/Comment.html` | Declaration comparison | [Document][difference-javax-xml-stream-events-Comment] |
| `javax/xml/stream/events/DTD.html` | Declaration comparison | [Document][difference-javax-xml-stream-events-DTD] |
| `javax/xml/stream/events/EndDocument.html` | Declaration comparison | [Document][difference-javax-xml-stream-events-EndDocument] |
| `javax/xml/stream/events/EndElement.html` | Declaration comparison | [Document][difference-javax-xml-stream-events-EndElement] |
| `javax/xml/stream/events/EntityDeclaration.html` | Declaration comparison | [Document][difference-javax-xml-stream-events-EntityDeclaration] |
| `javax/xml/stream/events/EntityReference.html` | Declaration comparison | [Document][difference-javax-xml-stream-events-EntityReference] |
| `javax/xml/stream/events/Namespace.html` | Declaration comparison | [Document][difference-javax-xml-stream-events-Namespace] |
| `javax/xml/stream/events/NotationDeclaration.html` | Declaration comparison | [Document][difference-javax-xml-stream-events-NotationDeclaration] |
| `javax/xml/stream/events/ProcessingInstruction.html` | Declaration comparison | [Document][difference-javax-xml-stream-events-ProcessingInstruction] |
| `javax/xml/stream/events/StartDocument.html` | Declaration comparison | [Document][difference-javax-xml-stream-events-StartDocument] |
| `javax/xml/stream/events/StartElement.html` | Declaration comparison | [Document][difference-javax-xml-stream-events-StartElement] |
| `javax/xml/stream/events/XMLEvent.html` | Declaration comparison | [Document][difference-javax-xml-stream-events-XMLEvent] |
| `javax/xml/stream/events/package-summary.html` | Package comparison | [Document][difference-javax-xml-stream-events-package-summary] |
| `javax/xml/stream/package-summary.html` | Package comparison | [Document][difference-javax-xml-stream-package-summary] |
| `javax/xml/stream/util/EventReaderDelegate.html` | Declaration comparison | [Document][difference-javax-xml-stream-util-EventReaderDelegate] |
| `javax/xml/stream/util/StreamReaderDelegate.html` | Declaration comparison | [Document][difference-javax-xml-stream-util-StreamReaderDelegate] |
| `javax/xml/stream/util/XMLEventAllocator.html` | Declaration comparison | [Document][difference-javax-xml-stream-util-XMLEventAllocator] |
| `javax/xml/stream/util/XMLEventConsumer.html` | Declaration comparison | [Document][difference-javax-xml-stream-util-XMLEventConsumer] |
| `javax/xml/stream/util/package-summary.html` | Package comparison | [Document][difference-javax-xml-stream-util-package-summary] |
| `javax/xml/transform/ErrorListener.html` | Declaration comparison | [Document][difference-javax-xml-transform-ErrorListener] |
| `javax/xml/transform/OutputKeys.html` | Declaration comparison | [Document][difference-javax-xml-transform-OutputKeys] |
| `javax/xml/transform/Result.html` | Declaration comparison | [Document][difference-javax-xml-transform-Result] |
| `javax/xml/transform/Source.html` | Declaration comparison | [Document][difference-javax-xml-transform-Source] |
| `javax/xml/transform/SourceLocator.html` | Declaration comparison | [Document][difference-javax-xml-transform-SourceLocator] |
| `javax/xml/transform/Templates.html` | Declaration comparison | [Document][difference-javax-xml-transform-Templates] |
| `javax/xml/transform/Transformer.html` | Declaration comparison | [Document][difference-javax-xml-transform-Transformer] |
| `javax/xml/transform/TransformerConfigurationException.html` | Declaration comparison | [Document][difference-javax-xml-transform-TransformerConfigurationException] |
| `javax/xml/transform/TransformerException.html` | Declaration comparison | [Document][difference-javax-xml-transform-TransformerException] |
| `javax/xml/transform/TransformerFactory.html` | Declaration comparison | [Document][difference-javax-xml-transform-TransformerFactory] |
| `javax/xml/transform/TransformerFactoryConfigurationError.html` | Declaration comparison | [Document][difference-javax-xml-transform-TransformerFactoryConfigurationError] |
| `javax/xml/transform/URIResolver.html` | Declaration comparison | [Document][difference-javax-xml-transform-URIResolver] |
| `javax/xml/transform/dom/DOMLocator.html` | Declaration comparison | [Document][difference-javax-xml-transform-dom-DOMLocator] |
| `javax/xml/transform/dom/DOMResult.html` | Declaration comparison | [Document][difference-javax-xml-transform-dom-DOMResult] |
| `javax/xml/transform/dom/DOMSource.html` | Declaration comparison | [Document][difference-javax-xml-transform-dom-DOMSource] |
| `javax/xml/transform/dom/package-summary.html` | Package comparison | [Document][difference-javax-xml-transform-dom-package-summary] |
| `javax/xml/transform/package-summary.html` | Package comparison | [Document][difference-javax-xml-transform-package-summary] |
| `javax/xml/transform/sax/SAXResult.html` | Declaration comparison | [Document][difference-javax-xml-transform-sax-SAXResult] |
| `javax/xml/transform/sax/SAXSource.html` | Declaration comparison | [Document][difference-javax-xml-transform-sax-SAXSource] |
| `javax/xml/transform/sax/SAXTransformerFactory.html` | Declaration comparison | [Document][difference-javax-xml-transform-sax-SAXTransformerFactory] |
| `javax/xml/transform/sax/TemplatesHandler.html` | Declaration comparison | [Document][difference-javax-xml-transform-sax-TemplatesHandler] |
| `javax/xml/transform/sax/TransformerHandler.html` | Declaration comparison | [Document][difference-javax-xml-transform-sax-TransformerHandler] |
| `javax/xml/transform/sax/package-summary.html` | Package comparison | [Document][difference-javax-xml-transform-sax-package-summary] |
| `javax/xml/transform/stax/StAXResult.html` | Declaration comparison | [Document][difference-javax-xml-transform-stax-StAXResult] |
| `javax/xml/transform/stax/StAXSource.html` | Declaration comparison | [Document][difference-javax-xml-transform-stax-StAXSource] |
| `javax/xml/transform/stax/package-summary.html` | Package comparison | [Document][difference-javax-xml-transform-stax-package-summary] |
| `javax/xml/transform/stream/StreamResult.html` | Declaration comparison | [Document][difference-javax-xml-transform-stream-StreamResult] |
| `javax/xml/transform/stream/StreamSource.html` | Declaration comparison | [Document][difference-javax-xml-transform-stream-StreamSource] |
| `javax/xml/transform/stream/package-summary.html` | Package comparison | [Document][difference-javax-xml-transform-stream-package-summary] |
| `javax/xml/validation/Schema.html` | Declaration comparison | [Document][difference-javax-xml-validation-Schema] |
| `javax/xml/validation/SchemaFactory.html` | Declaration comparison | [Document][difference-javax-xml-validation-SchemaFactory] |
| `javax/xml/validation/SchemaFactoryConfigurationError.html` | Declaration comparison | [Document][difference-javax-xml-validation-SchemaFactoryConfigurationError] |
| `javax/xml/validation/SchemaFactoryLoader.html` | Declaration comparison | [Document][difference-javax-xml-validation-SchemaFactoryLoader] |
| `javax/xml/validation/TypeInfoProvider.html` | Declaration comparison | [Document][difference-javax-xml-validation-TypeInfoProvider] |
| `javax/xml/validation/Validator.html` | Declaration comparison | [Document][difference-javax-xml-validation-Validator] |
| `javax/xml/validation/ValidatorHandler.html` | Declaration comparison | [Document][difference-javax-xml-validation-ValidatorHandler] |
| `javax/xml/validation/package-summary.html` | Package comparison | [Document][difference-javax-xml-validation-package-summary] |
| `javax/xml/xpath/XPath.html` | Declaration comparison | [Document][difference-javax-xml-xpath-XPath] |
| `javax/xml/xpath/XPathConstants.html` | Declaration comparison | [Document][difference-javax-xml-xpath-XPathConstants] |
| `javax/xml/xpath/XPathEvaluationResult.XPathResultType.html` | Declaration comparison | [Document][difference-javax-xml-xpath-XPathEvaluationResult.XPathResultType] |
| `javax/xml/xpath/XPathEvaluationResult.html` | Declaration comparison | [Document][difference-javax-xml-xpath-XPathEvaluationResult] |
| `javax/xml/xpath/XPathException.html` | Declaration comparison | [Document][difference-javax-xml-xpath-XPathException] |
| `javax/xml/xpath/XPathExpression.html` | Declaration comparison | [Document][difference-javax-xml-xpath-XPathExpression] |
| `javax/xml/xpath/XPathExpressionException.html` | Declaration comparison | [Document][difference-javax-xml-xpath-XPathExpressionException] |
| `javax/xml/xpath/XPathFactory.html` | Declaration comparison | [Document][difference-javax-xml-xpath-XPathFactory] |
| `javax/xml/xpath/XPathFactoryConfigurationException.html` | Declaration comparison | [Document][difference-javax-xml-xpath-XPathFactoryConfigurationException] |
| `javax/xml/xpath/XPathFunction.html` | Declaration comparison | [Document][difference-javax-xml-xpath-XPathFunction] |
| `javax/xml/xpath/XPathFunctionException.html` | Declaration comparison | [Document][difference-javax-xml-xpath-XPathFunctionException] |
| `javax/xml/xpath/XPathFunctionResolver.html` | Declaration comparison | [Document][difference-javax-xml-xpath-XPathFunctionResolver] |
| `javax/xml/xpath/XPathNodes.html` | Declaration comparison | [Document][difference-javax-xml-xpath-XPathNodes] |
| `javax/xml/xpath/XPathVariableResolver.html` | Declaration comparison | [Document][difference-javax-xml-xpath-XPathVariableResolver] |
| `javax/xml/xpath/package-summary.html` | Package comparison | [Document][difference-javax-xml-xpath-package-summary] |
| `module-summary.html` | Module comparison | [Document][difference-module-summary] |

## Unavailable referenced documents

These addresses were emitted by the comparison publication and remain explicitly
**Unresolved identity**. A failed retrieval does not establish that a declaration
or requirement is absent from Java 25.

| Referenced document path | Retrieval result | Destination | Example referring page |
| --- | --- | --- | --- |
| `org/w3c/dom/ls/LSSerializer.html` | `The requested URL returned error: 404` | [Destination][unavailable-org-w3c-dom-ls-LSSerializer] | [Source][unavailable-org-w3c-dom-ls-LSSerializer-origin] |
| `org/xml/sax/EntityResolver.html` | `The requested URL returned error: 404` | [Destination][unavailable-org-xml-sax-EntityResolver] | [Source][unavailable-org-xml-sax-EntityResolver-origin] |
| `org/xml/sax/package-summary.html` | `The requested URL returned error: 404` | [Destination][unavailable-org-xml-sax-package-summary] | [Source][unavailable-org-xml-sax-package-summary-origin] |
| `org/w3c/dom/package-summary.html` | `The requested URL returned error: 404` | [Destination][unavailable-org-w3c-dom-package-summary] | [Source][unavailable-org-w3c-dom-package-summary-origin] |
| `org/w3c/dom/ls/LSResourceResolver.html` | `The requested URL returned error: 404` | [Destination][unavailable-org-w3c-dom-ls-LSResourceResolver] | [Source][unavailable-org-w3c-dom-ls-LSResourceResolver-origin] |
| `org/xml/sax/SAXException.html` | `The requested URL returned error: 404` | [Destination][unavailable-org-xml-sax-SAXException] | [Source][unavailable-org-xml-sax-SAXException-origin] |
| `org/w3c/dom/DOMException.html` | `The requested URL returned error: 404` | [Destination][unavailable-org-w3c-dom-DOMException] | [Source][unavailable-org-w3c-dom-DOMException-origin] |
| `org/w3c/dom/ls/LSException.html` | `The requested URL returned error: 404` | [Destination][unavailable-org-w3c-dom-ls-LSException] | [Source][unavailable-org-w3c-dom-ls-LSException-origin] |
| `org/w3c/dom/events/EventException.html` | `The requested URL returned error: 404` | [Destination][unavailable-org-w3c-dom-events-EventException] | [Source][unavailable-org-w3c-dom-events-EventException-origin] |
| `org/w3c/dom/ranges/RangeException.html` | `The requested URL returned error: 404` | [Destination][unavailable-org-w3c-dom-ranges-RangeException] | [Source][unavailable-org-w3c-dom-ranges-RangeException-origin] |

## References

- [JSR 400 Annex 2][map-link-1].
- Every document row links to its exact source in that publication.

[difference-javax-xml-XMLConstants]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/XMLConstants.html
[difference-javax-xml-catalog-Catalog]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/catalog/Catalog.html
[difference-javax-xml-catalog-CatalogException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/catalog/CatalogException.html
[difference-javax-xml-catalog-CatalogFeatures.Builder]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/catalog/CatalogFeatures.Builder.html
[difference-javax-xml-catalog-CatalogFeatures.Feature]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/catalog/CatalogFeatures.Feature.html
[difference-javax-xml-catalog-CatalogFeatures]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/catalog/CatalogFeatures.html
[difference-javax-xml-catalog-CatalogManager]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/catalog/CatalogManager.html
[difference-javax-xml-catalog-CatalogResolver.NotFoundAction]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/catalog/CatalogResolver.NotFoundAction.html
[difference-javax-xml-catalog-CatalogResolver]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/catalog/CatalogResolver.html
[difference-javax-xml-catalog-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/catalog/package-summary.html
[difference-javax-xml-datatype-DatatypeConfigurationException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/datatype/DatatypeConfigurationException.html
[difference-javax-xml-datatype-DatatypeConstants.Field]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/datatype/DatatypeConstants.Field.html
[difference-javax-xml-datatype-DatatypeConstants]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/datatype/DatatypeConstants.html
[difference-javax-xml-datatype-DatatypeFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/datatype/DatatypeFactory.html
[difference-javax-xml-datatype-Duration]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/datatype/Duration.html
[difference-javax-xml-datatype-XMLGregorianCalendar]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/datatype/XMLGregorianCalendar.html
[difference-javax-xml-datatype-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/datatype/package-summary.html
[difference-javax-xml-namespace-NamespaceContext]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/namespace/NamespaceContext.html
[difference-javax-xml-namespace-QName]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/namespace/QName.html
[difference-javax-xml-namespace-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/namespace/package-summary.html
[difference-javax-xml-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/package-summary.html
[difference-javax-xml-parsers-DocumentBuilder]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/parsers/DocumentBuilder.html
[difference-javax-xml-parsers-DocumentBuilderFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/parsers/DocumentBuilderFactory.html
[difference-javax-xml-parsers-FactoryConfigurationError]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/parsers/FactoryConfigurationError.html
[difference-javax-xml-parsers-ParserConfigurationException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/parsers/ParserConfigurationException.html
[difference-javax-xml-parsers-SAXParser]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/parsers/SAXParser.html
[difference-javax-xml-parsers-SAXParserFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/parsers/SAXParserFactory.html
[difference-javax-xml-parsers-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/parsers/package-summary.html
[difference-javax-xml-stream-EventFilter]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/EventFilter.html
[difference-javax-xml-stream-FactoryConfigurationError]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/FactoryConfigurationError.html
[difference-javax-xml-stream-Location]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/Location.html
[difference-javax-xml-stream-StreamFilter]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/StreamFilter.html
[difference-javax-xml-stream-XMLEventFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/XMLEventFactory.html
[difference-javax-xml-stream-XMLEventReader]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/XMLEventReader.html
[difference-javax-xml-stream-XMLEventWriter]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/XMLEventWriter.html
[difference-javax-xml-stream-XMLInputFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/XMLInputFactory.html
[difference-javax-xml-stream-XMLOutputFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/XMLOutputFactory.html
[difference-javax-xml-stream-XMLReporter]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/XMLReporter.html
[difference-javax-xml-stream-XMLResolver]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/XMLResolver.html
[difference-javax-xml-stream-XMLStreamConstants]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/XMLStreamConstants.html
[difference-javax-xml-stream-XMLStreamException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/XMLStreamException.html
[difference-javax-xml-stream-XMLStreamReader]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/XMLStreamReader.html
[difference-javax-xml-stream-XMLStreamWriter]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/XMLStreamWriter.html
[difference-javax-xml-stream-events-Attribute]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/events/Attribute.html
[difference-javax-xml-stream-events-Characters]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/events/Characters.html
[difference-javax-xml-stream-events-Comment]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/events/Comment.html
[difference-javax-xml-stream-events-DTD]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/events/DTD.html
[difference-javax-xml-stream-events-EndDocument]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/events/EndDocument.html
[difference-javax-xml-stream-events-EndElement]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/events/EndElement.html
[difference-javax-xml-stream-events-EntityDeclaration]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/events/EntityDeclaration.html
[difference-javax-xml-stream-events-EntityReference]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/events/EntityReference.html
[difference-javax-xml-stream-events-Namespace]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/events/Namespace.html
[difference-javax-xml-stream-events-NotationDeclaration]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/events/NotationDeclaration.html
[difference-javax-xml-stream-events-ProcessingInstruction]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/events/ProcessingInstruction.html
[difference-javax-xml-stream-events-StartDocument]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/events/StartDocument.html
[difference-javax-xml-stream-events-StartElement]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/events/StartElement.html
[difference-javax-xml-stream-events-XMLEvent]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/events/XMLEvent.html
[difference-javax-xml-stream-events-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/events/package-summary.html
[difference-javax-xml-stream-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/package-summary.html
[difference-javax-xml-stream-util-EventReaderDelegate]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/util/EventReaderDelegate.html
[difference-javax-xml-stream-util-StreamReaderDelegate]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/util/StreamReaderDelegate.html
[difference-javax-xml-stream-util-XMLEventAllocator]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/util/XMLEventAllocator.html
[difference-javax-xml-stream-util-XMLEventConsumer]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/util/XMLEventConsumer.html
[difference-javax-xml-stream-util-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/stream/util/package-summary.html
[difference-javax-xml-transform-ErrorListener]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/ErrorListener.html
[difference-javax-xml-transform-OutputKeys]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/OutputKeys.html
[difference-javax-xml-transform-Result]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/Result.html
[difference-javax-xml-transform-Source]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/Source.html
[difference-javax-xml-transform-SourceLocator]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/SourceLocator.html
[difference-javax-xml-transform-Templates]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/Templates.html
[difference-javax-xml-transform-Transformer]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/Transformer.html
[difference-javax-xml-transform-TransformerConfigurationException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/TransformerConfigurationException.html
[difference-javax-xml-transform-TransformerException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/TransformerException.html
[difference-javax-xml-transform-TransformerFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/TransformerFactory.html
[difference-javax-xml-transform-TransformerFactoryConfigurationError]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/TransformerFactoryConfigurationError.html
[difference-javax-xml-transform-URIResolver]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/URIResolver.html
[difference-javax-xml-transform-dom-DOMLocator]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/dom/DOMLocator.html
[difference-javax-xml-transform-dom-DOMResult]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/dom/DOMResult.html
[difference-javax-xml-transform-dom-DOMSource]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/dom/DOMSource.html
[difference-javax-xml-transform-dom-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/dom/package-summary.html
[difference-javax-xml-transform-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/package-summary.html
[difference-javax-xml-transform-sax-SAXResult]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/sax/SAXResult.html
[difference-javax-xml-transform-sax-SAXSource]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/sax/SAXSource.html
[difference-javax-xml-transform-sax-SAXTransformerFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/sax/SAXTransformerFactory.html
[difference-javax-xml-transform-sax-TemplatesHandler]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/sax/TemplatesHandler.html
[difference-javax-xml-transform-sax-TransformerHandler]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/sax/TransformerHandler.html
[difference-javax-xml-transform-sax-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/sax/package-summary.html
[difference-javax-xml-transform-stax-StAXResult]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/stax/StAXResult.html
[difference-javax-xml-transform-stax-StAXSource]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/stax/StAXSource.html
[difference-javax-xml-transform-stax-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/stax/package-summary.html
[difference-javax-xml-transform-stream-StreamResult]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/stream/StreamResult.html
[difference-javax-xml-transform-stream-StreamSource]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/stream/StreamSource.html
[difference-javax-xml-transform-stream-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/transform/stream/package-summary.html
[difference-javax-xml-validation-Schema]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/validation/Schema.html
[difference-javax-xml-validation-SchemaFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/validation/SchemaFactory.html
[difference-javax-xml-validation-SchemaFactoryConfigurationError]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/validation/SchemaFactoryConfigurationError.html
[difference-javax-xml-validation-SchemaFactoryLoader]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/validation/SchemaFactoryLoader.html
[difference-javax-xml-validation-TypeInfoProvider]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/validation/TypeInfoProvider.html
[difference-javax-xml-validation-Validator]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/validation/Validator.html
[difference-javax-xml-validation-ValidatorHandler]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/validation/ValidatorHandler.html
[difference-javax-xml-validation-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/validation/package-summary.html
[difference-javax-xml-xpath-XPath]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/xpath/XPath.html
[difference-javax-xml-xpath-XPathConstants]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/xpath/XPathConstants.html
[difference-javax-xml-xpath-XPathEvaluationResult.XPathResultType]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/xpath/XPathEvaluationResult.XPathResultType.html
[difference-javax-xml-xpath-XPathEvaluationResult]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/xpath/XPathEvaluationResult.html
[difference-javax-xml-xpath-XPathException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/xpath/XPathException.html
[difference-javax-xml-xpath-XPathExpression]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/xpath/XPathExpression.html
[difference-javax-xml-xpath-XPathExpressionException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/xpath/XPathExpressionException.html
[difference-javax-xml-xpath-XPathFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/xpath/XPathFactory.html
[difference-javax-xml-xpath-XPathFactoryConfigurationException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/xpath/XPathFactoryConfigurationException.html
[difference-javax-xml-xpath-XPathFunction]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/xpath/XPathFunction.html
[difference-javax-xml-xpath-XPathFunctionException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/xpath/XPathFunctionException.html
[difference-javax-xml-xpath-XPathFunctionResolver]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/xpath/XPathFunctionResolver.html
[difference-javax-xml-xpath-XPathNodes]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/xpath/XPathNodes.html
[difference-javax-xml-xpath-XPathVariableResolver]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/xpath/XPathVariableResolver.html
[difference-javax-xml-xpath-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/xpath/package-summary.html
[difference-module-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/module-summary.html
[unavailable-org-w3c-dom-ls-LSSerializer]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/org/w3c/dom/ls/LSSerializer.html
[unavailable-org-w3c-dom-ls-LSSerializer-origin]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/module-summary.html
[unavailable-org-xml-sax-EntityResolver]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/org/xml/sax/EntityResolver.html
[unavailable-org-xml-sax-EntityResolver-origin]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/catalog/CatalogResolver.html
[unavailable-org-xml-sax-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/org/xml/sax/package-summary.html
[unavailable-org-xml-sax-package-summary-origin]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/module-summary.html
[unavailable-org-w3c-dom-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/org/w3c/dom/package-summary.html
[unavailable-org-w3c-dom-package-summary-origin]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/module-summary.html
[unavailable-org-w3c-dom-ls-LSResourceResolver]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/org/w3c/dom/ls/LSResourceResolver.html
[unavailable-org-w3c-dom-ls-LSResourceResolver-origin]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/javax/xml/catalog/CatalogResolver.html
[unavailable-org-xml-sax-SAXException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/org/xml/sax/SAXException.html
[unavailable-org-xml-sax-SAXException-origin]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.base/java/lang/Exception.html
[unavailable-org-w3c-dom-DOMException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/org/w3c/dom/DOMException.html
[unavailable-org-w3c-dom-DOMException-origin]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.base/java/lang/RuntimeException.html
[unavailable-org-w3c-dom-ls-LSException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/org/w3c/dom/ls/LSException.html
[unavailable-org-w3c-dom-ls-LSException-origin]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.base/java/lang/RuntimeException.html
[unavailable-org-w3c-dom-events-EventException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/org/w3c/dom/events/EventException.html
[unavailable-org-w3c-dom-events-EventException-origin]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.base/java/lang/RuntimeException.html
[unavailable-org-w3c-dom-ranges-RangeException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.xml/org/w3c/dom/ranges/RangeException.html
[unavailable-org-w3c-dom-ranges-RangeException-origin]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.base/java/lang/RuntimeException.html

[map-link-1]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/index.html
