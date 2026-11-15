package org.aquilagraph.source.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.aquilagraph.identity.api.CanonicalValueReader;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.junit.jupiter.api.Test;

class EnclosingTypeCodecTest {
  @Test
  void givenDirectAndIndirectEnclosingTypes_whenRetained_thenOnlyTheIndirectTypeHasADistinctOwnerView() {
    // Arrange
    var owner = new JavaTypeStructure.Declared("Outer", Optional.empty(), List.of());
    var capture = new JavaTypeStructure.Variable("Probe#O");
    var direct = new JavaTypeStructure.Declared("Outer.Box", Optional.of(owner), List.of());
    var indirect = new JavaTypeStructure.Declared("Outer.Box", Optional.of("Outer"), Optional.of(capture), List.of(), Optional.of(owner));
    var codec = new RetainedValueCodec(false);
    var writer = new CanonicalValueWriter(AstResult.SOURCE_MODEL_VERSION);
    codec.write(writer, List.of(direct, indirect));
    var reader = new CanonicalValueReader(writer.toByteArray(), AstResult.SOURCE_MODEL_VERSION);

    // Act
    var restored = (List<?>) codec.read(reader);

    // Assert
    reader.requireFinished();
    assertEquals(List.of(direct, indirect), restored);
  }
}
