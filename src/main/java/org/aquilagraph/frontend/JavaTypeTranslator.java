package org.aquilagraph.frontend;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.TreeSet;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.CoverageTypeStructure;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.DeclarationTypeEvidence;
import org.aquilagraph.source.api.JavaField;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.JavaRecordComponent;
import org.aquilagraph.source.api.JavaSynthesizedDeclaration;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeKind;
import org.aquilagraph.source.api.JavaTypeModifier;
import org.aquilagraph.source.api.JavaTypeParameter;
import org.aquilagraph.source.api.JavaTypeReference;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;
import org.aquilagraph.source.api.SyntaxType;
import org.jspecify.annotations.Nullable;

final class JavaTypeTranslator {
  private static final String DIAGNOSTIC_PRODUCER = "JavaTypeTranslator";
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory treeInventory;
  private final JavaMethodTranslator methodTranslator;

  JavaTypeTranslator(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory,
      JavaDeclarationPropertyObserver propertyObserver, JavaMethodTranslator methodTranslator) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.treeInventory = Objects.requireNonNull(treeInventory, "treeInventory");
    Objects.requireNonNull(propertyObserver, "propertyObserver");
    this.methodTranslator = Objects.requireNonNull(methodTranslator, "methodTranslator");
  }

  TranslationResult<Optional<TranslatedType>> translate(CompilationUnitTree unit, TreePath parentPath, SourceFile sourceFile,
      ClassTree classTree) {
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
    var recordComponents = new ArrayList<JavaRecordComponent>();
    var methods = new ArrayList<JavaMethod>();
    var methodBodies = new ArrayList<TranslatedMethodBody>();
    var nestedTypes = new ArrayList<JavaType>();
    var memberOrders = new ArrayList<DeclarationMemberOrder>();
    var declarationTypes = new ArrayList<DeclarationTypeEvidence>();
    var diagnostics = new TranslationDiagnosticEvidence(DIAGNOSTIC_PRODUCER, typeKey + ":declaration");
    var occurrences = new ArrayList<LanguageFeatureOccurrence>();
    var observations = new ArrayList<LanguageFeatureObservation>();
    if (typeElement == null) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(typeKey,
          "frontend.unresolved-type-declaration", "A type declaration element is unavailable", origin));
    }

    var completeComponents = true;
    if (typeElement != null) {
      recordComponents.addAll(translateRecordComponents(unit, classTree, sourceFile, typeElement, typeKey, diagnostics));
      completeComponents = recordComponents.size() == typeElement.getRecordComponents().size();
    }
    var modifiers = typeModifiers(typeElement == null ? classTree.getModifiers().getFlags() : typeElement.getModifiers());
    var enclosingType = enclosingType(typeElement, typeKey, origin, diagnostics);
    var directSuperclass = directSuperclass(typeElement, typeKey, origin, diagnostics);
    var directInterfaces = typeReferences(typeElement == null ? List.of() : typeElement.getInterfaces(), typeKey,
        "frontend.unresolved-direct-interface", "A direct interface identity is unavailable", origin, diagnostics);
    var permittedSubclasses = typeReferences(typeElement == null ? List.of() : typeElement.getPermittedSubclasses(), typeKey,
        "frontend.unresolved-permitted-subclass", "A permitted subclass identity is unavailable", origin, diagnostics);
    var typeParameters = translateTypeParameters(unit, sourceFile, typeKey, classTree.getTypeParameters(), typeElement, diagnostics);

    var completeMembers = true;
    for (var member : classTree.getMembers()) {
      if (!classifyMember(unit, classPath, sourceFile, typeKey, member, fields, methods, methodBodies, nestedTypes, memberOrders,
          declarationTypes, diagnostics, occurrences, observations)) {
        completeMembers = false;
      }
    }
    if (!completeComponents || !completeMembers) {
      return new TranslationResult<>(Optional.empty(), diagnostics, occurrences, observations);
    }
    var firstReconciliationDiagnostic = diagnostics.size();
    var synthesizedDeclarations = reconcileEnclosedDeclarations(unit, classPath, classTree, typeElement, sourceFile, typeKey, origin,
        fields, recordComponents, methods, nestedTypes, diagnostics);
    if (diagnostics.size() > firstReconciliationDiagnostic) {
      return new TranslationResult<>(Optional.empty(), diagnostics, occurrences, observations);
    }
    if (typeElement != null) {
      var members = new ArrayList<DeclarationMemberOrder.Member>();
      for (var member : typeElement.getEnclosedElements()) {
        var key = semantics.declarationKey(member).orElseThrow();
        var provenance = semantics.declarationOrigin(member) == Elements.Origin.EXPLICIT
            ? CoveragePropertyValue.Provenance.WRITTEN
            : CoveragePropertyValue.Provenance.COMPILER_DERIVED;
        members.add(new DeclarationMemberOrder.Member(key, member.getKind().name(), member.getKind().name() + ':' + member, provenance,
            Optional.empty()));
      }
      var orderedKeys = members.stream().map(DeclarationMemberOrder.Member::declarationKey).toList();
      fields.sort(Comparator.comparingInt(field -> orderedKeys.indexOf(field.fieldKey())));
      methods.sort(Comparator.comparingInt(method -> orderedKeys.indexOf(method.methodKey())));
      memberOrders.addFirst(new DeclarationMemberOrder(sourceFile.sourceFileKey(), typeKey, members));
    }
    var type = new JavaType(typeKey, qualifiedName, javaTypeKind(classTree.getKind()), modifiers, enclosingType, directSuperclass,
        directInterfaces, permittedSubclasses, typeParameters, origin.isPresent(), fields, recordComponents, methods,
        synthesizedDeclarations, origin.stream().toList());
    var types = new ArrayList<JavaType>();
    types.add(type);
    types.addAll(nestedTypes);
    if (typeElement != null) {
      var declaredMirror = typeElement.asType();
      var declaredIdentity = semantics.typeIdentity(declaredMirror);
      if (declaredIdentity.category() == JavaTypeCategory.DECLARED) {
        var declaredStructure = JavaCoverageTypeProjection.copy(declaredMirror, semantics);
        if (declaredStructure instanceof CoverageTypeStructure.Declared declared && declared.declarationKey().equals(type.typeKey())) {
          declarationTypes
              .addFirst(new DeclarationTypeEvidence(type.typeKey(), sourceFile.sourceFileKey(), declaredIdentity, declaredStructure));
        }
      }
    }
    return new TranslationResult<>(Optional.of(new TranslatedType(types, methodBodies, memberOrders, declarationTypes)), diagnostics,
        occurrences, observations);
  }

  List<JavaSynthesizedDeclaration> reconcileEnclosedDeclarations(CompilationUnitTree unit, TreePath classPath, ClassTree classTree,
      @Nullable TypeElement typeElement, SourceFile sourceFile, String typeKey, Optional<SourceSpan> origin, List<JavaField> fields,
      List<JavaRecordComponent> components, List<JavaMethod> methods, List<JavaType> nestedTypes, List<AnalysisDiagnostic> diagnostics) {
    if (typeElement == null) {
      return List.of();
    }
    var representedKeys = new TreeSet<String>();
    fields.stream().map(JavaField::fieldKey).forEach(representedKeys::add);
    components.stream().map(JavaRecordComponent::componentKey).forEach(representedKeys::add);
    methods.stream().map(JavaMethod::methodKey).forEach(representedKeys::add);
    nestedTypes.stream().filter(type -> type.enclosingType().flatMap(JavaTypeReference::declarationKey).filter(typeKey::equals).isPresent())
        .map(JavaType::typeKey).forEach(representedKeys::add);
    var synthesized = new ArrayList<JavaSynthesizedDeclaration>();
    var compilerKeys = new HashSet<String>();
    var writtenMethodKeys = new HashSet<String>();
    var completeWrittenMethodInventory = treeInventory.isWritten(classTree);
    for (var member : classTree.getMembers()) {
      if (member instanceof MethodTree method && treeInventory.isWritten(method)) {
        var methodPath = TreePath.getPath(classPath, method);
        var writtenElement = methodPath == null ? null : semantics.element(methodPath);
        var writtenKey = writtenElement instanceof ExecutableElement executable && typeElement.equals(executable.getEnclosingElement())
            ? semantics.declarationKey(executable)
            : Optional.<String>empty();
        if (writtenKey.isEmpty()) {
          TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
              FrontendDiagnosticFactory.otherError(typeKey, "frontend.unresolved-written-method-provenance",
                  "A written method cannot be correlated with a direct compiler declaration",
                  sourceEvidence.origin(unit, sourceFile, method)));
          completeWrittenMethodInventory = false;
        } else {
          writtenMethodKeys.add(writtenKey.orElseThrow());
        }
      }
    }
    for (var enclosed : typeElement.getEnclosedElements()) {
      var key = semantics.declarationKey(enclosed);
      if (!isDirectDeclarationKind(enclosed.getKind()) || key.isEmpty()) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
            FrontendDiagnosticFactory.otherError(typeKey, "frontend.unavailable-enclosed-declaration",
                "A compiler-exposed direct declaration has no supported kind or canonical key", origin));
        continue;
      }
      if (!compilerKeys.add(key.orElseThrow())) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(key.orElseThrow(),
            "frontend.duplicate-enclosed-declaration", "Compiler-exposed direct declarations share a canonical key", origin));
        continue;
      }
      if (!representedKeys.contains(key.orElseThrow())) {
        var sourceTree = semantics.sourceTree(enclosed);
        var unwrittenMethod = enclosed.getKind() == ElementKind.METHOD && sourceTree == null && completeWrittenMethodInventory
            && !writtenMethodKeys.contains(key.orElseThrow());
        if (enclosed.getKind() == ElementKind.RECORD_COMPONENT || (sourceTree != null && treeInventory.isWritten(sourceTree))
            || writtenMethodKeys.contains(key.orElseThrow())
            || (semantics.declarationOrigin(enclosed) == Elements.Origin.EXPLICIT && !unwrittenMethod)) {
          TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
              FrontendDiagnosticFactory.otherError(key.orElseThrow(), "frontend.unrepresented-written-declaration",
                  "A written direct declaration is absent from its type aggregate",
                  sourceTree == null ? origin : sourceEvidence.origin(unit, sourceFile, sourceTree)));
          continue;
        }
        synthesized.add(
            new JavaSynthesizedDeclaration(key.orElseThrow(), typeKey, JavaSynthesizedDeclaration.Kind.valueOf(enclosed.getKind().name())));
        representedKeys.add(key.orElseThrow());
      }
    }
    for (var representedKey : representedKeys) {
      if (!compilerKeys.contains(representedKey)) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(representedKey,
            "frontend.unmatched-enclosed-declaration", "A modeled direct declaration has no compiler-enclosed counterpart", origin));
      }
    }
    synthesized.sort(Comparator.comparing(JavaSynthesizedDeclaration::declarationKey));
    return List.copyOf(synthesized);
  }

  private static boolean isDirectDeclarationKind(ElementKind kind) {
    return switch (kind) {
      case ANNOTATION_TYPE, CLASS, CONSTRUCTOR, ENUM, ENUM_CONSTANT, FIELD, INTERFACE, METHOD, RECORD, RECORD_COMPONENT -> true;
      default -> false;
    };
  }

  private List<JavaRecordComponent> translateRecordComponents(CompilationUnitTree unit, ClassTree classTree, SourceFile sourceFile,
      TypeElement typeElement, String typeKey, List<AnalysisDiagnostic> diagnostics) {
    var elements = typeElement.getRecordComponents();
    var translated = new ArrayList<JavaRecordComponent>();
    var constructor = canonicalConstructor(typeElement, elements);
    for (var ordinal = 0; ordinal < elements.size(); ordinal++) {
      var component = elements.get(ordinal);
      var componentKey = semantics.declarationKey(component);
      var componentTree = semantics.sourceTree(component);
      var sourceStart = componentTree == null ? -1 : sourceEvidence.rawStart(unit, componentTree);
      var written = classTree.getMembers().stream().filter(VariableTree.class::isInstance).map(VariableTree.class::cast)
          .filter(candidate -> treeInventory.isWritten(candidate) && candidate.getName().contentEquals(component.getSimpleName())
              && sourceEvidence.rawStart(unit, candidate) == sourceStart)
          .toList();
      var origin = written.size() == 1
          ? sourceEvidence.origin(unit, sourceFile, written.getFirst())
          : sourceEvidence.origin(unit, sourceFile, classTree);
      var type = semantics.typeReference(component.asType());
      var expectedKey = typeKey + "#record-component:" + ordinal;
      if (componentKey.filter(expectedKey::equals).isEmpty() || semantics.declarationOrigin(component) != Elements.Origin.EXPLICIT
          || componentTree == null || sourceStart < 0 || written.size() != 1 || origin.isEmpty() || type.isEmpty()
          || type.orElseThrow().identity().category() == JavaTypeCategory.ERROR
          || type.orElseThrow().identity().category() == JavaTypeCategory.OTHER || constructor.isEmpty()) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
            FrontendDiagnosticFactory.otherError(expectedKey, "frontend.unavailable-record-component",
                "A written record component lacks a correlated element, source range, type, or canonical constructor", origin));
        continue;
      }
      var accessor = component.getAccessor();
      var accessorKey = accessor == null ? Optional.<String>empty() : semantics.declarationKey(accessor);
      var backingFieldKey = typeElement.getEnclosedElements().stream().filter(VariableElement.class::isInstance)
          .map(VariableElement.class::cast)
          .filter(element -> element.getKind() == ElementKind.FIELD && element.getSimpleName().contentEquals(component.getSimpleName()))
          .findFirst().flatMap(semantics::declarationKey);
      var parameterKey = semantics.declarationKey(constructor.orElseThrow().getParameters().get(ordinal));
      if (accessorKey.isEmpty() || backingFieldKey.isEmpty() || parameterKey.isEmpty()) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
            FrontendDiagnosticFactory.otherError(expectedKey, "frontend.unavailable-record-counterpart",
                "A record component lacks a compiler-correlated accessor, field, or canonical parameter", origin));
        continue;
      }
      var varArgs = constructor.orElseThrow().isVarArgs() && ordinal == elements.size() - 1;
      translated.add(new JavaRecordComponent(expectedKey, typeKey, ordinal, component.getSimpleName().toString(),
          type.orElseThrow().identity(), Set.of(), typeModifiers(component.getModifiers()), varArgs, accessorKey, backingFieldKey,
          parameterKey, origin.orElseThrow()));
    }
    return List.copyOf(translated);
  }

  private Optional<ExecutableElement> canonicalConstructor(TypeElement owner, List<? extends RecordComponentElement> components) {
    return owner.getEnclosedElements().stream().filter(ExecutableElement.class::isInstance).map(ExecutableElement.class::cast)
        .filter(executable -> executable.getKind() == ElementKind.CONSTRUCTOR && executable.getParameters().size() == components.size())
        .filter(executable -> {
          for (var ordinal = 0; ordinal < components.size(); ordinal++) {
            if (!semantics.sameType(executable.getParameters().get(ordinal).asType(), components.get(ordinal).asType())) {
              return false;
            }
          }
          return true;
        }).findFirst();
  }

  private boolean classifyMember(CompilationUnitTree unit, TreePath classPath, SourceFile sourceFile, String typeKey, Tree member,
      List<JavaField> fields, List<JavaMethod> methods, List<TranslatedMethodBody> methodBodies, List<JavaType> nestedTypes,
      List<DeclarationMemberOrder> memberOrders, List<DeclarationTypeEvidence> declarationTypes, List<AnalysisDiagnostic> diagnostics,
      List<LanguageFeatureOccurrence> occurrences, List<LanguageFeatureObservation> observations) {
    switch (member) {
      case MethodTree methodTree -> {
        var translation = methodTranslator.translate(unit, classPath, sourceFile, typeKey, methodTree);
        var method = translation.value().method();
        if (methods.stream().anyMatch(existing -> existing.methodKey().equals(method.methodKey()))) {
          TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
              FrontendDiagnosticFactory.otherError(method.methodKey(), "frontend.duplicate-member-declaration",
                  "Written members share a canonical declaration key", sourceEvidence.origin(unit, sourceFile, methodTree)));
        } else {
          methods.add(method);
          translation.value().body().ifPresent(methodBodies::add);
        }
        diagnostics.addAll(translation.diagnostics());
        occurrences.addAll(translation.featureOccurrences());
        observations.addAll(translation.featureObservations());
      }
      case VariableTree variableTree -> {
        var translation = translateField(unit, classPath, sourceFile, typeKey, variableTree);
        var field = translation.value();
        if (fields.stream().anyMatch(existing -> existing.fieldKey().equals(field.fieldKey()))) {
          TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
              FrontendDiagnosticFactory.otherError(field.fieldKey(), "frontend.duplicate-member-declaration",
                  "Written members share a canonical declaration key", sourceEvidence.origin(unit, sourceFile, variableTree)));
        } else {
          fields.add(field);
        }
        diagnostics.addAll(translation.diagnostics());
        occurrences.addAll(translation.featureOccurrences());
        observations.addAll(translation.featureObservations());
      }
      case ClassTree nestedClass -> {
        var translation = translate(unit, classPath, sourceFile, nestedClass);
        translation.value().ifPresent(value -> {
          nestedTypes.addAll(value.types());
          methodBodies.addAll(value.methodBodies());
          memberOrders.addAll(value.memberOrders());
          declarationTypes.addAll(value.declarationTypes());
        });
        diagnostics.addAll(translation.diagnostics());
        occurrences.addAll(translation.featureOccurrences());
        observations.addAll(translation.featureObservations());
        return translation.value().isPresent();
      }
      case BlockTree initializer ->
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, initializerDiagnostic(unit, sourceFile, typeKey, initializer));
      case EmptyStatementTree _ -> {
        // Empty class-body declarations have no executable or declaration semantics.
      }
      default -> TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          unsupportedMemberDiagnostic(unit, sourceFile, typeKey, member));
    }
    return true;
  }

  private TranslationResult<JavaField> translateField(CompilationUnitTree unit, TreePath classPath, SourceFile sourceFile, String typeKey,
      VariableTree variableTree) {
    var path = new TreePath(classPath, variableTree);
    var element = semantics.element(path);
    var variable = element instanceof VariableElement value ? value : null;
    var written = treeInventory.isWritten(variableTree) && variable != null
        && semantics.declarationOrigin(variable) == Elements.Origin.EXPLICIT;
    var origin = sourceEvidence.origin(unit, sourceFile, variableTree);
    var enumConstant = variable != null && variable.getKind() == ElementKind.ENUM_CONSTANT;
    var fieldKey = variable == null
        ? typeKey + "#unresolved-field:"
            + origin.map(value -> value.startOffset() + ":" + value.endOffsetExclusive())
                .orElseGet(() -> StableId.sha256("unresolved-field", typeKey, variableTree.getName().toString()))
        : semantics.declarationKey(variable).orElseThrow();
    var diagnostics = new TranslationDiagnosticEvidence(DIAGNOSTIC_PRODUCER, fieldKey + ":field");
    var occurrences = new ArrayList<LanguageFeatureOccurrence>();
    var observations = new ArrayList<LanguageFeatureObservation>();
    if (variable == null) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(fieldKey,
          "frontend.unresolved-field", "A field declaration element is unavailable", origin));
    }
    var owner = semantics.element(classPath);
    var recordBacking = owner instanceof TypeElement typeElement && typeElement.getKind() == ElementKind.RECORD && typeElement
        .getRecordComponents().stream().anyMatch(component -> component.getSimpleName().contentEquals(variableTree.getName()));
    if (variableTree.getNameExpression() != null) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(fieldKey, "frontend.invalid-field-name-expression",
              "A field declaration cannot have a receiver name expression",
              sourceEvidence.origin(unit, sourceFile, variableTree.getNameExpression())));
    }
    var staticType = declarationType(semantics.type(path));
    if (staticType.category() == JavaTypeCategory.ERROR) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.erroneousCompilerState(fieldKey,
          "frontend.error-field-type", "javac reported an error type for a field", origin));
    } else if (staticType.category() == JavaTypeCategory.OTHER) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(fieldKey, "frontend.unresolved-field-type", "A field type is unavailable", origin));
    }
    var modifiers = variableTree.getModifiers().getFlags().stream().map(modifier -> modifier.name().toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());
    var field = new JavaField(fieldKey, typeKey, variableTree.getName().toString(), staticType, modifiers, enumConstant,
        !recordBacking && written && origin.isPresent(), recordBacking || !written ? List.of() : origin.stream().toList());
    if (variableTree.getInitializer() != null) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          fieldInitializerDiagnostic(unit, sourceFile, fieldKey, variableTree, enumConstant));
    }
    return new TranslationResult<>(field, diagnostics, occurrences, observations);
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
      return FrontendDiagnosticFactory.capabilityError(fieldKey, code, message, bodyOrigin);
    }
    var origin = sourceEvidence.origin(unit, sourceFile, initializer);
    var code = enumConstant ? "frontend.unsupported-enum-constant-initialization" : "frontend.unsupported-field-initializer";
    var message = enumConstant
        ? "Enum constant initialization is not supported for direct graph lowering"
        : "Field initialization is not supported for direct graph lowering";
    return FrontendDiagnosticFactory.capabilityError(fieldKey, code, message, origin);
  }

  private AnalysisDiagnostic initializerDiagnostic(CompilationUnitTree unit, SourceFile sourceFile, String typeKey, BlockTree initializer) {
    var origin = sourceEvidence.origin(unit, sourceFile, initializer);
    var form = initializer.isStatic() ? "static" : "instance";
    return FrontendDiagnosticFactory.capabilityError(typeKey + ':' + form + "-initializer", "frontend.unsupported-initializer",
        "A " + form + " initializer block is not supported for direct graph lowering", origin);
  }

  private AnalysisDiagnostic unsupportedMemberDiagnostic(CompilationUnitTree unit, SourceFile sourceFile, String typeKey, Tree member) {
    var origin = sourceEvidence.origin(unit, sourceFile, member);
    return FrontendDiagnosticFactory.capabilityError(typeKey + ':' + member.getKind(), "frontend.unsupported-class-member",
        "Unsupported Java class-body declaration: " + member.getKind(), origin);
  }

  private Optional<JavaTypeReference> enclosingType(@Nullable TypeElement typeElement, String typeKey, Optional<SourceSpan> origin,
      List<AnalysisDiagnostic> diagnostics) {
    if (typeElement == null || !(typeElement.getEnclosingElement() instanceof TypeElement owner)) {
      return Optional.empty();
    }
    var reference = semantics.typeReference(owner.asType());
    if (reference.isEmpty()) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(typeKey,
          "frontend.unresolved-enclosing-type", "An enclosing type identity is unavailable", origin));
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
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(typeKey,
          "frontend.unresolved-direct-superclass", "A direct superclass identity is unavailable", origin));
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
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
            FrontendDiagnosticFactory.otherError(typeKey + ':' + index, code, message, origin));
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
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
            FrontendDiagnosticFactory.otherError(typeKey + ":type-parameter:" + index, "frontend.unresolved-type-parameter",
                "A type parameter declaration element is unavailable", origin));
        continue;
      }
      var parameterElement = parameterElements.get(index);
      var expectedKey = typeKey + "#type-parameter:" + index;
      var parameterKey = semantics.declarationKey(parameterElement);
      if (parameterKey.filter(expectedKey::equals).isEmpty()) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(expectedKey,
            "frontend.unresolved-type-parameter", "A type parameter declaration identity is unavailable or inconsistent", origin));
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

  static Set<JavaTypeModifier> typeModifiers(Set<Modifier> modifiers) {
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

}
