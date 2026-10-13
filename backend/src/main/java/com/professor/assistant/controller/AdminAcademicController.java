package com.professor.assistant.controller;

import com.professor.assistant.dto.AcademicYearDto;
import com.professor.assistant.dto.AcademicYearRequest;
import com.professor.assistant.dto.ClassDto;
import com.professor.assistant.dto.ClassRequest;
import com.professor.assistant.dto.ClassSubjectDto;
import com.professor.assistant.dto.ClassSubjectRequest;
import com.professor.assistant.dto.DepartmentDto;
import com.professor.assistant.dto.DepartmentRequest;
import com.professor.assistant.dto.ProgramDto;
import com.professor.assistant.dto.ProgramRequest;
import com.professor.assistant.dto.SemesterDto;
import com.professor.assistant.dto.SemesterRequest;
import com.professor.assistant.dto.SubjectDto;
import com.professor.assistant.dto.SubjectRequest;
import com.professor.assistant.service.AcademicMapper;
import com.professor.assistant.service.AcademicQueryService;
import com.professor.assistant.service.AcademicStructureService;
import com.professor.assistant.web.Principals;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
 * ADMIN-only management of the academic structure.
 *
 * <p>Authorization comes from {@code SecurityConfig}, which requires the
 * ADMIN role for everything under {@code /api/v1/admin/**}.
 */
@RestController
@RequestMapping(value = "/api/v1/admin/academic", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Academic administration", description = "ADMIN-only management of the academic structure")
public class AdminAcademicController {

  private final AcademicStructureService structure;
  private final AcademicQueryService query;
  private final AcademicMapper mapper;

  public AdminAcademicController(
      AcademicStructureService structure, AcademicQueryService query, AcademicMapper mapper) {
    this.structure = structure;
    this.query = query;
    this.mapper = mapper;
  }

  /* ------------------------------- departments ----------------------------- */

  @PostMapping(value = "/departments", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Create a department")
  public ResponseEntity<DepartmentDto> createDepartment(@Valid @RequestBody DepartmentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(structure.createDepartment(request));
  }

  @PutMapping(value = "/departments/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Update a department")
  public DepartmentDto updateDepartment(
      @PathVariable Long id, @Valid @RequestBody DepartmentRequest request) {
    return structure.updateDepartment(id, request);
  }

  @DeleteMapping("/departments/{id}")
  @Operation(summary = "Delete a department")
  public ResponseEntity<Void> deleteDepartment(@PathVariable Long id) {
    structure.deleteDepartment(id);
    return ResponseEntity.noContent().build();
  }

  /* -------------------------------- programs ------------------------------- */

  @PostMapping(value = "/programs", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Create a program")
  public ResponseEntity<ProgramDto> createProgram(@Valid @RequestBody ProgramRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(structure.createProgram(request));
  }

  @PutMapping(value = "/programs/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Update a program")
  public ProgramDto updateProgram(@PathVariable Long id, @Valid @RequestBody ProgramRequest request) {
    return structure.updateProgram(id, request);
  }

  @DeleteMapping("/programs/{id}")
  @Operation(summary = "Delete a program")
  public ResponseEntity<Void> deleteProgram(@PathVariable Long id) {
    structure.deleteProgram(id);
    return ResponseEntity.noContent().build();
  }

  /* ----------------------------- academic years ---------------------------- */

  @PostMapping(value = "/academic-years", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Create an academic year")
  public ResponseEntity<AcademicYearDto> createAcademicYear(
      @Valid @RequestBody AcademicYearRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(structure.createAcademicYear(request));
  }

  @PutMapping(value = "/academic-years/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Update an academic year")
  public AcademicYearDto updateAcademicYear(
      @PathVariable Long id, @Valid @RequestBody AcademicYearRequest request) {
    return structure.updateAcademicYear(id, request);
  }

  @DeleteMapping("/academic-years/{id}")
  @Operation(summary = "Delete an academic year")
  public ResponseEntity<Void> deleteAcademicYear(@PathVariable Long id) {
    structure.deleteAcademicYear(id);
    return ResponseEntity.noContent().build();
  }

  /* -------------------------------- semesters ------------------------------ */

  @PostMapping(value = "/semesters", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Create a semester")
  public ResponseEntity<SemesterDto> createSemester(@Valid @RequestBody SemesterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(structure.createSemester(request));
  }

  @PutMapping(value = "/semesters/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Update a semester")
  public SemesterDto updateSemester(
      @PathVariable Long id, @Valid @RequestBody SemesterRequest request) {
    return structure.updateSemester(id, request);
  }

  @DeleteMapping("/semesters/{id}")
  @Operation(summary = "Delete a semester")
  public ResponseEntity<Void> deleteSemester(@PathVariable Long id) {
    structure.deleteSemester(id);
    return ResponseEntity.noContent().build();
  }

  /* -------------------------------- subjects ------------------------------- */

  @PostMapping(value = "/subjects", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Create a subject")
  public ResponseEntity<SubjectDto> createSubject(@Valid @RequestBody SubjectRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(structure.createSubject(request));
  }

  @PutMapping(value = "/subjects/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Update a subject")
  public SubjectDto updateSubject(@PathVariable Long id, @Valid @RequestBody SubjectRequest request) {
    return structure.updateSubject(id, request);
  }

  @DeleteMapping("/subjects/{id}")
  @Operation(summary = "Delete a subject")
  public ResponseEntity<Void> deleteSubject(@PathVariable Long id) {
    structure.deleteSubject(id);
    return ResponseEntity.noContent().build();
  }

  /* --------------------------------- classes ------------------------------- */

  @PostMapping(value = "/classes", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Create a class")
  public ResponseEntity<ClassDto> createClass(
      Authentication authentication, @Valid @RequestBody ClassRequest request) {
    Long id = structure.createClass(request).getId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(query.getClass(Principals.require(authentication), id));
  }

  @PutMapping(value = "/classes/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Update a class")
  public ClassDto updateClass(
      Authentication authentication,
      @PathVariable Long id,
      @Valid @RequestBody ClassRequest request) {
    structure.updateClass(id, request);
    return query.getClass(Principals.require(authentication), id);
  }

  @DeleteMapping("/classes/{id}")
  @Operation(summary = "Delete a class")
  public ResponseEntity<Void> deleteClass(@PathVariable Long id) {
    structure.deleteClass(id);
    return ResponseEntity.noContent().build();
  }

  /* ----------------------------- class subjects ---------------------------- */

  @PostMapping(value = "/classes/{classId}/subjects", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Offer a subject in a class")
  public ResponseEntity<ClassSubjectDto> attachSubject(
      @PathVariable Long classId, @Valid @RequestBody ClassSubjectRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(structure.attachSubject(classId, request));
  }

  @DeleteMapping("/classes/{classId}/subjects/{linkId}")
  @Operation(summary = "Remove a subject from a class")
  public ResponseEntity<Void> detachSubject(@PathVariable Long classId, @PathVariable Long linkId) {
    structure.detachSubject(classId, linkId);
    return ResponseEntity.noContent().build();
  }

  /* ------------------------------- conveniences ---------------------------- */

  @GetMapping("/classes/{classId}/subjects")
  @Operation(summary = "Subjects offered in a class")
  public List<ClassSubjectDto> classSubjects(Authentication authentication, @PathVariable Long classId) {
    return query.listClassSubjects(Principals.require(authentication), classId);
  }
}