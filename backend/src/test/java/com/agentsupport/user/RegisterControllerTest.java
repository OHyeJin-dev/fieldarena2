package com.agentsupport.user;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RegisterControllerTest {

  @Autowired WebApplicationContext webApplicationContext;
  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();
  }

  private static final String VALID_REGISTER = """
      {
        "id": "testuser01",
        "password": "Test1234!",
        "name": "홍길동",
        "phone": "010-1234-5678",
        "gaName": "테스트GA",
        "email": "test@example.com"
      }
      """;

  @Test
  void register_with_valid_data_returns201() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_REGISTER))
        .andExpect(status().isCreated());
  }

  @Test
  void register_duplicate_id_returns409() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_REGISTER))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_REGISTER))
        .andExpect(status().isConflict());
  }

  @Test
  void register_duplicate_email_returns409() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_REGISTER))
        .andExpect(status().isCreated());

    String differentId = VALID_REGISTER.replace("testuser01", "testuser02");
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(differentId))
        .andExpect(status().isConflict());
  }

  @Test
  void login_with_pending_user_returns401_with_code() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_REGISTER))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"testuser01\",\"password\":\"Test1234!\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void register_blank_fields_returns400() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\"\",\"password\":\"\",\"name\":\"\",\"phone\":\"\",\"gaName\":\"\",\"email\":\"\"}"))
        .andExpect(status().isBadRequest());
  }
}
