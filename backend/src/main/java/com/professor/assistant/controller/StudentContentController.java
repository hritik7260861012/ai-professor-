package com.professor.assistant.controller;

import com.professor.assistant.dto.StudentContentDto;
import com.professor.assistant.service.ContentReviewService;
import com.professor.assistant.web.Principals;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Student access to released AI-generated material.
 *
 * <p>Read-only by design. Everything reachable here must be student
 * audience, APPROVED, explicitly released by a teacher, not outdated, and
 * belong to a class the student is actively enrolled in. Teacher study
 * material and answer keys are excluded structurally, not by filtering the
 * response afterwards.
 *
 * <p>This is the minimum Phase 5 surface; the fuller student dashboard and
 * export features belong to Phase 6.
 */
@RestController
@RequestMapping(
    value = "/api/v1/student/content",
    produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Student content", description = "Released study material for enrolled classes")
public class StudentContentController {

  private final ContentReviewService review;

  public StudentContentController(ContentReviewService review) {
    this.review = review;
  }

  @GetMapping
  @Operation(
      summary = "List released content for my enrollments",
      description = "Only approved, released, student-facing material is returned.")
  public List<StudentContentDto> list(Authentication authentication) {
    return review.listForStudent(Principals.require(authentication));
  }

  @GetMapping("/{contentId}")
  @Operation(summary = "View one released artifact")
  public StudentContentDto get(Authentication authentication, @PathVariable Long contentId) {
    return review.getForStudent(Principals.require(authentication), contentId);
  }
}