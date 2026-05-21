package com.agentsupport.healthanalysis;

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
class HealthAnalysisControllerTest {

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

  private String createCustomer(MockHttpSession session, String name, String phone) throws Exception {
    MvcResult r = mockMvc.perform(post("/api/customers")
            .session(session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + name + "\",\"phone\":\"" + phone + "\","
                + "\"birthDate\":\"1990-01-01\",\"gender\":\"M\","
                + "\"email\":\"a@b.com\",\"address\":\"서울\",\"memo\":\"\"}"))
        .andExpect(status().isCreated())
        .andReturn();
    return JsonMapper.builder().build()
        .readTree(r.getResponse().getContentAsString()).get("id").asText();
  }

  private String analysisBody(String customerId, String scenario) {
    return "{\"customerId\":\"" + customerId + "\",\"scenario\":\"" + scenario + "\"}";
  }

  @Test
  void agent2_returns_403_on_post() throws Exception {
    String customerId = createCustomer(agent2Session, "고객A", "010-9999-0001");
    mockMvc.perform(post("/api/health-analyses")
            .session(agent2Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(analysisBody(customerId, "NORMAL")))
        .andExpect(status().isForbidden());
  }

  @Test
  void agent1_can_analyze_own_customer() throws Exception {
    String customerId = createCustomer(agent1Session, "고객B", "010-9999-0002");
    mockMvc.perform(post("/api/health-analyses")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(analysisBody(customerId, "NORMAL")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerId").value(customerId));
  }

  @Test
  void agent1_cannot_analyze_others_customer() throws Exception {
    String customerId = createCustomer(agent2Session, "고객C", "010-9999-0003");
    mockMvc.perform(post("/api/health-analyses")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(analysisBody(customerId, "NORMAL")))
        .andExpect(status().isForbidden());
  }

  @Test
  void admin_can_analyze_any_customer() throws Exception {
    String customerId = createCustomer(agent1Session, "고객D", "010-9999-0004");
    mockMvc.perform(post("/api/health-analyses")
            .session(adminSession)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(analysisBody(customerId, "NORMAL")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerId").value(customerId));
  }

  @Test
  void by_customers_filters_out_unauthorized_ids() throws Exception {
    String ownId = createCustomer(agent1Session, "고객E", "010-9999-0005");
    String otherId = createCustomer(agent2Session, "고객F", "010-9999-0006");

    // Create analyses so the map has entries
    mockMvc.perform(post("/api/health-analyses")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(analysisBody(ownId, "NORMAL")))
        .andExpect(status().isOk());

    // admin creates analysis for the other customer
    mockMvc.perform(post("/api/health-analyses")
            .session(adminSession)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(analysisBody(otherId, "NORMAL")))
        .andExpect(status().isOk());

    MvcResult result = mockMvc.perform(get("/api/health-analyses")
            .session(agent1Session)
            .param("customerIds", ownId, otherId))
        .andExpect(status().isOk())
        .andReturn();

    String json = result.getResponse().getContentAsString();
    org.junit.jupiter.api.Assertions.assertTrue(json.contains(ownId),
        "response should contain own customer id");
    org.junit.jupiter.api.Assertions.assertFalse(json.contains(otherId),
        "response should NOT contain other agent's customer id");
  }

  @Test
  void summary_returns_self_only_for_agent1() throws Exception {
    // agent1 creates 1 analysis
    String agent1CustomerId = createCustomer(agent1Session, "고객G", "010-9999-0007");
    mockMvc.perform(post("/api/health-analyses")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(analysisBody(agent1CustomerId, "NORMAL")))
        .andExpect(status().isOk());

    // admin creates 2 more analyses (for customers belonging to agent1, so admin can analyze)
    String customer2Id = createCustomer(agent1Session, "고객H", "010-9999-0008");
    String customer3Id = createCustomer(agent1Session, "고객I", "010-9999-0009");
    mockMvc.perform(post("/api/health-analyses")
            .session(adminSession)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(analysisBody(customer2Id, "HYPERTENSION")))
        .andExpect(status().isOk());
    mockMvc.perform(post("/api/health-analyses")
            .session(adminSession)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(analysisBody(customer3Id, "DIABETES")))
        .andExpect(status().isOk());

    // agent1 summary should show total=1 (only their own analyses)
    mockMvc.perform(get("/api/health-analyses/summary")
            .session(agent1Session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));
  }
}
