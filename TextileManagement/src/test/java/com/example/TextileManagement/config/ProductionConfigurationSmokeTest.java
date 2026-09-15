package com.example.TextileManagement.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.example.TextileManagement.config.DataInitializer;

class ProductionConfigurationSmokeTest {
    @Test
    void startupFailsWhenNoDeploymentProfileIsSelected() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(DeploymentConfiguration.class);
        try {
            BeanCreationException failure = assertThrows(BeanCreationException.class, context::refresh);
            assertTrue(rootMessage(failure).contains("SPRING_PROFILES_ACTIVE must be set"));
        } finally {
            context.close();
        }
    }

    @Test
    void productionStartupFailsWhenRequiredConfigurationIsMissing() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("prod");
        context.register(DeploymentConfiguration.class);
        try {
            assertThrows(BeanCreationException.class, context::refresh);
        } finally {
            context.close();
        }
    }

    @Test
    void productionProfileCannotCreateTheDevelopmentSeeder() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("prod");
        context.register(DataInitializer.class);
        try {
            context.refresh();
            assertTrue(context.getBeansOfType(DataInitializer.class).isEmpty());
        } finally {
            context.close();
        }
    }

    private String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "" : current.getMessage();
    }
}
