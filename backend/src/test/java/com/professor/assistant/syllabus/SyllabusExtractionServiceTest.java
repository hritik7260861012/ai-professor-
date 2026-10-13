package com.professor.assistant.syllabus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.professor.assistant.config.SyllabusProperties;
import com.professor.assistant.domain.SyllabusFileType;
import com.professor.assistant.exception.SyllabusExtractionException;
import com.professor.assistant.support.DocumentFixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Extraction and parsing in isolation — no Spring, no database.
 *
 * <p>Fixtures are generated with the same libraries the production code
 * reads, so these exercise genuine parsing rather than mocks.
 */
class SyllabusExtractionServiceTest {

  private static final String SAMPLE_SYLLABUS =
      String.join(
          "\n",
          "CS101 - Introduction to Computer Science",
          "Credits: 4",
          "",
          "Unit I: Foundations",
          "1.1 History of computing",
          "1.2 Number systems",
          "Unit II: Programming",
          "2.1 Variables and types",
          "2.2 Control flow",
          "2.2.1 Loops");

  private SyllabusExtractionService extraction;
  private SyllabusStructureParser parser;

  @BeforeEach
  void setUp() {
    SyllabusProperties properties = new SyllabusProperties();
    parser = new SyllabusStructureParser();
    extraction =
        new SyllabusExtractionService(
            List.of(
                new PdfSyllabusTextExtractor(properties),
                new DocxSyllabusTextExtractor(properties),
                new PlainTextSyllabusTextExtractor(properties)),
            parser);
  }

  /* ------------------------------ per format ------------------------------- */

  @Test
  @DisplayName("Extracts text and units from a PDF")
  void extractsFromPdf() {
    SyllabusExtractionService.ExtractionResult result =
        extraction.extract(SyllabusFileType.PDF, DocumentFixtures.pdfWithText(SAMPLE_SYLLABUS.split("\n")));

    assertThat(result.hasText()).isTrue();
    assertThat(result.text()).contains("Introduction to Computer Science");
    assertThat(result.units()).hasSize(2);
    assertThat(result.units().get(0).title()).contains("Foundations");
    assertThat(result.warning()).isNull();
  }

  @Test
  @DisplayName("Extracts text and units from a DOCX")
  void extractsFromDocx() {
    SyllabusExtractionService.ExtractionResult result =
        extraction.extract(SyllabusFileType.DOCX, DocumentFixtures.docxWithParagraphs(SAMPLE_SYLLABUS.split("\n")));

    assertThat(result.hasText()).isTrue();
    assertThat(result.text()).contains("Introduction to Computer Science");
    assertThat(result.units()).hasSize(2);
  }

  @Test
  @DisplayName("Extracts text and units from a plain-text file")
  void extractsFromTxt() {
    SyllabusExtractionService.ExtractionResult result =
        extraction.extract(SyllabusFileType.TXT, DocumentFixtures.txt(SAMPLE_SYLLABUS));

    assertThat(result.hasText()).isTrue();
    assertThat(result.units()).hasSize(2);
  }

  /* --------------------------- no usable content --------------------------- */

  @Test
  @DisplayName("A PDF with no text reports that OCR is not supported")
  void scannedPdfIsReportedHonestly() {
    SyllabusExtractionService.ExtractionResult result =
        extraction.extract(SyllabusFileType.PDF, DocumentFixtures.pdfWithoutText());

    assertThat(result.hasText()).isFalse();
    assertThat(result.units()).isEmpty();
    // Must not claim success it cannot back up.
    assertThat(result.warning()).contains("optical character recognition (OCR) is not supported");
  }

  @Test
  @DisplayName("A blank text file is reported as having no readable content")
  void blankTextIsReported() {
    SyllabusExtractionService.ExtractionResult result =
        extraction.extract(SyllabusFileType.TXT, DocumentFixtures.txt("   \n\n  \n"));

    assertThat(result.hasText()).isFalse();
    assertThat(result.warning()).contains("optical character recognition (OCR) is not supported");
  }

  /* ------------------------------ bad documents --------------------------- */

  @Test
  @DisplayName("A corrupt PDF is rejected with a readable message")
  void corruptPdfIsRejected() {
    assertThatThrownBy(
            () -> extraction.extract(SyllabusFileType.PDF, DocumentFixtures.corrupt()))
        .isInstanceOf(SyllabusExtractionException.class)
        .hasMessageContaining("could not be read");
  }

  @Test
  @DisplayName("A password-protected PDF is rejected with actionable guidance")
  void encryptedPdfIsRejected() {
    assertThatThrownBy(
            () ->
                extraction.extract(
                    SyllabusFileType.PDF, DocumentFixtures.passwordProtectedPdf()))
        .isInstanceOf(SyllabusExtractionException.class)
        .hasMessageContaining("password");
  }

  @Test
  @DisplayName("A corrupt DOCX is rejected")
  void corruptDocxIsRejected() {
    assertThatThrownBy(
            () -> extraction.extract(SyllabusFileType.DOCX, DocumentFixtures.corrupt()))
        .isInstanceOf(SyllabusExtractionException.class);
  }

  /* --------------------------- structure detection ------------------------- */

  @Test
  @DisplayName("Text without unit headings yields no units and a warning")
  void noUnitsDetectedDoesNotInventThem() {
    SyllabusExtractionService.ExtractionResult result =
        extraction.extract(
            SyllabusFileType.TXT,
            DocumentFixtures.txt(
                "Course title\nInstructor notes\nSome prose describing the course in a single block."));

    assertThat(result.hasText()).isTrue();
    // Nothing invented.
    assertThat(result.units()).isEmpty();
    assertThat(result.warning()).contains("No unit headings were detected");
    // The original text is still available for manual organisation.
    assertThat(result.text()).contains("Course title");
  }

  @Test
  @DisplayName("Common unit heading variants are all recognised")
  void detectsCommonUnitHeadingVariants() {
    List<String> variants =
        List.of(
            "Unit I: Alpha",
            "Unit 1: Alpha",
            "UNIT-I: Alpha",
            "UNIT 2 - Alpha",
            "Module 3: Alpha",
            "Part IV: Alpha");

    for (String heading : variants) {
      // The topic line is deliberately long enough to clear the
      // "is there real text here?" threshold in TextNormalizer.
      ParsedSyllabus parsed =
          parser.parse(heading + "\n1.1 Introduction to computer architecture and history");
      assertThat(parsed.unitsDetected())
          .as("expected %s to be detected as a unit heading", heading)
          .isTrue();
      assertThat(parsed.units()).hasSize(1);
    }
  }

  @Test
  @DisplayName("Prose containing the word 'unit' is not treated as a heading")
  void doesNotMistakeProseForAUnitHeading() {
    ParsedSyllabus parsed = parser.parse("Unit tests are part of the weekly schedule for everyone.");

    assertThat(parsed.unitsDetected()).isFalse();
    assertThat(parsed.units()).isEmpty();
  }

  @Test
  @DisplayName("Nested numbering produces topics and subtopics")
  void parsesTopicsAndSubtopics() {
    ParsedSyllabus parsed =
        parser.parse(
            String.join(
                "\n",
                "Unit I: Intro",
                "1.1 First topic",
                "1.2 Second topic",
                "1.2.1 Nested detail",
                "Unit II: Next",
                "2.1 Another topic"));

    assertThat(parsed.unitsDetected()).isTrue();
    assertThat(parsed.units()).hasSize(2);

    ParsedUnit first = parsed.units().get(0);
    assertThat(first.position()).isZero();
    assertThat(first.topics()).hasSize(2);
    assertThat(first.topics().get(0).title()).isEqualTo("First topic");
    assertThat(first.topics().get(0).subtopics()).isEmpty();
    assertThat(first.topics().get(1).title()).isEqualTo("Second topic");
    assertThat(first.topics().get(1).subtopics()).containsExactly("Nested detail");

    assertThat(parsed.units().get(1).position()).isEqualTo(1);
  }

  @Test
  @DisplayName("Content before the first unit is not forced into a unit")
  void ignoresPreambleBeforeFirstUnit() {
    ParsedSyllabus parsed =
        parser.parse(
            String.join("\n", "Course code: CS101", "Credits: 4", "Unit I: Real unit", "1.1 Topic"));

    assertThat(parsed.units()).hasSize(1);
    assertThat(parsed.units().get(0).title()).contains("Real unit");
  }

  @Test
  @DisplayName("Parsing is deterministic")
  void parsingIsDeterministic() {
    ParsedSyllabus first = parser.parse(SAMPLE_SYLLABUS);
    ParsedSyllabus second = parser.parse(SAMPLE_SYLLABUS);

    assertThat(first.units()).isEqualTo(second.units());
    assertThat(first.unitsDetected()).isEqualTo(second.unitsDetected());
  }

  /* ------------------------------ normalisation --------------------------- */

  @Test
  @DisplayName("Normalisation collapses blank runs and repeated spaces")
  void normalisesWhitespace() {
    String normalized =
        TextNormalizer.normalize("Title\r\n\r\n\r\n\r\nBody    with     spaces   \n\nEnd", 10_000);

    // Carriage returns gone, runs of blank lines collapsed to a single one,
    // and runs of 3+ spaces reduced to two.
    assertThat(normalized).doesNotContain("\r");
    assertThat(normalized).doesNotContain("    ");
    assertThat(normalized).contains("Body  with  spaces");
    assertThat(normalized.lines().toList()).containsExactly("Title", "", "Body  with  spaces", "", "End");
  }

  @Test
  @DisplayName("Extraction is capped at the configured character limit")
  void capsExtractedCharacters() {
    SyllabusProperties properties = new SyllabusProperties();
    properties.setMaxExtractedCharacters(50);
    SyllabusExtractionService capped =
        new SyllabusExtractionService(
            List.of(new PlainTextSyllabusTextExtractor(properties)), parser);

    SyllabusExtractionService.ExtractionResult result =
        capped.extract(SyllabusFileType.TXT, DocumentFixtures.txt("a".repeat(10_000)));

    assertThat(result.text().length()).isLessThanOrEqualTo(50);
  }
}