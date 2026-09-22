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
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.model.StableId;
import org.aquilagraph.syntax.MethodSyntax;
import org.aquilagraph.syntax.SyntaxType;
import org.jspecify.annotations.Nullable;

final class JavaMethodTranslator {
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final JavaMethodBodyTranslator bodyTranslator;

  JavaMethodTranslator(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, JavaMethodBodyTranslator bodyTranslator) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
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
    if (executable == null) {
      diagnostics.add(diagnostic(methodKey, "frontend.unresolved-method", "A method declaration element is unavailable", origin));
    }
    diagnoseUnsupportedDeclarationProperties(unit, sourceFile, methodKey, methodTree, executable, diagnostics);
    var parameterTranslation = parameters(unit, sourceFile, methodKey, methodPath, methodTree, executable);
    diagnostics.addAll(parameterTranslation.diagnostics());
    var semanticModifiers = executable == null ? methodTree.getModifiers().getFlags() : executable.getModifiers();
    var modifiers = semanticModifiers.stream().map(modifier -> modifier.name().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    var bodyTranslation = translateBody(unit, sourceFile, methodKey, methodPath, methodTree);
    diagnostics.addAll(bodyTranslation.diagnostics());
    var returnType = constructor ? "void" : executable == null ? "unresolved" : executable.getReturnType().toString();
    var method = new JavaMethod(methodKey, ownerTypeKey, name, signature, returnType, parameterTranslation.parameters(), modifiers,
        constructor, origin.isPresent(), bodyTranslation.value().isPresent(), origin.stream().toList());
    return new TranslationResult<>(new TranslatedMethod(method, bodyTranslation.value()), diagnostics);
  }

  private ParameterTranslation parameters(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, TreePath methodPath,
      MethodTree methodTree, @Nullable ExecutableElement executable) {
    var parameters = new ArrayList<JavaParameter>();
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    for (var index = 0; index < methodTree.getParameters().size(); index++) {
      var parameterTree = methodTree.getParameters().get(index);
      var parameterPath = parameterPath(methodPath, parameterTree);
      var origin = sourceEvidence.origin(unit, sourceFile, parameterTree);
      var parameterElement = executable == null || index >= executable.getParameters().size()
          ? null
          : executable.getParameters().get(index);
      var parameterKey = methodKey + ":parameter:" + index;
      if (parameterElement == null || semantics.declarationKey(parameterElement).filter(parameterKey::equals).isEmpty()) {
        diagnostics.add(diagnostic(parameterKey, "frontend.unresolved-parameter",
            "A parameter declaration identity is unavailable or inconsistent", origin));
      }
      diagnoseUnsupportedVariableProperties(unit, sourceFile, parameterKey, "Parameter", parameterTree, diagnostics);
      var type = parameterType(semantics.type(parameterPath));
      if (type.category() == JavaTypeCategory.ERROR) {
        diagnostics.add(diagnostic(parameterKey, "frontend.error-parameter-type", "javac reported an error type for a parameter", origin));
      } else if (type.category() == JavaTypeCategory.OTHER) {
        diagnostics.add(diagnostic(parameterKey, "frontend.unresolved-parameter-type", "A parameter type is unavailable", origin));
      }
      parameters.add(new JavaParameter(parameterKey, parameterTree.getName().toString(), type, origin.stream().toList()));
    }
    return new ParameterTranslation(parameters, diagnostics);
  }

  private void diagnoseUnsupportedDeclarationProperties(CompilationUnitTree unit, SourceFile sourceFile, String methodKey,
      MethodTree methodTree, @Nullable ExecutableElement executable, List<AnalysisDiagnostic> diagnostics) {
    if (!methodTree.getTypeParameters().isEmpty()) {
      diagnostics.add(diagnostic(methodKey, JavaDeclarationPropertyCoverage.METHOD_TYPE_PARAMETERS_DIAGNOSTIC,
          "Method type parameters are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, methodTree.getTypeParameters().getFirst())));
    }
    if (!methodTree.getThrows().isEmpty()) {
      diagnostics.add(diagnostic(methodKey, JavaDeclarationPropertyCoverage.THROWN_TYPES_DIAGNOSTIC,
          "Declared thrown types are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, methodTree.getThrows().getFirst())));
    }
    if (methodTree.getReceiverParameter() != null) {
      diagnostics.add(diagnostic(methodKey, JavaDeclarationPropertyCoverage.RECEIVER_PARAMETER_DIAGNOSTIC,
          "Receiver parameters are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, methodTree.getReceiverParameter())));
      diagnoseUnsupportedVariableProperties(unit, sourceFile, methodKey + ":receiver", "Receiver parameter",
          methodTree.getReceiverParameter(), diagnostics);
    }
    if (executable != null && executable.isVarArgs()) {
      var varargsTree = methodTree.getParameters().getLast();
      diagnostics.add(diagnostic(methodKey, JavaDeclarationPropertyCoverage.VARARGS_DIAGNOSTIC,
          "Variable-arity declarations are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, varargsTree)));
    }
    for (var annotation : methodTree.getModifiers().getAnnotations()) {
      diagnostics.add(diagnostic(methodKey, JavaDeclarationPropertyCoverage.DECLARATION_ANNOTATIONS_DIAGNOSTIC,
          "Method and constructor declaration annotations are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, annotation)));
    }
    if (methodTree.getDefaultValue() != null) {
      diagnostics.add(diagnostic(methodKey, JavaDeclarationPropertyCoverage.ANNOTATION_DEFAULT_DIAGNOSTIC,
          "Annotation element defaults are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, methodTree.getDefaultValue())));
    }
  }

  private void diagnoseUnsupportedVariableProperties(CompilationUnitTree unit, SourceFile sourceFile, String context,
      String declarationKind, VariableTree variableTree, List<AnalysisDiagnostic> diagnostics) {
    var origin = sourceEvidence.origin(unit, sourceFile, variableTree);
    if (!variableTree.getModifiers().getFlags().isEmpty()) {
      diagnostics.add(diagnostic(context, JavaDeclarationPropertyCoverage.VARIABLE_MODIFIERS_DIAGNOSTIC,
          declarationKind + " modifiers are outside the supported semantic graph profile", origin));
    }
    for (var annotation : variableTree.getModifiers().getAnnotations()) {
      diagnostics.add(diagnostic(context, JavaDeclarationPropertyCoverage.DECLARATION_ANNOTATIONS_DIAGNOSTIC,
          declarationKind + " annotations are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, annotation)));
    }
    if (variableTree.getNameExpression() != null) {
      diagnostics.add(diagnostic(context, JavaDeclarationPropertyCoverage.VARIABLE_NAME_EXPRESSION_DIAGNOSTIC,
          declarationKind + " name expressions are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, variableTree.getNameExpression())));
    }
    if (variableTree.getInitializer() != null) {
      diagnostics.add(diagnostic(context, JavaDeclarationPropertyCoverage.VARIABLE_INITIALIZER_DIAGNOSTIC,
          declarationKind + " initializers are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, variableTree.getInitializer())));
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

  private TranslationResult<Optional<MethodSyntax>> translateBody(CompilationUnitTree unit, SourceFile sourceFile, String methodKey,
      TreePath methodPath, MethodTree methodTree) {
    if (methodTree.getBody() == null) {
      return new TranslationResult<>(Optional.empty(), List.of());
    }
    var bodyPath = new TreePath(methodPath, methodTree.getBody());
    var translation = bodyTranslator.translate(unit, sourceFile, methodKey, methodTree.getBody(), bodyPath);
    return new TranslationResult<>(Optional.of(translation.value()), translation.diagnostics());
  }

  private static AnalysisDiagnostic diagnostic(String context, String code, String message, Optional<SourceSpan> origin) {
    return FrontendDiagnosticFactory.error(context, code, message, origin);
  }

  private record ParameterTranslation(List<JavaParameter> parameters, List<AnalysisDiagnostic> diagnostics) {
    private ParameterTranslation {
      parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
      diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
  }
}
