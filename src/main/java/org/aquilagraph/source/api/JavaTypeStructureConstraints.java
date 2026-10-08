package org.aquilagraph.source.api;

/** Shared constraints on components of compiler-neutral value types. */
final class JavaTypeStructureConstraints {
  private JavaTypeStructureConstraints() {
  }

  static void requireValueComponent(JavaTypeStructure component) {
    if (component instanceof JavaTypeStructure.Executable) {
      throw new IllegalArgumentException("An executable signature cannot be a value-type component");
    }
    if (component instanceof JavaTypeStructure.Primitive primitive && primitive.kind() == JavaTypeCategory.VOID) {
      throw new IllegalArgumentException("Void cannot be a value-type component");
    }
  }
}
