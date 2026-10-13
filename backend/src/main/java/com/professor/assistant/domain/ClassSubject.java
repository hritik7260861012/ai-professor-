package com.professor.assistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * Join entity mapping a {@link Subject} onto an {@link AcademicClass} for a
 * given {@link Semester}.
 *
 * <p>{@code semesterId} is denormalised from the owning class so that
 * "subjects offered in semester X" is a single-index lookup. The service
 * layer always derives it from the class, so it can never disagree with
 * {@code academic_class.semester_id}.
 */
@Entity
@Table(
    name = "class_subject",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_class_subject",
            columnNames = {"class_id", "subject_id"}))
public class ClassSubject {

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

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ClassSubject() {}

  public ClassSubject(AcademicClass academicClass, Subject subject) {
    this.academicClass = academicClass;
    this.subject = subject;
    // Always derived from the class so the denormalised column stays true.
    this.semester = academicClass.getSemester();
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
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

  public Instant getCreatedAt() {
    return createdAt;
  }
}