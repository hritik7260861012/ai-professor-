package com.professor.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Payload for creating or updating an academic year. */
public record AcademicYearRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 20, message = "Name must be at most 20 characters")
    String name,

    @NotNull(message = "Start date is required") LocalDate startDate,

    @NotNull(message = "End date is required") LocalDate endDate,

    Boolean current) {}