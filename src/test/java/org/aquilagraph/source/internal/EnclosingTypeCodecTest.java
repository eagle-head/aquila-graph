package org.aquilagraph.source.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.aquilagraph.identity.api.CanonicalValueReader;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class EnclosingTypeCodecTest {
  @TestFactory
  Stream<DynamicTest> givenIndirectEnclosingView_whenOlderFormatsReadOrWrite_thenTheNewShapeIsRejected() {
    return IntStream.rangeClosed(2, 21).mapToObj(version -> DynamicTest.dynamicTest("givenIndirectView_whenReadOrWrittenAsFormat" + version + "_thenRejected", () -> {
      // Arrange
      var owner = new JavaTypeStructure.Declared("Outer", Optional.empty(), List.of());
      var value = new JavaTypeStructure.Declared("Outer.Box", Optional.of("Outer"),
          Optional.of(new JavaTypeStructure.Variable("Probe#O")), List.of(), Optional.of(owner));
      var format = "java-source/" + version;
      var previous = new RetainedValueCodec(false, format);
      var current = new CanonicalValueWriter(AstResult.SOURCE_MODEL_VERSION);
      new RetainedValueCodec(false).write(current, value);

      // Act
      var writing = assertThrows(IllegalArgumentException.class, () -> previous.write(new CanonicalValueWriter(format), value));
      var reading = assertThrows(IllegalArgumentException.class,
          () -> previous.read(new CanonicalValueReader(current.toByteArray(), AstResult.SOURCE_MODEL_VERSION)));

      // Assert
      assertEquals("Indirect enclosing instance views require retained AST format java-source/22", writing.getMessage());
      assertEquals("Wrong retained field count: java-type-structure/declared", reading.getMessage());
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenDirectEnclosingInstance_whenOlderFormatsRoundTrip_thenNoOwnerViewIsSynthesized() {
    return IntStream.rangeClosed(2, 21).mapToObj(version -> DynamicTest.dynamicTest("givenDirectInstance_whenFormat" + version + "IsRetained_thenItsBytesRemainIdentical", () -> {
      // Arrange
      var owner = new JavaTypeStructure.Declared("Outer", Optional.empty(), List.of());
      var value = new JavaTypeStructure.Declared("Outer.Box", Optional.of(owner), List.of());
      var format = "java-source/" + version;
      var codec = new RetainedValueCodec(false, format);
      var encoded = new CanonicalValueWriter(format);
      codec.write(encoded, value);

      // Act
      var decoded = codec.read(new CanonicalValueReader(encoded.toByteArray(), format));
      var retained = new CanonicalValueWriter(format);
      codec.write(retained, decoded);

      // Assert
      assertEquals(value, decoded);
      assertArrayEquals(encoded.toByteArray(), retained.toByteArray());
    }));
  }
}
