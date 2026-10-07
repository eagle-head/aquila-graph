package org.aquilagraph.application;

/** Independent storage decision or operation outcome. */
public enum PublicationStatus {
  /** No repository acquisition was requested. */
  NOT_REQUESTED,
  /** The requested subject is ineligible and no repository was acquired. */
  NOT_ELIGIBLE,
  /** Save and every required close returned normally after acknowledged commit. */
  SAVED,
  /** Declared repository failure prevented normal publication. */
  FAILED
}
