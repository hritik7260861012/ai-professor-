package com.professor.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Payload for creating or updating a department. */
public record DepartmentRequest(
    @NotBlank(message = "Code is required")
    @Size(max = 20, message = "Code must be at most 20 characters")
    @Pattern(regexp = "[A-Za-z0-9-]+", message = "Code may only contain letters, digits and dashes")
    String code,

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    String name,

    @Size(max = 500, message = "Description must be at most 500 characters")
    String description) {}