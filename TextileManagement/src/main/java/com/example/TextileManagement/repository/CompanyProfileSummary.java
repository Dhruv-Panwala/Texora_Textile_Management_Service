package com.example.TextileManagement.repository;

import java.time.LocalDateTime;

/** Scalar company data used by authenticated bootstrap; logo bytes stay out of this query. */
public interface CompanyProfileSummary {
    Long getId();

    String getTradeName();

    String getGstNo();

    String getPhone();

    String getAddress();

    String getDefaultBroker();

    String getDefaultQuality();

    String getLogoContentType();

    Integer getLogoWidth();

    Integer getLogoHeight();

    Long getVersion();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();
}
