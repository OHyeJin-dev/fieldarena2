package com.agentsupport.proposal.repository;

import com.agentsupport.proposal.entity.Proposal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProposalRepository extends JpaRepository<Proposal, UUID> {

  @Query(
      """
      SELECT p FROM Proposal p
      WHERE p.agentId = :agentId
        AND (:status IS NULL OR p.status = :status)
      ORDER BY p.proposedDate DESC
      """)
  Page<Proposal> findByCondition(
      @Param("agentId") String agentId,
      @Param("status") String status,
      Pageable pageable);

  @Query(
      """
      SELECT COUNT(p) FROM Proposal p
      WHERE p.agentId = :agentId
        AND p.status IN ('작성 중', '설계 완료')
      """)
  long countActiveByAgentId(@Param("agentId") String agentId);

  @Query(
      """
      SELECT COUNT(p) FROM Proposal p
      WHERE p.agentId = :agentId
        AND p.proposedDate >= :startDate
        AND p.proposedDate <= :endDate
      """)
  long countByAgentIdAndProposedDateBetween(
      @Param("agentId") String agentId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  List<Proposal> findTop5ByAgentIdOrderByProposedDateDesc(String agentId);
}
