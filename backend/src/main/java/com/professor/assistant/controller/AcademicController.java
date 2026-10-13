package com.professor.assistant.controller;

import com.professor.assistant.dto.AcademicYearDto;
import com.professor.assistant.dto.ClassDto;
import com.professor.assistant.dto.ClassSubjectDto;
import com.professor.assistant.dto.DepartmentDto;
import com.professor.assistant.dto.EnrollmentDto;
import com.professor.assistant.dto.PageResponse;
import com.professor.assistant.dto.ProgramDto;
import com.professor.assistant.dto.SemesterDto;
import com.professor.assistant.dto.SubjectDto;
import com.professor.assistant.dto.TeachingAssignmentDto;
import com.professor.assistant.service.AcademicQueryService;
import com.professor.assistant.web.Principals;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read side of the academic structure for every authenticated role.
 *
 * <p>Departments, programs, academic years and semesters are global
 * reference data and are readable by all. Classes, subjects, rosters and
 * teaching assignments are filtered by {@link AcademicQueryService} so a
 * student only ever sees their enrolled classes and a teacher only the
 * classes and subjects assigned to them.
 */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Academic structure", description = "Role-scoped read access to the academic structure")
public class AcademicController {

  private final AcademicQueryService academic;

  public AcademicController(AcademicQueryService academic) {
    this.academic = academic;
  }

  /* ------------------------------- catalogue ------------------------------- */

  @GetMapping("/departments")
  @Operation(summary = "List departments")
  public PageResponse<DepartmentDto> listDepartments(
      @PageableDefault(size = 20, sort = "name") Pageable pageable) {
    return academic.listDepartments(pageable);
  }

  @GetMapping("/programs")
  @Operation(summary = "List academic programs")
  public PageResponse<ProgramDto> listPrograms(
      @RequestParam(required = false) Long departmentId,
      @PageableDefault(size = 20, sort = "name") Pageable pageable) {
    return academic.listPrograms(departmentId, pageable);
  }

  @GetMapping("/academic-years")
  @Operation(summary = "List academic years")
  public PageResponse<AcademicYearDto> listAcademicYears(
      @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return academic.listAcademicYears(pageable);
  }

  @GetMapping("/semesters")
  @Operation(summary = "List semesters")
  public List<SemesterDto> listSemesters(@RequestParam(required = false) Long academicYearId) {
    return academic.listSemesters(academicYearId);
  }

  /* -------------------------------- classes -------------------------------- */

  @GetMapping("/classes")
  @Operation(
      summary = "List classes visible to the caller",
      description =
          "ADMIN sees all classes, TEACHER sees assigned classes, "
              + "STUDENT sees enrolled classes.")
  public PageResponse<ClassDto> listClasses(
      Authentication authentication,
      @RequestParam(required = false) Long programId,
      @RequestParam(required = false) Long semesterId,
      @PageableDefault(size = 20, sort = "code") Pageable pageable) {
    return academic.listClasses(
        Principals.require(authentication), programId, semesterId, pageable);
  }

  @GetMapping("/classes/{id}")
  @Operation(summary = "Get a class")
  public ClassDto getClass(Authentication authentication, @PathVariable Long id) {
    return academic.getClass(Principals.require(authentication), id);
  }

  @GetMapping("/classes/{id}/subjects")
  @Operation(
      summary = "Subjects offered in a class",
      description =
          "Teachers receive only the subjects assigned to them; students and "
              + "admins receive the full class syllabus.")
  public List<ClassSubjectDto> listClassSubjects(
      Authentication authentication, @PathVariable Long id) {
    return academic.listClassSubjects(Principals.require(authentication), id);
  }

  @GetMapping("/classes/{id}/students")
  @Operation(summary = "Class roster", description = "Visible to administrators and assigned teachers.")
  public List<EnrollmentDto> classRoster(Authentication authentication, @PathVariable Long id) {
    return academic.classRoster(Principals.require(authentication), id);
  }

  @GetMapping("/classes/{id}/teachers")
  @Operation(summary = "Teaching assignments in a class")
  public List<TeachingAssignmentDto> classAssignments(
      Authentication authentication, @PathVariable Long id) {
    return academic.classAssignments(Principals.require(authentication), id);
  }

  /* -------------------------------- subjects ------------------------------- */

  @GetMapping("/subjects")
  @Operation(
      summary = "List subjects visible to the caller",
      description =
          "ADMIN sees every subject; TEACHER and STUDENT see only subjects "
              + "reachable through their assignments or enrollments.")
  public PageResponse<SubjectDto> listSubjects(
      Authentication authentication,
      @RequestParam(required = false) Long departmentId,
      @PageableDefault(size = 20, sort = "code") Pageable pageable) {
    return academic.listSubjects(Principals.require(authentication), departmentId, pageable);
  }

  @GetMapping("/subjects/{id}")
  @Operation(summary = "Get a subject")
  public SubjectDto getSubject(Authentication authentication, @PathVariable Long id) {
    return academic.getSubject(Principals.require(authentication), id);
  }

  /* ---------------------------- personal views ----------------------------- */

  @GetMapping("/me/classes")
  @Operation(summary = "Classes for the signed-in account")
  public List<ClassDto> myClasses(Authentication authentication) {
    return academic.myClasses(Principals.require(authentication));
  }

  @GetMapping("/me/enrollments")
  @Operation(summary = "Enrollments for the signed-in student")
  public List<EnrollmentDto> myEnrollments(Authentication authentication) {
    return academic.myEnrollments(Principals.require(authentication));
  }

  @GetMapping("/me/assignments")
  @Operation(summary = "Teaching assignments for the signed-in teacher")
  public List<TeachingAssignmentDto> myAssignments(Authentication authentication) {
    return academic.myAssignments(Principals.require(authentication));
  }
}