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
 * Links a TEACHER account to a specific subject within a specific class and
 * semester. A teacher may hold any number of assignments; this table is the
 * authority for what a teacher is allowed to see.
 */
@Entity
@Table(
    name = "teaching_assignment",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_teaching_assignment",
            columnNames = {"teacher_id", "class_id", "subject_id", "semester_id"}))
public class TeachingAssignment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "teacher_id", nullable = false)
  private User teacher;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "class_id", nullable = false)
  private AcademicClass academicClass;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subject_id", nullable = false)
  private Subject subject;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "semester_id", nullable = false)
  private Semester semester;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assigned_by")
  private User assignedBy;

  @Column(name = "assigned_at", nullable = false, updatable = false)
  private Instant assignedAt;

  protected TeachingAssignment() {}

  public TeachingAssignment(
      User teacher,
      AcademicClass academicClass,
      Subject subject,
      Semester semester,
      User assignedBy) {
    this.teacher = teacher;
    this.academicClass = academicClass;
    this.subject = subject;
    this.semester = semester;
    this.assignedBy = assignedBy;
  }

  @PrePersist
  void onCreate() {
    this.assignedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public User getTeacher() {
    return teacher;
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

  public User getAssignedBy() {
    return assignedBy;
  }

  public Instant getAssignedAt() {
    return assignedAt;
  }
}