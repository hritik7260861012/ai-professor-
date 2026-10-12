package com.professor.assistant.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Simple access log filter so every API request is visible in structured form:
 * method, URI, response status, duration and client address.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AccessLogFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long start = System.currentTimeMillis();
    StatusCapturingResponse wrapped = new StatusCapturingResponse(response);
    try {
      chain.doFilter(request, wrapped);
    } finally {
      long duration = System.currentTimeMillis() - start;
      log.info(
          "access: method={} uri={} status={} durationMs={} remote={}",
          request.getMethod(),
          request.getRequestURI(),
          wrapped.getStatus(),
          duration,
          request.getRemoteAddr());
    }
  }

  private static class StatusCapturingResponse extends HttpServletResponseWrapper {

    private int status = 200;

    StatusCapturingResponse(HttpServletResponse response) {
      super(response);
    }

    public int getStatus() {
      return status;
    }

    @Override
    public void setStatus(int sc) {
      super.setStatus(sc);
      this.status = sc;
    }

    @Override
    public void sendError(int sc) throws IOException {
      super.sendError(sc);
      this.status = sc;
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
      super.sendError(sc, msg);
      this.status = sc;
    }
  }
}
