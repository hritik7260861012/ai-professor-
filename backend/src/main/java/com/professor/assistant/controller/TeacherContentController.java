package com.professor.assistant.controller;

import com.professor.assistant.ai.GenerationWorker;
import com.professor.assistant.domain.AiGenerationJob;
import com.professor.assistant.dto.GenerationJobDto;
import com.professor.assistant.dto.GenerationRequestDto;
import com.professor.assistant.dto.GeneratedContentDto;
import com.professor.assistant.dto.PageResponse;
import com.professor.assistant.service.ContentGenerationService;
import com.professor.assistant.service.ContentReviewService;
import com.professor.assistant.web.Principals;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Teacher content-generation workspace.
 *
 * <p>Generation is asynchronous: the submit endpoint returns
 * {@code 202 Accepted} with a job id and the teacher polls the job
 * endpoint for status. No HTTP request is held open during a provider call.
 */
@RestController
@RequestMapping(
    value = "/api/v1/teacher/content",
    produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Teacher content generation", description = "Generate and review syllabus-grounded study material")
public class TeacherContentController {

  private final ContentGenerationService generation;
  private final ContentReviewService review;
  private final GenerationWorker worker;

  public TeacherContentController(
      ContentGenerationService generation, ContentReviewService review, GenerationWorker worker) {
    this.generation = generation;
    this.review = review;
    this.worker = worker;
  }

  /* ------------------------------- generation ------------------------------ */

  @PostMapping(value = "/generations", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Start a generation job",
      description =
          "Returns 202 with a job id immediately. The job runs on a bounded "
              + "executor; poll the job endpoint for the result.")
  public ResponseEntity<GenerationJobDto> submit(
      Authentication authentication, @Valid @RequestBody GenerationRequestDto request) {
    GenerationJobDto job = generation.submit(Principals.require(authentication), request);
    // Hand off only after the job row is committed.
    worker.submit(job.id());
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
  }

  @GetMapping("/generations")
  @Operation(summary = "List my generation jobs, newest first")
  public PageResponse<GenerationJobDto> list(
      Authentication authentication,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return generation.listJobs(Principals.require(authentication), pageable);
  }

  @GetMapping("/generations/{jobId}")
  @Operation(summary = "Get a job's status and, once completed, its content id")
  public GenerationJobDto getJob(Authentication authentication, @PathVariable Long jobId) {
    return generation.getJob(Principals.require(authentication), jobId);
  }

  @PostMapping("/generations/{jobId}/retry")
  @Operation(
      summary = "Retry a failed job",
      description = "Only FAILED and CANCELLED jobs can be retried, so a retry can never duplicate a paid call.")
  public ResponseEntity<GenerationJobDto> retry(
      Authentication authentication, @PathVariable Long jobId) {
    var principal = Principals.require(authentication);
    AiGenerationJob job = generation.requireRetryableJob(principal, jobId);
    GenerationJobDto dto = generation.requeue(job);
    worker.submit(job.getId());
    return ResponseEntity.accepted().body(dto);
  }

  /* -------------------------------- content --------------------------------- */

  @GetMapping("/syllabi/{syllabusId}/content")
  @Operation(summary = "List content generated from a syllabus")
  public PageResponse<GeneratedContentDto> listForSyllabus(
      Authentication authentication,
      @PathVariable Long syllabusId,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return review.listForTeacher(
        Principals.require(authentication), syllabusId, pageable);
  }

  @GetMapping("/syllabi/{syllabusId}/content/recent")
  @Operation(summary = "Recent content for a syllabus, unpaged")
  public List<GeneratedContentDto> recentForSyllabus(
      Authentication authentication, @PathVariable Long syllabusId) {
    return review.recentForSyllabus(Principals.require(authentication), syllabusId);
  }

  @GetMapping("/{contentId}")
  @Operation(summary = "Get one generated artifact")
  public GeneratedContentDto get(Authentication authentication, @PathVariable Long contentId) {
    return review.getForTeacher(Principals.require(authentication), contentId);
  }

  @PutMapping(value = "/{contentId}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Edit a draft artifact")
  public GeneratedContentDto update(
      Authentication authentication,
      @PathVariable Long contentId,
      @Valid @RequestBody ContentEditRequest request) {
    return review.update(
        Principals.require(authentication), contentId, request.title(), request.body(), request.structuredJson());
  }

  @PostMapping(value = "/{contentId}/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Approve an artifact",
      description =
          "Teacher-only material can never be released to students, even when "
              + "releaseToStudents is requested.")
  public GeneratedContentDto approve(
      Authentication authentication,
      @PathVariable Long contentId,
      @RequestBody(required = false) ContentReleaseRequest request) {
    boolean release = request != null && Boolean.TRUE.equals(request.releaseToStudents());
    return review.approve(Principals.require(authentication), contentId, release);
  }

  @PostMapping(value = "/{contentId}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Reject an artifact with a reason")
  public GeneratedContentDto reject(
      Authentication authentication,
      @PathVariable Long contentId,
      @RequestBody(required = false) ContentRejectRequest request) {
    return review.reject(
        Principals.require(authentication), contentId, request == null ? null : request.reason());
  }

  @DeleteMapping("/{contentId}")
  @Operation(summary = "Delete an artifact")
  public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long contentId) {
    review.delete(Principals.require(authentication), contentId);
    return ResponseEntity.noContent().build();
  }

  /* -------------------------------- requests -------------------------------- */

  /** Body edits. Blank fields are left untouched. */
  public record ContentEditRequest(
      @Size(max = 300, message = "Title must be at most 300 characters") String title,
      String body,
      String structuredJson) {}

  /** Release switch, honoured only for student-facing material. */
  public record ContentReleaseRequest(Boolean releaseToStudents) {}

  /** Optional rejection reason. */
  public record ContentRejectRequest(
      @Size(max = 500, message = "Reason must be at most 500 characters") String reason) {}
}