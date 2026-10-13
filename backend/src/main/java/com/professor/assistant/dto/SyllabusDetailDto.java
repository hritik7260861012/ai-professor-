package com.professor.assistant.dto;

import java.util.List;

/**
 * A syllabus with its reviewed structure and the raw extracted text.
 *
 * <p>The nested records mirror the unit / topic / subtopic tree so the
 * frontend can render and edit it without a second round trip.
 */
public record SyllabusDetailDto(
    SyllabusDto syllabus,
    List<UnitDto> units,
    List<OutcomeDto> learningOutcomes,
    /** Preserved so the teacher can correct imperfect parsing. */
    String extractedText,
    /** Extraction or parsing guidance; null when everything went cleanly. */
    String warning) {

  /**
   * @param id stable id of this unit
   * @param title unit heading
   * @param position zero-based ordering
   * @param topics topics within the unit
   */
  public record UnitDto(Long id, String title, int position, List<TopicDto> topics) {

    public UnitDto {
      topics = topics == null ? List.of() : List.copyOf(topics);
    }
  }

  /** @param subtopics nested items, possibly empty */
  public record TopicDto(Long id, String title, int position, List<String> subtopics) {

    public TopicDto {
      subtopics = subtopics == null ? List.of() : List.copyOf(subtopics);
    }
  }

  /** @param unitId owning unit, or null for a syllabus-level outcome */
  public record OutcomeDto(Long id, String statement, int position, Long unitId) {}
}