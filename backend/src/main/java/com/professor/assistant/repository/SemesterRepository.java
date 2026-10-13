package com.professor.assistant.repository;

import com.professor.assistant.domain.Semester;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SemesterRepository extends JpaRepository<Semester, Long> {

  Optional<Semester> findByAcademicYearIdAndSemesterNumber(Long academicYearId, Integer number);

  List<Semester> findAllByAcademicYearIdOrderBySemesterNumberAsc(Long academicYearId);

  long countByAcademicYearId(Long academicYearId);
}