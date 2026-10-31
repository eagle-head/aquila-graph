# Language model elements

[Source map](../README.md)

Edition: Oracle Java SE/JDK 25. This index links public types and declared members to their official API contracts.
It is navigation, not an API transcription or an implementation-coverage result.

## Types and declared members

### AnnotationMirror

[Official contract][map-link-1]

- [`getAnnotationType()`][map-link-2]
- [`getElementValues()`][map-link-3]

### AnnotationValue

[Official contract][map-link-4]

- [`getValue()`][map-link-5]
- [`toString()`][map-link-6]
-
  [`accept(javax.lang.model.element.AnnotationValueVisitor,P)`][model-elements-annotationvalue-accept-javax-lang-model-element-annotationvaluevisitor-p]

### AnnotationValueVisitor

[Official contract][map-link-7]

-
  [`visit(javax.lang.model.element.AnnotationValue,P)`][model-elements-annotationvaluevisitor-visit-javax-lang-model-element-annotationvalue-p]
- [`visit(javax.lang.model.element.AnnotationValue)`][model-elements-annotationvaluevisitor-visit-javax-lang-model-element-annotationvalue]
- [`visitBoolean(boolean,P)`][map-link-8]
- [`visitByte(byte,P)`][map-link-9]
- [`visitChar(char,P)`][map-link-10]
- [`visitDouble(double,P)`][map-link-11]
- [`visitFloat(float,P)`][map-link-12]
- [`visitInt(int,P)`][map-link-13]
- [`visitLong(long,P)`][map-link-14]
- [`visitShort(short,P)`][map-link-15]
- [`visitString(java.lang.String,P)`][map-link-16]
- [`visitType(javax.lang.model.type.TypeMirror,P)`][map-link-17]
-
  [`visitEnumConstant(javax.lang.model.element.VariableElement,P)`][model-elements-annotationvaluevisitor-visitenumconstant-javax-lang-model-element-variableelement-p]
-
  [`visitAnnotation(javax.lang.model.element.AnnotationMirror,P)`][model-elements-annotationvaluevisitor-visitannotation-javax-lang-model-element-annotationmirror-p]
- [`visitArray(java.util.List,P)`][map-link-18]
-
  [`visitUnknown(javax.lang.model.element.AnnotationValue,P)`][model-elements-annotationvaluevisitor-visitunknown-javax-lang-model-element-annotationvalue-p]

### Element

[Official contract][map-link-19]

- [`asType()`][map-link-20]
- [`getKind()`][map-link-21]
- [`getModifiers()`][map-link-22]
- [`getSimpleName()`][map-link-23]
- [`getEnclosingElement()`][map-link-24]
- [`getEnclosedElements()`][map-link-25]
- [`equals(java.lang.Object)`][map-link-26]
- [`hashCode()`][map-link-27]
- [`getAnnotationMirrors()`][map-link-28]
- [`getAnnotation(java.lang.Class)`][map-link-29]
- [`getAnnotationsByType(java.lang.Class)`][map-link-30]
- [`accept(javax.lang.model.element.ElementVisitor,P)`][map-link-31]

### ElementKind

[Official contract][map-link-32]

- [`PACKAGE`][map-link-33]
- [`ENUM`][map-link-34]
- [`CLASS`][map-link-35]
- [`ANNOTATION_TYPE`][map-link-36]
- [`INTERFACE`][map-link-37]
- [`ENUM_CONSTANT`][map-link-38]
- [`FIELD`][map-link-39]
- [`PARAMETER`][map-link-40]
- [`LOCAL_VARIABLE`][map-link-41]
- [`EXCEPTION_PARAMETER`][map-link-42]
- [`METHOD`][map-link-43]
- [`CONSTRUCTOR`][map-link-44]
- [`STATIC_INIT`][map-link-45]
- [`INSTANCE_INIT`][map-link-46]
- [`TYPE_PARAMETER`][map-link-47]
- [`OTHER`][map-link-48]
- [`RESOURCE_VARIABLE`][map-link-49]
- [`MODULE`][map-link-50]
- [`RECORD`][map-link-51]
- [`RECORD_COMPONENT`][map-link-52]
- [`BINDING_VARIABLE`][map-link-53]
- [`values()`][map-link-54]
- [`valueOf(java.lang.String)`][map-link-55]
- [`isClass()`][map-link-56]
- [`isInterface()`][map-link-57]
- [`isDeclaredType()`][map-link-58]
- [`isField()`][map-link-59]
- [`isExecutable()`][map-link-60]
- [`isInitializer()`][map-link-61]
- [`isVariable()`][map-link-62]

### ElementVisitor

[Official contract][map-link-63]

- [`visit(javax.lang.model.element.Element,P)`][map-link-64]
- [`visit(javax.lang.model.element.Element)`][map-link-65]
-
  [`visitPackage(javax.lang.model.element.PackageElement,P)`][model-elements-elementvisitor-visitpackage-javax-lang-model-element-packageelement-p]
- [`visitType(javax.lang.model.element.TypeElement,P)`][map-link-66]
-
  [`visitVariable(javax.lang.model.element.VariableElement,P)`][model-elements-elementvisitor-visitvariable-javax-lang-model-element-variableelement-p]
-
  [`visitExecutable(javax.lang.model.element.ExecutableElement,P)`][model-elements-elementvisitor-visitexecutable-javax-lang-model-element-executableelement-p]
-
  [`visitTypeParameter(javax.lang.model.element.TypeParameterElement,P)`][model-elements-elementvisitor-visittypeparameter-javax-lang-model-element-typeparameterelement-p]
- [`visitUnknown(javax.lang.model.element.Element,P)`][map-link-67]
-
  [`visitModule(javax.lang.model.element.ModuleElement,P)`][model-elements-elementvisitor-visitmodule-javax-lang-model-element-moduleelement-p]
-
  [`visitRecordComponent(javax.lang.model.element.RecordComponentElement,P)`][model-elements-elementvisitor-visitrecordcomponent-javax-lang-model-element-recordcomponentelement-p]

### ExecutableElement

[Official contract][map-link-68]

- [`asType()`][map-link-69]
- [`getTypeParameters()`][map-link-70]
- [`getReturnType()`][map-link-71]
- [`getParameters()`][map-link-72]
- [`getReceiverType()`][map-link-73]
- [`isVarArgs()`][map-link-74]
- [`isDefault()`][map-link-75]
- [`getThrownTypes()`][map-link-76]
- [`getDefaultValue()`][map-link-77]
- [`getEnclosingElement()`][map-link-78]
- [`getSimpleName()`][map-link-79]

### Modifier

[Official contract][map-link-80]

- [`PUBLIC`][map-link-81]
- [`PROTECTED`][map-link-82]
- [`PRIVATE`][map-link-83]
- [`ABSTRACT`][map-link-84]
- [`DEFAULT`][map-link-85]
- [`STATIC`][map-link-86]
- [`SEALED`][map-link-87]
- [`NON_SEALED`][map-link-88]
- [`FINAL`][map-link-89]
- [`TRANSIENT`][map-link-90]
- [`VOLATILE`][map-link-91]
- [`SYNCHRONIZED`][map-link-92]
- [`NATIVE`][map-link-93]
- [`STRICTFP`][map-link-94]
- [`values()`][map-link-95]
- [`valueOf(java.lang.String)`][map-link-96]
- [`toString()`][map-link-97]

### ModuleElement.Directive

[Official contract][map-link-98]

- [`getKind()`][map-link-99]
-
  [`accept(javax.lang.model.element.ModuleElement.DirectiveVisitor,P)`][model-elements-moduleelement-directive-accept-javax-lang-model-element-moduleelement-directivevisitor-p]

### ModuleElement.DirectiveKind

[Official contract][map-link-100]

- [`REQUIRES`][map-link-101]
- [`EXPORTS`][map-link-102]
- [`OPENS`][map-link-103]
- [`USES`][map-link-104]
- [`PROVIDES`][map-link-105]
- [`values()`][map-link-106]
- [`valueOf(java.lang.String)`][map-link-107]

### ModuleElement.DirectiveVisitor

[Official contract][map-link-108]

-
  [`visit(javax.lang.model.element.ModuleElement.Directive)`][model-elements-moduleelement-directivevisitor-visit-javax-lang-model-element-moduleelement-directive]
-
  [`visit(javax.lang.model.element.ModuleElement.Directive,P)`][model-elements-moduleelement-directivevisitor-visit-javax-lang-model-element-moduleelement-directive-p]
-
  [`visitRequires(javax.lang.model.element.ModuleElement.RequiresDirective,P)`][model-elements-moduleelement-directivevisitor-visitrequires-javax-lang-model-element-moduleelement-requiresdirective-p]
-
  [`visitExports(javax.lang.model.element.ModuleElement.ExportsDirective,P)`][model-elements-moduleelement-directivevisitor-visitexports-javax-lang-model-element-moduleelement-exportsdirective-p]
-
  [`visitOpens(javax.lang.model.element.ModuleElement.OpensDirective,P)`][model-elements-moduleelement-directivevisitor-visitopens-javax-lang-model-element-moduleelement-opensdirective-p]
-
  [`visitUses(javax.lang.model.element.ModuleElement.UsesDirective,P)`][model-elements-moduleelement-directivevisitor-visituses-javax-lang-model-element-moduleelement-usesdirective-p]
-
  [`visitProvides(javax.lang.model.element.ModuleElement.ProvidesDirective,P)`][model-elements-moduleelement-directivevisitor-visitprovides-javax-lang-model-element-moduleelement-providesdirective-p]
-
  [`visitUnknown(javax.lang.model.element.ModuleElement.Directive,P)`][model-elements-moduleelement-directivevisitor-visitunknown-javax-lang-model-element-moduleelement-directive-p]

### ModuleElement.ExportsDirective

[Official contract][map-link-109]

- [`getPackage()`][map-link-110]
- [`getTargetModules()`][map-link-111]

### ModuleElement.OpensDirective

[Official contract][map-link-112]

- [`getPackage()`][map-link-113]
- [`getTargetModules()`][map-link-114]

### ModuleElement.ProvidesDirective

[Official contract][map-link-115]

- [`getService()`][map-link-116]
- [`getImplementations()`][map-link-117]

### ModuleElement.RequiresDirective

[Official contract][map-link-118]

- [`isStatic()`][map-link-119]
- [`isTransitive()`][map-link-120]
- [`getDependency()`][map-link-121]

### ModuleElement.UsesDirective

[Official contract][map-link-122]

- [`getService()`][map-link-123]

### ModuleElement

[Official contract][map-link-124]

- [`asType()`][map-link-125]
- [`getQualifiedName()`][map-link-126]
- [`getSimpleName()`][map-link-127]
- [`getEnclosedElements()`][map-link-128]
- [`isOpen()`][map-link-129]
- [`isUnnamed()`][map-link-130]
- [`getEnclosingElement()`][map-link-131]
- [`getDirectives()`][map-link-132]

### Name

[Official contract][map-link-133]

- [`equals(java.lang.Object)`][map-link-134]
- [`hashCode()`][map-link-135]
- [`contentEquals(java.lang.CharSequence)`][map-link-136]

### NestingKind

[Official contract][map-link-137]

- [`TOP_LEVEL`][map-link-138]
- [`MEMBER`][map-link-139]
- [`LOCAL`][map-link-140]
- [`ANONYMOUS`][map-link-141]
- [`values()`][map-link-142]
- [`valueOf(java.lang.String)`][map-link-143]
- [`isNested()`][map-link-144]

### PackageElement

[Official contract][map-link-145]

- [`asType()`][map-link-146]
- [`getQualifiedName()`][map-link-147]
- [`getSimpleName()`][map-link-148]
- [`getEnclosedElements()`][map-link-149]
- [`isUnnamed()`][map-link-150]
- [`getEnclosingElement()`][map-link-151]

### Parameterizable

[Official contract][map-link-152]

- [`getTypeParameters()`][map-link-153]

### QualifiedNameable

[Official contract][map-link-154]

- [`getQualifiedName()`][map-link-155]

### RecordComponentElement

[Official contract][map-link-156]

- [`asType()`][map-link-157]
- [`getEnclosingElement()`][map-link-158]
- [`getSimpleName()`][map-link-159]
- [`getAccessor()`][map-link-160]

### TypeElement

[Official contract][map-link-161]

- [`asType()`][map-link-162]
- [`getEnclosedElements()`][map-link-163]
- [`getNestingKind()`][map-link-164]
- [`getQualifiedName()`][map-link-165]
- [`getSimpleName()`][map-link-166]
- [`getSuperclass()`][map-link-167]
- [`getInterfaces()`][map-link-168]
- [`getTypeParameters()`][map-link-169]
- [`getRecordComponents()`][map-link-170]
- [`getPermittedSubclasses()`][map-link-171]
- [`getEnclosingElement()`][map-link-172]

### TypeParameterElement

[Official contract][map-link-173]

- [`asType()`][map-link-174]
- [`getGenericElement()`][map-link-175]
- [`getBounds()`][map-link-176]
- [`getEnclosingElement()`][map-link-177]

### UnknownAnnotationValueException

[Official contract][map-link-178]

-
  [`<init>(javax.lang.model.element.AnnotationValue,java.lang.Object)`][model-elements-unknownannotationvalueexception-init-javax-lang-model-element-annotationvalue-java-lang-object]
- [`getUnknownAnnotationValue()`][map-link-179]
- [`getArgument()`][map-link-180]

### UnknownDirectiveException

[Official contract][map-link-181]

-
  [`<init>(javax.lang.model.element.ModuleElement.Directive,java.lang.Object)`][model-elements-unknowndirectiveexception-init-javax-lang-model-element-moduleelement-directive-java-lang-object]
- [`getUnknownDirective()`][map-link-182]
- [`getArgument()`][map-link-183]

### UnknownElementException

[Official contract][map-link-184]

-
  [`<init>(javax.lang.model.element.Element,java.lang.Object)`][model-elements-unknownelementexception-init-javax-lang-model-element-element-java-lang-object]
- [`getUnknownElement()`][map-link-185]
- [`getArgument()`][map-link-186]

### VariableElement

[Official contract][map-link-187]

- [`asType()`][map-link-188]
- [`getConstantValue()`][map-link-189]
- [`getSimpleName()`][map-link-190]
- [`getEnclosingElement()`][map-link-191]
- [`isUnnamed()`][map-link-192]

## References

- [Oracle Java SE 25 package
  specification][map-link-193].

[model-elements-annotationvalue-accept-javax-lang-model-element-annotationvaluevisitor-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValue.html#accept(javax.lang.model.element.AnnotationValueVisitor,P)
[model-elements-annotationvaluevisitor-visit-javax-lang-model-element-annotationvalue]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValueVisitor.html#visit(javax.lang.model.element.AnnotationValue)
[model-elements-annotationvaluevisitor-visit-javax-lang-model-element-annotationvalue-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValueVisitor.html#visit(javax.lang.model.element.AnnotationValue,P)
[model-elements-annotationvaluevisitor-visitannotation-javax-lang-model-element-annotationmirror-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValueVisitor.html#visitAnnotation(javax.lang.model.element.AnnotationMirror,P)
[model-elements-annotationvaluevisitor-visitenumconstant-javax-lang-model-element-variableelement-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValueVisitor.html#visitEnumConstant(javax.lang.model.element.VariableElement,P)
[model-elements-annotationvaluevisitor-visitunknown-javax-lang-model-element-annotationvalue-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValueVisitor.html#visitUnknown(javax.lang.model.element.AnnotationValue,P)
[model-elements-elementvisitor-visitexecutable-javax-lang-model-element-executableelement-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementVisitor.html#visitExecutable(javax.lang.model.element.ExecutableElement,P)
[model-elements-elementvisitor-visitmodule-javax-lang-model-element-moduleelement-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementVisitor.html#visitModule(javax.lang.model.element.ModuleElement,P)
[model-elements-elementvisitor-visitpackage-javax-lang-model-element-packageelement-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementVisitor.html#visitPackage(javax.lang.model.element.PackageElement,P)
[model-elements-elementvisitor-visitrecordcomponent-javax-lang-model-element-recordcomponentelement-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementVisitor.html#visitRecordComponent(javax.lang.model.element.RecordComponentElement,P)
[model-elements-elementvisitor-visittypeparameter-javax-lang-model-element-typeparameterelement-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementVisitor.html#visitTypeParameter(javax.lang.model.element.TypeParameterElement,P)
[model-elements-elementvisitor-visitvariable-javax-lang-model-element-variableelement-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementVisitor.html#visitVariable(javax.lang.model.element.VariableElement,P)
[model-elements-moduleelement-directive-accept-javax-lang-model-element-moduleelement-directivevisitor-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.Directive.html#accept(javax.lang.model.element.ModuleElement.DirectiveVisitor,P)
[model-elements-moduleelement-directivevisitor-visit-javax-lang-model-element-moduleelement-directive]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.DirectiveVisitor.html#visit(javax.lang.model.element.ModuleElement.Directive)
[model-elements-moduleelement-directivevisitor-visit-javax-lang-model-element-moduleelement-directive-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.DirectiveVisitor.html#visit(javax.lang.model.element.ModuleElement.Directive,P)
[model-elements-moduleelement-directivevisitor-visitexports-javax-lang-model-element-moduleelement-exportsdirective-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.DirectiveVisitor.html#visitExports(javax.lang.model.element.ModuleElement.ExportsDirective,P)
[model-elements-moduleelement-directivevisitor-visitopens-javax-lang-model-element-moduleelement-opensdirective-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.DirectiveVisitor.html#visitOpens(javax.lang.model.element.ModuleElement.OpensDirective,P)
[model-elements-moduleelement-directivevisitor-visitprovides-javax-lang-model-element-moduleelement-providesdirective-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.DirectiveVisitor.html#visitProvides(javax.lang.model.element.ModuleElement.ProvidesDirective,P)
[model-elements-moduleelement-directivevisitor-visitrequires-javax-lang-model-element-moduleelement-requiresdirective-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.DirectiveVisitor.html#visitRequires(javax.lang.model.element.ModuleElement.RequiresDirective,P)
[model-elements-moduleelement-directivevisitor-visitunknown-javax-lang-model-element-moduleelement-directive-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.DirectiveVisitor.html#visitUnknown(javax.lang.model.element.ModuleElement.Directive,P)
[model-elements-moduleelement-directivevisitor-visituses-javax-lang-model-element-moduleelement-usesdirective-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.DirectiveVisitor.html#visitUses(javax.lang.model.element.ModuleElement.UsesDirective,P)
[model-elements-unknownannotationvalueexception-init-javax-lang-model-element-annotationvalue-java-lang-object]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/UnknownAnnotationValueException.html#%3Cinit%3E(javax.lang.model.element.AnnotationValue,java.lang.Object)
[model-elements-unknowndirectiveexception-init-javax-lang-model-element-moduleelement-directive-java-lang-object]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/UnknownDirectiveException.html#%3Cinit%3E(javax.lang.model.element.ModuleElement.Directive,java.lang.Object)
[model-elements-unknownelementexception-init-javax-lang-model-element-element-java-lang-object]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/UnknownElementException.html#%3Cinit%3E(javax.lang.model.element.Element,java.lang.Object)

[map-link-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationMirror.html
[map-link-2]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationMirror.html#getAnnotationType()
[map-link-3]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationMirror.html#getElementValues()
[map-link-4]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValue.html
[map-link-5]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValue.html#getValue()
[map-link-6]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValue.html#toString()
[map-link-7]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValueVisitor.html
[map-link-8]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValueVisitor.html#visitBoolean(boolean,P)
[map-link-9]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValueVisitor.html#visitByte(byte,P)
[map-link-10]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValueVisitor.html#visitChar(char,P)
[map-link-11]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValueVisitor.html#visitDouble(double,P)
[map-link-12]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValueVisitor.html#visitFloat(float,P)
[map-link-13]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValueVisitor.html#visitInt(int,P)
[map-link-14]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValueVisitor.html#visitLong(long,P)
[map-link-15]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValueVisitor.html#visitShort(short,P)
[map-link-16]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValueVisitor.html#visitString(java.lang.String,P)
[map-link-17]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValueVisitor.html#visitType(javax.lang.model.type.TypeMirror,P)
[map-link-18]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/AnnotationValueVisitor.html#visitArray(java.util.List,P)
[map-link-19]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Element.html
[map-link-20]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Element.html#asType()
[map-link-21]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Element.html#getKind()
[map-link-22]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Element.html#getModifiers()
[map-link-23]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Element.html#getSimpleName()
[map-link-24]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Element.html#getEnclosingElement()
[map-link-25]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Element.html#getEnclosedElements()
[map-link-26]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Element.html#equals(java.lang.Object)
[map-link-27]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Element.html#hashCode()
[map-link-28]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Element.html#getAnnotationMirrors()
[map-link-29]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Element.html#getAnnotation(java.lang.Class)
[map-link-30]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Element.html#getAnnotationsByType(java.lang.Class)
[map-link-31]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Element.html#accept(javax.lang.model.element.ElementVisitor,P)
[map-link-32]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html
[map-link-33]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#PACKAGE
[map-link-34]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#ENUM
[map-link-35]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#CLASS
[map-link-36]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#ANNOTATION_TYPE
[map-link-37]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#INTERFACE
[map-link-38]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#ENUM_CONSTANT
[map-link-39]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#FIELD
[map-link-40]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#PARAMETER
[map-link-41]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#LOCAL_VARIABLE
[map-link-42]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#EXCEPTION_PARAMETER
[map-link-43]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#METHOD
[map-link-44]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#CONSTRUCTOR
[map-link-45]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#STATIC_INIT
[map-link-46]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#INSTANCE_INIT
[map-link-47]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#TYPE_PARAMETER
[map-link-48]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#OTHER
[map-link-49]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#RESOURCE_VARIABLE
[map-link-50]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#MODULE
[map-link-51]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#RECORD
[map-link-52]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#RECORD_COMPONENT
[map-link-53]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#BINDING_VARIABLE
[map-link-54]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#values()
[map-link-55]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#valueOf(java.lang.String)
[map-link-56]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#isClass()
[map-link-57]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#isInterface()
[map-link-58]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#isDeclaredType()
[map-link-59]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#isField()
[map-link-60]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#isExecutable()
[map-link-61]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#isInitializer()
[map-link-62]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementKind.html#isVariable()
[map-link-63]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementVisitor.html
[map-link-64]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementVisitor.html#visit(javax.lang.model.element.Element,P)
[map-link-65]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementVisitor.html#visit(javax.lang.model.element.Element)
[map-link-66]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementVisitor.html#visitType(javax.lang.model.element.TypeElement,P)
[map-link-67]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ElementVisitor.html#visitUnknown(javax.lang.model.element.Element,P)
[map-link-68]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ExecutableElement.html
[map-link-69]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ExecutableElement.html#asType()
[map-link-70]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ExecutableElement.html#getTypeParameters()
[map-link-71]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ExecutableElement.html#getReturnType()
[map-link-72]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ExecutableElement.html#getParameters()
[map-link-73]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ExecutableElement.html#getReceiverType()
[map-link-74]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ExecutableElement.html#isVarArgs()
[map-link-75]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ExecutableElement.html#isDefault()
[map-link-76]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ExecutableElement.html#getThrownTypes()
[map-link-77]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ExecutableElement.html#getDefaultValue()
[map-link-78]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ExecutableElement.html#getEnclosingElement()
[map-link-79]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ExecutableElement.html#getSimpleName()
[map-link-80]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Modifier.html
[map-link-81]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Modifier.html#PUBLIC
[map-link-82]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Modifier.html#PROTECTED
[map-link-83]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Modifier.html#PRIVATE
[map-link-84]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Modifier.html#ABSTRACT
[map-link-85]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Modifier.html#DEFAULT
[map-link-86]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Modifier.html#STATIC
[map-link-87]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Modifier.html#SEALED
[map-link-88]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Modifier.html#NON_SEALED
[map-link-89]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Modifier.html#FINAL
[map-link-90]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Modifier.html#TRANSIENT
[map-link-91]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Modifier.html#VOLATILE
[map-link-92]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Modifier.html#SYNCHRONIZED
[map-link-93]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Modifier.html#NATIVE
[map-link-94]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Modifier.html#STRICTFP
[map-link-95]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Modifier.html#values()
[map-link-96]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Modifier.html#valueOf(java.lang.String)
[map-link-97]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Modifier.html#toString()
[map-link-98]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.Directive.html
[map-link-99]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.Directive.html#getKind()
[map-link-100]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.DirectiveKind.html
[map-link-101]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.DirectiveKind.html#REQUIRES
[map-link-102]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.DirectiveKind.html#EXPORTS
[map-link-103]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.DirectiveKind.html#OPENS
[map-link-104]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.DirectiveKind.html#USES
[map-link-105]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.DirectiveKind.html#PROVIDES
[map-link-106]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.DirectiveKind.html#values()
[map-link-107]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.DirectiveKind.html#valueOf(java.lang.String)
[map-link-108]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.DirectiveVisitor.html
[map-link-109]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.ExportsDirective.html
[map-link-110]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.ExportsDirective.html#getPackage()
[map-link-111]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.ExportsDirective.html#getTargetModules()
[map-link-112]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.OpensDirective.html
[map-link-113]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.OpensDirective.html#getPackage()
[map-link-114]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.OpensDirective.html#getTargetModules()
[map-link-115]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.ProvidesDirective.html
[map-link-116]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.ProvidesDirective.html#getService()
[map-link-117]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.ProvidesDirective.html#getImplementations()
[map-link-118]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.RequiresDirective.html
[map-link-119]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.RequiresDirective.html#isStatic()
[map-link-120]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.RequiresDirective.html#isTransitive()
[map-link-121]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.RequiresDirective.html#getDependency()
[map-link-122]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.UsesDirective.html
[map-link-123]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.UsesDirective.html#getService()
[map-link-124]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.html
[map-link-125]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.html#asType()
[map-link-126]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.html#getQualifiedName()
[map-link-127]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.html#getSimpleName()
[map-link-128]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.html#getEnclosedElements()
[map-link-129]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.html#isOpen()
[map-link-130]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.html#isUnnamed()
[map-link-131]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.html#getEnclosingElement()
[map-link-132]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/ModuleElement.html#getDirectives()
[map-link-133]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Name.html
[map-link-134]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Name.html#equals(java.lang.Object)
[map-link-135]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Name.html#hashCode()
[map-link-136]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Name.html#contentEquals(java.lang.CharSequence)
[map-link-137]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/NestingKind.html
[map-link-138]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/NestingKind.html#TOP_LEVEL
[map-link-139]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/NestingKind.html#MEMBER
[map-link-140]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/NestingKind.html#LOCAL
[map-link-141]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/NestingKind.html#ANONYMOUS
[map-link-142]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/NestingKind.html#values()
[map-link-143]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/NestingKind.html#valueOf(java.lang.String)
[map-link-144]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/NestingKind.html#isNested()
[map-link-145]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/PackageElement.html
[map-link-146]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/PackageElement.html#asType()
[map-link-147]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/PackageElement.html#getQualifiedName()
[map-link-148]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/PackageElement.html#getSimpleName()
[map-link-149]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/PackageElement.html#getEnclosedElements()
[map-link-150]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/PackageElement.html#isUnnamed()
[map-link-151]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/PackageElement.html#getEnclosingElement()
[map-link-152]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Parameterizable.html
[map-link-153]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/Parameterizable.html#getTypeParameters()
[map-link-154]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/QualifiedNameable.html
[map-link-155]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/QualifiedNameable.html#getQualifiedName()
[map-link-156]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/RecordComponentElement.html
[map-link-157]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/RecordComponentElement.html#asType()
[map-link-158]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/RecordComponentElement.html#getEnclosingElement()
[map-link-159]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/RecordComponentElement.html#getSimpleName()
[map-link-160]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/RecordComponentElement.html#getAccessor()
[map-link-161]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/TypeElement.html
[map-link-162]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/TypeElement.html#asType()
[map-link-163]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/TypeElement.html#getEnclosedElements()
[map-link-164]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/TypeElement.html#getNestingKind()
[map-link-165]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/TypeElement.html#getQualifiedName()
[map-link-166]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/TypeElement.html#getSimpleName()
[map-link-167]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/TypeElement.html#getSuperclass()
[map-link-168]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/TypeElement.html#getInterfaces()
[map-link-169]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/TypeElement.html#getTypeParameters()
[map-link-170]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/TypeElement.html#getRecordComponents()
[map-link-171]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/TypeElement.html#getPermittedSubclasses()
[map-link-172]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/TypeElement.html#getEnclosingElement()
[map-link-173]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/TypeParameterElement.html
[map-link-174]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/TypeParameterElement.html#asType()
[map-link-175]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/TypeParameterElement.html#getGenericElement()
[map-link-176]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/TypeParameterElement.html#getBounds()
[map-link-177]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/TypeParameterElement.html#getEnclosingElement()
[map-link-178]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/UnknownAnnotationValueException.html
[map-link-179]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/UnknownAnnotationValueException.html#getUnknownAnnotationValue()
[map-link-180]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/UnknownAnnotationValueException.html#getArgument()
[map-link-181]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/UnknownDirectiveException.html
[map-link-182]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/UnknownDirectiveException.html#getUnknownDirective()
[map-link-183]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/UnknownDirectiveException.html#getArgument()
[map-link-184]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/UnknownElementException.html
[map-link-185]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/UnknownElementException.html#getUnknownElement()
[map-link-186]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/UnknownElementException.html#getArgument()
[map-link-187]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/VariableElement.html
[map-link-188]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/VariableElement.html#asType()
[map-link-189]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/VariableElement.html#getConstantValue()
[map-link-190]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/VariableElement.html#getSimpleName()
[map-link-191]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/VariableElement.html#getEnclosingElement()
[map-link-192]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/VariableElement.html#isUnnamed()
[map-link-193]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/element/package-summary.html
