package org.aquilagraph.repository.internal.neo4j;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class HistoricalAstCompatibilityTest {
  @TestFactory
  Stream<DynamicTest> givenOriginalHistoricalResults_whenDecoded_thenTheirRetiredFormatsAreRefused() {
    // Arrange
    var formats = IntStream.rangeClosed(2, 21);

    // Act
    var observations = formats.mapToObj(version -> DynamicTest.dynamicTest("givenOriginalSourceFormat" + version
        + "_whenDecoded_thenItsRetiredFormatIsRefused", () -> {
          // Arrange
          var original = HistoricalAstFixtures.read(version);

          // Act
          var failure = assertThrows(AstContractException.class, () -> AstCodec.decode(original.payload(), original.identity()));

          // Assert
          var cause = assertInstanceOf(IllegalArgumentException.class, failure.getCause());
          assertTrue(cause.getMessage().contains("Unsupported retained AST format: java-source/" + version), cause::getMessage);
        }));

    // Assert
    return observations;
  }
}
