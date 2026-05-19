package com.agentsupport.customer.repository;

import com.agentsupport.customer.entity.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

  Page<Customer> findByAgentIdOrderByCreatedAtDesc(String agentId, Pageable pageable);

  Page<Customer> findAllByOrderByCreatedAtDesc(Pageable pageable);

  Optional<Customer> findByIdAndAgentId(UUID id, String agentId);

  long countByAgentId(String agentId);
}
