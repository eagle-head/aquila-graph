package org.aquilagraph.frontend.internal;

import com.sun.source.tree.NewClassTree;
import com.sun.source.util.TreePath;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

/** The attributed creation and the constructor it invokes, including an anonymous creation's delegation target. */
record ConstructorOccurrence(TreePath path, NewClassTree creation, ExecutableElement constructor, DeclaredType created, TypeElement owner) {
}
