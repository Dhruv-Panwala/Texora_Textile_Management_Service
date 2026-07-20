package com.example.TextileManagement.security;

import java.io.Serializable;

public record AuthSessionDetails(Long userId, Long authVersion) implements Serializable {
}
