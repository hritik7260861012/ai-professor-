package com.professor.assistant.syllabus;

import com.professor.assistant.config.SyllabusProperties;
import com.professor.assistant.domain.SyllabusFileType;
import com.professor.assistant.exception.InvalidUploadException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Rejects bad uploads with messages a teacher can act on.
 *
 * <p>Layered validation: extension, declared MIME type, then the actual
 * magic bytes. A mismatch between any two produces a specific message
 * rather than a generic rejection, because "wrong type" is the single most
 * common real-world upload problem.
 *
 * <p>Also strips the client filename down to something safe to store and
 * display. It is never used to build a path - the storage layer generates
 * its own random key.
 */
@Component
public class SyllabusUploadValidator {

  private final FileSignatureValidator signatures;
  private final SyllabusProperties properties;

  public SyllabusUploadValidator(
      FileSignatureValidator signatures, SyllabusProperties properties) {
    this.signatures = signatures;
    this.properties = properties;
  }

  public SyllabusFileType validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new InvalidUploadException("Choose a syllabus file to upload");
    }
    if (file.getSize() > properties.getMaxFileSize()) {
      throw new InvalidUploadException(
          "File is too large. The maximum size is "
              + (properties.getMaxFileSize() / (1024 * 1024))
              + " MB.");
    }

    String originalName = file.getOriginalFilename();
    SyllabusFileType byExtension = SyllabusFileType.fromFilename(originalName);
    SyllabusFileType byContentType = SyllabusFileType.fromContentType(file.getContentType());

    if (byContentType == null && byExtension == null) {
      throw new InvalidUploadException(
          "Unsupported file type. Upload a PDF, DOCX or TXT file.");
    }

    byte[] content;
    try {
      content = file.getBytes();
    } catch (java.io.IOException e) {
      throw new InvalidUploadException("The uploaded file could not be read", e);
    }

    if (content.length == 0) {
      throw new InvalidUploadException("The uploaded file is empty");
    }

    SyllabusFileType declared = byContentType != null ? byContentType : byExtension;

    // The client controls both the extension and the MIME header, so only
    // the bytes decide what this really is.
    SyllabusFileType detected = signatures.detectFormat(content);

    if (!signatures.matchesSignature(declared, content)) {
      throw new InvalidUploadException(mismatchMessage(declared, detected));
    }
    return detected;
  }

  private String mismatchMessage(SyllabusFileType declared, SyllabusFileType detected) {
    if (detected != null) {
      return "This file looks like a "
          + describe(detected)
          + " but was uploaded as a "
          + describe(declared)
          + ".";
    }
    return "This file is not a readable "
        + describe(declared)
        + " document. It may be corrupt or in an older format (for example a legacy .doc file).";
  }

  private String describe(SyllabusFileType type) {
    return switch (type) {
      case PDF -> "PDF";
      case DOCX -> "DOCX";
      case TXT -> "plain text";
    };
  }

  /**
   * Reduces a client-supplied filename to a short, safe, display-only value.
   * Any path component is discarded outright rather than "cleaned".
   */
  public String sanitizeFilename(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      return "syllabus";
    }
    // Strip any directory component: handles both / and \ separators.
    String base = originalFilename.replace('\\', '/');
    int slash = base.lastIndexOf('/');
    if (slash >= 0) {
      base = base.substring(slash + 1);
    }
    base = base.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("^[.]+", "");
    if (base.isBlank()) {
      base = "syllabus";
    }
    if (base.length() > 200) {
      base = base.substring(base.length() - 200);
    }
    return base;
  }

  /** Lower-cased extension used when generating the internal storage key. */
  public String extensionFor(SyllabusFileType type) {
    return type.extension().toLowerCase(Locale.ROOT);
  }
}