package org.aquilagraph.cfg.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.aquilagraph.cfg.internal.ExceptionHandler.Alternative;
import org.junit.jupiter.api.Test;

class ExceptionRoutingTest {
  @Test
  void givenExactClassAndEarlierUnknownRelationship_whenRouted_thenEarlierPossibleCatchPrecedesFirstProvenMatch() {
    // Arrange
    var effect = ExceptionType.resolved("sample.Child", List.of("sample.Parent", "java.lang.Throwable"));
    var target = ExceptionTarget.methodExit().nested(List.of(handler("possible", "other.Unknown"), handler("match", "sample.Parent"),
        handler("later", "java.lang.Throwable")));

    // Act
    var route = target.route(effect);

    // Assert
    assertEquals(List.of("possible", "match"), route.handlerOperationNodeIds());
    assertFalse(route.exitsMethod());
  }

  @Test
  void givenExactClassAndOnlyUnknownRelationships_whenRouted_thenPossibleHandlersCannotRemoveExit() {
    // Arrange
    var effect = ExceptionType.resolved("sample.Exact", List.of());
    var target = ExceptionTarget.methodExit().nested(List.of(handler("first", "other.First"), handler("second", "other.Second")));

    // Act
    var route = target.route(effect);

    // Assert
    assertEquals(List.of("first", "second"), route.handlerOperationNodeIds());
    assertTrue(route.exitsMethod());
  }

  @Test
  void givenExactBaseAndProvenStrictSubclassCatch_whenRouted_thenNarrowerClassIsDisjointFromTheExactObject() {
    // Arrange
    var effect = ExceptionType.resolved("sample.Base", List.of("java.lang.Throwable"));
    var target = ExceptionTarget.methodExit().nested(List.of(qualifiedHandler("narrow", "sample.Child", "sample.Base", "java.lang.Throwable"),
        handler("base", "sample.Base")));

    // Act
    var route = target.route(effect);

    // Assert
    assertEquals(List.of("base"), route.handlerOperationNodeIds());
    assertFalse(route.exitsMethod());
  }

  @Test
  void givenSubtypeBoundWithNarrowerCatchesAndProvenSupertype_whenRouted_thenAllPossibleSubsetsPrecedeDefiniteCoverage() {
    // Arrange
    var effect = ExceptionType.subtypeBound("sample.Base", List.of("java.lang.Throwable"), false);
    var target = ExceptionTarget.methodExit().nested(List.of(qualifiedHandler("child", "sample.Child", "sample.Base"),
        handler("unknown", "other.Unrelated"), handler("base", "sample.Base"), handler("later", "java.lang.Throwable")));

    // Act
    var route = target.route(effect);

    // Assert
    assertEquals(List.of("child", "unknown", "base"), route.handlerOperationNodeIds());
    assertFalse(route.exitsMethod());
  }

  @Test
  void givenSubtypeBoundWithOnlyNarrowerCatch_whenRouted_thenUnhandledSubclassesStillReachMethodExit() {
    // Arrange
    var effect = ExceptionType.subtypeBound("sample.Base", List.of("java.lang.Throwable"), false);
    var target = ExceptionTarget.methodExit().nested(List.of(qualifiedHandler("child", "sample.Child", "sample.Base")));

    // Act
    var route = target.route(effect);

    // Assert
    assertEquals(List.of("child"), route.handlerOperationNodeIds());
    assertTrue(route.exitsMethod());
  }

  @Test
  void givenSourceAndOuterHandlerChains_whenRouted_thenResidualPossibilitiesRetainInnerBeforeOuterOrder() {
    // Arrange
    var effect = ExceptionType.subtypeBound("sample.Base", List.of("java.lang.Throwable"), false);
    var outer = ExceptionTarget.methodExit().nested(List.of(handler("outer-possible", "other.Outer"), handler("outer-cover", "java.lang.Throwable")));
    var target = outer.nested(List.of(handler("inner-first", "other.First"), handler("inner-second", "other.Second")));

    // Act
    var route = target.route(effect);

    // Assert
    assertEquals(List.of("inner-first", "inner-second", "outer-possible", "outer-cover"), route.handlerOperationNodeIds());
    assertFalse(route.exitsMethod());
  }

  @Test
  void givenInnerCatchAndProvenOuterSubtype_whenRouted_thenTheAlreadyHandledSubsetIsSubtracted() {
    // Arrange
    var effect = ExceptionType.subtypeBound("sample.Wide", List.of("java.lang.Throwable"), false);
    var outer = ExceptionTarget.methodExit().nested(List.of(qualifiedHandler("shadowed", "sample.Child", "sample.Branch"),
        handler("outer-cover", "sample.Wide")));
    var target = outer.nested(List.of(handler("inner-branch", "sample.Branch")));

    // Act
    var route = target.route(effect);

    // Assert
    assertEquals(List.of("inner-branch", "outer-cover"), route.handlerOperationNodeIds());
    assertFalse(route.exitsMethod());
  }

  @Test
  void givenOnlyUnprovenOuterSubtypeRelationship_whenRouted_thenNameDifferencesDoNotProveShadowing() {
    // Arrange
    var effect = ExceptionType.subtypeBound("sample.Wide", List.of(), false);
    var target = ExceptionTarget.methodExit().nested(List.of(handler("outer", "sample.Child")))
        .nested(List.of(handler("inner", "sample.Branch")));

    // Act
    var route = target.route(effect);

    // Assert
    assertEquals(List.of("inner", "outer"), route.handlerOperationNodeIds());
    assertTrue(route.exitsMethod());
  }

  @Test
  void givenRepeatedQualifiedCatchDomainAcrossScopes_whenRouted_thenItsLaterSubsetCannotBeSelectedAgain() {
    // Arrange
    var effect = ExceptionType.subtypeBound("sample.Wide", List.of(), false);
    var target = ExceptionTarget.methodExit().nested(List.of(handler("outer", "sample.Branch")))
        .nested(List.of(handler("inner", "sample.Branch")));

    // Act
    var route = target.route(effect);

    // Assert
    assertEquals(List.of("inner"), route.handlerOperationNodeIds());
    assertTrue(route.exitsMethod());
  }

  @Test
  void givenPartlyShadowedMultiCatch_whenRouted_thenUnshadowedAlternativeKeepsItsHandlerPossible() {
    // Arrange
    var effect = ExceptionType.subtypeBound("sample.Wide", List.of(), false);
    var mixed = new ExceptionHandler(List.of("sample.Child", "other.Other"), "mixed",
        List.of(new Alternative("sample.Child", List.of("sample.Branch")), new Alternative("other.Other", List.of())));
    var target = ExceptionTarget.methodExit().nested(List.of(mixed)).nested(List.of(handler("inner", "sample.Branch")));

    // Act
    var route = target.route(effect);

    // Assert
    assertEquals(List.of("inner", "mixed"), route.handlerOperationNodeIds());
    assertTrue(route.exitsMethod());
  }

  @Test
  void givenUnknownEffectWithBroadAndShadowedHandlers_whenRouted_thenTheConservativeCategoryKeepsAllScopesAndExit() {
    // Arrange
    var target = ExceptionTarget.methodExit().nested(List.of(handler("outer", "sample.Child")))
        .nested(List.of(handler("broad", "java.lang.Throwable"), handler("later", "sample.Child")));

    // Act
    var route = target.route(ExceptionType.unknown("unchecked-invocation"));

    // Assert
    assertEquals(List.of("broad", "later", "outer"), route.handlerOperationNodeIds());
    assertTrue(route.exitsMethod());
  }

  @Test
  void givenNullableBoundWithSeparateNullAndReferenceCatches_whenRouted_thenBothOutcomesRetainSourceOrder() {
    // Arrange
    var effect = ExceptionType.subtypeBound("sample.Base", List.of("java.lang.Throwable"), true);
    var target = ExceptionTarget.methodExit().nested(List.of(handler("base", "sample.Base"), handler("null", "java.lang.NullPointerException"),
        handler("broad", "java.lang.Throwable")));

    // Act
    var route = target.route(effect);

    // Assert
    assertEquals(List.of("base", "null"), route.handlerOperationNodeIds());
    assertFalse(route.exitsMethod());
    assertEquals(List.of("sample.Base", "java.lang.NullPointerException"), effect.outcomes().stream().map(ExceptionType::typeName).toList());
  }

  @Test
  void givenUnresolvedHandlerAnywhereInChain_whenRouted_thenAdmissionInvariantFailsEvenBeforeAnInnerDefiniteMatch() {
    // Arrange
    var target = ExceptionTarget.methodExit().nested(List.of(new ExceptionHandler(List.of(), "missing")))
        .nested(List.of(handler("match", "sample.Exact")));

    // Act and Assert
    assertThrows(IllegalStateException.class, () -> target.route(ExceptionType.resolved("sample.Exact", List.of())));
    assertThrows(IllegalStateException.class, () -> target.route(ExceptionType.unknown("unchecked-constructor")));
  }

  @Test
  void givenMutableHandlerInputs_whenTargetsAreBuilt_thenCopiesAndRouteListsRemainImmutable() {
    // Arrange
    var names = new ArrayList<>(List.of("sample.Exact"));
    var handler = new ExceptionHandler(names, "handler");
    var handlers = new ArrayList<>(List.of(handler));
    var target = ExceptionTarget.methodExit().nested(handlers);
    names.clear();
    handlers.clear();

    // Act
    var route = target.route(ExceptionType.resolved("sample.Exact", List.of()));

    // Assert
    assertEquals(List.of("handler"), route.handlerOperationNodeIds());
    assertFalse(route.exitsMethod());
    assertThrows(UnsupportedOperationException.class, () -> handler.alternatives().clear());
    assertThrows(UnsupportedOperationException.class, () -> target.handlers().clear());
    assertThrows(UnsupportedOperationException.class, () -> route.handlerOperationNodeIds().clear());
  }

  @Test
  void givenMismatchedAlternativesOrBlankTypes_whenHandlerIsBuilt_thenInvalidNeutralFactsReject() {
    // Arrange, Act and Assert
    assertThrows(IllegalArgumentException.class,
        () -> new ExceptionHandler(List.of("sample.First"), "handler", List.of(new Alternative("sample.Second", List.of()))));
    assertThrows(IllegalArgumentException.class, () -> new ExceptionHandler(List.of(" "), "handler"));
    assertThrows(IllegalArgumentException.class, () -> new ExceptionHandler(List.of("sample.First"), " "));
    assertThrows(IllegalArgumentException.class, () -> new Alternative("sample.First", List.of(" ")));
  }

  private static ExceptionHandler handler(String operationId, String name) {
    return new ExceptionHandler(List.of(name), operationId);
  }

  private static ExceptionHandler qualifiedHandler(String operationId, String name, String... ancestors) {
    return new ExceptionHandler(List.of(name), operationId, List.of(new Alternative(name, List.of(ancestors))));
  }
}
