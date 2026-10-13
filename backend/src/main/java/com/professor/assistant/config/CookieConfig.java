package com.professor.assistant.config;

import org.apache.tomcat.util.http.Rfc6265CookieProcessor;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sets {@code SameSite=Lax} on all cookies, which mitigates CSRF
 * while allowing top-level navigation from external sites. Session
 * cookies are also HttpOnly by default in Spring Boot; the
 * {@code Secure} flag is toggled with the
 * {@code SESSION_COOKIE_SECURE} environment variable once
 * HTTPS is terminated in front of the app.
 *
 * <p>The CSRF token is held in the HttpSession rather than a
 * JavaScript-readable cookie, so this customizer only has to cover
 * the session cookie.
 */
@Configuration
public class CookieConfig {

  @Bean
  public WebServerFactoryCustomizer<TomcatServletWebServerFactory>
      sameSiteCookieCustomizer() {
    return factory ->
        factory.addContextCustomizers(
            context -> {
              Rfc6265CookieProcessor processor = new Rfc6265CookieProcessor();
              processor.setSameSiteCookies("Lax");
              context.setCookieProcessor(processor);
            });
  }
}
