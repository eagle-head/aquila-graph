package org.aquilagraph.frontend.internal;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/** Projects an attributed enclosing expression to a member receiver without spelling its captures. */
final class ConstructorReceiverProjection {
  private final TypeElement enclosingDeclaration;
  private final TypeElement member;
  private final String methodName;
  private final List<? extends TypeParameterElement> parameters;
  private final TypeMirror result;
  private final ConstructorTypeRenderer renderer;
  private final Elements elements;

  ConstructorReceiverProjection(TypeElement member, DeclaredType created, String name, boolean diamond, Types types, Elements elements) {
    this.member = member;
    if (!(member.getEnclosingElement() instanceof TypeElement enclosing)) {
      throw new IllegalArgumentException("A qualified constructor receiver requires a lexical enclosing type");
    }
    enclosingDeclaration = enclosing;
    methodName = name;
    this.elements = elements;
    var raw = !member.getTypeParameters().isEmpty() && created.getTypeArguments().isEmpty();
    parameters = diamond || raw ? List.of() : member.getTypeParameters();
    var used = new HashSet<String>();
    for (Element scope = member; scope != null; scope = scope.getEnclosingElement()) {
      if (scope instanceof TypeElement type) {
        type.getTypeParameters().forEach(parameter -> used.add(parameter.getSimpleName().toString()));
      } else if (scope instanceof ExecutableElement executable) {
        executable.getTypeParameters().forEach(parameter -> used.add(parameter.getSimpleName().toString()));
      }
    }
    var names = new LinkedHashMap<Element, String>();
    for (var index = 0; index < parameters.size(); index++) {
      var suffix = index;
      while (!used.add(name + "Type" + suffix)) {
        suffix++;
      }
      names.put(parameters.get(index), name + "Type" + suffix);
    }
    renderer = new ConstructorTypeRenderer(elements, names);
    result = raw
        ? types.erasure(member.asType())
        : diamond
            ? types.getDeclaredType((DeclaredType) enclosingDeclaration.asType(), member,
                member.getTypeParameters().stream().map(ignored -> types.getWildcardType(null, null)).toArray(TypeMirror[]::new))
            : member.asType();
  }

  TypeElement owner() {
    return enclosingDeclaration;
  }

  String name() {
    return methodName;
  }

  String sourceMethod() {
    return "\npublic " + renderer.sourceParameters(parameters) + renderer.source(result) + " " + methodName + "() { return null; }\n";
  }

  byte[] adaptBinary(byte[] original) {
    var signature = renderer.signatureParameters(parameters) + "()" + renderer.signature(result);
    var descriptor = MethodTypeDesc.of(ClassDesc.of(elements.getBinaryName(member).toString()));
    var classFile = ClassFile.of();
    return classFile.transformClass(classFile.parse(original),
        ClassTransform.endHandler(builder -> builder.withMethod(methodName, descriptor, ClassFile.ACC_PUBLIC, method -> {
          method.with(SignatureAttribute.of(MethodSignature.parseFrom(signature)));
          method.withCode(code -> code.aconst_null().areturn());
        })));
  }
}
