package org.aquilagraph.frontend.internal;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceFile;

final class JavaMethodTranslator {
  private final SemanticResolver semantics;
  private final JavaMethodDeclarationTranslator declarations;
  private final JavaMethodBodyTranslator bodies;

  JavaMethodTranslator(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory,
      JavaMethodBodyTranslator bodyTranslator) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    declarations = new JavaMethodDeclarationTranslator(semantics, sourceEvidence, treeInventory);
    bodies = Objects.requireNonNull(bodyTranslator, "bodyTranslator");
  }

  TranslationResult<TranslatedMethod> translate(CompilationUnitTree unit, TreePath classPath, SourceFile sourceFile, String ownerTypeKey,
      MethodTree methodTree) {
    var declaration = declarations.translate(unit, classPath, sourceFile, ownerTypeKey, methodTree);
    var method = declaration.value();
    var diagnostics = new DiagnosticEmissionCollector("JavaMethodTranslator", method.methodKey() + ":declaration",
        new DiagnosticSubject.Declaration(method.methodKey()));
    diagnostics.include(declaration.emissions());
    var body = Optional.<TranslatedMethodBody>empty();
    if (methodTree.getBody() != null) {
      var path = new TreePath(classPath, methodTree);
      var translation = bodies.translate(unit, sourceFile, method.methodKey(), methodTree.getBody(),
          new TreePath(path, methodTree.getBody()), method.constructor(), semantics.isImplicitDeclaration(path));
      body = Optional.of(translation.value());
      diagnostics.include(translation.emissions());
    }
    return new TranslationResult<>(new TranslatedMethod(method, body), diagnostics.snapshot());
  }
}
