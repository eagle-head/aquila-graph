package org.aquilagraph.frontend.internal;

import org.aquilagraph.identity.api.DependencyKind;

/** Filesystem facts captured before the dependency is interpreted for its configured compiler role. */
record PhysicalDependency(DependencyKind kind, long byteCount, int entryCount, String fingerprint) {
}
