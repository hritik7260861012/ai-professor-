package com.professor.assistant.exception;

/**
 * A generation request exceeded a cost or capacity guard (daily quota,
 * per-user concurrency, item count).
 *
 * <p>Rendered as 429 so the frontend can distinguish "try later" from
 * "this request was invalid" (400).
 */
public class GenerationQuotaExceededException extends RuntimeException {

  private final String errorCode;

  public GenerationQuotaExceededException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public String errorCode() {
    return errorCode;
  }
}