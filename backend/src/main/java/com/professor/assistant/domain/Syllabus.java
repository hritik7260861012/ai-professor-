package com.professor.assistant.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A syllabus document uploaded by a teacher for one (class, subject,
 * semester) placement.
 *
 * <p>Academic placement links to the Phase 3 entities rather than
 * duplicating them. {@code semesterId} mirrors the class's semester and is
 * validated in the service layer so it can never disagree.
 *
 * <p>The original file lives in private storage behind {@link
 * #storageKey}. {@link #originalFilename} is display metadata only and is
 * never used to build a filesystem path.
 */
@Entity
@Table(
    name = "syllabus",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_syllabus_class_subject_semester_version",
            columnNames = {"class_id", "subject_id", "semester_id", "version"}))
public class Syllabus {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "class_id", nullable = false)
  private AcademicClass academicClass;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subject_id", nullable = false)
  private Subject subject;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "semester_id", nullable = false)
  private Semester semester;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "uploaded_by", nullable = false)
  private User uploadedBy;

  @Column(name = "original_filename", nullable = false, length = 255)
  private String originalFilename;

  @Column(name = "content_type", nullable = false, length = 120)
  private String contentType;

  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  /** Random internal key. The only thing the storage layer accepts. */
  @Column(name = "storage_key", nullable = false, length = 120, unique = true)
  private String storageKey;

  @Column(name = "checksum_sha256", length = 64)
  private String checksumSha256;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private SyllabusStatus status;

  @Column(nullable = false)
  private Integer version;

  @Column(name = "is_current", nullable = false)
  private boolean current;

  // Mapped as TEXT by V4. Deliberately not @Lob: that would make Hibernate
  // expect a CLOB and fail ddl-auto=validate against the migration.
  @Column(name = "extracted_text")
  private String extractedText;

  /** Safe, teacher-facing summary. Never contains document content. */
  @Column(name = "extraction_error", length = 500)
  private String extractionError;

  @Column(name = "review_note", length = 1000)
  private String reviewNote;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reviewed_by")
  private User reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "uploaded_at", nullable = false, updatable = false)
  private Instant uploadedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(
      mappedBy = "syllabus",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("position ASC")
  private List<SyllabusUnit> units = new ArrayList<>();

  @OneToMany(
      mappedBy = "syllabus",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("position ASC")
  private List<SyllabusLearningOutcome> learningOutcomes = new ArrayList<>();

  protected Syllabus() {}

  public Syllabus(
      AcademicClass academicClass,
      Subject subject,
      Semester semester,
      User uploadedBy,
      String originalFilename,
      String contentType,
      Long fileSize,
      String storageKey,
      String checksumSha256,
      Integer version,
      boolean current) {
    this.academicClass = academicClass;
    this.subject = subject;
    this.semester = semester;
    this.uploadedBy = uploadedBy;
    this.originalFilename = originalFilename;
    this.contentType = contentType;
    this.fileSize = fileSize;
    this.storageKey = storageKey;
    this.checksumSha256 = checksumSha256;
    this.status = SyllabusStatus.UPLOADED;
    this.version = version;
    this.current = current;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.uploadedAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public AcademicClass getAcademicClass() {
    return academicClass;
  }

  public Subject getSubject() {
    return subject;
  }

  public Semester getSemester() {
    return semester;
  }

  public User getUploadedBy() {
    return uploadedBy;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public String getContentType() {
    return contentType;
  }

  public Long getFileSize() {
    return fileSize;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public String getChecksumSha256() {
    return checksumSha256;
  }

  public SyllabusStatus getStatus() {
    return status;
  }

  public void setStatus(SyllabusStatus status) {
    this.status = status;
  }

  public Integer getVersion() {
    return version;
  }

  public boolean isCurrent() {
    return current;
  }

  public void setCurrent(boolean current) {
    this.current = current;
  }

  public String getExtractedText() {
    return extractedText;
  }

  public void setExtractedText(String extractedText) {
    this.extractedText = extractedText;
  }

  public String getExtractionError() {
    return extractionError;
  }

  public void setExtractionError(String extractionError) {
    this.extractionError = extractionError;
  }

  public String getReviewNote() {
    return reviewNote;
  }

  public void setReviewNote(String reviewNote) {
    this.reviewNote = reviewNote;
  }

  public User getReviewedBy() {
    return reviewedBy;
  }

  public void setReviewedBy(User reviewedBy) {
    this.reviewedBy = reviewedBy;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public void setReviewedAt(Instant reviewedAt) {
    this.reviewedAt = reviewedAt;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public List<SyllabusUnit> getUnits() {
    return units;
  }

  public List<SyllabusLearningOutcome> getLearningOutcomes() {
    return learningOutcomes;
  }

  /** Replaces the whole unit tree (orphanRemoval cascades the deletes). */
  public void replaceUnits(List<SyllabusUnit> replacement) {
    units.clear();
    units.addAll(replacement);
    for (SyllabusUnit unit : units) {
      unit.attachTo(this);
    }
  }

  public void replaceLearningOutcomes(List<SyllabusLearningOutcome> replacement) {
    learningOutcomes.clear();
    learningOutcomes.addAll(replacement);
    for (SyllabusLearningOutcome outcome : learningOutcomes) {
      outcome.attachTo(this);
    }
  }
}