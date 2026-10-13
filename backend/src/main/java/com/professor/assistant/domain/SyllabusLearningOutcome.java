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
import java.time.Instant;

/** A learning outcome, attached to the syllabus or to one specific unit. */
@Entity
@Table(name = "syllabus_learning_outcome")
public class SyllabusLearningOutcome {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "syllabus_id", nullable = false)
  private Syllabus syllabus;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "unit_id")
  private SyllabusUnit unit;

  @Column(nullable = false, length = 1000)
  private String statement;

  @Column(nullable = false)
  private Integer position;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected SyllabusLearningOutcome() {}

  public SyllabusLearningOutcome(String statement, int position) {
    this.statement = statement;
    this.position = position;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Syllabus getSyllabus() {
    return syllabus;
  }

  void attachTo(Syllabus owner) {
    this.syllabus = owner;
  }

  public SyllabusUnit getUnit() {
    return unit;
  }

  public void setUnit(SyllabusUnit unit) {
    this.unit = unit;
  }

  public String getStatement() {
    return statement;
  }

  public void setStatement(String statement) {
    this.statement = statement;
  }

  public Integer getPosition() {
    return position;
  }

  public void setPosition(int position) {
    this.position = position;
  }
}