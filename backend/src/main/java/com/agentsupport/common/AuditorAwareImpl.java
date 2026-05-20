package com.agentsupport.common;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<String> {

  @Override
  public Optional<String> getCurrentAuditor() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null
        || !auth.isAuthenticated()
        || "anonymousUser".equals(auth.getPrincipal())) {
      return Optional.of("SYSTEM");
    }
    return Optional.of(auth.getName());
  }
}