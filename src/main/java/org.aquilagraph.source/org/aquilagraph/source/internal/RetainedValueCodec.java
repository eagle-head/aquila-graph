package org.aquilagraph.source.internal;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import org.aquilagraph.identity.api.CanonicalValueReader;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.internal.AstValueSchema.Container;
import org.aquilagraph.source.internal.AstValueSchema.Fields;
import org.aquilagraph.source.internal.AstValueSchema.Scalar;
import org.jspecify.annotations.Nullable;

/** Reads and writes a retained value stream with operation-owned occurrence references. */
public final class RetainedValueCodec {
  private static final String SCALAR_FORMAT = "source-value/1";
  private final boolean canonical;
  private final Function<SyntaxNode, @Nullable Integer> nodeOrdinal;
  private final ToIntFunction<SyntaxNode> registerNode;
  private final List<@Nullable SyntaxNode> readNodes = new ArrayList<>();

  /**
   * Creates one codec for an entire retained or canonical value stream.
   *
   * @param canonical
   *          whether to use identity fields and canonical order
   */
  public RetainedValueCodec(boolean canonical) {
    this.canonical = canonical;
    var nodes = new IdentityHashMap<SyntaxNode, Integer>();
    nodeOrdinal = nodes::get;
    registerNode = node -> {
      var ordinal = nodes.size();
      nodes.put(node, ordinal);
      return ordinal;
    };
  }

  /**
   * Writes a value using its registered shape and this stream's occurrence slots.
   *
   * @param writer
   *          value destination
   * @param value
   *          copied value to write
   */
  public void write(CanonicalValueWriter writer, Object value) {
    switch (AstValueSchema.shape(value)) {
      case Scalar scalar -> {
        writer.text(scalar.tag());
        scalar.write().accept(writer, value);
      }
      case Container container -> writeContainer(writer, container, value);
      case Fields fields -> writeFields(writer, fields, value);
    }
  }

  private void writeContainer(CanonicalValueWriter writer, Container shape, Object value) {
    writer.text(shape.tag());
    var members = shape.members(value, set -> orderedSet(set, canonical));
    if (shape == Container.LIST || shape == Container.SET) {
      writer.count(members.size());
    }
    members.forEach(member -> write(writer, member));
  }

  private void writeFields(CanonicalValueWriter writer, Fields fields, Object value) {
    if (fields.occurrence()) {
      var node = required(value, SyntaxNode.class);
      var previous = nodeOrdinal.apply(node);
      if (previous != null) {
        writer.text(AstValueSchema.OCCURRENCE_REFERENCE);
        writer.integer(previous);
        return;
      }
      writer.text(fields.tag());
      writer.integer(registerNode.applyAsInt(node));
    } else {
      writer.text(fields.tag());
    }
    var components = fields.components(value, canonical);
    writer.count(components.size());
    for (var component : components) {
      writer.text(component.name());
      write(writer, component.value());
    }
  }

  /**
   * Reads a value using its registered shape and this stream's occurrence slots.
   *
   * @param reader
   *          value source
   * @return validated value
   */
  public Object read(CanonicalValueReader reader) {
    return read(reader, Object.class);
  }

  /**
   * Reads a value whose registered shape must agree with the required type before its fields are read.
   *
   * @param <T>
   *          required decoded value type
   * @param reader
   *          value source
   * @param type
   *          required decoded value type
   * @return validated value
   */
  public <T> T read(CanonicalValueReader reader, Class<T> type) {
    return required(read(reader, new ValuePosition(type, null, type.getSimpleName())), type);
  }

  private Object read(CanonicalValueReader reader, ValuePosition root) {
    var unfinished = new ArrayDeque<UnfinishedValue>();
    var position = root;
    while (true) {
      var decoded = startValue(reader, position);
      if (decoded instanceof UnfinishedValue composite && composite.hasNext()) {
        unfinished.addLast(composite);
        position = composite.next();
        continue;
      }
      var value = decoded instanceof UnfinishedValue composite ? composite.finish() : decoded;
      while (!unfinished.isEmpty()) {
        var parent = unfinished.getLast();
        parent.accept(value);
        if (parent.hasNext()) {
          break;
        }
        value = unfinished.removeLast().finish();
      }
      if (unfinished.isEmpty()) {
        return value;
      }
      position = unfinished.getLast().next();
    }
  }

  private Object startValue(CanonicalValueReader reader, ValuePosition position) {
    var expected = boundType(position.type(), position.owner());
    var tag = reader.text();
    if (AstValueSchema.OCCURRENCE_REFERENCE.equals(tag)) {
      AstValueSchema.requireAssignable(expected, SyntaxNode.class, position.field());
      return readReference(reader);
    }
    var shape = AstValueSchema.tag(tag);
    AstValueSchema.requireAssignable(expected, shape.valueType(), position.field());
    return switch (shape) {
      case Scalar scalar -> scalar.read().apply(reader);
      case Container container -> startContainer(reader, container, expected, position);
      case Fields fields when fields.occurrence() -> startOccurrence(reader, fields);
      case Fields fields -> startFields(reader, fields, expected, fields::reconstruct);
    };
  }

  private static Type boundType(Type type, @Nullable ParameterizedType owner) {
    if (type instanceof TypeVariable<?> variable && owner != null && owner.getRawType().equals(variable.getGenericDeclaration())) {
      var parameters = Arrays.asList(variable.getGenericDeclaration().getTypeParameters());
      return owner.getActualTypeArguments()[parameters.indexOf(variable)];
    }
    return type;
  }

  private Object startContainer(CanonicalValueReader reader, Container shape, Type expected, ValuePosition position) {
    var member = expected instanceof ParameterizedType parameterized ? parameterized.getActualTypeArguments()[0] : Object.class;
    var memberPosition = new ValuePosition(member, position.owner(), position.field());
    return switch (shape) {
      case PRESENT -> new UnfinishedValue(1, ignored -> memberPosition, values -> Optional.of(values.getFirst()));
      case ABSENT -> Optional.empty();
      case LIST -> startSequence(reader, memberPosition, List::copyOf);
      case SET -> startSequence(reader, memberPosition, RetainedValueCodec::assembleSet);
    };
  }

  private static UnfinishedValue startSequence(CanonicalValueReader reader, ValuePosition member, Function<List<Object>, Object> assemble) {
    var count = reader.count();
    if (count > reader.remaining() / Integer.BYTES) {
      throw new IllegalArgumentException("Retained sequence count exceeds the remaining payload");
    }
    return new UnfinishedValue(count, ignored -> member, assemble);
  }

  private static Set<Object> assembleSet(List<Object> values) {
    var result = new LinkedHashSet<>(values);
    if (result.size() != values.size()) {
      throw new IllegalArgumentException("Retained set cannot contain repeated entries");
    }
    return Set.copyOf(result);
  }

  private static UnfinishedValue startFields(CanonicalValueReader reader, Fields shape, Type expected,
      Function<List<Object>, Object> assemble) {
    if (reader.count() != shape.fields().size()) {
      throw new IllegalArgumentException("Wrong retained field count: " + shape.tag());
    }
    var owner = expected instanceof ParameterizedType parameterized ? parameterized : null;
    return new UnfinishedValue(shape.fields().size(), index -> {
      var field = shape.fields().get(index);
      if (!field.name().equals(reader.text())) {
        throw new IllegalArgumentException("Wrong retained field order: " + shape.tag() + "/" + field.name());
      }
      return new ValuePosition(field.type(), owner, shape.tag() + "/" + field.name());
    }, assemble);
  }

  private UnfinishedValue startOccurrence(CanonicalValueReader reader, Fields shape) {
    var ordinal = reader.integer();
    if (ordinal != readNodes.size()) {
      throw new IllegalArgumentException("Source occurrence slots must be contiguous");
    }
    readNodes.add(null);
    return startFields(reader, shape, SyntaxNode.class, values -> {
      var node = required(shape.reconstruct(values), SyntaxNode.class);
      readNodes.set(ordinal, node);
      return node;
    });
  }

  private record ValuePosition(Type type, @Nullable ParameterizedType owner, String field) {
  }

  private static final class UnfinishedValue {
    private final int componentCount;
    private final IntFunction<ValuePosition> componentAt;
    private final Function<List<Object>, Object> assemble;
    private final List<Object> components = new ArrayList<>();

    private UnfinishedValue(int componentCount, IntFunction<ValuePosition> componentAt, Function<List<Object>, Object> assemble) {
      this.componentCount = componentCount;
      this.componentAt = componentAt;
      this.assemble = assemble;
    }

    private boolean hasNext() {
      return components.size() < componentCount;
    }

    private ValuePosition next() {
      return componentAt.apply(components.size());
    }

    private void accept(Object component) {
      components.add(component);
    }

    private Object finish() {
      return assemble.apply(components);
    }
  }

  private SyntaxNode readReference(CanonicalValueReader reader) {
    var ordinal = reader.integer();
    if (ordinal >= 0 && ordinal < readNodes.size()) {
      var node = readNodes.get(ordinal);
      if (node != null) {
        return node;
      }
    }
    throw new IllegalArgumentException("Source occurrence reference requires an earlier complete occurrence");
  }

  static List<?> orderedSet(Set<?> values, boolean canonical) {
    var ordered = new ArrayList<>(values);
    ordered.sort((left, right) -> Arrays.compareUnsigned(valueBytes(left, canonical), valueBytes(right, canonical)));
    return ordered;
  }

  static byte[] valueBytes(Object value, boolean canonical) {
    var writer = new CanonicalValueWriter(SCALAR_FORMAT);
    new RetainedValueCodec(canonical).write(writer, value);
    return writer.toByteArray();
  }

  /**
   * Requires a decoded value of the requested closed-schema type.
   *
   * @param <T>
   *          required decoded value type
   * @param value
   *          decoded value
   * @param type
   *          required value type
   * @return validated value with its requested type
   */
  public static <T> T required(Object value, Class<T> type) {
    if (!type.isInstance(value)) {
      throw new IllegalArgumentException("Unexpected retained value for " + type.getSimpleName());
    }
    return Objects.requireNonNull(type.cast(value), "retained value");
  }
}
