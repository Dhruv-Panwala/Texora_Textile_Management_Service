package com.example.TextileManagement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.TextileManagement.config.InputLimitExceededException;

public final class TakaEntryValidator {
    public static final int MAX_TAKA_ENTRIES = 200;
    public static final int MAX_DECIMAL_PLACES = 2;
    public static final int MAX_TAKA_NUMBER = 999_999_999;
    public static final BigDecimal MAX_METERS_PER_ENTRY = new BigDecimal("10000000.00");

    private TakaEntryValidator() {
    }

    public static void validateCount(int count, int configuredMaximum) {
        if (count > Math.min(MAX_TAKA_ENTRIES, Math.max(1, configuredMaximum))) {
            throw new InputLimitExceededException("Too many taka entries");
        }
    }

    public static void validateEntry(Integer takaNo, BigDecimal meters) {
        if (takaNo == null || takaNo <= 0 || takaNo > MAX_TAKA_NUMBER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Taka number must be a positive whole number");
        }
        if (meters == null || meters.compareTo(BigDecimal.ZERO) <= 0 || meters.compareTo(MAX_METERS_PER_ENTRY) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Taka meters must be greater than zero");
        }
        if (meters.stripTrailingZeros().scale() > MAX_DECIMAL_PLACES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Taka meters can have at most two decimal places");
        }
    }

    public static BigDecimal normalizeMeters(BigDecimal meters) {
        return meters.setScale(MAX_DECIMAL_PLACES, RoundingMode.UNNECESSARY);
    }

    public static void validateUniqueNumbers(Collection<Integer> takaNumbers) {
        Set<Integer> seen = new HashSet<>();
        for (Integer takaNo : takaNumbers) {
            if (!seen.add(takaNo)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Taka number " + takaNo + " is duplicated");
            }
        }
    }
}
