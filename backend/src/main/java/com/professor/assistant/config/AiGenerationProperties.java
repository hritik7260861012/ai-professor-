package com.professor.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Phase 5 configuration.
 *
 * <p>The API key is read <strong>only</strong> from {@code GEMINI_API_KEY}
 * and is never returned by any endpoint, logged, or sent to the frontend.
 * When it is absent the application still starts normally; generation
 * requests then fail with a clear configuration error rather than
 * returning fabricated content.
 */
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiGenerationProperties {

  /**
   * Which provider to use: {@code GEMINI} or {@code MOCK}. The mock
   * provider exists for tests and local demos only; it is never selected
   * automatically, so production cannot silently fall back to fake content.
   */
  private Provider provider = Provider.GEMINI;

  /**
   * Provider credential. Bound from {@code GEMINI_API_KEY}. Empty means the
   * deployment has not configured AI generation.
   */
  private String apiKey = "";

  /** Model id. Current Gemini free-tier eligible Flash model by default. */
  private String model = "gemini-3.8-flash";

  /** Overridable for testing or regional endpoints. */
  private String baseUrl = "https://generativelanguage.googleapis.com";

  /** API version segment of the endpoint path. */
  private String apiVersion = "v1beta";

  /** HTTP connect/read timeout for a single provider call. */
  private int timeoutSeconds = 60;

  /** Total attempts for a retryable failure (429 / 5xx / timeout). */
  private int maxAttempts = 3;

  /** Concurrent generation jobs across the whole application. */
  private int maxConcurrentJobs = 2;

  /** Concurrent jobs a single user may have in flight. */
  private int maxConcurrentJobsPerUser = 1;

  /**
   * Jobs a single user may start per day. Counted from the database.
   * Advisory only — not a billing-grade quota (see docs).
   */
  private int dailyQuotaPerUser = 50;

  /** Upper bound on questions or slides in one request. */
  private int maxItems = 30;

  /** Upper bound on the teacher's free-text instructions. */
  private int maxInstructionLength = 500;

  /** Characters of generated content retained; longer output is truncated. */
  private int maxContentCharacters = 200_000;

  /** A RUNNING job older than this is treated as stale and reconciled. */
  private int staleJobMinutes = 15;

  /** Window in which an identical request reuses the existing job. */
  private int duplicateWindowSeconds = 60;

  public enum Provider {
    GEMINI,
    MOCK
  }

  /** True when a credential is present; never logs or exposes the value. */
  public boolean isConfigured() {
    return provider == Provider.MOCK
        || (apiKey != null && !apiKey.isBlank());
  }

  public Provider getProvider() {
    return provider;
  }

  public void setProvider(Provider provider) {
    this.provider = provider;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getApiVersion() {
    return apiVersion;
  }

  public void setApiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
  }

  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public void setTimeoutSeconds(int timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  public int getMaxConcurrentJobs() {
    return maxConcurrentJobs;
  }

  public void setMaxConcurrentJobs(int maxConcurrentJobs) {
    this.maxConcurrentJobs = maxConcurrentJobs;
  }

  public int getMaxConcurrentJobsPerUser() {
    return maxConcurrentJobsPerUser;
  }

  public void setMaxConcurrentJobsPerUser(int maxConcurrentJobsPerUser) {
    this.maxConcurrentJobsPerUser = maxConcurrentJobsPerUser;
  }

  public int getDailyQuotaPerUser() {
    return dailyQuotaPerUser;
  }

  public void setDailyQuotaPerUser(int dailyQuotaPerUser) {
    this.dailyQuotaPerUser = dailyQuotaPerUser;
  }

  public int getMaxItems() {
    return maxItems;
  }

  public void setMaxItems(int maxItems) {
    this.maxItems = maxItems;
  }

  public int getMaxInstructionLength() {
    return maxInstructionLength;
  }

  public void setMaxInstructionLength(int maxInstructionLength) {
    this.maxInstructionLength = maxInstructionLength;
  }

  public int getMaxContentCharacters() {
    return maxContentCharacters;
  }

  public void setMaxContentCharacters(int maxContentCharacters) {
    this.maxContentCharacters = maxContentCharacters;
  }

  public int getStaleJobMinutes() {
    return staleJobMinutes;
  }

  public void setStaleJobMinutes(int staleJobMinutes) {
    this.staleJobMinutes = staleJobMinutes;
  }

  public int getDuplicateWindowSeconds() {
    return duplicateWindowSeconds;
  }

  public void setDuplicateWindowSeconds(int duplicateWindowSeconds) {
    this.duplicateWindowSeconds = duplicateWindowSeconds;
  }
}