package com.professor.assistant.syllabus;

import com.professor.assistant.domain.SyllabusFileType;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * Validates uploaded bytes before anything tries to parse them.
 *
 * <p>Three independent layers, because any one of them can be trivially
 * bypassed by a client:
 *
 * <ol>
 *   <li><b>Filename extension</b> - a cheap filter, never trusted alone.
 *   <li><b>Declared MIME type</b> - also client-controlled.
 *   <li><b>Actual magic bytes</b> - the only layer that reflects reality.
 * </ol>
 *
 * <p>Nothing here executes file content, and no document text is ever
 * included in an error message.
 */
@Component
public class FileSignatureValidator {

  private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};
  private static final byte[] ZIP_MAGIC = {'P', 'K', 0x03, 0x04};
  private static final byte[] OLE2_MAGIC = {
    (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
  };

  /**
   * Confirms the bytes really are the format the client claimed.
   *
   * @return true when the signature matches
   */
  public boolean matchesSignature(SyllabusFileType type, byte[] content) {
    if (content.length < 4) {
      return false;
    }
    return switch (type) {
      case PDF -> startsWith(content, PDF_MAGIC);
      case DOCX -> startsWith(content, ZIP_MAGIC);
      case TXT -> looksLikeText(content);
    };
  }

  /**
   * A file that parses as any of the supported formats, whatever the
   * client called it. Used to give a precise "this is a PDF, not a DOCX"
   * message instead of a generic rejection.
   */
  public SyllabusFileType detectFormat(byte[] content) {
    if (content.length >= 4) {
      if (startsWith(content, PDF_MAGIC)) {
        return SyllabusFileType.PDF;
      }
      if (startsWith(content, ZIP_MAGIC)) {
        return SyllabusFileType.DOCX;
      }
      // Legacy .doc is an OLE2 container, not a supported DOCX.
      if (startsWith(content, OLE2_MAGIC)) {
        return null;
      }
    }
    if (looksLikeText(content)) {
      return SyllabusFileType.TXT;
    }
    return null;
  }

  /**
   * Plain-text heuristic: valid UTF-8 and free of NUL bytes.
   *
   * <p>Deliberately conservative - a binary blob that happens to decode
   * without NULs still has to survive the size and parse checks later.
   */
  private boolean looksLikeText(byte[] content) {
    int sampleLength = Math.min(content.length, 4096);
    byte[] sample = new byte[sampleLength];
    System.arraycopy(content, 0, sample, 0, sampleLength);

    for (byte b : sample) {
      if (b == 0) {
        return false;
      }
    }
    String text = new String(sample, StandardCharsets.UTF_8);
    // Replacement characters mean the bytes were not valid UTF-8 text.
    return text.indexOf('\uFFFD') < 0;
  }

  private boolean startsWith(byte[] content, byte[] signature) {
    for (int i = 0; i < signature.length; i++) {
      if (content[i] != signature[i]) {
        return false;
      }
    }
    return true;
  }
}