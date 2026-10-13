package com.professor.assistant.domain;

/** Supported syllabus upload formats. */
public enum SyllabusFileType {
  PDF("pdf", "application/pdf"),
  DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
  TXT("txt", "text/plain");

  private final String extension;
  private final String contentType;

  SyllabusFileType(String extension, String contentType) {
    this.extension = extension;
    this.contentType = contentType;
  }

  public String extension() {
    return extension;
  }

  public String contentType() {
    return contentType;
  }

  /** Resolves from a declared MIME type, or {@code null} when unsupported. */
  public static SyllabusFileType fromContentType(String contentType) {
    if (contentType == null) {
      return null;
    }
    // Strip any parameters, e.g. "text/plain; charset=utf-8".
    String base = contentType.split(";")[0].trim().toLowerCase();
    for (SyllabusFileType type : values()) {
      if (type.contentType.equals(base)) {
        return type;
      }
    }
    return null;
  }

  /**
   * Resolves from the filename extension, or {@code null} when unsupported.
   * Only the final extension is considered, so "notes.final.pdf" resolves to
   * PDF while "syllabus.exe" resolves to nothing.
   */
  public static SyllabusFileType fromFilename(String filename) {
    if (filename == null) {
      return null;
    }
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) {
      return null;
    }
    String extension = filename.substring(dot + 1).toLowerCase();
    for (SyllabusFileType type : values()) {
      if (type.extension.equals(extension)) {
        return type;
      }
    }
    return null;
  }
}