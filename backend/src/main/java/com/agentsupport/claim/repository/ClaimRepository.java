package com.agentsupport.claim.repository;

import com.agentsupport.claim.entity.Claim;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

  @Query(
      """
      SELECT c FROM Claim c
      WHERE c.agentId = :agentId
        AND (:status IS NULL OR c.status = :status)
      ORDER BY c.claimDate DESC
      """)
  Page<Claim> findByCondition(
      @Param("agentId") String agentId,
      @Param("status") String status,
      Pageable pageable);

  @Query(
      """
      SELECT COUNT(c) FROM Claim c
      WHERE c.agentId = :agentId
        AND c.status IN ('접수', '심사 중')
      """)
  long countInProgressByAgentId(@Param("agentId") String agentId);
}
