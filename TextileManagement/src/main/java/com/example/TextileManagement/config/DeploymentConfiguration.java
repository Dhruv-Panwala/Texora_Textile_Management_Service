package com.example.TextileManagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
public class DeploymentConfiguration {
    @Bean
    @Profile("default")
    Object missingActiveProfile() {
        throw new IllegalStateException("SPRING_PROFILES_ACTIVE must be set to local, dev, or prod");
    }

    @Bean
    @Profile("prod")
    ProductionSettings productionSettings(Environment environment) {
        String datasourceUrl = environment.getProperty("spring.datasource.url");
        String datasourceUsername = environment.getProperty("spring.datasource.username");
        String datasourcePassword = environment.getProperty("spring.datasource.password");
        String allowedOrigin = environment.getProperty("app.cors.allowed-origin");
        String publicUrl = environment.getProperty("app.public-url");
        String trustedProxy = environment.getProperty("app.trusted-proxy");
        require("SPRING_DATASOURCE_URL", datasourceUrl);
        require("SPRING_DATASOURCE_USERNAME", datasourceUsername);
        require("SPRING_DATASOURCE_PASSWORD", datasourcePassword);
        require("APP_CORS_ALLOWED_ORIGIN", allowedOrigin);
        require("APP_PUBLIC_URL", publicUrl);
        if (!"cloudflare".equalsIgnoreCase(trustedProxy)) {
            throw new IllegalStateException("APP_TRUSTED_PROXY must be cloudflare for the prod profile");
        }
        return new ProductionSettings(datasourceUrl, datasourceUsername, allowedOrigin, publicUrl, trustedProxy);
    }

    private void require(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set for the prod profile");
        }
    }

    public record ProductionSettings(String datasourceUrl, String datasourceUsername,
            String allowedOrigin, String publicUrl, String trustedProxy) {
    }
}
