package com.professor.assistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * An asynchronous AI generation request.
 *
 * <p>Created by {@code ContentGenerationService} with status QUEUED and
 * handed to a bounded executor. The HTTP request returns immediately with
 * the id; the teacher polls for status.
 *
 * <p>{@code syllabusId} and {@code syllabusVersion} are captured at request
 * time, so the provenance of an artifact survives even if the syllabus is
 * later replaced by a newer version.
 */
@Entity
@Table(name = "ai_generation_job")
public class AiGenerationJob {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Always the authenticated principal; never supplied by a client. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "requested_by", nullable = false)
  private User requestedBy;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "syllabus_id", nullable = false)
  private Syllabus syllabus;

  @Column(name = "syllabus_version", nullable = false)
  private Integer syllabusVersion;

  /** Null when the request covers the whole approved syllabus. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "unit_id")
  private SyllabusUnit unit;

  @Enumerated(EnumType.STRING)
  @Column(name = "content_type", nullable = false, length = 40)
  private GenerationContentType contentType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private GenerationLanguage language;

  @Enumerated(EnumType.STRING)
  @Column(name = "detail_level", nullable = false, length = 20)
  private GenerationDetailLevel detailLevel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private GenerationDifficulty difficulty;

  @Column(name = "item_count", nullable = false)
  private Integer itemCount;

  @Column(length = 1000)
  private String instructions;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private GenerationJobStatus status;

  /** Bounded count of provider attempts actually made. */
  @Column(name = "attempt_count", nullable = false)
  private Integer attemptCount;

  /** Safe machine-readable failure category. Never a credential. */
  @Column(name = "error_code", length = 40)
  private String errorCode;

  /** Safe teacher-facing summary. Never the full prompt or the API key. */
  @Column(name = "error_message", length = 500)
  private String errorMessage;

  @Column(name = "provider_name", length = 40)
  private String providerName;

  @Column(name = "model_name", length = 80)
  private String modelName;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "content_id")
  private GeneratedContent content;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected AiGenerationJob() {}

  public AiGenerationJob(
      User requestedBy,
      Syllabus syllabus,
      SyllabusUnit unit,
      GenerationContentType contentType,
      GenerationLanguage language,
      GenerationDetailLevel detailLevel,
      GenerationDifficulty difficulty,
      Integer itemCount,
      String instructions) {
    this.requestedBy = requestedBy;
    this.syllabus = syllabus;
    this.syllabusVersion = syllabus.getVersion();
    this.unit = unit;
    this.contentType = contentType;
    this.language = language;
    this.detailLevel = detailLevel;
    this.difficulty = difficulty;
    this.itemCount = itemCount;
    this.instructions = instructions;
    this.status = GenerationJobStatus.QUEUED;
    this.attemptCount = 0;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public User getRequestedBy() {
    return requestedBy;
  }

  public Syllabus getSyllabus() {
    return syllabus;
  }

  public Integer getSyllabusVersion() {
    return syllabusVersion;
  }

  public SyllabusUnit getUnit() {
    return unit;
  }

  public Long getUnitId() {
    return unit == null ? null : unit.getId();
  }

  public GenerationContentType getContentType() {
    return contentType;
  }

  public GenerationLanguage getLanguage() {
    return language;
  }

  public GenerationDetailLevel getDetailLevel() {
    return detailLevel;
  }

  public GenerationDifficulty getDifficulty() {
    return difficulty;
  }

  public Integer getItemCount() {
    return itemCount;
  }

  public String getInstructions() {
    return instructions;
  }

  public GenerationJobStatus getStatus() {
    return status;
  }

  public void setStatus(GenerationJobStatus status) {
    this.status = status;
  }

  /**
   * Sets the RUNNING state in memory.
   *
   * <p>Execution claims the job through a conditional update instead, so this
   * is only used when a job's state is reconstructed rather than claimed.
   */
  public void markRunning(Instant now) {
    this.status = GenerationJobStatus.RUNNING;
    this.startedAt = now;
    this.attemptCount = this.attemptCount + 1;
    clearError();
  }

  public void markCompleted(GeneratedContent content, Instant now) {
    this.content = content;
    this.status = GenerationJobStatus.COMPLETED;
    this.completedAt = now;
    clearError();
  }

  public void markFailed(String errorCode, String errorMessage, Instant now) {
    this.status = GenerationJobStatus.FAILED;
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.completedAt = now;
  }

  public void markCancelled(String reason, Instant now) {
    this.status = GenerationJobStatus.CANCELLED;
    this.errorCode = "CANCELLED";
    this.errorMessage = reason;
    this.completedAt = now;
  }

  /** Returns a terminal job to QUEUED so it can be retried deliberately. */
  public void requeue() {
    this.status = GenerationJobStatus.QUEUED;
    this.errorCode = null;
    this.errorMessage = null;
    this.completedAt = null;
    this.startedAt = null;
  }

  private void clearError() {
    this.errorCode = null;
    this.errorMessage = null;
  }

  public Integer getAttemptCount() {
    return attemptCount;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public String getProviderName() {
    return providerName;
  }

  public void setProviderName(String providerName) {
    this.providerName = providerName;
  }

  public String getModelName() {
    return modelName;
  }

  public void setModelName(String modelName) {
    this.modelName = modelName;
  }

  public Long getContentId() {
    return content == null ? null : content.getId();
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /**
   * True once the syllabus this job was based on has been superseded.
   *
   * <p>See {@link GeneratedContent#isStale()}: a re-upload creates a new
   * syllabus row and demotes the old one, so {@code is_current} is the
   * signal that matters.
   */
  public boolean isStale() {
    if (syllabus == null) {
      return false;
    }
    if (!syllabus.isCurrent()) {
      return true;
    }
    return syllabus.getVersion() != null
        && !syllabus.getVersion().equals(this.syllabusVersion);
  }
}