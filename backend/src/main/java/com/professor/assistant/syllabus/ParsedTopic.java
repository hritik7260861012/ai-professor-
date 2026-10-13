package com.professor.assistant.syllabus;

/**
 * One topic parsed from a syllabus, with any nested subtopics.
 *
 * @param title the topic heading
 * @param subtopics nested items, possibly empty
 */
public record ParsedTopic(String title, java.util.List<String> subtopics) {

  public ParsedTopic(String title) {
    this(title, java.util.List.of());
  }
}