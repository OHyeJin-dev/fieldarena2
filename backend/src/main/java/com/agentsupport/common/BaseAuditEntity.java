package com.agentsupport.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseAuditEntity {

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  protected LocalDateTime createdAt;

  @CreatedBy
  @Column(name = "created_by", nullable = false, updatable = false, length = 50)
  protected String createdBy;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  protected LocalDateTime updatedAt;

  @LastModifiedBy
  @Column(name = "updated_by", nullable = false, length = 50)
  protected String updatedBy;

  public LocalDateTime getCreatedAt() { return createdAt; }
  public String getCreatedBy() { return createdBy; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public String getUpdatedBy() { return updatedBy; }
}
