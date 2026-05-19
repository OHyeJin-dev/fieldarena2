package com.agentsupport.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CustomerUpdateRequest(
    @NotBlank @Size(max = 50) String name,
    @NotBlank @Size(max = 20) String phone,
    LocalDate birthDate,
    @Size(max = 10) String gender,
    @Size(max = 100) String email,
    @Size(max = 200) String address,
    @Size(max = 2000) String memo) {}