package org.aquilagraph.frontend;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Keeps compiler object correlation inside reference-identity lookup operations. */
record ConstructorIdentityLookup<K, V>(Function<K, Optional<V>> finder, BiConsumer<K, V> writer, Supplier<List<Entry<K, V>>> snapshot) {
  ConstructorIdentityLookup {
    Objects.requireNonNull(finder, "finder");
    Objects.requireNonNull(writer, "writer");
    Objects.requireNonNull(snapshot, "snapshot");
  }

  static <K, V> ConstructorIdentityLookup<K, V> usingReferenceIdentity() {
    var entries = new IdentityHashMap<K, V>();
    return new ConstructorIdentityLookup<>(key -> Optional.ofNullable(entries.get(key)), entries::put,
        () -> entries.entrySet().stream().map(entry -> new Entry<>(entry.getKey(), entry.getValue())).toList());
  }

  Optional<V> find(K key) {
    return finder.apply(key);
  }

  void remember(K key, V value) {
    writer.accept(key, value);
  }

  List<Entry<K, V>> entries() {
    return snapshot.get();
  }

  record Entry<K, V>(K key, V value) {
  }
}
