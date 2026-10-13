package com.professor.assistant.domain;

/** Lifecycle state of an account. Suspended accounts cannot sign in. */
public enum AccountStatus {
  ACTIVE,
  SUSPENDED
}
