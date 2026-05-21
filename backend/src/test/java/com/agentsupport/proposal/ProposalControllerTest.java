package com.agentsupport.proposal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.UUID;
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
class ProposalControllerTest {

  @Autowired WebApplicationContext webApplicationContext;
  MockMvc mockMvc;
  MockHttpSession adminSession;
  MockHttpSession agent1Session;
  MockHttpSession agent2Session;

  @BeforeEach
  void setUp() throws Exception {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();

    adminSession = login("admin", "Admin1234!");
    agent1Session = registerAndApprove("ag1", "AGENT1", "test1@x.com", "010-1111-1111");
    agent2Session = registerAndApprove("ag2", "AGENT2", "test2@x.com", "010-2222-2222");
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

  private String createCustomer(MockHttpSession session, String name) throws Exception {
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

  private String proposalBody(String customerId) {
    return "{\"customerId\":\"" + customerId + "\","
        + "\"productName\":\"무배당 종신보험\","
        + "\"insurerName\":\"삼성생명\","
        + "\"monthlyPremium\":150000}";
  }

  @Test
  void agent1CanCreateProposalForOwnCustomer() throws Exception {
    String customerId = createCustomer(agent1Session, "김고객");

    MvcResult result = mockMvc.perform(post("/api/proposals")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(proposalBody(customerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.customerId").value(customerId))
        .andExpect(jsonPath("$.customerName").isString())
        .andReturn();

    String body = result.getResponse().getContentAsString();
    // Masked name should start with '김' followed by masked chars
    org.junit.jupiter.api.Assertions.assertTrue(body.contains("김"));
  }

  @Test
  void agent1CannotCreateProposalForOtherAgentCustomer() throws Exception {
    String otherCustomerId = createCustomer(agent2Session, "타인고객");

    mockMvc.perform(post("/api/proposals")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(proposalBody(otherCustomerId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void postWithNonexistentCustomerIdReturns404() throws Exception {
    String randomId = UUID.randomUUID().toString();

    mockMvc.perform(post("/api/proposals")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(proposalBody(randomId)))
        .andExpect(status().isNotFound());
  }

  @Test
  void adminCanCreateProposalForAnyCustomer() throws Exception {
    String agent2CustomerId = createCustomer(agent2Session, "에이전트2고객");

    mockMvc.perform(post("/api/proposals")
            .session(adminSession)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(proposalBody(agent2CustomerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.customerId").value(agent2CustomerId));
  }
}
