package org.aquilagraph.cfg.api;

/** Control transfer represented by a control-flow graph edge. */
public enum TransferKind {
  /** Sequential control transfer. */
  NORMAL,
  /** Transfer taken when a condition is true. */
  TRUE_BRANCH,
  /** Transfer taken when a condition is false. */
  FALSE_BRANCH,
  /** Transfer to one switch alternative. */
  SWITCH_ALTERNATIVE,
  /** Transfer from a return operation to method exit. */
  RETURN,
  /** Transfer caused by a possible exception. */
  EXCEPTIONAL
}
