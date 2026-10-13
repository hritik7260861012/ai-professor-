package com.professor.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Replaces the whole unit tree during teacher review.
 *
 * <p>The structure is sent as a full replacement rather than a set of
 * granular patches: it matches what the editor screen holds in memory, and
 * it makes ordering explicit because the frontend controls array order.
 */
public record SyllabusStructureRequest(
    @Valid List<@Valid UnitRequest> units,
    @Valid List<@Valid OutcomeRequest> learningOutcomes) {

  /**
   * @param title unit heading
   * @param topics topics in display order
   */
  public record UnitRequest(
      @NotBlank(message = "Unit title is required")
      @Size(max = 255, message = "Unit title must be at most 255 characters")
      String title,

      @Valid List<@Valid TopicRequest> topics) {

    public UnitRequest {
      topics = topics == null ? List.of() : List.copyOf(topics);
    }
  }

  /** @param subtopics plain-text nested items, possibly empty */
  public record TopicRequest(
      @NotBlank(message = "Topic title is required")
      @Size(max = 500, message = "Topic title must be at most 500 characters")
      String title,

      @Size(max = 100, message = "A topic may have at most 100 subtopics")
      List<@Size(max = 500, message = "Subtopic must be at most 500 characters") String>
          subtopics) {

    public TopicRequest {
      subtopics = subtopics == null ? List.of() : List.copyOf(subtopics);
    }
  }

  /** @param unitIndex zero-based index into {@code units}, or null for syllabus-level */
  public record OutcomeRequest(
      @NotBlank(message = "Learning outcome text is required")
      @Size(max = 1000, message = "Learning outcome must be at most 1000 characters")
      String statement,

      Integer unitIndex) {}
}