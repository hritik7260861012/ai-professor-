package com.professor.assistant.controller;

import com.professor.assistant.dto.SyllabusDetailDto;
import com.professor.assistant.service.SyllabusService;
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
 * Student view of approved syllabi.
 *
 * <p>Only {@code APPROVED} records for actively enrolled classes are ever
 * returned. Drafts, failed extractions and rejected syllabi are treated as
 * non-existent, so a student cannot probe for them.
 */
@RestController
@RequestMapping(value = "/api/v1/student/syllabi", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Student syllabus", description = "Approved syllabus content for enrolled classes")
public class StudentSyllabusController {

  private final SyllabusService syllabusService;

  public StudentSyllabusController(SyllabusService syllabusService) {
    this.syllabusService = syllabusService;
  }

  @GetMapping
  @Operation(
      summary = "List approved syllabi for my enrollments",
      description = "Newest approved version per class and subject.")
  public List<SyllabusDetailDto> list(Authentication authentication) {
    return syllabusService.listForStudent(Principals.require(authentication));
  }

  @GetMapping("/{id}")
  @Operation(summary = "View an approved syllabus")
  public SyllabusDetailDto get(Authentication authentication, @PathVariable Long id) {
    return syllabusService.getForStudent(Principals.require(authentication), id);
  }
}