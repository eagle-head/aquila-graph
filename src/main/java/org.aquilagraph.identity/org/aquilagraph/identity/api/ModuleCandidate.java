package org.aquilagraph.identity.api;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.CapturedLocation.Syntax;

/**
 * Detached module lookup and its association with a configured dependency.
 *
 * @param member
 *          slash-separated relative member of a module directory, or {@code .} for the configured artifact itself
 * @param lookupName
 *          compiler lookup name
 * @param descriptorName
 *          explicit descriptor name, absent for automatic modules
 * @param nameOrigin
 *          source of the lookup name
 */
public record ModuleCandidate(String member, String lookupName, Optional<String> descriptorName, ModuleNameOrigin nameOrigin) {
  private static final String ENTRY_ROOT = ".";

  /** Validates copied module facts without inspecting any artifact. */
  public ModuleCandidate {
    Objects.requireNonNull(member, "member");
    var fileName = memberName(member, Syntax.POSIX);
    lookupName = requireName(lookupName);
    descriptorName = Objects.requireNonNull(descriptorName, "descriptorName").map(ModuleCandidate::requireName);
    Objects.requireNonNull(nameOrigin, "nameOrigin");
    if (descriptorName.isPresent() != (nameOrigin == ModuleNameOrigin.DESCRIPTOR || nameOrigin == ModuleNameOrigin.DIRECTORY)) {
      throw new IllegalArgumentException("Explicit and automatic module name origins must agree with descriptor presence");
    }
    if (nameOrigin == ModuleNameOrigin.DESCRIPTOR && !descriptorName.orElseThrow().equals(lookupName)) {
      throw new IllegalArgumentException("Descriptor lookup must retain the descriptor name");
    }
    if (nameOrigin == ModuleNameOrigin.DIRECTORY && ENTRY_ROOT.equals(member)) {
      throw new IllegalArgumentException("Directory-derived lookup requires a child module");
    }
    if (nameOrigin == ModuleNameOrigin.DIRECTORY && !lookupName.equals(fileName)) {
      throw new IllegalArgumentException("Directory-derived lookup must match its child directory name");
    }
  }

  void requireMemberSyntax(Syntax syntax) {
    if (syntax != Syntax.POSIX) {
      memberName(member, syntax);
    }
  }

  private static String memberName(String member, Syntax syntax) {
    if (ENTRY_ROOT.equals(member)) {
      return member;
    }
    var location = CapturedLocation.parse(syntax, member);
    if (member.isBlank() || location.isAbsolute() || !member.equals(location.canonicalRelativeName())) {
      throw new IllegalArgumentException("Module member must be a canonical relative artifact locator");
    }
    return location.components().getLast();
  }

  private static String requireName(String name) {
    if (Objects.requireNonNull(name, "module name").isBlank()) {
      throw new IllegalArgumentException("Module name cannot be blank");
    }
    return name;
  }
}
