package com.professor.assistant.controller;

import com.professor.assistant.dto.PageResponse;
import com.professor.assistant.dto.SyllabusDecisionRequest;
import com.professor.assistant.dto.SyllabusDetailDto;
import com.professor.assistant.dto.SyllabusDto;
import com.professor.assistant.dto.SyllabusStructureRequest;
import com.professor.assistant.service.SyllabusService;
import com.professor.assistant.web.Principals;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Teacher-facing syllabus management.
 *
 * <p>Every action is authorized server-side: the uploader must hold a
 * teaching assignment for the syllabus' (class, subject) pair. No client
 * supplied teacher id is accepted anywhere on these routes.
 */
@RestController
@RequestMapping(value = "/api/v1/teacher/syllabi", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Teacher syllabus", description = "Upload, review and approve syllabi for assigned classes")
public class TeacherSyllabusController {

  private final SyllabusService syllabusService;

  public TeacherSyllabusController(SyllabusService syllabusService) {
    this.syllabusService = syllabusService;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Upload a syllabus",
      description =
          "Stores the file privately, extracts text and derives a unit/topic "
              + "outline. Re-uploading the same class and subject creates a "
              + "new version rather than replacing the previous one.")
  public ResponseEntity<SyllabusDetailDto> upload(
      Authentication authentication,
      @RequestParam Long classId,
      @RequestParam Long subjectId,
      @RequestPart("file") MultipartFile file) {
    SyllabusDetailDto created =
        syllabusService.upload(Principals.require(authentication), classId, subjectId, file);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping
  @Operation(summary = "List syllabi uploaded by the signed-in teacher")
  public PageResponse<SyllabusDto> list(
      Authentication authentication,
      @PageableDefault(size = 20, sort = "uploadedAt") Pageable pageable) {
    return syllabusService.listMine(Principals.require(authentication), pageable);
  }

  @GetMapping("/{id}")
  @Operation(summary = "View a syllabus with its structure and extracted text")
  public SyllabusDetailDto get(Authentication authentication, @PathVariable Long id) {
    return syllabusService.getForTeacher(Principals.require(authentication), id);
  }

  @GetMapping(value = "/{id}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @Operation(
      summary = "Download the original file",
      description =
          "Authorized download. The storage path is never exposed; the file is "
              + "streamed through this endpoint.")
  public ResponseEntity<byte[]> download(Authentication authentication, @PathVariable Long id) {
    SyllabusService.DownloadPayload payload =
        syllabusService.downloadForTeacher(Principals.require(authentication), id);

    ContentDisposition disposition =
        ContentDisposition.attachment()
            // The filename was sanitized on upload, and quoting protects the
            // header from injection.
            .filename(payload.filename())
            .build();

    // The real content type is preserved so the browser can save the file
    // with the right extension; Content-Disposition forces a download
    // instead of inline rendering.
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .contentType(MediaType.parseMediaType(payload.contentType()))
        .contentLength(payload.content().length)
        .body(payload.content());
  }

  @GetMapping("/{id}/extracted-content")
  @Operation(summary = "Retrieve the extracted text and parsed outline")
  public SyllabusDetailDto extractedContent(Authentication authentication, @PathVariable Long id) {
    return syllabusService.getForTeacher(Principals.require(authentication), id);
  }

  @PutMapping(value = "/{id}/structure", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Replace the unit and topic structure",
      description =
          "Only allowed while the syllabus is in an editable state; an "
              + "approved syllabus is locked and must be re-uploaded to change.")
  public SyllabusDetailDto updateStructure(
      Authentication authentication,
      @PathVariable Long id,
      @Valid @RequestBody SyllabusStructureRequest request) {
    return syllabusService.updateStructure(Principals.require(authentication), id, request);
  }

  @PostMapping("/{id}/approve")
  @Operation(summary = "Approve the reviewed structure", description = "Makes the syllabus visible to enrolled students.")
  public SyllabusDetailDto approve(Authentication authentication, @PathVariable Long id) {
    return syllabusService.approve(Principals.require(authentication), id);
  }

  @PostMapping("/{id}/reject")
  @Operation(
      summary = "Reject the syllabus with a reason",
      description = "The body is optional; without one the syllabus is simply marked rejected.")
  public SyllabusDetailDto reject(
      Authentication authentication,
      @PathVariable Long id,
      @RequestBody(required = false) SyllabusDecisionRequest request) {
    return syllabusService.reject(
        Principals.require(authentication), id, request == null ? new SyllabusDecisionRequest(null) : request);
  }

  @PostMapping("/{id}/retry-extraction")
  @Operation(
      summary = "Retry a failed extraction",
      description = "Re-runs extraction against the original stored file; the upload is kept.")
  public SyllabusDetailDto retry(Authentication authentication, @PathVariable Long id) {
    return syllabusService.retryExtraction(Principals.require(authentication), id);
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete a syllabus and its stored file")
  public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
    syllabusService.delete(Principals.require(authentication), id);
    return ResponseEntity.noContent().build();
  }
}