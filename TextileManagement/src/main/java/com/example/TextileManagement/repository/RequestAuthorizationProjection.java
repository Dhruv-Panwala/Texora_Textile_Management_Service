package com.example.TextileManagement.repository;

public interface RequestAuthorizationProjection {
    String getUsername();

    String getDisplayName();

    Long getUserId();

    Long getAuthVersion();

    String getUserStatus();

    Long getCompanyId();

    String getRole();
}
