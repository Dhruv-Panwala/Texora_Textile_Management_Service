package com.example.TextileManagement.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class HealthControllerTest {
    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final HealthController controller = new HealthController(jdbcTemplate);

    @Test
    void livenessDoesNotTouchTheDatabase() {
        var response = controller.liveness();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new HealthController.HealthResponse("UP", "UNKNOWN"), response.getBody());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void readinessChecksTheDatabase() {
        when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(1);

        var response = controller.readiness();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new HealthController.HealthResponse("UP", "UP"), response.getBody());
        verify(jdbcTemplate).queryForObject("SELECT 1", Integer.class);
    }

    @Test
    void readinessReturnsUnavailableWhenTheDatabaseFails() {
        when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        var response = controller.readiness();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(new HealthController.HealthResponse("DOWN", "DOWN"), response.getBody());
    }
}
