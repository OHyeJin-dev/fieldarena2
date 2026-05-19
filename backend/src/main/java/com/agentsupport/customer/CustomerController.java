package com.agentsupport.customer;

import com.agentsupport.common.dto.PageResponse;
import com.agentsupport.customer.dto.CustomerCreateRequest;
import com.agentsupport.customer.dto.CustomerDto;
import com.agentsupport.customer.dto.CustomerUpdateRequest;
import com.agentsupport.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@Tag(name = "고객")
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

  private final CustomerService customerService;

  public CustomerController(CustomerService customerService) {
    this.customerService = customerService;
  }

  @GetMapping
  public PageResponse<CustomerDto> list(
      Authentication auth,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return customerService.findCustomers(auth.getName(), isAdmin(auth), page, size);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CustomerDto create(Authentication auth, @Valid @RequestBody CustomerCreateRequest req) {
    return customerService.create(auth.getName(), req);
  }

  @GetMapping("/{id}")
  public CustomerDto get(Authentication auth, @PathVariable UUID id) {
    return customerService.findOne(id, auth.getName(), isAdmin(auth));
  }

  @PutMapping("/{id}")
  public CustomerDto update(
      Authentication auth, @PathVariable UUID id,
      @Valid @RequestBody CustomerUpdateRequest req) {
    return customerService.update(id, auth.getName(), isAdmin(auth), req);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(Authentication auth, @PathVariable UUID id) {
    customerService.delete(id, auth.getName(), isAdmin(auth));
  }

  private boolean isAdmin(Authentication auth) {
    for (GrantedAuthority a : auth.getAuthorities()) {
      if ("ROLE_ADMIN".equals(a.getAuthority())) return true;
    }
    return false;
  }
}
