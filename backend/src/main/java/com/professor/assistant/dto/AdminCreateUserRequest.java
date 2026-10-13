package com.professor.assistant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Administrator-only payload for creating accounts with a
 * privileged role (TEACHER or ADMIN).
 */
public record AdminCreateUserRequest(
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    String fullName,

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    String password,

    @NotNull(message = "Role is required")
    @Pattern(regexp = "STUDENT|TEACHER|ADMIN", message = "Role must be STUDENT, TEACHER or ADMIN")
    String role) {}
