# Compiler tree utilities

[Source map](../README.md)

Edition: Oracle Java SE/JDK 25. This index links public types and declared members to their official API contracts.
It is navigation, not an API transcription or an implementation-coverage result.

## Types and declared members

### DocSourcePositions

[Official contract][map-link-1]

-
  [`getStartPosition(com.sun.source.tree.CompilationUnitTree,com.sun.source.doctree.DocCommentTree,com.sun.source.doctree.DocTree)`][utilities-docsourcepositions-getstartposition-com-sun-source-tree-compilationunittree-com-sun-source-doctree-doccommenttree-com-sun-source-doctree-doctree]
-
  [`getEndPosition(com.sun.source.tree.CompilationUnitTree,com.sun.source.doctree.DocCommentTree,com.sun.source.doctree.DocTree)`][utilities-docsourcepositions-getendposition-com-sun-source-tree-compilationunittree-com-sun-source-doctree-doccommenttree-com-sun-source-doctree-doctree]

### DocTreeFactory

[Official contract][map-link-2]

-
  [`newAttributeTree(javax.lang.model.element.Name,com.sun.source.doctree.AttributeTree.ValueKind,java.util.List)`][utilities-doctreefactory-newattributetree-javax-lang-model-element-name-com-sun-source-doctree-attributetree-valuekind-java-util-list]
- [`newAuthorTree(java.util.List)`][map-link-3]
- [`newCodeTree(com.sun.source.doctree.TextTree)`][map-link-4]
- [`newCommentTree(java.lang.String)`][map-link-5]
- [`newDeprecatedTree(java.util.List)`][map-link-6]
- [`newDocCommentTree(java.util.List,java.util.List)`][map-link-7]
-
  [`newDocCommentTree(java.util.List,java.util.List,java.util.List,java.util.List)`][utilities-doctreefactory-newdoccommenttree-java-util-list-java-util-list-java-util-list-java-util-list]
- [`newDocRootTree()`][map-link-8]
- [`newDocTypeTree(java.lang.String)`][map-link-9]
- [`newEndElementTree(javax.lang.model.element.Name)`][map-link-10]
- [`newEntityTree(javax.lang.model.element.Name)`][map-link-11]
-
  [`newErroneousTree(java.lang.String,javax.tools.Diagnostic)`][utilities-doctreefactory-newerroneoustree-java-lang-string-javax-tools-diagnostic]
- [`newEscapeTree(char)`][map-link-12]
-
  [`newExceptionTree(com.sun.source.doctree.ReferenceTree,java.util.List)`][utilities-doctreefactory-newexceptiontree-com-sun-source-doctree-referencetree-java-util-list]
- [`newHiddenTree(java.util.List)`][map-link-13]
- [`newIdentifierTree(javax.lang.model.element.Name)`][map-link-14]
-
  [`newIndexTree(com.sun.source.doctree.DocTree,java.util.List)`][utilities-doctreefactory-newindextree-com-sun-source-doctree-doctree-java-util-list]
- [`newInheritDocTree()`][map-link-15]
-
  [`newInheritDocTree(com.sun.source.doctree.ReferenceTree)`][utilities-doctreefactory-newinheritdoctree-com-sun-source-doctree-referencetree]
-
  [`newLinkTree(com.sun.source.doctree.ReferenceTree,java.util.List)`][utilities-doctreefactory-newlinktree-com-sun-source-doctree-referencetree-java-util-list]
-
  [`newLinkPlainTree(com.sun.source.doctree.ReferenceTree,java.util.List)`][utilities-doctreefactory-newlinkplaintree-com-sun-source-doctree-referencetree-java-util-list]
- [`newLiteralTree(com.sun.source.doctree.TextTree)`][map-link-16]
-
  [`newParamTree(boolean,com.sun.source.doctree.IdentifierTree,java.util.List)`][utilities-doctreefactory-newparamtree-boolean-com-sun-source-doctree-identifiertree-java-util-list]
-
  [`newProvidesTree(com.sun.source.doctree.ReferenceTree,java.util.List)`][utilities-doctreefactory-newprovidestree-com-sun-source-doctree-referencetree-java-util-list]
-
  [`newRawTextTree(com.sun.source.doctree.DocTree.Kind,java.lang.String)`][utilities-doctreefactory-newrawtexttree-com-sun-source-doctree-doctree-kind-java-lang-string]
- [`newReferenceTree(java.lang.String)`][map-link-17]
- [`newReturnTree(java.util.List)`][map-link-18]
- [`newReturnTree(boolean,java.util.List)`][map-link-19]
- [`newSeeTree(java.util.List)`][map-link-20]
- [`newSerialTree(java.util.List)`][map-link-21]
- [`newSerialDataTree(java.util.List)`][map-link-22]
-
  [`newSerialFieldTree(com.sun.source.doctree.IdentifierTree,com.sun.source.doctree.ReferenceTree,java.util.List)`][utilities-doctreefactory-newserialfieldtree-com-sun-source-doctree-identifiertree-com-sun-source-doctree-referencetree-java-util-list]
- [`newSinceTree(java.util.List)`][map-link-23]
-
  [`newSnippetTree(java.util.List,com.sun.source.doctree.TextTree)`][utilities-doctreefactory-newsnippettree-java-util-list-com-sun-source-doctree-texttree]
-
  [`newSpecTree(com.sun.source.doctree.TextTree,java.util.List)`][utilities-doctreefactory-newspectree-com-sun-source-doctree-texttree-java-util-list]
-
  [`newStartElementTree(javax.lang.model.element.Name,java.util.List,boolean)`][utilities-doctreefactory-newstartelementtree-javax-lang-model-element-name-java-util-list-boolean]
- [`newSummaryTree(java.util.List)`][map-link-24]
- [`newSystemPropertyTree(javax.lang.model.element.Name)`][map-link-25]
- [`newTextTree(java.lang.String)`][map-link-26]
-
  [`newThrowsTree(com.sun.source.doctree.ReferenceTree,java.util.List)`][utilities-doctreefactory-newthrowstree-com-sun-source-doctree-referencetree-java-util-list]
-
  [`newUnknownBlockTagTree(javax.lang.model.element.Name,java.util.List)`][utilities-doctreefactory-newunknownblocktagtree-javax-lang-model-element-name-java-util-list]
-
  [`newUnknownInlineTagTree(javax.lang.model.element.Name,java.util.List)`][utilities-doctreefactory-newunknowninlinetagtree-javax-lang-model-element-name-java-util-list]
-
  [`newUsesTree(com.sun.source.doctree.ReferenceTree,java.util.List)`][utilities-doctreefactory-newusestree-com-sun-source-doctree-referencetree-java-util-list]
- [`newValueTree(com.sun.source.doctree.ReferenceTree)`][map-link-27]
-
  [`newValueTree(com.sun.source.doctree.TextTree,com.sun.source.doctree.ReferenceTree)`][utilities-doctreefactory-newvaluetree-com-sun-source-doctree-texttree-com-sun-source-doctree-referencetree]
- [`newVersionTree(java.util.List)`][map-link-28]
- [`at(int)`][map-link-29]
- [`getFirstSentence(java.util.List)`][map-link-30]

### DocTreePath

[Official contract][map-link-31]

-
  [`<init>(com.sun.source.util.TreePath,com.sun.source.doctree.DocCommentTree)`][utilities-doctreepath-init-com-sun-source-util-treepath-com-sun-source-doctree-doccommenttree]
-
  [`<init>(com.sun.source.util.DocTreePath,com.sun.source.doctree.DocTree)`][utilities-doctreepath-init-com-sun-source-util-doctreepath-com-sun-source-doctree-doctree]
-
  [`getPath(com.sun.source.util.TreePath,com.sun.source.doctree.DocCommentTree,com.sun.source.doctree.DocTree)`][utilities-doctreepath-getpath-com-sun-source-util-treepath-com-sun-source-doctree-doccommenttree-com-sun-source-doctree-doctree]
-
  [`getPath(com.sun.source.util.DocTreePath,com.sun.source.doctree.DocTree)`][utilities-doctreepath-getpath-com-sun-source-util-doctreepath-com-sun-source-doctree-doctree]
- [`getTreePath()`][map-link-32]
- [`getDocComment()`][map-link-33]
- [`getLeaf()`][map-link-34]
- [`getParentPath()`][map-link-35]
- [`iterator()`][map-link-36]

### DocTreePathScanner

[Official contract][map-link-37]

- [`<init>()`][map-link-38]
- [`scan(com.sun.source.util.DocTreePath,P)`][map-link-39]
- [`scan(com.sun.source.doctree.DocTree,P)`][map-link-40]
- [`getCurrentPath()`][map-link-41]

### DocTreeScanner

[Official contract][map-link-42]

- [`<init>()`][map-link-43]
- [`scan(com.sun.source.doctree.DocTree,P)`][map-link-44]
- [`scan(java.lang.Iterable,P)`][map-link-45]
- [`reduce(R,R)`][map-link-46]
- [`visitAttribute(com.sun.source.doctree.AttributeTree,P)`][utilities-doctreescanner-visitattribute-com-sun-source-doctree-attributetree-p]
- [`visitAuthor(com.sun.source.doctree.AuthorTree,P)`][map-link-47]
- [`visitComment(com.sun.source.doctree.CommentTree,P)`][map-link-48]
-
  [`visitDeprecated(com.sun.source.doctree.DeprecatedTree,P)`][utilities-doctreescanner-visitdeprecated-com-sun-source-doctree-deprecatedtree-p]
-
  [`visitDocComment(com.sun.source.doctree.DocCommentTree,P)`][utilities-doctreescanner-visitdoccomment-com-sun-source-doctree-doccommenttree-p]
- [`visitDocRoot(com.sun.source.doctree.DocRootTree,P)`][map-link-49]
- [`visitDocType(com.sun.source.doctree.DocTypeTree,P)`][map-link-50]
-
  [`visitEndElement(com.sun.source.doctree.EndElementTree,P)`][utilities-doctreescanner-visitendelement-com-sun-source-doctree-endelementtree-p]
- [`visitEntity(com.sun.source.doctree.EntityTree,P)`][map-link-51]
- [`visitErroneous(com.sun.source.doctree.ErroneousTree,P)`][utilities-doctreescanner-visiterroneous-com-sun-source-doctree-erroneoustree-p]
- [`visitEscape(com.sun.source.doctree.EscapeTree,P)`][map-link-52]
- [`visitHidden(com.sun.source.doctree.HiddenTree,P)`][map-link-53]
-
  [`visitIdentifier(com.sun.source.doctree.IdentifierTree,P)`][utilities-doctreescanner-visitidentifier-com-sun-source-doctree-identifiertree-p]
- [`visitIndex(com.sun.source.doctree.IndexTree,P)`][map-link-54]
-
  [`visitInheritDoc(com.sun.source.doctree.InheritDocTree,P)`][utilities-doctreescanner-visitinheritdoc-com-sun-source-doctree-inheritdoctree-p]
- [`visitLink(com.sun.source.doctree.LinkTree,P)`][map-link-55]
- [`visitLiteral(com.sun.source.doctree.LiteralTree,P)`][map-link-56]
- [`visitParam(com.sun.source.doctree.ParamTree,P)`][map-link-57]
- [`visitProvides(com.sun.source.doctree.ProvidesTree,P)`][map-link-58]
- [`visitRawText(com.sun.source.doctree.RawTextTree,P)`][map-link-59]
- [`visitReference(com.sun.source.doctree.ReferenceTree,P)`][utilities-doctreescanner-visitreference-com-sun-source-doctree-referencetree-p]
- [`visitReturn(com.sun.source.doctree.ReturnTree,P)`][map-link-60]
- [`visitSee(com.sun.source.doctree.SeeTree,P)`][map-link-61]
- [`visitSerial(com.sun.source.doctree.SerialTree,P)`][map-link-62]
-
  [`visitSerialData(com.sun.source.doctree.SerialDataTree,P)`][utilities-doctreescanner-visitserialdata-com-sun-source-doctree-serialdatatree-p]
-
  [`visitSerialField(com.sun.source.doctree.SerialFieldTree,P)`][utilities-doctreescanner-visitserialfield-com-sun-source-doctree-serialfieldtree-p]
- [`visitSince(com.sun.source.doctree.SinceTree,P)`][map-link-63]
- [`visitSnippet(com.sun.source.doctree.SnippetTree,P)`][map-link-64]
- [`visitSpec(com.sun.source.doctree.SpecTree,P)`][map-link-65]
-
  [`visitStartElement(com.sun.source.doctree.StartElementTree,P)`][utilities-doctreescanner-visitstartelement-com-sun-source-doctree-startelementtree-p]
- [`visitSummary(com.sun.source.doctree.SummaryTree,P)`][map-link-66]
-
  [`visitSystemProperty(com.sun.source.doctree.SystemPropertyTree,P)`][utilities-doctreescanner-visitsystemproperty-com-sun-source-doctree-systempropertytree-p]
- [`visitText(com.sun.source.doctree.TextTree,P)`][map-link-67]
- [`visitThrows(com.sun.source.doctree.ThrowsTree,P)`][map-link-68]
-
  [`visitUnknownBlockTag(com.sun.source.doctree.UnknownBlockTagTree,P)`][utilities-doctreescanner-visitunknownblocktag-com-sun-source-doctree-unknownblocktagtree-p]
-
  [`visitUnknownInlineTag(com.sun.source.doctree.UnknownInlineTagTree,P)`][utilities-doctreescanner-visitunknowninlinetag-com-sun-source-doctree-unknowninlinetagtree-p]
- [`visitUses(com.sun.source.doctree.UsesTree,P)`][map-link-69]
- [`visitValue(com.sun.source.doctree.ValueTree,P)`][map-link-70]
- [`visitVersion(com.sun.source.doctree.VersionTree,P)`][map-link-71]
- [`visitOther(com.sun.source.doctree.DocTree,P)`][map-link-72]

### DocTrees

[Official contract][map-link-73]

- [`<init>()`][map-link-74]
- [`instance(javax.tools.JavaCompiler.CompilationTask)`][map-link-75]
-
  [`instance(javax.annotation.processing.ProcessingEnvironment)`][utilities-doctrees-instance-javax-annotation-processing-processingenvironment]
- [`getBreakIterator()`][map-link-76]
- [`getDocCommentKind(com.sun.source.util.TreePath)`][map-link-77]
- [`getDocCommentTree(com.sun.source.util.TreePath)`][map-link-78]
- [`getDocCommentTree(javax.lang.model.element.Element)`][map-link-79]
- [`getDocCommentTree(javax.tools.FileObject)`][map-link-80]
-
  [`getDocCommentTree(javax.lang.model.element.Element,java.lang.String)`][utilities-doctrees-getdoccommenttree-javax-lang-model-element-element-java-lang-string]
-
  [`getDocTreePath(javax.tools.FileObject,javax.lang.model.element.PackageElement)`][utilities-doctrees-getdoctreepath-javax-tools-fileobject-javax-lang-model-element-packageelement]
- [`getElement(com.sun.source.util.DocTreePath)`][map-link-81]
- [`getType(com.sun.source.util.DocTreePath)`][map-link-82]
- [`getFirstSentence(java.util.List)`][map-link-83]
- [`getSourcePositions()`][map-link-84]
-
  [`printMessage(javax.tools.Diagnostic.Kind,java.lang.CharSequence,com.sun.source.doctree.DocTree,com.sun.source.doctree.DocCommentTree,com.sun.source.tree.CompilationUnitTree)`][utilities-doctrees-printmessage-javax-tools-diagnostic-kind-java-lang-charsequence-com-sun-source-doctree-doctree-com-sun-source-doctree-doccommenttree-com-sun-source-tree-compilationunittree]
- [`setBreakIterator(java.text.BreakIterator)`][map-link-85]
- [`getDocTreeFactory()`][map-link-86]
- [`getCharacters(com.sun.source.doctree.EntityTree)`][map-link-87]

### JavacTask

[Official contract][map-link-88]

- [`<init>()`][map-link-89]
-
  [`instance(javax.annotation.processing.ProcessingEnvironment)`][utilities-javactask-instance-javax-annotation-processing-processingenvironment]
- [`parse()`][map-link-90]
- [`analyze()`][map-link-91]
- [`generate()`][map-link-92]
- [`setTaskListener(com.sun.source.util.TaskListener)`][map-link-93]
- [`addTaskListener(com.sun.source.util.TaskListener)`][map-link-94]
- [`removeTaskListener(com.sun.source.util.TaskListener)`][map-link-95]
-
  [`setParameterNameProvider(com.sun.source.util.ParameterNameProvider)`][utilities-javactask-setparameternameprovider-com-sun-source-util-parameternameprovider]
- [`getTypeMirror(java.lang.Iterable)`][map-link-96]
- [`getElements()`][map-link-97]
- [`getTypes()`][map-link-98]

### ParameterNameProvider

[Official contract][map-link-99]

-
  [`getParameterName(javax.lang.model.element.VariableElement)`][utilities-parameternameprovider-getparametername-javax-lang-model-element-variableelement]

### Plugin

[Official contract][map-link-100]

- [`getName()`][map-link-101]
- [`init(com.sun.source.util.JavacTask,java.lang.String...)`][map-link-102]
- [`autoStart()`][map-link-103]

### SimpleDocTreeVisitor

[Official contract][map-link-104]

- [`DEFAULT_VALUE`][map-link-105]
- [`<init>()`][map-link-106]
- [`<init>(R)`][map-link-107]
- [`defaultAction(com.sun.source.doctree.DocTree,P)`][map-link-108]
- [`visit(com.sun.source.doctree.DocTree,P)`][map-link-109]
- [`visit(java.lang.Iterable,P)`][map-link-110]
-
  [`visitAttribute(com.sun.source.doctree.AttributeTree,P)`][utilities-simpledoctreevisitor-visitattribute-com-sun-source-doctree-attributetree-p]
- [`visitAuthor(com.sun.source.doctree.AuthorTree,P)`][map-link-111]
- [`visitComment(com.sun.source.doctree.CommentTree,P)`][utilities-simpledoctreevisitor-visitcomment-com-sun-source-doctree-commenttree-p]
-
  [`visitDeprecated(com.sun.source.doctree.DeprecatedTree,P)`][utilities-simpledoctreevisitor-visitdeprecated-com-sun-source-doctree-deprecatedtree-p]
-
  [`visitDocComment(com.sun.source.doctree.DocCommentTree,P)`][utilities-simpledoctreevisitor-visitdoccomment-com-sun-source-doctree-doccommenttree-p]
- [`visitDocRoot(com.sun.source.doctree.DocRootTree,P)`][utilities-simpledoctreevisitor-visitdocroot-com-sun-source-doctree-docroottree-p]
- [`visitDocType(com.sun.source.doctree.DocTypeTree,P)`][utilities-simpledoctreevisitor-visitdoctype-com-sun-source-doctree-doctypetree-p]
-
  [`visitEndElement(com.sun.source.doctree.EndElementTree,P)`][utilities-simpledoctreevisitor-visitendelement-com-sun-source-doctree-endelementtree-p]
- [`visitEntity(com.sun.source.doctree.EntityTree,P)`][map-link-112]
-
  [`visitErroneous(com.sun.source.doctree.ErroneousTree,P)`][utilities-simpledoctreevisitor-visiterroneous-com-sun-source-doctree-erroneoustree-p]
- [`visitEscape(com.sun.source.doctree.EscapeTree,P)`][map-link-113]
- [`visitHidden(com.sun.source.doctree.HiddenTree,P)`][map-link-114]
-
  [`visitIdentifier(com.sun.source.doctree.IdentifierTree,P)`][utilities-simpledoctreevisitor-visitidentifier-com-sun-source-doctree-identifiertree-p]
- [`visitIndex(com.sun.source.doctree.IndexTree,P)`][map-link-115]
-
  [`visitInheritDoc(com.sun.source.doctree.InheritDocTree,P)`][utilities-simpledoctreevisitor-visitinheritdoc-com-sun-source-doctree-inheritdoctree-p]
- [`visitLink(com.sun.source.doctree.LinkTree,P)`][map-link-116]
- [`visitLiteral(com.sun.source.doctree.LiteralTree,P)`][utilities-simpledoctreevisitor-visitliteral-com-sun-source-doctree-literaltree-p]
- [`visitParam(com.sun.source.doctree.ParamTree,P)`][map-link-117]
-
  [`visitProvides(com.sun.source.doctree.ProvidesTree,P)`][utilities-simpledoctreevisitor-visitprovides-com-sun-source-doctree-providestree-p]
- [`visitRawText(com.sun.source.doctree.RawTextTree,P)`][utilities-simpledoctreevisitor-visitrawtext-com-sun-source-doctree-rawtexttree-p]
-
  [`visitReference(com.sun.source.doctree.ReferenceTree,P)`][utilities-simpledoctreevisitor-visitreference-com-sun-source-doctree-referencetree-p]
- [`visitReturn(com.sun.source.doctree.ReturnTree,P)`][map-link-118]
- [`visitSee(com.sun.source.doctree.SeeTree,P)`][map-link-119]
- [`visitSerial(com.sun.source.doctree.SerialTree,P)`][map-link-120]
-
  [`visitSerialData(com.sun.source.doctree.SerialDataTree,P)`][utilities-simpledoctreevisitor-visitserialdata-com-sun-source-doctree-serialdatatree-p]
-
  [`visitSerialField(com.sun.source.doctree.SerialFieldTree,P)`][utilities-simpledoctreevisitor-visitserialfield-com-sun-source-doctree-serialfieldtree-p]
- [`visitSince(com.sun.source.doctree.SinceTree,P)`][map-link-121]
- [`visitSnippet(com.sun.source.doctree.SnippetTree,P)`][utilities-simpledoctreevisitor-visitsnippet-com-sun-source-doctree-snippettree-p]
- [`visitSpec(com.sun.source.doctree.SpecTree,P)`][map-link-122]
-
  [`visitStartElement(com.sun.source.doctree.StartElementTree,P)`][utilities-simpledoctreevisitor-visitstartelement-com-sun-source-doctree-startelementtree-p]
- [`visitSummary(com.sun.source.doctree.SummaryTree,P)`][utilities-simpledoctreevisitor-visitsummary-com-sun-source-doctree-summarytree-p]
-
  [`visitSystemProperty(com.sun.source.doctree.SystemPropertyTree,P)`][utilities-simpledoctreevisitor-visitsystemproperty-com-sun-source-doctree-systempropertytree-p]
- [`visitText(com.sun.source.doctree.TextTree,P)`][map-link-123]
- [`visitThrows(com.sun.source.doctree.ThrowsTree,P)`][map-link-124]
-
  [`visitUnknownBlockTag(com.sun.source.doctree.UnknownBlockTagTree,P)`][utilities-simpledoctreevisitor-visitunknownblocktag-com-sun-source-doctree-unknownblocktagtree-p]
-
  [`visitUnknownInlineTag(com.sun.source.doctree.UnknownInlineTagTree,P)`][utilities-simpledoctreevisitor-visitunknowninlinetag-com-sun-source-doctree-unknowninlinetagtree-p]
- [`visitUses(com.sun.source.doctree.UsesTree,P)`][map-link-125]
- [`visitValue(com.sun.source.doctree.ValueTree,P)`][map-link-126]
- [`visitVersion(com.sun.source.doctree.VersionTree,P)`][utilities-simpledoctreevisitor-visitversion-com-sun-source-doctree-versiontree-p]
- [`visitOther(com.sun.source.doctree.DocTree,P)`][map-link-127]

### SimpleTreeVisitor

[Official contract][map-link-128]

- [`DEFAULT_VALUE`][map-link-129]
- [`<init>()`][map-link-130]
- [`<init>(R)`][map-link-131]
- [`defaultAction(com.sun.source.tree.Tree,P)`][map-link-132]
- [`visit(com.sun.source.tree.Tree,P)`][map-link-133]
- [`visit(java.lang.Iterable,P)`][map-link-134]
-
  [`visitCompilationUnit(com.sun.source.tree.CompilationUnitTree,P)`][utilities-simpletreevisitor-visitcompilationunit-com-sun-source-tree-compilationunittree-p]
- [`visitPackage(com.sun.source.tree.PackageTree,P)`][map-link-135]
- [`visitImport(com.sun.source.tree.ImportTree,P)`][map-link-136]
- [`visitClass(com.sun.source.tree.ClassTree,P)`][map-link-137]
- [`visitMethod(com.sun.source.tree.MethodTree,P)`][map-link-138]
- [`visitVariable(com.sun.source.tree.VariableTree,P)`][map-link-139]
-
  [`visitEmptyStatement(com.sun.source.tree.EmptyStatementTree,P)`][utilities-simpletreevisitor-visitemptystatement-com-sun-source-tree-emptystatementtree-p]
- [`visitBlock(com.sun.source.tree.BlockTree,P)`][map-link-140]
-
  [`visitDoWhileLoop(com.sun.source.tree.DoWhileLoopTree,P)`][utilities-simpletreevisitor-visitdowhileloop-com-sun-source-tree-dowhilelooptree-p]
- [`visitWhileLoop(com.sun.source.tree.WhileLoopTree,P)`][utilities-simpletreevisitor-visitwhileloop-com-sun-source-tree-whilelooptree-p]
- [`visitForLoop(com.sun.source.tree.ForLoopTree,P)`][map-link-141]
-
  [`visitEnhancedForLoop(com.sun.source.tree.EnhancedForLoopTree,P)`][utilities-simpletreevisitor-visitenhancedforloop-com-sun-source-tree-enhancedforlooptree-p]
-
  [`visitLabeledStatement(com.sun.source.tree.LabeledStatementTree,P)`][utilities-simpletreevisitor-visitlabeledstatement-com-sun-source-tree-labeledstatementtree-p]
- [`visitSwitch(com.sun.source.tree.SwitchTree,P)`][map-link-142]
-
  [`visitSwitchExpression(com.sun.source.tree.SwitchExpressionTree,P)`][utilities-simpletreevisitor-visitswitchexpression-com-sun-source-tree-switchexpressiontree-p]
- [`visitCase(com.sun.source.tree.CaseTree,P)`][map-link-143]
-
  [`visitSynchronized(com.sun.source.tree.SynchronizedTree,P)`][utilities-simpletreevisitor-visitsynchronized-com-sun-source-tree-synchronizedtree-p]
- [`visitTry(com.sun.source.tree.TryTree,P)`][map-link-144]
- [`visitCatch(com.sun.source.tree.CatchTree,P)`][map-link-145]
-
  [`visitConditionalExpression(com.sun.source.tree.ConditionalExpressionTree,P)`][utilities-simpletreevisitor-visitconditionalexpression-com-sun-source-tree-conditionalexpressiontree-p]
- [`visitIf(com.sun.source.tree.IfTree,P)`][map-link-146]
-
  [`visitExpressionStatement(com.sun.source.tree.ExpressionStatementTree,P)`][utilities-simpletreevisitor-visitexpressionstatement-com-sun-source-tree-expressionstatementtree-p]
- [`visitBreak(com.sun.source.tree.BreakTree,P)`][map-link-147]
- [`visitContinue(com.sun.source.tree.ContinueTree,P)`][map-link-148]
- [`visitReturn(com.sun.source.tree.ReturnTree,P)`][map-link-149]
- [`visitThrow(com.sun.source.tree.ThrowTree,P)`][map-link-150]
- [`visitAssert(com.sun.source.tree.AssertTree,P)`][map-link-151]
-
  [`visitMethodInvocation(com.sun.source.tree.MethodInvocationTree,P)`][utilities-simpletreevisitor-visitmethodinvocation-com-sun-source-tree-methodinvocationtree-p]
- [`visitNewClass(com.sun.source.tree.NewClassTree,P)`][map-link-152]
- [`visitNewArray(com.sun.source.tree.NewArrayTree,P)`][map-link-153]
-
  [`visitLambdaExpression(com.sun.source.tree.LambdaExpressionTree,P)`][utilities-simpletreevisitor-visitlambdaexpression-com-sun-source-tree-lambdaexpressiontree-p]
-
  [`visitParenthesized(com.sun.source.tree.ParenthesizedTree,P)`][utilities-simpletreevisitor-visitparenthesized-com-sun-source-tree-parenthesizedtree-p]
-
  [`visitAssignment(com.sun.source.tree.AssignmentTree,P)`][utilities-simpletreevisitor-visitassignment-com-sun-source-tree-assignmenttree-p]
-
  [`visitCompoundAssignment(com.sun.source.tree.CompoundAssignmentTree,P)`][utilities-simpletreevisitor-visitcompoundassignment-com-sun-source-tree-compoundassignmenttree-p]
- [`visitUnary(com.sun.source.tree.UnaryTree,P)`][map-link-154]
- [`visitBinary(com.sun.source.tree.BinaryTree,P)`][map-link-155]
- [`visitTypeCast(com.sun.source.tree.TypeCastTree,P)`][map-link-156]
-
  [`visitInstanceOf(com.sun.source.tree.InstanceOfTree,P)`][utilities-simpletreevisitor-visitinstanceof-com-sun-source-tree-instanceoftree-p]
-
  [`visitAnyPattern(com.sun.source.tree.AnyPatternTree,P)`][utilities-simpletreevisitor-visitanypattern-com-sun-source-tree-anypatterntree-p]
-
  [`visitBindingPattern(com.sun.source.tree.BindingPatternTree,P)`][utilities-simpletreevisitor-visitbindingpattern-com-sun-source-tree-bindingpatterntree-p]
-
  [`visitDefaultCaseLabel(com.sun.source.tree.DefaultCaseLabelTree,P)`][utilities-simpletreevisitor-visitdefaultcaselabel-com-sun-source-tree-defaultcaselabeltree-p]
-
  [`visitConstantCaseLabel(com.sun.source.tree.ConstantCaseLabelTree,P)`][utilities-simpletreevisitor-visitconstantcaselabel-com-sun-source-tree-constantcaselabeltree-p]
-
  [`visitDeconstructionPattern(com.sun.source.tree.DeconstructionPatternTree,P)`][utilities-simpletreevisitor-visitdeconstructionpattern-com-sun-source-tree-deconstructionpatterntree-p]
-
  [`visitPatternCaseLabel(com.sun.source.tree.PatternCaseLabelTree,P)`][utilities-simpletreevisitor-visitpatterncaselabel-com-sun-source-tree-patterncaselabeltree-p]
-
  [`visitArrayAccess(com.sun.source.tree.ArrayAccessTree,P)`][utilities-simpletreevisitor-visitarrayaccess-com-sun-source-tree-arrayaccesstree-p]
-
  [`visitMemberSelect(com.sun.source.tree.MemberSelectTree,P)`][utilities-simpletreevisitor-visitmemberselect-com-sun-source-tree-memberselecttree-p]
-
  [`visitMemberReference(com.sun.source.tree.MemberReferenceTree,P)`][utilities-simpletreevisitor-visitmemberreference-com-sun-source-tree-memberreferencetree-p]
-
  [`visitIdentifier(com.sun.source.tree.IdentifierTree,P)`][utilities-simpletreevisitor-visitidentifier-com-sun-source-tree-identifiertree-p]
- [`visitLiteral(com.sun.source.tree.LiteralTree,P)`][map-link-157]
-
  [`visitPrimitiveType(com.sun.source.tree.PrimitiveTypeTree,P)`][utilities-simpletreevisitor-visitprimitivetype-com-sun-source-tree-primitivetypetree-p]
- [`visitArrayType(com.sun.source.tree.ArrayTypeTree,P)`][utilities-simpletreevisitor-visitarraytype-com-sun-source-tree-arraytypetree-p]
-
  [`visitParameterizedType(com.sun.source.tree.ParameterizedTypeTree,P)`][utilities-simpletreevisitor-visitparameterizedtype-com-sun-source-tree-parameterizedtypetree-p]
- [`visitUnionType(com.sun.source.tree.UnionTypeTree,P)`][utilities-simpletreevisitor-visituniontype-com-sun-source-tree-uniontypetree-p]
-
  [`visitIntersectionType(com.sun.source.tree.IntersectionTypeTree,P)`][utilities-simpletreevisitor-visitintersectiontype-com-sun-source-tree-intersectiontypetree-p]
-
  [`visitTypeParameter(com.sun.source.tree.TypeParameterTree,P)`][utilities-simpletreevisitor-visittypeparameter-com-sun-source-tree-typeparametertree-p]
- [`visitWildcard(com.sun.source.tree.WildcardTree,P)`][map-link-158]
- [`visitModifiers(com.sun.source.tree.ModifiersTree,P)`][utilities-simpletreevisitor-visitmodifiers-com-sun-source-tree-modifierstree-p]
-
  [`visitAnnotation(com.sun.source.tree.AnnotationTree,P)`][utilities-simpletreevisitor-visitannotation-com-sun-source-tree-annotationtree-p]
-
  [`visitAnnotatedType(com.sun.source.tree.AnnotatedTypeTree,P)`][utilities-simpletreevisitor-visitannotatedtype-com-sun-source-tree-annotatedtypetree-p]
- [`visitModule(com.sun.source.tree.ModuleTree,P)`][map-link-159]
- [`visitExports(com.sun.source.tree.ExportsTree,P)`][map-link-160]
- [`visitOpens(com.sun.source.tree.OpensTree,P)`][map-link-161]
- [`visitProvides(com.sun.source.tree.ProvidesTree,P)`][map-link-162]
- [`visitRequires(com.sun.source.tree.RequiresTree,P)`][map-link-163]
- [`visitUses(com.sun.source.tree.UsesTree,P)`][map-link-164]
- [`visitErroneous(com.sun.source.tree.ErroneousTree,P)`][utilities-simpletreevisitor-visiterroneous-com-sun-source-tree-erroneoustree-p]
- [`visitOther(com.sun.source.tree.Tree,P)`][map-link-165]
- [`visitYield(com.sun.source.tree.YieldTree,P)`][map-link-166]

### SourcePositions

[Official contract][map-link-167]

-
  [`getStartPosition(com.sun.source.tree.CompilationUnitTree,com.sun.source.tree.Tree)`][utilities-sourcepositions-getstartposition-com-sun-source-tree-compilationunittree-com-sun-source-tree-tree]
-
  [`getEndPosition(com.sun.source.tree.CompilationUnitTree,com.sun.source.tree.Tree)`][utilities-sourcepositions-getendposition-com-sun-source-tree-compilationunittree-com-sun-source-tree-tree]

### TaskEvent.Kind

[Official contract][map-link-168]

- [`PARSE`][map-link-169]
- [`ENTER`][map-link-170]
- [`ANALYZE`][map-link-171]
- [`GENERATE`][map-link-172]
- [`ANNOTATION_PROCESSING`][map-link-173]
- [`ANNOTATION_PROCESSING_ROUND`][map-link-174]
- [`COMPILATION`][map-link-175]
- [`values()`][map-link-176]
- [`valueOf(java.lang.String)`][map-link-177]

### TaskEvent

[Official contract][map-link-178]

- [`<init>(com.sun.source.util.TaskEvent.Kind)`][map-link-179]
-
  [`<init>(com.sun.source.util.TaskEvent.Kind,javax.tools.JavaFileObject)`][utilities-taskevent-init-com-sun-source-util-taskevent-kind-javax-tools-javafileobject]
-
  [`<init>(com.sun.source.util.TaskEvent.Kind,com.sun.source.tree.CompilationUnitTree)`][utilities-taskevent-init-com-sun-source-util-taskevent-kind-com-sun-source-tree-compilationunittree]
-
  [`<init>(com.sun.source.util.TaskEvent.Kind,com.sun.source.tree.CompilationUnitTree,javax.lang.model.element.TypeElement)`][utilities-taskevent-init-com-sun-source-util-taskevent-kind-com-sun-source-tree-compilationunittree-javax-lang-model-element-typeelement]
- [`getKind()`][map-link-180]
- [`getSourceFile()`][map-link-181]
- [`getCompilationUnit()`][map-link-182]
- [`getTypeElement()`][map-link-183]

### TaskListener

[Official contract][map-link-184]

- [`started(com.sun.source.util.TaskEvent)`][map-link-185]
- [`finished(com.sun.source.util.TaskEvent)`][map-link-186]

### TreePath

[Official contract][map-link-187]

- [`<init>(com.sun.source.tree.CompilationUnitTree)`][map-link-188]
-
  [`<init>(com.sun.source.util.TreePath,com.sun.source.tree.Tree)`][utilities-treepath-init-com-sun-source-util-treepath-com-sun-source-tree-tree]
-
  [`getPath(com.sun.source.tree.CompilationUnitTree,com.sun.source.tree.Tree)`][utilities-treepath-getpath-com-sun-source-tree-compilationunittree-com-sun-source-tree-tree]
-
  [`getPath(com.sun.source.util.TreePath,com.sun.source.tree.Tree)`][utilities-treepath-getpath-com-sun-source-util-treepath-com-sun-source-tree-tree]
- [`getCompilationUnit()`][map-link-189]
- [`getLeaf()`][map-link-190]
- [`getParentPath()`][map-link-191]
- [`iterator()`][map-link-192]

### TreePathScanner

[Official contract][map-link-193]

- [`<init>()`][map-link-194]
- [`scan(com.sun.source.util.TreePath,P)`][map-link-195]
- [`scan(com.sun.source.tree.Tree,P)`][map-link-196]
- [`getCurrentPath()`][map-link-197]

### TreeScanner

[Official contract][map-link-198]

- [`<init>()`][map-link-199]
- [`scan(com.sun.source.tree.Tree,P)`][map-link-200]
- [`scan(java.lang.Iterable,P)`][map-link-201]
- [`reduce(R,R)`][map-link-202]
-
  [`visitCompilationUnit(com.sun.source.tree.CompilationUnitTree,P)`][utilities-treescanner-visitcompilationunit-com-sun-source-tree-compilationunittree-p]
- [`visitPackage(com.sun.source.tree.PackageTree,P)`][map-link-203]
- [`visitImport(com.sun.source.tree.ImportTree,P)`][map-link-204]
- [`visitClass(com.sun.source.tree.ClassTree,P)`][map-link-205]
- [`visitMethod(com.sun.source.tree.MethodTree,P)`][map-link-206]
- [`visitVariable(com.sun.source.tree.VariableTree,P)`][map-link-207]
-
  [`visitEmptyStatement(com.sun.source.tree.EmptyStatementTree,P)`][utilities-treescanner-visitemptystatement-com-sun-source-tree-emptystatementtree-p]
- [`visitBlock(com.sun.source.tree.BlockTree,P)`][map-link-208]
- [`visitDoWhileLoop(com.sun.source.tree.DoWhileLoopTree,P)`][utilities-treescanner-visitdowhileloop-com-sun-source-tree-dowhilelooptree-p]
- [`visitWhileLoop(com.sun.source.tree.WhileLoopTree,P)`][map-link-209]
- [`visitForLoop(com.sun.source.tree.ForLoopTree,P)`][map-link-210]
-
  [`visitEnhancedForLoop(com.sun.source.tree.EnhancedForLoopTree,P)`][utilities-treescanner-visitenhancedforloop-com-sun-source-tree-enhancedforlooptree-p]
-
  [`visitLabeledStatement(com.sun.source.tree.LabeledStatementTree,P)`][utilities-treescanner-visitlabeledstatement-com-sun-source-tree-labeledstatementtree-p]
- [`visitSwitch(com.sun.source.tree.SwitchTree,P)`][map-link-211]
-
  [`visitSwitchExpression(com.sun.source.tree.SwitchExpressionTree,P)`][utilities-treescanner-visitswitchexpression-com-sun-source-tree-switchexpressiontree-p]
- [`visitCase(com.sun.source.tree.CaseTree,P)`][map-link-212]
-
  [`visitSynchronized(com.sun.source.tree.SynchronizedTree,P)`][utilities-treescanner-visitsynchronized-com-sun-source-tree-synchronizedtree-p]
- [`visitTry(com.sun.source.tree.TryTree,P)`][map-link-213]
- [`visitCatch(com.sun.source.tree.CatchTree,P)`][map-link-214]
-
  [`visitConditionalExpression(com.sun.source.tree.ConditionalExpressionTree,P)`][utilities-treescanner-visitconditionalexpression-com-sun-source-tree-conditionalexpressiontree-p]
- [`visitIf(com.sun.source.tree.IfTree,P)`][map-link-215]
-
  [`visitExpressionStatement(com.sun.source.tree.ExpressionStatementTree,P)`][utilities-treescanner-visitexpressionstatement-com-sun-source-tree-expressionstatementtree-p]
- [`visitBreak(com.sun.source.tree.BreakTree,P)`][map-link-216]
- [`visitContinue(com.sun.source.tree.ContinueTree,P)`][map-link-217]
- [`visitReturn(com.sun.source.tree.ReturnTree,P)`][map-link-218]
- [`visitThrow(com.sun.source.tree.ThrowTree,P)`][map-link-219]
- [`visitAssert(com.sun.source.tree.AssertTree,P)`][map-link-220]
-
  [`visitMethodInvocation(com.sun.source.tree.MethodInvocationTree,P)`][utilities-treescanner-visitmethodinvocation-com-sun-source-tree-methodinvocationtree-p]
- [`visitNewClass(com.sun.source.tree.NewClassTree,P)`][map-link-221]
- [`visitNewArray(com.sun.source.tree.NewArrayTree,P)`][map-link-222]
-
  [`visitLambdaExpression(com.sun.source.tree.LambdaExpressionTree,P)`][utilities-treescanner-visitlambdaexpression-com-sun-source-tree-lambdaexpressiontree-p]
-
  [`visitParenthesized(com.sun.source.tree.ParenthesizedTree,P)`][utilities-treescanner-visitparenthesized-com-sun-source-tree-parenthesizedtree-p]
- [`visitAssignment(com.sun.source.tree.AssignmentTree,P)`][map-link-223]
-
  [`visitCompoundAssignment(com.sun.source.tree.CompoundAssignmentTree,P)`][utilities-treescanner-visitcompoundassignment-com-sun-source-tree-compoundassignmenttree-p]
- [`visitUnary(com.sun.source.tree.UnaryTree,P)`][map-link-224]
- [`visitBinary(com.sun.source.tree.BinaryTree,P)`][map-link-225]
- [`visitTypeCast(com.sun.source.tree.TypeCastTree,P)`][map-link-226]
- [`visitInstanceOf(com.sun.source.tree.InstanceOfTree,P)`][map-link-227]
- [`visitAnyPattern(com.sun.source.tree.AnyPatternTree,P)`][map-link-228]
-
  [`visitBindingPattern(com.sun.source.tree.BindingPatternTree,P)`][utilities-treescanner-visitbindingpattern-com-sun-source-tree-bindingpatterntree-p]
-
  [`visitDefaultCaseLabel(com.sun.source.tree.DefaultCaseLabelTree,P)`][utilities-treescanner-visitdefaultcaselabel-com-sun-source-tree-defaultcaselabeltree-p]
-
  [`visitConstantCaseLabel(com.sun.source.tree.ConstantCaseLabelTree,P)`][utilities-treescanner-visitconstantcaselabel-com-sun-source-tree-constantcaselabeltree-p]
-
  [`visitPatternCaseLabel(com.sun.source.tree.PatternCaseLabelTree,P)`][utilities-treescanner-visitpatterncaselabel-com-sun-source-tree-patterncaselabeltree-p]
-
  [`visitDeconstructionPattern(com.sun.source.tree.DeconstructionPatternTree,P)`][utilities-treescanner-visitdeconstructionpattern-com-sun-source-tree-deconstructionpatterntree-p]
- [`visitArrayAccess(com.sun.source.tree.ArrayAccessTree,P)`][utilities-treescanner-visitarrayaccess-com-sun-source-tree-arrayaccesstree-p]
-
  [`visitMemberSelect(com.sun.source.tree.MemberSelectTree,P)`][utilities-treescanner-visitmemberselect-com-sun-source-tree-memberselecttree-p]
-
  [`visitMemberReference(com.sun.source.tree.MemberReferenceTree,P)`][utilities-treescanner-visitmemberreference-com-sun-source-tree-memberreferencetree-p]
- [`visitIdentifier(com.sun.source.tree.IdentifierTree,P)`][map-link-229]
- [`visitLiteral(com.sun.source.tree.LiteralTree,P)`][map-link-230]
-
  [`visitPrimitiveType(com.sun.source.tree.PrimitiveTypeTree,P)`][utilities-treescanner-visitprimitivetype-com-sun-source-tree-primitivetypetree-p]
- [`visitArrayType(com.sun.source.tree.ArrayTypeTree,P)`][map-link-231]
-
  [`visitParameterizedType(com.sun.source.tree.ParameterizedTypeTree,P)`][utilities-treescanner-visitparameterizedtype-com-sun-source-tree-parameterizedtypetree-p]
- [`visitUnionType(com.sun.source.tree.UnionTypeTree,P)`][map-link-232]
-
  [`visitIntersectionType(com.sun.source.tree.IntersectionTypeTree,P)`][utilities-treescanner-visitintersectiontype-com-sun-source-tree-intersectiontypetree-p]
-
  [`visitTypeParameter(com.sun.source.tree.TypeParameterTree,P)`][utilities-treescanner-visittypeparameter-com-sun-source-tree-typeparametertree-p]
- [`visitWildcard(com.sun.source.tree.WildcardTree,P)`][map-link-233]
- [`visitModifiers(com.sun.source.tree.ModifiersTree,P)`][map-link-234]
- [`visitAnnotation(com.sun.source.tree.AnnotationTree,P)`][map-link-235]
-
  [`visitAnnotatedType(com.sun.source.tree.AnnotatedTypeTree,P)`][utilities-treescanner-visitannotatedtype-com-sun-source-tree-annotatedtypetree-p]
- [`visitModule(com.sun.source.tree.ModuleTree,P)`][map-link-236]
- [`visitExports(com.sun.source.tree.ExportsTree,P)`][map-link-237]
- [`visitOpens(com.sun.source.tree.OpensTree,P)`][map-link-238]
- [`visitProvides(com.sun.source.tree.ProvidesTree,P)`][map-link-239]
- [`visitRequires(com.sun.source.tree.RequiresTree,P)`][map-link-240]
- [`visitUses(com.sun.source.tree.UsesTree,P)`][map-link-241]
- [`visitOther(com.sun.source.tree.Tree,P)`][map-link-242]
- [`visitErroneous(com.sun.source.tree.ErroneousTree,P)`][map-link-243]
- [`visitYield(com.sun.source.tree.YieldTree,P)`][map-link-244]

### Trees

[Official contract][map-link-245]

- [`<init>()`][map-link-246]
- [`instance(javax.tools.JavaCompiler.CompilationTask)`][map-link-247]
-
  [`instance(javax.annotation.processing.ProcessingEnvironment)`][utilities-trees-instance-javax-annotation-processing-processingenvironment]
- [`getSourcePositions()`][map-link-248]
- [`getTree(javax.lang.model.element.Element)`][map-link-249]
- [`getTree(javax.lang.model.element.TypeElement)`][map-link-250]
- [`getTree(javax.lang.model.element.ExecutableElement)`][map-link-251]
-
  [`getTree(javax.lang.model.element.Element,javax.lang.model.element.AnnotationMirror)`][utilities-trees-gettree-javax-lang-model-element-element-javax-lang-model-element-annotationmirror]
-
  [`getTree(javax.lang.model.element.Element,javax.lang.model.element.AnnotationMirror,javax.lang.model.element.AnnotationValue)`][utilities-trees-gettree-javax-lang-model-element-element-javax-lang-model-element-annotationmirror-javax-lang-model-element-annotationvalue]
-
  [`getPath(com.sun.source.tree.CompilationUnitTree,com.sun.source.tree.Tree)`][utilities-trees-getpath-com-sun-source-tree-compilationunittree-com-sun-source-tree-tree]
- [`getPath(javax.lang.model.element.Element)`][map-link-252]
-
  [`getPath(javax.lang.model.element.Element,javax.lang.model.element.AnnotationMirror)`][utilities-trees-getpath-javax-lang-model-element-element-javax-lang-model-element-annotationmirror]
-
  [`getPath(javax.lang.model.element.Element,javax.lang.model.element.AnnotationMirror,javax.lang.model.element.AnnotationValue)`][utilities-trees-getpath-javax-lang-model-element-element-javax-lang-model-element-annotationmirror-javax-lang-model-element-annotationvalue]
- [`getElement(com.sun.source.util.TreePath)`][map-link-253]
- [`getTypeMirror(com.sun.source.util.TreePath)`][map-link-254]
- [`getScope(com.sun.source.util.TreePath)`][map-link-255]
- [`getDocComment(com.sun.source.util.TreePath)`][map-link-256]
-
  [`isAccessible(com.sun.source.tree.Scope,javax.lang.model.element.TypeElement)`][utilities-trees-isaccessible-com-sun-source-tree-scope-javax-lang-model-element-typeelement]
-
  [`isAccessible(com.sun.source.tree.Scope,javax.lang.model.element.Element,javax.lang.model.type.DeclaredType)`][utilities-trees-isaccessible-com-sun-source-tree-scope-javax-lang-model-element-element-javax-lang-model-type-declaredtype]
- [`getOriginalType(javax.lang.model.type.ErrorType)`][map-link-257]
-
  [`printMessage(javax.tools.Diagnostic.Kind,java.lang.CharSequence,com.sun.source.tree.Tree,com.sun.source.tree.CompilationUnitTree)`][utilities-trees-printmessage-javax-tools-diagnostic-kind-java-lang-charsequence-com-sun-source-tree-tree-com-sun-source-tree-compilationunittree]
- [`getLub(com.sun.source.tree.CatchTree)`][map-link-258]

## References

- [Oracle Java SE 25 package
  specification][map-link-259].

[utilities-docsourcepositions-getendposition-com-sun-source-tree-compilationunittree-com-sun-source-doctree-doccommenttree-com-sun-source-doctree-doctree]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocSourcePositions.html#getEndPosition(com.sun.source.tree.CompilationUnitTree,com.sun.source.doctree.DocCommentTree,com.sun.source.doctree.DocTree)
[utilities-docsourcepositions-getstartposition-com-sun-source-tree-compilationunittree-com-sun-source-doctree-doccommenttree-com-sun-source-doctree-doctree]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocSourcePositions.html#getStartPosition(com.sun.source.tree.CompilationUnitTree,com.sun.source.doctree.DocCommentTree,com.sun.source.doctree.DocTree)
[utilities-doctreefactory-newattributetree-javax-lang-model-element-name-com-sun-source-doctree-attributetree-valuekind-java-util-list]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newAttributeTree(javax.lang.model.element.Name,com.sun.source.doctree.AttributeTree.ValueKind,java.util.List)
[utilities-doctreefactory-newdoccommenttree-java-util-list-java-util-list-java-util-list-java-util-list]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newDocCommentTree(java.util.List,java.util.List,java.util.List,java.util.List)
[utilities-doctreefactory-newerroneoustree-java-lang-string-javax-tools-diagnostic]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newErroneousTree(java.lang.String,javax.tools.Diagnostic)
[utilities-doctreefactory-newexceptiontree-com-sun-source-doctree-referencetree-java-util-list]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newExceptionTree(com.sun.source.doctree.ReferenceTree,java.util.List)
[utilities-doctreefactory-newindextree-com-sun-source-doctree-doctree-java-util-list]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newIndexTree(com.sun.source.doctree.DocTree,java.util.List)
[utilities-doctreefactory-newinheritdoctree-com-sun-source-doctree-referencetree]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newInheritDocTree(com.sun.source.doctree.ReferenceTree)
[utilities-doctreefactory-newlinkplaintree-com-sun-source-doctree-referencetree-java-util-list]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newLinkPlainTree(com.sun.source.doctree.ReferenceTree,java.util.List)
[utilities-doctreefactory-newlinktree-com-sun-source-doctree-referencetree-java-util-list]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newLinkTree(com.sun.source.doctree.ReferenceTree,java.util.List)
[utilities-doctreefactory-newparamtree-boolean-com-sun-source-doctree-identifiertree-java-util-list]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newParamTree(boolean,com.sun.source.doctree.IdentifierTree,java.util.List)
[utilities-doctreefactory-newprovidestree-com-sun-source-doctree-referencetree-java-util-list]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newProvidesTree(com.sun.source.doctree.ReferenceTree,java.util.List)
[utilities-doctreefactory-newrawtexttree-com-sun-source-doctree-doctree-kind-java-lang-string]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newRawTextTree(com.sun.source.doctree.DocTree.Kind,java.lang.String)
[utilities-doctreefactory-newserialfieldtree-com-sun-source-doctree-identifiertree-com-sun-source-doctree-referencetree-java-util-list]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newSerialFieldTree(com.sun.source.doctree.IdentifierTree,com.sun.source.doctree.ReferenceTree,java.util.List)
[utilities-doctreefactory-newsnippettree-java-util-list-com-sun-source-doctree-texttree]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newSnippetTree(java.util.List,com.sun.source.doctree.TextTree)
[utilities-doctreefactory-newspectree-com-sun-source-doctree-texttree-java-util-list]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newSpecTree(com.sun.source.doctree.TextTree,java.util.List)
[utilities-doctreefactory-newstartelementtree-javax-lang-model-element-name-java-util-list-boolean]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newStartElementTree(javax.lang.model.element.Name,java.util.List,boolean)
[utilities-doctreefactory-newthrowstree-com-sun-source-doctree-referencetree-java-util-list]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newThrowsTree(com.sun.source.doctree.ReferenceTree,java.util.List)
[utilities-doctreefactory-newunknownblocktagtree-javax-lang-model-element-name-java-util-list]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newUnknownBlockTagTree(javax.lang.model.element.Name,java.util.List)
[utilities-doctreefactory-newunknowninlinetagtree-javax-lang-model-element-name-java-util-list]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newUnknownInlineTagTree(javax.lang.model.element.Name,java.util.List)
[utilities-doctreefactory-newusestree-com-sun-source-doctree-referencetree-java-util-list]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newUsesTree(com.sun.source.doctree.ReferenceTree,java.util.List)
[utilities-doctreefactory-newvaluetree-com-sun-source-doctree-texttree-com-sun-source-doctree-referencetree]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newValueTree(com.sun.source.doctree.TextTree,com.sun.source.doctree.ReferenceTree)
[utilities-doctreepath-getpath-com-sun-source-util-doctreepath-com-sun-source-doctree-doctree]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreePath.html#getPath(com.sun.source.util.DocTreePath,com.sun.source.doctree.DocTree)
[utilities-doctreepath-getpath-com-sun-source-util-treepath-com-sun-source-doctree-doccommenttree-com-sun-source-doctree-doctree]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreePath.html#getPath(com.sun.source.util.TreePath,com.sun.source.doctree.DocCommentTree,com.sun.source.doctree.DocTree)
[utilities-doctreepath-init-com-sun-source-util-doctreepath-com-sun-source-doctree-doctree]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreePath.html#%3Cinit%3E(com.sun.source.util.DocTreePath,com.sun.source.doctree.DocTree)
[utilities-doctreepath-init-com-sun-source-util-treepath-com-sun-source-doctree-doccommenttree]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreePath.html#%3Cinit%3E(com.sun.source.util.TreePath,com.sun.source.doctree.DocCommentTree)
[utilities-doctrees-getdoccommenttree-javax-lang-model-element-element-java-lang-string]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html#getDocCommentTree(javax.lang.model.element.Element,java.lang.String)
[utilities-doctrees-getdoctreepath-javax-tools-fileobject-javax-lang-model-element-packageelement]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html#getDocTreePath(javax.tools.FileObject,javax.lang.model.element.PackageElement)
[utilities-doctrees-instance-javax-annotation-processing-processingenvironment]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html#instance(javax.annotation.processing.ProcessingEnvironment)
[utilities-doctrees-printmessage-javax-tools-diagnostic-kind-java-lang-charsequence-com-sun-source-doctree-doctree-com-sun-source-doctree-doccommenttree-com-sun-source-tree-compilationunittree]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html#printMessage(javax.tools.Diagnostic.Kind,java.lang.CharSequence,com.sun.source.doctree.DocTree,com.sun.source.doctree.DocCommentTree,com.sun.source.tree.CompilationUnitTree)
[utilities-doctreescanner-visitattribute-com-sun-source-doctree-attributetree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitAttribute(com.sun.source.doctree.AttributeTree,P)
[utilities-doctreescanner-visitdeprecated-com-sun-source-doctree-deprecatedtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitDeprecated(com.sun.source.doctree.DeprecatedTree,P)
[utilities-doctreescanner-visitdoccomment-com-sun-source-doctree-doccommenttree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitDocComment(com.sun.source.doctree.DocCommentTree,P)
[utilities-doctreescanner-visitendelement-com-sun-source-doctree-endelementtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitEndElement(com.sun.source.doctree.EndElementTree,P)
[utilities-doctreescanner-visiterroneous-com-sun-source-doctree-erroneoustree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitErroneous(com.sun.source.doctree.ErroneousTree,P)
[utilities-doctreescanner-visitidentifier-com-sun-source-doctree-identifiertree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitIdentifier(com.sun.source.doctree.IdentifierTree,P)
[utilities-doctreescanner-visitinheritdoc-com-sun-source-doctree-inheritdoctree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitInheritDoc(com.sun.source.doctree.InheritDocTree,P)
[utilities-doctreescanner-visitreference-com-sun-source-doctree-referencetree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitReference(com.sun.source.doctree.ReferenceTree,P)
[utilities-doctreescanner-visitserialdata-com-sun-source-doctree-serialdatatree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitSerialData(com.sun.source.doctree.SerialDataTree,P)
[utilities-doctreescanner-visitserialfield-com-sun-source-doctree-serialfieldtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitSerialField(com.sun.source.doctree.SerialFieldTree,P)
[utilities-doctreescanner-visitstartelement-com-sun-source-doctree-startelementtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitStartElement(com.sun.source.doctree.StartElementTree,P)
[utilities-doctreescanner-visitsystemproperty-com-sun-source-doctree-systempropertytree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitSystemProperty(com.sun.source.doctree.SystemPropertyTree,P)
[utilities-doctreescanner-visitunknownblocktag-com-sun-source-doctree-unknownblocktagtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitUnknownBlockTag(com.sun.source.doctree.UnknownBlockTagTree,P)
[utilities-doctreescanner-visitunknowninlinetag-com-sun-source-doctree-unknowninlinetagtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitUnknownInlineTag(com.sun.source.doctree.UnknownInlineTagTree,P)
[utilities-javactask-instance-javax-annotation-processing-processingenvironment]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/JavacTask.html#instance(javax.annotation.processing.ProcessingEnvironment)
[utilities-javactask-setparameternameprovider-com-sun-source-util-parameternameprovider]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/JavacTask.html#setParameterNameProvider(com.sun.source.util.ParameterNameProvider)
[utilities-parameternameprovider-getparametername-javax-lang-model-element-variableelement]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/ParameterNameProvider.html#getParameterName(javax.lang.model.element.VariableElement)
[utilities-simpledoctreevisitor-visitattribute-com-sun-source-doctree-attributetree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitAttribute(com.sun.source.doctree.AttributeTree,P)
[utilities-simpledoctreevisitor-visitcomment-com-sun-source-doctree-commenttree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitComment(com.sun.source.doctree.CommentTree,P)
[utilities-simpledoctreevisitor-visitdeprecated-com-sun-source-doctree-deprecatedtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitDeprecated(com.sun.source.doctree.DeprecatedTree,P)
[utilities-simpledoctreevisitor-visitdoccomment-com-sun-source-doctree-doccommenttree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitDocComment(com.sun.source.doctree.DocCommentTree,P)
[utilities-simpledoctreevisitor-visitdocroot-com-sun-source-doctree-docroottree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitDocRoot(com.sun.source.doctree.DocRootTree,P)
[utilities-simpledoctreevisitor-visitdoctype-com-sun-source-doctree-doctypetree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitDocType(com.sun.source.doctree.DocTypeTree,P)
[utilities-simpledoctreevisitor-visitendelement-com-sun-source-doctree-endelementtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitEndElement(com.sun.source.doctree.EndElementTree,P)
[utilities-simpledoctreevisitor-visiterroneous-com-sun-source-doctree-erroneoustree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitErroneous(com.sun.source.doctree.ErroneousTree,P)
[utilities-simpledoctreevisitor-visitidentifier-com-sun-source-doctree-identifiertree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitIdentifier(com.sun.source.doctree.IdentifierTree,P)
[utilities-simpledoctreevisitor-visitinheritdoc-com-sun-source-doctree-inheritdoctree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitInheritDoc(com.sun.source.doctree.InheritDocTree,P)
[utilities-simpledoctreevisitor-visitliteral-com-sun-source-doctree-literaltree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitLiteral(com.sun.source.doctree.LiteralTree,P)
[utilities-simpledoctreevisitor-visitprovides-com-sun-source-doctree-providestree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitProvides(com.sun.source.doctree.ProvidesTree,P)
[utilities-simpledoctreevisitor-visitrawtext-com-sun-source-doctree-rawtexttree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitRawText(com.sun.source.doctree.RawTextTree,P)
[utilities-simpledoctreevisitor-visitreference-com-sun-source-doctree-referencetree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitReference(com.sun.source.doctree.ReferenceTree,P)
[utilities-simpledoctreevisitor-visitserialdata-com-sun-source-doctree-serialdatatree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitSerialData(com.sun.source.doctree.SerialDataTree,P)
[utilities-simpledoctreevisitor-visitserialfield-com-sun-source-doctree-serialfieldtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitSerialField(com.sun.source.doctree.SerialFieldTree,P)
[utilities-simpledoctreevisitor-visitsnippet-com-sun-source-doctree-snippettree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitSnippet(com.sun.source.doctree.SnippetTree,P)
[utilities-simpledoctreevisitor-visitstartelement-com-sun-source-doctree-startelementtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitStartElement(com.sun.source.doctree.StartElementTree,P)
[utilities-simpledoctreevisitor-visitsummary-com-sun-source-doctree-summarytree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitSummary(com.sun.source.doctree.SummaryTree,P)
[utilities-simpledoctreevisitor-visitsystemproperty-com-sun-source-doctree-systempropertytree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitSystemProperty(com.sun.source.doctree.SystemPropertyTree,P)
[utilities-simpledoctreevisitor-visitunknownblocktag-com-sun-source-doctree-unknownblocktagtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitUnknownBlockTag(com.sun.source.doctree.UnknownBlockTagTree,P)
[utilities-simpledoctreevisitor-visitunknowninlinetag-com-sun-source-doctree-unknowninlinetagtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitUnknownInlineTag(com.sun.source.doctree.UnknownInlineTagTree,P)
[utilities-simpledoctreevisitor-visitversion-com-sun-source-doctree-versiontree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitVersion(com.sun.source.doctree.VersionTree,P)
[utilities-simpletreevisitor-visitannotatedtype-com-sun-source-tree-annotatedtypetree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitAnnotatedType(com.sun.source.tree.AnnotatedTypeTree,P)
[utilities-simpletreevisitor-visitannotation-com-sun-source-tree-annotationtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitAnnotation(com.sun.source.tree.AnnotationTree,P)
[utilities-simpletreevisitor-visitanypattern-com-sun-source-tree-anypatterntree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitAnyPattern(com.sun.source.tree.AnyPatternTree,P)
[utilities-simpletreevisitor-visitarrayaccess-com-sun-source-tree-arrayaccesstree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitArrayAccess(com.sun.source.tree.ArrayAccessTree,P)
[utilities-simpletreevisitor-visitarraytype-com-sun-source-tree-arraytypetree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitArrayType(com.sun.source.tree.ArrayTypeTree,P)
[utilities-simpletreevisitor-visitassignment-com-sun-source-tree-assignmenttree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitAssignment(com.sun.source.tree.AssignmentTree,P)
[utilities-simpletreevisitor-visitbindingpattern-com-sun-source-tree-bindingpatterntree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitBindingPattern(com.sun.source.tree.BindingPatternTree,P)
[utilities-simpletreevisitor-visitcompilationunit-com-sun-source-tree-compilationunittree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitCompilationUnit(com.sun.source.tree.CompilationUnitTree,P)
[utilities-simpletreevisitor-visitcompoundassignment-com-sun-source-tree-compoundassignmenttree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitCompoundAssignment(com.sun.source.tree.CompoundAssignmentTree,P)
[utilities-simpletreevisitor-visitconditionalexpression-com-sun-source-tree-conditionalexpressiontree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitConditionalExpression(com.sun.source.tree.ConditionalExpressionTree,P)
[utilities-simpletreevisitor-visitconstantcaselabel-com-sun-source-tree-constantcaselabeltree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitConstantCaseLabel(com.sun.source.tree.ConstantCaseLabelTree,P)
[utilities-simpletreevisitor-visitdeconstructionpattern-com-sun-source-tree-deconstructionpatterntree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitDeconstructionPattern(com.sun.source.tree.DeconstructionPatternTree,P)
[utilities-simpletreevisitor-visitdefaultcaselabel-com-sun-source-tree-defaultcaselabeltree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitDefaultCaseLabel(com.sun.source.tree.DefaultCaseLabelTree,P)
[utilities-simpletreevisitor-visitdowhileloop-com-sun-source-tree-dowhilelooptree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitDoWhileLoop(com.sun.source.tree.DoWhileLoopTree,P)
[utilities-simpletreevisitor-visitemptystatement-com-sun-source-tree-emptystatementtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitEmptyStatement(com.sun.source.tree.EmptyStatementTree,P)
[utilities-simpletreevisitor-visitenhancedforloop-com-sun-source-tree-enhancedforlooptree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitEnhancedForLoop(com.sun.source.tree.EnhancedForLoopTree,P)
[utilities-simpletreevisitor-visiterroneous-com-sun-source-tree-erroneoustree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitErroneous(com.sun.source.tree.ErroneousTree,P)
[utilities-simpletreevisitor-visitexpressionstatement-com-sun-source-tree-expressionstatementtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitExpressionStatement(com.sun.source.tree.ExpressionStatementTree,P)
[utilities-simpletreevisitor-visitidentifier-com-sun-source-tree-identifiertree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitIdentifier(com.sun.source.tree.IdentifierTree,P)
[utilities-simpletreevisitor-visitinstanceof-com-sun-source-tree-instanceoftree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitInstanceOf(com.sun.source.tree.InstanceOfTree,P)
[utilities-simpletreevisitor-visitintersectiontype-com-sun-source-tree-intersectiontypetree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitIntersectionType(com.sun.source.tree.IntersectionTypeTree,P)
[utilities-simpletreevisitor-visitlabeledstatement-com-sun-source-tree-labeledstatementtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitLabeledStatement(com.sun.source.tree.LabeledStatementTree,P)
[utilities-simpletreevisitor-visitlambdaexpression-com-sun-source-tree-lambdaexpressiontree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitLambdaExpression(com.sun.source.tree.LambdaExpressionTree,P)
[utilities-simpletreevisitor-visitmemberreference-com-sun-source-tree-memberreferencetree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitMemberReference(com.sun.source.tree.MemberReferenceTree,P)
[utilities-simpletreevisitor-visitmemberselect-com-sun-source-tree-memberselecttree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitMemberSelect(com.sun.source.tree.MemberSelectTree,P)
[utilities-simpletreevisitor-visitmethodinvocation-com-sun-source-tree-methodinvocationtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitMethodInvocation(com.sun.source.tree.MethodInvocationTree,P)
[utilities-simpletreevisitor-visitmodifiers-com-sun-source-tree-modifierstree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitModifiers(com.sun.source.tree.ModifiersTree,P)
[utilities-simpletreevisitor-visitparameterizedtype-com-sun-source-tree-parameterizedtypetree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitParameterizedType(com.sun.source.tree.ParameterizedTypeTree,P)
[utilities-simpletreevisitor-visitparenthesized-com-sun-source-tree-parenthesizedtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitParenthesized(com.sun.source.tree.ParenthesizedTree,P)
[utilities-simpletreevisitor-visitpatterncaselabel-com-sun-source-tree-patterncaselabeltree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitPatternCaseLabel(com.sun.source.tree.PatternCaseLabelTree,P)
[utilities-simpletreevisitor-visitprimitivetype-com-sun-source-tree-primitivetypetree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitPrimitiveType(com.sun.source.tree.PrimitiveTypeTree,P)
[utilities-simpletreevisitor-visitswitchexpression-com-sun-source-tree-switchexpressiontree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitSwitchExpression(com.sun.source.tree.SwitchExpressionTree,P)
[utilities-simpletreevisitor-visitsynchronized-com-sun-source-tree-synchronizedtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitSynchronized(com.sun.source.tree.SynchronizedTree,P)
[utilities-simpletreevisitor-visittypeparameter-com-sun-source-tree-typeparametertree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitTypeParameter(com.sun.source.tree.TypeParameterTree,P)
[utilities-simpletreevisitor-visituniontype-com-sun-source-tree-uniontypetree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitUnionType(com.sun.source.tree.UnionTypeTree,P)
[utilities-simpletreevisitor-visitwhileloop-com-sun-source-tree-whilelooptree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitWhileLoop(com.sun.source.tree.WhileLoopTree,P)
[utilities-sourcepositions-getendposition-com-sun-source-tree-compilationunittree-com-sun-source-tree-tree]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SourcePositions.html#getEndPosition(com.sun.source.tree.CompilationUnitTree,com.sun.source.tree.Tree)
[utilities-sourcepositions-getstartposition-com-sun-source-tree-compilationunittree-com-sun-source-tree-tree]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SourcePositions.html#getStartPosition(com.sun.source.tree.CompilationUnitTree,com.sun.source.tree.Tree)
[utilities-taskevent-init-com-sun-source-util-taskevent-kind-com-sun-source-tree-compilationunittree]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskEvent.html#%3Cinit%3E(com.sun.source.util.TaskEvent.Kind,com.sun.source.tree.CompilationUnitTree)
[utilities-taskevent-init-com-sun-source-util-taskevent-kind-com-sun-source-tree-compilationunittree-javax-lang-model-element-typeelement]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskEvent.html#%3Cinit%3E(com.sun.source.util.TaskEvent.Kind,com.sun.source.tree.CompilationUnitTree,javax.lang.model.element.TypeElement)
[utilities-taskevent-init-com-sun-source-util-taskevent-kind-javax-tools-javafileobject]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskEvent.html#%3Cinit%3E(com.sun.source.util.TaskEvent.Kind,javax.tools.JavaFileObject)
[utilities-treepath-getpath-com-sun-source-tree-compilationunittree-com-sun-source-tree-tree]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreePath.html#getPath(com.sun.source.tree.CompilationUnitTree,com.sun.source.tree.Tree)
[utilities-treepath-getpath-com-sun-source-util-treepath-com-sun-source-tree-tree]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreePath.html#getPath(com.sun.source.util.TreePath,com.sun.source.tree.Tree)
[utilities-treepath-init-com-sun-source-util-treepath-com-sun-source-tree-tree]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreePath.html#%3Cinit%3E(com.sun.source.util.TreePath,com.sun.source.tree.Tree)
[utilities-trees-getpath-com-sun-source-tree-compilationunittree-com-sun-source-tree-tree]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#getPath(com.sun.source.tree.CompilationUnitTree,com.sun.source.tree.Tree)
[utilities-trees-getpath-javax-lang-model-element-element-javax-lang-model-element-annotationmirror]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#getPath(javax.lang.model.element.Element,javax.lang.model.element.AnnotationMirror)
[utilities-trees-getpath-javax-lang-model-element-element-javax-lang-model-element-annotationmirror-javax-lang-model-element-annotationvalue]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#getPath(javax.lang.model.element.Element,javax.lang.model.element.AnnotationMirror,javax.lang.model.element.AnnotationValue)
[utilities-trees-gettree-javax-lang-model-element-element-javax-lang-model-element-annotationmirror]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#getTree(javax.lang.model.element.Element,javax.lang.model.element.AnnotationMirror)
[utilities-trees-gettree-javax-lang-model-element-element-javax-lang-model-element-annotationmirror-javax-lang-model-element-annotationvalue]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#getTree(javax.lang.model.element.Element,javax.lang.model.element.AnnotationMirror,javax.lang.model.element.AnnotationValue)
[utilities-trees-instance-javax-annotation-processing-processingenvironment]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#instance(javax.annotation.processing.ProcessingEnvironment)
[utilities-trees-isaccessible-com-sun-source-tree-scope-javax-lang-model-element-element-javax-lang-model-type-declaredtype]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#isAccessible(com.sun.source.tree.Scope,javax.lang.model.element.Element,javax.lang.model.type.DeclaredType)
[utilities-trees-isaccessible-com-sun-source-tree-scope-javax-lang-model-element-typeelement]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#isAccessible(com.sun.source.tree.Scope,javax.lang.model.element.TypeElement)
[utilities-trees-printmessage-javax-tools-diagnostic-kind-java-lang-charsequence-com-sun-source-tree-tree-com-sun-source-tree-compilationunittree]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#printMessage(javax.tools.Diagnostic.Kind,java.lang.CharSequence,com.sun.source.tree.Tree,com.sun.source.tree.CompilationUnitTree)
[utilities-treescanner-visitannotatedtype-com-sun-source-tree-annotatedtypetree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitAnnotatedType(com.sun.source.tree.AnnotatedTypeTree,P)
[utilities-treescanner-visitarrayaccess-com-sun-source-tree-arrayaccesstree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitArrayAccess(com.sun.source.tree.ArrayAccessTree,P)
[utilities-treescanner-visitbindingpattern-com-sun-source-tree-bindingpatterntree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitBindingPattern(com.sun.source.tree.BindingPatternTree,P)
[utilities-treescanner-visitcompilationunit-com-sun-source-tree-compilationunittree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitCompilationUnit(com.sun.source.tree.CompilationUnitTree,P)
[utilities-treescanner-visitcompoundassignment-com-sun-source-tree-compoundassignmenttree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitCompoundAssignment(com.sun.source.tree.CompoundAssignmentTree,P)
[utilities-treescanner-visitconditionalexpression-com-sun-source-tree-conditionalexpressiontree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitConditionalExpression(com.sun.source.tree.ConditionalExpressionTree,P)
[utilities-treescanner-visitconstantcaselabel-com-sun-source-tree-constantcaselabeltree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitConstantCaseLabel(com.sun.source.tree.ConstantCaseLabelTree,P)
[utilities-treescanner-visitdeconstructionpattern-com-sun-source-tree-deconstructionpatterntree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitDeconstructionPattern(com.sun.source.tree.DeconstructionPatternTree,P)
[utilities-treescanner-visitdefaultcaselabel-com-sun-source-tree-defaultcaselabeltree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitDefaultCaseLabel(com.sun.source.tree.DefaultCaseLabelTree,P)
[utilities-treescanner-visitdowhileloop-com-sun-source-tree-dowhilelooptree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitDoWhileLoop(com.sun.source.tree.DoWhileLoopTree,P)
[utilities-treescanner-visitemptystatement-com-sun-source-tree-emptystatementtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitEmptyStatement(com.sun.source.tree.EmptyStatementTree,P)
[utilities-treescanner-visitenhancedforloop-com-sun-source-tree-enhancedforlooptree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitEnhancedForLoop(com.sun.source.tree.EnhancedForLoopTree,P)
[utilities-treescanner-visitexpressionstatement-com-sun-source-tree-expressionstatementtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitExpressionStatement(com.sun.source.tree.ExpressionStatementTree,P)
[utilities-treescanner-visitintersectiontype-com-sun-source-tree-intersectiontypetree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitIntersectionType(com.sun.source.tree.IntersectionTypeTree,P)
[utilities-treescanner-visitlabeledstatement-com-sun-source-tree-labeledstatementtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitLabeledStatement(com.sun.source.tree.LabeledStatementTree,P)
[utilities-treescanner-visitlambdaexpression-com-sun-source-tree-lambdaexpressiontree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitLambdaExpression(com.sun.source.tree.LambdaExpressionTree,P)
[utilities-treescanner-visitmemberreference-com-sun-source-tree-memberreferencetree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitMemberReference(com.sun.source.tree.MemberReferenceTree,P)
[utilities-treescanner-visitmemberselect-com-sun-source-tree-memberselecttree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitMemberSelect(com.sun.source.tree.MemberSelectTree,P)
[utilities-treescanner-visitmethodinvocation-com-sun-source-tree-methodinvocationtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitMethodInvocation(com.sun.source.tree.MethodInvocationTree,P)
[utilities-treescanner-visitparameterizedtype-com-sun-source-tree-parameterizedtypetree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitParameterizedType(com.sun.source.tree.ParameterizedTypeTree,P)
[utilities-treescanner-visitparenthesized-com-sun-source-tree-parenthesizedtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitParenthesized(com.sun.source.tree.ParenthesizedTree,P)
[utilities-treescanner-visitpatterncaselabel-com-sun-source-tree-patterncaselabeltree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitPatternCaseLabel(com.sun.source.tree.PatternCaseLabelTree,P)
[utilities-treescanner-visitprimitivetype-com-sun-source-tree-primitivetypetree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitPrimitiveType(com.sun.source.tree.PrimitiveTypeTree,P)
[utilities-treescanner-visitswitchexpression-com-sun-source-tree-switchexpressiontree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitSwitchExpression(com.sun.source.tree.SwitchExpressionTree,P)
[utilities-treescanner-visitsynchronized-com-sun-source-tree-synchronizedtree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitSynchronized(com.sun.source.tree.SynchronizedTree,P)
[utilities-treescanner-visittypeparameter-com-sun-source-tree-typeparametertree-p]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitTypeParameter(com.sun.source.tree.TypeParameterTree,P)

[map-link-1]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocSourcePositions.html
[map-link-2]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html
[map-link-3]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newAuthorTree(java.util.List)
[map-link-4]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newCodeTree(com.sun.source.doctree.TextTree)
[map-link-5]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newCommentTree(java.lang.String)
[map-link-6]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newDeprecatedTree(java.util.List)
[map-link-7]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newDocCommentTree(java.util.List,java.util.List)
[map-link-8]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newDocRootTree()
[map-link-9]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newDocTypeTree(java.lang.String)
[map-link-10]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newEndElementTree(javax.lang.model.element.Name)
[map-link-11]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newEntityTree(javax.lang.model.element.Name)
[map-link-12]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newEscapeTree(char)
[map-link-13]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newHiddenTree(java.util.List)
[map-link-14]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newIdentifierTree(javax.lang.model.element.Name)
[map-link-15]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newInheritDocTree()
[map-link-16]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newLiteralTree(com.sun.source.doctree.TextTree)
[map-link-17]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newReferenceTree(java.lang.String)
[map-link-18]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newReturnTree(java.util.List)
[map-link-19]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newReturnTree(boolean,java.util.List)
[map-link-20]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newSeeTree(java.util.List)
[map-link-21]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newSerialTree(java.util.List)
[map-link-22]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newSerialDataTree(java.util.List)
[map-link-23]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newSinceTree(java.util.List)
[map-link-24]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newSummaryTree(java.util.List)
[map-link-25]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newSystemPropertyTree(javax.lang.model.element.Name)
[map-link-26]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newTextTree(java.lang.String)
[map-link-27]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newValueTree(com.sun.source.doctree.ReferenceTree)
[map-link-28]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#newVersionTree(java.util.List)
[map-link-29]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#at(int)
[map-link-30]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeFactory.html#getFirstSentence(java.util.List)
[map-link-31]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreePath.html
[map-link-32]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreePath.html#getTreePath()
[map-link-33]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreePath.html#getDocComment()
[map-link-34]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreePath.html#getLeaf()
[map-link-35]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreePath.html#getParentPath()
[map-link-36]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreePath.html#iterator()
[map-link-37]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreePathScanner.html
[map-link-38]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreePathScanner.html#%3Cinit%3E()
[map-link-39]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreePathScanner.html#scan(com.sun.source.util.DocTreePath,P)
[map-link-40]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreePathScanner.html#scan(com.sun.source.doctree.DocTree,P)
[map-link-41]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreePathScanner.html#getCurrentPath()
[map-link-42]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html
[map-link-43]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#%3Cinit%3E()
[map-link-44]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#scan(com.sun.source.doctree.DocTree,P)
[map-link-45]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#scan(java.lang.Iterable,P)
[map-link-46]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#reduce(R,R)
[map-link-47]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitAuthor(com.sun.source.doctree.AuthorTree,P)
[map-link-48]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitComment(com.sun.source.doctree.CommentTree,P)
[map-link-49]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitDocRoot(com.sun.source.doctree.DocRootTree,P)
[map-link-50]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitDocType(com.sun.source.doctree.DocTypeTree,P)
[map-link-51]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitEntity(com.sun.source.doctree.EntityTree,P)
[map-link-52]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitEscape(com.sun.source.doctree.EscapeTree,P)
[map-link-53]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitHidden(com.sun.source.doctree.HiddenTree,P)
[map-link-54]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitIndex(com.sun.source.doctree.IndexTree,P)
[map-link-55]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitLink(com.sun.source.doctree.LinkTree,P)
[map-link-56]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitLiteral(com.sun.source.doctree.LiteralTree,P)
[map-link-57]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitParam(com.sun.source.doctree.ParamTree,P)
[map-link-58]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitProvides(com.sun.source.doctree.ProvidesTree,P)
[map-link-59]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitRawText(com.sun.source.doctree.RawTextTree,P)
[map-link-60]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitReturn(com.sun.source.doctree.ReturnTree,P)
[map-link-61]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitSee(com.sun.source.doctree.SeeTree,P)
[map-link-62]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitSerial(com.sun.source.doctree.SerialTree,P)
[map-link-63]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitSince(com.sun.source.doctree.SinceTree,P)
[map-link-64]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitSnippet(com.sun.source.doctree.SnippetTree,P)
[map-link-65]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitSpec(com.sun.source.doctree.SpecTree,P)
[map-link-66]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitSummary(com.sun.source.doctree.SummaryTree,P)
[map-link-67]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitText(com.sun.source.doctree.TextTree,P)
[map-link-68]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitThrows(com.sun.source.doctree.ThrowsTree,P)
[map-link-69]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitUses(com.sun.source.doctree.UsesTree,P)
[map-link-70]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitValue(com.sun.source.doctree.ValueTree,P)
[map-link-71]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitVersion(com.sun.source.doctree.VersionTree,P)
[map-link-72]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTreeScanner.html#visitOther(com.sun.source.doctree.DocTree,P)
[map-link-73]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html
[map-link-74]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html#%3Cinit%3E()
[map-link-75]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html#instance(javax.tools.JavaCompiler.CompilationTask)
[map-link-76]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html#getBreakIterator()
[map-link-77]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html#getDocCommentKind(com.sun.source.util.TreePath)
[map-link-78]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html#getDocCommentTree(com.sun.source.util.TreePath)
[map-link-79]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html#getDocCommentTree(javax.lang.model.element.Element)
[map-link-80]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html#getDocCommentTree(javax.tools.FileObject)
[map-link-81]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html#getElement(com.sun.source.util.DocTreePath)
[map-link-82]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html#getType(com.sun.source.util.DocTreePath)
[map-link-83]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html#getFirstSentence(java.util.List)
[map-link-84]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html#getSourcePositions()
[map-link-85]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html#setBreakIterator(java.text.BreakIterator)
[map-link-86]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html#getDocTreeFactory()
[map-link-87]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/DocTrees.html#getCharacters(com.sun.source.doctree.EntityTree)
[map-link-88]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/JavacTask.html
[map-link-89]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/JavacTask.html#%3Cinit%3E()
[map-link-90]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/JavacTask.html#parse()
[map-link-91]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/JavacTask.html#analyze()
[map-link-92]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/JavacTask.html#generate()
[map-link-93]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/JavacTask.html#setTaskListener(com.sun.source.util.TaskListener)
[map-link-94]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/JavacTask.html#addTaskListener(com.sun.source.util.TaskListener)
[map-link-95]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/JavacTask.html#removeTaskListener(com.sun.source.util.TaskListener)
[map-link-96]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/JavacTask.html#getTypeMirror(java.lang.Iterable)
[map-link-97]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/JavacTask.html#getElements()
[map-link-98]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/JavacTask.html#getTypes()
[map-link-99]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/ParameterNameProvider.html
[map-link-100]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Plugin.html
[map-link-101]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Plugin.html#getName()
[map-link-102]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Plugin.html#init(com.sun.source.util.JavacTask,java.lang.String...)
[map-link-103]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Plugin.html#autoStart()
[map-link-104]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html
[map-link-105]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#DEFAULT_VALUE
[map-link-106]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#%3Cinit%3E()
[map-link-107]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#%3Cinit%3E(R)
[map-link-108]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#defaultAction(com.sun.source.doctree.DocTree,P)
[map-link-109]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visit(com.sun.source.doctree.DocTree,P)
[map-link-110]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visit(java.lang.Iterable,P)
[map-link-111]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitAuthor(com.sun.source.doctree.AuthorTree,P)
[map-link-112]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitEntity(com.sun.source.doctree.EntityTree,P)
[map-link-113]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitEscape(com.sun.source.doctree.EscapeTree,P)
[map-link-114]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitHidden(com.sun.source.doctree.HiddenTree,P)
[map-link-115]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitIndex(com.sun.source.doctree.IndexTree,P)
[map-link-116]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitLink(com.sun.source.doctree.LinkTree,P)
[map-link-117]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitParam(com.sun.source.doctree.ParamTree,P)
[map-link-118]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitReturn(com.sun.source.doctree.ReturnTree,P)
[map-link-119]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitSee(com.sun.source.doctree.SeeTree,P)
[map-link-120]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitSerial(com.sun.source.doctree.SerialTree,P)
[map-link-121]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitSince(com.sun.source.doctree.SinceTree,P)
[map-link-122]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitSpec(com.sun.source.doctree.SpecTree,P)
[map-link-123]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitText(com.sun.source.doctree.TextTree,P)
[map-link-124]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitThrows(com.sun.source.doctree.ThrowsTree,P)
[map-link-125]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitUses(com.sun.source.doctree.UsesTree,P)
[map-link-126]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitValue(com.sun.source.doctree.ValueTree,P)
[map-link-127]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleDocTreeVisitor.html#visitOther(com.sun.source.doctree.DocTree,P)
[map-link-128]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html
[map-link-129]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#DEFAULT_VALUE
[map-link-130]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#%3Cinit%3E()
[map-link-131]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#%3Cinit%3E(R)
[map-link-132]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#defaultAction(com.sun.source.tree.Tree,P)
[map-link-133]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visit(com.sun.source.tree.Tree,P)
[map-link-134]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visit(java.lang.Iterable,P)
[map-link-135]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitPackage(com.sun.source.tree.PackageTree,P)
[map-link-136]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitImport(com.sun.source.tree.ImportTree,P)
[map-link-137]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitClass(com.sun.source.tree.ClassTree,P)
[map-link-138]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitMethod(com.sun.source.tree.MethodTree,P)
[map-link-139]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitVariable(com.sun.source.tree.VariableTree,P)
[map-link-140]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitBlock(com.sun.source.tree.BlockTree,P)
[map-link-141]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitForLoop(com.sun.source.tree.ForLoopTree,P)
[map-link-142]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitSwitch(com.sun.source.tree.SwitchTree,P)
[map-link-143]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitCase(com.sun.source.tree.CaseTree,P)
[map-link-144]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitTry(com.sun.source.tree.TryTree,P)
[map-link-145]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitCatch(com.sun.source.tree.CatchTree,P)
[map-link-146]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitIf(com.sun.source.tree.IfTree,P)
[map-link-147]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitBreak(com.sun.source.tree.BreakTree,P)
[map-link-148]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitContinue(com.sun.source.tree.ContinueTree,P)
[map-link-149]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitReturn(com.sun.source.tree.ReturnTree,P)
[map-link-150]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitThrow(com.sun.source.tree.ThrowTree,P)
[map-link-151]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitAssert(com.sun.source.tree.AssertTree,P)
[map-link-152]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitNewClass(com.sun.source.tree.NewClassTree,P)
[map-link-153]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitNewArray(com.sun.source.tree.NewArrayTree,P)
[map-link-154]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitUnary(com.sun.source.tree.UnaryTree,P)
[map-link-155]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitBinary(com.sun.source.tree.BinaryTree,P)
[map-link-156]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitTypeCast(com.sun.source.tree.TypeCastTree,P)
[map-link-157]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitLiteral(com.sun.source.tree.LiteralTree,P)
[map-link-158]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitWildcard(com.sun.source.tree.WildcardTree,P)
[map-link-159]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitModule(com.sun.source.tree.ModuleTree,P)
[map-link-160]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitExports(com.sun.source.tree.ExportsTree,P)
[map-link-161]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitOpens(com.sun.source.tree.OpensTree,P)
[map-link-162]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitProvides(com.sun.source.tree.ProvidesTree,P)
[map-link-163]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitRequires(com.sun.source.tree.RequiresTree,P)
[map-link-164]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitUses(com.sun.source.tree.UsesTree,P)
[map-link-165]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitOther(com.sun.source.tree.Tree,P)
[map-link-166]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SimpleTreeVisitor.html#visitYield(com.sun.source.tree.YieldTree,P)
[map-link-167]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/SourcePositions.html
[map-link-168]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskEvent.Kind.html
[map-link-169]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskEvent.Kind.html#PARSE
[map-link-170]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskEvent.Kind.html#ENTER
[map-link-171]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskEvent.Kind.html#ANALYZE
[map-link-172]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskEvent.Kind.html#GENERATE
[map-link-173]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskEvent.Kind.html#ANNOTATION_PROCESSING
[map-link-174]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskEvent.Kind.html#ANNOTATION_PROCESSING_ROUND
[map-link-175]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskEvent.Kind.html#COMPILATION
[map-link-176]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskEvent.Kind.html#values()
[map-link-177]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskEvent.Kind.html#valueOf(java.lang.String)
[map-link-178]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskEvent.html
[map-link-179]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskEvent.html#%3Cinit%3E(com.sun.source.util.TaskEvent.Kind)
[map-link-180]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskEvent.html#getKind()
[map-link-181]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskEvent.html#getSourceFile()
[map-link-182]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskEvent.html#getCompilationUnit()
[map-link-183]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskEvent.html#getTypeElement()
[map-link-184]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskListener.html
[map-link-185]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskListener.html#started(com.sun.source.util.TaskEvent)
[map-link-186]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TaskListener.html#finished(com.sun.source.util.TaskEvent)
[map-link-187]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreePath.html
[map-link-188]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreePath.html#%3Cinit%3E(com.sun.source.tree.CompilationUnitTree)
[map-link-189]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreePath.html#getCompilationUnit()
[map-link-190]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreePath.html#getLeaf()
[map-link-191]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreePath.html#getParentPath()
[map-link-192]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreePath.html#iterator()
[map-link-193]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreePathScanner.html
[map-link-194]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreePathScanner.html#%3Cinit%3E()
[map-link-195]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreePathScanner.html#scan(com.sun.source.util.TreePath,P)
[map-link-196]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreePathScanner.html#scan(com.sun.source.tree.Tree,P)
[map-link-197]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreePathScanner.html#getCurrentPath()
[map-link-198]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html
[map-link-199]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#%3Cinit%3E()
[map-link-200]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#scan(com.sun.source.tree.Tree,P)
[map-link-201]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#scan(java.lang.Iterable,P)
[map-link-202]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#reduce(R,R)
[map-link-203]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitPackage(com.sun.source.tree.PackageTree,P)
[map-link-204]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitImport(com.sun.source.tree.ImportTree,P)
[map-link-205]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitClass(com.sun.source.tree.ClassTree,P)
[map-link-206]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitMethod(com.sun.source.tree.MethodTree,P)
[map-link-207]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitVariable(com.sun.source.tree.VariableTree,P)
[map-link-208]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitBlock(com.sun.source.tree.BlockTree,P)
[map-link-209]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitWhileLoop(com.sun.source.tree.WhileLoopTree,P)
[map-link-210]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitForLoop(com.sun.source.tree.ForLoopTree,P)
[map-link-211]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitSwitch(com.sun.source.tree.SwitchTree,P)
[map-link-212]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitCase(com.sun.source.tree.CaseTree,P)
[map-link-213]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitTry(com.sun.source.tree.TryTree,P)
[map-link-214]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitCatch(com.sun.source.tree.CatchTree,P)
[map-link-215]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitIf(com.sun.source.tree.IfTree,P)
[map-link-216]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitBreak(com.sun.source.tree.BreakTree,P)
[map-link-217]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitContinue(com.sun.source.tree.ContinueTree,P)
[map-link-218]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitReturn(com.sun.source.tree.ReturnTree,P)
[map-link-219]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitThrow(com.sun.source.tree.ThrowTree,P)
[map-link-220]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitAssert(com.sun.source.tree.AssertTree,P)
[map-link-221]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitNewClass(com.sun.source.tree.NewClassTree,P)
[map-link-222]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitNewArray(com.sun.source.tree.NewArrayTree,P)
[map-link-223]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitAssignment(com.sun.source.tree.AssignmentTree,P)
[map-link-224]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitUnary(com.sun.source.tree.UnaryTree,P)
[map-link-225]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitBinary(com.sun.source.tree.BinaryTree,P)
[map-link-226]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitTypeCast(com.sun.source.tree.TypeCastTree,P)
[map-link-227]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitInstanceOf(com.sun.source.tree.InstanceOfTree,P)
[map-link-228]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitAnyPattern(com.sun.source.tree.AnyPatternTree,P)
[map-link-229]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitIdentifier(com.sun.source.tree.IdentifierTree,P)
[map-link-230]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitLiteral(com.sun.source.tree.LiteralTree,P)
[map-link-231]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitArrayType(com.sun.source.tree.ArrayTypeTree,P)
[map-link-232]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitUnionType(com.sun.source.tree.UnionTypeTree,P)
[map-link-233]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitWildcard(com.sun.source.tree.WildcardTree,P)
[map-link-234]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitModifiers(com.sun.source.tree.ModifiersTree,P)
[map-link-235]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitAnnotation(com.sun.source.tree.AnnotationTree,P)
[map-link-236]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitModule(com.sun.source.tree.ModuleTree,P)
[map-link-237]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitExports(com.sun.source.tree.ExportsTree,P)
[map-link-238]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitOpens(com.sun.source.tree.OpensTree,P)
[map-link-239]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitProvides(com.sun.source.tree.ProvidesTree,P)
[map-link-240]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitRequires(com.sun.source.tree.RequiresTree,P)
[map-link-241]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitUses(com.sun.source.tree.UsesTree,P)
[map-link-242]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitOther(com.sun.source.tree.Tree,P)
[map-link-243]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitErroneous(com.sun.source.tree.ErroneousTree,P)
[map-link-244]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/TreeScanner.html#visitYield(com.sun.source.tree.YieldTree,P)
[map-link-245]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html
[map-link-246]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#%3Cinit%3E()
[map-link-247]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#instance(javax.tools.JavaCompiler.CompilationTask)
[map-link-248]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#getSourcePositions()
[map-link-249]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#getTree(javax.lang.model.element.Element)
[map-link-250]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#getTree(javax.lang.model.element.TypeElement)
[map-link-251]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#getTree(javax.lang.model.element.ExecutableElement)
[map-link-252]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#getPath(javax.lang.model.element.Element)
[map-link-253]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#getElement(com.sun.source.util.TreePath)
[map-link-254]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#getTypeMirror(com.sun.source.util.TreePath)
[map-link-255]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#getScope(com.sun.source.util.TreePath)
[map-link-256]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#getDocComment(com.sun.source.util.TreePath)
[map-link-257]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#getOriginalType(javax.lang.model.type.ErrorType)
[map-link-258]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/Trees.html#getLub(com.sun.source.tree.CatchTree)
[map-link-259]: https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/util/package-summary.html
