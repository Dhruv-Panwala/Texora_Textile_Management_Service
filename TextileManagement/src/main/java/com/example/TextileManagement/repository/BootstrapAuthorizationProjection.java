package com.example.TextileManagement.repository;

/** One scalar row carries session validation and one accessible company summary. */
public interface BootstrapAuthorizationProjection extends RequestAuthorizationProjection, CompanyProfileSummary {
}
