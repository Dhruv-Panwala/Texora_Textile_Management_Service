package com.example.TextileManagement.config;

import org.springframework.stereotype.Component;

@Component
public class CurrentCompanyContext {
    private static final ThreadLocal<Long> CURRENT_COMPANY = new ThreadLocal<>();

    public Long getCompanyId() {
        return CURRENT_COMPANY.get();
    }

    public void setCompanyId(Long companyId) {
        CURRENT_COMPANY.set(companyId);
    }

    public void clear() {
        CURRENT_COMPANY.remove();
    }
}