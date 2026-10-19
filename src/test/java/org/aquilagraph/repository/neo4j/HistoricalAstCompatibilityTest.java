package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.admission.AstCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class HistoricalAstCompatibilityTest {
  @Test
  void givenAuthenticModuleResult_whenReproducedWithOriginalProfile_thenOriginalIdentityAndBytesRemain() throws Exception {
    // Arrange
    var original = HistoricalAstFixtures.read(19);
    var request = new AstProductionRequest(new SourceInput.Snapshot(original.snapshot()), original.environment().compilerConfiguration(), original.profile());
    // Act
    var reproduced = new JavaFrontend(new JavaSourceLoader()).produce(request).result().orElseThrow();
    // Assert
    assertEquals(original.identity(), reproduced.identity());
    assertEquals(original.completion(), reproduced.completion());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(reproduced));
  }

  @TestFactory
  Stream<DynamicTest> givenOriginalHistoricalResults_whenDecodedAndMaterialized_thenOriginalIdentityQualificationAndValuesRemain() {
    // Arrange
    var formats = IntStream.rangeClosed(2, 19);

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
