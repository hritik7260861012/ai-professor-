package com.professor.assistant.dto;

import com.professor.assistant.domain.GenerationContentType;
import com.professor.assistant.domain.GenerationLanguage;

/**
 * The deliberately narrow projection students receive.
 *
 * <p>Only student-audience content ever reaches this type. Teacher study
 * material, answer keys, provider/model metadata and rejection reasons are
 * absent by construction, so a mistake in a filter cannot leak them.
 */
public record StudentContentDto(
    Long id,
    Long syllabusId,
    String subjectCode,
    String subjectName,
    String classCode,
    Long unitId,
    String unitTitle,
    GenerationContentType contentType,
    GenerationLanguage language,
    String title,
    String body,
    String structuredJson) {}