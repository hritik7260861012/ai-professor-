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
 * A generated artifact plus its teacher-controlled review state.
 *
 * <p>Two independent gates decide whether a student can read this:
 *
 * <ol>
 *   <li>{@link #audience} must be {@link ContentAudience#STUDENT} — teacher
 *       study material and answer keys can never satisfy this, whatever the
 *       review status is.
 *   <li>{@link #reviewStatus} must be {@link ContentReviewStatus#APPROVED}
 *       <em>and</em> {@link #releasedToStudents} must be set.
 * </ol>
 *
 * <p>The large text is stored once here rather than duplicated per content
 * type; structured types additionally carry a machine-readable payload in
 * {@link #structuredJson}.
 */
@Entity
@Table(name = "generated_content")
public class GeneratedContent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Null for content a teacher creates or edits entirely by hand. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "job_id")
  private AiGenerationJob job;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "syllabus_id", nullable = false)
  private Syllabus syllabus;

  @Column(name = "syllabus_version", nullable = false)
  private Integer syllabusVersion;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "unit_id")
  private SyllabusUnit unit;

  @Enumerated(EnumType.STRING)
  @Column(name = "content_type", nullable = false, length = 40)
  private GenerationContentType contentType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private GenerationLanguage language;

  @Column(nullable = false, length = 300)
  private String title;

  /** Human-readable body: notes, study material, revision summary. */
  @Column
  private String body;

  /** Machine-readable payload for MCQs, slides and question sets. */
  @Column(name = "structured_json")
  private String structuredJson;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ContentAudience audience;

  /** True when the body contains an answer key or marking guidance. */
  @Column(name = "contains_answer_key", nullable = false)
  private boolean answerKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "review_status", nullable = false, length = 20)
  private ContentReviewStatus reviewStatus;

  /** Teacher-set switch. Required, alongside APPROVED, before students see it. */
  @Column(name = "released_to_students", nullable = false)
  private boolean releasedToStudents;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reviewed_by")
  private User reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "rejection_reason", length = 500)
  private String rejectionReason;

  @Column(name = "provider_name", length = 40)
  private String providerName;

  @Column(name = "model_name", length = 80)
  private String modelName;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected GeneratedContent() {}

  public GeneratedContent(
      AiGenerationJob job,
      Syllabus syllabus,
      SyllabusUnit unit,
      GenerationContentType contentType,
      GenerationLanguage language,
      String title,
      String body,
      String structuredJson) {
    this.job = job;
    this.syllabus = syllabus;
    this.syllabusVersion = syllabus.getVersion();
    this.unit = unit;
    this.contentType = contentType;
    this.language = language;
    this.title = title;
    this.body = body;
    this.structuredJson = structuredJson;
    this.audience = contentType.audience();
    this.answerKey = contentType.mayContainAnswerKey();
    this.reviewStatus = ContentReviewStatus.DRAFT;
    // Teacher-only material can never be released; a CHECK constraint
    // enforces this in the database too.
    this.releasedToStudents = false;
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

  public AiGenerationJob getJob() {
    return job;
  }

  public Long getJobId() {
    return job == null ? null : job.getId();
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

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public String getStructuredJson() {
    return structuredJson;
  }

  public void setStructuredJson(String structuredJson) {
    this.structuredJson = structuredJson;
  }

  public ContentAudience getAudience() {
    return audience;
  }

  public boolean hasAnswerKey() {
    return answerKey;
  }

  public void setAnswerKey(boolean answerKey) {
    this.answerKey = answerKey;
  }

  public ContentReviewStatus getReviewStatus() {
    return reviewStatus;
  }

  public void setReviewStatus(ContentReviewStatus reviewStatus) {
    this.reviewStatus = reviewStatus;
  }

  public boolean isReleasedToStudents() {
    return releasedToStudents;
  }

  /** Refuses to release teacher-only material, whatever the caller wants. */
  public void setReleasedToStudents(boolean released) {
    this.releasedToStudents = released && audience == ContentAudience.STUDENT;
  }

  public User getReviewedBy() {
    return reviewedBy;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public void setRejectionReason(String rejectionReason) {
    this.rejectionReason = rejectionReason;
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

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /**
   * True when the source syllabus has been superseded by a newer version.
   *
   * <p>Derived rather than stored, so it is correct immediately after a
   * re-upload without needing a background sweep.
   *
   * <p>Uploading a new version creates a <em>new</em> syllabus row and
   * demotes the previous one to {@code is_current = false}. This row's own
   * {@code version} therefore never changes, so demotion is the signal
   * that matters; the version comparison is kept as a defensive check for
   * any future path that mutates the row in place.
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

  /** The status a client should see, folding staleness into the stored value. */
  public ContentReviewStatus effectiveReviewStatus() {
    return isStale() ? ContentReviewStatus.OUTDATED : reviewStatus;
  }

  public void approve(User reviewer, boolean releaseToStudents, Instant now) {
    this.reviewStatus = ContentReviewStatus.APPROVED;
    this.reviewedBy = reviewer;
    this.reviewedAt = now;
    this.rejectionReason = null;
    setReleasedToStudents(releaseToStudents);
  }

  public void reject(User reviewer, String reason, Instant now) {
    this.reviewStatus = ContentReviewStatus.REJECTED;
    this.reviewedBy = reviewer;
    this.reviewedAt = now;
    this.rejectionReason = reason;
    this.releasedToStudents = false;
  }

  /**
   * Whether a student may read this artifact. Both gates must pass:
   * student audience, approved, and explicitly released.
   */
  public boolean isVisibleToStudents() {
    return audience == ContentAudience.STUDENT
        && reviewStatus == ContentReviewStatus.APPROVED
        && releasedToStudents
        && !isStale();
  }
}