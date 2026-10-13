package com.professor.assistant.dto;

/**
 * Read model for a class (section). Carries denormalised program and
 * semester labels so the UI never needs follow-up requests, plus the live
 * enrollment count.
 */
public record ClassDto(
    Long id,
    Long programId,
    String programCode,
    String programName,
    Long semesterId,
    String semesterName,
    String academicYearName,
    String code,
    String name,
    Integer capacity,
    long enrolledCount) {}