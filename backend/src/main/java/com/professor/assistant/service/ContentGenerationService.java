package com.professor.assistant.service;

import com.professor.assistant.ai.ContentGenerationProvider;
import com.professor.assistant.ai.GenerationRequest;
import com.professor.assistant.ai.SyllabusPromptBuilder;
import com.professor.assistant.config.AiGenerationProperties;
import com.professor.assistant.domain.AiGenerationJob;
import com.professor.assistant.domain.EnrollmentStatus;
import com.professor.assistant.domain.GenerationContentType;
import com.professor.assistant.domain.GenerationJobStatus;
import com.professor.assistant.domain.Role;
import com.professor.assistant.domain.Syllabus;
import com.professor.assistant.domain.SyllabusStatus;
import com.professor.assistant.domain.SyllabusUnit;
import com.professor.assistant.domain.User;
import com.professor.assistant.dto.GenerationRequestDto;
import com.professor.assistant.dto.GenerationJobDto;
import com.professor.assistant.dto.PageResponse;
import com.professor.assistant.exception.AiNotConfiguredException;
import com.professor.assistant.exception.ConflictException;
import com.professor.assistant.exception.GenerationQuotaExceededException;
import com.professor.assistant.exception.InvalidGenerationRequestException;
import com.professor.assistant.exception.ResourceNotFoundException;
import com.professor.assistant.repository.AiGenerationJobRepository;
import com.professor.assistant.repository.EnrollmentRepository;
import com.professor.assistant.repository.SyllabusRepository;
import com.professor.assistant.repository.TeachingAssignmentRepository;
import com.professor.assistant.repository.UserRepository;
import com.professor.assistant.security.AppUserDetails;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepts generation requests: validates options, checks authorization and
 * quotas, and builds the grounded request the worker will execute.
 *
 * <p>Authorization rules, matching the Phase 3/4 convention that an
 * out-of-scope record is a 404 rather than a 403:
 *
 * <ul>
 *   <li>The source syllabus must be APPROVED.
 *   <li>The caller must be an ADMIN, or a TEACHER who uploaded it and still
 *       holds the teaching assignment.
 *   <li>Student identity is never accepted from the client.
 * </ul>
 *
 * <p>Job creation commits in its own short transaction
 * ({@link Propagation#REQUIRES_NEW}); the provider call happens later on an
 * executor thread, never inside a database transaction.
 */
@Service
public class ContentGenerationService {

  private final AiGenerationJobRepository jobs;
  private final SyllabusRepository syllabi;
  private final TeachingAssignmentRepository assignments;
  private final EnrollmentRepository enrollments;
  private final UserRepository users;
  private final ContentGenerationProvider provider;
  private final SyllabusPromptBuilder promptBuilder;
  private final AiGenerationProperties properties;

  public ContentGenerationService(
      AiGenerationJobRepository jobs,
      SyllabusRepository syllabi,
      TeachingAssignmentRepository assignments,
      EnrollmentRepository enrollments,
      UserRepository users,
      ContentGenerationProvider provider,
      SyllabusPromptBuilder promptBuilder,
      AiGenerationProperties properties) {
    this.jobs = jobs;
    this.syllabi = syllabi;
    this.assignments = assignments;
    this.enrollments = enrollments;
    this.users = users;
    this.provider = provider;
    this.promptBuilder = promptBuilder;
    this.properties = properties;
  }

  /**
   * Validates and persists a generation job.
   *
   * @return the newly created job, or an existing identical one when the
   *     request falls inside the duplicate-suppression window
   */
  @Transactional
  public GenerationJobDto submit(AppUserDetails principal, GenerationRequestDto request) {
    GenerationRequestDto validated = validate(request);

    // Missing configuration is reported before anything is persisted, so a
    // misconfigured deployment never accumulates dead QUEUED jobs.
    if (!provider.isAvailable()) {
      throw new AiNotConfiguredException(
          "AI content generation is not configured on this server. "
              + "An administrator must set GEMINI_API_KEY.");
    }

    Syllabus syllabus = requireAuthorizedApprovedSyllabus(principal, validated.syllabusId());
    SyllabusUnit unit = resolveUnit(syllabus, validated.unitId());

    // Duplicate suppression runs before the quota guards: an identical
    // resubmission must be answered with the existing job rather than
    // rejected, because it costs nothing and is what stops a double-click
    // or a retrying client from buying a second generation.
    AiGenerationJob duplicate = findDuplicate(principal.getId(), validated, syllabus, unit);
    if (duplicate != null) {
      return toDto(duplicate, null);
    }

    enforceQuotas(principal.getId());

    User requester =
        users.findById(principal.getId()).orElseThrow(() -> ResourceNotFoundException.of("User", principal.getId()));

    AiGenerationJob job =
        new AiGenerationJob(
            requester,
            syllabus,
            unit,
            validated.contentType(),
            validated.language(),
            validated.detailLevel(),
            validated.difficulty(),
            validated.itemCount(),
            validated.instructions());
    job.setProviderName(provider.name());
    job.setModelName(provider.model());

    return toDto(jobs.save(job), null);
  }

  @Transactional(readOnly = true)
  public PageResponse<GenerationJobDto> listJobs(AppUserDetails principal, Pageable pageable) {
    Page<AiGenerationJob> page =
        jobs.findAllByRequestedByIdOrderByCreatedAtDesc(principal.getId(), pageable);
    return PageResponse.from(page.map(job -> toDto(job, null)));
  }

  @Transactional(readOnly = true)
  public GenerationJobDto getJob(AppUserDetails principal, Long jobId) {
    return toDto(requireOwnedJob(principal, jobId), null);
  }

  /**
   * The grounded payload the worker sends to the provider.
   *
   * <p>Takes an id rather than an entity: the caller is on a worker thread
   * with no transaction open, so the job and its whole unit tree must be
   * loaded inside this one.
   */
  @Transactional(readOnly = true)
  public GenerationRequest buildProviderRequest(Long jobId) {
    Syllabus syllabus = syllabi.findById(findSyllabusId(jobId)).orElseThrow();
    return buildProviderRequest(loadJobForPrompt(jobId), syllabus);
  }

  private Long findSyllabusId(Long jobId) {
    return jobs.findById(jobId).map(job -> job.getSyllabus().getId()).orElse(null);
  }

  /** Loads the job and its syllabus within the caller's transaction. */
  private AiGenerationJob loadJobForPrompt(Long jobId) {
    return jobs.findById(jobId).orElseThrow(() -> new IllegalStateException("Job vanished: " + jobId));
  }

  private GenerationRequest buildProviderRequest(AiGenerationJob job, Syllabus syllabus) {
    SyllabusUnit unit = job.getUnit();

    List<String> topicLines =
        unit == null
            ? syllabus.getUnits().stream()
                .flatMap(u -> u.getTopics().stream())
                .map(topic -> SyllabusPromptBuilder.renderTopicLine(topic.getTitle(), subTitles(topic)))
                .toList()
            : unit.getTopics().stream()
                .map(topic -> SyllabusPromptBuilder.renderTopicLine(topic.getTitle(), subTitles(topic)))
                .toList();

    List<String> outcomes = unit == null ? syllabusOutcomes(syllabus) : unitOutcomes(unit, syllabus);

    return new GenerationRequest(
        syllabus.getSubject().getCode(),
        syllabus.getSubject().getName(),
        syllabus.getAcademicClass().getCode(),
        job.getSyllabusVersion(),
        unit == null ? null : unit.getTitle(),
        topicLines,
        outcomes,
        job.getContentType(),
        job.getLanguage(),
        job.getDetailLevel(),
        job.getDifficulty(),
        job.getItemCount(),
        job.getContentType().mayContainAnswerKey(),
        job.getInstructions());
  }

  private List<String> subTitles(
      com.professor.assistant.domain.SyllabusTopic topic) {
    return topic.getSubtopics().stream().map(com.professor.assistant.domain.SyllabusSubtopic::getTitle).toList();
  }

  private List<String> syllabusOutcomes(Syllabus syllabus) {
    return syllabus.getLearningOutcomes().stream()
        .map(com.professor.assistant.domain.SyllabusLearningOutcome::getStatement)
        .toList();
  }

  private List<String> unitOutcomes(SyllabusUnit unit, Syllabus syllabus) {
    return syllabus.getLearningOutcomes().stream()
        .filter(outcome -> outcome.getUnit() != null && outcome.getUnit().getId().equals(unit.getId()))
        .map(com.professor.assistant.domain.SyllabusLearningOutcome::getStatement)
        .toList();
  }

  /* ------------------------------- helpers --------------------------------- */

  /**
   * Backend validation of every option. The frontend validates too, but
   * that is a convenience, never the authority.
   */
  GenerationRequestDto validate(GenerationRequestDto request) {
    if (request.syllabusId() == null) {
      throw new InvalidGenerationRequestException("A syllabus is required");
    }
    GenerationContentType type = request.contentType();
    if (type == null) {
      throw new InvalidGenerationRequestException("A content type is required");
    }

    int itemCount = request.itemCount() == null ? (type.isCountable() ? 5 : 1) : request.itemCount();
    if (itemCount < 1) {
      throw new InvalidGenerationRequestException("Number of items must be at least 1");
    }
    if (type.isCountable() && itemCount > properties.getMaxItems()) {
      throw new InvalidGenerationRequestException(
          "Number of items must be at most " + properties.getMaxItems());
    }
    // Notes and study material are not countable; force a stable value so
    // duplicate suppression is not defeated by a meaningless difference.
    if (!type.isCountable()) {
      itemCount = 1;
    }

    String instructions = request.instructions();
    if (instructions != null && !instructions.isBlank()) {
      instructions = instructions.strip();
      if (instructions.length() > properties.getMaxInstructionLength()) {
        throw new InvalidGenerationRequestException(
            "Instructions must be at most "
                + properties.getMaxInstructionLength()
                + " characters");
      }
    } else {
      instructions = null;
    }

    return new GenerationRequestDto(
        request.syllabusId(),
        request.unitId(),
        type,
        request.language() == null ? com.professor.assistant.domain.GenerationLanguage.ENGLISH : request.language(),
        request.detailLevel() == null
            ? com.professor.assistant.domain.GenerationDetailLevel.STANDARD
            : request.detailLevel(),
        request.difficulty() == null
            ? com.professor.assistant.domain.GenerationDifficulty.INTERMEDIATE
            : request.difficulty(),
        itemCount,
        request.includeAnswerKey() != null && request.includeAnswerKey(),
        instructions);
  }

  /**
   * Generation is grounded in an approved syllabus the caller is
   * authorised for. Everything else is treated as non-existent.
   */
  Syllabus requireAuthorizedApprovedSyllabus(AppUserDetails principal, Long syllabusId) {
    Syllabus syllabus =
        syllabi.findById(syllabusId).orElseThrow(() -> ResourceNotFoundException.of("Syllabus", syllabusId));

    if (principal.getRole() == Role.ADMIN) {
      requireApproved(syllabus);
      return syllabus;
    }
    if (principal.getRole() != Role.TEACHER) {
      throw ResourceNotFoundException.of("Syllabus", syllabusId);
    }
    if (!syllabus.getUploadedBy().getId().equals(principal.getId())) {
      throw ResourceNotFoundException.of("Syllabus", syllabusId);
    }
    if (!assignments.existsByTeacherIdAndAcademicClassIdAndSubjectIdAndSemesterId(
        principal.getId(),
        syllabus.getAcademicClass().getId(),
        syllabus.getSubject().getId(),
        syllabus.getSemester().getId())) {
      throw ResourceNotFoundException.of("Syllabus", syllabusId);
    }
    requireApproved(syllabus);
    return syllabus;
  }

  private void requireApproved(Syllabus syllabus) {
    if (syllabus.getStatus() != SyllabusStatus.APPROVED) {
      // An unapproved or rejected syllabus is not a generation source.
      throw new ConflictException(
          "Only an approved syllabus can be used as a source. This one is "
              + syllabus.getStatus()
              + ".");
    }
  }

  private SyllabusUnit resolveUnit(Syllabus syllabus, Long unitId) {
    if (unitId == null) {
      return null;
    }
    return syllabus.getUnits().stream()
        .filter(unit -> unit.getId().equals(unitId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("That unit does not belong to this syllabus"));
  }

  /** Concurrency and daily-quota guards, counted from the database. */
  void enforceQuotas(Long userId) {
    long active =
        jobs.findAllByRequestedByIdAndStatusIn(
                userId, List.of(GenerationJobStatus.QUEUED, GenerationJobStatus.RUNNING))
            .size();
    if (active >= properties.getMaxConcurrentJobsPerUser()) {
      throw new GenerationQuotaExceededException(
          "CONCURRENCY_LIMIT",
          "You already have "
              + active
              + " generation(s) in progress. Wait for it to finish before starting another.");
    }

    long today = jobs.countJobsSince(userId, startOfToday());
    if (today >= properties.getDailyQuotaPerUser()) {
      throw new GenerationQuotaExceededException(
          "DAILY_QUOTA_EXCEEDED",
          "You have reached the daily generation limit of "
              + properties.getDailyQuotaPerUser()
              + ". Try again tomorrow.");
    }
  }

  private Instant startOfToday() {
    return LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
  }

  /**
   * Suppresses an identical request fired within the configured window,
   * so a double-click or a retrying client does not buy two generations.
   */
  private AiGenerationJob findDuplicate(
      Long userId, GenerationRequestDto request, Syllabus syllabus, SyllabusUnit unit) {
    Instant since =
        Instant.now().minusSeconds(properties.getDuplicateWindowSeconds());
    List<AiGenerationJob> matches =
        jobs.findRecentIdentical(
            userId,
            syllabus.getId(),
            unit == null ? null : unit.getId(),
            request.contentType(),
            request.language(),
            request.detailLevel(),
            request.difficulty(),
            request.itemCount(),
            since);
    if (matches.isEmpty()) {
      return null;
    }
    AiGenerationJob existing = matches.get(0);
    // Only reuse a job that did not already fail terminally.
    if (existing.getStatus() == GenerationJobStatus.FAILED
        || existing.getStatus() == GenerationJobStatus.CANCELLED) {
      return null;
    }
    return existing;
  }

  /**
   * Only a failed or cancelled job may be retried. Refusing COMPLETED jobs
   * is what stops a "retry" from paying for a second generation.
   */
  @Transactional
  public AiGenerationJob requireRetryableJob(AppUserDetails principal, Long jobId) {
    AiGenerationJob job = requireOwnedJob(principal, jobId);
    if (job.getStatus() != GenerationJobStatus.FAILED
        && job.getStatus() != GenerationJobStatus.CANCELLED) {
      throw new ConflictException(
          "Only a failed or cancelled job can be retried. This one is "
              + job.getStatus()
              + ".");
    }
    return job;
  }

  /** Returns a job to QUEUED so the worker can pick it up again. */
  @Transactional
  public GenerationJobDto requeue(AiGenerationJob job) {
    job.requeue();
    return toDto(jobs.save(job), null);
  }

  AiGenerationJob requireOwnedJob(AppUserDetails principal, Long jobId) {
    AiGenerationJob job =
        jobs.findById(jobId).orElseThrow(() -> ResourceNotFoundException.of("Generation job", jobId));
    if (principal.getRole() == Role.ADMIN) {
      return job;
    }
    if (principal.getRole() != Role.TEACHER
        || !job.getRequestedBy().getId().equals(principal.getId())) {
      throw ResourceNotFoundException.of("Generation job", jobId);
    }
    return job;
  }

  GenerationJobDto toDto(AiGenerationJob job, String note) {
    return new GenerationJobDto(
        job.getId(),
        job.getSyllabus().getId(),
        job.getSyllabusVersion(),
        job.getUnitId(),
        job.getUnit() == null ? null : job.getUnit().getTitle(),
        job.getContentType(),
        job.getLanguage(),
        job.getDetailLevel(),
        job.getDifficulty(),
        job.getItemCount(),
        job.getInstructions(),
        job.getStatus(),
        job.getAttemptCount(),
        job.getErrorCode(),
        job.getErrorMessage(),
        job.getProviderName(),
        job.getModelName(),
        job.getContentId(),
        job.isStale(),
        job.getCreatedAt(),
        job.getStartedAt(),
        job.getCompletedAt(),
        note);
  }

  /** Exposed for the worker and for tests. */
  boolean isEnrolled(Long studentId, Long classId) {
    return enrollments
        .findByStudentIdAndAcademicClassId(studentId, classId)
        .map(enrollment -> enrollment.getStatus() == EnrollmentStatus.ENROLLED)
        .orElse(false);
  }

  ContentGenerationProvider provider() {
    return provider;
  }

  SyllabusPromptBuilder prompts() {
    return promptBuilder;
  }
}