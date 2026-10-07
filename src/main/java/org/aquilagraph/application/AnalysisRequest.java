package org.aquilagraph.application;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput;

/**
 * Source production, optional unchanged graph selection, and independent storage intent.
 *
 * @param input
 *          source root or exact already captured input
 * @param configuration
 *          compiler configuration
 * @param astProfile
 *          source representation profile
 * @param cfgSelection
 *          optional unbound graph selection
 * @param publicationIntent
 *          independent storage intent
 */
public record AnalysisRequest(SourceInput input, CompilerConfiguration configuration, AstProfile astProfile,
    Optional<CfgSelection> cfgSelection, PublicationIntent publicationIntent) {
  /** Requires explicit source, selection presence, and publication intent. */
  public AnalysisRequest {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(astProfile, "astProfile");
    Objects.requireNonNull(cfgSelection, "cfgSelection");
    Objects.requireNonNull(publicationIntent, "publicationIntent");
  }
}
