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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * Links a STUDENT account to an {@link AcademicClass}. This is the authority
 * for what a student is allowed to see.
 */
@Entity
@Table(
    name = "enrollment",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_enrollment_student_class",
            columnNames = {"student_id", "class_id"}))
public class Enrollment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_id", nullable = false)
  private User student;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "class_id", nullable = false)
  private AcademicClass academicClass;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EnrollmentStatus status;

  @Column(name = "enrolled_at", nullable = false, updatable = false)
  private Instant enrolledAt;

  protected Enrollment() {}

  public Enrollment(User student, AcademicClass academicClass) {
    this.student = student;
    this.academicClass = academicClass;
    this.status = EnrollmentStatus.ENROLLED;
  }

  @PrePersist
  void onCreate() {
    this.enrolledAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public User getStudent() {
    return student;
  }

  public AcademicClass getAcademicClass() {
    return academicClass;
  }

  public EnrollmentStatus getStatus() {
    return status;
  }

  public void setStatus(EnrollmentStatus status) {
    this.status = status;
  }

  public Instant getEnrolledAt() {
    return enrolledAt;
  }
}