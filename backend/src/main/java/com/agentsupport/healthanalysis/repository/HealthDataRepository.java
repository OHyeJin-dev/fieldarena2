package com.agentsupport.healthanalysis.repository;

import com.agentsupport.healthanalysis.entity.HealthData;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthDataRepository extends JpaRepository<HealthData, UUID> {
}
