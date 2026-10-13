package com.professor.assistant.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A topic within a {@link SyllabusUnit}. */
@Entity
@Table(name = "syllabus_topic")
public class SyllabusTopic {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "syllabus_id", nullable = false)
  private Syllabus syllabus;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "unit_id", nullable = false)
  private SyllabusUnit unit;

  @Column(nullable = false, length = 500)
  private String title;

  @Column(nullable = false)
  private Integer position;

  @OneToMany(
      mappedBy = "topic",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("position ASC")
  private List<SyllabusSubtopic> subtopics = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected SyllabusTopic() {}

  public SyllabusTopic(String title, int position) {
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

  void attachTo(SyllabusUnit owner) {
    this.unit = owner;
    // Denormalised onto every child row so review queries and cascade
    // deletes stay single-table. The unit's owner is already set by the
    // time this runs (Syllabus#replaceUnits sets it first).
    this.syllabus = owner.getSyllabus();
    for (SyllabusSubtopic subtopic : subtopics) {
      subtopic.attachTo(this);
    }
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

  public List<SyllabusSubtopic> getSubtopics() {
    return subtopics;
  }

  public void replaceSubtopics(List<SyllabusSubtopic> replacement) {
    subtopics.clear();
    subtopics.addAll(replacement);
    for (SyllabusSubtopic subtopic : subtopics) {
      subtopic.attachTo(this);
    }
  }
}