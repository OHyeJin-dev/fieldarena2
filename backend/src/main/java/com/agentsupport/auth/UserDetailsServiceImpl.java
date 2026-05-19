package com.agentsupport.auth;

import com.agentsupport.user.entity.User;
import com.agentsupport.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

  private final UserRepository userRepository;

  public UserDetailsServiceImpl(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user = userRepository.findById(username)
        .orElseThrow(() -> new UsernameNotFoundException(username));
    String role = user.getRole() != null ? user.getRole() : "NONE";
    boolean active = "ACTIVE".equals(user.getStatus());
    boolean rejected = "REJECTED".equals(user.getStatus());
    return org.springframework.security.core.userdetails.User.builder()
        .username(user.getId())
        .password(user.getPassword())
        .roles(role)
        .disabled(!active)
        .accountLocked(rejected)
        .build();
  }
}
