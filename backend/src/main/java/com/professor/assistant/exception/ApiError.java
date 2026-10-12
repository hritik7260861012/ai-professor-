package com.professor.assistant.exception;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * The single error envelope returned by every API endpoint, so the
 * frontend (and the future Android app) can always parse failures the
 * same way.
 */
public record ApiError(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    Map<String, Object> details) {

  public static ApiError of(HttpStatus status, String message, String path) {
    return of(status, message, path, null);
  }

  public static ApiError of(
      HttpStatus status, String message, String path, Map<String, Object> details) {
    return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, path, details);
  }
}
