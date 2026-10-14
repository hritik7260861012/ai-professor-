package com.professor.assistant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.professor.assistant.ai.AiProviderException;
import com.professor.assistant.ai.ContentGenerationProvider;
import com.professor.assistant.ai.GenerationResult;
import com.professor.assistant.ai.GenerationWorker;
import com.professor.assistant.config.AiGenerationProperties;
import com.professor.assistant.domain.AiGenerationJob;
import com.professor.assistant.domain.GeneratedContent;
import com.professor.assistant.domain.GenerationContentType;
import com.professor.assistant.domain.GenerationJobStatus;
import com.professor.assistant.repository.GeneratedContentRepository;
import com.professor.assistant.service.ContentGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * The worker's contract with a misbehaving provider.
 *
 * <p>The real Gemini provider is never contacted here. The provider is
 * replaced with a stub so retry budgets, exhaustion and output validation can
 * be driven exactly, with no network, no API key and no timing dependence.
 *
 * <p>These are the cases the mock provider cannot produce on its own: it only
 * fails once per enqueue, so it can show a retry succeeding but not a retry
 * budget running out.
 */
@SpringBootTest
@ActiveProfiles("test")
class GenerationProviderContractTest extends AbstractContentGenerationTest {

  @MockBean private ContentGenerationProvider provider;

  @Autowired private ContentGenerationService generationService;
  @Autowired private GenerationWorker worker;
  @Autowired private AiGenerationProperties properties;
  @Autowired private GeneratedContentRepository contents;

  @BeforeEach
  void allowSubmissions() throws Exception {
    createApprovedSyllabus();
    // The submit path checks availability before persisting anything.
    when(provider.isAvailable()).thenReturn(true);
  }

  private long submit(GenerationContentType type) {
    return generationService.submit(teacher, request(type)).id();
  }

  @Test
  @DisplayName("Retries stop after the configured attempt budget")
  void retryBudgetIsBounded() {
    int maxAttempts = properties.getMaxAttempts();
    when(provider.generate(any()))
        .thenThrow(new AiProviderException(AiProviderException.Kind.RATE_LIMITED, "slow down"));

    long jobId = submit(GenerationContentType.UNIT_NOTES);
    worker.runSafely(jobId);

    AiGenerationJob failed = jobs.findById(jobId).orElseThrow();
    assertThat(failed.getStatus()).isEqualTo(GenerationJobStatus.FAILED);
    assertThat(failed.getAttemptCount()).isEqualTo(maxAttempts);
    assertThat(failed.getErrorCode())
        .isEqualTo(AiProviderException.Kind.RATE_LIMITED.name());
    // Bounded, not unbounded: a broken provider must not spin forever.
    verify(provider, times(maxAttempts)).generate(any());
  }

  @Test
  @DisplayName("A retryable failure is recovered when the provider then succeeds")
  void retryableThenSuccessCompletes() {
    when(provider.generate(any()))
        .thenThrow(new AiProviderException(AiProviderException.Kind.TIMEOUT, "timeout"))
        .thenReturn(new GenerationResult("# Notes\n\nRecovered content.", null, "STUB", "stub-1"));

    long jobId = submit(GenerationContentType.UNIT_NOTES);
    worker.runSafely(jobId);

    AiGenerationJob done = jobs.findById(jobId).orElseThrow();
    assertThat(done.getStatus()).isEqualTo(GenerationJobStatus.COMPLETED);
    assertThat(done.getAttemptCount()).isEqualTo(2);
    assertThat(done.getProviderName()).isEqualTo("STUB");
    assertThat(done.getModelName()).isEqualTo("stub-1");
  }

  @Test
  @DisplayName("A provider that is not configured fails the job instead of hanging")
  void unavailableProviderFailsTheJob() {
    // First call is the submit-time check (true), the second is the worker's
    // own check (false) — e.g. the key was removed between the two.
    when(provider.isAvailable()).thenReturn(true).thenReturn(false);

    long jobId = submit(GenerationContentType.UNIT_NOTES);
    worker.runSafely(jobId);

    AiGenerationJob failed = jobs.findById(jobId).orElseThrow();
    assertThat(failed.getStatus()).isEqualTo(GenerationJobStatus.FAILED);
    assertThat(failed.getErrorCode()).isEqualTo("PROVIDER_NOT_CONFIGURED");
    assertThat(failed.getContentId()).isNull();
  }

  @Test
  @DisplayName("An unexpected provider error fails the job rather than escaping")
  void unexpectedErrorIsContained() {
    when(provider.generate(any())).thenThrow(new IllegalStateException("boom"));

    long jobId = submit(GenerationContentType.UNIT_NOTES);
    worker.runSafely(jobId);

    AiGenerationJob failed = jobs.findById(jobId).orElseThrow();
    assertThat(failed.getStatus()).isEqualTo(GenerationJobStatus.FAILED);
    assertThat(failed.getErrorCode()).isEqualTo("INTERNAL_ERROR");
    // The raw provider message must not be stored or shown to a teacher.
    assertThat(failed.getErrorMessage()).doesNotContain("boom");
  }

  @Test
  @DisplayName("Malformed structured output is rejected and never stored")
  void malformedStructuredOutputIsNotPersisted() {
    // An MCQ with a single option: structurally impossible, and unusable by a
    // student. It must fail the job at the validation boundary.
    String badMcq =
        """
        {"questions":[{"question":"What is 2+2?","options":{"A":"4"},
        "correctAnswer":"A","explanation":"basic addition"}]}
        """;
    when(provider.generate(any()))
        .thenReturn(new GenerationResult(badMcq, badMcq, "STUB", "stub-1"));

    long contentCountBefore = contents.count();
    long jobId = submit(GenerationContentType.MCQS);
    worker.runSafely(jobId);

    AiGenerationJob failed = jobs.findById(jobId).orElseThrow();
    assertThat(failed.getStatus()).isEqualTo(GenerationJobStatus.FAILED);
    assertThat(failed.getErrorCode())
        .isEqualTo(AiProviderException.Kind.MALFORMED_RESPONSE.name());
    assertThat(failed.getContentId()).isNull();
    assertThat(contents.count()).isEqualTo(contentCountBefore);
  }

  @Test
  @DisplayName("Valid structured output is stored with its provider provenance")
  void validStructuredOutputIsStored() {
    String goodMcq =
        """
        {"questions":[{"question":"What is 2+2?",
        "options":{"A":"3","B":"4","C":"5","D":"6"},
        "correctAnswer":"B","explanation":"Basic addition."}]}
        """;
    when(provider.generate(any()))
        .thenReturn(new GenerationResult(goodMcq, goodMcq, "STUB", "stub-1"));

    long jobId = submit(GenerationContentType.MCQS);
    worker.runSafely(jobId);

    AiGenerationJob done = jobs.findById(jobId).orElseThrow();
    assertThat(done.getStatus()).isEqualTo(GenerationJobStatus.COMPLETED);

    GeneratedContent stored = contents.findById(done.getContentId()).orElseThrow();
    assertThat(stored.getProviderName()).isEqualTo("STUB");
    assertThat(stored.getModelName()).isEqualTo("stub-1");
    assertThat(stored.getStructuredJson()).contains("\"correctAnswer\":\"B\"");
  }
}