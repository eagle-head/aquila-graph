package org.aquilagraph.identity.api;

/** Source of the compiler's module lookup name. */
public enum ModuleNameOrigin {
  /** Name read from the explicit module descriptor. */
  DESCRIPTOR,
  /** Automatic module name read from the manifest. */
  MANIFEST,
  /** Automatic module name derived from an archive filename. */
  FILENAME,
  /** Exploded child module looked up by its containing directory name. */
  DIRECTORY
}
