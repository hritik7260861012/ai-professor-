package com.professor.assistant.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.professor.assistant.exception.ApiError;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Returns 403 with the standard {@link ApiError} envelope
 * instead of Spring Security's default error page.
 *
 * <p>The {@link ObjectMapper} is the Spring-managed one, so the
 * {@code ApiError} envelope serializes identically to the one produced
 * by {@code GlobalExceptionHandler} (a bare {@code new ObjectMapper()}
 * lacks the JavaTimeModule and would fail on the {@code Instant}
 * timestamp, turning a 403 into a 500).
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  public RestAccessDeniedHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException, ServletException {
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(
        response.getWriter(),
        ApiError.of(
            HttpStatus.FORBIDDEN,
            "You do not have permission to access this resource",
            request.getRequestURI()));
  }
}