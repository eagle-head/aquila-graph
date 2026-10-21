package org.aquilagraph.identity.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StableIdTest {
  @Test
  void givenALargeMultibyteComponent_whenAnIdentityIsDerived_thenEveryLengthByteContributesToTheFixedDigest() {
    // Arrange
    // The UTF-8 component length is 0x01010101, exercising all four length bytes.
    var component = "\u00e9".repeat(0x00808080) + "a";

    // Act
    var identity = StableId.sha256("identity", component);

    // Assert
    assertEquals("identity:830c8bd93f090d5edc1d7e251042ad2eee8f77f56dc95d195c9300f7061901c0", identity);
  }
}
