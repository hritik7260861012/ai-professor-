package com.professor.assistant.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * Builds real PDF and DOCX bytes in-memory for tests.
 *
 * <p>Generating fixtures with the same libraries the production code reads
 * keeps the tests self-contained and avoids committing binary files. It also
 * means the fixtures are guaranteed to be genuinely parseable.
 */
public final class DocumentFixtures {

  private DocumentFixtures() {}

  /** A PDF whose single page contains the given lines of text. */
  public static byte[] pdfWithText(String... lines) {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
        stream.setLeading(14.5f);
        stream.newLineAtOffset(50, 750);
        for (String line : lines) {
          stream.showText(sanitizeForPdf(line));
          stream.newLine();
        }
        stream.endText();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Could not build the PDF fixture", e);
    }
  }

  /**
   * A structurally valid PDF with no extractable text - the stand-in for a
   * scanned image. Drawn as a filled rectangle so the file is a real PDF.
   */
  public static byte[] pdfWithoutText() {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        // A filled shape, deliberately no text operators.
        stream.setNonStrokingColor(0.78f, 0.78f, 0.78f);
        stream.addRect(50, 50, 300, 400);
        stream.fill();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Could not build the PDF fixture", e);
    }
  }

  /** A PDF encrypted with an empty user password; opening needs the owner password. */
  public static byte[] passwordProtectedPdf() {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        stream.newLineAtOffset(50, 750);
        stream.showText("Confidential syllabus");
        stream.endText();
      }
      AccessPermission permissions = new AccessPermission();
      StandardProtectionPolicy policy =
          new StandardProtectionPolicy("owner-secret", "user-secret", permissions);
      policy.setEncryptionKeyLength(128);
      document.protect(policy);

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Could not build the encrypted PDF fixture", e);
    }
  }

  /** A DOCX whose body contains the given paragraphs. */
  public static byte[] docxWithParagraphs(String... paragraphs) {
    try (XWPFDocument document = new XWPFDocument()) {
      for (String paragraph : paragraphs) {
        document.createParagraph().createRun().setText(paragraph);
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Could not build the DOCX fixture", e);
    }
  }

  public static byte[] txt(String content) {
    return content.getBytes(StandardCharsets.UTF_8);
  }

  /** Random bytes that are definitely not any supported document format. */
  public static byte[] corrupt() {
    byte[] bytes = new byte[512];
    // Deterministic garbage so failures reproduce exactly.
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) ((i * 31 + 7) & 0xFF);
    }
    return bytes;
  }

  /** PDFBox's standard fonts cannot encode every character. */
  private static String sanitizeForPdf(String line) {
    return line.replaceAll("[^\\x20-\\x7E]", "?");
  }
}