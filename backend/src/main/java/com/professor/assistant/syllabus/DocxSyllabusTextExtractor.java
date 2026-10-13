package com.professor.assistant.syllabus;

import com.professor.assistant.config.SyllabusProperties;
import com.professor.assistant.domain.SyllabusFileType;
import com.professor.assistant.exception.SyllabusExtractionException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DOCX text extraction via Apache POI.
 *
 * <p>DOCX is a zip of XML parts; POI reads the document part and ignores
 * embedded images and macros. Legacy binary {@code .doc} is not supported
 * and is rejected during upload validation rather than here.
 */
@Component
public class DocxSyllabusTextExtractor implements SyllabusTextExtractor {

  private static final Logger log = LoggerFactory.getLogger(DocxSyllabusTextExtractor.class);

  private final SyllabusProperties properties;

  public DocxSyllabusTextExtractor(SyllabusProperties properties) {
    this.properties = properties;
  }

  @Override
  public SyllabusFileType supports() {
    return SyllabusFileType.DOCX;
  }

  @Override
  public String extract(byte[] content) {
    // POI is memory-hungry; the byte[] overload keeps us off temp files.
    try (XWPFDocument document =
        new XWPFDocument(new ByteArrayInputStream(content));
        XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {

      if (document.getParagraphs().isEmpty()) {
        throw new SyllabusExtractionException("This document has no readable paragraphs.");
      }
      String text = extractor.getText();
      return TextNormalizer.normalize(text, properties.getMaxExtractedCharacters());
    } catch (IOException | RuntimeException e) {
      log.warn("DOCX text extraction failed", e);
      throw new SyllabusExtractionException(
          "This DOCX could not be read; it may be corrupt or password protected.", e);
    }
  }
}