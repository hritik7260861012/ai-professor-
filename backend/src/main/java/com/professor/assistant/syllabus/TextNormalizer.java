package com.professor.assistant.syllabus;

/**
 * Whitespace and line-ending clean-up shared by every extractor.
 *
 * <p>Only ever removes blank lines and collapses runs of spaces; it never
 * rewrites words, drops numbers or reorders content, so the teacher always
 * sees text close to what the document actually said.
 */
public final class TextNormalizer {

  private TextNormalizer() {}

  public static String normalize(String raw, int maxCharacters) {
    if (raw == null) {
      return "";
    }
    String text = raw.replace("\r\n", "\n").replace('\r', '\n');

    StringBuilder out = new StringBuilder(Math.min(text.length(), maxCharacters));
    String[] lines = text.split("\n");
    int blankRun = 0;
    for (String line : lines) {
      // Collapse tabs and repeated spaces, and trim trailing whitespace.
      String cleaned = line.replaceAll("\t", " ").replaceAll(" {3,}", "  ").stripTrailing();
      if (cleaned.isBlank()) {
        // Collapse runs of blank lines to a single separator.
        blankRun++;
        if (blankRun <= 1 && out.length() > 0) {
          out.append('\n');
        }
        continue;
      }
      blankRun = 0;
      if (out.length() > 0) {
        out.append('\n');
      }
      out.append(cleaned);
      if (out.length() >= maxCharacters) {
        break;
      }
    }

    String result = out.toString().strip();
    if (result.length() > maxCharacters) {
      result = result.substring(0, maxCharacters);
    }
    return result;
  }

  /**
   * How much real prose the text contains, used to decide whether the
   * document is effectively an image scan.
   *
   * @return true when there is enough non-whitespace content to be useful
   */
  public static boolean hasUsableText(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    int lettersAndDigits = 0;
    for (int i = 0; i < text.length() && lettersAndDigits < 40; i++) {
      char c = text.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        lettersAndDigits++;
      }
    }
    return lettersAndDigits >= 40;
  }
}