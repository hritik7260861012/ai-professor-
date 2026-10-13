package com.professor.assistant.repository;

import com.professor.assistant.domain.AcademicClass;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AcademicClassRepository
    extends JpaRepository<AcademicClass, Long>, JpaSpecificationExecutor<AcademicClass> {

  Optional<AcademicClass> findBySemesterIdAndCode(Long semesterId, String code);

  boolean existsBySemesterIdAndCode(Long semesterId, String code);

  long countByProgramId(Long programId);

  long countBySemesterId(Long semesterId);
}