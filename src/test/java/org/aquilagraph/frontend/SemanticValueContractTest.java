package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.JavaTypeStructure.Array;
import org.aquilagraph.source.api.JavaTypeStructure.Declared;
import org.aquilagraph.source.api.JavaTypeStructure.Intersection;
import org.aquilagraph.source.api.JavaTypeStructure.LocalDeclared;
import org.aquilagraph.source.api.JavaTypeStructure.LocalDeclared.Kind;
import org.aquilagraph.source.api.JavaTypeStructure.Primitive;
import org.aquilagraph.source.api.JavaTypeStructure.Union;
import org.aquilagraph.source.api.JavaTypeStructure.Variable;
import org.aquilagraph.source.api.JavaTypeStructure.Wildcard;
import org.aquilagraph.source.api.JavaTypeStructure.Wildcard.BoundKind;
import org.aquilagraph.source.api.SyntaxType.NoType;
import org.aquilagraph.source.api.SyntaxType.Resolved;
import org.aquilagraph.source.api.SyntaxType.StructureUnavailable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class SemanticValueContractTest {
  @Test
  void givenOrderedRecursiveComponentsAndIndependentOwners_whenSuppliedStructuresAreConstructed_thenCopiedFactsRetainTheirExactOrder() {
    // Arrange
    var owner = new Declared("type:Owner", Optional.empty(), List.of());
    var first = new Variable("type:Owner#type-parameter:0");
    var second = new Variable("type:Owner#type-parameter:1");
    var arguments = new ArrayList<JavaTypeStructure>(List.of(second, first));
    var bounds = new ArrayList<JavaTypeStructure>(List.of(owner, first));
    var alternatives = new ArrayList<JavaTypeStructure>(List.of(first, owner));

    // Act
    var member = new Declared("type:Owner.Member", Optional.of(owner), arguments);
    var staticMember = new Declared("type:Owner.Static", Optional.of("type:Owner"), Optional.empty(), arguments);
    var local = new LocalDeclared("type:Owner#read():type:1:5", "type:Owner#read()", Kind.LOCAL, Optional.of(owner), arguments);
    var anonymous = new LocalDeclared("type:Owner#read():type:8:12", "different-lexical-owner", Kind.ANONYMOUS, Optional.of(owner), arguments);
    var intersection = new Intersection(bounds);
    var union = new Union(alternatives);
    arguments.clear();
    bounds.clear();
    alternatives.clear();

    // Assert
    assertEquals(Optional.of("type:Owner"), member.lexicalOwnerKey());
    assertEquals(Optional.empty(), staticMember.enclosingType());
    assertEquals("type:Owner#read()", local.lexicalOwnerKey());
    assertEquals(Optional.of(owner), anonymous.enclosingType());
    assertEquals(List.of(second, first), member.arguments());
    assertEquals(List.of(second, first), staticMember.arguments());
    assertEquals(List.of(second, first), local.arguments());
    assertEquals(List.of(second, first), anonymous.arguments());
    assertEquals(List.of(owner, first), intersection.bounds());
    assertEquals(List.of(first, owner), union.alternatives());
    assertThrows(UnsupportedOperationException.class, () -> member.arguments().clear());
    assertThrows(UnsupportedOperationException.class, () -> local.arguments().clear());
    assertThrows(UnsupportedOperationException.class, () -> intersection.bounds().clear());
    assertThrows(UnsupportedOperationException.class, () -> union.alternatives().clear());
    assertEquals(new Array(new Array(first)).component(), new Array(first));
  }

  @Test
  void givenBlankKeysAndContradictoryRecursiveFacts_whenStructuresAreSupplied_thenEachVariantRejectsOnlyItsOwnInvalidBoundary() {
    // Arrange
    var primitive = new Primitive(JavaTypeCategory.INT);
    var first = new Declared("type:First", Optional.empty(), List.of());
    var second = new Declared("type:Second", Optional.empty(), List.of());
    var failures = List.<Executable>of(
        () -> new Declared(" ", Optional.empty(), List.of()),
        () -> new Declared("type:Nested", Optional.of(" "), Optional.empty(), List.of()),
        () -> new Declared("type:Nested", Optional.of("type:Second"), Optional.of(first), List.of()),
        () -> new Declared("type:Nested", Optional.of(primitive), List.of()),
        () -> new LocalDeclared(" ", "owner", Kind.LOCAL, Optional.empty(), List.of()),
        () -> new LocalDeclared("local", " ", Kind.LOCAL, Optional.empty(), List.of()),
        () -> new LocalDeclared("local", "owner", Kind.LOCAL, Optional.of(primitive), List.of()),
        () -> new Variable(" "),
        () -> new Wildcard(BoundKind.UNBOUNDED, Optional.of(first)),
        () -> new Wildcard(BoundKind.EXTENDS, Optional.empty()),
        () -> new Wildcard(BoundKind.SUPER, Optional.empty()),
        () -> new Intersection(List.of()), () -> new Intersection(List.of(first)),
        () -> new Intersection(List.of(first, first)),
        () -> new Union(List.of()), () -> new Union(List.of(first)), () -> new Union(List.of(first, first)));

    // Act
    var exceptions = failures.stream().map(action -> assertThrows(IllegalArgumentException.class, action)).toList();

    // Assert
    assertEquals(17, exceptions.size());
    assertEquals(new Wildcard(BoundKind.UNBOUNDED, Optional.empty()).bound(), Optional.empty());
    assertEquals(new Wildcard(BoundKind.EXTENDS, Optional.of(first)).bound(), Optional.of(first));
    assertEquals(new Wildcard(BoundKind.SUPER, Optional.of(second)).bound(), Optional.of(second));
    for (var category : JavaTypeCategory.values()) {
      if (!List.of(JavaTypeCategory.BOOLEAN, JavaTypeCategory.BYTE, JavaTypeCategory.SHORT, JavaTypeCategory.INT,
          JavaTypeCategory.LONG, JavaTypeCategory.CHAR, JavaTypeCategory.FLOAT, JavaTypeCategory.DOUBLE, JavaTypeCategory.VOID).contains(category)) {
        assertThrows(IllegalArgumentException.class, () -> new Primitive(category));
      } else {
        assertEquals(category, new Primitive(category).kind());
      }
    }
  }

  @Test
  void givenEveryRecursiveVariant_whenResolvedEvidenceIsSupplied_thenCategoryCompatibilityAndIdentityOnlyLimitsArePreserved() {
    // Arrange
    var declared = new Declared("type:Sample", Optional.empty(), List.of());
    var variable = new Variable("type:Sample#type-parameter:0");
    var structures = List.<JavaTypeStructure>of(new Primitive(JavaTypeCategory.INT), declared,
        new LocalDeclared("local", "owner", Kind.LOCAL, Optional.empty(), List.of()), variable, new Array(variable),
        new Wildcard(BoundKind.UNBOUNDED, Optional.empty()), new Intersection(List.of(declared, variable)), new Union(List.of(declared, variable)));
    var categories = List.of(JavaTypeCategory.INT, JavaTypeCategory.DECLARED, JavaTypeCategory.DECLARED, JavaTypeCategory.TYPE_VARIABLE,
        JavaTypeCategory.ARRAY, JavaTypeCategory.WILDCARD, JavaTypeCategory.INTERSECTION, JavaTypeCategory.UNION);

    // Act
    var supplied = new ArrayList<Resolved>();
    for (var index = 0; index < structures.size(); index++) {
      supplied.add(new Resolved(identity(categories.get(index)), structures.get(index)));
    }

    // Assert
    for (var index = 0; index < structures.size(); index++) {
      assertEquals(Optional.of(structures.get(index)), supplied.get(index).structure());
      var structure = structures.get(index);
      assertThrows(IllegalArgumentException.class, () -> new Resolved(identity(JavaTypeCategory.VOID), structure));
    }
    for (var category : List.of(JavaTypeCategory.DECLARED, JavaTypeCategory.ARRAY, JavaTypeCategory.TYPE_VARIABLE,
        JavaTypeCategory.NULL, JavaTypeCategory.EXECUTABLE, JavaTypeCategory.ERROR, JavaTypeCategory.OTHER)) {
      assertEquals(Optional.empty(), new Resolved(identity(category)).structure());
    }
    for (var category : List.of(JavaTypeCategory.WILDCARD, JavaTypeCategory.INTERSECTION, JavaTypeCategory.UNION)) {
      assertThrows(IllegalArgumentException.class, () -> new Resolved(identity(category)));
    }
    assertEquals(identity(JavaTypeCategory.ERROR), new StructureUnavailable(identity(JavaTypeCategory.ERROR)).identity());
  }

  @Test
  void givenNullRecursiveFieldsAndComponents_whenSemanticValuesAreSupplied_thenNullsAreRejectedBeforeRetention() {
    // Arrange
    var failures = List.<Executable>of(
        () -> new Primitive(null), () -> new Variable(null), () -> new Array(null),
        () -> new Declared(null, Optional.empty(), List.of()),
        () -> new Declared("type:Sample", null, Optional.empty(), List.of()),
        () -> new Declared("type:Sample", Optional.empty(), null, List.of()),
        () -> new Declared("type:Sample", Optional.empty(), Optional.empty(), null),
        () -> new Declared("type:Sample", Optional.empty(), Collections.singletonList(null)),
        () -> new LocalDeclared(null, "owner", Kind.LOCAL, Optional.empty(), List.of()),
        () -> new LocalDeclared("local", null, Kind.LOCAL, Optional.empty(), List.of()),
        () -> new LocalDeclared("local", "owner", null, Optional.empty(), List.of()),
        () -> new LocalDeclared("local", "owner", Kind.LOCAL, null, List.of()),
        () -> new LocalDeclared("local", "owner", Kind.LOCAL, Optional.empty(), null),
        () -> new LocalDeclared("local", "owner", Kind.LOCAL, Optional.empty(), Collections.singletonList(null)),
        () -> new Wildcard(null, Optional.empty()), () -> new Wildcard(BoundKind.UNBOUNDED, null),
        () -> new Intersection(null), () -> new Intersection(Collections.singletonList(null)),
        () -> new Union(null), () -> new Union(Collections.singletonList(null)),
        () -> new NoType(null), () -> new StructureUnavailable(null),
        () -> new Resolved(null), () -> new Resolved(identity(JavaTypeCategory.DECLARED), (Optional<JavaTypeStructure>) null),
        () -> new Resolved(identity(JavaTypeCategory.DECLARED), (JavaTypeStructure) null));

    // Act
    var exceptions = failures.stream().map(action -> assertThrows(NullPointerException.class, action)).toList();

    // Assert
    assertEquals(25, exceptions.size());
    assertEquals("kind", exceptions.getFirst().getMessage());
    assertEquals("structure", exceptions.getLast().getMessage());
  }

  @Test
  void givenCategoryDisplayAndErasureTuples_whenContentIdentityIsDerived_thenLiteralFramedOrderAndNarrowSuppliedValidationAreRetained() {
    // Arrange
    var declared = new JavaTypeIdentity(JavaTypeCategory.DECLARED, "List<String>", "java.util.List");
    var reversed = new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.util.List", "List<String>");
    var erroneous = new JavaTypeIdentity(JavaTypeCategory.ERROR, "List<String>", "java.util.List");

    // Act
    var keys = List.of(declared.typeKey(), reversed.typeKey(), erroneous.typeKey());

    // Assert
    assertEquals(List.of("java-type:589a037db04686afb2cc639240bf643b59b1d0c5c5eab4fd2afaafb92d08dd78",
        "java-type:f52f440751de8322fe717f7ad9282dabb3ea14a669b3f2f70a734e1527b336a5",
        "java-type:33e396c1147d2b3b7d8ef4487156ad71b70a4ec82980be19b35dd82f3897d1c5"), keys);
    assertNotEquals(declared, reversed);
    assertEquals(" caller text ", new JavaTypeIdentity(JavaTypeCategory.OTHER, " caller text ", "erasure").displayName());
    assertThrows(NullPointerException.class, () -> new JavaTypeIdentity(null, "name", "erasure"));
    assertThrows(NullPointerException.class, () -> new JavaTypeIdentity(JavaTypeCategory.OTHER, null, "erasure"));
    assertThrows(NullPointerException.class, () -> new JavaTypeIdentity(JavaTypeCategory.OTHER, "name", null));
    assertThrows(IllegalArgumentException.class, () -> new JavaTypeIdentity(JavaTypeCategory.OTHER, " ", "erasure"));
    assertThrows(IllegalArgumentException.class, () -> new JavaTypeIdentity(JavaTypeCategory.OTHER, "name", " "));
  }

  private static JavaTypeIdentity identity(JavaTypeCategory category) {
    return new JavaTypeIdentity(category, "supplied display", "supplied erasure");
  }
}
