package org.aquilagraph.cli;

import java.util.Objects;

/** Structured argument refusal retaining unchanged caller values. */
public sealed interface ArgumentRejection {
  /**
   * An option was repeated.
   *
   * @param option
   *          original option
   */
  record DuplicateOption(String option) implements ArgumentRejection {
    /** Requires the original option. */
    public DuplicateOption {
      Objects.requireNonNull(option, "option");
    }
  }

  /** Missing release operand. */
  enum MissingRelease implements ArgumentRejection {
    /** The next operand is absent or another option. */
    INSTANCE
  }

  /**
   * An operand was not an integer feature release.
   *
   * @param value
   *          unchanged operand
   */
  record MalformedRelease(String value) implements ArgumentRejection {
    /** Requires the unchanged operand. */
    public MalformedRelease {
      Objects.requireNonNull(value, "value");
    }
  }

  /**
   * A parsed feature is unsupported.
   *
   * @param value
   *          unchanged operand
   * @param feature
   *          parsed feature
   */
  record UnsupportedRelease(String value, int feature) implements ArgumentRejection {
    /** Requires the unchanged operand. */
    public UnsupportedRelease {
      Objects.requireNonNull(value, "value");
    }
  }

  /**
   * An unknown option was supplied.
   *
   * @param option
   *          unchanged token
   */
  record UnknownOption(String option) implements ArgumentRejection {
    /** Requires the original token. */
    public UnknownOption {
      Objects.requireNonNull(option, "option");
    }
  }

  /**
   * The positional argument count differs from the required single root.
   *
   * @param count
   *          positional argument count
   */
  record RootCount(int count) implements ArgumentRejection {
  }

  /**
   * The platform rejected the root path.
   *
   * @param value
   *          unchanged root
   */
  record InvalidRoot(String value) implements ArgumentRejection {
    /** Requires the original root. */
    public InvalidRoot {
      Objects.requireNonNull(value, "value");
    }
  }
}
