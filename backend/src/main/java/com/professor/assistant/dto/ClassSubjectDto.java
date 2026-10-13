package com.professor.assistant.dto;

/** Read model for a subject attached to a class. */
public record ClassSubjectDto(
    Long id,
    Long classId,
    Long subjectId,
    String subjectCode,
    String subjectName,
    Integer credits,
    Long semesterId) {}