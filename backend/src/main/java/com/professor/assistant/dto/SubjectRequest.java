package com.professor.assistant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Payload for creating or updating a subject. */
public record SubjectRequest(
    @NotNull(message = "Department is required") Long departmentId,

    /** Optional: a subject may belong to a specific program. */
    Long programId,

    @NotBlank(message = "Code is required")
    @Size(max = 20, message = "Code must be at most 20 characters")
    @Pattern(regexp = "[A-Za-z0-9-]+", message = "Code may only contain letters, digits and dashes")
    String code,

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    String name,

    @NotNull(message = "Credits are required")
    @Min(value = 1, message = "Credits must be at least 1")
    @Max(value = 12, message = "Credits must be at most 12")
    Integer credits,

    @Size(max = 500, message = "Description must be at most 500 characters")
    String description) {}