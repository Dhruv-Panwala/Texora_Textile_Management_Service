package com.example.TextileManagement.config;

public class InputLimitExceededException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InputLimitExceededException(String message) {
        super(message);
    }
}
