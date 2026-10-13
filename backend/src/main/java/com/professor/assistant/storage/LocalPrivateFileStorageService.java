package com.professor.assistant.storage;

import com.professor.assistant.config.SyllabusProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Local filesystem implementation of {@link FileStorageService} for
 * development and small deployments.
 *
 * <p>Security properties:
 *
 * <ul>
 *   <li>Every file gets a cryptographically random key; the uploaded
 *       filename is never used to build a path.
 *   <li>{@link #resolve} accepts only a strict key shape and then verifies
 *       the resolved path really is inside the storage root, so neither a
 *       traversal key nor a symlinked root can escape.
 *   <li>The directory sits outside any static resource location and is
 *       never served directly; downloads go through the authorized API.
 *   <li>Stored bytes are opaque - filenames and contents are not logged.
 * </ul>
 *
 * <p>A cloud provider would implement the same interface; no caller changes.
 */
@Service
public class LocalPrivateFileStorageService implements FileStorageService {

  private static final Logger log =
      LoggerFactory.getLogger(LocalPrivateFileStorageService.class);

  private final SyllabusProperties properties;
  private final SecureRandom random = new SecureRandom();
  private final Path root;

  public LocalPrivateFileStorageService(SyllabusProperties properties) throws IOException {
    this.properties = properties;
    this.root = Paths.get(properties.getStorageDirectory()).toAbsolutePath().normalize();
    Files.createDirectories(this.root);
    log.info("Private syllabus storage directory: {}", root);
  }

  @Override
  public String store(String extension, byte[] content) {
    String key = generateKey(extension);
    Path target = resolve(key);
    try {
      Path temp = Files.createTempFile(root, "upload-", ".tmp");
      try {
        Files.write(temp, content);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temp);
      }
    } catch (IOException e) {
      // Deliberately logs the key only, never the filename or contents.
      log.error("Failed to store file under key {}", key, e);
      throw new StorageException("Could not store the uploaded file");
    }
    return key;
  }

  @Override
  public byte[] load(String storageKey) {
    Path target = resolve(storageKey);
    if (!Files.isRegularFile(target)) {
      throw new StorageException("Stored file not found");
    }
    try (InputStream in = Files.newInputStream(target)) {
      return in.readAllBytes();
    } catch (IOException e) {
      log.error("Failed to read file under key {}", storageKey, e);
      throw new StorageException("Could not read the stored file");
    }
  }

  @Override
  public void delete(String storageKey) {
    try {
      Files.deleteIfExists(resolve(storageKey));
    } catch (IOException e) {
      log.error("Failed to delete file under key {}", storageKey, e);
      throw new StorageException("Could not delete the stored file");
    }
  }

  @Override
  public String generateKey(String extension) {
    byte[] bytes = new byte[16];
    random.nextBytes(bytes);
    String suffix =
        extension == null || extension.isBlank()
            ? ""
            : "." + extension.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    return HexFormat.of().formatHex(bytes) + suffix;
  }

  /**
   * Maps a storage key to a concrete path, refusing anything that could
   * escape the storage root.
   */
  private Path resolve(String storageKey) {
    if (storageKey == null || storageKey.isBlank()) {
      throw new StorageException("Invalid storage key");
    }
    // Strict allow-list: hex key, optional single known extension. This
    // rejects "..", separators, absolute paths and URL-encoded tricks
    // before they ever reach the filesystem.
    if (!storageKey.matches("^[0-9a-f]{32}(\\.(pdf|docx|txt))?$")) {
      throw new StorageException("Invalid storage key");
    }
    Path resolved = root.resolve(storageKey).normalize();
    if (!resolved.startsWith(root)) {
      throw new StorageException("Invalid storage key");
    }
    return resolved;
  }

  /** Exposed for tests and diagnostics; never sent to a client. */
  public Path rootPath() {
    return root;
  }
}