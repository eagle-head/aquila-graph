package org.aquilagraph.repository.internal.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.SourceInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class HistoricalAstCompatibilityTest {
  @Test
  void givenAuthenticModuleResult_whenReproducedWithOriginalProfile_thenSuccessorQualificationPreservesSourceBehavior() throws Exception {
    // Arrange
    var original = HistoricalAstFixtures.read(19);
    var request = new AstProductionRequest(new SourceInput.Snapshot(original.snapshot()), original.environment().compilerConfiguration(), original.profile());
    // Act
    var reproduced = new JavaFrontend(new JavaSourceLoader()).produce(request).result().orElseThrow();
    // Assert
    assertEquals("java-source/22", reproduced.identity().sourceModelVersion());
    assertNotEquals(original.identity(), reproduced.identity());
    assertEquals(original.completion(), reproduced.completion());
    assertEquals(original.snapshot(), reproduced.snapshot());
    assertEquals(original.profile(), reproduced.profile());
    assertEquals(original.program(), reproduced.program());
    assertEquals(original.diagnostics(), reproduced.diagnostics());
  }

  @TestFactory
  Stream<DynamicTest> givenOriginalHistoricalResults_whenDecodedAndMaterialized_thenOriginalIdentityQualificationAndValuesRemain() {
    // Arrange
    var formats = IntStream.rangeClosed(2, 21);

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
