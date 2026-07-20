package com.example.TextileManagement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.TextileManagement.entities.CompanyProfile;

public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, Long> {
	@Query("select c.workspace.id from CompanyProfile c where c.id = :companyId")
	Optional<Long> findWorkspaceIdById(@Param("companyId") Long companyId);

	Optional<CompanyProfile> findByTradeNameIgnoreCase(String tradeName);

    Optional<CompanyProfile> findByTradeNameIgnoreCaseAndIdNot(String tradeName, Long id);

    Optional<CompanyProfile> findByWorkspace_IdAndTradeNameIgnoreCase(Long workspaceId, String tradeName);

    Optional<CompanyProfile> findByWorkspace_IdAndTradeNameIgnoreCaseAndIdNot(Long workspaceId, String tradeName, Long id);

	Optional<CompanyProfile> findFirstByOrderByIdAsc();

    List<CompanyProfile> findAllByOrderByTradeNameAsc();

    List<CompanyProfile> findAllByWorkspace_IdOrderByTradeNameAsc(Long workspaceId);

    @Query("""
            select c from CompanyProfile c
            where exists (
                select 1 from WorkspaceMember member
                where member.workspace.id = c.workspace.id
                  and lower(member.user.username) = lower(:username)
            )
            order by c.tradeName asc
            """)
    List<CompanyProfile> findAllAccessibleByUsername(@Param("username") String username);

    @Query("""
            select count(c) from CompanyProfile c
            where c.id = :companyId
              and exists (
                  select 1 from WorkspaceMember member
                  where member.workspace.id = c.workspace.id
                    and lower(member.user.username) = lower(:username)
              )
            """)
    long countAccessibleByUsernameAndId(@Param("username") String username, @Param("companyId") Long companyId);

}
