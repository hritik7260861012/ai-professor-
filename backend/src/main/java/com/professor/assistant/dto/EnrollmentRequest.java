package com.professor.assistant.dto;

import jakarta.validation.constraints.NotNull;

/** Payload for enrolling a student into a class. */
public record EnrollmentRequest(
    @NotNull(message = "Student is required") Long studentId,
    @NotNull(message = "Class is required") Long classId) {}