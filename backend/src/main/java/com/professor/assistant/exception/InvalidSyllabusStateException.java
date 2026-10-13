package com.professor.assistant.exception;

/**
 * A lifecycle transition is not allowed from the syllabus's current state,
 * for example editing after approval. Rendered as 409.
 */
public class InvalidSyllabusStateException extends RuntimeException {

  public InvalidSyllabusStateException(String message) {
    super(message);
  }
}