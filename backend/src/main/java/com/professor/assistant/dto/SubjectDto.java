package com.professor.assistant.dto;

/** Read model for a subject. */
public record SubjectDto(
    Long id,
    Long departmentId,
    String departmentCode,
    Long programId,
    String code,
    String name,
    Integer credits,
    String description) {}