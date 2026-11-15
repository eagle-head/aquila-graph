package org.aquilagraph.source.admission;

import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.internal.CoverageActualFactUniverse;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoverageSyntaxFactValue;
import org.aquilagraph.source.api.SyntaxRole;
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
   * Finds the first actual reference equal to the entire supplied reference.
   *
   * @param claimed
   *          claimed reference, including value and origin
   * @return actual reference, absent if any component differs
   */
  default Optional<CoverageFactReference> reference(CoverageFactReference claimed) {
    var ordinal = indexOf(claimed);
    return ordinal >= 0 ? Optional.of(get(ordinal)) : Optional.empty();
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
   * Resolves the selected direct child attachments in structural order.
   *
   * @param parent
   *          exact actual syntax reference
   * @param roles
   *          selected attachment roles
   * @return immutable list of exact children, empty when no attachment has a selected role
   * @throws IllegalArgumentException
   *           if the parent or a selected attachment does not resolve to its exact source occurrence
   */
  List<CoverageFactReference> children(CoverageFactReference parent, List<SyntaxRole> roles);

  /**
   * Resolves a direct child at a single role-local position.
   *
   * @param parent
   *          exact actual syntax reference
   * @param role
   *          attachment role
   * @param position
   *          zero-based role-local position
   * @return exact child, absent when no attachment occupies the position
   * @throws IllegalArgumentException
   *           if the parent is foreign or the selected position has an invalid or ambiguous attachment
   */
  Optional<CoverageFactReference> child(CoverageFactReference parent, SyntaxRole role, int position);

  /**
   * Reads the captured text of an exact actual occurrence's origin.
   *
   * @param reference
   *          exact actual syntax reference
   * @return captured text, absent only if the occurrence has no origin
   * @throws IllegalArgumentException
   *           if the occurrence or its supplied span does not belong to the captured source
   */
  Optional<String> capturedText(CoverageFactReference reference);

  /**
   * Reads a span carried by an exact actual syntax occurrence, such as a package qualifier or written type.
   *
   * @param reference
   *          exact actual syntax reference that owns the evidence
   * @param span
   *          supplied span within the captured source of the occurrence
   * @return captured text
   * @throws IllegalArgumentException
   *           if the occurrence or span does not belong to the captured source
   */
  String capturedText(CoverageFactReference reference, SourceSpan span);

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
