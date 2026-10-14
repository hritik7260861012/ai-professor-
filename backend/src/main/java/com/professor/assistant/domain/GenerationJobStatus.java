package com.professor.assistant.domain;

/** Lifecycle of an AI generation job. */
public enum GenerationJobStatus {
  /** Accepted and persisted; waiting for an executor slot. */
  QUEUED,
  /** A provider call is in flight. */
  RUNNING,
  /** Finished successfully; {@code contentId} is set. */
  COMPLETED,
  /** Terminal failure; see the safe {@code errorCode} / {@code errorMessage}. */
  FAILED,
  /** Abandoned before completion, including stale jobs reconciled at startup. */
  CANCELLED;

  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED || this == CANCELLED;
  }
}