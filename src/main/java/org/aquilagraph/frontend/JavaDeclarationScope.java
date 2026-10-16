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
  private boolean closed;
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
    fields = new HashMap<>();
    locals = new HashMap<>();
  }

  JavaDeclarationScope child() {
    requireOpen();
    return new JavaDeclarationScope(this);
  }

  void accept() {
    requireOpen();
    var owner = parent.orElseThrow();
    owner.requireOpen();
    closed = true;
    owner.references.addAll(references);
    fields.forEach((key, value) -> owner.fields.put(key, value.copy()));
    locals.forEach((key, value) -> owner.locals.put(key, value.copy()));
  }

  void add(SyntaxNode source, SemanticReference reference) {
    requireOpen();
    references.add(new DeclarationReference(source, reference));
  }

  JavaSharedAnnotations fieldAnnotations(String headerKey) {
    requireOpen();
    return fields.computeIfAbsent(headerKey, ignored -> parent.flatMap(owner -> owner.findField(headerKey)).map(JavaSharedAnnotations::copy)
        .orElseGet(JavaSharedAnnotations::new));
  }

  JavaSharedAnnotations localAnnotations(String headerKey) {
    requireOpen();
    return locals.computeIfAbsent(headerKey, ignored -> parent.flatMap(owner -> owner.findLocal(headerKey)).map(JavaSharedAnnotations::copy)
        .orElseGet(JavaSharedAnnotations::new));
  }

  void refuse() {
    requireOpen();
    closed = true;
  }

  private void requireOpen() {
    if (closed) {
      throw new IllegalStateException("Declaration scope is already closed");
    }
  }

  private Optional<JavaSharedAnnotations> findField(String key) {
    var value = fields.get(key);
    return value == null ? parent.flatMap(owner -> owner.findField(key)) : Optional.of(value);
  }

  private Optional<JavaSharedAnnotations> findLocal(String key) {
    var value = locals.get(key);
    return value == null ? parent.flatMap(owner -> owner.findLocal(key)) : Optional.of(value);
  }

  List<SyntaxSemanticEdge> rebind(JavaSourceCopyCorrespondence copies) {
    return references.stream().map(reference -> new SyntaxSemanticEdge(copies.finalCopy(reference.source()).nodeId(),
        SyntaxSemanticRelation.DECLARATION_REFERENCE, new SyntaxSemanticTarget.Declaration(reference.reference()))).toList();
  }

  private record DeclarationReference(SyntaxNode source, SemanticReference reference) {
  }
}
