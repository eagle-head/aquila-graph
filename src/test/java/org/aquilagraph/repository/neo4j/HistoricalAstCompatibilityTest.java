package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.aquilagraph.source.api.AstCompletion;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class HistoricalAstCompatibilityTest {
  @TestFactory
  Stream<DynamicTest> givenOriginalHistoricalResults_whenDecodedAndMaterialized_thenOriginalIdentityQualificationAndValuesRemain() {
    // Arrange
    var formats = IntStream.rangeClosed(2, 17);

    // Act
    var observations = formats.mapToObj(format -> DynamicTest.dynamicTest("givenOriginalSourceFormat" + format
        + "_whenDecodedAndMaterialized_thenOriginalIdentityQualificationAndValuesRemain", () -> {
          // Arrange
          var expectedFormat = format;

          // Act
          var result = HistoricalAstFixtures.read(expectedFormat);

          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion());
          assertEquals("java-source/" + expectedFormat, result.identity().sourceModelVersion());
        }));

    // Assert
    return observations;
  }
}
