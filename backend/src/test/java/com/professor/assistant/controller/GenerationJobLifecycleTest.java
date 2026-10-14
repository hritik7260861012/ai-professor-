package com.professor.assistant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.professor.assistant.ai.AiProviderException;
import com.professor.assistant.ai.ContentGenerationProvider;
import com.professor.assistant.ai.GenerationJobStore;
import com.professor.assistant.ai.GenerationWorker;
import com.professor.assistant.ai.MockContentGenerationProvider;
import com.professor.assistant.ai.StaleJobReconciler;
import com.professor.assistant.ai.StructuredContentValidator;
import com.professor.assistant.config.AiGenerationProperties;
import com.professor.assistant.domain.AiGenerationJob;
import com.professor.assistant.domain.GenerationContentType;
import com.professor.assistant.domain.GenerationJobStatus;
import com.professor.assistant.service.ContentGenerationService;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Job lifecycle: claiming, retries, failures and stale-job recovery.
 *
 * <p>Every test here runs the worker <em>synchronously</em> via
 * {@link GenerationWorker#runSafely(Long)} instead of posting to the submit
 * endpoint. That matters for two reasons:
 *
 * <ul>
 *   <li>The submit endpoint dispatches to a real background executor, so the
 *       job may already be RUNNING before the test can observe it. Driving the
 *       worker on the test thread removes that race entirely — there are no
 *       sleeps and no polling anywhere in this class.
 *   <li>{@link MockContentGenerationProvider#enqueueFailures(String...)} is
 *       thread-local by design, so a forced failure only lands on the thread
 *       that enqueued it.
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class GenerationJobLifecycleTest extends AbstractContentGenerationTest {

  @Autowired private ContentGenerationService generationService;
  @Autowired private GenerationWorker worker;
  @Autowired private GenerationJobStore store;
  @Autowired private StaleJobReconciler reconciler;
  @Autowired private ContentGenerationProvider provider;
  @Autowired private AiGenerationProperties properties;
  @Autowired private StructuredContentValidator validator;
  @Autowired private MockContentGenerationProvider mockProvider;
  @Autowired
  @Qualifier("aiGenerationExecutor")
  private Executor executor;

  @BeforeEach
  void prepare() throws Exception {
    createApprovedSyllabus();
  }

  /** Creates a QUEUED job without dispatching it, so execution stays under test control. */
  private AiGenerationJob queuedJob() {
    return jobs
        .findById(submitOnly())
        .orElseThrow();
  }

  private long submitOnly() {
    return generationService.submit(teacher, request(GenerationContentType.UNIT_NOTES)).id();
  }

  /* ------------------- mock provider needs no credential ------------------- */

  @Test
  @DisplayName("The mock provider is available with no API key configured")
  void mockProviderNeedsNoCredential() {
    assertThat(properties.getApiKey()).isEmpty();
    assertThat(properties.getProvider()).isEqualTo(AiGenerationProperties.Provider.MOCK);
    assertThat(provider.name()).isEqualTo("MOCK");
    assertThat(provider.isAvailable()).isTrue();
  }

  @Test
  @DisplayName("The mock provider completes a job end to end with no API key")
  void mockProviderGeneratesWithoutCredential() {
    AiGenerationJob job = queuedJob();
    worker.runSafely(job.getId());

    AiGenerationJob done = jobs.findById(job.getId()).orElseThrow();
    assertThat(done.getStatus()).isEqualTo(GenerationJobStatus.COMPLETED);
    assertThat(done.getContentId()).isNotNull();
    assertThat(done.getProviderName()).isEqualTo("MOCK");
  }

  /* ------------------------------ atomic claim ----------------------------- */

  @Test
  @DisplayName("A queued job starts unattempted")
  void newJobIsQueuedWithNoAttempts() {
    AiGenerationJob job = queuedJob();

    assertThat(job.getStatus()).isEqualTo(GenerationJobStatus.QUEUED);
    assertThat(job.getAttemptCount()).isZero();
    assertThat(job.getContentId()).isNull();
  }

  @Test
  @DisplayName("A job can be claimed by exactly one caller")
  void claimIsExclusive() {
    AiGenerationJob job = queuedJob();

    assertThat(store.claimForExecution(job.getId())).isTrue();
    // The conditional UPDATE matches no row the second time, so a duplicate
    // submit, a retry and the executor queue cannot all run the same job.
    assertThat(store.claimForExecution(job.getId())).isFalse();

    AiGenerationJob claimed = jobs.findById(job.getId()).orElseThrow();
    assertThat(claimed.getStatus()).isEqualTo(GenerationJobStatus.RUNNING);
    assertThat(claimed.getAttemptCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("A completed job can never be claimed again")
  void completedJobIsNotClaimable() {
    AiGenerationJob job = queuedJob();
    worker.runSafely(job.getId());

    assertThat(jobs.findById(job.getId()).orElseThrow().getStatus())
        .isEqualTo(GenerationJobStatus.COMPLETED);
    assertThat(store.claimForExecution(job.getId())).isFalse();
  }

  @Test
  @DisplayName("Running an already claimed job is a no-op")
  void claimedJobIsNotExecutedTwice() {
    AiGenerationJob job = queuedJob();

    // Simulate another worker winning the race: claim it, then run the
    // worker. It must return immediately without producing content.
    assertThat(store.claimForExecution(job.getId())).isTrue();
    worker.runSafely(job.getId());

    AiGenerationJob claimed = jobs.findById(job.getId()).orElseThrow();
    assertThat(claimed.getStatus()).isEqualTo(GenerationJobStatus.RUNNING);
    assertThat(claimed.getAttemptCount()).isEqualTo(1);
  }

  /* -------------------------------- retries ------------------------------- */

  @Test
  @DisplayName("A retryable failure is retried and the job then succeeds")
  void retryableFailureRecovers() {
    AiGenerationJob job = queuedJob();

    // Thread-local, so it applies to this test thread's provider call only.
    mockProvider.enqueueFailures(AiProviderException.Kind.RATE_LIMITED.name());
    worker.runSafely(job.getId());

    AiGenerationJob done = jobs.findById(job.getId()).orElseThrow();
    assertThat(done.getStatus()).isEqualTo(GenerationJobStatus.COMPLETED);
    assertThat(done.getAttemptCount()).isEqualTo(2);
    assertThat(done.getContentId()).isNotNull();
  }

  @Test
  @DisplayName("A non-retryable failure fails immediately without spending another attempt")
  void nonRetryableFailureDoesNotRetry() {
    AiGenerationJob job = queuedJob();

    // Malformed output will not improve on a second attempt, so retrying it
    // would burn quota for no benefit.
    mockProvider.enqueueFailures(AiProviderException.Kind.MALFORMED_RESPONSE.name());
    worker.runSafely(job.getId());

    AiGenerationJob failed = jobs.findById(job.getId()).orElseThrow();
    assertThat(failed.getStatus()).isEqualTo(GenerationJobStatus.FAILED);
    assertThat(failed.getAttemptCount()).isEqualTo(1);
    assertThat(failed.getErrorCode())
        .isEqualTo(AiProviderException.Kind.MALFORMED_RESPONSE.name());
    assertThat(failed.getContentId()).isNull();
  }

  @Test
  @DisplayName("Releasing a claimed job for retry returns it to QUEUED")
  void releaseForRetryRequeues() {
    AiGenerationJob job = queuedJob();
    store.claimForExecution(job.getId());

    store.releaseForRetry(job.getId());

    AiGenerationJob released = jobs.findById(job.getId()).orElseThrow();
    assertThat(released.getStatus()).isEqualTo(GenerationJobStatus.QUEUED);
    // Releasing is what makes a retry possible at all: without it the next
    // attempt would find its own job RUNNING and refuse to run.
    assertThat(store.claimForExecution(job.getId())).isTrue();
  }

  @Test
  @DisplayName("A failed job can be retried through the API")
  void failedJobCanBeRetriedThroughApi() throws Exception {
    AiGenerationJob job = queuedJob();
    mockProvider.enqueueFailures(AiProviderException.Kind.INVALID_REQUEST.name());
    worker.runSafely(job.getId());
    assertThat(jobs.findById(job.getId()).orElseThrow().getStatus())
        .isEqualTo(GenerationJobStatus.FAILED);

    mockMvc
        .perform(
            post("/api/v1/teacher/content/generations/{jobId}/retry", job.getId())
                .with(user(teacher))
                .with(csrf()))
        .andExpect(status().isAccepted());

    // The endpoint requeues and re-dispatches, so the job is no longer
    // terminally failed. Either QUEUED or already RUNNING is correct.
    GenerationJobStatus after = jobs.findById(job.getId()).orElseThrow().getStatus();
    assertThat(after).isIn(GenerationJobStatus.QUEUED, GenerationJobStatus.RUNNING);
  }

  /* --------------------------- stale job recovery -------------------------- */

  @Test
  @DisplayName("A job left RUNNING by a dead process is cancelled on reconcile")
  void staleRunningJobIsCancelled() {
    AiGenerationJob job = queuedJob();
    store.claimForExecution(job.getId());
    assertThat(jobs.findById(job.getId()).orElseThrow().getStatus())
        .isEqualTo(GenerationJobStatus.RUNNING);

    backdate(job.getId(), properties.getStaleJobMinutes() + 15);
    reconciler.reconcileStaleJobs();

    AiGenerationJob stale = jobs.findById(job.getId()).orElseThrow();
    assertThat(stale.getStatus()).isEqualTo(GenerationJobStatus.CANCELLED);
    assertThat(stale.getErrorCode()).isEqualTo("CANCELLED");
    assertThat(stale.getErrorMessage()).containsIgnoringCase("restart");
  }

  @Test
  @DisplayName("A QUEUED job lost across a restart is cancelled on reconcile")
  void staleQueuedJobIsCancelled() {
    AiGenerationJob job = queuedJob();
    backdate(job.getId(), properties.getStaleJobMinutes() + 15);

    reconciler.reconcileStaleJobs();

    assertThat(jobs.findById(job.getId()).orElseThrow().getStatus())
        .isEqualTo(GenerationJobStatus.CANCELLED);
  }

  @Test
  @DisplayName("A job younger than the stale threshold is left alone")
  void recentJobIsNotReconciled() {
    AiGenerationJob job = queuedJob();

    reconciler.reconcileStaleJobs();

    assertThat(jobs.findById(job.getId()).orElseThrow().getStatus())
        .isEqualTo(GenerationJobStatus.QUEUED);
  }

  @Test
  @DisplayName("A completed job is never turned into a cancelled one")
  void completedJobSurvivesReconcile() {
    AiGenerationJob job = queuedJob();
    worker.runSafely(job.getId());
    backdate(job.getId(), properties.getStaleJobMinutes() + 15);

    reconciler.reconcileStaleJobs();

    AiGenerationJob done = jobs.findById(job.getId()).orElseThrow();
    assertThat(done.getStatus()).isEqualTo(GenerationJobStatus.COMPLETED);
    assertThat(done.getContentId()).isNotNull();
  }

  /* --------------------------- failure bookkeeping ------------------------- */

  @Test
  @DisplayName("A late failure never orphans content that was already stored")
  void failJobDoesNotOverwriteCompleted() {
    AiGenerationJob job = queuedJob();
    worker.runSafely(job.getId());

    store.failJob(job.getId(), "INTERNAL_ERROR", "A late error arrived.");

    AiGenerationJob done = jobs.findById(job.getId()).orElseThrow();
    assertThat(done.getStatus()).isEqualTo(GenerationJobStatus.COMPLETED);
    assertThat(done.getErrorCode()).isNull();
    assertThat(done.getContentId()).isNotNull();
  }

  @Test
  @DisplayName("A saturated executor fails the job instead of stranding it")
  void executorSaturationFailsTheJob() {
    AiGenerationJob job = queuedJob();

    // Deliberately not the shared application pool: filling that one depends
    // on whatever other tests left queued, which is order-dependent. This is
    // the real GenerationWorker with every collaborator wired in except the
    // executor, which is swapped for one that always rejects. The branch under
    // test is the production one.
    GenerationWorker saturatedWorker =
        new GenerationWorker(
            store, generationService, provider, validator, properties, rejectingExecutor());

    saturatedWorker.submit(job.getId());

    AiGenerationJob failed = jobs.findById(job.getId()).orElseThrow();
    assertThat(failed.getStatus()).isEqualTo(GenerationJobStatus.FAILED);
    assertThat(failed.getErrorCode()).isEqualTo("EXECUTOR_SATURATED");
    // Never left QUEUED: an unservable job must not silently wait forever.
    assertThat(failed.getCompletedAt()).isNotNull();
  }

  private Executor rejectingExecutor() {
    return command -> {
      throw new RejectedExecutionException("queue is full");
    };
  }
}