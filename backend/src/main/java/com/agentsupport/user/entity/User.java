package com.agentsupport.user.entity;

import com.agentsupport.common.BaseAuditEntity;
import com.agentsupport.security.PiiAttributeConverter;
import jakarta.persistence.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class User extends BaseAuditEntity {

  @Id
  @Column(length = 50)
  private String id;

  @Column(nullable = false, length = 255)
  private String password;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(nullable = false, length = 500)
  private String name;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(nullable = false, length = 500)
  private String phone;

  @Column(name = "ga_name", nullable = false, length = 100)
  private String gaName;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(nullable = false, length = 500)
  private String email;

  @Column(name = "email_hash", nullable = false, length = 64, unique = true)
  private String emailHash;

  @Column(length = 20)
  private String role;

  @Column(nullable = false, length = 20)
  private String status;

  protected User() {}

  public static User create(
      String id, String password, String name, String phone,
      String gaName, String email, String emailHash) {
    User u = new User();
    u.id = id;
    u.password = password;
    u.name = name;
    u.phone = phone;
    u.gaName = gaName;
    u.email = email;
    u.emailHash = emailHash;
    u.role = null;
    u.status = "PENDING";
    return u;
  }

  public static User createAdmin(
      String id, String password, String name, String phone,
      String gaName, String email, String emailHash) {
    User u = create(id, password, name, phone, gaName, email, emailHash);
    u.role = "ADMIN";
    u.status = "ACTIVE";
    return u;
  }

  public void approve(String role) {
    this.role = role;
    this.status = "ACTIVE";
  }

  public void reject() {
    this.status = "REJECTED";
  }

  public String getId() { return id; }
  public String getPassword() { return password; }
  public String getName() { return name; }
  public String getPhone() { return phone; }
  public String getGaName() { return gaName; }
  public String getEmail() { return email; }
  public String getEmailHash() { return emailHash; }
  public String getRole() { return role; }
  public String getStatus() { return status; }
}
