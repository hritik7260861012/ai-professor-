package com.professor.assistant.syllabus;

import java.util.List;

/**
 * Outcome of parsing a syllabus document.
 *
 * @param units detected units, possibly empty
 * @param unitsDetected whether any unit heading was recognised
 * @param warning teacher-facing guidance; never claims success it cannot
 *     back up. For a document with no extractable text this explains that
 *     OCR is not supported in this phase.
 */
public record ParsedSyllabus(List<ParsedUnit> units, boolean unitsDetected, String warning) {

  public ParsedSyllabus(List<ParsedUnit> units, boolean unitsDetected, String warning) {
    this.units = List.copyOf(units);
    this.unitsDetected = unitsDetected;
    this.warning = warning;
  }
}