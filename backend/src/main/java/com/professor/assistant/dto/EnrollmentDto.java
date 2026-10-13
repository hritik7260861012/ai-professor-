package com.professor.assistant.dto;

import java.time.Instant;

/** Read model for a student enrollment. */
public record EnrollmentDto(
    Long id,
    Long studentId,
    String studentName,
    String studentEmail,
    Long classId,
    String classCode,
    String className,
    String status,
    Instant enrolledAt) {}