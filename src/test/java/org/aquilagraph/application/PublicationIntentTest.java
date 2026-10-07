package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class PublicationIntentTest {
  @Test
  void givenRepositoryPolicyContract_whenValuesRequested_thenSupportedPoliciesAreReturned() {
    // Arrange
    var expected = new PublicationIntent[] {PublicationIntent.DO_NOT_SAVE, PublicationIntent.SAVE_COMPLETE_AST, PublicationIntent.SAVE_COMPLETED};

    // Act
    var actual = PublicationIntent.values();

    // Assert
    assertArrayEquals(expected, actual);
  }
}
