package com.agentsupport.admin;

import com.agentsupport.user.dto.ApproveRequest;
import com.agentsupport.user.dto.UserSummaryDto;
import com.agentsupport.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

  private final UserService userService;

  public AdminUserController(UserService userService) {
    this.userService = userService;
  }

  @Operation(summary = "사용자 목록 조회")
  @GetMapping
  public ResponseEntity<List<UserSummaryDto>> list(
      @RequestParam(required = false) String status) {
    return ResponseEntity.ok(userService.findAll(status));
  }

  @Operation(summary = "가입 승인 및 역할 지정")
  @PatchMapping("/{id}/approve")
  public ResponseEntity<Void> approve(
      @PathVariable String id,
      @Valid @RequestBody ApproveRequest request) {
    userService.approve(id, request);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "가입 거절")
  @PatchMapping("/{id}/reject")
  public ResponseEntity<Void> reject(@PathVariable String id) {
    userService.reject(id);
    return ResponseEntity.ok().build();
  }
}
