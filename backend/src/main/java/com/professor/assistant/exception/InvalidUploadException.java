package com.professor.assistant.exception;

/** Rejected upload. Rendered as 400 with a teacher-facing explanation. */
public class InvalidUploadException extends RuntimeException {

  public InvalidUploadException(String message) {
    super(message);
  }

  public InvalidUploadException(String message, Throwable cause) {
    super(message, cause);
  }
}