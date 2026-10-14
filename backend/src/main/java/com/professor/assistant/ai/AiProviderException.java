package com.professor.assistant.ai;

/**
 * Failure from an AI provider, reduced to a safe, storable summary.
 *
 * <p>{@link #retryable()} decides whether the worker may spend another
 * attempt. Configuration problems and malformed output are never retried:
 * repeating them would burn quota without changing the outcome.
 */
public class AiProviderException extends RuntimeException {

  /** Category stored on the job; never contains provider internals. */
  public enum Kind {
    /** No API key or provider not configured. */
    CONFIGURATION(false),
    /** HTTP 429 or an explicit rate-limit response. */
    RATE_LIMITED(true),
    /** Connect/read timeout. */
    TIMEOUT(true),
    /** HTTP 5xx. */
    PROVIDER_UNAVAILABLE(true),
    /** Any other 4xx, e.g. a malformed request. */
    INVALID_REQUEST(false),
    /** Response did not match the expected structure. */
    MALFORMED_RESPONSE(false),
    /** Anything unexpected. */
    UNKNOWN(false);

    private final boolean retryable;

    Kind(boolean retryable) {
      this.retryable = retryable;
    }

    public boolean isRetryable() {
      return retryable;
    }
  }

  private final Kind kind;

  public AiProviderException(Kind kind, String message) {
    super(message);
    this.kind = kind;
  }

  public AiProviderException(Kind kind, String message, Throwable cause) {
    super(message, cause);
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }

  public boolean isRetryable() {
    return kind.isRetryable();
  }
}