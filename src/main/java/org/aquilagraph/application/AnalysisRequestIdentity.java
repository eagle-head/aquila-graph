package org.aquilagraph.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.api.AstOutcome;

/**
 * Full request provenance, retaining available or unavailable source and selection variants.
 *
 * @param operation
 *          semantic operation
 * @param source
 *          exact source subject descriptor
 * @param selection
 *          unchanged graph request descriptor
 */
public record AnalysisRequestIdentity(AnalysisOperation operation, SourceSubject source, Selection selection) {
  /** Source identity exists only for an established admitted result. */
  public sealed interface SourceSubject permits AvailableSource, UnavailableSource {
  }

  /**
   * Exact available source subject.
   *
   * @param identity
   *          established admitted source identity
   */
  public record AvailableSource(AstIdentity identity) implements SourceSubject {
    /** Requires an actual qualified source identity. */
    public AvailableSource {
      Objects.requireNonNull(identity, "identity");
    }
  }

  /**
   * Unavailable source subject without a fabricated AST identity.
   *
   * @param profileVersion
   *          declared source profile
   * @param capturedValuesDigest
   *          canonical full retained unavailable facts
   */
  public record UnavailableSource(String profileVersion, String capturedValuesDigest) implements SourceSubject {
    /** Requires both represented qualification and full captured subject digest. */
    public UnavailableSource {
      requireText(profileVersion, "profileVersion");
      requireText(capturedValuesDigest, "capturedValuesDigest");
    }
  }

  /** Explicit selection provenance before or after source production. */
  public sealed interface Selection permits NotRequested, ProductionSelection, ExistingSelection {
  }

  /** Absence of a graph request differs from an empty requested selection. */
  public enum NotRequested implements Selection {
    /** No control-flow derivation was requested. */
    INSTANCE
  }

  /**
   * Unchanged production selection with explicit bound or unbound source identity.
   *
   * @param boundIdentity
   *          exact produced source identity or explicit absence
   * @param selection
   *          unchanged graph selection
   * @param methodKeys
   *          exact explicit keys or resolved canonical body keys
   */
  public record ProductionSelection(Optional<AstIdentity> boundIdentity, CfgSelection selection,
      List<String> methodKeys) implements Selection {
    /**
     * Retains an explicit method selection without changing its entries.
     *
     * @param boundIdentity
     *          produced source identity
     * @param selection
     *          unchanged caller selection
     */
    public ProductionSelection(Optional<AstIdentity> boundIdentity, CfgSelection selection) {
      this(boundIdentity, selection, selection.methodKeys());
    }

    /** Requires explicit identity presence and unchanged selection. */
    public ProductionSelection {
      Objects.requireNonNull(boundIdentity, "boundIdentity");
      Objects.requireNonNull(selection, "selection");
      methodKeys = List.copyOf(Objects.requireNonNull(methodKeys, "methodKeys"));
      if ((selection.scope() == CfgSelection.Scope.METHODS && !methodKeys.equals(selection.methodKeys()))
          || (selection.scope() == CfgSelection.Scope.ALL_BODIES && boundIdentity.isEmpty() && !methodKeys.isEmpty())) {
        throw new IllegalArgumentException("Production must retain exact explicit keys or source-bound all-body keys");
      }
    }
  }

  /**
   * Caller-bound existing-source request, including rejected selection or identity.
   *
   * @param request
   *          exact caller-supplied CFG request
   */
  public record ExistingSelection(CfgRequest request) implements Selection {
    /** Preserves the exact caller request. */
    public ExistingSelection {
      Objects.requireNonNull(request, "request");
    }
  }

  /** Enforces operation and descriptor variants without normalizing invalid selections. */
  public AnalysisRequestIdentity {
    Objects.requireNonNull(operation, "operation");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(selection, "selection");
    var matching = switch (operation) {
      case AST_ONLY -> selection instanceof NotRequested;
      case PRODUCTION_WITH_CFG -> selection instanceof ProductionSelection;
      case DERIVE_EXISTING -> selection instanceof ExistingSelection;
    };
    if (!matching) {
      throw new IllegalArgumentException("Analysis operation and selection descriptor disagree");
    }
    if (selection instanceof ProductionSelection production) {
      var expected = source instanceof AvailableSource available ? Optional.of(available.identity()) : Optional.<AstIdentity>empty();
      if (!expected.equals(production.boundIdentity())) {
        throw new IllegalArgumentException("Production binding must equal the established source subject");
      }
    }
  }

  /**
   * Derives content identity from operation, represented source facts, and exact selection.
   *
   * @return canonical application request key
   */
  public String key() {
    var subject = new CanonicalValueWriter("analysis-source-subject/1");
    switch (source) {
      case AvailableSource available -> {
        subject.text("AST");
        subject.text(available.identity().astResultKey());
      }
      case UnavailableSource unavailable -> {
        subject.text("UNAVAILABLE");
        subject.text(unavailable.profileVersion());
        subject.text(unavailable.capturedValuesDigest());
      }
    }
    var descriptor = new CanonicalValueWriter("analysis-selection/2");
    switch (selection) {
      case NotRequested _ -> descriptor.text("NOT_REQUESTED");
      case ProductionSelection production -> {
        descriptor.text("PRODUCTION_SELECTION");
        descriptor.text(production.boundIdentity().isPresent() ? "BOUND" : "UNBOUND");
        production.boundIdentity().ifPresent(identity -> identity(descriptor, identity));
        descriptor.text(production.selection().scope().name());
        selection(descriptor, production.selection().methodKeys(), production.selection().profile().version(),
            production.selection().profile().graphModelVersion());
        descriptor.count(production.methodKeys().size());
        production.methodKeys().forEach(descriptor::text);
      }
      case ExistingSelection existing -> {
        descriptor.text("EXISTING_SELECTION");
        identity(descriptor, existing.request().astIdentity());
        selection(descriptor, existing.request().methodKeys(), existing.request().profile().version(),
            existing.request().profile().graphModelVersion());
      }
    }
    return StableId.sha256("analysis-request", operation.name(), subject.digest("source-subject"), descriptor.digest("selection"));
  }

  /**
   * Describes the source already established by production or supplied by the caller.
   *
   * @param outcome
   *          established source outcome
   * @return available identity or complete unavailable subject provenance
   */
  public static SourceSubject sourceSubject(AstOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    return outcome.result().<SourceSubject>map(result -> new AvailableSource(result.identity()))
        .orElseGet(() -> new UnavailableSource(outcome.profile().version(), outcome.sourceSubjectDigest()));
  }

  private static void identity(CanonicalValueWriter writer, AstIdentity identity) {
    writer.text(identity.astResultKey());
    writer.text(identity.softwareSignature());
    writer.text(identity.sourceModelVersion());
    writer.text(identity.astProfileVersion());
    writer.text(identity.semanticProductionRevision());
    writer.text(identity.canonicalContentDigest());
  }

  private static void selection(CanonicalValueWriter writer, List<String> methods, String profile, String format) {
    writer.count(methods.size());
    methods.forEach(writer::text);
    writer.text(profile);
    writer.text(format);
  }

  private static void requireText(String value, String name) {
    if (Objects.requireNonNull(value, name).isBlank()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
  }
}
