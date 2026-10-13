package com.professor.assistant.dto;

import java.time.Instant;

/** Read model for a teacher's subject assignment within a class. */
public record TeachingAssignmentDto(
    Long id,
    Long teacherId,
    String teacherName,
    Long classId,
    String classCode,
    Long subjectId,
    String subjectCode,
    Long semesterId,
    String semesterName,
    Instant assignedAt) {}