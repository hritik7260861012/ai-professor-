package com.professor.assistant.repository;

import com.professor.assistant.domain.Department;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

  Optional<Department> findByCode(String code);

  boolean existsByCode(String code);
}