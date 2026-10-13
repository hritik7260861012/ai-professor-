package com.professor.assistant.dto;

import jakarta.validation.constraints.NotNull;

/** Payload for attaching an existing subject to a class. */
public record ClassSubjectRequest(
    @NotNull(message = "Subject is required") Long subjectId) {}