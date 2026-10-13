package com.professor.assistant.syllabus;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Turns extracted text into a unit / topic / subtopic outline.
 *
 * <p>Entirely deterministic: no AI, no API key, same input always yields
 * the same outline. It is deliberately conservative - a line is only treated
 * as a heading when it matches a known numbering pattern, and anything it
 * cannot classify is preserved as continuation text rather than discarded.
 *
 * <p>Real syllabi are inconsistent, so when the document has no recognisable
 * unit headings this parser returns an empty outline plus a warning. It
 * never invents units to make the output look complete; the teacher
 * organises the document by hand instead.
 */
@Component
public class SyllabusStructureParser {

  /**
   * Unit headings: "Unit I", "Unit 1", "UNIT-II", "Unit - 3", "Module 2",
   * "PART IV". The numeral is required, so prose like "Unit tests" or
   * "Unit conversion" is not mistaken for a heading.
   */
  private static final Pattern UNIT_HEADING =
      Pattern.compile(
          "^\\s*(?:UNIT|Unit|unit|MODULE|Module|module|PART|Part)\\s*[-–—.:]?\\s*"
              + "([IVXLCDM]+|\\d+)\\b\\s*[.:–—-]?\\s*(.*)$");

  /** "1.2 Title" / "2.3.1 Title" - the leading number gives the depth. */
  private static final Pattern NUMBERED =
      Pattern.compile("^\\s*(\\d+(?:\\.\\d+)+)\\s*[.)]?\\s+(.+)$");

  /** "1. Title", "1) Title" - depth 1. */
  private static final Pattern FLAT_NUMBERED =
      Pattern.compile("^\\s*(\\d+)\\s*[.)]\\s+(.+)$");

  /** Bullet markers used for unnumbered topics and subtopics. */
  private static final Pattern BULLET = Pattern.compile("^\\s*[-–—•*·]\\s+(.+)$");

  private static final int MAX_TITLE = 500;

  public ParsedSyllabus parse(String text) {
    if (!TextNormalizer.hasUsableText(text)) {
      // Nothing to parse. Be explicit about why rather than pretending.
      return new ParsedSyllabus(
          List.of(),
          false,
          "No text could be read from this document. If it is a scanned image, "
              + "optical character recognition (OCR) is not supported yet — "
              + "please upload a text-based PDF, DOCX or TXT version, or type "
              + "the syllabus in by hand.");
    }

    List<String> lines = List.of(text.split("\n"));
    List<MutableUnit> units = new ArrayList<>();
    MutableUnit current = null;

    for (String line : lines) {
      String trimmed = line.strip();
      if (trimmed.isEmpty()) {
        continue;
      }

      Matcher unitMatcher = UNIT_HEADING.matcher(trimmed);
      if (unitMatcher.matches()) {
        String number = unitMatcher.group(1);
        String rest = unitMatcher.group(2).strip();
        String title = rest.isEmpty() ? trimmed : trimmed;
        current = new MutableUnit(clip(title, MAX_TITLE));
        units.add(current);
        continue;
      }

      if (current == null) {
        // Content before the first unit heading (course code, credits,
        // prerequisites). Kept in the raw text, not forced into a unit.
        continue;
      }

      applyLineToUnit(current, trimmed);
    }

    List<ParsedUnit> parsed = new ArrayList<>();
    for (int i = 0; i < units.size(); i++) {
      parsed.add(units.get(i).toParsed(i));
    }

    if (parsed.isEmpty()) {
      return new ParsedSyllabus(
          List.of(),
          false,
          "No unit headings were detected in this document, so nothing was "
              + "organised automatically. The full text is shown below — "
              + "please add the units and topics yourself.");
    }

    return new ParsedSyllabus(parsed, true, null);
  }

  private void applyLineToUnit(MutableUnit unit, String line) {
    Matcher numbered = NUMBERED.matcher(line);
    if (numbered.matches()) {
      String number = numbered.group(1);
      String title = clip(numbered.group(2).strip(), MAX_TITLE);
      int depth = number.split("\\.").length;
      if (depth <= 1) {
        unit.addTopic(title, List.of());
      } else if (depth == 2) {
        unit.addTopic(title, List.of());
      } else {
        // Depth 3+ hangs off the most recent topic.
        unit.addSubtopic(title);
      }
      return;
    }

    Matcher flat = FLAT_NUMBERED.matcher(line);
    if (flat.matches()) {
      unit.addTopic(clip(flat.group(2).strip(), MAX_TITLE), List.of());
      return;
    }

    Matcher bullet = BULLET.matcher(line);
    if (bullet.matches()) {
      String text = clip(bullet.group(1).strip(), MAX_TITLE);
      // A bullet immediately after a topic with no subtopics is a subtopic.
      if (unit.lastTopicHasNoSubtopics()) {
        unit.addSubtopic(text);
      } else {
        unit.addTopic(text, List.of());
      }
      return;
    }

    // Unclassified line: treat as continuation of the current topic rather
    // than dropping the teacher's content on the floor.
    unit.appendToLastTopic(clip(line, MAX_TITLE));
  }

  private String clip(String value, int max) {
    String trimmed = value == null ? "" : value.strip();
    return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
  }

  /** Mutable accumulator used while scanning, then frozen into records. */
  private static final class MutableUnit {
    private final String title;
    private final List<String> topicTitles = new ArrayList<>();
    private final List<List<String>> subtopics = new ArrayList<>();

    MutableUnit(String title) {
      this.title = title;
    }

    void addTopic(String title, List<String> initialSubtopics) {
      topicTitles.add(title);
      subtopics.add(new ArrayList<>(initialSubtopics));
    }

    void addSubtopic(String title) {
      if (subtopics.isEmpty()) {
        // No parent topic yet - promote to a topic so nothing is lost.
        addTopic(title, List.of());
        return;
      }
      subtopics.get(subtopics.size() - 1).add(title);
    }

    boolean lastTopicHasNoSubtopics() {
      return !subtopics.isEmpty() && subtopics.get(subtopics.size() - 1).isEmpty();
    }

    void appendToLastTopic(String continuation) {
      if (topicTitles.isEmpty()) {
        topicTitles.add(continuation);
        subtopics.add(new ArrayList<>());
        return;
      }
      int last = topicTitles.size() - 1;
      String combined = topicTitles.get(last) + " " + continuation;
      topicTitles.set(last, clip(combined, MAX_TITLE));
    }

    ParsedUnit toParsed(int position) {
      List<ParsedTopic> topics = new ArrayList<>();
      for (int i = 0; i < topicTitles.size(); i++) {
        topics.add(new ParsedTopic(topicTitles.get(i), subtopics.get(i)));
      }
      return new ParsedUnit(title, position, topics);
    }

    private static String clip(String value, int max) {
      return value.length() > max ? value.substring(0, max) : value;
    }
  }
}