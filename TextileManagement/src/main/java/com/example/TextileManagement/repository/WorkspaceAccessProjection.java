package com.example.TextileManagement.repository;

/** Workspace access row containing the role already resolved by the database query. */
public interface WorkspaceAccessProjection {
    Long getId();

    String getName();

    String getRole();
}
