package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.aquilagraph.repository.neo4j.BoltSchemaInspectionMessages.QueryKind;
import org.aquilagraph.repository.neo4j.BoltSchemaInspectionMessages.SchemaAcknowledgements;
import org.junit.jupiter.api.Test;

class BoltSchemaInspectionMessagesTest {
  @Test
  void givenExactMetadataQueryInThreeFieldRun_whenClassified_thenInspectionIsRecognized() throws IOException {
    // Arrange
    var payload = run(BoltSchemaInspectionMessages.INSPECTION, bytes("a0"));

    // Act
    var kind = BoltSchemaInspectionMessages.queryKind(payload);

    // Assert
    assertEquals(QueryKind.INSPECTION, kind);
  }

  @Test
  void givenMetadataTextOnlyInsideParameters_whenRunIsClassified_thenQueryRemainsOther() throws IOException {
    // Arrange
    var parameters = concat(bytes("a18576616c7565"), text(BoltSchemaInspectionMessages.INSPECTION));
    var payload = run("RETURN $value", parameters);

    // Act
    var kind = BoltSchemaInspectionMessages.queryKind(payload);

    // Assert
    assertEquals(QueryKind.OTHER, kind);
  }

  @Test
  void givenRequiredCreateQueries_whenClassified_thenTheTwoCompletionOwnersRemainDistinct() throws IOException {
    // Arrange
    var first = run(BoltSchemaInspectionMessages.ENTITY_CONSTRAINT, bytes("a0"));
    var second = run(BoltSchemaInspectionMessages.RUN_CONSTRAINT, bytes("a0"));

    // Act
    var firstKind = BoltSchemaInspectionMessages.queryKind(first);
    var secondKind = BoltSchemaInspectionMessages.queryKind(second);

    // Assert
    assertEquals(QueryKind.ENTITY_CONSTRAINT, firstKind);
    assertEquals(QueryKind.RUN_CONSTRAINT, secondKind);
  }

  @Test
  void givenWrongFieldCountOrTrailingBytes_whenRunIsClassified_thenMalformedStructureIsRejected() {
    // Arrange
    var wrongCount = bytes("b110");
    var trailing = concat(run(BoltSchemaInspectionMessages.INSPECTION, bytes("a0")), bytes("00"));

    // Act
    var countFailure = assertThrows(IOException.class, () -> BoltSchemaInspectionMessages.queryKind(wrongCount));
    var trailingFailure = assertThrows(IOException.class, () -> BoltSchemaInspectionMessages.queryKind(trailing));

    // Assert
    assertEquals("The Bolt message has an unexpected public field shape", countFailure.getMessage());
    assertEquals("The Bolt structure has trailing fields or bytes", trailingFailure.getMessage());
  }

  @Test
  void givenTopLevelAndNestedCompletionFlags_whenSuccessIsInspected_thenOnlyTheTopLevelFlagControlsCompletion() throws IOException {
    // Arrange
    var continuing = bytes("b170a1886861735f6d6f7265c3");
    var finished = bytes("b170a1886861735f6d6f7265c2");
    var nested = bytes("b170a1856f74686572a1886861735f6d6f7265c3");

    // Act
    var hasMore = BoltSchemaInspectionMessages.terminalSuccess(continuing);
    var hasFinished = BoltSchemaInspectionMessages.terminalSuccess(finished);
    var nestedFlag = BoltSchemaInspectionMessages.terminalSuccess(nested);

    // Assert
    assertFalse(hasMore);
    assertTrue(hasFinished);
    assertTrue(nestedFlag);
  }

  @Test
  void givenDuplicateOrNonbooleanCompletionFlag_whenSuccessIsInspected_thenInvalidMetadataIsRejected() {
    // Arrange
    var duplicate = bytes("b170a2886861735f6d6f7265c2886861735f6d6f7265c2");
    var numeric = bytes("b170a1886861735f6d6f726500");

    // Act
    var duplicateFailure = assertThrows(IOException.class, () -> BoltSchemaInspectionMessages.terminalSuccess(duplicate));
    var typeFailure = assertThrows(IOException.class, () -> BoltSchemaInspectionMessages.terminalSuccess(numeric));

    // Assert
    assertEquals("The Bolt SUCCESS metadata repeats the completion field", duplicateFailure.getMessage());
    assertEquals("The Bolt completion field is not a boolean", typeFailure.getMessage());
  }

  @Test
  void givenRunSuccessWithoutTerminalResult_whenAcknowledged_thenSchemaIsNotYetCompleted() throws IOException {
    // Arrange
    var schema = new SchemaAcknowledgements();

    // Act
    schema.acceptSummary(BoltSchemaInspectionMessages.RUN, QueryKind.ENTITY_CONSTRAINT, bytes("b170a0"));
    var failure = assertThrows(IOException.class, schema::requireInspectionPhase);

    // Assert
    assertEquals(0, schema.entityCompletions());
    assertEquals(0, schema.runCompletions());
    assertEquals("Metadata interception requires both completed schema statements", failure.getMessage());
  }

  @Test
  void givenTerminalPullThenDiscardSuccess_whenAcknowledged_thenInspectionMayFollowBothCompletedStatements() throws IOException {
    // Arrange
    var schema = new SchemaAcknowledgements();

    // Act
    schema.acceptSummary(BoltSchemaInspectionMessages.PULL, QueryKind.ENTITY_CONSTRAINT, bytes("b170a0"));
    var partial = assertThrows(IOException.class, schema::requireInspectionPhase);
    schema.acceptSummary(BoltSchemaInspectionMessages.DISCARD, QueryKind.RUN_CONSTRAINT, bytes("b170a0"));
    schema.requireInspectionPhase();

    // Assert
    assertEquals("Metadata interception requires both completed schema statements", partial.getMessage());
    assertEquals(1, schema.entityCompletions());
    assertEquals(1, schema.runCompletions());
  }

  @Test
  void givenContinuingResultOrFailureSummary_whenAcknowledged_thenConstraintCompletionIsNotInvented() throws IOException {
    // Arrange
    var schema = new SchemaAcknowledgements();

    // Act
    schema.acceptSummary(BoltSchemaInspectionMessages.PULL, QueryKind.ENTITY_CONSTRAINT, bytes("b170a1886861735f6d6f7265c3"));
    schema.acceptSummary(BoltSchemaInspectionMessages.DISCARD, QueryKind.ENTITY_CONSTRAINT, bytes("b17fa0"));

    // Assert
    assertEquals(0, schema.entityCompletions());
    assertThrows(IOException.class, schema::requireInspectionPhase);
  }

  @Test
  void givenOutOfOrderOrRepeatedTerminalCompletion_whenAcknowledged_thenObservationFailsExplicitly() throws IOException {
    // Arrange
    var outOfOrder = new SchemaAcknowledgements();
    var repeated = new SchemaAcknowledgements();
    repeated.acceptSummary(BoltSchemaInspectionMessages.PULL, QueryKind.ENTITY_CONSTRAINT, bytes("b170a0"));

    // Act
    var orderFailure = assertThrows(IOException.class,
        () -> outOfOrder.acceptSummary(BoltSchemaInspectionMessages.PULL, QueryKind.RUN_CONSTRAINT, bytes("b170a0")));
    var repeatFailure = assertThrows(IOException.class,
        () -> repeated.acceptSummary(BoltSchemaInspectionMessages.PULL, QueryKind.ENTITY_CONSTRAINT, bytes("b170a0")));

    // Assert
    assertEquals("The second schema statement did not complete once after the first", orderFailure.getMessage());
    assertEquals("The first schema statement completed more than once", repeatFailure.getMessage());
  }

  @Test
  void givenReservedOrExcessivelyNestedFields_whenSuccessIsInspected_thenUnsupportedObservationIsRejected() {
    // Arrange
    var reserved = bytes("b170a1856f74686572c7");
    var deep = new ByteArrayOutputStream();
    deep.writeBytes(bytes("b170a1856f74686572"));
    for (var depth = 0; depth < 34; depth++) {
      deep.write(0x91);
    }
    deep.write(0xC0);

    // Act
    var reservedFailure = assertThrows(IOException.class, () -> BoltSchemaInspectionMessages.terminalSuccess(reserved));
    var depthFailure = assertThrows(IOException.class, () -> BoltSchemaInspectionMessages.terminalSuccess(deep.toByteArray()));

    // Assert
    assertEquals("The Bolt field uses an unsupported or reserved PackStream marker", reservedFailure.getMessage());
    assertEquals("The Bolt field nesting exceeds this observation's bound", depthFailure.getMessage());
  }

  private static byte[] run(String query, byte[] parameters) {
    return concat(bytes("b310"), text(query), parameters, bytes("a0"));
  }

  private static byte[] text(String value) {
    var encoded = value.getBytes(StandardCharsets.UTF_8);
    var text = new ByteArrayOutputStream();
    text.write(0xD2);
    text.write(encoded.length >>> 24);
    text.write(encoded.length >>> 16);
    text.write(encoded.length >>> 8);
    text.write(encoded.length);
    text.writeBytes(encoded);
    return text.toByteArray();
  }

  private static byte[] bytes(String hexadecimal) {
    return HexFormat.of().parseHex(hexadecimal);
  }

  private static byte[] concat(byte[]... parts) {
    var combined = new ByteArrayOutputStream();
    for (var part : parts) {
      combined.writeBytes(part);
    }
    return combined.toByteArray();
  }
}
