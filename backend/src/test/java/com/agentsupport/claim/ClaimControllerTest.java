package com.agentsupport.claim;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.json.JsonMapper;
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
class ClaimControllerTest {

  @Autowired WebApplicationContext webApplicationContext;
  MockMvc mockMvc;
  MockHttpSession adminSession;
  MockHttpSession agent2Session;
  MockHttpSession otherAgentSession;

  @BeforeEach
  void setUp() throws Exception {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();
    adminSession = login("admin", "Admin1234!");
    agent2Session = registerAndApprove("ag2", "AGENT2", "ag2@x.com", "010-2222-2222");
    otherAgentSession = registerAndApprove("ag3", "AGENT2", "ag3@x.com", "010-3333-3333");
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
            .content("{\"id\":\"" + id + "\",\"password\":\"Test1234!\",\"name\":\"홍\","
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
                + "\"email\":\"a@b.com\",\"address\":\"\",\"memo\":\"\"}"))
        .andExpect(status().isCreated())
        .andReturn();
    return JsonMapper.builder().build()
        .readTree(r.getResponse().getContentAsString()).get("id").asText();
  }

  private String claimBody(String customerId) {
    return "{\"customerId\":\"" + customerId + "\",\"policyNumber\":\"P12345\","
        + "\"insurerName\":\"삼성생명\",\"claimType\":\"실손\","
        + "\"claimAmount\":100000,\"claimDate\":\"2026-05-19\"}";
  }

  @Test
  void agent2_creates_claim_with_own_customer_returns201() throws Exception {
    String cid = createCustomer(agent2Session, "김고객");
    mockMvc.perform(post("/api/claims")
            .session(agent2Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(claimBody(cid)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.customerName").value("김고객"))
        .andExpect(jsonPath("$.status").value("접수"));
  }

  @Test
  void agent_cannot_create_claim_for_other_agent_customer_returns403() throws Exception {
    String cid = createCustomer(otherAgentSession, "타인고객");
    mockMvc.perform(post("/api/claims")
            .session(agent2Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(claimBody(cid)))
        .andExpect(status().isForbidden());
  }

  @Test
  void create_claim_without_auth_returns401() throws Exception {
    mockMvc.perform(post("/api/claims")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(claimBody("00000000-0000-0000-0000-000000000000")))
        .andExpect(status().isUnauthorized());
  }
}
