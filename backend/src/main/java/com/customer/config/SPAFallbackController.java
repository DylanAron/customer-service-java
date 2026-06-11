package com.customer.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 路由回退控制器。
 *
 * <p>单 JAR 部署时，前端 React 路由（如 /admin, /agent）在 Spring Boot
 * 中没有对应的后端控制器，直接访问会返回 404。
 * 此控制器将这些路径转发到 classpath:/static/index.html，
 * 由前端 React Router 接管路由。</p>
 */
@Controller
public class SPAFallbackController {

    /**
     * 匹配所有前端 SPA 路由，返回 index.html。
     * 排除 /api/*（后端接口）和 /uploads/*（文件访问）。
     */
    @GetMapping({
        "/",
        "/admin/**",
        "/agent/**",
        "/user/**"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
