package org.aquilagraph.source.api;

import java.util.List;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Ordered immutable actual references and their exactly correlated syntax-local views. */
public sealed interface CoverageActualFactView extends Iterable<CoverageFactReference> permits CoverageActualFactUniverse {
  /**
   * Returns the independently copied actual references in retained order.
   *
   * @return immutable actual reference list
   */
  List<CoverageFactReference> references();

  /**
   * Reads one actual retained reference by ordinal.
   *
   * @param ordinal
   *          actual retained ordinal
   * @return actual reference
   */
  default CoverageFactReference get(int ordinal) {
    return references().get(ordinal);
  }

  /**
   * Counts actual retained references.
   *
   * @return actual reference count
   */
  default int size() {
    return references().size();
  }

  /**
   * Locates the first exact reference in retained order.
   *
   * @param reference
   *          reference to locate
   * @return actual ordinal, or minus one when absent
   */
  default int indexOf(CoverageFactReference reference) {
    return references().indexOf(reference);
  }

  /**
   * Streams actual retained references in order.
   *
   * @return actual reference stream
   */
  default Stream<CoverageFactReference> stream() {
    return references().stream();
  }

  @Override
  default Iterator<CoverageFactReference> iterator() {
    return references().iterator();
  }

  /**
   * Reads the actual syntax view only for its exact independently copied neutral reference.
   *
   * @param reference
   *          claimed or actual reference
   * @return actual view, absent for a foreign reference or nonsyntax fact
   */
  Optional<CoverageSyntaxFactValue> sourceValue(CoverageFactReference reference);

  /**
   * Requires the exact actual syntax view for a source reference.
   *
   * @param reference
   *          actual source reference
   * @return actual typed view
   */
  CoverageSyntaxFactValue requiredSyntax(CoverageFactReference reference);

  /**
   * Applies a local source condition to the exact actual view.
   *
   * @param reference
   *          reference to check
   * @param condition
   *          local source condition
   * @return whether the actual source satisfies the condition
   */
  boolean isSource(CoverageFactReference reference, Predicate<CoverageSyntaxFactValue> condition);

  /**
   * Streams the exact actual source view.
   *
   * @param reference
   *          reference to read
   * @return zero or one actual view
   */
  Stream<CoverageSyntaxFactValue> syntaxValues(CoverageFactReference reference);
}
