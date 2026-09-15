package com.example.TextileManagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TakaEntryValidatorTest {
    @Test
    void rejectsMoreThanTwoDecimalPlaces() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> TakaEntryValidator.validateEntry(123, new BigDecimal("450.505")));

        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void rejectsDuplicateNumbers() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> TakaEntryValidator.validateUniqueNumbers(List.of(123, 123)));

        assertEquals(400, error.getStatusCode().value());
    }
}
