package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class RepositoryStatusTest {
  @Test
  void givenRepositoryStatusContract_whenValuesRequested_thenSupportedStatusesAreReturned() {
    // Arrange
    var expected = new RepositoryStatus[] {RepositoryStatus.NOT_REQUESTED, RepositoryStatus.NOT_ELIGIBLE, RepositoryStatus.SAVED};

    // Act
    var actual = RepositoryStatus.values();

    // Assert
    assertArrayEquals(expected, actual);
  }
}
