package org.aquilagraph.identity.api;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Detached module lookup and its association with a configured dependency.
 *
 * @param member
 *          relative member of a module directory, or {@code .} for the configured artifact itself
 * @param lookupName
 *          compiler lookup name
 * @param descriptorName
 *          explicit descriptor name, absent for automatic modules
 * @param nameOrigin
 *          source of the lookup name
 */
public record ModuleCandidate(String member, String lookupName, Optional<String> descriptorName, ModuleNameOrigin nameOrigin) {
  /** Validates copied module facts without inspecting any artifact. */
  public ModuleCandidate {
    Objects.requireNonNull(member, "member");
    var path = Path.of(member);
    var fileName = path.getFileName();
    if (fileName == null || member.isBlank() || path.isAbsolute() || path.getRoot() != null || path.startsWith("..")
        || (!".".equals(member) && !path.normalize().equals(path)) || !path.toString().replace('\\', '/').equals(member)) {
      throw new IllegalArgumentException("Module member must be a canonical relative artifact locator");
    }
    lookupName = requireName(lookupName);
    descriptorName = Objects.requireNonNull(descriptorName, "descriptorName").map(ModuleCandidate::requireName);
    Objects.requireNonNull(nameOrigin, "nameOrigin");
    if (descriptorName.isPresent() != (nameOrigin == ModuleNameOrigin.DESCRIPTOR || nameOrigin == ModuleNameOrigin.DIRECTORY)) {
      throw new IllegalArgumentException("Explicit and automatic module name origins must agree with descriptor presence");
    }
    if (nameOrigin == ModuleNameOrigin.DESCRIPTOR && !descriptorName.orElseThrow().equals(lookupName)) {
      throw new IllegalArgumentException("Descriptor lookup must retain the descriptor name");
    }
    if (nameOrigin == ModuleNameOrigin.DIRECTORY && ".".equals(member)) {
      throw new IllegalArgumentException("Directory-derived lookup requires a child module");
    }
    if (nameOrigin == ModuleNameOrigin.DIRECTORY && !lookupName.equals(fileName.toString())) {
      throw new IllegalArgumentException("Directory-derived lookup must match its child directory name");
    }
  }

  private static String requireName(String name) {
    if (Objects.requireNonNull(name, "module name").isBlank()) {
      throw new IllegalArgumentException("Module name cannot be blank");
    }
    return name;
  }
}
