package org.aquilagraph.source.internal;

/** Execution registration binds its immutable occurrence facts and decision inputs as one contribution. */
record ExecutionRegistration(SourceExecutionFacts facts, ExecutionDecisionRegistration decisions) {
}
