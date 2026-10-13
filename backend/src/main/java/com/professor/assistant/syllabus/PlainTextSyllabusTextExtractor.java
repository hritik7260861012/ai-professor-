package com.professor.assistant.syllabus;

import com.professor.assistant.config.SyllabusProperties;
import com.professor.assistant.domain.SyllabusFileType;
import com.professor.assistant.exception.SyllabusExtractionException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/** Plain-text extraction. No parsing library needed. */
@Component
public class PlainTextSyllabusTextExtractor implements SyllabusTextExtractor {

  private final SyllabusProperties properties;

  public PlainTextSyllabusTextExtractor(SyllabusProperties properties) {
    this.properties = properties;
  }

  @Override
  public SyllabusFileType supports() {
    return SyllabusFileType.TXT;
  }

  @Override
  public String extract(byte[] content) {
    try {
      String text = new String(content, StandardCharsets.UTF_8);
      if (text.indexOf('\uFFFD') >= 0) {
        throw new SyllabusExtractionException(
            "This text file is not valid UTF-8. Re-save it as UTF-8 and upload it again.");
      }
      return TextNormalizer.normalize(text, properties.getMaxExtractedCharacters());
    } catch (RuntimeException e) {
      if (e instanceof SyllabusExtractionException) {
        throw e;
      }
      throw new SyllabusExtractionException("This text file could not be read.", e);
    }
  }
}