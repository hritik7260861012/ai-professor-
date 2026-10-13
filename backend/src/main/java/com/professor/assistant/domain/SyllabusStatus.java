package com.professor.assistant.domain;

/**
 * Lifecycle of a syllabus document.
 *
 * <pre>
 * UPLOADED ──► EXTRACTING ──► REVIEW_REQUIRED ──► APPROVED
 *      │            │                 │
 *      │            │                 └──► REJECTED ──► (new version)
 *      │            ▼
 *      └────► EXTRACTION_FAILED ──► (retry-extraction)
 * </pre>
 *
 * <p>Only APPROVED rows are visible to students. REJECTED and
 * EXTRACTION_FAILED rows keep the original upload so the teacher can retry
 * without re-uploading.
 */
public enum SyllabusStatus {
  /** File stored, extraction not started yet. */
  UPLOADED,
  /** Extraction in progress. */
  EXTRACTING,
  /** Extraction produced no usable text (for example a scanned PDF). */
  EXTRACTION_FAILED,
  /** Text extracted; awaiting teacher review. */
  REVIEW_REQUIRED,
  /** Teacher approved the structure; visible to enrolled students. */
  APPROVED,
  /** Teacher rejected the structure; never visible to students. */
  REJECTED;

  /** Students may only ever read approved syllabi. */
  public boolean isVisibleToStudents() {
    return this == APPROVED;
  }

  /** The structure can still be edited while in these states. */
  public boolean isEditable() {
    return this == REVIEW_REQUIRED || this == REJECTED || this == EXTRACTION_FAILED;
  }

  public boolean isTerminalForEditing() {
    return this == APPROVED;
  }
}