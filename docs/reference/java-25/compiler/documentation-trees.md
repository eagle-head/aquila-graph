# Documentation trees

[Source map](../README.md)

Edition: Oracle Java SE/JDK 25. This index links public types and declared members to their official API contracts.
It is navigation, not an API transcription or an implementation-coverage result.

## Types and declared members

### AttributeTree.ValueKind

[Official contract][map-link-1]

- [`EMPTY`][map-link-2]
- [`UNQUOTED`][map-link-3]
- [`SINGLE`][map-link-4]
- [`DOUBLE`][map-link-5]
- [`values()`][map-link-6]
- [`valueOf(java.lang.String)`][map-link-7]

### AttributeTree

[Official contract][map-link-8]

- [`getName()`][map-link-9]
- [`getValueKind()`][map-link-10]
- [`getValue()`][map-link-11]

### AuthorTree

[Official contract][map-link-12]

- [`getName()`][map-link-13]

### BlockTagTree

[Official contract][map-link-14]

- [`getTagName()`][map-link-15]

### CommentTree

[Official contract][map-link-16]

- [`getBody()`][map-link-17]

### DeprecatedTree

[Official contract][map-link-18]

- [`getBody()`][map-link-19]

### DocCommentTree

[Official contract][map-link-20]

- [`getFirstSentence()`][map-link-21]
- [`getFullBody()`][map-link-22]
- [`getBody()`][map-link-23]
- [`getBlockTags()`][map-link-24]
- [`getPreamble()`][map-link-25]
- [`getPostamble()`][map-link-26]

### DocRootTree

[Official contract][map-link-27]

This page declares no separately documented member detail. Its type contract and inherited surface
remain in the complete page.

### DocTree.Kind

[Official contract][map-link-28]

- [`ATTRIBUTE`][map-link-29]
- [`AUTHOR`][map-link-30]
- [`CODE`][map-link-31]
- [`COMMENT`][map-link-32]
- [`DEPRECATED`][map-link-33]
- [`DOC_COMMENT`][map-link-34]
- [`DOC_ROOT`][map-link-35]
- [`DOC_TYPE`][map-link-36]
- [`END_ELEMENT`][map-link-37]
- [`ENTITY`][map-link-38]
- [`ERRONEOUS`][map-link-39]
- [`ESCAPE`][map-link-40]
- [`EXCEPTION`][map-link-41]
- [`HIDDEN`][map-link-42]
- [`IDENTIFIER`][map-link-43]
- [`INDEX`][map-link-44]
- [`INHERIT_DOC`][map-link-45]
- [`LINK`][map-link-46]
- [`LINK_PLAIN`][map-link-47]
- [`LITERAL`][map-link-48]
- [`MARKDOWN`][map-link-49]
- [`PARAM`][map-link-50]
- [`PROVIDES`][map-link-51]
- [`REFERENCE`][map-link-52]
- [`RETURN`][map-link-53]
- [`SEE`][map-link-54]
- [`SERIAL`][map-link-55]
- [`SERIAL_DATA`][map-link-56]
- [`SERIAL_FIELD`][map-link-57]
- [`SINCE`][map-link-58]
- [`SNIPPET`][map-link-59]
- [`SPEC`][map-link-60]
- [`START_ELEMENT`][map-link-61]
- [`SYSTEM_PROPERTY`][map-link-62]
- [`SUMMARY`][map-link-63]
- [`TEXT`][map-link-64]
- [`THROWS`][map-link-65]
- [`UNKNOWN_BLOCK_TAG`][map-link-66]
- [`UNKNOWN_INLINE_TAG`][map-link-67]
- [`USES`][map-link-68]
- [`VALUE`][map-link-69]
- [`VERSION`][map-link-70]
- [`OTHER`][map-link-71]
- [`tagName`][map-link-72]
- [`values()`][map-link-73]
- [`valueOf(java.lang.String)`][map-link-74]

### DocTree

[Official contract][map-link-75]

- [`getKind()`][map-link-76]
- [`accept(com.sun.source.doctree.DocTreeVisitor,D)`][map-link-77]

### DocTreeVisitor

[Official contract][map-link-78]

-
  [`visitAttribute(com.sun.source.doctree.AttributeTree,P)`][documentation-doctreevisitor-visitattribute-com-sun-source-doctree-attributetree-p]
- [`visitAuthor(com.sun.source.doctree.AuthorTree,P)`][map-link-79]
- [`visitComment(com.sun.source.doctree.CommentTree,P)`][map-link-80]
-
  [`visitDeprecated(com.sun.source.doctree.DeprecatedTree,P)`][documentation-doctreevisitor-visitdeprecated-com-sun-source-doctree-deprecatedtree-p]
-
  [`visitDocComment(com.sun.source.doctree.DocCommentTree,P)`][documentation-doctreevisitor-visitdoccomment-com-sun-source-doctree-doccommenttree-p]
- [`visitDocRoot(com.sun.source.doctree.DocRootTree,P)`][map-link-81]
- [`visitDocType(com.sun.source.doctree.DocTypeTree,P)`][map-link-82]
-
  [`visitEndElement(com.sun.source.doctree.EndElementTree,P)`][documentation-doctreevisitor-visitendelement-com-sun-source-doctree-endelementtree-p]
- [`visitEntity(com.sun.source.doctree.EntityTree,P)`][map-link-83]
-
  [`visitErroneous(com.sun.source.doctree.ErroneousTree,P)`][documentation-doctreevisitor-visiterroneous-com-sun-source-doctree-erroneoustree-p]
- [`visitEscape(com.sun.source.doctree.EscapeTree,P)`][map-link-84]
- [`visitHidden(com.sun.source.doctree.HiddenTree,P)`][map-link-85]
-
  [`visitIdentifier(com.sun.source.doctree.IdentifierTree,P)`][documentation-doctreevisitor-visitidentifier-com-sun-source-doctree-identifiertree-p]
- [`visitIndex(com.sun.source.doctree.IndexTree,P)`][map-link-86]
-
  [`visitInheritDoc(com.sun.source.doctree.InheritDocTree,P)`][documentation-doctreevisitor-visitinheritdoc-com-sun-source-doctree-inheritdoctree-p]
- [`visitLink(com.sun.source.doctree.LinkTree,P)`][map-link-87]
- [`visitLiteral(com.sun.source.doctree.LiteralTree,P)`][map-link-88]
- [`visitParam(com.sun.source.doctree.ParamTree,P)`][map-link-89]
- [`visitProvides(com.sun.source.doctree.ProvidesTree,P)`][documentation-doctreevisitor-visitprovides-com-sun-source-doctree-providestree-p]
- [`visitRawText(com.sun.source.doctree.RawTextTree,P)`][map-link-90]
-
  [`visitReference(com.sun.source.doctree.ReferenceTree,P)`][documentation-doctreevisitor-visitreference-com-sun-source-doctree-referencetree-p]
- [`visitReturn(com.sun.source.doctree.ReturnTree,P)`][map-link-91]
- [`visitSee(com.sun.source.doctree.SeeTree,P)`][map-link-92]
- [`visitSerial(com.sun.source.doctree.SerialTree,P)`][map-link-93]
-
  [`visitSerialData(com.sun.source.doctree.SerialDataTree,P)`][documentation-doctreevisitor-visitserialdata-com-sun-source-doctree-serialdatatree-p]
-
  [`visitSerialField(com.sun.source.doctree.SerialFieldTree,P)`][documentation-doctreevisitor-visitserialfield-com-sun-source-doctree-serialfieldtree-p]
- [`visitSince(com.sun.source.doctree.SinceTree,P)`][map-link-94]
- [`visitSnippet(com.sun.source.doctree.SnippetTree,P)`][map-link-95]
- [`visitSpec(com.sun.source.doctree.SpecTree,P)`][map-link-96]
-
  [`visitStartElement(com.sun.source.doctree.StartElementTree,P)`][documentation-doctreevisitor-visitstartelement-com-sun-source-doctree-startelementtree-p]
- [`visitSummary(com.sun.source.doctree.SummaryTree,P)`][map-link-97]
-
  [`visitSystemProperty(com.sun.source.doctree.SystemPropertyTree,P)`][documentation-doctreevisitor-visitsystemproperty-com-sun-source-doctree-systempropertytree-p]
- [`visitText(com.sun.source.doctree.TextTree,P)`][map-link-98]
- [`visitThrows(com.sun.source.doctree.ThrowsTree,P)`][map-link-99]
-
  [`visitUnknownBlockTag(com.sun.source.doctree.UnknownBlockTagTree,P)`][documentation-doctreevisitor-visitunknownblocktag-com-sun-source-doctree-unknownblocktagtree-p]
-
  [`visitUnknownInlineTag(com.sun.source.doctree.UnknownInlineTagTree,P)`][documentation-doctreevisitor-visitunknowninlinetag-com-sun-source-doctree-unknowninlinetagtree-p]
- [`visitUses(com.sun.source.doctree.UsesTree,P)`][map-link-100]
- [`visitValue(com.sun.source.doctree.ValueTree,P)`][map-link-101]
- [`visitVersion(com.sun.source.doctree.VersionTree,P)`][map-link-102]
- [`visitOther(com.sun.source.doctree.DocTree,P)`][map-link-103]

### DocTypeTree

[Official contract][map-link-104]

- [`getText()`][map-link-105]

### EndElementTree

[Official contract][map-link-106]

- [`getName()`][map-link-107]

### EntityTree

[Official contract][map-link-108]

- [`getName()`][map-link-109]

### ErroneousTree

[Official contract][map-link-110]

- [`getDiagnostic()`][map-link-111]

### EscapeTree

[Official contract][map-link-112]

- [`getBody()`][map-link-113]

### HiddenTree

[Official contract][map-link-114]

- [`getBody()`][map-link-115]

### IdentifierTree

[Official contract][map-link-116]

- [`getName()`][map-link-117]

### IndexTree

[Official contract][map-link-118]

- [`getSearchTerm()`][map-link-119]
- [`getDescription()`][map-link-120]

### InheritDocTree

[Official contract][map-link-121]

- [`getSupertype()`][map-link-122]

### InlineTagTree

[Official contract][map-link-123]

- [`getTagName()`][map-link-124]

### LinkTree

[Official contract][map-link-125]

- [`getReference()`][map-link-126]
- [`getLabel()`][map-link-127]

### LiteralTree

[Official contract][map-link-128]

- [`getBody()`][map-link-129]

### ParamTree

[Official contract][map-link-130]

- [`isTypeParameter()`][map-link-131]
- [`getName()`][map-link-132]
- [`getDescription()`][map-link-133]

### ProvidesTree

[Official contract][map-link-134]

- [`getServiceType()`][map-link-135]
- [`getDescription()`][map-link-136]

### RawTextTree

[Official contract][map-link-137]

- [`getContent()`][map-link-138]

### ReferenceTree

[Official contract][map-link-139]

- [`getSignature()`][map-link-140]

### ReturnTree

[Official contract][map-link-141]

- [`isInline()`][map-link-142]
- [`getDescription()`][map-link-143]

### SeeTree

[Official contract][map-link-144]

- [`getReference()`][map-link-145]

### SerialDataTree

[Official contract][map-link-146]

- [`getDescription()`][map-link-147]

### SerialFieldTree

[Official contract][map-link-148]

- [`getName()`][map-link-149]
- [`getType()`][map-link-150]
- [`getDescription()`][map-link-151]

### SerialTree

[Official contract][map-link-152]

- [`getDescription()`][map-link-153]

### SinceTree

[Official contract][map-link-154]

- [`getBody()`][map-link-155]

### SnippetTree

[Official contract][map-link-156]

- [`getAttributes()`][map-link-157]
- [`getBody()`][map-link-158]

### SpecTree

[Official contract][map-link-159]

- [`getURL()`][map-link-160]
- [`getTitle()`][map-link-161]

### StartElementTree

[Official contract][map-link-162]

- [`getName()`][map-link-163]
- [`getAttributes()`][map-link-164]
- [`isSelfClosing()`][map-link-165]

### SummaryTree

[Official contract][map-link-166]

- [`getSummary()`][map-link-167]

### SystemPropertyTree

[Official contract][map-link-168]

- [`getPropertyName()`][map-link-169]

### TextTree

[Official contract][map-link-170]

- [`getBody()`][map-link-171]

### ThrowsTree

[Official contract][map-link-172]

- [`getExceptionName()`][map-link-173]
- [`getDescription()`][map-link-174]

### UnknownBlockTagTree

[Official contract][map-link-175]

- [`getContent()`][map-link-176]

### UnknownInlineTagTree

[Official contract][map-link-177]

- [`getContent()`][map-link-178]

### UsesTree

[Official contract][map-link-179]

- [`getServiceType()`][map-link-180]
- [`getDescription()`][map-link-181]

### ValueTree

[Official contract][map-link-182]

- [`getReference()`][map-link-183]
- [`getFormat()`][map-link-184]

### VersionTree

[Official contract][map-link-185]

- [`getBody()`][map-link-186]

## References

- [Oracle Java SE 25 package
  specification][map-link-187].

[documentation-doctreevisitor-visitattribute-com-sun-source-doctree-attributetree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitAttribute(com.sun.source.doctree.AttributeTree,P)
[documentation-doctreevisitor-visitdeprecated-com-sun-source-doctree-deprecatedtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitDeprecated(com.sun.source.doctree.DeprecatedTree,P)
[documentation-doctreevisitor-visitdoccomment-com-sun-source-doctree-doccommenttree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitDocComment(com.sun.source.doctree.DocCommentTree,P)
[documentation-doctreevisitor-visitendelement-com-sun-source-doctree-endelementtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitEndElement(com.sun.source.doctree.EndElementTree,P)
[documentation-doctreevisitor-visiterroneous-com-sun-source-doctree-erroneoustree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitErroneous(com.sun.source.doctree.ErroneousTree,P)
[documentation-doctreevisitor-visitidentifier-com-sun-source-doctree-identifiertree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitIdentifier(com.sun.source.doctree.IdentifierTree,P)
[documentation-doctreevisitor-visitinheritdoc-com-sun-source-doctree-inheritdoctree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitInheritDoc(com.sun.source.doctree.InheritDocTree,P)
[documentation-doctreevisitor-visitprovides-com-sun-source-doctree-providestree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitProvides(com.sun.source.doctree.ProvidesTree,P)
[documentation-doctreevisitor-visitreference-com-sun-source-doctree-referencetree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitReference(com.sun.source.doctree.ReferenceTree,P)
[documentation-doctreevisitor-visitserialdata-com-sun-source-doctree-serialdatatree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitSerialData(com.sun.source.doctree.SerialDataTree,P)
[documentation-doctreevisitor-visitserialfield-com-sun-source-doctree-serialfieldtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitSerialField(com.sun.source.doctree.SerialFieldTree,P)
[documentation-doctreevisitor-visitstartelement-com-sun-source-doctree-startelementtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitStartElement(com.sun.source.doctree.StartElementTree,P)
[documentation-doctreevisitor-visitsystemproperty-com-sun-source-doctree-systempropertytree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitSystemProperty(com.sun.source.doctree.SystemPropertyTree,P)
[documentation-doctreevisitor-visitunknownblocktag-com-sun-source-doctree-unknownblocktagtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitUnknownBlockTag(com.sun.source.doctree.UnknownBlockTagTree,P)
[documentation-doctreevisitor-visitunknowninlinetag-com-sun-source-doctree-unknowninlinetagtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitUnknownInlineTag(com.sun.source.doctree.UnknownInlineTagTree,P)

[map-link-1]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/AttributeTree.ValueKind.html
[map-link-2]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/AttributeTree.ValueKind.html#EMPTY
[map-link-3]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/AttributeTree.ValueKind.html#UNQUOTED
[map-link-4]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/AttributeTree.ValueKind.html#SINGLE
[map-link-5]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/AttributeTree.ValueKind.html#DOUBLE
[map-link-6]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/AttributeTree.ValueKind.html#values()
[map-link-7]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/AttributeTree.ValueKind.html#valueOf(java.lang.String)
[map-link-8]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/AttributeTree.html
[map-link-9]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/AttributeTree.html#getName()
[map-link-10]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/AttributeTree.html#getValueKind()
[map-link-11]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/AttributeTree.html#getValue()
[map-link-12]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/AuthorTree.html
[map-link-13]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/AuthorTree.html#getName()
[map-link-14]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/BlockTagTree.html
[map-link-15]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/BlockTagTree.html#getTagName()
[map-link-16]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/CommentTree.html
[map-link-17]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/CommentTree.html#getBody()
[map-link-18]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DeprecatedTree.html
[map-link-19]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DeprecatedTree.html#getBody()
[map-link-20]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocCommentTree.html
[map-link-21]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocCommentTree.html#getFirstSentence()
[map-link-22]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocCommentTree.html#getFullBody()
[map-link-23]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocCommentTree.html#getBody()
[map-link-24]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocCommentTree.html#getBlockTags()
[map-link-25]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocCommentTree.html#getPreamble()
[map-link-26]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocCommentTree.html#getPostamble()
[map-link-27]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocRootTree.html
[map-link-28]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html
[map-link-29]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#ATTRIBUTE
[map-link-30]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#AUTHOR
[map-link-31]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#CODE
[map-link-32]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#COMMENT
[map-link-33]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#DEPRECATED
[map-link-34]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#DOC_COMMENT
[map-link-35]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#DOC_ROOT
[map-link-36]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#DOC_TYPE
[map-link-37]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#END_ELEMENT
[map-link-38]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#ENTITY
[map-link-39]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#ERRONEOUS
[map-link-40]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#ESCAPE
[map-link-41]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#EXCEPTION
[map-link-42]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#HIDDEN
[map-link-43]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#IDENTIFIER
[map-link-44]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#INDEX
[map-link-45]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#INHERIT_DOC
[map-link-46]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#LINK
[map-link-47]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#LINK_PLAIN
[map-link-48]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#LITERAL
[map-link-49]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#MARKDOWN
[map-link-50]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#PARAM
[map-link-51]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#PROVIDES
[map-link-52]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#REFERENCE
[map-link-53]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#RETURN
[map-link-54]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#SEE
[map-link-55]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#SERIAL
[map-link-56]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#SERIAL_DATA
[map-link-57]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#SERIAL_FIELD
[map-link-58]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#SINCE
[map-link-59]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#SNIPPET
[map-link-60]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#SPEC
[map-link-61]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#START_ELEMENT
[map-link-62]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#SYSTEM_PROPERTY
[map-link-63]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#SUMMARY
[map-link-64]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#TEXT
[map-link-65]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#THROWS
[map-link-66]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#UNKNOWN_BLOCK_TAG
[map-link-67]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#UNKNOWN_INLINE_TAG
[map-link-68]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#USES
[map-link-69]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#VALUE
[map-link-70]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#VERSION
[map-link-71]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#OTHER
[map-link-72]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#tagName
[map-link-73]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#values()
[map-link-74]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.Kind.html#valueOf(java.lang.String)
[map-link-75]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.html
[map-link-76]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.html#getKind()
[map-link-77]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTree.html#accept(com.sun.source.doctree.DocTreeVisitor,D)
[map-link-78]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html
[map-link-79]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitAuthor(com.sun.source.doctree.AuthorTree,P)
[map-link-80]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitComment(com.sun.source.doctree.CommentTree,P)
[map-link-81]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitDocRoot(com.sun.source.doctree.DocRootTree,P)
[map-link-82]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitDocType(com.sun.source.doctree.DocTypeTree,P)
[map-link-83]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitEntity(com.sun.source.doctree.EntityTree,P)
[map-link-84]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitEscape(com.sun.source.doctree.EscapeTree,P)
[map-link-85]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitHidden(com.sun.source.doctree.HiddenTree,P)
[map-link-86]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitIndex(com.sun.source.doctree.IndexTree,P)
[map-link-87]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitLink(com.sun.source.doctree.LinkTree,P)
[map-link-88]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitLiteral(com.sun.source.doctree.LiteralTree,P)
[map-link-89]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitParam(com.sun.source.doctree.ParamTree,P)
[map-link-90]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitRawText(com.sun.source.doctree.RawTextTree,P)
[map-link-91]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitReturn(com.sun.source.doctree.ReturnTree,P)
[map-link-92]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitSee(com.sun.source.doctree.SeeTree,P)
[map-link-93]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitSerial(com.sun.source.doctree.SerialTree,P)
[map-link-94]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitSince(com.sun.source.doctree.SinceTree,P)
[map-link-95]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitSnippet(com.sun.source.doctree.SnippetTree,P)
[map-link-96]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitSpec(com.sun.source.doctree.SpecTree,P)
[map-link-97]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitSummary(com.sun.source.doctree.SummaryTree,P)
[map-link-98]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitText(com.sun.source.doctree.TextTree,P)
[map-link-99]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitThrows(com.sun.source.doctree.ThrowsTree,P)
[map-link-100]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitUses(com.sun.source.doctree.UsesTree,P)
[map-link-101]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitValue(com.sun.source.doctree.ValueTree,P)
[map-link-102]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitVersion(com.sun.source.doctree.VersionTree,P)
[map-link-103]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTreeVisitor.html#visitOther(com.sun.source.doctree.DocTree,P)
[map-link-104]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTypeTree.html
[map-link-105]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/DocTypeTree.html#getText()
[map-link-106]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/EndElementTree.html
[map-link-107]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/EndElementTree.html#getName()
[map-link-108]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/EntityTree.html
[map-link-109]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/EntityTree.html#getName()
[map-link-110]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ErroneousTree.html
[map-link-111]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ErroneousTree.html#getDiagnostic()
[map-link-112]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/EscapeTree.html
[map-link-113]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/EscapeTree.html#getBody()
[map-link-114]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/HiddenTree.html
[map-link-115]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/HiddenTree.html#getBody()
[map-link-116]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/IdentifierTree.html
[map-link-117]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/IdentifierTree.html#getName()
[map-link-118]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/IndexTree.html
[map-link-119]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/IndexTree.html#getSearchTerm()
[map-link-120]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/IndexTree.html#getDescription()
[map-link-121]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/InheritDocTree.html
[map-link-122]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/InheritDocTree.html#getSupertype()
[map-link-123]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/InlineTagTree.html
[map-link-124]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/InlineTagTree.html#getTagName()
[map-link-125]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/LinkTree.html
[map-link-126]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/LinkTree.html#getReference()
[map-link-127]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/LinkTree.html#getLabel()
[map-link-128]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/LiteralTree.html
[map-link-129]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/LiteralTree.html#getBody()
[map-link-130]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ParamTree.html
[map-link-131]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ParamTree.html#isTypeParameter()
[map-link-132]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ParamTree.html#getName()
[map-link-133]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ParamTree.html#getDescription()
[map-link-134]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ProvidesTree.html
[map-link-135]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ProvidesTree.html#getServiceType()
[map-link-136]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ProvidesTree.html#getDescription()
[map-link-137]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/RawTextTree.html
[map-link-138]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/RawTextTree.html#getContent()
[map-link-139]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ReferenceTree.html
[map-link-140]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ReferenceTree.html#getSignature()
[map-link-141]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ReturnTree.html
[map-link-142]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ReturnTree.html#isInline()
[map-link-143]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ReturnTree.html#getDescription()
[map-link-144]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SeeTree.html
[map-link-145]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SeeTree.html#getReference()
[map-link-146]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SerialDataTree.html
[map-link-147]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SerialDataTree.html#getDescription()
[map-link-148]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SerialFieldTree.html
[map-link-149]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SerialFieldTree.html#getName()
[map-link-150]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SerialFieldTree.html#getType()
[map-link-151]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SerialFieldTree.html#getDescription()
[map-link-152]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SerialTree.html
[map-link-153]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SerialTree.html#getDescription()
[map-link-154]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SinceTree.html
[map-link-155]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SinceTree.html#getBody()
[map-link-156]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SnippetTree.html
[map-link-157]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SnippetTree.html#getAttributes()
[map-link-158]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SnippetTree.html#getBody()
[map-link-159]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SpecTree.html
[map-link-160]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SpecTree.html#getURL()
[map-link-161]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SpecTree.html#getTitle()
[map-link-162]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/StartElementTree.html
[map-link-163]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/StartElementTree.html#getName()
[map-link-164]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/StartElementTree.html#getAttributes()
[map-link-165]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/StartElementTree.html#isSelfClosing()
[map-link-166]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SummaryTree.html
[map-link-167]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SummaryTree.html#getSummary()
[map-link-168]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SystemPropertyTree.html
[map-link-169]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/SystemPropertyTree.html#getPropertyName()
[map-link-170]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/TextTree.html
[map-link-171]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/TextTree.html#getBody()
[map-link-172]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ThrowsTree.html
[map-link-173]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ThrowsTree.html#getExceptionName()
[map-link-174]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ThrowsTree.html#getDescription()
[map-link-175]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/UnknownBlockTagTree.html
[map-link-176]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/UnknownBlockTagTree.html#getContent()
[map-link-177]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/UnknownInlineTagTree.html
[map-link-178]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/UnknownInlineTagTree.html#getContent()
[map-link-179]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/UsesTree.html
[map-link-180]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/UsesTree.html#getServiceType()
[map-link-181]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/UsesTree.html#getDescription()
[map-link-182]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ValueTree.html
[map-link-183]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ValueTree.html#getReference()
[map-link-184]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/ValueTree.html#getFormat()
[map-link-185]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/VersionTree.html
[map-link-186]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/VersionTree.html#getBody()
[map-link-187]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/package-summary.html
