package com.example.TextileManagement.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.repository.CompanyProfileRepository;

@Service
public class CompanyAccessService {
    private final CompanyProfileRepository companyProfileRepository;

    public CompanyAccessService(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    public List<CompanyProfile> findAccessibleCompanies(String username) {
        return companyProfileRepository.findAllAccessibleByUsername(username);
    }

    public boolean canAccess(String username, Long companyId) {
        return username != null
                && companyId != null
                && companyProfileRepository.countAccessibleByUsernameAndId(username, companyId) == 1;
    }

}
