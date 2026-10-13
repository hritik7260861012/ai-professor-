package com.professor.assistant.dto;

import java.time.Instant;

/** Read model for a department. */
public record DepartmentDto(
    Long id, String code, String name, String description, Instant createdAt) {}