package com.professor.assistant.dto;

import jakarta.validation.constraints.Size;

/**
 * Reason supplied when rejecting a syllabus.
 *
 * @param reason why the structure was rejected; shown to the teacher
 */
public record SyllabusDecisionRequest(
    @Size(max = 1000, message = "Reason must be at most 1000 characters") String reason) {}