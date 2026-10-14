package com.professor.assistant.ai;

/**
 * Pluggable AI content generator.
 *
 * <p>Isolates the provider so the service, worker and tests never depend on
 * a specific vendor. Implementations must be safe to call from a worker
 * thread and must never throw vendor-specific exceptions.
 */
public interface ContentGenerationProvider {

  /** Stable identifier stored on each job and artifact for provenance. */
  String name();

  /** Model id currently in use, stored alongside the artifact. */
  String model();

  /**
   * True when this provider can serve a request right now. A Gemini
   * provider without a configured key returns false, and the caller turns
   * that into a clear configuration error rather than fake content.
   */
  boolean isAvailable();

  /**
   * Generates content for a request.
   *
   * @throws AiProviderException on any failure, including configuration,
   *     timeout, rate limiting and malformed provider output
   */
  GenerationResult generate(GenerationRequest request);
}