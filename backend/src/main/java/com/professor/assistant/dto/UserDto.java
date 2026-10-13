package com.professor.assistant.dto;

import java.time.Instant;

/** Public representation of a user. Never contains the password hash. */
public record UserDto(
    Long id,
    String fullName,
    String email,
    String role,
    String status,
    Instant createdAt) {}
