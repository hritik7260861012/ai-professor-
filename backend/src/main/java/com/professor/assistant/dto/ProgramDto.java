package com.professor.assistant.dto;

/** Read model for an academic program, including its department for display. */
public record ProgramDto(
    Long id,
    Long departmentId,
    String departmentCode,
    String code,
    String name,
    String level,
    Integer durationYears,
    String description) {}