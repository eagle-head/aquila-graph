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
import org.aquilagraph.model.DiagnosticSeverity;
import org.aquilagraph.model.DiagnosticStage;
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
  private final JavaSemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final JavaMethodBodyTranslator bodyTranslator;

  JavaMethodTranslator(JavaSemanticResolver semantics, JavaSourceEvidence sourceEvidence, JavaMethodBodyTranslator bodyTranslator) {
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
    var parameterTranslation = parameters(unit, sourceFile, methodKey, methodPath, methodTree, executable);
    diagnostics.addAll(parameterTranslation.diagnostics());
    var modifiers = methodTree.getModifiers().getFlags().stream().map(modifier -> modifier.name().toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());
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
    return new AnalysisDiagnostic(StableId.sha256("diagnostic", context, code), code, DiagnosticStage.FRONTEND, DiagnosticSeverity.ERROR,
        message, origin.stream().toList());
  }

  private record ParameterTranslation(List<JavaParameter> parameters, List<AnalysisDiagnostic> diagnostics) {
    private ParameterTranslation {
      parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
      diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
  }
}
