package com.customer.config;

import com.customer.entity.Agent;
import com.customer.repository.AgentRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {
    private final AgentRepository agentRepository;
    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(AgentRepository agentRepository, JdbcTemplate jdbcTemplate) {
        this.agentRepository = agentRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        // Apply column comments to existing tables (Hibernate ddl-auto=update does not add comments)
        addColumnComments();

        if (agentRepository.findByUsername("admin").isEmpty()) {
            Agent admin = new Agent();
            admin.setUsername("admin");
            admin.setPassword("admin123");
            admin.setNickname("管理员");
            admin.setEnabled(true);
            agentRepository.save(admin);
            System.out.println("Admin user created: admin/admin123");
        }

        if (agentRepository.findByUsername("kf1").isEmpty()) {
            Agent kf1 = new Agent();
            kf1.setUsername("kf1");
            kf1.setPassword("123456");
            kf1.setNickname("客服1");
            kf1.setEnabled(true);
            agentRepository.save(kf1);
            System.out.println("Agent created: kf1/123456");
        }

        if (agentRepository.findByUsername("kf2").isEmpty()) {
            Agent kf2 = new Agent();
            kf2.setUsername("kf2");
            kf2.setPassword("123456");
            kf2.setNickname("客服2");
            kf2.setEnabled(true);
            agentRepository.save(kf2);
            System.out.println("Agent created: kf2/123456");
        }

        if (agentRepository.findByUsername("kf3").isEmpty()) {
            Agent kf3 = new Agent();
            kf3.setUsername("kf3");
            kf3.setPassword("123456");
            kf3.setNickname("客服3");
            kf3.setEnabled(true);
            agentRepository.save(kf3);
            System.out.println("Agent created: kf3/123456");
        }
    }

    private void addColumnComments() {
        String[][] tableComments = {
            {"cs_agent", "客服账号表"},
            {"cs_message", "消息记录表"},
            {"cs_user", "访客用户表"},
        };
        for (String[] tc : tableComments) {
            jdbcTemplate.execute("ALTER TABLE " + tc[0] + " COMMENT = '" + tc[1] + "'");
        }

        String[][] columnComments = {
            {"cs_agent", "id", "主键ID"},
            {"cs_agent", "username", "登录用户名"},
            {"cs_agent", "password", "登录密码"},
            {"cs_agent", "nickname", "客服昵称"},
            {"cs_agent", "enabled", "是否启用"},
            {"cs_agent", "online", "是否在线"},
            {"cs_agent", "last_login_time", "最后登录时间"},
            {"cs_agent", "created_at", "创建时间"},
            {"cs_agent", "updated_at", "更新时间"},

            {"cs_message", "id", "主键ID"},
            {"cs_message", "user_id", "用户ID"},
            {"cs_message", "agent_id", "回复此消息的客服ID"},
            {"cs_message", "content", "消息内容"},
            {"cs_message", "msg_type", "消息类型：text/image/file"},
            {"cs_message", "file_url", "文件URL（图片/文件时使用）"},
            {"cs_message", "is_read", "是否已读"},
            {"cs_message", "channel_code", "渠道编码：h5/pc/app/..."},
            {"cs_message", "direction", "消息方向：user=用户发送, agent=客服发送"},
            {"cs_message", "created_at", "创建时间"},

            {"cs_user", "id", "主键ID"},
            {"cs_user", "user_id", "用户唯一标识（自动生成）"},
            {"cs_user", "username", "用户名（注册用户）"},
            {"cs_user", "password", "登录密码"},
            {"cs_user", "nickname", "用户昵称"},
            {"cs_user", "avatar", "头像URL"},
            {"cs_user", "last_active_time", "最后活跃时间"},
            {"cs_user", "created_at", "创建时间"},
        };
        for (String[] cc : columnComments) {
            try {
                jdbcTemplate.execute("ALTER TABLE " + cc[0] + " MODIFY COLUMN " + cc[1] + " " + getColumnType(cc[0], cc[1]) + " COMMENT '" + cc[2] + "'");
            } catch (Exception e) {
                System.out.println("Skipped comment for " + cc[0] + "." + cc[1] + ": " + e.getMessage());
            }
        }
    }

    private String getColumnType(String table, String column) {
        // We check if the column exists and preserve its type
        try {
            var result = jdbcTemplate.queryForRowSet(
                "SELECT COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = (SELECT DATABASE()) AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                table, column);
            if (result.next()) {
                String colType = result.getString("COLUMN_TYPE");
                String nullable = "YES".equals(result.getString("IS_NULLABLE")) ? "NULL" : "NOT NULL";
                String extra = result.getString("EXTRA");
                String defaultVal = result.getString("COLUMN_DEFAULT");
                StringBuilder sb = new StringBuilder(colType);
                sb.append(" ").append(nullable);
                if (defaultVal != null) {
                    if ("CURRENT_TIMESTAMP".equals(defaultVal) || "current_timestamp()".equalsIgnoreCase(defaultVal)) {
                        sb.append(" DEFAULT CURRENT_TIMESTAMP");
                    } else {
                        sb.append(" DEFAULT '").append(defaultVal).append("'");
                    }
                }
                if (extra != null && !extra.isEmpty() && !"DEFAULT_GENERATED".equals(extra)) {
                    sb.append(" ").append(extra);
                }
                return sb.toString();
            }
        } catch (Exception ignored) {}
        // Fallback: use common types
        if (column.equals("id")) return "BIGINT NOT NULL auto_increment";
        if (column.contains("is_")) return "TINYINT(1) DEFAULT 0";
        if (column.equals("content")) return "TEXT NOT NULL";
        if (column.equals("file_url") || column.equals("avatar")) return "VARCHAR(500)";
        if (column.contains("name") || column.contains("user_id") || column.contains("username") || column.contains("nickname") || column.contains("password") || column.contains("msg_type")) return "VARCHAR(255)";
        if (column.contains("time") || column.contains("_at")) return "DATETIME";
        if (column.contains("agent_id")) return "BIGINT";
        return "VARCHAR(255)";
    }
}
