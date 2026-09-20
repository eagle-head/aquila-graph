package org.aquilagraph.frontend;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.JavaParameter;
import org.aquilagraph.model.JavaProgram;
import org.aquilagraph.model.JavaType;
import org.aquilagraph.model.JavaTypeKind;
import org.aquilagraph.model.MethodBody;
import org.aquilagraph.model.SourceFile;

final class JavaProgramTranslator {
  private final SnapshotSourceCatalog sources;
  private final JavaTreeSemantics semantics;

  JavaProgramTranslator(SnapshotSourceCatalog sources, JavaTreeSemantics semantics) {
    this.sources = Objects.requireNonNull(sources, "sources");
    this.semantics = Objects.requireNonNull(semantics, "semantics");
  }

  JavaProgramTranslation translate(List<CompilationUnitTree> units) {
    Objects.requireNonNull(units, "units");
    var types = new ArrayList<JavaType>();
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    for (var unit : units) {
      var sourceFile = sources.find(unit).orElse(null);
      if (sourceFile == null) {
        continue;
      }
      var unitPath = new TreePath(unit);
      for (var declaration : unit.getTypeDecls()) {
        if (declaration instanceof ClassTree classTree) {
          types.add(translateType(unit, unitPath, sourceFile, classTree, diagnostics));
        }
      }
    }
    return new JavaProgramTranslation(new JavaProgram(types), diagnostics);
  }

  private JavaType translateType(
      CompilationUnitTree unit,
      TreePath unitPath,
      SourceFile sourceFile,
      ClassTree classTree,
      List<AnalysisDiagnostic> diagnostics) {
    var classPath = new TreePath(unitPath, classTree);
    var element = semantics.element(classPath);
    var qualifiedName =
        element instanceof TypeElement typeElement
            ? typeElement.getQualifiedName().toString()
            : fallbackQualifiedName(unit, classTree);
    var typeKey = "type:" + qualifiedName;
    var methods = new ArrayList<JavaMethod>();
    for (var member : classTree.getMembers()) {
      if (member instanceof MethodTree methodTree) {
        methods.add(
            translateMethod(unit, classPath, sourceFile, typeKey, methodTree, diagnostics));
      }
    }
    var origins = semantics.sourceOrigins(unit, sourceFile, classTree);
    return new JavaType(
        typeKey,
        qualifiedName,
        javaTypeKind(classTree.getKind()),
        !origins.isEmpty(),
        methods,
        origins);
  }

  private JavaMethod translateMethod(
      CompilationUnitTree unit,
      TreePath classPath,
      SourceFile sourceFile,
      String ownerTypeKey,
      MethodTree methodTree,
      List<AnalysisDiagnostic> diagnostics) {
    var methodPath = new TreePath(classPath, methodTree);
    var element = semantics.element(methodPath);
    var executable = element instanceof ExecutableElement value ? value : null;
    var constructor = executable != null && executable.getKind() == ElementKind.CONSTRUCTOR;
    var name = constructor ? "<init>" : methodTree.getName().toString();
    var parameterTypes = parameterTypes(methodTree, executable);
    var signature = name + "(" + String.join(",", parameterTypes) + ")";
    var methodKey = ownerTypeKey + "#" + signature;
    var parameters = parameters(unit, sourceFile, methodKey, methodTree, parameterTypes);
    var modifiers =
        methodTree.getModifiers().getFlags().stream()
            .map(modifier -> modifier.name().toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
    var origins = semantics.sourceOrigins(unit, sourceFile, methodTree);
    var body = translateBody(unit, sourceFile, methodKey, methodPath, methodTree, diagnostics);
    var returnType =
        constructor
            ? "void"
            : executable == null
                ? String.valueOf(methodTree.getReturnType())
                : executable.getReturnType().toString();
    return new JavaMethod(
        methodKey,
        ownerTypeKey,
        name,
        signature,
        returnType,
        parameters,
        modifiers,
        constructor,
        !origins.isEmpty(),
        body,
        origins);
  }

  private List<String> parameterTypes(MethodTree methodTree, ExecutableElement executable) {
    if (executable == null) {
      return methodTree.getParameters().stream()
          .map(parameter -> parameter.getType().toString())
          .toList();
    }
    return executable.getParameters().stream()
        .map(parameter -> parameter.asType().toString())
        .toList();
  }

  private List<JavaParameter> parameters(
      CompilationUnitTree unit,
      SourceFile sourceFile,
      String methodKey,
      MethodTree methodTree,
      List<String> parameterTypes) {
    var parameters = new ArrayList<JavaParameter>();
    for (var index = 0; index < methodTree.getParameters().size(); index++) {
      var parameter = methodTree.getParameters().get(index);
      parameters.add(
          new JavaParameter(
              methodKey + ":parameter:" + index,
              parameter.getName().toString(),
              parameterTypes.get(index),
              semantics.sourceOrigins(unit, sourceFile, parameter)));
    }
    return List.copyOf(parameters);
  }

  private Optional<MethodBody> translateBody(
      CompilationUnitTree unit,
      SourceFile sourceFile,
      String methodKey,
      TreePath methodPath,
      MethodTree methodTree,
      List<AnalysisDiagnostic> diagnostics) {
    if (methodTree.getBody() == null) {
      return Optional.empty();
    }
    var bodyPath = new TreePath(methodPath, methodTree.getBody());
    var translation =
        new JavaSyntaxTranslator(semantics, unit, sourceFile, methodKey)
            .translate(methodTree.getBody(), bodyPath);
    diagnostics.addAll(translation.diagnostics());
    return Optional.of(translation.body());
  }

  private static String fallbackQualifiedName(
      CompilationUnitTree unit, ClassTree classTree) {
    var packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
    return packageName.isEmpty()
        ? classTree.getSimpleName().toString()
        : packageName + "." + classTree.getSimpleName();
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
