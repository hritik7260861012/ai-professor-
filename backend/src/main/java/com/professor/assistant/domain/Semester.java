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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

/** A semester within an {@link AcademicYear}. */
@Entity
@Table(
    name = "semester",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_semester_year_number",
            columnNames = {"academic_year_id", "semester_number"}))
public class Semester {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "academic_year_id", nullable = false)
  private AcademicYear academicYear;

  @Column(nullable = false, length = 60)
  private String name;

  @Column(name = "semester_number", nullable = false)
  private Integer semesterNumber;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Semester() {}

  public Semester(
      AcademicYear academicYear,
      String name,
      Integer semesterNumber,
      LocalDate startDate,
      LocalDate endDate) {
    this.academicYear = academicYear;
    this.name = name;
    this.semesterNumber = semesterNumber;
    this.startDate = startDate;
    this.endDate = endDate;
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

  public AcademicYear getAcademicYear() {
    return academicYear;
  }

  public void setAcademicYear(AcademicYear academicYear) {
    this.academicYear = academicYear;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Integer getSemesterNumber() {
    return semesterNumber;
  }

  public void setSemesterNumber(Integer semesterNumber) {
    this.semesterNumber = semesterNumber;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}