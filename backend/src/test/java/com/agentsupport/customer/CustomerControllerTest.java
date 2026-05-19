package com.agentsupport.customer;

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
class CustomerControllerTest {

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

  private String createBody(String name) {
    return "{\"name\":\"" + name + "\",\"phone\":\"010-1234-5678\","
        + "\"birthDate\":\"1990-01-01\",\"gender\":\"M\","
        + "\"email\":\"a@b.com\",\"address\":\"서울\",\"memo\":\"\"}";
  }

  @Test
  void agent2_creates_customer_returns201() throws Exception {
    mockMvc.perform(post("/api/customers")
            .session(agent2Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody("김고객")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("김고객"));
  }

  @Test
  void agent1_lists_only_own_customers() throws Exception {
    mockMvc.perform(post("/api/customers")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody("A고객")))
        .andExpect(status().isCreated());
    mockMvc.perform(post("/api/customers")
            .session(agent2Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody("B고객")))
        .andExpect(status().isCreated());

    mockMvc.perform(get("/api/customers").session(agent1Session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].name").value("A고객"));
  }

  @Test
  void admin_lists_all_customers() throws Exception {
    mockMvc.perform(post("/api/customers")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody("A고객")))
        .andExpect(status().isCreated());
    mockMvc.perform(post("/api/customers")
            .session(agent2Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody("B고객")))
        .andExpect(status().isCreated());

    mockMvc.perform(get("/api/customers").session(adminSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2));
  }

  @Test
  void agent_cannot_access_other_agent_customer_returns403() throws Exception {
    MvcResult r = mockMvc.perform(post("/api/customers")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody("A고객")))
        .andExpect(status().isCreated())
        .andReturn();
    String id = JsonMapper.builder().build()
        .readTree(r.getResponse().getContentAsString()).get("id").asText();

    mockMvc.perform(get("/api/customers/" + id).session(agent2Session))
        .andExpect(status().isForbidden());
  }

  @Test
  void list_without_auth_returns401() throws Exception {
    mockMvc.perform(get("/api/customers"))
        .andExpect(status().isUnauthorized());
  }
}
