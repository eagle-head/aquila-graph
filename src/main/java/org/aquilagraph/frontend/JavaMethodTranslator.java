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
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxType;
import org.jspecify.annotations.Nullable;

final class JavaMethodTranslator {
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final JavaDeclarationPropertyObserver propertyObserver;
  private final JavaMethodBodyTranslator bodyTranslator;

  JavaMethodTranslator(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, JavaDeclarationPropertyObserver propertyObserver,
      JavaMethodBodyTranslator bodyTranslator) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.propertyObserver = Objects.requireNonNull(propertyObserver, "propertyObserver");
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
    var origin = sourceEvidence.origin(unit, sourceFile, methodTree);
    var element = semantics.element(methodPath);
    var executable = element instanceof ExecutableElement value ? value : null;
    var constructor = executable != null ? executable.getKind() == ElementKind.CONSTRUCTOR : methodTree.getReturnType() == null;
    var name = constructor ? "<init>" : methodTree.getName().toString();
    var signature = executable == null ? name + "(unresolved)" : semantics.executableSignature(executable);
    var methodKey = executable == null
        ? ownerTypeKey + "#unresolved-executable:"
            + origin.map(value -> value.startOffset() + ":" + value.endOffsetExclusive())
                .orElseGet(() -> StableId.sha256("unresolved-executable", ownerTypeKey, name))
        : semantics.declarationKey(executable).orElseThrow();
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    var occurrences = new ArrayList<LanguageFeatureOccurrence>();
    var observations = new ArrayList<LanguageFeatureObservation>();
    if (executable == null) {
      diagnostics.add(FrontendDiagnosticFactory.otherError(methodKey, "frontend.unresolved-method",
          "A method declaration element is unavailable", origin));
    }
    diagnoseUnsupportedDeclarationProperties(unit, sourceFile, methodKey, methodTree, executable, diagnostics, occurrences, observations);
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
    var returnType = constructor ? "void" : executable == null ? "unresolved" : executable.getReturnType().toString();
    var method = new JavaMethod(methodKey, ownerTypeKey, name, signature, returnType, parameterTranslation.parameters(), modifiers,
        constructor, origin.isPresent(), bodyTranslation.value().isPresent(), origin.stream().toList());
    return new TranslationResult<>(new TranslatedMethod(method, bodyTranslation.value()), diagnostics, occurrences, observations);
  }

  private ParameterTranslation parameters(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, TreePath methodPath,
      MethodTree methodTree, @Nullable ExecutableElement executable) {
    var parameters = new ArrayList<JavaParameter>();
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    var occurrences = new ArrayList<LanguageFeatureOccurrence>();
    var observations = new ArrayList<LanguageFeatureObservation>();
    for (var index = 0; index < methodTree.getParameters().size(); index++) {
      var parameterTree = methodTree.getParameters().get(index);
      var parameterPath = parameterPath(methodPath, parameterTree);
      var origin = sourceEvidence.origin(unit, sourceFile, parameterTree);
      var parameterElement = executable == null || index >= executable.getParameters().size()
          ? null
          : executable.getParameters().get(index);
      var parameterKey = methodKey + ":parameter:" + index;
      if (parameterElement == null || semantics.declarationKey(parameterElement).filter(parameterKey::equals).isEmpty()) {
        diagnostics.add(FrontendDiagnosticFactory.otherError(parameterKey, "frontend.unresolved-parameter",
            "A parameter declaration identity is unavailable or inconsistent", origin));
      }
      diagnoseUnsupportedVariableProperties(unit, sourceFile, parameterKey, "Parameter", parameterTree, diagnostics, occurrences,
          observations);
      var type = parameterType(semantics.type(parameterPath));
      if (type.category() == JavaTypeCategory.ERROR) {
        diagnostics.add(FrontendDiagnosticFactory.erroneousCompilerState(parameterKey, "frontend.error-parameter-type",
            "javac reported an error type for a parameter", origin));
      } else if (type.category() == JavaTypeCategory.OTHER) {
        diagnostics.add(FrontendDiagnosticFactory.otherError(parameterKey, "frontend.unresolved-parameter-type",
            "A parameter type is unavailable", origin));
      }
      parameters.add(new JavaParameter(parameterKey, parameterTree.getName().toString(), type, origin.stream().toList()));
    }
    return new ParameterTranslation(parameters, diagnostics, occurrences, observations);
  }

  private void diagnoseUnsupportedDeclarationProperties(CompilationUnitTree unit, SourceFile sourceFile, String methodKey,
      MethodTree methodTree, @Nullable ExecutableElement executable, List<AnalysisDiagnostic> diagnostics,
      List<LanguageFeatureOccurrence> occurrences, List<LanguageFeatureObservation> observations) {
    if (!methodTree.getTypeParameters().isEmpty()) {
      observeMethodAndExecutableProperty(diagnostics, occurrences, observations, sourceFile, methodKey,
          JavaDeclarationPropertyCoverage.METHOD_TYPE_PARAMETERS_DIAGNOSTIC,
          "Method type parameters are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, methodTree.getTypeParameters().getFirst()), executable,
          JavaDeclarationPropertyCoverage.METHOD_TYPE_PARAMETERS, JavaDeclarationPropertyCoverage.EXECUTABLE_ELEMENT_TYPE_PARAMETERS);
    }
    if (!methodTree.getThrows().isEmpty()) {
      observeMethodAndExecutableProperty(diagnostics, occurrences, observations, sourceFile, methodKey,
          JavaDeclarationPropertyCoverage.THROWN_TYPES_DIAGNOSTIC, "Declared thrown types are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, methodTree.getThrows().getFirst()), executable,
          JavaDeclarationPropertyCoverage.METHOD_THROWS, JavaDeclarationPropertyCoverage.EXECUTABLE_ELEMENT_THROWN_TYPES);
    }
    if (methodTree.getReceiverParameter() != null) {
      observeMethodAndExecutableProperty(diagnostics, occurrences, observations, sourceFile, methodKey,
          JavaDeclarationPropertyCoverage.RECEIVER_PARAMETER_DIAGNOSTIC,
          "Receiver parameters are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, methodTree.getReceiverParameter()), executable,
          JavaDeclarationPropertyCoverage.METHOD_RECEIVER_PARAMETER, JavaDeclarationPropertyCoverage.EXECUTABLE_ELEMENT_RECEIVER_TYPE);
      diagnoseUnsupportedVariableProperties(unit, sourceFile, methodKey + ":receiver", "Receiver parameter",
          methodTree.getReceiverParameter(), diagnostics, occurrences, observations);
    }
    if (executable != null && executable.isVarArgs()) {
      var varargsTree = methodTree.getParameters().getLast();
      propertyObserver.observe(diagnostics, occurrences, observations, sourceFile, methodKey,
          JavaDeclarationPropertyCoverage.VARARGS_DIAGNOSTIC,
          "Variable-arity declarations are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, varargsTree), JavaDeclarationPropertyCoverage.EXECUTABLE_ELEMENT_VARARGS);
    }
    if (executable != null) {
      for (var annotation : methodTree.getModifiers().getAnnotations()) {
        propertyObserver.observe(diagnostics, occurrences, observations, sourceFile, methodKey,
            JavaDeclarationPropertyCoverage.DECLARATION_ANNOTATIONS_DIAGNOSTIC,
            "Method and constructor declaration annotations are outside the supported semantic graph profile",
            sourceEvidence.origin(unit, sourceFile, annotation), JavaDeclarationPropertyCoverage.EXECUTABLE_ELEMENT_ANNOTATION_MIRRORS);
      }
    }
    if (methodTree.getDefaultValue() != null) {
      observeMethodAndExecutableProperty(diagnostics, occurrences, observations, sourceFile, methodKey,
          JavaDeclarationPropertyCoverage.ANNOTATION_DEFAULT_DIAGNOSTIC,
          "Annotation element defaults are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, methodTree.getDefaultValue()), executable,
          JavaDeclarationPropertyCoverage.METHOD_DEFAULT_VALUE, JavaDeclarationPropertyCoverage.EXECUTABLE_ELEMENT_DEFAULT_VALUE);
    }
  }

  private void diagnoseUnsupportedVariableProperties(CompilationUnitTree unit, SourceFile sourceFile, String context,
      String declarationKind, VariableTree variableTree, List<AnalysisDiagnostic> diagnostics, List<LanguageFeatureOccurrence> occurrences,
      List<LanguageFeatureObservation> observations) {
    var origin = sourceEvidence.origin(unit, sourceFile, variableTree);
    if (!variableTree.getModifiers().getFlags().isEmpty()) {
      propertyObserver.observe(diagnostics, occurrences, observations, sourceFile, context,
          JavaDeclarationPropertyCoverage.VARIABLE_MODIFIERS_DIAGNOSTIC,
          declarationKind + " modifiers are outside the supported semantic graph profile", origin,
          JavaDeclarationPropertyCoverage.VARIABLE_MODIFIERS);
    }
    for (var annotation : variableTree.getModifiers().getAnnotations()) {
      propertyObserver.observe(diagnostics, occurrences, observations, sourceFile, context,
          JavaDeclarationPropertyCoverage.VARIABLE_MODIFIERS_DIAGNOSTIC,
          declarationKind + " annotations are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, annotation), JavaDeclarationPropertyCoverage.VARIABLE_MODIFIERS);
    }
    if (variableTree.getNameExpression() != null) {
      propertyObserver.observe(diagnostics, occurrences, observations, sourceFile, context,
          JavaDeclarationPropertyCoverage.VARIABLE_NAME_EXPRESSION_DIAGNOSTIC,
          declarationKind + " name expressions are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, variableTree.getNameExpression()),
          JavaDeclarationPropertyCoverage.VARIABLE_NAME_EXPRESSION);
    }
    if (variableTree.getInitializer() != null) {
      propertyObserver.observe(diagnostics, occurrences, observations, sourceFile, context,
          JavaDeclarationPropertyCoverage.VARIABLE_INITIALIZER_DIAGNOSTIC,
          declarationKind + " initializers are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, variableTree.getInitializer()), JavaDeclarationPropertyCoverage.VARIABLE_INITIALIZER);
    }
  }

  private void observeMethodAndExecutableProperty(List<AnalysisDiagnostic> diagnostics, List<LanguageFeatureOccurrence> occurrences,
      List<LanguageFeatureObservation> observations, SourceFile sourceFile, String context, String code, String message,
      Optional<SourceSpan> origin, @Nullable ExecutableElement executable, String methodFeature, String executableFeature) {
    if (executable == null) {
      propertyObserver.observe(diagnostics, occurrences, observations, sourceFile, context, code, message, origin, methodFeature);
    } else {
      propertyObserver.observe(diagnostics, occurrences, observations, sourceFile, context, code, message, origin, methodFeature,
          executableFeature);
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
        : new JavaTypeIdentity(JavaTypeCategory.OTHER, "unresolved", "unresolved");
  }

  private TranslationResult<Optional<SyntaxNode>> translateBody(CompilationUnitTree unit, SourceFile sourceFile, String methodKey,
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
      diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
      featureOccurrences = List.copyOf(Objects.requireNonNull(featureOccurrences, "featureOccurrences"));
      featureObservations = List.copyOf(Objects.requireNonNull(featureObservations, "featureObservations"));
    }
  }
}
