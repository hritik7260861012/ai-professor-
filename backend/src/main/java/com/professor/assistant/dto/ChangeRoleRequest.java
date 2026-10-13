package com.professor.assistant.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Administrator-only payload for promoting/demoting an account. */
public record ChangeRoleRequest(
    @NotNull(message = "Role is required")
    @Pattern(regexp = "STUDENT|TEACHER|ADMIN", message = "Role must be STUDENT, TEACHER or ADMIN")
    String role) {}
