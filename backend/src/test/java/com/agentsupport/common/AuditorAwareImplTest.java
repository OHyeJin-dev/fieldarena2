package com.agentsupport.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditorAwareImplTest {

  private final AuditorAwareImpl auditorAware = new AuditorAwareImpl();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void returns_SYSTEM_when_no_authentication() {
    SecurityContextHolder.clearContext();
    Optional<String> result = auditorAware.getCurrentAuditor();
    assertTrue(result.isPresent());
    assertEquals("SYSTEM", result.get());
  }

  @Test
  void returns_SYSTEM_when_anonymous_authentication() {
    SecurityContextHolder.getContext().setAuthentication(
        new AnonymousAuthenticationToken(
            "key",
            "anonymousUser",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    Optional<String> result = auditorAware.getCurrentAuditor();
    assertEquals("SYSTEM", result.get());
  }

  @Test
  void returns_username_when_authenticated() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            "ohyejin",
            "password",
            AuthorityUtils.createAuthorityList("ROLE_AGENT1")));
    Optional<String> result = auditorAware.getCurrentAuditor();
    assertEquals("ohyejin", result.get());
  }
}