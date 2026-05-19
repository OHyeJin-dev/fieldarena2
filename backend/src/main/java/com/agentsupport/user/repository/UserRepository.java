package com.agentsupport.user.repository;

import com.agentsupport.user.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
  boolean existsByEmailHash(String emailHash);
  List<User> findByStatus(String status);
}
