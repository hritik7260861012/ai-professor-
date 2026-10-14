package com.professor.assistant.ai;

import com.professor.assistant.config.AiGenerationProperties;
import com.professor.assistant.domain.GenerationContentType;
import com.professor.assistant.service.ContentGenerationService;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Runs a generation job on the bounded executor.
 *
 * <p>The HTTP request has already returned by the time this runs, and no
 * database transaction is held open across the provider call: every state
 * change goes through {@link GenerationJobStore}, which opens and commits
 * its own short transaction.
 *
 * <p>Retries are bounded and only fire for genuinely retryable failures
 * (429, 5xx, timeout). Configuration problems and malformed output fail
 * immediately, because repeating them would spend quota for no benefit.
 */
@Component
public class GenerationWorker {

  private static final Logger log = LoggerFactory.getLogger(GenerationWorker.class);

  private final GenerationJobStore store;
  private final ContentGenerationService generation;
  private final ContentGenerationProvider provider;
  private final StructuredContentValidator validator;
  private final AiGenerationProperties properties;
  private final Executor executor;

  public GenerationWorker(
      GenerationJobStore store,
      ContentGenerationService generation,
      ContentGenerationProvider provider,
      StructuredContentValidator validator,
      AiGenerationProperties properties,
      @Qualifier("aiGenerationExecutor") Executor executor) {
    this.store = store;
    this.generation = generation;
    this.provider = provider;
    this.validator = validator;
    this.properties = properties;
    this.executor = executor;
  }

  /** Hands the job to the bounded executor. Never blocks the caller. */
  public void submit(Long jobId) {
    try {
      executor.execute(() -> runSafely(jobId));
    } catch (RejectedExecutionException e) {
      // Queue is full: fail the job explicitly rather than leaving it QUEUED
      // forever.
      log.warn("Generation executor is saturated; cancelling job {}", jobId);
      store.failJob(jobId, "EXECUTOR_SATURATED", "The generation service is busy. Please try again.");
    }
  }

  /** Synchronous execution, also used directly by tests. */
  public void runSafely(Long jobId) {
    try {
      execute(jobId);
    } catch (Exception e) {
      // The executor thread must never die silently.
      log.error("Generation job {} failed unexpectedly", jobId, e);
      store.failJob(jobId, "INTERNAL_ERROR", "Generation failed unexpectedly.");
    }
  }

  void execute(Long jobId) {
    if (!provider.isAvailable()) {
      store.failJob(
          jobId,
          "PROVIDER_NOT_CONFIGURED",
          "AI content generation is not configured on this server.");
      return;
    }

    int maxAttempts = Math.max(1, properties.getMaxAttempts());
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      // Claim the job first. Only the caller that wins the conditional
      // update may execute it, so a duplicate submit, a retry and the
      // executor queue cannot all run the same job.
      if (!store.claimForExecution(jobId)) {
        log.debug("Generation job {} was already claimed; skipping.", jobId);
        return;
      }
      try {
        GenerationRequest request = generation.buildProviderRequest(jobId);
        // Content type comes from the job, read inside the transaction.
        GenerationContentType type = store.requireJob(jobId).getContentType();
        GenerationResult result = provider.generate(request);

        // Validate before storing: malformed structured output must never
        // reach a student.
        String structured = validator.validate(type, result.structuredJson());
        store.persistContent(jobId, result, structured);
        return;
      } catch (AiProviderException e) {
        // A retryable failure must release the claim so the next attempt
        // can take it.
        if (e.isRetryable() && attempt < maxAttempts) {
          store.releaseForRetry(jobId);
          log.warn(
              "Generation job {} attempt {}/{} failed ({}); retrying.",
              jobId, attempt, maxAttempts, e.kind());
          pause();
          continue;
        }
        store.failJob(jobId, e.kind().name(), e.getMessage());
        return;
      } catch (RuntimeException e) {
        log.error("Generation job {} raised an unexpected error", jobId, e);
        store.failJob(jobId, "INTERNAL_ERROR", "Generation failed unexpectedly.");
        return;
      }
    }
  }

  /** Short pause between bounded attempts. */
  private void pause() {
    try {
      Thread.sleep(200L);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}