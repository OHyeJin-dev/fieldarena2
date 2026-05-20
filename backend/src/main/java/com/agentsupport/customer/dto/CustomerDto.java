package com.agentsupport.customer.dto;

import com.agentsupport.customer.entity.Customer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerDto(
    UUID id,
    String name,
    String phone,
    LocalDate birthDate,
    String gender,
    String email,
    String address,
    String memo,
    LocalDateTime createdAt,
    String createdBy,
    LocalDateTime updatedAt,
    String updatedBy) {

  public static CustomerDto from(Customer c) {
    return new CustomerDto(
        c.getId(), c.getName(), c.getPhone(),
        c.getBirthDate(), c.getGender(), c.getEmail(),
        c.getAddress(), c.getMemo(),
        c.getCreatedAt(), c.getCreatedBy(),
        c.getUpdatedAt(), c.getUpdatedBy());
  }
}
