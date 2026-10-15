package org.aquilagraph.frontend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxSemanticEdge;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget;

/** Stages declaration references at an explicit lexical parent and owns shared header correspondence. */
final class JavaDeclarationScope {
  private final Optional<JavaDeclarationScope> parent;
  private final List<DeclarationReference> references = new ArrayList<>();
  private final Map<String, JavaSharedAnnotations> fields;
  private final Map<String, JavaSharedAnnotations> locals;

  JavaDeclarationScope() {
    parent = Optional.empty();
    fields = new HashMap<>();
    locals = new HashMap<>();
  }

  private JavaDeclarationScope(JavaDeclarationScope parent) {
    this.parent = Optional.of(parent);
    fields = parent.fields;
    locals = parent.locals;
  }

  JavaDeclarationScope child() {
    return new JavaDeclarationScope(this);
  }

  void accept() {
    parent.orElseThrow().references.addAll(references);
  }

  void add(SyntaxNode source, SemanticReference reference) {
    references.add(new DeclarationReference(source, reference));
  }

  JavaSharedAnnotations fieldAnnotations(String headerKey) {
    return fields.computeIfAbsent(headerKey, ignored -> new JavaSharedAnnotations());
  }

  JavaSharedAnnotations localAnnotations(String headerKey) {
    return locals.computeIfAbsent(headerKey, ignored -> new JavaSharedAnnotations());
  }

  List<SyntaxSemanticEdge> rebind(JavaSourceCopyCorrespondence copies) {
    return references.stream().map(reference -> new SyntaxSemanticEdge(copies.finalCopy(reference.source()).nodeId(),
        SyntaxSemanticRelation.DECLARATION_REFERENCE, new SyntaxSemanticTarget.Declaration(reference.reference()))).toList();
  }

  private record DeclarationReference(SyntaxNode source, SemanticReference reference) {
  }
}
