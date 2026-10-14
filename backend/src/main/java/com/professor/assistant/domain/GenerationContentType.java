package com.professor.assistant.domain;

/** Kinds of material that can be generated from an approved syllabus. */
public enum GenerationContentType {
  /** Student-facing, unit-wise detailed notes. */
  UNIT_NOTES,
  /** Teacher-only study and teaching material. Never releasable to students. */
  TEACHER_STUDY_MATERIAL,
  /** Slide-by-slide content with speaker notes. No PPTX is produced. */
  PPT_SLIDES,
  /** Questions with difficulty and optional teacher answer key. */
  ASSIGNMENTS_QUESTIONS,
  /** Multiple-choice questions. Validated before storage. */
  MCQS,
  /** Student-facing condensed revision material. */
  QUICK_REVISION;

  /** Whether {@code itemCount} (questions/slides) is meaningful. */
  public boolean isCountable() {
    return this == PPT_SLIDES || this == ASSIGNMENTS_QUESTIONS || this == MCQS;
  }

  /**
   * Audience this material is written for. Teacher study material is
   * teacher-only and can never be released to students.
   */
  public ContentAudience audience() {
    return this == TEACHER_STUDY_MATERIAL
        ? ContentAudience.TEACHER
        : ContentAudience.STUDENT;
  }

  /** True when the body is expected to include an answer key / marking guidance. */
  public boolean mayContainAnswerKey() {
    return this == ASSIGNMENTS_QUESTIONS;
  }
}