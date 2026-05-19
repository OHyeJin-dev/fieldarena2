package com.agentsupport.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "아이디를 입력해주세요") @Size(max = 50) String id,
    @NotBlank(message = "비밀번호를 입력해주세요") @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다") String password,
    @NotBlank(message = "이름을 입력해주세요") String name,
    @NotBlank(message = "연락처를 입력해주세요") String phone,
    @NotBlank(message = "소속 GA를 입력해주세요") String gaName,
    @NotBlank(message = "이메일을 입력해주세요") @Email(message = "이메일 형식이 올바르지 않습니다") String email) {}
