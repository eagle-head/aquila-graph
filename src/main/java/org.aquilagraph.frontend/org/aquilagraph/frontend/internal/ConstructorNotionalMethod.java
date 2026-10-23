package org.aquilagraph.frontend.internal;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.MethodBuilder;
import java.lang.classfile.attribute.ExceptionsAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.lang.classfile.Attributes;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/** Builds a notional method from one compiler-selected constructor declaration. */
final class ConstructorNotionalMethod {
  private static final int SELECTED_DECLARATION_COUNT = 1;
  private final ExecutableElement constructor;
  private final TypeElement owner;
  private final Types types;
  private final Elements elements;
  private final String name;
  private final List<TypeParameterElement> parameters;
  private final ConstructorTypeRenderer renderer;
  private final Set<String> usedParameterNames = new LinkedHashSet<>();
  private final List<TypeMirror> formalTypes;
  private final ConstructorWitnessTypeAnnotations annotations;

  ConstructorNotionalMethod(ExecutableElement constructor, TypeElement owner, Types types, Elements elements, String name, boolean diamond,
      int argumentCount, boolean expanded) {
    this.constructor = Objects.requireNonNull(constructor, "constructor");
    this.owner = Objects.requireNonNull(owner, "owner");
    this.types = Objects.requireNonNull(types, "types");
    this.elements = Objects.requireNonNull(elements, "elements");
    this.name = Objects.requireNonNull(name, "name");
    formalTypes = new ArrayList<>();
    constructor.getParameters().forEach(parameter -> formalTypes.add(parameter.asType()));
    if (expanded) {
      if (!constructor.isVarArgs() || formalTypes.isEmpty() || argumentCount < formalTypes.size() - 1
          || !(formalTypes.getLast() instanceof ArrayType array)) {
        throw new IllegalArgumentException("Expanded inference requires the selected variable declaration and its written arguments");
      }
      var tailCount = argumentCount - formalTypes.size() + 1;
      for (var index = 0; index < tailCount; index++) {
        formalTypes.add(array.getComponentType());
      }
    }
    parameters = new ArrayList<>();
    for (Element scope = constructor; scope != null; scope = scope.getEnclosingElement()) {
      if (scope instanceof TypeElement type) {
        type.getTypeParameters().forEach(parameter -> usedParameterNames.add(parameter.getSimpleName().toString()));
      } else if (scope instanceof ExecutableElement executable) {
        executable.getTypeParameters().forEach(parameter -> usedParameterNames.add(parameter.getSimpleName().toString()));
      }
    }
    var names = new LinkedHashMap<Element, String>();
    if (diamond) {
      addParameters(owner.getTypeParameters(), "ClassType", names);
    }
    addParameters(constructor.getTypeParameters(), "ConstructorType", names);
    renderer = new ConstructorTypeRenderer(elements, names);
    annotations = new ConstructorWitnessTypeAnnotations(diamond ? owner.getTypeParameters().size() : 0, constructor.getParameters().size(),
        formalTypes.size() - constructor.getParameters().size(), expanded);
  }

  private void addParameters(List<? extends TypeParameterElement> values, String prefix, Map<Element, String> names) {
    for (var index = 0; index < values.size(); index++) {
      var value = values.get(index);
      parameters.add(value);
      var suffix = index;
      while (usedParameterNames.contains(prefix + suffix)) {
        suffix++;
      }
      var name = prefix + suffix;
      usedParameterNames.add(name);
      names.put(value, name);
    }
  }

  String sourceMethod() {
    var arguments = new ArrayList<String>();
    for (var index = 0; index < formalTypes.size(); index++) {
      var type = renderer.source(formalTypes.get(index));
      arguments.add(type + " argument" + index);
    }
    var thrown = constructor.getThrownTypes();
    return "\npublic " + renderer.sourceParameters(parameters) + renderer.source(owner.asType()) + " " + name + "("
        + String.join(",", arguments) + ")"
        + (thrown.isEmpty() ? "" : thrown.stream().map(renderer::source).collect(Collectors.joining(",", " throws ", "")))
        + " { return null; }\n";
  }

  byte[] adaptBinary(byte[] original) {
    var classFile = ClassFile.of();
    var model = classFile.parse(original);
    var descriptor = constructor.getParameters().stream().map(parameter -> renderer.signature(types.erasure(parameter.asType())))
        .collect(Collectors.joining("", "(", ")V"));
    var binaryDescriptor = owner.getNestingKind() == NestingKind.MEMBER && !owner.getModifiers().contains(Modifier.STATIC)
        ? "(" + renderer.signature(types.erasure(Objects.requireNonNull(owner.getEnclosingElement(), "enclosing owner").asType()))
            + descriptor.substring(1)
        : descriptor;
    var matching = model.methods().stream()
        .filter(method -> method.methodName().equalsString("<init>") && method.methodType().equalsString(binaryDescriptor)).toList();
    if (matching.size() != SELECTED_DECLARATION_COUNT) {
      throw new IllegalArgumentException("The selected constructor requires one matching binary declaration");
    }
    var ownerDescriptor = ClassDesc.of(elements.getBinaryName(owner).toString());
    var methodDescriptor = MethodTypeDesc
        .ofDescriptor(formalTypes.stream().map(type -> renderer.signature(types.erasure(type))).collect(Collectors.joining("", "(", ")V")))
        .changeReturnType(ownerDescriptor);
    var signature = renderer.signatureParameters(parameters)
        + formalTypes.stream().map(renderer::signature).collect(Collectors.joining("", "(", ")")) + renderer.signature(owner.asType())
        + constructor.getThrownTypes().stream().map(type -> "^" + renderer.signature(type)).collect(Collectors.joining());
    var flags = ClassFile.ACC_PUBLIC;
    return classFile.transformClass(model,
        ClassTransform.endHandler(builder -> builder.withMethod(name, methodDescriptor, flags, method -> {
          method.with(SignatureAttribute.of(MethodSignature.parseFrom(signature)));
          addAnnotations(model, matching.getFirst(), method);
          for (var attribute : matching.getFirst().attributes()) {
            if (attribute instanceof ExceptionsAttribute exceptions) {
              method.with(exceptions);
            }
          }
          method.withCode(code -> code.aconst_null().areturn());
        })));
  }

  private void addAnnotations(ClassModel ownerModel, MethodModel selectedModel, MethodBuilder builder) {
    var visible = annotations.select(
        ownerModel.findAttribute(Attributes.runtimeVisibleTypeAnnotations()).map(RuntimeVisibleTypeAnnotationsAttribute::annotations)
            .orElseGet(List::of),
        selectedModel.findAttribute(Attributes.runtimeVisibleTypeAnnotations()).map(RuntimeVisibleTypeAnnotationsAttribute::annotations)
            .orElseGet(List::of));
    if (!visible.isEmpty()) {
      builder.with(RuntimeVisibleTypeAnnotationsAttribute.of(visible));
    }
    var invisible = annotations.select(
        ownerModel.findAttribute(Attributes.runtimeInvisibleTypeAnnotations()).map(RuntimeInvisibleTypeAnnotationsAttribute::annotations)
            .orElseGet(List::of),
        selectedModel.findAttribute(Attributes.runtimeInvisibleTypeAnnotations()).map(RuntimeInvisibleTypeAnnotationsAttribute::annotations)
            .orElseGet(List::of));
    if (!invisible.isEmpty()) {
      builder.with(RuntimeInvisibleTypeAnnotationsAttribute.of(invisible));
    }
  }

}
