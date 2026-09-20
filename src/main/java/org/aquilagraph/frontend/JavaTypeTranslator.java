package org.aquilagraph.frontend;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.Objects;
import javax.lang.model.element.TypeElement;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.JavaType;
import org.aquilagraph.model.JavaTypeKind;
import org.aquilagraph.model.SourceFile;

final class JavaTypeTranslator {
  private final JavaSemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final JavaMethodTranslator methodTranslator;

  JavaTypeTranslator(JavaSemanticResolver semantics, JavaSourceEvidence sourceEvidence, JavaMethodTranslator methodTranslator) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.methodTranslator = Objects.requireNonNull(methodTranslator, "methodTranslator");
  }

  TranslationResult<JavaType> translate(CompilationUnitTree unit, TreePath unitPath, SourceFile sourceFile, ClassTree classTree) {
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(unitPath, "unitPath");
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(classTree, "classTree");
    var classPath = new TreePath(unitPath, classTree);
    var element = semantics.element(classPath);
    var qualifiedName = element instanceof TypeElement typeElement
        ? typeElement.getQualifiedName().toString()
        : fallbackQualifiedName(unit, classTree);
    var typeKey = "type:" + qualifiedName;
    var methods = new ArrayList<JavaMethod>();
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    for (var member : classTree.getMembers()) {
      if (member instanceof MethodTree methodTree) {
        var translation = methodTranslator.translate(unit, classPath, sourceFile, typeKey, methodTree);
        methods.add(translation.value());
        diagnostics.addAll(translation.diagnostics());
      }
    }
    var origins = sourceEvidence.origins(unit, sourceFile, classTree);
    var type = new JavaType(typeKey, qualifiedName, javaTypeKind(classTree.getKind()), !origins.isEmpty(), methods, origins);
    return new TranslationResult<>(type, diagnostics);
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
