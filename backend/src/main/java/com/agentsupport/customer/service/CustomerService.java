package com.agentsupport.customer.service;

import com.agentsupport.common.dto.PageResponse;
import com.agentsupport.customer.dto.CustomerCreateRequest;
import com.agentsupport.customer.dto.CustomerDto;
import com.agentsupport.customer.dto.CustomerUpdateRequest;
import com.agentsupport.customer.entity.Customer;
import com.agentsupport.customer.repository.CustomerRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CustomerService {

  private final CustomerRepository customerRepository;

  public CustomerService(CustomerRepository customerRepository) {
    this.customerRepository = customerRepository;
  }

  public PageResponse<CustomerDto> findCustomers(String agentId, boolean isAdmin, int page, int size) {
    Pageable pageable = PageRequest.of(page, Math.min(size, 100));
    Page<Customer> result = isAdmin
        ? customerRepository.findAllByOrderByCreatedAtDesc(pageable)
        : customerRepository.findByAgentIdOrderByCreatedAtDesc(agentId, pageable);
    return PageResponse.from(result.map(CustomerDto::from));
  }

  public CustomerDto findOne(UUID id, String agentId, boolean isAdmin) {
    Customer c = loadOwned(id, agentId, isAdmin);
    return CustomerDto.from(c);
  }

  @Transactional
  public CustomerDto create(String agentId, CustomerCreateRequest req) {
    Customer c = Customer.create(
        agentId, req.name(), req.phone(),
        req.birthDate(), req.gender(), req.email(), req.address(), req.memo());
    return CustomerDto.from(customerRepository.save(c));
  }

  @Transactional
  public CustomerDto update(UUID id, String agentId, boolean isAdmin, CustomerUpdateRequest req) {
    Customer c = loadOwned(id, agentId, isAdmin);
    c.update(req.name(), req.phone(), req.birthDate(),
        req.gender(), req.email(), req.address(), req.memo());
    return CustomerDto.from(c);
  }

  @Transactional
  public void delete(UUID id, String agentId, boolean isAdmin) {
    Customer c = loadOwned(id, agentId, isAdmin);
    customerRepository.delete(c);
  }

  private Customer loadOwned(UUID id, String agentId, boolean isAdmin) {
    if (isAdmin) {
      return customerRepository.findById(id)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "고객을 찾을 수 없습니다"));
    }
    return customerRepository.findByIdAndAgentId(id, agentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다"));
  }
}
