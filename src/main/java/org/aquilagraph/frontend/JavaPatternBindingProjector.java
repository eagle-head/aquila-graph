package org.aquilagraph.frontend;

import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.SyntaxValue;

/** Copies compiler-selected pattern accesses without reconstructing flow analysis or CFG edges. */
final class JavaPatternBindingProjector {
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final Map<SourceSpan, Map<Element, List<SourceSpan>>> methodAccesses = new HashMap<>();

  JavaPatternBindingProjector(SemanticResolver semantics, JavaSourceEvidence sourceEvidence) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
  }

  SyntaxValue.PatternBinding project(CompilationUnitTree unit, SourceFile source, BindingPatternTree pattern, TreePath path) {
    var binding = semantics.element(new TreePath(path, pattern.getVariable()));
    var owner = path;
    while (owner != null && !(owner.getLeaf() instanceof MethodTree)) {
      owner = owner.getParentPath();
    }
    if (binding == null || owner == null || binding.getKind() != ElementKind.BINDING_VARIABLE) {
      return new SyntaxValue.PatternBinding(Optional.empty());
    }
    var methodPath = owner;
    var methodOrigin = sourceEvidence.origin(unit, source, owner.getLeaf());
    if (methodOrigin.isEmpty()) {
      return new SyntaxValue.PatternBinding(Optional.empty());
    }
    var accesses = methodAccesses.computeIfAbsent(methodOrigin.orElseThrow(), ignored -> capture(unit, source, methodPath));
    return new SyntaxValue.PatternBinding(Optional.of(accesses.getOrDefault(binding, List.of())));
  }

  private Map<Element, List<SourceSpan>> capture(CompilationUnitTree unit, SourceFile source, TreePath owner) {
    var accesses = new HashMap<Element, List<SourceSpan>>();
    new TreePathScanner<Void, Void>() {
      @Override
      public Void visitIdentifier(IdentifierTree identifier, Void unused) {
        var element = semantics.element(getCurrentPath());
        if (element != null && element.getKind() == ElementKind.BINDING_VARIABLE) {
          sourceEvidence.origin(unit, source, identifier)
              .ifPresent(origin -> accesses.computeIfAbsent(element, ignored -> new ArrayList<>()).add(origin));
        }
        return super.visitIdentifier(identifier, null);
      }
    }.scan(owner, null);
    return accesses;
  }
}
