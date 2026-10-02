package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.JavaParameter;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.LanguageFeatureObservation;
import org.aquilagraph.model.LanguageFeatureOccurrence;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.model.StableId;
import org.aquilagraph.syntax.SyntaxType;
import org.jspecify.annotations.Nullable;

final class JavaMethodTranslator {
  private static final String DIAGNOSTIC_PRODUCER = "JavaMethodTranslator";
  private static final String UNRESOLVED_TYPE_NAME = "unresolved";
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory treeInventory;
  private final JavaMethodBodyTranslator bodyTranslator;

  JavaMethodTranslator(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory,
      JavaMethodBodyTranslator bodyTranslator) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.treeInventory = Objects.requireNonNull(treeInventory, "treeInventory");
    this.bodyTranslator = Objects.requireNonNull(bodyTranslator, "bodyTranslator");
  }

  TranslationResult<TranslatedMethod> translate(CompilationUnitTree unit, TreePath classPath, SourceFile sourceFile, String ownerTypeKey,
      MethodTree methodTree) {
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(classPath, "classPath");
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(ownerTypeKey, "ownerTypeKey");
    Objects.requireNonNull(methodTree, "methodTree");
    var methodPath = new TreePath(classPath, methodTree);
    var element = semantics.element(methodPath);
    var executable = element instanceof ExecutableElement value ? value : null;
    var written = treeInventory.isWritten(methodTree) && executable != null
        && semantics.declarationOrigin(executable) == Elements.Origin.EXPLICIT;
    var origin = written ? sourceEvidence.origin(unit, sourceFile, methodTree) : Optional.<SourceSpan>empty();
    var constructor = executable != null ? executable.getKind() == ElementKind.CONSTRUCTOR : methodTree.getReturnType() == null;
    var name = constructor ? "<init>" : methodTree.getName().toString();
    var signature = executable == null ? name + "(unresolved)" : semantics.executableSignature(executable);
    var methodKey = executable == null
        ? ownerTypeKey + "#unresolved-executable:"
            + origin.map(value -> value.startOffset() + ":" + value.endOffsetExclusive())
                .orElseGet(() -> StableId.sha256("unresolved-executable", ownerTypeKey, name))
        : semantics.declarationKey(executable).orElseThrow();
    var diagnostics = new TranslationDiagnosticEvidence(DIAGNOSTIC_PRODUCER, methodKey + ":declaration");
    var occurrences = new ArrayList<LanguageFeatureOccurrence>();
    var observations = new ArrayList<LanguageFeatureObservation>();
    if (executable == null) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(methodKey,
          "frontend.unresolved-method", "A method declaration element is unavailable", origin));
    }
    diagnoseUnsupportedDeclarationProperties(unit, sourceFile, methodKey, methodTree, diagnostics);
    var parameterTranslation = parameters(unit, sourceFile, methodKey, methodPath, methodTree, executable);
    diagnostics.addAll(parameterTranslation.diagnostics());
    occurrences.addAll(parameterTranslation.featureOccurrences());
    observations.addAll(parameterTranslation.featureObservations());
    var semanticModifiers = executable == null ? methodTree.getModifiers().getFlags() : executable.getModifiers();
    var modifiers = semanticModifiers.stream().map(modifier -> modifier.name().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    var bodyTranslation = translateBody(unit, sourceFile, methodKey, methodPath, methodTree, constructor,
        semantics.isImplicitDeclaration(methodPath));
    diagnostics.addAll(bodyTranslation.diagnostics());
    occurrences.addAll(bodyTranslation.featureOccurrences());
    observations.addAll(bodyTranslation.featureObservations());
    var returnType = constructor ? "void" : executable == null ? UNRESOLVED_TYPE_NAME : executable.getReturnType().toString();
    var method = new JavaMethod(methodKey, ownerTypeKey, name, signature, returnType, parameterTranslation.parameters(), modifiers,
        constructor, written && origin.isPresent(), bodyTranslation.value().isPresent(), origin.stream().toList());
    return new TranslationResult<>(new TranslatedMethod(method, bodyTranslation.value()), diagnostics, occurrences, observations);
  }

  private ParameterTranslation parameters(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, TreePath methodPath,
      MethodTree methodTree, @Nullable ExecutableElement executable) {
    var parameters = new ArrayList<JavaParameter>();
    var diagnostics = new TranslationDiagnosticEvidence(DIAGNOSTIC_PRODUCER, methodKey + ":parameters");
    var occurrences = new ArrayList<LanguageFeatureOccurrence>();
    var observations = new ArrayList<LanguageFeatureObservation>();
    var parameterCount = executable == null ? methodTree.getParameters().size() : executable.getParameters().size();
    if (executable != null && methodTree.getParameters().size() != parameterCount) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(methodKey, "frontend.parameter-inventory-mismatch",
              "Compiler and source-tree formal parameter inventories disagree", sourceEvidence.origin(unit, sourceFile, methodTree)));
    }
    for (var index = 0; index < parameterCount; index++) {
      var parameterTree = index < methodTree.getParameters().size() ? methodTree.getParameters().get(index) : null;
      var parameterElement = executable == null ? null : executable.getParameters().get(index);
      var written = parameterTree != null && treeInventory.isWritten(parameterTree) && parameterElement != null
          && semantics.declarationOrigin(parameterElement) == Elements.Origin.EXPLICIT;
      var origin = written && parameterTree != null ? sourceEvidence.origin(unit, sourceFile, parameterTree) : Optional.<SourceSpan>empty();
      var parameterKey = methodKey + ":parameter:" + index;
      if (parameterElement == null || semantics.declarationKey(parameterElement).filter(parameterKey::equals).isEmpty()) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(parameterKey,
            "frontend.unresolved-parameter", "A parameter declaration identity is unavailable or inconsistent", origin));
      }
      if (written && origin.isEmpty()) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
            FrontendDiagnosticFactory.otherError(parameterKey, "frontend.unavailable-parameter-source",
                "A written parameter has no source range", sourceEvidence.origin(unit, sourceFile, methodTree)));
      }
      if (parameterTree != null && written) {
        diagnoseUnsupportedVariableProperties(unit, sourceFile, parameterKey, parameterTree, false, diagnostics);
      }
      JavaTypeIdentity type;
      String name;
      if (parameterElement != null && (!written || parameterTree == null)) {
        type = parameterType(parameterElement);
        name = parameterElement.getSimpleName().toString();
      } else if (parameterTree != null) {
        type = parameterType(semantics.type(parameterPath(methodPath, parameterTree)));
        name = parameterElement == null ? parameterTree.getName().toString() : parameterElement.getSimpleName().toString();
      } else {
        continue;
      }
      if (type.category() == JavaTypeCategory.ERROR) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.erroneousCompilerState(parameterKey,
            "frontend.error-parameter-type", "javac reported an error type for a parameter", origin));
      } else if (type.category() == JavaTypeCategory.OTHER) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(parameterKey,
            "frontend.unresolved-parameter-type", "A parameter type is unavailable", origin));
      }
      parameters.add(new JavaParameter(parameterKey, name, type, written && origin.isPresent(), origin.stream().toList()));
    }
    return new ParameterTranslation(parameters, diagnostics, occurrences, observations);
  }

  private void diagnoseUnsupportedDeclarationProperties(CompilationUnitTree unit, SourceFile sourceFile, String methodKey,
      MethodTree methodTree, List<AnalysisDiagnostic> diagnostics) {
    if (methodTree.getReceiverParameter() != null) {
      diagnoseUnsupportedVariableProperties(unit, sourceFile, methodKey + ":receiver", methodTree.getReceiverParameter(), true,
          diagnostics);
    }
  }

  private void diagnoseUnsupportedVariableProperties(CompilationUnitTree unit, SourceFile sourceFile, String context,
      VariableTree variableTree, boolean receiver, List<AnalysisDiagnostic> diagnostics) {
    if (variableTree.getInitializer() != null) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(context,
              receiver ? "frontend.invalid-receiver-initializer" : "frontend.invalid-parameter-initializer",
              "A parameter cannot have an initializer", sourceEvidence.origin(unit, sourceFile, variableTree.getInitializer())));
    }
  }

  private static TreePath parameterPath(TreePath methodPath, VariableTree parameterTree) {
    return new TreePath(methodPath, parameterTree);
  }

  private static JavaTypeIdentity parameterType(SyntaxType type) {
    if (type instanceof SyntaxType.Resolved resolved) {
      return resolved.identity();
    }
    return type == SyntaxType.Erroneous.INSTANCE
        ? new JavaTypeIdentity(JavaTypeCategory.ERROR, "error", "error")
        : new JavaTypeIdentity(JavaTypeCategory.OTHER, UNRESOLVED_TYPE_NAME, UNRESOLVED_TYPE_NAME);
  }

  private JavaTypeIdentity parameterType(VariableElement parameter) {
    return semantics.typeReference(parameter.asType()).map(reference -> reference.identity())
        .orElseGet(() -> new JavaTypeIdentity(JavaTypeCategory.OTHER, UNRESOLVED_TYPE_NAME, UNRESOLVED_TYPE_NAME));
  }

  private TranslationResult<Optional<TranslatedMethodBody>> translateBody(CompilationUnitTree unit, SourceFile sourceFile, String methodKey,
      TreePath methodPath, MethodTree methodTree, boolean constructor, boolean implicitDeclaration) {
    if (methodTree.getBody() == null) {
      return new TranslationResult<>(Optional.empty(), List.of(), List.of(), List.of());
    }
    var bodyPath = new TreePath(methodPath, methodTree.getBody());
    var translation = bodyTranslator.translate(unit, sourceFile, methodKey, methodTree.getBody(), bodyPath, constructor,
        implicitDeclaration);
    return new TranslationResult<>(Optional.of(translation.value()), translation.diagnostics(), translation.featureOccurrences(),
        translation.featureObservations());
  }

  private record ParameterTranslation(List<JavaParameter> parameters, List<AnalysisDiagnostic> diagnostics,
      List<LanguageFeatureOccurrence> featureOccurrences, List<LanguageFeatureObservation> featureObservations) {
    private ParameterTranslation {
      parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
      diagnostics = TranslationDiagnosticEvidence.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
      featureOccurrences = List.copyOf(Objects.requireNonNull(featureOccurrences, "featureOccurrences"));
      featureObservations = List.copyOf(Objects.requireNonNull(featureObservations, "featureObservations"));
    }
  }
}
