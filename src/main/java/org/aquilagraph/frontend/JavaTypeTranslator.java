package org.aquilagraph.frontend;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.DiagnosticSeverity;
import org.aquilagraph.model.DiagnosticStage;
import org.aquilagraph.model.JavaField;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.JavaType;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.JavaTypeKind;
import org.aquilagraph.model.JavaTypeModifier;
import org.aquilagraph.model.JavaTypeParameter;
import org.aquilagraph.model.JavaTypeReference;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.model.StableId;
import org.aquilagraph.syntax.MethodSyntax;
import org.aquilagraph.syntax.SyntaxType;
import org.jspecify.annotations.Nullable;

final class JavaTypeTranslator {
  private final JavaSemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final JavaMethodTranslator methodTranslator;

  JavaTypeTranslator(JavaSemanticResolver semantics, JavaSourceEvidence sourceEvidence, JavaMethodTranslator methodTranslator) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.methodTranslator = Objects.requireNonNull(methodTranslator, "methodTranslator");
  }

  TranslationResult<TranslatedType> translate(CompilationUnitTree unit, TreePath parentPath, SourceFile sourceFile, ClassTree classTree) {
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(parentPath, "parentPath");
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(classTree, "classTree");
    var classPath = new TreePath(parentPath, classTree);
    var origin = sourceEvidence.origin(unit, sourceFile, classTree);
    var element = semantics.element(classPath);
    var typeElement = element instanceof TypeElement value ? value : null;
    var qualifiedName = typeElement == null ? fallbackQualifiedName(unit, classTree) : typeElement.getQualifiedName().toString();
    var typeKey = typeElement == null
        ? "type:unresolved:" + origin.map(value -> value.sourceFileKey() + ':' + value.startOffset() + ':' + value.endOffsetExclusive())
            .orElseGet(() -> StableId.sha256("unresolved-type", qualifiedName))
        : semantics.declarationKey(typeElement).orElseThrow();
    var fields = new ArrayList<JavaField>();
    var methods = new ArrayList<JavaMethod>();
    var methodSyntax = new ArrayList<MethodSyntax>();
    var nestedTypes = new ArrayList<JavaType>();
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    if (typeElement == null) {
      diagnostics.add(diagnostic(typeKey, "frontend.unresolved-type-declaration", "A type declaration element is unavailable", origin));
    }

    diagnoseUnsupportedAnnotations(unit, sourceFile, typeKey, classTree, diagnostics);
    var modifiers = typeModifiers(typeElement == null ? classTree.getModifiers().getFlags() : typeElement.getModifiers());
    var enclosingType = enclosingType(typeElement, typeKey, origin, diagnostics);
    var directSuperclass = directSuperclass(typeElement, typeKey, origin, diagnostics);
    var directInterfaces = typeReferences(typeElement == null ? List.of() : typeElement.getInterfaces(), typeKey,
        "frontend.unresolved-direct-interface", "A direct interface identity is unavailable", origin, diagnostics);
    var permittedSubclasses = typeReferences(typeElement == null ? List.of() : typeElement.getPermittedSubclasses(), typeKey,
        "frontend.unresolved-permitted-subclass", "A permitted subclass identity is unavailable", origin, diagnostics);
    var typeParameters = translateTypeParameters(unit, sourceFile, typeKey, classTree.getTypeParameters(), typeElement, diagnostics);

    for (var member : classTree.getMembers()) {
      classifyMember(unit, classPath, sourceFile, typeKey, member, fields, methods, methodSyntax, nestedTypes, diagnostics);
    }
    var type = new JavaType(typeKey, qualifiedName, javaTypeKind(classTree.getKind()), modifiers, enclosingType, directSuperclass,
        directInterfaces, permittedSubclasses, typeParameters, origin.isPresent(), fields, methods, origin.stream().toList());
    var types = new ArrayList<JavaType>();
    types.add(type);
    types.addAll(nestedTypes);
    return new TranslationResult<>(new TranslatedType(types, methodSyntax), diagnostics);
  }

  private void classifyMember(CompilationUnitTree unit, TreePath classPath, SourceFile sourceFile, String typeKey, Tree member,
      List<JavaField> fields, List<JavaMethod> methods, List<MethodSyntax> methodSyntax, List<JavaType> nestedTypes,
      List<AnalysisDiagnostic> diagnostics) {
    switch (member) {
      case MethodTree methodTree -> {
        var translation = methodTranslator.translate(unit, classPath, sourceFile, typeKey, methodTree);
        methods.add(translation.value().method());
        translation.value().syntax().ifPresent(methodSyntax::add);
        diagnostics.addAll(translation.diagnostics());
      }
      case VariableTree variableTree -> {
        var translation = translateField(unit, classPath, sourceFile, typeKey, variableTree);
        fields.add(translation.value());
        diagnostics.addAll(translation.diagnostics());
      }
      case ClassTree nestedClass -> {
        var translation = translate(unit, classPath, sourceFile, nestedClass);
        nestedTypes.addAll(translation.value().types());
        methodSyntax.addAll(translation.value().methodSyntax());
        diagnostics.addAll(translation.diagnostics());
      }
      case BlockTree initializer -> diagnostics.add(initializerDiagnostic(unit, sourceFile, typeKey, initializer));
      case EmptyStatementTree ignored -> {
        // Empty class-body declarations have no executable or declaration semantics.
      }
      default -> diagnostics.add(unsupportedMemberDiagnostic(unit, sourceFile, typeKey, member));
    }
  }

  private TranslationResult<JavaField> translateField(CompilationUnitTree unit, TreePath classPath, SourceFile sourceFile, String typeKey,
      VariableTree variableTree) {
    var path = new TreePath(classPath, variableTree);
    var origin = sourceEvidence.origin(unit, sourceFile, variableTree);
    var element = semantics.element(path);
    var variable = element instanceof VariableElement value ? value : null;
    var enumConstant = variable != null && variable.getKind() == ElementKind.ENUM_CONSTANT;
    var fieldKey = variable == null
        ? typeKey + "#unresolved-field:"
            + origin.map(value -> value.startOffset() + ":" + value.endOffsetExclusive())
                .orElseGet(() -> StableId.sha256("unresolved-field", typeKey, variableTree.getName().toString()))
        : semantics.declarationKey(variable).orElseThrow();
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    if (variable == null) {
      diagnostics.add(diagnostic(fieldKey, "frontend.unresolved-field", "A field declaration element is unavailable", origin));
    }
    var staticType = declarationType(semantics.type(path));
    if (staticType.category() == JavaTypeCategory.ERROR) {
      diagnostics.add(diagnostic(fieldKey, "frontend.error-field-type", "javac reported an error type for a field", origin));
    } else if (staticType.category() == JavaTypeCategory.OTHER) {
      diagnostics.add(diagnostic(fieldKey, "frontend.unresolved-field-type", "A field type is unavailable", origin));
    }
    var modifiers = variableTree.getModifiers().getFlags().stream().map(modifier -> modifier.name().toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());
    var field = new JavaField(fieldKey, typeKey, variableTree.getName().toString(), staticType, modifiers, enumConstant, origin.isPresent(),
        origin.stream().toList());
    if (variableTree.getInitializer() != null) {
      diagnostics.add(fieldInitializerDiagnostic(unit, sourceFile, fieldKey, variableTree, enumConstant));
    }
    return new TranslationResult<>(field, diagnostics);
  }

  private AnalysisDiagnostic fieldInitializerDiagnostic(CompilationUnitTree unit, SourceFile sourceFile, String fieldKey,
      VariableTree variableTree, boolean enumConstant) {
    var initializer = variableTree.getInitializer();
    if (initializer instanceof NewClassTree creation && creation.getClassBody() != null) {
      var bodyOrigin = sourceEvidence.origin(unit, sourceFile, creation.getClassBody());
      var code = enumConstant ? "frontend.unsupported-enum-constant-body" : "frontend.unsupported-anonymous-class-field-initializer";
      var message = enumConstant
          ? "Enum constant-specific class bodies are not supported for direct graph lowering"
          : "Anonymous classes in field initializers are not supported for direct graph lowering";
      return diagnostic(fieldKey, code, message, bodyOrigin);
    }
    var origin = sourceEvidence.origin(unit, sourceFile, initializer);
    var code = enumConstant ? "frontend.unsupported-enum-constant-initialization" : "frontend.unsupported-field-initializer";
    var message = enumConstant
        ? "Enum constant initialization is not supported for direct graph lowering"
        : "Field initialization is not supported for direct graph lowering";
    return diagnostic(fieldKey, code, message, origin);
  }

  private AnalysisDiagnostic initializerDiagnostic(CompilationUnitTree unit, SourceFile sourceFile, String typeKey, BlockTree initializer) {
    var origin = sourceEvidence.origin(unit, sourceFile, initializer);
    var form = initializer.isStatic() ? "static" : "instance";
    return diagnostic(typeKey + ':' + form + "-initializer", "frontend.unsupported-initializer",
        "A " + form + " initializer block is not supported for direct graph lowering", origin);
  }

  private AnalysisDiagnostic unsupportedMemberDiagnostic(CompilationUnitTree unit, SourceFile sourceFile, String typeKey, Tree member) {
    var origin = sourceEvidence.origin(unit, sourceFile, member);
    return diagnostic(typeKey + ':' + member.getKind(), "frontend.unsupported-class-member",
        "Unsupported Java class-body declaration: " + member.getKind(), origin);
  }

  private Optional<JavaTypeReference> enclosingType(@Nullable TypeElement typeElement, String typeKey, Optional<SourceSpan> origin,
      List<AnalysisDiagnostic> diagnostics) {
    if (typeElement == null || !(typeElement.getEnclosingElement() instanceof TypeElement owner)) {
      return Optional.empty();
    }
    var reference = semantics.typeReference(owner.asType());
    if (reference.isEmpty()) {
      diagnostics.add(diagnostic(typeKey, "frontend.unresolved-enclosing-type", "An enclosing type identity is unavailable", origin));
    }
    return reference;
  }

  private Optional<JavaTypeReference> directSuperclass(@Nullable TypeElement typeElement, String typeKey, Optional<SourceSpan> origin,
      List<AnalysisDiagnostic> diagnostics) {
    if (typeElement == null || typeElement.getSuperclass().getKind() == TypeKind.NONE) {
      return Optional.empty();
    }
    var reference = semantics.typeReference(typeElement.getSuperclass());
    if (reference.isEmpty()) {
      diagnostics.add(diagnostic(typeKey, "frontend.unresolved-direct-superclass", "A direct superclass identity is unavailable", origin));
    }
    return reference;
  }

  private List<JavaTypeReference> typeReferences(List<? extends TypeMirror> types, String typeKey, String code, String message,
      Optional<SourceSpan> origin, List<AnalysisDiagnostic> diagnostics) {
    var references = new ArrayList<JavaTypeReference>(types.size());
    for (var index = 0; index < types.size(); index++) {
      var reference = semantics.typeReference(types.get(index));
      if (reference.isPresent()) {
        references.add(reference.orElseThrow());
      } else {
        diagnostics.add(diagnostic(typeKey + ':' + index, code, message, origin));
      }
    }
    return List.copyOf(references);
  }

  private List<JavaTypeParameter> translateTypeParameters(CompilationUnitTree unit, SourceFile sourceFile, String typeKey,
      List<? extends TypeParameterTree> parameterTrees, @Nullable TypeElement typeElement, List<AnalysisDiagnostic> diagnostics) {
    var parameters = new ArrayList<JavaTypeParameter>();
    var parameterElements = typeElement == null ? List.<TypeParameterElement>of() : typeElement.getTypeParameters();
    for (var index = 0; index < parameterTrees.size(); index++) {
      var parameterTree = parameterTrees.get(index);
      var origin = sourceEvidence.origin(unit, sourceFile, parameterTree);
      if (index >= parameterElements.size()) {
        diagnostics.add(diagnostic(typeKey + ":type-parameter:" + index, "frontend.unresolved-type-parameter",
            "A type parameter declaration element is unavailable", origin));
        continue;
      }
      var parameterElement = parameterElements.get(index);
      var expectedKey = typeKey + "#type-parameter:" + index;
      var parameterKey = semantics.declarationKey(parameterElement);
      if (parameterKey.filter(expectedKey::equals).isEmpty()) {
        diagnostics.add(diagnostic(expectedKey, "frontend.unresolved-type-parameter",
            "A type parameter declaration identity is unavailable or inconsistent", origin));
        continue;
      }
      var bounds = typeReferences(parameterElement.getBounds(), parameterKey.orElseThrow(), "frontend.unresolved-type-parameter-bound",
          "A type parameter bound identity is unavailable", origin, diagnostics);
      if (bounds.size() == parameterElement.getBounds().size()) {
        parameters.add(new JavaTypeParameter(parameterKey.orElseThrow(), typeKey, parameterTree.getName().toString(), bounds,
            origin.isPresent(), origin.stream().toList()));
      }
    }
    return List.copyOf(parameters);
  }

  private void diagnoseUnsupportedAnnotations(CompilationUnitTree unit, SourceFile sourceFile, String typeKey, ClassTree classTree,
      List<AnalysisDiagnostic> diagnostics) {
    addAnnotationDiagnostics(unit, sourceFile, typeKey, classTree.getModifiers().getAnnotations(),
        "frontend.unsupported-type-declaration-annotation", "Type declaration annotations are outside the supported declaration profile",
        diagnostics);
    for (var parameter : classTree.getTypeParameters()) {
      addAnnotationDiagnostics(unit, sourceFile, typeKey, parameter.getAnnotations(), "frontend.unsupported-type-parameter-annotation",
          "Type parameter annotations are outside the supported declaration profile", diagnostics);
      addNestedAnnotationDiagnostics(unit, sourceFile, typeKey, parameter.getBounds(), diagnostics);
    }
    var headerTypes = new ArrayList<Tree>();
    if (classTree.getExtendsClause() != null) {
      headerTypes.add(classTree.getExtendsClause());
    }
    headerTypes.addAll(classTree.getImplementsClause());
    headerTypes.addAll(classTree.getPermitsClause());
    addNestedAnnotationDiagnostics(unit, sourceFile, typeKey, headerTypes, diagnostics);
  }

  private void addNestedAnnotationDiagnostics(CompilationUnitTree unit, SourceFile sourceFile, String typeKey, List<? extends Tree> trees,
      List<AnalysisDiagnostic> diagnostics) {
    var scanner = new TreeScanner<Void, Void>() {
      @Override
      public Void visitAnnotation(AnnotationTree annotation, Void unused) {
        var origin = sourceEvidence.origin(unit, sourceFile, annotation);
        diagnostics.add(diagnostic(typeKey, "frontend.unsupported-type-use-annotation",
            "Type-use annotations in declaration headers are outside the supported declaration profile", origin));
        return super.visitAnnotation(annotation, null);
      }
    };
    trees.forEach(tree -> scanner.scan(tree, null));
  }

  private void addAnnotationDiagnostics(CompilationUnitTree unit, SourceFile sourceFile, String typeKey,
      List<? extends AnnotationTree> annotations, String code, String message, List<AnalysisDiagnostic> diagnostics) {
    for (var annotation : annotations) {
      diagnostics.add(diagnostic(typeKey, code, message, sourceEvidence.origin(unit, sourceFile, annotation)));
    }
  }

  private static Set<JavaTypeModifier> typeModifiers(Set<Modifier> modifiers) {
    return modifiers.stream().map(modifier -> JavaTypeModifier.valueOf(modifier.name())).collect(Collectors.toUnmodifiableSet());
  }

  private static JavaTypeIdentity declarationType(SyntaxType type) {
    if (type instanceof SyntaxType.Resolved resolved) {
      return resolved.identity();
    }
    return type == SyntaxType.Erroneous.INSTANCE
        ? new JavaTypeIdentity(JavaTypeCategory.ERROR, "error", "error")
        : new JavaTypeIdentity(JavaTypeCategory.OTHER, "unresolved", "unresolved");
  }

  private static String fallbackQualifiedName(CompilationUnitTree unit, ClassTree classTree) {
    var packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
    return packageName.isEmpty() ? classTree.getSimpleName().toString() : packageName + "." + classTree.getSimpleName();
  }

  private static JavaTypeKind javaTypeKind(Tree.Kind kind) {
    return switch (kind) {
      case CLASS -> JavaTypeKind.CLASS;
      case INTERFACE -> JavaTypeKind.INTERFACE;
      case ENUM -> JavaTypeKind.ENUM;
      case RECORD -> JavaTypeKind.RECORD;
      case ANNOTATION_TYPE -> JavaTypeKind.ANNOTATION;
      default -> throw new IllegalArgumentException("Not a Java type declaration: " + kind);
    };
  }

  private static AnalysisDiagnostic diagnostic(String context, String code, String message, Optional<SourceSpan> origin) {
    var occurrence = origin.map(value -> value.sourceFileKey() + ':' + value.startOffset() + ':' + value.endOffsetExclusive())
        .orElse("no-direct-source");
    return new AnalysisDiagnostic(StableId.sha256("diagnostic", context, code, occurrence), code, DiagnosticStage.FRONTEND,
        DiagnosticSeverity.ERROR, message, origin.stream().toList());
  }
}
