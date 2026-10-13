package com.professor.assistant.syllabus;

import com.professor.assistant.config.SyllabusProperties;
import com.professor.assistant.domain.SyllabusFileType;
import com.professor.assistant.exception.SyllabusExtractionException;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PDF text extraction via Apache PDFBox.
 *
 * <p>PDFs are read page by page and capped, so a 10 MB file with thousands
 * of pages cannot exhaust memory. Password-protected documents are detected
 * and rejected with a clear message rather than a stack trace.
 *
 * <p>A PDF containing only scanned images yields no text. That is reported
 * honestly as EXTRACTION_FAILED - this phase does no OCR and must not
 * pretend otherwise.
 */
@Component
public class PdfSyllabusTextExtractor implements SyllabusTextExtractor {

  private static final Logger log = LoggerFactory.getLogger(PdfSyllabusTextExtractor.class);

  private final SyllabusProperties properties;

  public PdfSyllabusTextExtractor(SyllabusProperties properties) {
    this.properties = properties;
  }

  @Override
  public SyllabusFileType supports() {
    return SyllabusFileType.PDF;
  }

  @Override
  public String extract(byte[] content) {
    try (PDDocument document = Loader.loadPDF(content)) {
      if (document.isEncrypted()) {
        // Should normally be caught by loadPDF, but be explicit.
        throw new SyllabusExtractionException(
            "This PDF is password protected. Remove the password and upload it again.");
      }
      int pageCount = document.getNumberOfPages();
      if (pageCount == 0) {
        throw new SyllabusExtractionException("This PDF has no pages.");
      }
      int pagesToRead = Math.min(pageCount, properties.getMaxPdfPages());
      if (pageCount > pagesToRead) {
        log.warn(
            "PDF has {} pages; extracting only the first {}.",
            pageCount,
            pagesToRead);
      }

      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setStartPage(1);
      stripper.setEndPage(pagesToRead);
      String text = stripper.getText(document);
      return TextNormalizer.normalize(text, properties.getMaxExtractedCharacters());
    } catch (InvalidPasswordException e) {
      throw new SyllabusExtractionException(
          "This PDF is password protected. Remove the password and upload it again.", e);
    } catch (IOException e) {
      // Message is about the file, never about its contents.
      log.warn("PDF text extraction failed", e);
      throw new SyllabusExtractionException("This PDF could not be read; it may be corrupt.", e);
    } catch (RuntimeException e) {
      log.warn("PDF text extraction failed unexpectedly", e);
      throw new SyllabusExtractionException("This PDF could not be read; it may be corrupt.", e);
    }
  }
}