package com.example.TextileManagement.controller;

import java.util.List;

import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> from(Page<T> result) {
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
