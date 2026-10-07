package org.aquilagraph.source.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EvidenceValueTest {
  @Test
  void givenKnownFalseAndUnavailableClassifications_whenEvidenceIsCopied_thenEveryStateRemainsDistinct() {
    // Arrange
    var origin = new EvidenceOrigin("method", "occurrence", "string-expression.constant", Optional.empty());

    // Act
    var known = EvidenceValue.known(false, origin);
    var missing = EvidenceValue.<Boolean>missing(origin, "Classification is unavailable");
    var erroneous = EvidenceValue.<Boolean>erroneous(origin, "Attribution is erroneous");
    var inapplicable = EvidenceValue.<Boolean>notApplicable(origin, "This context has no such classification");

    // Assert
    assertEquals(EvidenceState.KNOWN, known.state());
    assertEquals(Optional.of(false), known.knownValue());
    assertEquals(EvidenceState.MISSING, missing.state());
    assertEquals(EvidenceState.ERRONEOUS, erroneous.state());
    assertEquals(EvidenceState.NOT_APPLICABLE, inapplicable.state());
    for (var unavailable : List.of(missing, erroneous, inapplicable)) {
      assertTrue(unavailable.knownValue().isEmpty());
      assertTrue(unavailable.explanation().isPresent());
      assertEquals(origin, unavailable.origin());
    }
  }

  @Test
  void givenContradictoryStateAndValue_whenEvidenceIsConstructed_thenNoFalseKnownOrNegativeFactCanEscape() {
    // Arrange
    var origin = new EvidenceOrigin("method", "occurrence", "string-expression.constant", Optional.empty());

    // Act and Assert
    assertThrows(IllegalArgumentException.class,
        () -> new EvidenceValue<Boolean>(EvidenceState.KNOWN, Optional.empty(), origin, Optional.empty()));
    for (var state : List.of(EvidenceState.MISSING, EvidenceState.ERRONEOUS, EvidenceState.NOT_APPLICABLE)) {
      assertThrows(IllegalArgumentException.class,
          () -> new EvidenceValue<>(state, Optional.of(false), origin, Optional.of("Value is unavailable")));
      assertThrows(IllegalArgumentException.class,
          () -> new EvidenceValue<Boolean>(state, Optional.empty(), origin, Optional.empty()));
    }
  }

  @Test
  void givenAnAbsentExplanationOrNullKnownValue_whenEvidenceIsCreated_thenInvalidEvidenceIsRejected() {
    // Arrange
    var origin = new EvidenceOrigin("method", "occurrence", "string-expression.constant", Optional.empty());

    // Act and Assert
    assertThrows(IllegalArgumentException.class, () -> EvidenceValue.missing(origin, " "));
    assertThrows(NullPointerException.class, () -> EvidenceValue.known(null, origin));
    assertThrows(IllegalArgumentException.class,
        () -> new EvidenceValue<>(EvidenceState.KNOWN, Optional.of(true), origin, Optional.of("Unexpected negative explanation")));
  }
}
