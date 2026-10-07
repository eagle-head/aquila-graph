package org.aquilagraph.source.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Immutable actual references and syntax-local typed views, independently read from actual retained values. */
public final class CoverageActualFactUniverse implements CoverageActualFactView {
  private final List<CoverageFactReference> orderedReferences;
  private final Map<String, SourceEntry> sourceValues;

  CoverageActualFactUniverse(List<CoverageFactReference> references, Map<String, CoverageSyntaxFactValue> syntaxValues) {
    orderedReferences = List.copyOf(references);
    var entries = new HashMap<String, SourceEntry>();
    for (var reference : orderedReferences) {
      if (reference.kind() == CoverageFactReference.Kind.SYNTAX) {
        registerSource(reference, syntaxValues, entries);
      }
    }
    if (entries.size() != syntaxValues.size()) {
      throw new IllegalArgumentException("Every actual typed source view requires one exact neutral reference");
    }
    sourceValues = Map.copyOf(entries);
  }

  private static void registerSource(CoverageFactReference reference, Map<String, CoverageSyntaxFactValue> syntaxValues,
      Map<String, SourceEntry> entries) {
    var view = Optional.ofNullable(syntaxValues.get(reference.identity())).orElseThrow();
    if (!reference.value().equals(new CoverageFactValue.Source(CoverageSourceProjection.copy(view)))
        || entries.putIfAbsent(reference.identity(), new SourceEntry(reference, view)) != null) {
      throw new IllegalArgumentException("Typed actual source views require exact unique neutral fact values");
    }
  }

  @Override
  public List<CoverageFactReference> references() {
    return orderedReferences;
  }

  @Override
  public CoverageFactReference get(int index) {
    return orderedReferences.get(index);
  }

  @Override
  public int size() {
    return orderedReferences.size();
  }

  /**
   * Returns the actual syntax view only when its independently copied neutral reference exactly equals the supplied reference.
   *
   * @param reference
   *          claimed or actual reference
   * @return actual view, absent for a foreign reference or nonsyntax fact
   */
  @Override
  public Optional<CoverageSyntaxFactValue> sourceValue(CoverageFactReference reference) {
    var entry = sourceValues.get(reference.identity());
    return entry != null && entry.reference().equals(reference) ? Optional.of(entry.value()) : Optional.empty();
  }

  /**
   * Reads an exact actual syntax view.
   *
   * @param reference
   *          actual source reference
   * @return actual typed view
   */
  @Override
  public CoverageSyntaxFactValue requiredSyntax(CoverageFactReference reference) {
    return sourceValue(reference)
        .orElseThrow(() -> new IllegalArgumentException("A syntax view requires the exact independently copied actual reference"));
  }

  /**
   * Filters an exact actual source view.
   *
   * @param reference
   *          reference to check
   * @param condition
   *          local source condition
   * @return whether the actual source satisfies it
   */
  @Override
  public boolean isSource(CoverageFactReference reference, Predicate<CoverageSyntaxFactValue> condition) {
    return sourceValue(reference).filter(condition).isPresent();
  }

  /**
   * Streams an exact actual source view.
   *
   * @param reference
   *          reference to read
   * @return zero or one actual view
   */
  @Override
  public Stream<CoverageSyntaxFactValue> syntaxValues(CoverageFactReference reference) {
    return sourceValue(reference).stream();
  }

  private record SourceEntry(CoverageFactReference reference, CoverageSyntaxFactValue value) {
  }
}
