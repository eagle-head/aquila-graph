package org.aquilagraph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.model.JavaSynthesizedDeclaration.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class DeclarationValueContractTest {
  private static final JavaTypeIdentity INT = new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int");
  private static final SourceSpan ORIGIN = new SourceSpan("source", 10, 20);

  @Test
  void givenMutableHeaderMemberBoundModifierAndOriginInputs_whenDeclarationsAreConstructed_thenEveryRetainedCollectionIsAnOrderedImmutableCopy() {
    // Arrange
    var firstBound = reference("type:FirstBound", "FirstBound");
    var secondBound = reference("type:SecondBound", "SecondBound");
    var bounds = new ArrayList<>(List.of(secondBound, firstBound));
    var origins = new ArrayList<>(List.of(ORIGIN));
    var fieldModifiers = new HashSet<>(Set.of("private", "final"));
    var recordModifiers = new HashSet<>(Set.of(JavaTypeModifier.PUBLIC));
    var parameter = new JavaTypeParameter("type:Owner#type-parameter:0", "type:Owner", "T", bounds, true, origins);
    var field = new JavaField("type:Owner#field:value", "type:Owner", "value", INT, fieldModifiers, false, true, origins);
    var formal = new JavaParameter("caller-formal", "input", INT, true, origins);
    var recordComponent = new JavaRecordComponent("type:Record#record-component:0", "type:Record", 0, "value", INT, recordModifiers,
        recordModifiers, false, Optional.empty(), Optional.empty(), Optional.empty(), ORIGIN);
    var fields = new ArrayList<>(List.of(field));
    var parameters = new ArrayList<>(List.of(parameter));
    var references = new ArrayList<>(List.of(secondBound, firstBound));
    var typeModifiers = new HashSet<>(Set.of(JavaTypeModifier.PUBLIC));

    // Act
    var type = new JavaType("type:Owner", "Owner", JavaTypeKind.CLASS, typeModifiers, Optional.empty(), Optional.empty(), references,
        references, parameters, true, fields, List.of(), List.of(), List.of(), origins);
    bounds.clear();
    origins.clear();
    fieldModifiers.clear();
    recordModifiers.clear();
    fields.clear();
    parameters.clear();
    references.clear();
    typeModifiers.clear();

    // Assert
    assertEquals(List.of(secondBound, firstBound), parameter.bounds());
    assertEquals(List.of(ORIGIN), parameter.origins());
    assertEquals(List.of("final", "private"), List.copyOf(field.modifiers()));
    assertEquals(List.of(ORIGIN), field.origins());
    assertEquals(List.of(ORIGIN), formal.origins());
    assertEquals(Set.of(JavaTypeModifier.PUBLIC), recordComponent.writtenModifiers());
    assertEquals(Set.of(JavaTypeModifier.PUBLIC), recordComponent.effectiveModifiers());
    assertEquals(List.of(secondBound, firstBound), type.directInterfaces());
    assertEquals(List.of(secondBound, firstBound), type.permittedSubclasses());
    assertEquals(List.of(parameter), type.typeParameters());
    assertEquals(List.of(field), type.fields());
    assertEquals(List.of(ORIGIN), type.origins());
    assertEquals(Set.of(JavaTypeModifier.PUBLIC), type.modifiers());
    assertThrows(UnsupportedOperationException.class, parameter.bounds()::clear);
    assertThrows(UnsupportedOperationException.class, field.modifiers()::clear);
    assertThrows(UnsupportedOperationException.class, formal.origins()::clear);
    assertThrows(UnsupportedOperationException.class, recordComponent.writtenModifiers()::clear);
    assertThrows(UnsupportedOperationException.class, type.fields()::clear);
  }

  @Test
  void givenEmptyAndOrderedTypesWithOrderedMethods_whenProgramsAreConstructed_thenQueriesPreserveSuppliedOrderAndDefensiveOwnership() {
    // Arrange
    var first = method("type:First#later()", "type:First");
    var second = method("type:First#earlier()", "type:First");
    var third = method("type:Second#read()", "type:Second");
    var methods = new ArrayList<>(List.of(first, second));
    var types = new ArrayList<>(List.of(type("type:Second", Optional.empty(), List.of(third)),
        type("type:First", Optional.empty(), methods)));

    // Act
    var program = new JavaProgram(types);
    var empty = new JavaProgram(List.of());
    types.clear();
    methods.clear();

    // Assert
    assertEquals(List.of("type:Second", "type:First"), program.types().stream().map(JavaType::typeKey).toList());
    assertEquals(List.of(third, first, second), program.methods());
    assertEquals(3, program.methodCount());
    assertEquals(List.of(), empty.methods());
    assertEquals(0, empty.methodCount());
    assertThrows(UnsupportedOperationException.class, program.types()::clear);
    assertThrows(UnsupportedOperationException.class, program.methods()::clear);
  }

  @Test
  void givenNestedOwnerLaterInProgramAndExternalHierarchyReferences_whenProgramIsConstructed_thenMembershipClosesOnlyEnclosingOwnership() {
    // Arrange
    var owner = type("type:Owner", Optional.empty(), List.of());
    var nested = new JavaType("type:Nested", "Nested", JavaTypeKind.CLASS, Set.of(), Optional.of(reference("type:Owner", "Owner")),
        Optional.of(reference("type:External", "External")), List.of(reference("type:ExternalInterface", "ExternalInterface")),
        List.of(reference("type:ExternalPermitted", "ExternalPermitted")), List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of());

    // Act
    var program = new JavaProgram(List.of(nested, owner));

    // Assert
    assertEquals(List.of(nested, owner), program.types());
    assertEquals(Optional.of("type:External"), nested.directSuperclass().orElseThrow().declarationKey());
    assertEquals(List.of("type:ExternalInterface"), nested.directInterfaces().stream().map(value -> value.declarationKey().orElseThrow()).toList());
  }

  @Test
  void givenMissingSelfCyclicOrMismatchedEnclosingOwners_whenProgramsAreConstructed_thenEachInvalidOwnershipPartitionIsRejected() {
    // Arrange
    var owner = type("type:Owner", Optional.empty(), List.of());
    var missing = type("type:Nested", Optional.of(reference("type:Missing", "Missing")), List.of());
    var self = type("type:Self", Optional.of(reference("type:Self", "Self")), List.of());
    var firstCycle = type("type:First", Optional.of(reference("type:Second", "Second")), List.of());
    var secondCycle = type("type:Second", Optional.of(reference("type:First", "First")), List.of());
    var absentKey = type("type:Nested", Optional.of(new JavaTypeReference(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Owner", "Owner"), Optional.empty())), List.of());
    var mismatchedName = type("type:Nested", Optional.of(reference("type:Owner", "Other")), List.of());

    // Act
    var missingFailure = assertThrows(IllegalArgumentException.class, () -> new JavaProgram(List.of(missing)));
    var selfFailure = assertThrows(IllegalArgumentException.class, () -> new JavaProgram(List.of(self)));
    var cycleFailure = assertThrows(IllegalArgumentException.class, () -> new JavaProgram(List.of(firstCycle, secondCycle)));
    var absentFailure = assertThrows(IllegalArgumentException.class, () -> new JavaProgram(List.of(absentKey, owner)));
    var mismatchedFailure = assertThrows(IllegalArgumentException.class, () -> new JavaProgram(List.of(mismatchedName, owner)));

    // Assert
    assertEquals("An enclosing type must identify another declaration in the same program", missingFailure.getMessage());
    assertEquals(missingFailure.getMessage(), selfFailure.getMessage());
    assertEquals("Java type ownership cannot contain a cycle", cycleFailure.getMessage());
    assertEquals("An enclosing type requires a canonical declaration key", absentFailure.getMessage());
    assertEquals(missingFailure.getMessage(), mismatchedFailure.getMessage());
  }

  @Test
  void givenRecordComponentWithoutCounterpartsAndInvalidIdentityKindOrPosition_whenTypesAreConstructed_thenNarrowSuppliedComponentBoundaryIsRetained() {
    // Arrange
    var component = component("type:Record", 0, Optional.empty(), Optional.empty(), Optional.empty());
    var secondPosition = component("type:Record", 1, Optional.empty(), Optional.empty(), Optional.empty());

    // Act
    var record = aggregate("type:Record", JavaTypeKind.RECORD, List.of(), List.of(), List.of(component), List.of(), List.of());
    var wrongKind = assertThrows(IllegalArgumentException.class,
        () -> aggregate("type:Record", JavaTypeKind.CLASS, List.of(), List.of(), List.of(component), List.of(), List.of()));
    var wrongPosition = assertThrows(IllegalArgumentException.class,
        () -> aggregate("type:Record", JavaTypeKind.RECORD, List.of(), List.of(), List.of(secondPosition), List.of(), List.of()));
    var wrongIdentity = assertThrows(IllegalArgumentException.class,
        () -> new JavaRecordComponent("caller-key", "type:Record", 0, "value", INT, Set.of(), Set.of(), false, Optional.empty(), Optional.empty(), Optional.empty(), ORIGIN));
    var negative = assertThrows(IllegalArgumentException.class,
        () -> new JavaRecordComponent("type:Record#record-component:-1", "type:Record", -1, "value", INT, Set.of(), Set.of(), false, Optional.empty(), Optional.empty(), Optional.empty(), ORIGIN));

    // Assert
    assertEquals(List.of(component), record.recordComponents());
    assertEquals(Optional.empty(), component.accessorKey());
    assertEquals(Optional.empty(), component.backingFieldKey());
    assertEquals(Optional.empty(), component.canonicalParameterKey());
    assertEquals(List.of(ORIGIN), component.origins());
    assertEquals("Only a record can declare record components", wrongKind.getMessage());
    assertEquals("Record components must be ordered and uniquely owned by their record", wrongPosition.getMessage());
    assertEquals("A record component requires its owner-and-ordinal identity", wrongIdentity.getMessage());
    assertEquals(wrongIdentity.getMessage(), negative.getMessage());
  }

  @Test
  void givenForeignOrDuplicateMembersAndCrossKindKeys_whenTypeOrProgramIsConstructed_thenCanonicalMembershipRejectsEveryFamilyConflict() {
    // Arrange
    var bound = reference("type:External", "External");
    var parameter = new JavaTypeParameter("type:Owner#type-parameter:0", "type:Owner", "T", List.of(bound), false, List.of());
    var field = new JavaField("type:Owner#field:value", "type:Owner", "value", INT, Set.of(), false, false, List.of());
    var method = method("type:Owner#read()", "type:Owner");
    var synthesized = new JavaSynthesizedDeclaration("type:Owner#implicit()", "type:Owner", Kind.METHOD);
    List<Executable> invalidMembers = List.of(
        () -> aggregate("type:Other", JavaTypeKind.CLASS, List.of(parameter), List.of(), List.of(), List.of(), List.of()),
        () -> aggregate("type:Owner", JavaTypeKind.CLASS, List.of(parameter, parameter), List.of(), List.of(), List.of(), List.of()),
        () -> aggregate("type:Other", JavaTypeKind.CLASS, List.of(), List.of(field), List.of(), List.of(), List.of()),
        () -> aggregate("type:Owner", JavaTypeKind.CLASS, List.of(), List.of(field, field), List.of(), List.of(), List.of()),
        () -> aggregate("type:Other", JavaTypeKind.CLASS, List.of(), List.of(), List.of(), List.of(method), List.of()),
        () -> aggregate("type:Owner", JavaTypeKind.CLASS, List.of(), List.of(), List.of(), List.of(method, method), List.of()),
        () -> aggregate("type:Other", JavaTypeKind.CLASS, List.of(), List.of(), List.of(), List.of(), List.of(synthesized)),
        () -> aggregate("type:Owner", JavaTypeKind.CLASS, List.of(), List.of(), List.of(), List.of(), List.of(synthesized, synthesized)),
        () -> aggregate("type:Owner", JavaTypeKind.CLASS, List.of(), List.of(field), List.of(), List.of(method(field.fieldKey(), "type:Owner")), List.of()),
        () -> aggregate("type:Owner", JavaTypeKind.CLASS, List.of(), List.of(), List.of(), List.of(method), List.of(new JavaSynthesizedDeclaration(method.methodKey(), "type:Owner", Kind.METHOD))));

    // Act
    var failures = invalidMembers.stream().map(input -> assertThrows(IllegalArgumentException.class, input)).toList();
    var crossType = assertThrows(IllegalArgumentException.class, () -> new JavaProgram(List.of(
        type("type:Owner", Optional.empty(), List.of(method)), type(method.methodKey(), Optional.empty(), List.of()))));

    // Assert
    assertEquals(List.of("Type parameters must be uniquely owned by their declaring type", "Type parameters must be uniquely owned by their declaring type",
        "Fields must be uniquely owned by their declaring type", "Fields must be uniquely owned by their declaring type",
        "Methods must be uniquely owned by their declaring type", "Methods must be uniquely owned by their declaring type",
        "Synthesized declarations must be uniquely owned outside detailed members", "Synthesized declarations must be uniquely owned outside detailed members",
        "Methods must be uniquely owned by their declaring type", "Synthesized declarations must be uniquely owned outside detailed members"),
        failures.stream().map(Throwable::getMessage).toList());
    assertEquals("Java declaration keys must be unique across kinds and types", crossType.getMessage());
  }

  @Test
  void givenProvenanceContradictionsAndCallerAssuredMethodFacts_whenDeclarationsAreConstructed_thenFamilySpecificAdmissionIsPreserved() {
    // Arrange
    var bound = reference("type:External", "External");
    var duplicatedFormal = new JavaParameter("caller-formal", "input", INT, false, List.of());
    var formals = new ArrayList<>(List.of(duplicatedFormal, duplicatedFormal));
    var modifiers = new HashSet<>(Set.of("public"));
    var origins = new ArrayList<SourceSpan>();

    // Act
    var suppliedMethod = new JavaMethod("", "caller-owner", "", "", "", formals, modifiers, false, true, true, origins);
    formals.clear();
    modifiers.clear();
    origins.add(ORIGIN);
    var fieldFailure = assertThrows(IllegalArgumentException.class,
        () -> new JavaField("key", "owner", "value", INT, Set.of(), false, true, List.of()));
    var formalFailure = assertThrows(IllegalArgumentException.class, () -> new JavaParameter("key", "value", INT, false, List.of(ORIGIN)));
    var genericFailure = assertThrows(IllegalArgumentException.class,
        () -> new JavaTypeParameter("key", "owner", "T", List.of(bound), true, List.of()));
    var noBound = assertThrows(IllegalArgumentException.class, () -> new JavaTypeParameter("key", "owner", "T", List.of(), false, List.of()));
    var typeFailure = assertThrows(IllegalArgumentException.class, () -> new JavaType("type:Owner", "Owner", JavaTypeKind.CLASS, Set.of(),
        Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), true, List.of(), List.of(), List.of(), List.of(), List.of()));

    // Assert
    assertEquals(List.of(duplicatedFormal, duplicatedFormal), suppliedMethod.parameters());
    assertEquals(Set.of("public"), suppliedMethod.modifiers());
    assertEquals(List.of(), suppliedMethod.origins());
    assertEquals(true, suppliedMethod.sourceDeclared());
    assertEquals("Field source declaration state must match its source origin", fieldFailure.getMessage());
    assertEquals("Parameter source declaration state must match its source origin", formalFailure.getMessage());
    assertEquals("Type parameter source declaration state must match its source origin", genericFailure.getMessage());
    assertEquals("A Java type parameter requires at least one compiler-resolved bound", noBound.getMessage());
    assertEquals("Type source declaration state must match its source origin", typeFailure.getMessage());
    assertThrows(IllegalArgumentException.class, () -> new JavaMethod("key", "owner", "read", "read()", "int", List.of(), Set.of("abstract"), false, false, true, List.of()));
    assertThrows(IllegalArgumentException.class, () -> new JavaMethod("key", "owner", "read", "read()", "int", List.of(), Set.of("native"), false, false, true, List.of()));
  }

  @Test
  void givenCallerAssuredTypeAndSemanticReferenceValues_whenConstructed_thenIdentityOnlyDomainsAndOrderedCopiedFactsRemainDistinct() {
    // Arrange
    var alternatives = new ArrayList<>(List.of("Second", "First"));
    var assignable = new ArrayList<>(List.of("Second", "Object"));

    // Act
    var noDeclaration = new JavaTypeReference(INT, Optional.empty());
    var errorWithKey = new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.ERROR, Optional.of("caller-key"), Optional.of("Missing"), alternatives, assignable);
    var unresolvedWithKey = new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.UNRESOLVED, Optional.of("caller-key"), Optional.empty(), List.of(), List.of());
    alternatives.clear();
    assignable.clear();

    // Assert
    assertEquals(Optional.empty(), noDeclaration.declarationKey());
    assertEquals(List.of("Second", "First"), errorWithKey.typeAlternativeNames());
    assertEquals(List.of("Second", "Object"), errorWithKey.assignableToTypeNames());
    assertEquals(ResolutionStatus.UNRESOLVED, unresolvedWithKey.status());
    assertThrows(IllegalArgumentException.class, () -> new JavaTypeReference(INT, Optional.of(" ")));
    assertThrows(IllegalArgumentException.class, () -> new JavaTypeIdentity(JavaTypeCategory.INT, " ", "int"));
    assertThrows(IllegalArgumentException.class, () -> new JavaTypeIdentity(JavaTypeCategory.INT, "int", " "));
    assertThrows(IllegalArgumentException.class, () -> new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.RESOLVED, Optional.empty(), Optional.empty(), List.of(), List.of()));
    assertThrows(IllegalArgumentException.class, () -> new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.ERROR, Optional.of(" "), Optional.empty(), List.of(), List.of()));
    assertThrows(IllegalArgumentException.class, () -> new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.ERROR, Optional.empty(), Optional.empty(), List.of("Missing"), List.of()));
    assertThrows(IllegalArgumentException.class, () -> new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.ERROR, Optional.empty(), Optional.empty(), List.of(), List.of("Object")));
  }

  @Test
  void givenNullDeclarationListsAndFields_whenSuppliedDeclarationsAreConstructed_thenEveryRequiredFamilyBoundaryRejectsNull() {
    // Arrange
    List<Executable> invalidInputs = List.of(
        () -> new JavaProgram(null), () -> new JavaProgram(Collections.singletonList(null)),
        () -> new JavaParameter(null, "input", INT, false, List.of()),
        () -> new JavaParameter("key", null, INT, false, List.of()),
        () -> new JavaParameter("key", "input", null, false, List.of()),
        () -> new JavaParameter("key", "input", INT, false, null),
        () -> new JavaParameter("key", "input", INT, false, Collections.singletonList(null)),
        () -> new JavaTypeReference(null, Optional.empty()), () -> new JavaTypeReference(INT, null),
        () -> new JavaTypeIdentity(null, "int", "int"), () -> new JavaTypeIdentity(JavaTypeCategory.INT, null, "int"),
        () -> new JavaTypeIdentity(JavaTypeCategory.INT, "int", null),
        () -> new JavaField("key", "owner", "value", INT, null, false, false, List.of()),
        () -> new JavaField("key", "owner", "value", INT, Set.of(), false, false, null),
        () -> new JavaTypeParameter("key", "owner", "T", null, false, List.of()),
        () -> new JavaTypeParameter("key", "owner", "T", Collections.singletonList(null), false, List.of()),
        () -> new JavaMethod("key", "owner", "read", "read()", "int", null, Set.of(), false, false, false, List.of()),
        () -> new JavaMethod("key", "owner", "read", "read()", "int", Collections.singletonList(null), Set.of(), false, false, false, List.of()),
        () -> new JavaSynthesizedDeclaration(null, "owner", Kind.METHOD),
        () -> new JavaSynthesizedDeclaration("owner#read()", null, Kind.METHOD),
        () -> new JavaSynthesizedDeclaration("owner#read()", "owner", null));

    // Act
    var failures = invalidInputs.stream().map(input -> assertThrows(NullPointerException.class, input)).toList();

    // Assert
    assertEquals(21, failures.size());
    assertEquals("types", failures.getFirst().getMessage());
    assertEquals("parameterKey", failures.get(2).getMessage());
    assertEquals("identity", failures.get(7).getMessage());
  }

  @Test
  void givenNullTypeAggregateFieldsEntriesAndBlankIdentity_whenSuppliedTypesAreConstructed_thenEachRequiredCollectionAndIdentityBoundaryIsEnforced() {
    // Arrange
    var failures = List.<Executable>of(
        () -> new JavaType(null, "Owner", JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of()),
        () -> new JavaType("type:Owner", null, JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of()),
        () -> new JavaType("type:Owner", "Owner", null, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of()),
        () -> new JavaType("type:Owner", "Owner", JavaTypeKind.CLASS, null, Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of()),
        () -> new JavaType("type:Owner", "Owner", JavaTypeKind.CLASS, Set.of(), null, Optional.empty(), List.of(), List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of()),
        () -> new JavaType("type:Owner", "Owner", JavaTypeKind.CLASS, Set.of(), Optional.empty(), null, List.of(), List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of()),
        () -> new JavaType("type:Owner", "Owner", JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), null, List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of()),
        () -> new JavaType("type:Owner", "Owner", JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), null, List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of()),
        () -> new JavaType("type:Owner", "Owner", JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), null, false, List.of(), List.of(), List.of(), List.of(), List.of()),
        () -> new JavaType("type:Owner", "Owner", JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), false, null, List.of(), List.of(), List.of(), List.of()),
        () -> new JavaType("type:Owner", "Owner", JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), false, List.of(), null, List.of(), List.of(), List.of()),
        () -> new JavaType("type:Owner", "Owner", JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), false, List.of(), List.of(), null, List.of(), List.of()),
        () -> new JavaType("type:Owner", "Owner", JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), false, List.of(), List.of(), List.of(), null, List.of()),
        () -> new JavaType("type:Owner", "Owner", JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), null),
        () -> new JavaType("type:Owner", "Owner", JavaTypeKind.CLASS, Collections.singleton(null), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of()),
        () -> new JavaType("type:Owner", "Owner", JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), Collections.singletonList(null), List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of()),
        () -> new JavaType("type:Owner", "Owner", JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), Collections.singletonList(null), List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of()),
        () -> aggregate("type:Owner", JavaTypeKind.CLASS, Collections.singletonList(null), List.of(), List.of(), List.of(), List.of()),
        () -> aggregate("type:Owner", JavaTypeKind.CLASS, List.of(), Collections.singletonList(null), List.of(), List.of(), List.of()),
        () -> aggregate("type:Owner", JavaTypeKind.RECORD, List.of(), List.of(), Collections.singletonList(null), List.of(), List.of()),
        () -> aggregate("type:Owner", JavaTypeKind.CLASS, List.of(), List.of(), List.of(), Collections.singletonList(null), List.of()),
        () -> aggregate("type:Owner", JavaTypeKind.CLASS, List.of(), List.of(), List.of(), List.of(), Collections.singletonList(null)),
        () -> new JavaType("type:Owner", "Owner", JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), Collections.singletonList(null)));

    // Act
    var rejected = failures.stream().map(action -> assertThrows(NullPointerException.class, action)).toList();
    var blankKey = assertThrows(IllegalArgumentException.class, () -> new JavaType(" ", "Owner", JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of()));
    var blankName = assertThrows(IllegalArgumentException.class, () -> new JavaType("type:Owner", " ", JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of()));

    // Assert
    assertEquals(23, rejected.size());
    assertEquals(List.of("typeKey", "qualifiedName", "kind", "modifiers", "enclosingType", "directSuperclass", "directInterfaces",
        "permittedSubclasses", "typeParameters", "fields", "recordComponents", "methods", "synthesizedDeclarations", "origins"),
        rejected.subList(0, 14).stream().map(Throwable::getMessage).toList());
    assertEquals("typeKey cannot be blank", blankKey.getMessage());
    assertEquals("qualifiedName cannot be blank", blankName.getMessage());
  }

  @Test
  void givenNullRecordReferenceAndMemberFacts_whenSuppliedValuesAreConstructed_thenRequiredFieldsRejectNullWithoutAddingBroaderMethodOrFormalRules() {
    // Arrange
    var bound = reference("type:Bound", "Bound");
    var failures = List.<Executable>of(
        () -> new JavaRecordComponent(null, "type:Record", 0, "value", INT, Set.of(), Set.of(), false, Optional.empty(), Optional.empty(), Optional.empty(), ORIGIN),
        () -> new JavaRecordComponent("type:Record#record-component:0", null, 0, "value", INT, Set.of(), Set.of(), false, Optional.empty(), Optional.empty(), Optional.empty(), ORIGIN),
        () -> new JavaRecordComponent("type:Record#record-component:0", "type:Record", 0, null, INT, Set.of(), Set.of(), false, Optional.empty(), Optional.empty(), Optional.empty(), ORIGIN),
        () -> new JavaRecordComponent("type:Record#record-component:0", "type:Record", 0, "value", null, Set.of(), Set.of(), false, Optional.empty(), Optional.empty(), Optional.empty(), ORIGIN),
        () -> new JavaRecordComponent("type:Record#record-component:0", "type:Record", 0, "value", INT, null, Set.of(), false, Optional.empty(), Optional.empty(), Optional.empty(), ORIGIN),
        () -> new JavaRecordComponent("type:Record#record-component:0", "type:Record", 0, "value", INT, Set.of(), null, false, Optional.empty(), Optional.empty(), Optional.empty(), ORIGIN),
        () -> new JavaRecordComponent("type:Record#record-component:0", "type:Record", 0, "value", INT, Set.of(), Set.of(), false, null, Optional.empty(), Optional.empty(), ORIGIN),
        () -> new JavaRecordComponent("type:Record#record-component:0", "type:Record", 0, "value", INT, Set.of(), Set.of(), false, Optional.empty(), null, Optional.empty(), ORIGIN),
        () -> new JavaRecordComponent("type:Record#record-component:0", "type:Record", 0, "value", INT, Set.of(), Set.of(), false, Optional.empty(), Optional.empty(), null, ORIGIN),
        () -> new JavaRecordComponent("type:Record#record-component:0", "type:Record", 0, "value", INT, Set.of(), Set.of(), false, Optional.empty(), Optional.empty(), Optional.empty(), null),
        () -> new JavaRecordComponent("type:Record#record-component:0", "type:Record", 0, "value", INT, Collections.singleton(null), Set.of(), false, Optional.empty(), Optional.empty(), Optional.empty(), ORIGIN),
        () -> new JavaRecordComponent("type:Record#record-component:0", "type:Record", 0, "value", INT, Set.of(), Collections.singleton(null), false, Optional.empty(), Optional.empty(), Optional.empty(), ORIGIN),
        () -> new SemanticReference(null, ResolutionStatus.UNRESOLVED, Optional.empty(), Optional.empty(), List.of(), List.of()),
        () -> new SemanticReference(ReferenceKind.TYPE, null, Optional.empty(), Optional.empty(), List.of(), List.of()),
        () -> new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.UNRESOLVED, null, Optional.empty(), List.of(), List.of()),
        () -> new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.UNRESOLVED, Optional.empty(), null, List.of(), List.of()),
        () -> new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.UNRESOLVED, Optional.empty(), Optional.empty(), null, List.of()),
        () -> new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.UNRESOLVED, Optional.empty(), Optional.empty(), List.of(), null),
        () -> new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.UNRESOLVED, Optional.empty(), Optional.of("type"), Collections.singletonList(null), List.of()),
        () -> new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.UNRESOLVED, Optional.empty(), Optional.of("type"), List.of(), Collections.singletonList(null)),
        () -> new JavaField(null, "owner", "value", INT, Set.of(), false, false, List.of()),
        () -> new JavaField("key", null, "value", INT, Set.of(), false, false, List.of()),
        () -> new JavaField("key", "owner", null, INT, Set.of(), false, false, List.of()),
        () -> new JavaField("key", "owner", "value", null, Set.of(), false, false, List.of()),
        () -> new JavaField("key", "owner", "value", INT, Collections.singleton(null), false, false, List.of()),
        () -> new JavaField("key", "owner", "value", INT, Set.of(), false, false, Collections.singletonList(null)),
        () -> new JavaTypeParameter(null, "owner", "T", List.of(bound), false, List.of()),
        () -> new JavaTypeParameter("key", null, "T", List.of(bound), false, List.of()),
        () -> new JavaTypeParameter("key", "owner", null, List.of(bound), false, List.of()),
        () -> new JavaTypeParameter("key", "owner", "T", List.of(bound), false, null),
        () -> new JavaTypeParameter("key", "owner", "T", List.of(bound), false, Collections.singletonList(null)),
        () -> new JavaMethod(null, "owner", "read", "read()", "int", List.of(), Set.of(), false, false, false, List.of()),
        () -> new JavaMethod("key", null, "read", "read()", "int", List.of(), Set.of(), false, false, false, List.of()),
        () -> new JavaMethod("key", "owner", null, "read()", "int", List.of(), Set.of(), false, false, false, List.of()),
        () -> new JavaMethod("key", "owner", "read", null, "int", List.of(), Set.of(), false, false, false, List.of()),
        () -> new JavaMethod("key", "owner", "read", "read()", null, List.of(), Set.of(), false, false, false, List.of()),
        () -> new JavaMethod("key", "owner", "read", "read()", "int", List.of(), null, false, false, false, List.of()),
        () -> new JavaMethod("key", "owner", "read", "read()", "int", List.of(), Set.of(), false, false, false, null),
        () -> new JavaMethod("key", "owner", "read", "read()", "int", List.of(), Collections.singleton(null), false, false, false, List.of()),
        () -> new JavaMethod("key", "owner", "read", "read()", "int", List.of(), Set.of(), false, false, false, Collections.singletonList(null)));

    // Act
    var rejected = failures.stream().map(action -> assertThrows(NullPointerException.class, action)).toList();
    var emptyFormal = new JavaParameter("", "", INT, false, List.of());
    var suppliedMethod = new JavaMethod("", "", "", "", "", List.of(emptyFormal), Set.of(), false, false, false, List.of());

    // Assert
    assertEquals(40, rejected.size());
    assertEquals("componentKey", rejected.getFirst().getMessage());
    assertEquals("referenceKind", rejected.get(12).getMessage());
    assertEquals(List.of(emptyFormal), suppliedMethod.parameters());
    assertThrows(IllegalArgumentException.class, () -> component("type:Record", 0, Optional.of(" "), Optional.empty(), Optional.empty()));
    assertThrows(IllegalArgumentException.class, () -> component("type:Record", 0, Optional.empty(), Optional.of(" "), Optional.empty()));
    assertThrows(IllegalArgumentException.class, () -> component("type:Record", 0, Optional.empty(), Optional.empty(), Optional.of(" ")));
    assertThrows(IllegalArgumentException.class, () -> new JavaSynthesizedDeclaration("owner#", "owner", Kind.METHOD));
    assertThrows(IllegalArgumentException.class, () -> new JavaSynthesizedDeclaration("foreign#read()", "owner", Kind.METHOD));
  }

  private static JavaTypeReference reference(String key, String name) {
    return new JavaTypeReference(new JavaTypeIdentity(JavaTypeCategory.DECLARED, name, name), Optional.of(key));
  }

  private static JavaType type(String key, Optional<JavaTypeReference> enclosing, List<JavaMethod> methods) {
    return new JavaType(key, key.substring("type:".length()), JavaTypeKind.CLASS, Set.of(), enclosing, Optional.empty(), List.of(), List.of(),
        List.of(), false, List.of(), List.of(), methods, List.of(), List.of());
  }

  private static JavaType aggregate(String key, JavaTypeKind kind, List<JavaTypeParameter> parameters, List<JavaField> fields,
      List<JavaRecordComponent> components, List<JavaMethod> methods, List<JavaSynthesizedDeclaration> synthesized) {
    return new JavaType(key, key.substring("type:".length()), kind, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), parameters,
        false, fields, components, methods, synthesized, List.of());
  }

  private static JavaMethod method(String key, String owner) {
    return new JavaMethod(key, owner, "read", "read()", "int", List.of(), Set.of(), false, false, false, List.of());
  }

  private static JavaRecordComponent component(String owner, int position, Optional<String> accessor, Optional<String> field, Optional<String> formal) {
    return new JavaRecordComponent(owner + "#record-component:" + position, owner, position, "value", INT, Set.of(), Set.of(), false,
        accessor, field, formal, ORIGIN);
  }
}
