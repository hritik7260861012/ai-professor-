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

/** A unit of a syllabus, ordered by {@code position}. */
@Entity
@Table(name = "syllabus_unit")
public class SyllabusUnit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "syllabus_id", nullable = false)
  private Syllabus syllabus;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false)
  private Integer position;

  @OneToMany(mappedBy = "unit", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  @OrderBy("position ASC")
  private List<SyllabusTopic> topics = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected SyllabusUnit() {}

  public SyllabusUnit(String title, int position) {
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

  void attachTo(Syllabus owner) {
    this.syllabus = owner;
    for (SyllabusTopic topic : topics) {
      topic.attachTo(this);
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

  public List<SyllabusTopic> getTopics() {
    return topics;
  }

  public void replaceTopics(List<SyllabusTopic> replacement) {
    topics.clear();
    topics.addAll(replacement);
    for (SyllabusTopic topic : topics) {
      topic.attachTo(this);
    }
  }
}