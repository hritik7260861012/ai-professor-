package com.professor.assistant.repository;

import com.professor.assistant.domain.AcademicProgram;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AcademicProgramRepository
    extends JpaRepository<AcademicProgram, Long>, JpaSpecificationExecutor<AcademicProgram> {

  Optional<AcademicProgram> findByCode(String code);

  boolean existsByCode(String code);

  long countByDepartmentId(Long departmentId);
}