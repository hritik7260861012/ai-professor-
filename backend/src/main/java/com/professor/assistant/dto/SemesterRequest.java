package com.professor.assistant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Payload for creating or updating a semester. */
public record SemesterRequest(
    @NotNull(message = "Academic year is required") Long academicYearId,

    @NotBlank(message = "Name is required")
    @Size(max = 60, message = "Name must be at most 60 characters")
    String name,

    @NotNull(message = "Semester number is required")
    @Min(value = 1, message = "Semester number must be at least 1")
    @Max(value = 12, message = "Semester number must be at most 12")
    Integer semesterNumber,

    LocalDate startDate,
    LocalDate endDate) {}