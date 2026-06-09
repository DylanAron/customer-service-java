package com.customer.repository;

import com.customer.entity.CsUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CsUserRepository extends JpaRepository<CsUser, Long> {
    Optional<CsUser> findByUserId(String userId);
    Optional<CsUser> findByUsername(String username);
}
