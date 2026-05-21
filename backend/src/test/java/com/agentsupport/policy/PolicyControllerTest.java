package com.agentsupport.policy;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PolicyControllerTest {

  @Autowired WebApplicationContext webApplicationContext;
  MockMvc mockMvc;
  MockHttpSession adminSession;
  MockHttpSession agent1Session;

  @BeforeEach
  void setUp() throws Exception {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();

    adminSession = login("admin", "Admin1234!");
    agent1Session = registerAndApprove("ag1", "AGENT1", "test1@x.com", "010-1111-1111");
  }

  private MockHttpSession login(String username, String password) throws Exception {
    MvcResult r = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isOk())
        .andReturn();
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  private MockHttpSession registerAndApprove(String id, String role, String email, String phone) throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\"" + id + "\",\"password\":\"Test1234!\",\"name\":\"홍길동\","
                + "\"phone\":\"" + phone + "\",\"gaName\":\"GA\",\"email\":\"" + email + "\"}"))
        .andExpect(status().isCreated());
    mockMvc.perform(patch("/api/admin/users/" + id + "/approve")
            .session(adminSession)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"role\":\"" + role + "\"}"))
        .andExpect(status().isOk());
    return login(id, "Test1234!");
  }

  private String createCustomerAs(MockHttpSession session, String name) throws Exception {
    MvcResult r = mockMvc.perform(post("/api/customers")
            .session(session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + name + "\",\"phone\":\"010-1234-5678\","
                + "\"birthDate\":\"1990-01-01\",\"gender\":\"M\","
                + "\"email\":\"a@b.com\",\"address\":\"서울\",\"memo\":\"\"}"))
        .andExpect(status().isCreated())
        .andReturn();
    return JsonMapper.builder().build()
        .readTree(r.getResponse().getContentAsString()).get("id").asText();
  }

  private String policyBody(String customerId) {
    return "{\"customerId\":\"" + customerId + "\","
        + "\"productName\":\"무배당 종신보험\","
        + "\"insurerName\":\"삼성생명\","
        + "\"contractDate\":\"2026-05-21\","
        + "\"monthlyPremium\":150000}";
  }

  @Test
  void agent1CanCreatePolicyForOwnCustomer() throws Exception {
    String customerId = createCustomerAs(agent1Session, "김고객");

    mockMvc.perform(post("/api/policies")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(policyBody(customerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.customerId").value(customerId))
        .andExpect(jsonPath("$.status").value("심사 중"))
        .andExpect(jsonPath("$.policyNumber").isString());
  }

  @Test
  void agent1CannotCreatePolicyForOtherAgentCustomer() throws Exception {
    MockHttpSession agent2Session = registerAndApprove("ag2", "AGENT1", "test2@x.com", "010-2222-2222");
    String otherCustomerId = createCustomerAs(agent2Session, "타인고객");

    mockMvc.perform(post("/api/policies")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(policyBody(otherCustomerId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void postWithNonexistentCustomerIdReturns404() throws Exception {
    String randomId = UUID.randomUUID().toString();

    mockMvc.perform(post("/api/policies")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(policyBody(randomId)))
        .andExpect(status().isNotFound());
  }

  @Test
  void adminCanCreatePolicyForAnyCustomer() throws Exception {
    MockHttpSession agent2Session = registerAndApprove("ag2", "AGENT1", "test2@x.com", "010-2222-2222");
    String agent2CustomerId = createCustomerAs(agent2Session, "에이전트2고객");

    mockMvc.perform(post("/api/policies")
            .session(adminSession)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(policyBody(agent2CustomerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.customerId").value(agent2CustomerId));
  }

  @Test
  void policyNumberFollowsExpectedFormat() throws Exception {
    String customerId = createCustomerAs(agent1Session, "형식테스트고객");

    mockMvc.perform(post("/api/policies")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(policyBody(customerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.policyNumber").value(matchesPattern("^C-\\d{4}-\\d{4}-\\d{4}$")));
  }

  @Test
  void sequentialPoliciesOnSameDateGetIncrementingNumbers() throws Exception {
    String customerId1 = createCustomerAs(agent1Session, "첫번째고객");
    String customerId2 = createCustomerAs(agent1Session, "두번째고객");

    MvcResult r1 = mockMvc.perform(post("/api/policies")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(policyBody(customerId1)))
        .andExpect(status().isCreated())
        .andReturn();

    MvcResult r2 = mockMvc.perform(post("/api/policies")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(policyBody(customerId2)))
        .andExpect(status().isCreated())
        .andReturn();

    JsonMapper mapper = JsonMapper.builder().build();
    String policyNumber1 = mapper.readTree(r1.getResponse().getContentAsString()).get("policyNumber").asText();
    String policyNumber2 = mapper.readTree(r2.getResponse().getContentAsString()).get("policyNumber").asText();

    Assertions.assertThat(policyNumber1).endsWith("-0001");
    Assertions.assertThat(policyNumber2).endsWith("-0002");
  }
}
