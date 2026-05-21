package com.agentsupport.policy.repository;

import com.agentsupport.policy.entity.Policy;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

  @Query(
      """
      SELECT p FROM Policy p
      WHERE p.agentId = :agentId
        AND (:status IS NULL OR p.status = :status)
        AND (:startDate IS NULL OR p.contractDate >= :startDate)
        AND (:endDate IS NULL OR p.contractDate <= :endDate)
      ORDER BY p.contractDate DESC
      """)
  Page<Policy> findByCondition(
      @Param("agentId") String agentId,
      @Param("status") String status,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      Pageable pageable);

  @Query(
      """
      SELECT COUNT(p) FROM Policy p
      WHERE p.agentId = :agentId
        AND p.contractDate >= :startDate
        AND p.contractDate <= :endDate
      """)
  long countByAgentIdAndContractDateBetween(
      @Param("agentId") String agentId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  @Query(
      """
      SELECT COUNT(p) FROM Policy p
      WHERE p.agentId = :agentId
        AND p.status IN ('심사 중', '서류 보완')
      """)
  long countPendingByAgentId(@Param("agentId") String agentId);

  List<Policy> findTop5ByAgentIdOrderByContractDateDesc(String agentId);

  long countByPolicyNumberStartingWith(String prefix);

  @Query("SELECT p.status, COUNT(p) FROM Policy p WHERE p.agentId = :agentId GROUP BY p.status")
  List<Object[]> countGroupByStatusForAgent(@Param("agentId") String agentId);
}
