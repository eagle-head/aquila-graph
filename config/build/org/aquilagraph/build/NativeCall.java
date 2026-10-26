package org.aquilagraph.build;

import java.util.Optional;

/** Public functional boundary required by the JDK's typed method-handle adapter. */
@FunctionalInterface
public interface NativeCall {
  /**
   * Invokes one declared native signature with its captured-call-state argument.
   *
   * @param arguments
   *          native argument values, including the call-state segment
   * @return the native result, or empty for a native void function
   */
  Optional<Object> invoke(Object... arguments);
}
