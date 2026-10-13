package com.professor.assistant.syllabus;

import java.util.List;

/**
 * One unit parsed from a syllabus.
 *
 * @param title the unit heading as written in the document
 * @param position zero-based order
 * @param topics topics belonging to this unit, possibly empty
 */
public record ParsedUnit(String title, int position, List<ParsedTopic> topics) {

  public ParsedUnit(String title, int position, List<ParsedTopic> topics) {
    this.title = title;
    this.position = position;
    this.topics = List.copyOf(topics);
  }
}