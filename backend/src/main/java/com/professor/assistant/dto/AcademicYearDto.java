package com.professor.assistant.dto;

import java.time.LocalDate;

/** Read model for an academic year. */
public record AcademicYearDto(
    Long id, String name, LocalDate startDate, LocalDate endDate, boolean current) {}