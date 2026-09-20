package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.JavaParameter;
import org.aquilagraph.model.MethodBody;
import org.aquilagraph.model.SourceFile;
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

  TranslationResult<JavaMethod> translate(CompilationUnitTree unit, TreePath classPath, SourceFile sourceFile, String ownerTypeKey,
      MethodTree methodTree) {
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(classPath, "classPath");
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(ownerTypeKey, "ownerTypeKey");
    Objects.requireNonNull(methodTree, "methodTree");
    var methodPath = new TreePath(classPath, methodTree);
    var element = semantics.element(methodPath);
    var executable = element instanceof ExecutableElement value ? value : null;
    var constructor = executable != null && executable.getKind() == ElementKind.CONSTRUCTOR;
    var name = constructor ? "<init>" : methodTree.getName().toString();
    var parameterTypes = parameterTypes(methodTree, executable);
    var signature = name + "(" + String.join(",", parameterTypes) + ")";
    var methodKey = ownerTypeKey + "#" + signature;
    var parameters = parameters(unit, sourceFile, methodKey, methodTree, parameterTypes);
    var modifiers = methodTree.getModifiers().getFlags().stream().map(modifier -> modifier.name().toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());
    var origins = sourceEvidence.origins(unit, sourceFile, methodTree);
    var bodyTranslation = translateBody(unit, sourceFile, methodKey, methodPath, methodTree);
    var returnType = constructor
        ? "void"
        : executable == null ? String.valueOf(methodTree.getReturnType()) : executable.getReturnType().toString();
    var method = new JavaMethod(methodKey, ownerTypeKey, name, signature, returnType, parameters, modifiers, constructor,
        !origins.isEmpty(), bodyTranslation.value(), origins);
    return new TranslationResult<>(method, bodyTranslation.diagnostics());
  }

  private static List<String> parameterTypes(MethodTree methodTree, @Nullable ExecutableElement executable) {
    if (executable == null) {
      return methodTree.getParameters().stream().map(parameter -> parameter.getType().toString()).toList();
    }
    return executable.getParameters().stream().map(parameter -> parameter.asType().toString()).toList();
  }

  private List<JavaParameter> parameters(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, MethodTree methodTree,
      List<String> parameterTypes) {
    var parameters = new ArrayList<JavaParameter>();
    for (var index = 0; index < methodTree.getParameters().size(); index++) {
      var parameter = methodTree.getParameters().get(index);
      parameters.add(new JavaParameter(methodKey + ":parameter:" + index, parameter.getName().toString(), parameterTypes.get(index),
          sourceEvidence.origins(unit, sourceFile, parameter)));
    }
    return List.copyOf(parameters);
  }

  private TranslationResult<Optional<MethodBody>> translateBody(CompilationUnitTree unit, SourceFile sourceFile, String methodKey,
      TreePath methodPath, MethodTree methodTree) {
    if (methodTree.getBody() == null) {
      return new TranslationResult<>(Optional.empty(), List.of());
    }
    var bodyPath = new TreePath(methodPath, methodTree.getBody());
    var translation = bodyTranslator.translate(unit, sourceFile, methodKey, methodTree.getBody(), bodyPath);
    return new TranslationResult<>(Optional.of(translation.value()), translation.diagnostics());
  }
}
