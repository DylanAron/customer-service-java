package com.customer.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Set;

/**
 * JWT auth for management endpoints via FilterRegistrationBean.
 * <p>
 * Note: This filter is registered wide but only blocks specific admin-only paths.
 * The core auth logic is also duplicated in AuthHelper + MessageController for reliability.
 * </p>
 */
@Configuration
public class JwtAuthConfig {

    private static final Set<String> PROTECTED = Set.of(
            "/api/message/all-messages",
            "/api/message/reset",
            "/api/message/users",
            "/api/message/mark-read"
    );

    private final JwtUtil jwtUtil;

    public JwtAuthConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Bean
    public FilterRegistrationBean<Filter> jwtFilter() {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                    throws IOException, ServletException {
                HttpServletRequest request = (HttpServletRequest) req;
                HttpServletResponse response = (HttpServletResponse) res;
                String path = request.getRequestURI();

                if (!PROTECTED.contains(path)) {
                    chain.doFilter(req, res);
                    return;
                }

                String auth = request.getHeader("Authorization");
                if (auth == null || !auth.startsWith("Bearer ")) {
                    write401(response, "未授权");
                    return;
                }

                if (!jwtUtil.validateToken(auth.substring(7))) {
                    write401(response, "令牌无效");
                    return;
                }

                chain.doFilter(req, res);
            }

            private void write401(HttpServletResponse resp, String msg) throws IOException {
                resp.setStatus(401);
                resp.setContentType("application/json;charset=UTF-8");
                resp.getWriter().write("{\"error\":\"" + msg + "\"}");
            }

            @Override public void init(FilterConfig c) {}
            @Override public void destroy() {}
        });
        bean.addUrlPatterns("/api/message/*");
        bean.setName("jwtAuth");
        bean.setOrder(1);
        return bean;
    }
}
