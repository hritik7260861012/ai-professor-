package com.professor.assistant.syllabus;

import com.professor.assistant.domain.SyllabusFileType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Routes a document to the right extractor and then to the structure
 * parser.
 *
 * <p>Kept free of persistence and controllers so the whole extraction path
 * can be unit-tested against in-memory bytes.
 */
@Component
public class SyllabusExtractionService {

  private final Map<SyllabusFileType, SyllabusTextExtractor> extractors =
      new EnumMap<>(SyllabusFileType.class);
  private final SyllabusStructureParser parser;

  public SyllabusExtractionService(
      List<SyllabusTextExtractor> availableExtractors, SyllabusStructureParser parser) {
    for (SyllabusTextExtractor extractor : availableExtractors) {
      extractors.put(extractor.supports(), extractor);
    }
    this.parser = parser;
  }

  /**
   * Extracts text and derives an outline.
   *
   * <p>Throws {@link com.professor.assistant.exception.SyllabusExtractionException}
   * for corrupt or encrypted documents; a readable-but-empty document
   * returns {@code null} text plus a warning explaining that OCR is not
   * supported.
   */
  public ExtractionResult extract(SyllabusFileType type, byte[] content) {
    SyllabusTextExtractor extractor = extractors.get(type);
    if (extractor == null) {
      throw new com.professor.assistant.exception.SyllabusExtractionException(
          "No extractor is registered for " + type);
    }

    String text = extractor.extract(content);
    if (!TextNormalizer.hasUsableText(text)) {
      // Honest failure: the bytes were valid but carry no readable text.
      return new ExtractionResult(null, List.of(), null, OCR_NOT_SUPPORTED_WARNING);
    }

    ParsedSyllabus parsed = parser.parse(text);
    return new ExtractionResult(
        text, parsed.units(), parsed.unitsDetected() ? null : parsed.warning(), null);
  }

  public static final String OCR_NOT_SUPPORTED_WARNING =
      "No text could be read from this document. If it is a scanned image, optical "
          + "character recognition (OCR) is not supported yet — please upload a "
          + "text-based PDF, DOCX or TXT version, or type the syllabus in by hand.";

  /**
   * @param text normalised document text, or null when nothing was readable
   * @param units detected units (possibly empty)
   * @param parserWarning set when units could not be detected
   * @param extractionWarning set when no text could be read at all
   */
  public record ExtractionResult(
      String text,
      List<ParsedUnit> units,
      String parserWarning,
      String extractionWarning) {

    public boolean hasText() {
      return text != null;
    }

    /** Teacher-facing message, or null when extraction went cleanly. */
    public String warning() {
      return extractionWarning != null ? extractionWarning : parserWarning;
    }
  }
}