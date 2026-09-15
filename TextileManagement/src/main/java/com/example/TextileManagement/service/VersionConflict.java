package com.example.TextileManagement.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class VersionConflict {
    private VersionConflict() {
    }

    public static void requireCurrent(Long requested, Long current) {
        if (requested == null || !requested.equals(current)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This record was changed by another user");
        }
    }
}
