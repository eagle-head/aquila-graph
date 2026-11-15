package org.aquilagraph.source.internal;

import static org.aquilagraph.source.internal.CoverageWrittenAttachments.Cardinality.LIST;
import static org.aquilagraph.source.internal.CoverageWrittenAttachments.Cardinality.OPTIONAL_VALUE;

import org.aquilagraph.source.admission.CoverageActualFactView;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.source.api.CoveragePropertyValue.Item;
import org.aquilagraph.source.api.CoveragePropertyValue.Provenance;
import org.aquilagraph.source.api.CoveragePropertyValue.State;

/** Reconciles independent written array accessor expectations with actual canonical source children. */
public final class CoverageArrayFacts {
  private CoverageArrayFacts() {
  }

  /**
   * Checks whether the copied facts establish the requested coverage property.
   *
   * @param accessor
   *          accessor
   * @param expected
   *          expected
   * @param owner
   *          owner
   * @param facts
   *          facts
   * @return whether the facts satisfy the required relation
   */
  public static boolean supports(String accessor, CoveragePropertyValue expected, CoverageFactReference owner,
      CoverageActualFactView facts) {
    var syntax = facts.requiredSyntax(owner);
    var form = syntax.value() instanceof SyntaxValue.ArrayCreation array ? array : new SyntaxValue.ArrayCreation(false, true, List.of());
    return switch (accessor) {
      case "getKind" ->
        expected.equals(new CoveragePropertyValue(State.SCALAR, List.of(new Item("NEW_ARRAY", "NEW_ARRAY", Provenance.WRITTEN))));
      case "getType" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.TYPE_OPERAND), facts, OPTIONAL_VALUE);
      case "getDimensions" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.DIMENSION), facts, LIST);
      case "getInitializers" -> form.initializer()
          ? CoverageWrittenAttachments.matches(expected, owner,
              List.of(syntax.syntaxKind() == SyntaxKind.ANNOTATION_ARRAY ? SyntaxRole.VALUE : SyntaxRole.ARRAY_ELEMENT), facts, LIST)
          : expected.state() == State.ABSENT && facts.children(owner, List.of(SyntaxRole.ARRAY_ELEMENT)).isEmpty();
      case "getAnnotations" ->
        form.initializer() ? annotations(expected, owner, facts) : expected.equals(new CoveragePropertyValue(State.EMPTY, List.of()));
      case "getDimAnnotations" -> dimensions(expected, owner, form, facts);
      default -> false;
    };
  }

  private static boolean annotations(CoveragePropertyValue expected, CoverageFactReference owner, CoverageActualFactView facts) {
    var children = facts.children(owner, List.of(SyntaxRole.ANNOTATION));
    var items = new ArrayList<Item>();
    for (var ordinal = 0; ordinal < children.size(); ordinal++) {
      var item = annotationItem(children.get(ordinal), ordinal, facts);
      if (item.isEmpty()) {
        return false;
      }
      items.add(item.orElseThrow());
    }
    return expected.equals(new CoveragePropertyValue(items.isEmpty() ? State.EMPTY : State.ITEMS, items));
  }

  private static Optional<Item> annotationItem(CoverageFactReference child, int ordinal, CoverageActualFactView facts) {
    var syntax = facts.requiredSyntax(child);
    if (syntax.syntaxKind() != SyntaxKind.ANNOTATION || !(syntax.value() instanceof SyntaxValue.Annotation annotation)
        || annotation.location() != SyntaxValue.AnnotationLocation.TYPE_USE) {
      return Optional.empty();
    }
    return facts.capturedText(child).map(text -> new Item("TYPE_ANNOTATION:" + ordinal, text, Provenance.WRITTEN));
  }

  private static boolean dimensions(CoveragePropertyValue expected, CoverageFactReference owner, SyntaxValue.ArrayCreation form,
      CoverageActualFactView facts) {
    if (form.initializer()) {
      return expected.equals(new CoveragePropertyValue(State.EMPTY, List.of()));
    }
    var annotations = facts.children(owner, List.of(SyntaxRole.ANNOTATION));
    var items = new ArrayList<Item>();
    var position = 0;
    for (var dimension = 0; dimension < form.dimensionAnnotationCounts().size(); dimension++) {
      var count = form.dimensionAnnotationCounts().get(dimension);
      items.add(new Item("dimension:" + dimension, Integer.toString(count), Provenance.WRITTEN));
      for (var ordinal = 0; ordinal < count; ordinal++) {
        if (position >= annotations.size()) {
          return false;
        }
        var item = annotationItem(annotations.get(position), ordinal, facts);
        position++;
        if (item.isEmpty()) {
          return false;
        }
        items.add(new Item("dimension:" + dimension + '/' + item.orElseThrow().identity(), item.orElseThrow().value(), Provenance.WRITTEN));
      }
    }
    return position == annotations.size() && expected.equals(new CoveragePropertyValue(items.isEmpty() ? State.EMPTY : State.ITEMS, items));
  }
}
