package org.aquilagraph.cfg.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.CanonicalValueWriter;

/**
 * Exact source identity and ordered declaration selection for one derivation.
 *
 * @param astIdentity
 *          exact requested source result
 * @param methodKeys
 *          ordered selection, including any invalid or duplicate entries
 * @param profile
 *          requested control-flow interpretation
 */
public record CfgRequest(AstIdentity astIdentity, List<String> methodKeys, CfgProfile profile) {
  /** Copies selection without discarding invalid entries that rejection must report. */
  public CfgRequest {
    Objects.requireNonNull(astIdentity, "astIdentity");
    methodKeys = List.copyOf(Objects.requireNonNull(methodKeys, "methodKeys"));
    Objects.requireNonNull(profile, "profile");
  }

  boolean hasUniqueMethodSelection() {
    return new HashSet<>(methodKeys).size() == methodKeys.size();
  }

  /**
   * Returns content identity of the exact request independently of results and graphs.
   *
   * @return framed request identity
   */
  public String identityKey() {
    var writer = new CanonicalValueWriter("cfg-request/1");
    writer.text(astIdentity.astResultKey());
    writer.text(astIdentity.softwareSignature());
    writer.text(astIdentity.sourceModelVersion());
    writer.text(astIdentity.astProfileVersion());
    writer.text(astIdentity.semanticProductionRevision());
    writer.text(astIdentity.canonicalContentDigest());
    writer.count(methodKeys.size());
    methodKeys.forEach(writer::text);
    writer.text(profile.version());
    writer.text(profile.graphModelVersion());
    return writer.digest("cfg-request");
  }
}
