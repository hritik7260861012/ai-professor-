package com.professor.assistant;

import com.professor.assistant.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Local-development only: resets an existing administrator's password from
 * an environment-supplied value.
 *
 * <p>This exists because {@link BootstrapAdminRunner} deliberately refuses to
 * overwrite an existing administrator — correct for production, but it means
 * a developer who has forgotten the original bootstrap password is locked out
 * of their own local database with no way back.
 *
 * <p>There is intentionally <b>no HTTP endpoint</b> for this. Password
 * changes happen through an authenticated admin UI; a reset reachable over
 * the network would be an unauthenticated account-takeover primitive.
 *
 * <p>Safety properties:
 *
 * <ul>
 *   <li>The bean only exists at all when
 *       {@code app.bootstrap.admin.reset-password=true}, so it cannot fire by
 *       accident and the default deployment has no reset code path.</li>
 *   <li>It never creates an account. A missing account is reported, not
 *       provisioned.</li>
 *   <li>It only resets an account that already holds ADMIN, so it cannot be
 *       used to rewrite a student's or teacher's password.</li>
 *   <li>The password is hashed with the project's existing BCrypt encoder
 *       and is never written to the log.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(
    prefix = "app.bootstrap.admin",
    name = "reset-password",
    havingValue = "true")
public class BootstrapAdminPasswordResetter implements ApplicationRunner {

  private static final Logger log =
      LoggerFactory.getLogger(BootstrapAdminPasswordResetter.class);

  private final AuthService authService;
  private final String email;
  private final String password;

  public BootstrapAdminPasswordResetter(
      AuthService authService,
      @Value("${app.bootstrap.admin.email:}") String email,
      @Value("${app.bootstrap.admin.password:}") String password) {
    this.authService = authService;
    this.email = email;
    this.password = password;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
      // Never echo the supplied values, not even the email.
      log.warn(
          "Admin password reset was requested but APP_BOOTSTRAP_ADMIN_EMAIL and/or "
              + "APP_BOOTSTRAP_ADMIN_PASSWORD is missing. No password was changed.");
      return;
    }

    AuthService.AdminPasswordReset outcome =
        authService.resetAdminPassword(email, password);

    switch (outcome) {
      case RESET ->
          // Loud on purpose: a password was changed outside the UI, and the
          // operator should know to switch the flag back off.
          log.warn(
              "Admin password reset applied to {}. Set "
                  + "APP_BOOTSTRAP_ADMIN_RESET_PASSWORD=false once you have signed in.",
              email);
      case NO_SUCH_ACCOUNT ->
          log.warn(
              "Admin password reset skipped: no account exists for {}. "
                  + "Use APP_BOOTSTRAP_ADMIN_ENABLED=true to create the first admin.",
              email);
      case NOT_AN_ADMIN ->
          log.warn(
              "Admin password reset skipped: {} is not an administrator. "
                  + "Only an existing ADMIN account can be reset.",
              email);
    }
  }
}