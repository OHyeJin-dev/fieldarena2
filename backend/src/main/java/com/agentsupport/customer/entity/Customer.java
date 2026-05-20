package com.agentsupport.customer.entity;

import com.agentsupport.common.BaseAuditEntity;
import com.agentsupport.security.PiiAttributeConverter;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "agent_id", nullable = false, length = 50)
  private String agentId;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(nullable = false, length = 500)
  private String name;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(nullable = false, length = 500)
  private String phone;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Column(length = 10)
  private String gender;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(length = 500)
  private String email;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(length = 1000)
  private String address;

  @Column(columnDefinition = "TEXT")
  private String memo;

  protected Customer() {}

  public static Customer create(
      String agentId, String name, String phone,
      LocalDate birthDate, String gender, String email, String address, String memo) {
    Customer c = new Customer();
    c.agentId = agentId;
    c.name = name;
    c.phone = phone;
    c.birthDate = birthDate;
    c.gender = gender;
    c.email = email;
    c.address = address;
    c.memo = memo;
    return c;
  }

  public void update(
      String name, String phone, LocalDate birthDate,
      String gender, String email, String address, String memo) {
    this.name = name;
    this.phone = phone;
    this.birthDate = birthDate;
    this.gender = gender;
    this.email = email;
    this.address = address;
    this.memo = memo;
  }

  public UUID getId() { return id; }
  public String getAgentId() { return agentId; }
  public String getName() { return name; }
  public String getPhone() { return phone; }
  public LocalDate getBirthDate() { return birthDate; }
  public String getGender() { return gender; }
  public String getEmail() { return email; }
  public String getAddress() { return address; }
  public String getMemo() { return memo; }
}
