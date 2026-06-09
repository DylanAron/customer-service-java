package com.customer.repository;

import com.customer.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {
    Optional<Agent> findByUsername(String username);
    List<Agent> findByEnabledTrue();
    List<Agent> findByOnlineTrue();
    long countByOnlineTrue();
}
