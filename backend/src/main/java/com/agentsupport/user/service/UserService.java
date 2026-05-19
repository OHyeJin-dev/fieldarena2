package com.agentsupport.user.service;

import com.agentsupport.user.dto.ApproveRequest;
import com.agentsupport.user.dto.RegisterRequest;
import com.agentsupport.user.dto.UserSummaryDto;
import com.agentsupport.user.entity.User;
import com.agentsupport.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public void register(RegisterRequest req) {
    if (userRepository.existsById(req.id())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "ID_TAKEN");
    }
    String emailHash = sha256Hex(req.email().toLowerCase());
    if (userRepository.existsByEmailHash(emailHash)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_TAKEN");
    }
    User user = User.create(
        req.id(),
        passwordEncoder.encode(req.password()),
        req.name(),
        req.phone(),
        req.gaName(),
        req.email(),
        emailHash);
    userRepository.save(user);
  }

  public User findById(String id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  public List<UserSummaryDto> findAll(String status) {
    List<User> users = (status == null || status.isBlank())
        ? userRepository.findAll()
        : userRepository.findByStatus(status);
    return users.stream().map(UserSummaryDto::from).toList();
  }

  @Transactional
  public void approve(String id, ApproveRequest req) {
    User user = findById(id);
    user.approve(req.role());
  }

  @Transactional
  public void reject(String id) {
    User user = findById(id);
    user.reject();
  }

  @Transactional
  public void createAdminIfAbsent(String adminPassword) {
    if (!userRepository.existsById("admin")) {
      String emailHash = sha256Hex("admin@agentsupport.internal");
      User admin = User.createAdmin(
          "admin",
          passwordEncoder.encode(adminPassword),
          "관리자",
          "000-0000-0000",
          "system",
          "admin@agentsupport.internal",
          emailHash);
      userRepository.save(admin);
    }
  }

  public static String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
