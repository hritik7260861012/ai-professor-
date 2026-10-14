package com.professor.assistant.dto;

import com.professor.assistant.domain.GenerationContentType;
import com.professor.assistant.domain.GenerationDifficulty;
import com.professor.assistant.domain.GenerationDetailLevel;
import com.professor.assistant.domain.GenerationLanguage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Teacher-supplied generation options.
 *
 * <p>No user id is accepted here: the caller is always the authenticated
 * principal. Every bound is re-checked in the service layer, because
 * frontend validation is a convenience and never the authority.
 *
 * @param syllabusId source syllabus; must be APPROVED
 * @param unitId selected unit, or null for the whole syllabus
 * @param includeAnswerKey whether the teacher asked for marking guidance
 * @param instructions optional extra instruction, length-capped
 */
public record GenerationRequestDto(
    @NotNull(message = "A syllabus is required") Long syllabusId,

    Long unitId,

    @NotNull(message = "A content type is required") GenerationContentType contentType,

    GenerationLanguage language,

    GenerationDetailLevel detailLevel,

    GenerationDifficulty difficulty,

    @Min(value = 1, message = "Number of items must be at least 1")
    @Max(value = 30, message = "Number of items must be at most 30")
    Integer itemCount,

    Boolean includeAnswerKey,

    @Size(max = 500, message = "Instructions must be at most 500 characters")
    String instructions) {}