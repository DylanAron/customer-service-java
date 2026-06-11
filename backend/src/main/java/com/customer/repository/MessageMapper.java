package com.customer.repository;

import com.customer.entity.Message;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    List<Map<String, Object>> findRecentUserIdsWithPagination(@Param("since") LocalDateTime since, @Param("limit") int limit);

    List<Map<String, Object>> findRecentUserIdsByAgent(@Param("agentId") Long agentId, @Param("since") LocalDateTime since, @Param("limit") int limit);

    List<Map<String, Object>> countUnreadForAllUsers(@Param("since") LocalDateTime since);

    @Update("UPDATE cs_message SET is_read = true WHERE user_id = #{userId} AND direction = 'user' AND (is_read IS NULL OR is_read = false)")
    int markAsRead(@Param("userId") String userId);

    @Update("UPDATE cs_message SET is_read = true WHERE user_id = #{userId} AND agent_id = #{agentId} AND direction = 'user' AND (is_read IS NULL OR is_read = false)")
    int markAsReadByAgent(@Param("userId") String userId, @Param("agentId") Long agentId);

    /**
     * Find user_ids who have messages but no assigned agent.
     * Ordered by earliest message first (FIFO).
     */
    List<String> findUnassignedUserIds(@Param("limit") int limit);
}
