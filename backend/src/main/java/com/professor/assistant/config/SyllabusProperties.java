package com.professor.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Phase 4 configuration.
 *
 * <p>Everything is environment-overridable so no path or limit is baked
 * into the build. Values here are safe defaults for local development.
 */
@Component
@ConfigurationProperties(prefix = "app.syllabus")
public class SyllabusProperties {

  /**
   * Private storage directory for uploaded syllabus files.
   *
   * <p>Deliberately outside {@code frontend/public} and outside any static
   * resource location, so nothing can be reached over an unauthenticated
   * URL. Files are only served through the authorized download endpoint.
   */
  private String storageDirectory = "./private-storage/syllabi";

  /** Maximum accepted upload size in bytes (default 10 MB). */
  private long maxFileSize = 10L * 1024 * 1024;

  /** Hard cap on characters kept from extraction, to bound memory. */
  private int maxExtractedCharacters = 500_000;

  /** Maximum pages read from a PDF before extraction gives up. */
  private int maxPdfPages = 300;

  /** Whether accepting uploads is enabled. */
  private boolean enabled = true;

  public String getStorageDirectory() {
    return storageDirectory;
  }

  public void setStorageDirectory(String storageDirectory) {
    this.storageDirectory = storageDirectory;
  }

  public long getMaxFileSize() {
    return maxFileSize;
  }

  public void setMaxFileSize(long maxFileSize) {
    this.maxFileSize = maxFileSize;
  }

  public int getMaxExtractedCharacters() {
    return maxExtractedCharacters;
  }

  public void setMaxExtractedCharacters(int maxExtractedCharacters) {
    this.maxExtractedCharacters = maxExtractedCharacters;
  }

  public int getMaxPdfPages() {
    return maxPdfPages;
  }

  public void setMaxPdfPages(int maxPdfPages) {
    this.maxPdfPages = maxPdfPages;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}