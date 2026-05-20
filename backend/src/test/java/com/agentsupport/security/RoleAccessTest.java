package com.agentsupport.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
class RoleAccessTest {

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
    agent1Session = registerAndApprove("ag1", "AGENT1");
    agent2Session = registerAndApprove("ag2", "AGENT2");
  }

  private MockHttpSession login(String username, String password) throws Exception {
    MvcResult r = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isOk())
        .andReturn();
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  private MockHttpSession registerAndApprove(String id, String role) throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\"" + id + "\",\"password\":\"Test1234!\",\"name\":\"홍\","
                + "\"phone\":\"010-0000-" + id.substring(2) + id.substring(2) + id.substring(2) + id.substring(2) + "\","
                + "\"gaName\":\"GA\",\"email\":\"" + id + "@x.com\"}"))
        .andExpect(status().isCreated());
    mockMvc.perform(patch("/api/admin/users/" + id + "/approve")
            .session(adminSession)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"role\":\"" + role + "\"}"))
        .andExpect(status().isOk());
    return login(id, "Test1234!");
  }

  @Test
  void agent2_proposals_get_returns403() throws Exception {
    mockMvc.perform(get("/api/proposals").session(agent2Session))
        .andExpect(status().isForbidden());
  }

  @Test
  void agent2_underwriting_get_returns403() throws Exception {
    mockMvc.perform(get("/api/underwriting").session(agent2Session))
        .andExpect(status().isForbidden());
  }

  @Test
  void agent1_proposals_get_returns200() throws Exception {
    mockMvc.perform(get("/api/proposals").session(agent1Session))
        .andExpect(status().isOk());
  }

  @Test
  void agent2_customers_get_returns200() throws Exception {
    mockMvc.perform(get("/api/customers").session(agent2Session))
        .andExpect(status().isOk());
  }

  @Test
  void agent2_claims_get_returns200() throws Exception {
    mockMvc.perform(get("/api/claims").session(agent2Session))
        .andExpect(status().isOk());
  }
}