package com.professor.assistant.dto;

import com.professor.assistant.domain.GenerationContentType;
import com.professor.assistant.domain.GenerationDifficulty;
import com.professor.assistant.domain.GenerationDetailLevel;
import com.professor.assistant.domain.GenerationJobStatus;
import com.professor.assistant.domain.GenerationLanguage;
import java.time.Instant;

/**
 * Status of a generation job.
 *
 * <p>Never includes the provider's raw response or the prompt, only safe
 * error codes and messages.
 *
 * @param syllabusVersion the version this job was grounded in
 * @param stale true when the syllabus has since been superseded
 * @param attemptCount provider attempts actually made, for transparency
 */
public record GenerationJobDto(
    Long id,
    Long syllabusId,
    Integer syllabusVersion,
    Long unitId,
    String unitTitle,
    GenerationContentType contentType,
    GenerationLanguage language,
    GenerationDetailLevel detailLevel,
    GenerationDifficulty difficulty,
    Integer itemCount,
    String instructions,
    GenerationJobStatus status,
    Integer attemptCount,
    String errorCode,
    String errorMessage,
    String providerName,
    String modelName,
    Long contentId,
    boolean stale,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt,
    String note) {}