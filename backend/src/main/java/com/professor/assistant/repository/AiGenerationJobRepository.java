package com.professor.assistant.repository;

import com.professor.assistant.domain.AiGenerationJob;
import com.professor.assistant.domain.GenerationJobStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiGenerationJobRepository extends JpaRepository<AiGenerationJob, Long> {

  /**
   * Atomically claims a QUEUED job for execution.
   *
   * <p>The {@code WHERE status = QUEUED} guard is what makes a job
   * impossible to run twice: two workers racing the same job both issue
   * this, but only the first one affects a row, and only that one is told
   * it won.
   *
   * @return 1 if this caller claimed the job, 0 if someone else already had it
   */
  @org.springframework.data.jpa.repository.Modifying
  @Query(
      """
      update AiGenerationJob j
         set j.status = 'RUNNING', j.startedAt = :now, j.updatedAt = :now,
             j.attemptCount = j.attemptCount + 1,
             j.errorCode = null, j.errorMessage = null
       where j.id = :id and j.status = 'QUEUED'
      """)
  int claimQueued(@Param("id") Long id, @Param("now") java.time.Instant now);

  Page<AiGenerationJob> findAllByRequestedByIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

  List<AiGenerationJob> findAllByRequestedByIdOrderByCreatedAtDesc(Long userId);

  /** In-flight jobs, used to enforce per-user concurrency. */
  List<AiGenerationJob> findAllByRequestedByIdAndStatusIn(
      Long userId, Collection<GenerationJobStatus> statuses);

  /** Daily quota count, derived from the database rather than memory. */
  @Query(
      """
      select count(j) from AiGenerationJob j
      where j.requestedBy.id = :userId and j.createdAt >= :since
      """)
  long countJobsSince(@Param("userId") Long userId, @Param("since") Instant since);

  /** Jobs left mid-flight by a crash or restart. */
  @Query(
      """
      select j from AiGenerationJob j
      where j.status = :status and j.updatedAt < :before
      """)
  List<AiGenerationJob> findStaleJobs(
      @Param("status") GenerationJobStatus status, @Param("before") Instant before);

  /** Artifacts this user generated, for the workspace history list. */
  @Query(
      """
      select c from GeneratedContent c
      where c.syllabus.id in :syllabusIds
      order by c.createdAt desc
      """)
  List<com.professor.assistant.domain.GeneratedContent> findBySyllabusIds(
      @Param("syllabusIds") Collection<Long> syllabusIds);

  /** Recent job matching the same request, for duplicate suppression. */
  @Query(
      """
      select j from AiGenerationJob j
      where j.requestedBy.id = :userId
        and j.syllabus.id = :syllabusId
        and ((:unitId is null and j.unit is null) or j.unit.id = :unitId)
        and j.contentType = :contentType
        and j.language = :language
        and j.detailLevel = :detailLevel
        and j.difficulty = :difficulty
        and j.itemCount = :itemCount
        and j.createdAt >= :since
      order by j.createdAt desc
      """)
  List<AiGenerationJob> findRecentIdentical(
      @Param("userId") Long userId,
      @Param("syllabusId") Long syllabusId,
      @Param("unitId") Long unitId,
      @Param("contentType") com.professor.assistant.domain.GenerationContentType contentType,
      @Param("language") com.professor.assistant.domain.GenerationLanguage language,
      @Param("detailLevel") com.professor.assistant.domain.GenerationDetailLevel detailLevel,
      @Param("difficulty") com.professor.assistant.domain.GenerationDifficulty difficulty,
      @Param("itemCount") Integer itemCount,
      @Param("since") Instant since);
}