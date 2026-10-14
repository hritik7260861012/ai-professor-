package com.professor.assistant.ai;

import com.professor.assistant.config.AiGenerationProperties;
import com.professor.assistant.domain.AiGenerationJob;
import com.professor.assistant.domain.GenerationJobStatus;
import com.professor.assistant.repository.AiGenerationJobRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles jobs abandoned by a crash or restart.
 *
 * <p>Without this, a job that was RUNNING when the process died would stay
 * RUNNING forever and permanently consume the caller's concurrency slot. On
 * startup any job still marked RUNNING or QUEUED beyond the stale threshold
 * is moved to CANCELLED so it can be retried deliberately.
 *
 * <p>Nothing is retried automatically: a cancelled job costs nothing, and
 * re-running it could spend real provider quota behind the user's back.
 */
@Component
public class StaleJobReconciler {

  private static final Logger log = LoggerFactory.getLogger(StaleJobReconciler.class);

  private final AiGenerationJobRepository jobs;
  private final AiGenerationProperties properties;

  public StaleJobReconciler(AiGenerationJobRepository jobs, AiGenerationProperties properties) {
    this.jobs = jobs;
    this.properties = properties;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void reconcileOnStartup() {
    int cancelled = reconcileStaleJobs();
    if (cancelled > 0) {
      log.warn("Cancelled {} generation job(s) left in flight by a previous run.", cancelled);
    }
  }

  /**
   * @return how many jobs were moved to CANCELLED
   */
  @Transactional
  public int reconcileStaleJobs() {
    Instant cutoff =
        Instant.now().minus(Math.max(1, properties.getStaleJobMinutes()), ChronoUnit.MINUTES);

    // A RUNNING job can only be stale once it has exceeded the threshold.
    List<AiGenerationJob> staleRunning =
        jobs.findStaleJobs(GenerationJobStatus.RUNNING, cutoff);

    // A QUEUED job older than the threshold never started: the executor was
    // lost across a restart.
    List<AiGenerationJob> staleQueued =
        jobs.findStaleJobs(GenerationJobStatus.QUEUED, cutoff);

    int count = 0;
    Instant now = Instant.now();
    for (AiGenerationJob job : staleRunning) {
      job.markCancelled(
          "Interrupted by a server restart before it finished. Retry if you still need it.",
          now);
      jobs.save(job);
      count++;
    }
    for (AiGenerationJob job : staleQueued) {
      job.markCancelled(
          "Queued work was lost during a server restart. Retry if you still need it.", now);
      jobs.save(job);
      count++;
    }
    return count;
  }
}