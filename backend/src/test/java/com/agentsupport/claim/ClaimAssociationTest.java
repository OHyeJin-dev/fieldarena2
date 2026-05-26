package com.agentsupport.claim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.agentsupport.claim.entity.Claim;
import com.agentsupport.claim.repository.ClaimRepository;
import com.agentsupport.customer.entity.Customer;
import com.agentsupport.customer.repository.CustomerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ClaimAssociationTest {

  @Autowired ClaimRepository claimRepository;
  @Autowired CustomerRepository customerRepository;

  @Test
  @Transactional
  void claim_persists_customer_association_and_getCustomerId_delegates() {
    Customer customer = customerRepository.save(
        Customer.create("admin", "관계테스트", "010-1111-2222",
            LocalDate.of(1990, 1, 1), "M", null, null, null));

    Claim claim = Claim.create(
        "admin", customer, "P-ASSOC-001", "관계테스트",
        "삼성생명", "실손", BigDecimal.valueOf(1_000_000), "접수", LocalDate.now());

    Claim saved = claimRepository.save(claim);
    UUID savedId = saved.getId();

    Claim reloaded = claimRepository.findById(savedId).orElseThrow();
    assertNotNull(reloaded.getCustomer(), "association must be populated");
    assertEquals(customer.getId(), reloaded.getCustomer().getId());
    assertEquals(customer.getId(), reloaded.getCustomerId(),
        "delegate getCustomerId() must match association id");
    assertEquals("관계테스트", reloaded.getCustomer().getName(),
        "lazy load through association should return real customer");
  }
}