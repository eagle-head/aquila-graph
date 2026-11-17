package org.aquilagraph.source.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.admission.CoverageActualFactView;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoverageFactValue;
import org.aquilagraph.source.api.CoverageSyntaxFactValue;
import org.aquilagraph.source.api.CoverageSyntaxFactValue.Child;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxStructuralPath.Step;

/** Immutable actual references and syntax-local typed views, independently read from actual retained values. */
public final class CoverageActualFactUniverse implements CoverageActualFactView {
  private static final int MAX_ATTACHMENTS_PER_POSITION = 1;
  private final List<CoverageFactReference> orderedReferences;
  private final Map<String, SourceEntry> sourceValues;
  private final Map<String, String> capturedSources;
  private final CoverageFactIndex index;

  /**
   * Owns the copied nodes and declaration lookup used by coverage admission.
   *
   * @param references
   *          references
   * @param syntaxValues
   *          syntax values
   * @param sources
   *          source files captured in the same input snapshot
   */
  public CoverageActualFactUniverse(List<CoverageFactReference> references, Map<String, CoverageSyntaxFactValue> syntaxValues,
      List<SourceFile> sources) {
    orderedReferences = List.copyOf(references);
    var copiedSyntax = Map.copyOf(syntaxValues);
    var entries = new HashMap<String, SourceEntry>();
    for (var reference : orderedReferences) {
      if (reference.kind() == CoverageFactReference.Kind.SYNTAX) {
        registerSource(reference, copiedSyntax, entries);
      }
    }
    if (entries.size() != copiedSyntax.size()) {
      throw new IllegalArgumentException("Every actual typed source view requires one exact neutral reference");
    }
    sourceValues = Map.copyOf(entries);
    capturedSources = sources.stream().collect(Collectors.toUnmodifiableMap(SourceFile::sourceFileKey, SourceFile::content));
    index = new CoverageFactIndex(orderedReferences, copiedSyntax);
  }

  @Override
  public int indexOf(CoverageFactReference reference) {
    return index.indexOf(reference);
  }

  @Override
  public List<CoverageFactReference> inSource(String sourceFileKey) {
    return index.inSource(sourceFileKey);
  }

  @Override
  public List<CoverageFactReference> withIdentity(String sourceFileKey, String identity) {
    return index.withIdentity(sourceFileKey, identity);
  }

  @Override
  public List<CoverageFactReference> withIdentities(String sourceFileKey, List<String> identities) {
    return index.withIdentities(sourceFileKey, identities);
  }

  @Override
  public List<CoverageFactReference> forDeclaration(String sourceFileKey, String identity) {
    return index.forDeclaration(sourceFileKey, identity);
  }

  @Override
  public List<CoverageFactReference> atOwner(String sourceFileKey, SourceOwnerPath owner) {
    return index.atOwner(sourceFileKey, owner);
  }

  @Override
  public List<CoverageFactReference> atPath(String sourceFileKey, SyntaxStructuralPath path) {
    return index.atPath(sourceFileKey, path);
  }

  @Override
  public List<CoverageFactReference> ownedBy(SourceContext context) {
    return index.ownedBy(context);
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

  @Override
  public List<CoverageFactReference> children(CoverageFactReference parent, List<SyntaxRole> roles) {
    var syntax = requiredSyntax(parent);
    var selected = new ArrayList<CoverageFactReference>();
    var positions = new HashSet<Step>();
    for (var child : syntax.children()) {
      if (roles.contains(child.role())) {
        if (!positions.add(new Step(child.role(), child.ordinal()))) {
          throw new IllegalArgumentException("An actual child position requires one exact attachment");
        }
        selected.add(resolveChild(parent, syntax.structuralPath(), child));
      }
    }
    return List.copyOf(selected);
  }

  @Override
  public Optional<CoverageFactReference> child(CoverageFactReference parent, SyntaxRole role, int position) {
    var syntax = requiredSyntax(parent);
    var matches = syntax.children().stream().filter(child -> child.role() == role && child.ordinal() == position).toList();
    if (matches.size() > MAX_ATTACHMENTS_PER_POSITION) {
      throw new IllegalArgumentException("An actual child position requires one exact attachment");
    }
    return matches.stream().findFirst().map(child -> resolveChild(parent, syntax.structuralPath(), child));
  }

  private CoverageFactReference resolveChild(CoverageFactReference parent, SyntaxStructuralPath path, Child child) {
    var entry = sourceValues.get(child.nodeKey());
    if (entry == null || !entry.reference().sourceFileKey().equals(parent.sourceFileKey())
        || !entry.value().structuralPath().equals(path.child(child.role(), child.ordinal()))) {
      throw new IllegalArgumentException("An actual child attachment requires its exact source occurrence");
    }
    return entry.reference();
  }

  @Override
  public Optional<String> capturedText(CoverageFactReference reference) {
    requiredSyntax(reference);
    return reference.origin().map(span -> readSpan(reference, span));
  }

  @Override
  public String capturedText(CoverageFactReference reference, SourceSpan span) {
    requiredSyntax(reference);
    return readSpan(reference, span);
  }

  private String readSpan(CoverageFactReference reference, SourceSpan span) {
    var content = capturedSources.get(reference.sourceFileKey());
    if (content == null || !span.sourceFileKey().equals(reference.sourceFileKey()) || span.endOffsetExclusive() > content.length()) {
      throw new IllegalArgumentException("An actual source span must belong to its captured source");
    }
    return content.substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive()));
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
