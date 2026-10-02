package org.aquilagraph.syntax;

import java.util.Objects;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeReference;

/**
 * Selected static member and declaring initialization target at one access.
 *
 * @param methodKey
 *          canonical owning method key
 * @param accessNodeId
 *          access syntax identity
 * @param selectedMemberKey
 *          canonical selected member key
 * @param kind
 *          selected access kind
 * @param declaringType
 *          compiler-selected declaring class or interface
 * @param constantVariableRead
 *          attributed constant-variable read classification
 */
public record StaticAccessDecision(String methodKey, String accessNodeId, String selectedMemberKey, StaticAccessKind kind,
    JavaTypeReference declaringType, boolean constantVariableRead) {
  /** Validates supplied shape without inferring a declaring owner from the member key. */
  public StaticAccessDecision {
    DecisionValueValidation.requireKey(methodKey, "methodKey");
    DecisionValueValidation.requireKey(accessNodeId, "accessNodeId");
    DecisionValueValidation.requireKey(selectedMemberKey, "selectedMemberKey");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(declaringType, "declaringType");
    if (declaringType.identity().category() != JavaTypeCategory.DECLARED) {
      throw new IllegalArgumentException("A static access declaring type must identify a class or interface");
    }
    if (constantVariableRead && kind != StaticAccessKind.FIELD_READ) {
      throw new IllegalArgumentException("Only a static field read may be a constant-variable read");
    }
  }

  /**
   * Indicates whether this selected access has a possible initialization stage.
   *
   * @return false exactly for a constant-variable field read
   */
  public boolean triggersInitialization() {
    return !constantVariableRead;
  }
}
