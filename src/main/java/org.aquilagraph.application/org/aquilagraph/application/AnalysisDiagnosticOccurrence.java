package org.aquilagraph.application;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AstOutcome;

/** An exact native diagnostic slot admitted into one application diagnostic sequence. */
public final class AnalysisDiagnosticOccurrence {
  private final Owner nativeOwner;
  private final int nativePosition;

  AnalysisDiagnosticOccurrence(Owner owner, int localPosition) {
    nativeOwner = owner;
    nativePosition = localPosition;
  }

  /**
   * Returns the typed native owner, independently of diagnostic value equality.
   *
   * @return native context or application diagnostic list
   */
  public Owner owner() {
    return nativeOwner;
  }

  /**
   * Returns the zero-based slot in the native owner's list.
   *
   * @return native diagnostic position
   */
  public int localPosition() {
    return nativePosition;
  }

  /**
   * Reads the diagnostic from its native owner without creating another value.
   *
   * @return diagnostic at the exact owning slot
   */
  public AnalysisDiagnostic diagnostic() {
    var values = switch (nativeOwner) {
      case Owner.Source source -> source.outcome().diagnostics();
      case Owner.Request request -> request.outcome().diagnostics();
      case Owner.Method method -> method.outcome().diagnostics();
      case Owner.Application application -> application.diagnostics();
    };
    return values.get(nativePosition);
  }

  /** Native ownership within the enclosing application result, never a global event identity. */
  public sealed interface Owner {
    /**
     * Source-owned diagnostic slots, including unavailable production.
     *
     * @param outcome
     *          native source outcome
     */
    record Source(AstOutcome outcome) implements Owner {
      /** Requires the native owner. */
      public Source {
        Objects.requireNonNull(outcome, "outcome");
      }
    }

    /**
     * Diagnostics of a rejected CFG request.
     *
     * @param outcome
     *          rejected request outcome
     */
    record Request(CfgOutcome outcome) implements Owner {
      /** Requires the native owner. */
      public Request {
        Objects.requireNonNull(outcome, "outcome");
        if (outcome.state() != CfgState.REJECTED) {
          throw new IllegalArgumentException("A request diagnostic owner requires a rejected CFG outcome");
        }
      }
    }

    /**
     * Diagnostics owned by one selected canonical method result.
     *
     * @param outcome
     *          native method outcome
     */
    record Method(MethodCfgOutcome outcome) implements Owner {
      /** Requires the native owner. */
      public Method {
        Objects.requireNonNull(outcome, "outcome");
      }
    }

    /**
     * Application-created diagnostic slots.
     *
     * @param diagnostics
     *          immutable native application diagnostics
     */
    record Application(List<AnalysisDiagnostic> diagnostics) implements Owner {
      /** Freezes the native slots without merging equal values. */
      public Application {
        diagnostics = List.copyOf(diagnostics);
      }
    }
  }
}
