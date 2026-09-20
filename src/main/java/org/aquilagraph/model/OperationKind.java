package org.aquilagraph.model;

public enum OperationKind {
  READ,
  WRITE,
  CONSTANT,
  UNARY,
  BINARY,
  CAST,
  ARRAY_ACCESS,
  ARRAY_LENGTH,
  MEMBER_ACCESS,
  INVOKE,
  NEW_OBJECT,
  DECISION,
  SWITCH_SELECTOR,
  MERGE,
  CATCH,
  RETURN,
  THROW,
  NO_OP
}
