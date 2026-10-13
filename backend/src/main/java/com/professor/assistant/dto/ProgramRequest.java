package com.professor.assistant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Payload for creating or updating an academic program. */
public record ProgramRequest(
    @NotNull(message = "Department is required") Long departmentId,

    @NotBlank(message = "Code is required")
    @Size(max = 20, message = "Code must be at most 20 characters")
    @Pattern(regexp = "[A-Za-z0-9-]+", message = "Code may only contain letters, digits and dashes")
    String code,

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    String name,

    @NotBlank(message = "Level is required")
    @Pattern(
        regexp = "UNDERGRADUATE|POSTGRADUATE",
        message = "Level must be UNDERGRADUATE or POSTGRADUATE")
    String level,

    @NotNull(message = "Duration in years is required")
    @Min(value = 1, message = "Duration must be at least 1 year")
    @Max(value = 10, message = "Duration must be at most 10 years")
    Integer durationYears,

    @Size(max = 500, message = "Description must be at most 500 characters")
    String description) {}