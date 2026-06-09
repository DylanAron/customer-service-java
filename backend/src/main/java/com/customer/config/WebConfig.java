package com.customer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${storage.local.base-path:}")
    private String storageBasePath;

    @Value("${storage.url-prefix:/uploads}")
    private String urlPrefix;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Resolve storage root: default to project-root/filesUpload/
        String basePath = storageBasePath;
        if (basePath == null || basePath.isBlank()) {
            basePath = Paths.get(System.getProperty("user.dir")).getParent().resolve("filesUpload").toString();
        }
        String absolutePath = "file:" + Paths.get(basePath).normalize().toAbsolutePath().toString().replace("\\", "/") + "/";

        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations(absolutePath)
                .setCachePeriod(0);
    }
}
