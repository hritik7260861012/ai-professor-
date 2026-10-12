package com.professor.assistant.exception;

/** A single field-level validation error inside {@link ApiError#details()}. */
public record ApiFieldError(String field, String message) {}
