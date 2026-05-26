package com.agentsupport.healthanalysis.repository;

import com.agentsupport.healthanalysis.RiskGrade;
import com.agentsupport.healthanalysis.entity.HealthAnalysis;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HealthAnalysisRepository extends JpaRepository<HealthAnalysis, UUID> {

  Optional<HealthAnalysis> findByCustomer_Id(UUID customerId);

  List<HealthAnalysis> findByCustomer_IdIn(List<UUID> customerIds);

  @Query("SELECT h FROM HealthAnalysis h WHERE h.analyzedBy = :analyzedBy ORDER BY h.analyzedAt DESC")
  List<HealthAnalysis> findRecentByAnalyzedBy(@Param("analyzedBy") String analyzedBy, Pageable pageable);

  @Query("SELECT h FROM HealthAnalysis h ORDER BY h.analyzedAt DESC")
  List<HealthAnalysis> findAllRecent(Pageable pageable);

  long countByAnalyzedBy(String analyzedBy);
  long countByAnalyzedByAndRiskGrade(String analyzedBy, RiskGrade riskGrade);
  long countByRiskGrade(RiskGrade riskGrade);
}
