package org.aquilagraph.repository.internal.neo4j;



import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** Inspects bounded schema query and completion messages without retaining other message values. */
final class BoltSchemaInspectionMessages {
  static final int RUN = 0x10;
  static final int PULL = 0x3F;
  static final int DISCARD = 0x2F;
  static final String ENTITY_CONSTRAINT = "CREATE CONSTRAINT analysis_entity_identity IF NOT EXISTS "
      + "FOR (entity:AnalysisEntity) REQUIRE (entity.runId, entity.key) IS UNIQUE";
  static final String RUN_CONSTRAINT = "CREATE CONSTRAINT analysis_run_identity IF NOT EXISTS "
      + "FOR (run:AnalysisRun) REQUIRE run.runId IS UNIQUE";
  static final String INSPECTION = "SHOW CONSTRAINTS YIELD name, type, entityType, labelsOrTypes, properties "
      + "WHERE name IN $names RETURN name, type, entityType, labelsOrTypes, properties";
  private static final int SUCCESS = 0x70;

  private BoltSchemaInspectionMessages() {
  }

  static QueryKind queryKind(byte[] payload) throws IOException {
    if (BoltCommitAcknowledgementProxy.messageTag(payload) != RUN) {
      return QueryKind.OTHER;
    }
    var decoder = new Decoder(payload);
    decoder.require(0xB3);
    decoder.require(RUN);
    var query = decoder.text();
    decoder.dictionary(0);
    decoder.dictionary(0);
    decoder.requireEnd();
    if (ENTITY_CONSTRAINT.equals(query)) {
      return QueryKind.ENTITY_CONSTRAINT;
    }
    if (RUN_CONSTRAINT.equals(query)) {
      return QueryKind.RUN_CONSTRAINT;
    }
    return INSPECTION.equals(query) ? QueryKind.INSPECTION : QueryKind.OTHER;
  }

  static boolean terminalSuccess(byte[] payload) throws IOException {
    var decoder = new Decoder(payload);
    decoder.require(0xB1);
    decoder.require(SUCCESS);
    var entries = decoder.dictionarySize();
    var more = false;
    var found = false;
    for (var index = 0; index < entries; index++) {
      var key = decoder.text();
      if ("has_more".equals(key)) {
        if (found) {
          throw new IOException("The Bolt SUCCESS metadata repeats the completion field");
        }
        found = true;
        more = decoder.booleanValue();
      } else {
        decoder.skip(0);
      }
    }
    decoder.requireEnd();
    return !more;
  }

  enum QueryKind {
    ENTITY_CONSTRAINT, RUN_CONSTRAINT, INSPECTION, OTHER
  }

  static final class SchemaAcknowledgements {
    private final AtomicInteger entityCompletions = new AtomicInteger();
    private final AtomicInteger runCompletions = new AtomicInteger();

    void acceptSummary(int requestTag, QueryKind query, byte[] payload) throws IOException {
      if ((requestTag != PULL && requestTag != DISCARD) || BoltCommitAcknowledgementProxy.messageTag(payload) != SUCCESS
          || !terminalSuccess(payload)) {
        return;
      }
      if (query == QueryKind.ENTITY_CONSTRAINT && !entityCompletions.compareAndSet(0, 1)) {
        throw new IOException("The first schema statement completed more than once");
      }
      if (query == QueryKind.RUN_CONSTRAINT) {
        if (entityCompletions.get() != 1 || !runCompletions.compareAndSet(0, 1)) {
          throw new IOException("The second schema statement did not complete once after the first");
        }
      }
    }

    void requireInspectionPhase() throws IOException {
      if (entityCompletions.get() != 1 || runCompletions.get() != 1) {
        throw new IOException("Metadata interception requires both completed schema statements");
      }
    }

    int entityCompletions() {
      return entityCompletions.get();
    }

    int runCompletions() {
      return runCompletions.get();
    }
  }

  private static final class Decoder {
    private static final int MAX_NESTING = 32;
    private final byte[] payload;
    private int position;

    private Decoder(byte[] payload) {
      this.payload = payload;
    }

    private int next() throws IOException {
      requireBytes(1);
      return Byte.toUnsignedInt(payload[position++]);
    }

    private void require(int expected) throws IOException {
      if (next() != expected) {
        throw new IOException("The Bolt message has an unexpected public field shape");
      }
    }

    private boolean booleanValue() throws IOException {
      var marker = next();
      if (marker != 0xC2 && marker != 0xC3) {
        throw new IOException("The Bolt completion field is not a boolean");
      }
      return marker == 0xC3;
    }

    private String text() throws IOException {
      var length = length(next(), 0x80, 0xD0);
      requireBytes(length);
      var value = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(payload, position, length)).toString();
      position += length;
      return value;
    }

    private int dictionarySize() throws IOException {
      var count = length(next(), 0xA0, 0xD8);
      if (count > (payload.length - position) / 2) {
        throw new EOFException("The Bolt dictionary exceeds the remaining message");
      }
      return count;
    }

    private void dictionary(int depth) throws IOException {
      var entries = dictionarySize();
      for (var index = 0; index < entries; index++) {
        text();
        skip(depth + 1);
      }
    }

    private void skip(int depth) throws IOException {
      if (depth > MAX_NESTING) {
        throw new IOException("The Bolt field nesting exceeds this observation's bound");
      }
      var marker = next();
      if (marker <= 0x7F || marker >= 0xF0 || marker == 0xC0 || marker == 0xC2 || marker == 0xC3) {
        return;
      }
      if ((marker & 0xF0) == 0x80 || marker >= 0xD0 && marker <= 0xD2) {
        advance(length(marker, 0x80, 0xD0));
      } else if ((marker & 0xF0) == 0x90 || marker >= 0xD4 && marker <= 0xD6) {
        values(length(marker, 0x90, 0xD4), depth);
      } else if ((marker & 0xF0) == 0xA0 || marker >= 0xD8 && marker <= 0xDA) {
        var entries = length(marker, 0xA0, 0xD8);
        if (entries > (payload.length - position) / 2) {
          throw new EOFException("The Bolt dictionary exceeds the remaining message");
        }
        for (var index = 0; index < entries; index++) {
          text();
          skip(depth + 1);
        }
      } else if ((marker & 0xF0) == 0xB0) {
        next();
        values(marker & 0x0F, depth);
      } else {
        switch (marker) {
          case 0xC1, 0xCB -> advance(8);
          case 0xC8 -> advance(1);
          case 0xC9 -> advance(2);
          case 0xCA -> advance(4);
          case 0xCC, 0xCD, 0xCE -> advance(unsignedSize(marker - 0xCC));
          default -> throw new IOException("The Bolt field uses an unsupported or reserved PackStream marker");
        }
      }
    }

    private void values(int count, int depth) throws IOException {
      if (count > payload.length - position) {
        throw new EOFException("The Bolt collection exceeds the remaining message");
      }
      for (var index = 0; index < count; index++) {
        skip(depth + 1);
      }
    }

    private int length(int marker, int tiny, int sized) throws IOException {
      if ((marker & 0xF0) == tiny) {
        return marker & 0x0F;
      }
      if (marker >= sized && marker <= sized + 2) {
        return unsignedSize(marker - sized);
      }
      throw new IOException("The Bolt field does not have the required PackStream type");
    }

    private int unsignedSize(int scale) throws IOException {
      long size = 0;
      for (var index = 0; index < 1 << scale; index++) {
        size = (size << 8) | next();
      }
      if (size > payload.length) {
        throw new IOException("The Bolt field size exceeds the bounded message");
      }
      return Math.toIntExact(size);
    }

    private void advance(int count) throws IOException {
      requireBytes(count);
      position += count;
    }

    private void requireBytes(int count) throws IOException {
      if (count > payload.length - position) {
        throw new EOFException("EOF within a bounded PackStream field");
      }
    }

    private void requireEnd() throws IOException {
      if (position != payload.length) {
        throw new IOException("The Bolt structure has trailing fields or bytes");
      }
    }
  }
}
