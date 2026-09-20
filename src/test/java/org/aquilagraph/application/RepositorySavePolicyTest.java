package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class RepositorySavePolicyTest {
  @Test
  void givenRepositoryPolicyContract_whenValuesRequested_thenSupportedPoliciesAreReturned() {
    // Arrange
    var expected = new RepositorySavePolicy[] {RepositorySavePolicy.DO_NOT_SAVE, RepositorySavePolicy.SAVE_COMPLETED};

    // Act
    var actual = RepositorySavePolicy.values();

    // Assert
    assertArrayEquals(expected, actual);
  }
}
