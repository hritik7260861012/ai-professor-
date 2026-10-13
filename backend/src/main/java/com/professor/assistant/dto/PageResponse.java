package com.professor.assistant.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Stable pagination envelope. Wrapping {@code Page} directly would leak
 * Spring's unstable internal JSON structure into the public API.
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last) {

  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isFirst(),
        page.isLast());
  }
}