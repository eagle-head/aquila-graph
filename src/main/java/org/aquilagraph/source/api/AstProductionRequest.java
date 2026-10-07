package org.aquilagraph.source.api;

import java.nio.file.Path;
import java.util.Objects;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;

/**
 * Explicit source capture and production request.
 *
 * @param input
 *          source directory or existing immutable capture
 * @param configuration
 *          compiler and dependency configuration
 * @param profile
 *          exact requested source profile
 */
public record AstProductionRequest(SourceInput input, CompilerConfiguration configuration, AstProfile profile) {
  /** Requires the explicit input variant and its production policies. */
  public AstProductionRequest {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(profile, "profile");
  }

  /**
   * Requests capture beneath a source directory.
   *
   * @param sourceRoot
   *          source directory
   * @param configuration
   *          compiler configuration
   * @param profile
   *          source representation profile
   */
  public AstProductionRequest(Path sourceRoot, CompilerConfiguration configuration, AstProfile profile) {
    this(new SourceInput.Root(sourceRoot), configuration, profile);
  }

  /**
   * Requests production from an existing capture.
   *
   * @param snapshot
   *          captured source values
   * @param configuration
   *          compiler configuration
   * @param profile
   *          source representation profile
   */
  public AstProductionRequest(InputSnapshot snapshot, CompilerConfiguration configuration, AstProfile profile) {
    this(new SourceInput.Snapshot(snapshot), configuration, profile);
  }
}
