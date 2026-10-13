package com.professor.assistant.service;

import com.professor.assistant.domain.AcademicClass;
import com.professor.assistant.domain.AcademicProgram;
import com.professor.assistant.domain.AcademicYear;
import com.professor.assistant.domain.ClassSubject;
import com.professor.assistant.domain.Department;
import com.professor.assistant.domain.Enrollment;
import com.professor.assistant.domain.Semester;
import com.professor.assistant.domain.Subject;
import com.professor.assistant.domain.TeachingAssignment;
import com.professor.assistant.dto.AcademicYearDto;
import com.professor.assistant.dto.ClassDto;
import com.professor.assistant.dto.ClassSubjectDto;
import com.professor.assistant.dto.DepartmentDto;
import com.professor.assistant.dto.EnrollmentDto;
import com.professor.assistant.dto.ProgramDto;
import com.professor.assistant.dto.SemesterDto;
import com.professor.assistant.dto.SubjectDto;
import com.professor.assistant.dto.TeachingAssignmentDto;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Converts entities to their public read models.
 *
 * <p>Must be called inside a transaction: the {@code @ManyToOne}
 * associations are lazy and {@code spring.jpa.open-in-view} is disabled, so
 * touching them after the transaction closes would fail.
 */
@Component
public class AcademicMapper {

  public DepartmentDto toDto(Department department) {
    return new DepartmentDto(
        department.getId(),
        department.getCode(),
        department.getName(),
        department.getDescription(),
        department.getCreatedAt());
  }

  public ProgramDto toDto(AcademicProgram program) {
    Department department = program.getDepartment();
    return new ProgramDto(
        program.getId(),
        department.getId(),
        department.getCode(),
        program.getCode(),
        program.getName(),
        program.getLevel().name(),
        program.getDurationYears(),
        program.getDescription());
  }

  public AcademicYearDto toDto(AcademicYear year) {
    return new AcademicYearDto(
        year.getId(), year.getName(), year.getStartDate(), year.getEndDate(), year.isCurrent());
  }

  public SemesterDto toDto(Semester semester) {
    return new SemesterDto(
        semester.getId(),
        semester.getAcademicYear().getId(),
        semester.getAcademicYear().getName(),
        semester.getName(),
        semester.getSemesterNumber(),
        semester.getStartDate(),
        semester.getEndDate());
  }

  public SubjectDto toDto(Subject subject) {
    Department department = subject.getDepartment();
    return new SubjectDto(
        subject.getId(),
        department.getId(),
        department.getCode(),
        subject.getProgram() == null ? null : subject.getProgram().getId(),
        subject.getCode(),
        subject.getName(),
        subject.getCredits(),
        subject.getDescription());
  }

  /** {@code enrolledCounts} maps class id to active enrollment count. */
  public ClassDto toDto(AcademicClass academicClass, Map<Long, Long> enrolledCounts) {
    AcademicProgram program = academicClass.getProgram();
    Semester semester = academicClass.getSemester();
    return new ClassDto(
        academicClass.getId(),
        program.getId(),
        program.getCode(),
        program.getName(),
        semester.getId(),
        semester.getName(),
        semester.getAcademicYear().getName(),
        academicClass.getCode(),
        academicClass.getName(),
        academicClass.getCapacity(),
        enrolledCounts.getOrDefault(academicClass.getId(), 0L));
  }

  public ClassSubjectDto toDto(ClassSubject classSubject) {
    Subject subject = classSubject.getSubject();
    return new ClassSubjectDto(
        classSubject.getId(),
        classSubject.getAcademicClass().getId(),
        subject.getId(),
        subject.getCode(),
        subject.getName(),
        subject.getCredits(),
        classSubject.getSemester().getId());
  }

  public EnrollmentDto toDto(Enrollment enrollment) {
    return new EnrollmentDto(
        enrollment.getId(),
        enrollment.getStudent().getId(),
        enrollment.getStudent().getFullName(),
        enrollment.getStudent().getEmail(),
        enrollment.getAcademicClass().getId(),
        enrollment.getAcademicClass().getCode(),
        enrollment.getAcademicClass().getName(),
        enrollment.getStatus().name(),
        enrollment.getEnrolledAt());
  }

  public TeachingAssignmentDto toDto(TeachingAssignment assignment) {
    return new TeachingAssignmentDto(
        assignment.getId(),
        assignment.getTeacher().getId(),
        assignment.getTeacher().getFullName(),
        assignment.getAcademicClass().getId(),
        assignment.getAcademicClass().getCode(),
        assignment.getSubject().getId(),
        assignment.getSubject().getCode(),
        assignment.getSemester().getId(),
        assignment.getSemester().getName(),
        assignment.getAssignedAt());
  }
}