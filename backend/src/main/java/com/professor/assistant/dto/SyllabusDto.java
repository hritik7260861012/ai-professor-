package com.professor.assistant.dto;

import com.professor.assistant.domain.SyllabusStatus;
import java.time.Instant;

/** Syllabus metadata. Never includes the storage key or any file path. */
public record SyllabusDto(
    Long id,
    Long classId,
    String classCode,
    String className,
    Long subjectId,
    String subjectCode,
    String subjectName,
    Long semesterId,
    String semesterName,
    String academicYearName,
    Long uploadedById,
    String uploadedByName,
    String originalFilename,
    String contentType,
    long fileSize,
    SyllabusStatus status,
    Integer version,
    boolean current,
    String extractionError,
    String reviewNote,
    Long reviewedById,
    Instant reviewedAt,
    Instant uploadedAt,
    Instant updatedAt) {}