package com.professor.assistant.ai;

/**
 * What a provider returns for a {@link GenerationRequest}.
 *
 * @param body human-readable markdown content
 * @param structuredJson machine-readable payload for MCQs / slides /
 *     question sets; null for prose-only types
 * @param providerName provider that produced this, for provenance
 * @param modelName model that produced this, for provenance
 */
public record GenerationResult(
    String body, String structuredJson, String providerName, String modelName) {

  public GenerationResult(String body, String structuredJson) {
    this(body, structuredJson, "unknown", "unknown");
  }
}