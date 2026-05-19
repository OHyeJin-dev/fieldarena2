package com.agentsupport.auth;

import com.agentsupport.user.dto.RegisterRequest;
import com.agentsupport.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
public class RegisterController {

  private final UserService userService;

  public RegisterController(UserService userService) {
    this.userService = userService;
  }

  @Operation(summary = "회원 가입 신청")
  @PostMapping("/register")
  public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
    userService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }
}
