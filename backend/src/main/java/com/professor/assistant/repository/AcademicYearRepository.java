package com.professor.assistant.repository;

import com.professor.assistant.domain.AcademicYear;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {

  Optional<AcademicYear> findByName(String name);

  boolean existsByName(String name);

  List<AcademicYear> findAllByCurrentTrue();
}