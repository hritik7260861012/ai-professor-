package com.professor.assistant.ai;

import com.professor.assistant.config.AiGenerationProperties;
import com.professor.assistant.domain.AiGenerationJob;
import com.professor.assistant.domain.GeneratedContent;
import com.professor.assistant.domain.GenerationJobStatus;
import com.professor.assistant.domain.Syllabus;
import com.professor.assistant.repository.AiGenerationJobRepository;
import com.professor.assistant.repository.GeneratedContentRepository;
import com.professor.assistant.repository.SyllabusRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns every job/content state transition.
 *
 * <p>Split out of {@link GenerationWorker} deliberately: {@code @Transactional}
 * works through a proxy, so calling those methods from inside the worker
 * would self-invoke and silently run without a transaction. Keeping them
 * here means each transition really does commit in its own short
 * transaction, and no transaction is ever held across the provider call.
 */
@Component
public class GenerationJobStore {

  private final AiGenerationJobRepository jobs;
  private final GeneratedContentRepository contents;
  private final SyllabusRepository syllabi;
  private final AiGenerationProperties properties;

  public GenerationJobStore(
      AiGenerationJobRepository jobs,
      GeneratedContentRepository contents,
      SyllabusRepository syllabi,
      AiGenerationProperties properties) {
    this.jobs = jobs;
    this.contents = contents;
    this.syllabi = syllabi;
    this.properties = properties;
  }

  /**
   * Attempts to claim a QUEUED job for execution.
   *
   * <p>The claim is a single conditional UPDATE, so a job can never be
   * executed twice even if a retry, a duplicate submit and a scheduled
   * sweep race for it.
   *
   * @return true when this caller won the claim and should run the job
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean claimForExecution(Long jobId) {
    return jobs.claimQueued(jobId, Instant.now()) == 1;
  }

  /**
   * Returns a claimed job to QUEUED so the next attempt can claim it again.
   *
   * <p>Without this a retried attempt would find its own job already
   * RUNNING and refuse to run, turning every retryable failure into a
   * silent no-op.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void releaseForRetry(Long jobId) {
    jobs
        .findById(jobId)
        .ifPresent(
            job -> {
              if (job.getStatus() == GenerationJobStatus.RUNNING) {
                job.setStatus(GenerationJobStatus.QUEUED);
                jobs.save(job);
              }
            });
  }

  /**
   * Stores the artifact and closes the job.
   *
   * @return the persisted content id
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long persistContent(
      Long jobId, GenerationResult result, String validatedStructuredJson) {
    AiGenerationJob job = requireJob(jobId);
    Syllabus syllabus =
        syllabi
            .findById(job.getSyllabus().getId())
            .orElseThrow(() -> new IllegalStateException("Source syllabus missing"));

    GeneratedContent content =
        new GeneratedContent(
            job,
            syllabus,
            job.getUnit(),
            job.getContentType(),
            job.getLanguage(),
            titleFor(job, syllabus),
            truncate(result.body()),
            validatedStructuredJson);
    content.setProviderName(result.providerName());
    content.setModelName(result.modelName());
    GeneratedContent saved = contents.save(content);

    job.setProviderName(result.providerName());
    job.setModelName(result.modelName());
    job.markCompleted(saved, Instant.now());
    jobs.save(job);
    return saved.getId();
  }

  /**
   * Records a terminal failure.
   *
   * <p>Never overwrites a job that already completed, so a late error
   * cannot orphan content that was successfully stored.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failJob(Long jobId, String errorCode, String message) {
    jobs
        .findById(jobId)
        .ifPresent(
            job -> {
              if (job.getStatus() != GenerationJobStatus.COMPLETED) {
                job.markFailed(errorCode, clip(message), Instant.now());
                jobs.save(job);
              }
            });
  }

  @Transactional(readOnly = true)
  public AiGenerationJob requireJob(Long jobId) {
    return jobs
        .findById(jobId)
        .orElseThrow(() -> new IllegalStateException("Generation job not found: " + jobId));
  }

  private String titleFor(AiGenerationJob job, Syllabus syllabus) {
    String scope = job.getUnit() == null ? syllabus.getSubject().getName() : job.getUnit().getTitle();
    return clip(scope + " — " + job.getContentType().name().replace('_', ' '));
  }

  private String truncate(String body) {
    if (body == null) {
      return null;
    }
    int limit = properties.getMaxContentCharacters();
    if (body.length() <= limit) {
      return body;
    }
    return body.substring(0, limit) + "\n\n[Content truncated at the configured limit]";
  }

  private String clip(String value) {
    if (value == null) {
      return null;
    }
    return value.length() > 500 ? value.substring(0, 500) : value;
  }
}