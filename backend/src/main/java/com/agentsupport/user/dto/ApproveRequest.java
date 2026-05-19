package com.agentsupport.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ApproveRequest(
    @NotBlank
    @Pattern(regexp = "AGENT1|AGENT2|ADMIN", message = "유효하지 않은 역할입니다")
    String role) {}
