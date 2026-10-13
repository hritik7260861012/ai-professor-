package com.professor.assistant.dto;

import jakarta.validation.constraints.NotNull;

/** Payload for assigning a teacher to a subject within a class. */
public record TeachingAssignmentRequest(
    @NotNull(message = "Teacher is required") Long teacherId,

    @NotNull(message = "Class is required") Long classId,

    @NotNull(message = "Subject is required") Long subjectId) {}