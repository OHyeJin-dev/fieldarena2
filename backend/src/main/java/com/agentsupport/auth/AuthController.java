package com.agentsupport.auth;

import com.agentsupport.auth.dto.LoginRequest;
import com.agentsupport.auth.dto.MeResponse;
import com.agentsupport.user.entity.User;
import com.agentsupport.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthenticationManager authenticationManager;
  private final UserService userService;
  private final HttpSessionSecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();

  public AuthController(AuthenticationManager authenticationManager, UserService userService) {
    this.authenticationManager = authenticationManager;
    this.userService = userService;
  }

  @Operation(summary = "로그인")
  @PostMapping("/login")
  public ResponseEntity<?> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    try {
      Authentication auth =
          authenticationManager.authenticate(
              new UsernamePasswordAuthenticationToken(request.username(), request.password()));

      User user = userService.findById(auth.getName());
      if ("PENDING".equals(user.getStatus())) {
        return ResponseEntity.status(401).body(Map.of("code", "PENDING_APPROVAL"));
      }
      if ("REJECTED".equals(user.getStatus())) {
        return ResponseEntity.status(401).body(Map.of("code", "ACCOUNT_REJECTED"));
      }

      SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(auth);
      SecurityContextHolder.setContext(context);
      securityContextRepository.saveContext(context, httpRequest, httpResponse);
      return ResponseEntity.ok(new MeResponse(auth.getName(), user.getRole()));
    } catch (AuthenticationException e) {
      return ResponseEntity.status(401).build();
    }
  }

  @Operation(summary = "로그아웃")
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request) {
    var session = request.getSession(false);
    if (session != null) session.invalidate();
    SecurityContextHolder.clearContext();
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "현재 세션 사용자 확인")
  @GetMapping("/me")
  public ResponseEntity<MeResponse> me(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      return ResponseEntity.status(401).build();
    }
    User user = userService.findById(auth.getName());
    return ResponseEntity.ok(new MeResponse(auth.getName(), user.getRole()));
  }
}
