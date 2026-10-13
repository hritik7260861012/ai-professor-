package com.professor.assistant.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.professor.assistant.config.SyllabusProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Private local storage.
 *
 * <p>The security-relevant behaviour here is that a hostile storage key
 * cannot read or delete anything outside the storage directory.
 */
class LocalPrivateFileStorageServiceTest {

  @TempDir Path tempDir;

  private LocalPrivateFileStorageService storage;

  @BeforeEach
  void setUp() throws Exception {
    SyllabusProperties properties = new SyllabusProperties();
    properties.setStorageDirectory(tempDir.resolve("syllabi").toString());
    storage = new LocalPrivateFileStorageService(properties);
  }

  @Test
  @DisplayName("Stores and reads back the exact bytes")
  void roundTripsContent() {
    byte[] content = "syllabus content".getBytes(StandardCharsets.UTF_8);

    String key = storage.store("pdf", content);

    assertThat(storage.load(key)).isEqualTo(content);
  }

  @Test
  @DisplayName("Generates a random key that is not derived from any filename")
  void generatesRandomKeys() {
    String first = storage.generateKey("pdf");
    String second = storage.generateKey("pdf");

    assertThat(first).isNotEqualTo(second);
    assertThat(first).endsWith(".pdf");
    // 32 hex characters plus the extension.
    assertThat(first).matches("^[0-9a-f]{32}\\.pdf$");
  }

  @Test
  @DisplayName("Ignores any path information in the generated key")
  void generatedKeyNeverCarriesAPath() {
    String key = storage.generateKey("../../evil");

    assertThat(key).doesNotContain("/");
    assertThat(key).doesNotContain("\\");
    assertThat(key).doesNotContain("..");
    assertThat(key).matches("^[0-9a-f]{32}\\.[a-z0-9]*$");
  }

  @Test
  @DisplayName("Stores files inside the configured directory only")
  void storesInsideRoot() throws Exception {
    String key = storage.store("txt", "data".getBytes(StandardCharsets.UTF_8));

    Path stored = tempDir.resolve("syllabi").resolve(key);
    assertThat(Files.isRegularFile(stored)).isTrue();
    assertThat(stored.toAbsolutePath().normalize())
        .startsWith(tempDir.resolve("syllabi").toAbsolutePath().normalize());
  }

  @Test
  @DisplayName("Deletes stored files and tolerates deleting twice")
  void deletesFiles() {
    String key = storage.store("txt", "data".getBytes(StandardCharsets.UTF_8));

    storage.delete(key);
    storage.delete(key);

    assertThatThrownBy(() -> storage.load(key)).isInstanceOf(StorageException.class);
  }

  @Test
  @DisplayName("Rejects path traversal keys instead of following them")
  void rejectsPathTraversalKeys() {
    List<String> hostileKeys =
        List.of(
            "../secret.txt",
            "../../etc/passwd",
            "..%2F..%2Fetc%2Fpasswd",
            "/etc/passwd",
            "subdir/file.txt",
            "..",
            ".",
            "not-a-valid-key");

    for (String key : hostileKeys) {
      assertThatThrownBy(() -> storage.load(key))
          .as("expected key %s to be rejected", key)
          .isInstanceOf(StorageException.class)
          .hasMessageContaining("Invalid storage key");
    }
  }

  @Test
  @DisplayName("Rejects a null or blank key")
  void rejectsEmptyKeys() {
    assertThatThrownBy(() -> storage.load(null)).isInstanceOf(StorageException.class);
    assertThatThrownBy(() -> storage.load("   ")).isInstanceOf(StorageException.class);
  }

  @Test
  @DisplayName("Accepts a well-formed key that does not exist")
  void missingWellFormedKeyFailsCleanly() {
    assertThatThrownBy(() -> storage.load("0123456789abcdef0123456789abcdef.pdf"))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("not found");
  }
}