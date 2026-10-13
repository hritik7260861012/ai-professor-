package com.professor.assistant.syllabus;

import com.professor.assistant.domain.SyllabusFileType;

/** Pulls plain text out of one supported document format. */
public interface SyllabusTextExtractor {

  SyllabusFileType supports();

  /**
   * Extracts text from already-validated bytes.
   *
   * @return the document text; may be blank for a scanned document
   * @throws com.professor.assistant.exception.SyllabusExtractionException
   *     when the document is corrupt, encrypted or otherwise unreadable
   */
  String extract(byte[] content);
}