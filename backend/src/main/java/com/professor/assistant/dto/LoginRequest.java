package com.professor.assistant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for POST /api/v1/auth/login. */
public record LoginRequest(
    @NotBlank(message = "Email is required") @Email(message = "Email must be a valid address") String email,
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    String password) {}
