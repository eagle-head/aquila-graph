# Language model types

[Source map](../README.md)

Edition: Oracle Java SE/JDK 25. This index links public types and declared members to their official API contracts.
It is navigation, not an API transcription or an implementation-coverage result.

## Types and declared members

### ArrayType

[Official contract][map-link-1]

- [`getComponentType()`][map-link-2]

### DeclaredType

[Official contract][map-link-3]

- [`asElement()`][map-link-4]
- [`getEnclosingType()`][map-link-5]
- [`getTypeArguments()`][map-link-6]

### ErrorType

[Official contract][map-link-7]

This page declares no separately documented member detail. Its type contract and inherited surface
remain in the complete page.

### ExecutableType

[Official contract][map-link-8]

- [`getTypeVariables()`][map-link-9]
- [`getReturnType()`][map-link-10]
- [`getParameterTypes()`][map-link-11]
- [`getReceiverType()`][map-link-12]
- [`getThrownTypes()`][map-link-13]

### IntersectionType

[Official contract][map-link-14]

- [`getBounds()`][map-link-15]

### MirroredTypeException

[Official contract][map-link-16]

- [`<init>(javax.lang.model.type.TypeMirror)`][map-link-17]
- [`getTypeMirror()`][map-link-18]

### MirroredTypesException

[Official contract][map-link-19]

- [`<init>(java.util.List)`][map-link-20]
- [`getTypeMirrors()`][map-link-21]

### NoType

[Official contract][map-link-22]

This page declares no separately documented member detail. Its type contract and inherited surface
remain in the complete page.

### NullType

[Official contract][map-link-23]

This page declares no separately documented member detail. Its type contract and inherited surface
remain in the complete page.

### PrimitiveType

[Official contract][map-link-24]

This page declares no separately documented member detail. Its type contract and inherited surface
remain in the complete page.

### ReferenceType

[Official contract][map-link-25]

This page declares no separately documented member detail. Its type contract and inherited surface
remain in the complete page.

### TypeKind

[Official contract][map-link-26]

- [`BOOLEAN`][map-link-27]
- [`BYTE`][map-link-28]
- [`SHORT`][map-link-29]
- [`INT`][map-link-30]
- [`LONG`][map-link-31]
- [`CHAR`][map-link-32]
- [`FLOAT`][map-link-33]
- [`DOUBLE`][map-link-34]
- [`VOID`][map-link-35]
- [`NONE`][map-link-36]
- [`NULL`][map-link-37]
- [`ARRAY`][map-link-38]
- [`DECLARED`][map-link-39]
- [`ERROR`][map-link-40]
- [`TYPEVAR`][map-link-41]
- [`WILDCARD`][map-link-42]
- [`PACKAGE`][map-link-43]
- [`EXECUTABLE`][map-link-44]
- [`OTHER`][map-link-45]
- [`UNION`][map-link-46]
- [`INTERSECTION`][map-link-47]
- [`MODULE`][map-link-48]
- [`values()`][map-link-49]
- [`valueOf(java.lang.String)`][map-link-50]
- [`isPrimitive()`][map-link-51]

### TypeMirror

[Official contract][map-link-52]

- [`getKind()`][map-link-53]
- [`equals(java.lang.Object)`][map-link-54]
- [`hashCode()`][map-link-55]
- [`toString()`][map-link-56]
- [`getAnnotationMirrors()`][map-link-57]
- [`getAnnotation(java.lang.Class)`][map-link-58]
- [`getAnnotationsByType(java.lang.Class)`][map-link-59]
- [`accept(javax.lang.model.type.TypeVisitor,P)`][map-link-60]

### TypeVariable

[Official contract][map-link-61]

- [`asElement()`][map-link-62]
- [`getUpperBound()`][map-link-63]
- [`getLowerBound()`][map-link-64]

### TypeVisitor

[Official contract][map-link-65]

- [`visit(javax.lang.model.type.TypeMirror,P)`][map-link-66]
- [`visit(javax.lang.model.type.TypeMirror)`][map-link-67]
- [`visitPrimitive(javax.lang.model.type.PrimitiveType,P)`][model-types-typevisitor-visitprimitive-javax-lang-model-type-primitivetype-p]
- [`visitNull(javax.lang.model.type.NullType,P)`][map-link-68]
- [`visitArray(javax.lang.model.type.ArrayType,P)`][map-link-69]
- [`visitDeclared(javax.lang.model.type.DeclaredType,P)`][map-link-70]
- [`visitError(javax.lang.model.type.ErrorType,P)`][map-link-71]
-
  [`visitTypeVariable(javax.lang.model.type.TypeVariable,P)`][model-types-typevisitor-visittypevariable-javax-lang-model-type-typevariable-p]
- [`visitWildcard(javax.lang.model.type.WildcardType,P)`][map-link-72]
-
  [`visitExecutable(javax.lang.model.type.ExecutableType,P)`][model-types-typevisitor-visitexecutable-javax-lang-model-type-executabletype-p]
- [`visitNoType(javax.lang.model.type.NoType,P)`][map-link-73]
- [`visitUnknown(javax.lang.model.type.TypeMirror,P)`][map-link-74]
- [`visitUnion(javax.lang.model.type.UnionType,P)`][map-link-75]
-
  [`visitIntersection(javax.lang.model.type.IntersectionType,P)`][model-types-typevisitor-visitintersection-javax-lang-model-type-intersectiontype-p]

### UnionType

[Official contract][map-link-76]

- [`getAlternatives()`][map-link-77]

### UnknownTypeException

[Official contract][map-link-78]

-
  [`<init>(javax.lang.model.type.TypeMirror,java.lang.Object)`][model-types-unknowntypeexception-init-javax-lang-model-type-typemirror-java-lang-object]
- [`getUnknownType()`][map-link-79]
- [`getArgument()`][map-link-80]

### WildcardType

[Official contract][map-link-81]

- [`getExtendsBound()`][map-link-82]
- [`getSuperBound()`][map-link-83]

## References

- [Oracle Java SE 25 package
  specification][map-link-84].

[model-types-typevisitor-visitexecutable-javax-lang-model-type-executabletype-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeVisitor.html#visitExecutable(javax.lang.model.type.ExecutableType,P)
[model-types-typevisitor-visitintersection-javax-lang-model-type-intersectiontype-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeVisitor.html#visitIntersection(javax.lang.model.type.IntersectionType,P)
[model-types-typevisitor-visitprimitive-javax-lang-model-type-primitivetype-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeVisitor.html#visitPrimitive(javax.lang.model.type.PrimitiveType,P)
[model-types-typevisitor-visittypevariable-javax-lang-model-type-typevariable-p]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeVisitor.html#visitTypeVariable(javax.lang.model.type.TypeVariable,P)
[model-types-unknowntypeexception-init-javax-lang-model-type-typemirror-java-lang-object]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/UnknownTypeException.html#%3Cinit%3E(javax.lang.model.type.TypeMirror,java.lang.Object)

[map-link-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/ArrayType.html
[map-link-2]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/ArrayType.html#getComponentType()
[map-link-3]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/DeclaredType.html
[map-link-4]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/DeclaredType.html#asElement()
[map-link-5]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/DeclaredType.html#getEnclosingType()
[map-link-6]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/DeclaredType.html#getTypeArguments()
[map-link-7]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/ErrorType.html
[map-link-8]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/ExecutableType.html
[map-link-9]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/ExecutableType.html#getTypeVariables()
[map-link-10]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/ExecutableType.html#getReturnType()
[map-link-11]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/ExecutableType.html#getParameterTypes()
[map-link-12]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/ExecutableType.html#getReceiverType()
[map-link-13]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/ExecutableType.html#getThrownTypes()
[map-link-14]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/IntersectionType.html
[map-link-15]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/IntersectionType.html#getBounds()
[map-link-16]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/MirroredTypeException.html
[map-link-17]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/MirroredTypeException.html#%3Cinit%3E(javax.lang.model.type.TypeMirror)
[map-link-18]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/MirroredTypeException.html#getTypeMirror()
[map-link-19]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/MirroredTypesException.html
[map-link-20]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/MirroredTypesException.html#%3Cinit%3E(java.util.List)
[map-link-21]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/MirroredTypesException.html#getTypeMirrors()
[map-link-22]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/NoType.html
[map-link-23]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/NullType.html
[map-link-24]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/PrimitiveType.html
[map-link-25]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/ReferenceType.html
[map-link-26]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html
[map-link-27]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#BOOLEAN
[map-link-28]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#BYTE
[map-link-29]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#SHORT
[map-link-30]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#INT
[map-link-31]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#LONG
[map-link-32]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#CHAR
[map-link-33]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#FLOAT
[map-link-34]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#DOUBLE
[map-link-35]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#VOID
[map-link-36]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#NONE
[map-link-37]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#NULL
[map-link-38]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#ARRAY
[map-link-39]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#DECLARED
[map-link-40]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#ERROR
[map-link-41]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#TYPEVAR
[map-link-42]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#WILDCARD
[map-link-43]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#PACKAGE
[map-link-44]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#EXECUTABLE
[map-link-45]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#OTHER
[map-link-46]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#UNION
[map-link-47]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#INTERSECTION
[map-link-48]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#MODULE
[map-link-49]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#values()
[map-link-50]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#valueOf(java.lang.String)
[map-link-51]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeKind.html#isPrimitive()
[map-link-52]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeMirror.html
[map-link-53]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeMirror.html#getKind()
[map-link-54]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeMirror.html#equals(java.lang.Object)
[map-link-55]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeMirror.html#hashCode()
[map-link-56]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeMirror.html#toString()
[map-link-57]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeMirror.html#getAnnotationMirrors()
[map-link-58]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeMirror.html#getAnnotation(java.lang.Class)
[map-link-59]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeMirror.html#getAnnotationsByType(java.lang.Class)
[map-link-60]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeMirror.html#accept(javax.lang.model.type.TypeVisitor,P)
[map-link-61]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeVariable.html
[map-link-62]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeVariable.html#asElement()
[map-link-63]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeVariable.html#getUpperBound()
[map-link-64]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeVariable.html#getLowerBound()
[map-link-65]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeVisitor.html
[map-link-66]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeVisitor.html#visit(javax.lang.model.type.TypeMirror,P)
[map-link-67]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeVisitor.html#visit(javax.lang.model.type.TypeMirror)
[map-link-68]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeVisitor.html#visitNull(javax.lang.model.type.NullType,P)
[map-link-69]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeVisitor.html#visitArray(javax.lang.model.type.ArrayType,P)
[map-link-70]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeVisitor.html#visitDeclared(javax.lang.model.type.DeclaredType,P)
[map-link-71]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeVisitor.html#visitError(javax.lang.model.type.ErrorType,P)
[map-link-72]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeVisitor.html#visitWildcard(javax.lang.model.type.WildcardType,P)
[map-link-73]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeVisitor.html#visitNoType(javax.lang.model.type.NoType,P)
[map-link-74]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeVisitor.html#visitUnknown(javax.lang.model.type.TypeMirror,P)
[map-link-75]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/TypeVisitor.html#visitUnion(javax.lang.model.type.UnionType,P)
[map-link-76]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/UnionType.html
[map-link-77]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/UnionType.html#getAlternatives()
[map-link-78]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/UnknownTypeException.html
[map-link-79]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/UnknownTypeException.html#getUnknownType()
[map-link-80]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/UnknownTypeException.html#getArgument()
[map-link-81]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/WildcardType.html
[map-link-82]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/WildcardType.html#getExtendsBound()
[map-link-83]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/WildcardType.html#getSuperBound()
[map-link-84]: https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/lang/model/type/package-summary.html
