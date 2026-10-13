package com.professor.assistant.exception;

/**
 * Raised when a document cannot be parsed or yields no usable text.
 *
 * <p>Kept distinct from {@link InvalidUploadException} so the syllabus can
 * move to EXTRACTION_FAILED (retryable, original file retained) instead of
 * failing the upload outright.
 */
public class SyllabusExtractionException extends RuntimeException {

  public SyllabusExtractionException(String message) {
    super(message);
  }

  public SyllabusExtractionException(String message, Throwable cause) {
    super(message, cause);
  }
}