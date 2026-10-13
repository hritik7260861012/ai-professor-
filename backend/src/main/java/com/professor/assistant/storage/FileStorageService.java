package com.professor.assistant.storage;

/**
 * Private binary storage for uploaded documents.
 *
 * <p>Deliberately minimal so a future private object-storage provider (S3,
 * GCS, Azure blob) can be swapped in without touching callers: the only
 * identifier that ever crosses this boundary is an opaque, randomly
 * generated storage key. Implementations must never expose a filesystem
 * path, a URL, or any other physical location to a client.
 */
public interface FileStorageService {

  /**
   * Persists bytes under a freshly generated random key.
   *
   * @return the storage key to persist alongside the syllabus metadata
   */
  String store(String extension, byte[] content);

  /**
   * Reads previously stored bytes.
   *
   * @throws StorageException if the key is unknown or unreadable
   */
  byte[] load(String storageKey);

  /** Removes stored bytes. Deleting an absent key is a no-op. */
  void delete(String storageKey);

  /** The storage key used for a freshly uploaded file, without writing it. */
  String generateKey(String extension);
}