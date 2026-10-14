package com.professor.assistant.dto;

import com.professor.assistant.domain.ContentAudience;
import com.professor.assistant.domain.ContentReviewStatus;
import com.professor.assistant.domain.GenerationContentType;
import com.professor.assistant.domain.GenerationLanguage;
import java.time.Instant;

/**
 * A generated artifact as the owner sees it.
 *
 * <p>The student-facing projection is produced by a different DTO
 * ({@code StudentContentDto}) that omits provider metadata and can never
 * carry teacher-only material.
 *
 * @param stale true when the source syllabus has been superseded
 * @param outdated true when the stored review status is overridden by staleness
 */
public record GeneratedContentDto(
    Long id,
    Long jobId,
    Long syllabusId,
    Integer syllabusVersion,
    String subjectCode,
    String subjectName,
    String classCode,
    Long unitId,
    String unitTitle,
    GenerationContentType contentType,
    GenerationLanguage language,
    String title,
    String body,
    String structuredJson,
    ContentAudience audience,
    boolean containsAnswerKey,
    ContentReviewStatus reviewStatus,
    boolean releasedToStudents,
    String rejectionReason,
    String providerName,
    String modelName,
    boolean stale,
    Instant createdAt,
    Instant updatedAt,
    Instant reviewedAt) {

  /** True when the teacher may still edit this. */
  public boolean editable() {
    return !stale && reviewStatus.isEditable();
  }

  /** Whether the teacher is allowed to release it at all. */
  public boolean releasable() {
    return audience == ContentAudience.STUDENT;
  }
}