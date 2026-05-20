package com.agentsupport.admin;

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
class AdminUserControllerTest {

  @Autowired WebApplicationContext webApplicationContext;
  MockMvc mockMvc;
  MockHttpSession adminSession;

  @BeforeEach
  void setUp() throws Exception {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();

    MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"admin\",\"password\":\"Admin1234!\"}"))
        .andReturn();
    adminSession = (MockHttpSession) loginResult.getRequest().getSession(false);
  }

  @Test
  void list_users_as_admin_returns200() throws Exception {
    mockMvc.perform(get("/api/admin/users").session(adminSession))
        .andExpect(status().isOk());
  }

  @Test
  void list_users_without_auth_returns401() throws Exception {
    mockMvc.perform(get("/api/admin/users"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void approve_pending_user_returns200() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\"newagent\",\"password\":\"Test1234!\",\"name\":\"김설계\","
                + "\"phone\":\"010-9999-8888\",\"gaName\":\"테스트GA\",\"email\":\"agent@test.com\"}"))
        .andExpect(status().isCreated());

    mockMvc.perform(patch("/api/admin/users/newagent/approve")
            .session(adminSession)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"role\":\"AGENT1\"}"))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"newagent\",\"password\":\"Test1234!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("AGENT1"));
  }

  @Test
  void reject_pending_user_returns200() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\"rejectme\",\"password\":\"Test1234!\",\"name\":\"이거절\","
                + "\"phone\":\"010-1111-2222\",\"gaName\":\"GA\",\"email\":\"reject@test.com\"}"))
        .andExpect(status().isCreated());

    mockMvc.perform(patch("/api/admin/users/rejectme/reject")
            .session(adminSession)
            .with(csrf()))
        .andExpect(status().isOk());
  }
}
