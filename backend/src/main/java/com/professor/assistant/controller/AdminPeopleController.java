package com.professor.assistant.controller;

import com.professor.assistant.dto.EnrollmentDto;
import com.professor.assistant.dto.EnrollmentRequest;
import com.professor.assistant.dto.TeachingAssignmentDto;
import com.professor.assistant.dto.TeachingAssignmentRequest;
import com.professor.assistant.service.AcademicMapper;
import com.professor.assistant.service.AcademicStructureService;
import com.professor.assistant.web.Principals;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN-only management of the people side of the academic structure:
 * student enrollments and teacher assignments.
 *
 * <p>Gated to ADMIN by {@code SecurityConfig} via {@code /api/v1/admin/**}.
 */
@RestController
@RequestMapping(value = "/api/v1/admin/academic", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Academic administration", description = "ADMIN-only enrollment and teaching assignment management")
public class AdminPeopleController {

  private final AcademicStructureService structure;
  private final AcademicMapper mapper;

  public AdminPeopleController(AcademicStructureService structure, AcademicMapper mapper) {
    this.structure = structure;
    this.mapper = mapper;
  }

  /* ------------------------------- enrollments ----------------------------- */

  @PostMapping(value = "/enrollments", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Enroll a student in a class")
  public ResponseEntity<EnrollmentDto> enroll(
      @Valid @RequestBody EnrollmentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(structure.enrollStudent(request));
  }

  @DeleteMapping("/enrollments/{id}")
  @Operation(summary = "Remove an enrollment")
  public ResponseEntity<Void> unenroll(@PathVariable Long id) {
    structure.unenrollStudent(id);
    return ResponseEntity.noContent().build();
  }

  /* --------------------------- teaching assignments ------------------------ */

  @PostMapping(value = "/teaching-assignments", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Assign a teacher to a subject in a class",
      description = "The subject must already be offered in the class.")
  public ResponseEntity<TeachingAssignmentDto> assignTeacher(
      Authentication authentication, @Valid @RequestBody TeachingAssignmentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            structure.assignTeacher(request, Principals.require(authentication)));
  }

  @DeleteMapping("/teaching-assignments/{id}")
  @Operation(summary = "Remove a teaching assignment")
  public ResponseEntity<Void> removeAssignment(@PathVariable Long id) {
    structure.removeAssignment(id);
    return ResponseEntity.noContent().build();
  }
}