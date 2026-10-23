package org.aquilagraph.architecture;

import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.Signature;
import java.lang.classfile.Signature.ArrayTypeSig;
import java.lang.classfile.Signature.ClassTypeSig;
import java.lang.classfile.Signature.TypeArg.Bounded;
import java.lang.classfile.Signature.TypeParam;
import java.lang.classfile.attribute.AnnotationDefaultAttribute;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.ExceptionsAttribute;
import java.lang.classfile.attribute.LocalVariableTableAttribute;
import java.lang.classfile.attribute.LocalVariableTypeTableAttribute;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.MethodTypeEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ClassfileDependencies {
  private final Set<String> types = new LinkedHashSet<>();
  private final Set<String> annotations = new LinkedHashSet<>();
  private final boolean includeCode;

  private ClassfileDependencies(boolean includeCode) {
    this.includeCode = includeCode;
  }

  public record References(Set<String> types, Set<String> annotations) {
    public References {
      types = Set.copyOf(types);
      annotations = Set.copyOf(annotations);
    }
  }

  public static References read(byte[] bytes) {
    var reader = new ClassfileDependencies(true);
    var model = ClassFile.of().parse(bytes);
    for (var entry : model.constantPool()) {
      switch (entry) {
        case ClassEntry type -> {
          if (!model.isModuleInfo() || !type.asInternalName().equals("module-info")) {
            reader.type(type.asSymbol());
          }
        }
        case NameAndTypeEntry member -> reader.descriptor(member.type().stringValue());
        case MethodTypeEntry method -> reader.descriptor(method.descriptor().stringValue());
        default -> { }
      }
    }
    reader.attributes(model);
    for (var field : model.fields()) {
      reader.descriptor(field.fieldType().stringValue());
      reader.attributes(field);
    }
    for (var method : model.methods()) {
      reader.descriptor(method.methodType().stringValue());
      reader.attributes(method);
    }
    return new References(reader.types, reader.annotations);
  }

  public static References publicSignatures(byte[] bytes) {
    var reader = new ClassfileDependencies(false);
    var model = ClassFile.of().parse(bytes);
    model.superclass().ifPresent(type -> reader.type(type.asSymbol()));
    model.interfaces().forEach(type -> reader.type(type.asSymbol()));
    reader.attributes(model);
    for (var field : model.fields()) {
      if (field.flags().has(AccessFlag.PUBLIC) || field.flags().has(AccessFlag.PROTECTED)) {
        reader.descriptor(field.fieldType().stringValue());
        reader.attributes(field);
      }
    }
    for (var method : model.methods()) {
      if (method.flags().has(AccessFlag.PUBLIC) || method.flags().has(AccessFlag.PROTECTED)) {
        reader.descriptor(method.methodType().stringValue());
        reader.attributes(method);
      }
    }
    return new References(reader.types, reader.annotations);
  }

  private void descriptor(String descriptor) {
    if (descriptor.startsWith("(")) {
      var method = MethodTypeDesc.ofDescriptor(descriptor);
      type(method.returnType());
      for (var parameter : method.parameterArray()) {
        type(parameter);
      }
    } else {
      type(ClassDesc.ofDescriptor(descriptor));
    }
  }

  private void type(ClassDesc type) {
    var component = type;
    while (component.isArray()) {
      component = component.componentType();
    }
    if (!component.isPrimitive()) {
      var descriptor = component.descriptorString();
      types.add(descriptor.substring(1, descriptor.length() - 1).replace('/', '.'));
    }
  }

  private void attributes(AttributedElement element) {
    for (var attribute : element.attributes()) {
      switch (attribute) {
        case SignatureAttribute signature -> {
          if (element instanceof ClassModel) {
            classSignature(signature.asClassSignature());
          } else if (element instanceof MethodModel) {
            methodSignature(signature.asMethodSignature());
          } else {
            signature(signature.asTypeSignature());
          }
        }
        case RuntimeVisibleAnnotationsAttribute visible -> visible.annotations().forEach(this::annotation);
        case RuntimeInvisibleAnnotationsAttribute invisible -> invisible.annotations().forEach(this::annotation);
        case RuntimeVisibleParameterAnnotationsAttribute visible ->
            visible.parameterAnnotations().forEach(values -> values.forEach(this::annotation));
        case RuntimeInvisibleParameterAnnotationsAttribute invisible ->
            invisible.parameterAnnotations().forEach(values -> values.forEach(this::annotation));
        case RuntimeVisibleTypeAnnotationsAttribute visible ->
            visible.annotations().forEach(value -> annotation(value.annotation()));
        case RuntimeInvisibleTypeAnnotationsAttribute invisible ->
            invisible.annotations().forEach(value -> annotation(value.annotation()));
        case AnnotationDefaultAttribute value -> annotationValue(value.defaultValue());
        case ExceptionsAttribute exceptions -> exceptions.exceptions().forEach(type -> type(type.asSymbol()));
        case CodeAttribute code -> {
          if (includeCode) {
            attributes(code);
          }
        }
        case LocalVariableTableAttribute locals -> locals.localVariables().forEach(local -> type(local.typeSymbol()));
        case LocalVariableTypeTableAttribute locals ->
            locals.localVariableTypes().forEach(local -> signature(Signature.parseFrom(local.signature().stringValue())));
        case RecordAttribute record -> record.components().forEach(component -> {
          descriptor(component.descriptor().stringValue());
          attributes(component);
        });
        default -> { }
      }
    }
  }

  private void classSignature(ClassSignature signature) {
    signature.typeParameters().forEach(this::parameter);
    signature(signature.superclassSignature());
    signature.superinterfaceSignatures().forEach(this::signature);
  }

  private void methodSignature(MethodSignature signature) {
    signature.typeParameters().forEach(this::parameter);
    signature.arguments().forEach(this::signature);
    signature(signature.result());
    signature.throwableSignatures().forEach(this::signature);
  }

  private void parameter(TypeParam parameter) {
    parameter.classBound().ifPresent(this::signature);
    parameter.interfaceBounds().forEach(this::signature);
  }

  private void signature(Signature signature) {
    switch (signature) {
      case ClassTypeSig type -> {
        type(type.classDesc());
        type.outerType().ifPresent(this::signature);
        type.typeArgs().forEach(argument -> {
          if (argument instanceof Bounded bounded) {
            signature(bounded.boundType());
          }
        });
      }
      case ArrayTypeSig array -> signature(array.componentSignature());
      default -> { }
    }
  }

  private void annotation(Annotation annotation) {
    var descriptor = annotation.classSymbol().descriptorString();
    annotations.add(descriptor.substring(1, descriptor.length() - 1).replace('/', '.'));
    annotation.elements().forEach(element -> annotationValue(element.value()));
  }

  private void annotationValue(AnnotationValue value) {
    switch (value) {
      case AnnotationValue.OfClass literal -> type(literal.classSymbol());
      case AnnotationValue.OfEnum constant -> type(constant.classSymbol());
      case AnnotationValue.OfAnnotation nested -> annotation(nested.annotation());
      case AnnotationValue.OfArray array -> array.values().forEach(this::annotationValue);
      default -> { }
    }
  }
}
