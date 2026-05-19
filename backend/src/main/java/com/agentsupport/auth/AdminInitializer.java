package com.agentsupport.auth;

import com.agentsupport.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements CommandLineRunner {

  private final UserService userService;

  @Value("${ADMIN_INITIAL_PASSWORD:Admin1234!}")
  private String adminPassword;

  public AdminInitializer(UserService userService) {
    this.userService = userService;
  }

  @Override
  public void run(String... args) {
    userService.createAdminIfAbsent(adminPassword);
  }
}
