package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class PublicationStatusTest {
  @Test
  void givenPublicationStatusContract_whenValuesRequested_thenSupportedStatusesAreReturned() {
    // Arrange
    var expected = new PublicationStatus[] {PublicationStatus.NOT_REQUESTED, PublicationStatus.NOT_ELIGIBLE, PublicationStatus.SAVED, PublicationStatus.FAILED};

    // Act
    var actual = PublicationStatus.values();

    // Assert
    assertArrayEquals(expected, actual);
  }
}
