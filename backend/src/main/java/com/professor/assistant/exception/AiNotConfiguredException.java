package com.professor.assistant.exception;

/**
 * Generation was refused because no AI provider is configured (no
 * {@code GEMINI_API_KEY}). Rendered as 503 so the frontend can show a
 * configuration message rather than a generic failure.
 */
public class AiNotConfiguredException extends RuntimeException {

  public AiNotConfiguredException(String message) {
    super(message);
  }
}