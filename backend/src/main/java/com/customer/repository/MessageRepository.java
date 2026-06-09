package com.customer.repository;

import com.customer.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    Page<Message> findByUserIdOrderByCreatedAtAsc(String userId, Pageable pageable);

    List<Message> findByUserIdOrderByCreatedAtAsc(String userId);

    @Query("SELECT m.userId, MAX(m.createdAt) as lastMsgTime FROM Message m WHERE m.createdAt >= :since GROUP BY m.userId ORDER BY lastMsgTime DESC")
    List<Object[]> findRecentUserIds(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT m.userId, MAX(m.createdAt) as lastMsgTime FROM Message m WHERE m.createdAt >= :since GROUP BY m.userId ORDER BY lastMsgTime DESC")
    List<Object[]> findRecentUserIdsWithPagination(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.userId = :userId AND m.agentId IS NULL AND m.direction = 'user' AND m.createdAt >= :since")
    long countUnreadByUserId(@Param("userId") String userId, @Param("since") LocalDateTime since);

    @Query("SELECT m.userId, COUNT(m) FROM Message m WHERE m.direction = 'user' AND (m.isRead IS NULL OR m.isRead = false) AND m.createdAt >= :since GROUP BY m.userId")
    List<Object[]> countUnreadForAllUsers(@Param("since") LocalDateTime since);

    Message findTopByUserIdOrderByCreatedAtDesc(String userId);

    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.isRead = true WHERE m.userId = :userId AND m.direction = 'user' AND (m.isRead IS NULL OR m.isRead = false)")
    int markAsRead(@Param("userId") String userId);
}
