package org.aquilagraph.frontend.internal;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceContext.Partition;

/** Invokes actual represented accessors before support reconciliation, independently of expectation inventories. */
final class JavaDeclarationPropertyEncounterProducer {
  private final List<JavaCoverageTopology.Owner> actualOwners;
  private final JavaSourceEvidence sourceEvidence;
  private final LanguageFeatureCoverage coverage;
  private final JavaTranslationPolicy policy;

  JavaDeclarationPropertyEncounterProducer(List<CompilationUnitTree> units, SnapshotSourceCatalog sources, JavaSourceEvidence evidence,
      LanguageFeatureCoverage coverage, JavaTranslationPolicy policy) {
    actualOwners = JavaCoverageTopology.owners(units, sources, evidence).stream()
        .filter(owner -> !policy.supportsEnumInitialization() || !JavaEnumConstantSyntax.isImplicitEnvelope(owner.treePath(), evidence))
        .toList();
    sourceEvidence = evidence;
    this.coverage = coverage;
    this.policy = policy;
  }

  ObservedPropertyEncounters observeWritten() {
    var encounters = new ArrayList<ObservedPropertyEncounters.Encounter>();
    for (var owner : actualOwners) {
      JavaCoverageTopology.surface(owner.tree())
          .ifPresent(surface -> observe(owner, owner.tree(), surface, Partition.WRITTEN_PROPERTY, Optional.empty(), encounters));
    }
    return new ObservedPropertyEncounters(encounters);
  }

  ObservedPropertyEncounters observeAttributed(SemanticResolver semantics) {
    var encounters = new ArrayList<ObservedPropertyEncounters.Encounter>();
    var unavailable = new CoveragePropertyValue(CoveragePropertyValue.State.UNAVAILABLE, List.of());
    for (var owner : actualOwners) {
      if (!(owner.tree() instanceof ClassTree || owner.tree() instanceof MethodTree)) {
        continue;
      }
      var element = semantics.element(owner.treePath());
      if (element instanceof TypeElement) {
        observe(owner, element, TypeElement.class, Partition.ATTRIBUTED_PROPERTY, Optional.of(semantics), encounters);
      } else if (element instanceof ExecutableElement) {
        observe(owner, element, ExecutableElement.class, Partition.ATTRIBUTED_PROPERTY, Optional.of(semantics), encounters);
      } else if (owner.tree() instanceof ClassTree || owner.tree() instanceof MethodTree) {
        var expectedSurface = owner.tree() instanceof ClassTree ? TypeElement.class : ExecutableElement.class;
        for (var accessor : JavaDeclarationPropertyCoverage.accessors(expectedSurface, coverage)) {
          encounters.add(encounter(owner, accessor, Partition.ATTRIBUTED_PROPERTY, unavailable));
        }
      }
    }
    return new ObservedPropertyEncounters(encounters);
  }

  private void observe(JavaCoverageTopology.Owner sourceOwner, Object actualOwner, Class<?> surface, Partition partition,
      Optional<SemanticResolver> semantics, List<ObservedPropertyEncounters.Encounter> encounters) {
    for (var accessor : JavaDeclarationPropertyCoverage.accessors(surface, coverage)) {
      var value = JavaCoveragePropertyProjection.query(accessor, actualOwner, sourceOwner, sourceEvidence, semantics, policy);
      encounters.add(encounter(sourceOwner, accessor, partition, value));
    }
  }

  private static ObservedPropertyEncounters.Encounter encounter(JavaCoverageTopology.Owner owner, JavaDeclarationProperty accessor,
      Partition partition, CoveragePropertyValue value) {
    var address = new SourceContext.Address(owner.source().sourceFileKey(), partition, owner.path(),
        accessor.classification().feature().identifier(), 0);
    var occurrence = new LanguageFeatureOccurrence(owner.source().sourceFileKey(), accessor.classification(),
        owner.origin().stream().toList());
    var observation = new LanguageFeatureObservation(owner.source().sourceFileKey(), accessor.classification(), occurrence.origins(),
        Optional.empty());
    return new ObservedPropertyEncounters.Encounter(address, occurrence, observation, value);
  }
}
