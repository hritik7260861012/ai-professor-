package com.professor.assistant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Payload for creating or updating a class (section). */
public record ClassRequest(
    @NotNull(message = "Program is required") Long programId,

    @NotNull(message = "Semester is required") Long semesterId,

    @NotBlank(message = "Code is required")
    @Size(max = 30, message = "Code must be at most 30 characters")
    String code,

    @NotBlank(message = "Name is required")
    @Size(max = 120, message = "Name must be at most 120 characters")
    String name,

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    @Max(value = 1000, message = "Capacity must be at most 1000")
    Integer capacity) {}