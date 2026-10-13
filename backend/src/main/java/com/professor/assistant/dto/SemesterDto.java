package com.professor.assistant.dto;

import java.time.LocalDate;

/** Read model for a semester, including its academic year for display. */
public record SemesterDto(
    Long id,
    Long academicYearId,
    String academicYearName,
    String name,
    Integer semesterNumber,
    LocalDate startDate,
    LocalDate endDate) {}