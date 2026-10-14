package com.professor.assistant.domain;

/**
 * Review state of a generated artifact.
 *
 * <p>{@link #OUTDATED} is derived rather than stored as the authoritative
 * value: an artifact is stale when the syllabus has moved on to a newer
 * version. See {@code GeneratedContent#isStale()}.
 */
public enum ContentReviewStatus {
  DRAFT,
  IN_REVIEW,
  APPROVED,
  REJECTED,
  OUTDATED;

  /** A teacher may still edit and approve content in these states. */
  public boolean isEditable() {
    return this == DRAFT || this == IN_REVIEW || this == REJECTED;
  }
}