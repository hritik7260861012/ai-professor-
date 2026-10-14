package com.professor.assistant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.professor.assistant.ai.GenerationWorker;
import com.professor.assistant.domain.GenerationContentType;
import com.professor.assistant.domain.GenerationJobStatus;
import com.professor.assistant.service.ContentGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Request validation and quota guards, with the limits lowered below the
 * static bean-validation bounds on {@code GenerationRequestDto}.
 *
 * <p>{@code @Max(30)} and {@code @Size(500)} on the DTO catch the obvious
 * cases, so the service-layer checks look redundant. They are not: both the
 * item cap and the instruction cap are operator-configurable, and an operator
 * who lowers them must still get a validation failure rather than something
 * that looks like a missing resource.
 */
@SpringBootTest(
    properties = {
      "app.ai.max-items=2",
      "app.ai.max-instruction-length=10",
      "app.ai.daily-quota-per-user=2"
    })
@ActiveProfiles("test")
class GenerationQuotaValidationTest extends AbstractContentGenerationTest {

  @Autowired private GenerationWorker worker;
  @Autowired private ContentGenerationService generationService;

  @BeforeEach
  void prepare() throws Exception {
    createApprovedSyllabus();
  }

  @Test
  @DisplayName("A lowered item limit is a validation error, not a missing resource")
  void loweredItemLimitIsBadRequest() throws Exception {
    // Inside the DTO's static @Max(30), but above the configured max-items=2.
    submit(teacher, request(GenerationContentType.MCQS, 5, null), 400);
  }

  @Test
  @DisplayName("A lowered instruction limit is a validation error, not a missing resource")
  void loweredInstructionLimitIsBadRequest() throws Exception {
    // Inside the DTO's static @Size(500), but above the configured limit.
    submit(teacher, request(GenerationContentType.UNIT_NOTES, null, "x".repeat(50)), 400);
  }

  @Test
  @DisplayName("Starting a second job while one is in flight is rate limited")
  void concurrentJobIsRateLimited() throws Exception {
    // Submitted through the service, so it stays QUEUED: nothing dispatches it.
    long first = submitOnly(GenerationContentType.UNIT_NOTES);
    assertThat(jobs.findById(first).orElseThrow().getStatus())
        .isEqualTo(GenerationJobStatus.QUEUED);

    MvcResult result = submit(teacher, request(GenerationContentType.MCQS, 2, null), 429);

    assertThat(errorCodeOf(result)).isEqualTo("CONCURRENCY_LIMIT");
  }

  @Test
  @DisplayName("The daily quota is enforced once completed jobs have accumulated")
  void dailyQuotaIsEnforced() throws Exception {
    long first = submitOnly(GenerationContentType.UNIT_NOTES);
    worker.runSafely(first);
    long second = submitOnly(GenerationContentType.MCQS);
    worker.runSafely(second);

    assertThat(jobs.findById(first).orElseThrow().getStatus())
        .isEqualTo(GenerationJobStatus.COMPLETED);
    assertThat(jobs.findById(second).orElseThrow().getStatus())
        .isEqualTo(GenerationJobStatus.COMPLETED);

    // Both jobs are terminal, so the concurrency guard is clear and only the
    // daily quota can reject this one.
    MvcResult result = submit(teacher, request(GenerationContentType.QUICK_REVISION), 429);

    assertThat(errorCodeOf(result)).isEqualTo("DAILY_QUOTA_EXCEEDED");
  }

  @Test
  @DisplayName("The rate-limit response body carries no provider internals")
  void quotaErrorIsSafe() throws Exception {
    submitOnly(GenerationContentType.UNIT_NOTES);

    String body = submit(teacher, request(GenerationContentType.MCQS, 2, null), 429)
        .getResponse()
        .getContentAsString();

    assertThat(body).contains("CONCURRENCY_LIMIT");
    assertThat(body).doesNotContain("apiKey").doesNotContain("GEMINI_API_KEY");
  }

  /**
   * Creates a QUEUED job without dispatching it to the worker.
   *
   * <p>Deliberately calls the service rather than the endpoint: the endpoint
   * hands the job to the background executor, so the job would race between
   * QUEUED and COMPLETED and the concurrency guard would be non-deterministic.
   */
  private long submitOnly(GenerationContentType type) {
    return generationService.submit(teacher, request(type, type.isCountable() ? 2 : null, null)).id();
  }

  private String errorCodeOf(MvcResult result) throws Exception {
    JsonNode details = objectMapper.readTree(result.getResponse().getContentAsString()).path("details");
    return details.path("code").asText();
  }
}