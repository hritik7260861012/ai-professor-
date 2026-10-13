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

/** A subtopic within a {@link SyllabusTopic}. */
@Entity
@Table(name = "syllabus_subtopic")
public class SyllabusSubtopic {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "syllabus_id", nullable = false)
  private Syllabus syllabus;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "topic_id", nullable = false)
  private SyllabusTopic topic;

  @Column(nullable = false, length = 500)
  private String title;

  @Column(nullable = false)
  private Integer position;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected SyllabusSubtopic() {}

  public SyllabusSubtopic(String title, int position) {
    this.title = title;
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

  void attachTo(SyllabusTopic owner) {
    this.topic = owner;
    this.syllabus = owner.getSyllabus();
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Integer getPosition() {
    return position;
  }

  public void setPosition(int position) {
    this.position = position;
  }
}