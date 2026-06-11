package com.customer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled task that assigns unassigned users to online agents.
 * <p>
 * Triggers every 5 minutes, each time assigning up to 3 unassigned users
 * per online agent.  This prevents a single agent from being flooded when
 * many users queue up while no agents were online.
 * </p>
 */
@Component
public class PendingAssignmentScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingAssignmentScheduler.class);

    private final RedisAssignmentService assignmentService;

    public PendingAssignmentScheduler(RedisAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /**
     * Every 5 minutes, try to assign pending users.
     * Each online agent picks up to 3 unassigned users per tick.
     */
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void assignPending() {
        List<Long> onlineAgentIds = assignmentService.findOnlineEnabledAgentIds();
        if (onlineAgentIds.isEmpty()) {
            log.debug("No online agents, skipping pending assignment");
            return;
        }

        log.debug("Pending assignment tick: {} online agent(s)", onlineAgentIds.size());
        for (Long agentId : onlineAgentIds) {
            try {
                int claimed = assignmentService.assignPendingUsers(agentId, 3);
                if (claimed > 0) {
                    log.info("Scheduled assignment: agent {} claimed {} pending user(s)", agentId, claimed);
                }
            } catch (Exception e) {
                log.error("Scheduled assignment failed for agent {}", agentId, e);
            }
        }
    }
}
