package org.aquilagraph.frontend.internal;

import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;
import org.aquilagraph.source.api.SyntaxNode;

/** Correlates record-component annotations with mirrors on compiler-derived members. */
interface RecordAnnotationMirrorCorrelation {
  boolean containsAll(List<? extends AnnotationMirror> mirrors, List<SyntaxNode> expected);

  boolean sameAnnotatedType(TypeMirror source, TypeMirror counterpart);
}
