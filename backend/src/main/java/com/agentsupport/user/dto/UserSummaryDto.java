package com.agentsupport.user.dto;

import com.agentsupport.user.entity.User;
import java.time.LocalDateTime;

public record UserSummaryDto(
    String id,
    String name,
    String phone,
    String gaName,
    String email,
    String role,
    String status,
    LocalDateTime createdAt) {

  public static UserSummaryDto from(User u) {
    return new UserSummaryDto(
        u.getId(), u.getName(), u.getPhone(), u.getGaName(),
        u.getEmail(), u.getRole(), u.getStatus(), u.getCreatedAt());
  }
}
