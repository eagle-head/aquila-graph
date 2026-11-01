package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * A source-owned blocking cause, separate from producer diagnostic events.
 *
 * @param category
 *          semantic cause category
 * @param role
 *          required evidence or policy affected
 * @param subject
 *          exact input-local subject, without an invented result identity
 * @param origins
 *          available source locations
 * @param diagnostics
 *          established event associations only
 * @param evidence
 *          retained diagnostic values without an established event association
 */
public record SourceQualificationReason(Category category, EvidenceRole role, Subject subject, List<SourceSpan> origins,
    List<DiagnosticReference> diagnostics, List<DiagnosticEvidence> evidence) {
  /** Copies explanations without deduplicating occurrences. */
  public SourceQualificationReason {
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(subject, "subject");
    origins = List.copyOf(origins);
    diagnostics = List.copyOf(diagnostics);
    evidence = List.copyOf(evidence);
  }

  /** Stable semantic categories, independent of human diagnostic wording. */
  public enum Category {
    /** No captured source files. */
    EMPTY_SOURCE,
    /** No admitted roots were established. */
    UNAVAILABLE_ROOTS,
    /** The selected historical profile excludes this source form. */
    PROFILE_RESTRICTION,
    /** A native source error was emitted. */
    COMPILER_ERROR,
    /** A native noncompiler source error was emitted. */
    SOURCE_ERROR,
    /** Required copied evidence is absent. */
    MISSING_REQUIRED_EVIDENCE,
    /** Copied evidence explicitly reports an error. */
    ERRONEOUS_EVIDENCE,
    /** Required source structure or coverage is incomplete. */
    STRUCTURAL_COVERAGE_GAP
  }

  /** The independently assessed requirement at the subject. */
  public enum EvidenceRole {
    /** Captured files. */
    SOURCE_CAPTURE,
    /** Admitted source roots. */
    SOURCE_ROOTS,
    /** Historical declaration envelope. */
    DECLARATION_ENVELOPE,
    /** Module directive operands. */
    MODULE_SERVICE_SLOTS,
    /** Available module operand location. */
    MODULE_SERVICE_ORIGIN,
    /** Resolved module operand. */
    MODULE_SERVICE_TARGET,
    /** Independent module operand coverage. */
    MODULE_ENDPOINT_CONTEXT,
    /** Independent written expression inventory. */
    WRITTEN_EXPRESSIONS,
    /** Declaration type identity. */
    DECLARED_TYPE,
    /** Copied lexical owner. */
    LEXICAL_OWNER,
    /** Attributed member order. */
    MEMBER_ORDER,
    /** Native source diagnostic event. */
    DIAGNOSTIC,
    /** Independent source context support. */
    COVERAGE_SUPPORT,
    /** Unmatched actual source fact. */
    UNMATCHED_FACT,
    /** Required source form. */
    SOURCE_FORM,
    /** Required compiler type. */
    TYPE,
    /** Required selected symbol. */
    SEMANTIC_REFERENCE,
    /** Required invocation semantics. */
    INVOCATION_SEMANTICS,
    /** Profile-specific source form. */
    PROFILE_FORM,
    /** Profile-specific local name. */
    LOCAL_NAME,
    /** Profile-specific local modifiers. */
    LOCAL_MODIFIERS,
    /** Profile-specific local annotations. */
    LOCAL_ANNOTATIONS,
    /** Profile-specific local type syntax. */
    LOCAL_TYPE_FORM,
    /** Profile-specific shared declaration annotations. */
    LOCAL_SHARED_ANNOTATIONS,
    /** Required copied local binding. */
    LOCAL_BINDING,
    /** Required local declaration identity. */
    LOCAL_DECLARATION,
    /** Required declaration execution scope. */
    DECLARATION_SCOPE,
    /** Required selected enum constructor. */
    ENUM_CONSTRUCTOR,
    /** Written-expression constant classification. */
    CONSTANT_EXPRESSION,
    /** Required attributed execution root. */
    EXECUTION_ROOT,
    /** Required pattern placement. */
    PATTERN_PLACEMENT,
    /** Required switch structure. */
    SWITCH_STRUCTURE,
    /** Required static declaring owner. */
    STATIC_OWNER,
    /** Selected argument target. */
    ARGUMENT_TARGET,
    /** Field initializer use. */
    INITIALIZER_USE,
    /** Available method body provenance. */
    METHOD_BODY
  }

  /** Typed input-local subject. */
  public sealed interface Subject permits Capture, Node, Declaration, Context, Fact {
  }

  /** Source capture without a fabricated source position or result identity. */
  public enum Capture implements Subject {
    /** The captured input as a whole. */
    INSTANCE
  }

  /**
   * Canonical source occurrence or retained auxiliary execution node.
   *
   * @param nodeKey
   *          source node key
   * @param sourceFileKey
   *          captured file key
   */
  public record Node(String nodeKey, String sourceFileKey) implements Subject {
    /** Requires both source identities. */
    public Node {
      Objects.requireNonNull(nodeKey, "nodeKey");
      Objects.requireNonNull(sourceFileKey, "sourceFileKey");
    }
  }

  /**
   * Canonical declaration.
   *
   * @param key
   *          declaration key
   */
  public record Declaration(String key) implements Subject {
    /** Requires the declaration identity. */
    public Declaration {
      Objects.requireNonNull(key, "key");
    }
  }

  /**
   * Independently captured coverage context.
   *
   * @param address
   *          captured context address
   */
  public record Context(SourceContext.Address address) implements Subject {
    /** Requires the original context address. */
    public Context {
      Objects.requireNonNull(address, "address");
    }
  }

  /**
   * Actual copied fact with no matched obligation.
   *
   * @param reference
   *          exact retained fact
   */
  public record Fact(CoverageFactReference reference) implements Subject {
    /** Requires the retained fact identity. */
    public Fact {
      Objects.requireNonNull(reference, "reference");
    }
  }

  /**
   * Exact diagnostic list slot; equal values and repeated keys retain distinct positions.
   *
   * @param owner
   *          admitted diagnostic list
   * @param position
   *          zero-based owning list position
   * @param emission
   *          creating-site address when established
   */
  public record DiagnosticReference(List<AnalysisDiagnostic> owner, int position, Optional<DiagnosticEmission.Address> emission) {
    /** Validates the slot without looking it up by value or key. */
    public DiagnosticReference {
      owner = List.copyOf(owner);
      Objects.requireNonNull(emission, "emission");
      if (position < 0 || position >= owner.size()) {
        throw new IllegalArgumentException("A diagnostic reference requires an existing owning slot");
      }
    }

    /**
     * Returns the native value at this exact occurrence.
     *
     * @return native diagnostic, including its nonunique key
     */
    public AnalysisDiagnostic diagnostic() {
      return owner.get(position);
    }
  }

  /**
   * Retained evidence without an established diagnostic event association.
   *
   * @param role
   *          evidence requirement
   * @param diagnostic
   *          original evidence value
   */
  public record DiagnosticEvidence(EvidenceRole role, AnalysisDiagnostic diagnostic) {
    /** Preserves the evidence without inventing a list position. */
    public DiagnosticEvidence {
      Objects.requireNonNull(role, "role");
      Objects.requireNonNull(diagnostic, "diagnostic");
    }
  }
}
