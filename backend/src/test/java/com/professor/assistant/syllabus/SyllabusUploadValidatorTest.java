package com.professor.assistant.syllabus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.professor.assistant.config.SyllabusProperties;
import com.professor.assistant.domain.SyllabusFileType;
import com.professor.assistant.exception.InvalidUploadException;
import com.professor.assistant.support.DocumentFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Upload validation.
 *
 * <p>The point of these tests is that a client cannot lie about what it is
 * uploading: extension and MIME type are both attacker-controlled, so the
 * magic bytes must have the final say.
 */
class SyllabusUploadValidatorTest {

  private static final byte[] PDF_BYTES = DocumentFixtures.pdfWithText("Some syllabus content here");
  private static final byte[] DOCX_BYTES = DocumentFixtures.docxWithParagraphs("Some syllabus content");
  private static final byte[] TXT_BYTES = DocumentFixtures.txt("Some syllabus content here");

  private SyllabusProperties properties;
  private SyllabusUploadValidator validator;

  @BeforeEach
  void setUp() {
    properties = new SyllabusProperties();
    validator = new SyllabusUploadValidator(new FileSignatureValidator(), properties);
  }

  private MockMultipartFile file(String name, String contentType, byte[] content) {
    return new MockMultipartFile("file", name, contentType, content);
  }

  /* ------------------------------- happy path ------------------------------ */

  @Test
  @DisplayName("Accepts a PDF")
  void acceptsPdf() {
    assertThat(validator.validate(file("syllabus.pdf", "application/pdf", PDF_BYTES)))
        .isEqualTo(SyllabusFileType.PDF);
  }

  @Test
  @DisplayName("Accepts a DOCX")
  void acceptsDocx() {
    assertThat(
            validator.validate(
                file(
                    "syllabus.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    DOCX_BYTES)))
        .isEqualTo(SyllabusFileType.DOCX);
  }

  @Test
  @DisplayName("Accepts a plain-text file")
  void acceptsTxt() {
    assertThat(validator.validate(file("syllabus.txt", "text/plain", TXT_BYTES)))
        .isEqualTo(SyllabusFileType.TXT);
  }

  @Test
  @DisplayName("Accepts a PDF whose MIME type carries a charset parameter")
  void acceptsContentTypeWithParameters() {
    assertThat(validator.validate(file("s.txt", "text/plain; charset=utf-8", TXT_BYTES)))
        .isEqualTo(SyllabusFileType.TXT);
  }

  /* ------------------------------- rejection ------------------------------- */

  @Test
  @DisplayName("Rejects an unsupported MIME type")
  void rejectsUnsupportedContentType() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    file("malware.exe", "application/x-msdownload", DocumentFixtures.corrupt())))
        .isInstanceOf(InvalidUploadException.class)
        .hasMessageContaining("Unsupported file type");
  }

  @Test
  @DisplayName("Rejects a PDF extension on content that is not a PDF")
  void rejectsWrongContentBehindAPdfExtension() {
    assertThatThrownBy(
            () -> validator.validate(file("fake.pdf", "application/pdf", TXT_BYTES)))
        .isInstanceOf(InvalidUploadException.class)
        .hasMessageContaining("looks like a plain text but was uploaded as a PDF");
  }

  @Test
  @DisplayName("Rejects a corrupt file that claims to be a PDF")
  void rejectsCorruptPdf() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    file("broken.pdf", "application/pdf", DocumentFixtures.corrupt())))
        .isInstanceOf(InvalidUploadException.class)
        .hasMessageContaining("not a readable PDF");
  }

  @Test
  @DisplayName("Rejects an empty file")
  void rejectsEmptyFile() {
    // A zero-byte part is caught by the "nothing was sent" guard.
    assertThatThrownBy(
            () -> validator.validate(file("empty.pdf", "application/pdf", new byte[0])))
        .isInstanceOf(InvalidUploadException.class)
        .hasMessageContaining("Choose a syllabus file");
  }

  @Test
  @DisplayName("Rejects a file above the configured size limit")
  void rejectsOversizedFile() {
    properties.setMaxFileSize(10);
    assertThatThrownBy(
            () -> validator.validate(file("big.txt", "text/plain", TXT_BYTES)))
        .isInstanceOf(InvalidUploadException.class)
        .hasMessageContaining("too large");
  }

  @Test
  @DisplayName("Rejects a null upload")
  void rejectsNullUpload() {
    assertThatThrownBy(() -> validator.validate(null))
        .isInstanceOf(InvalidUploadException.class)
        .hasMessageContaining("Choose a syllabus file");
  }

  @Test
  @DisplayName("Rejects an extension the format registry does not know")
  void rejectsUnknownExtension() {
    assertThatThrownBy(
            () ->
                validator.validate(file("syllabus.pages", "application/octet-stream", TXT_BYTES)))
        .isInstanceOf(InvalidUploadException.class);
  }

  /* ------------------------------ magic bytes ------------------------------ */

  @Test
  @DisplayName("Rejects a mismatched declared type rather than silently re-typing it")
  void signatureBeatsDeclaredType() {
    // Declares DOCX but the bytes are a PDF. Rejecting is deliberate: a
    // mismatch means the client (or something in the middle) is confused, and
    // quietly storing it under a different type would hide that.
    assertThatThrownBy(
            () ->
                validator.validate(
                    file(
                        "s.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        PDF_BYTES)))
        .isInstanceOf(InvalidUploadException.class)
        .hasMessageContaining("looks like a PDF but was uploaded as a DOCX");
  }

  /* --------------------------- filename safety ---------------------------- */

  @Test
  @DisplayName("Strips any path component from the display filename")
  void sanitizesFilenames() {
    assertThat(validator.sanitizeFilename("../../etc/passwd")).isEqualTo("passwd");
    assertThat(validator.sanitizeFilename("C:\\Windows\\system32\\evil.txt")).isEqualTo("evil.txt");
    assertThat(validator.sanitizeFilename("....//....//syllabus.pdf")).isEqualTo("syllabus.pdf");
    assertThat(validator.sanitizeFilename("")).isEqualTo("syllabus");
    assertThat(validator.sanitizeFilename("my syllabus;rm -rf.pdf")).isEqualTo("my_syllabus_rm_-rf.pdf");
  }

  @Test
  @DisplayName("Handles a null filename")
  void sanitizesNullFilename() {
    assertThat(validator.sanitizeFilename(null)).isEqualTo("syllabus");
  }

  /* --------------------------- type resolution ---------------------------- */

  @Test
  @DisplayName("Resolves types from extension and MIME type")
  void resolvesTypes() {
    assertThat(SyllabusFileType.fromFilename("a.pdf")).isEqualTo(SyllabusFileType.PDF);
    assertThat(SyllabusFileType.fromFilename("a.DOCX")).isEqualTo(SyllabusFileType.DOCX);
    // Only the final extension counts.
    assertThat(SyllabusFileType.fromFilename("syllabus.pdf.exe")).isNull();
    assertThat(SyllabusFileType.fromFilename("noextension")).isNull();
    assertThat(SyllabusFileType.fromContentType("text/plain")).isEqualTo(SyllabusFileType.TXT);
    assertThat(SyllabusFileType.fromContentType("application/pdf")).isEqualTo(SyllabusFileType.PDF);
    assertThat(SyllabusFileType.fromContentType("image/png")).isNull();
  }
}