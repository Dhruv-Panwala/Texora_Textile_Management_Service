package com.example.TextileManagement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.TextileManagement.entities.CompanyProfile;

public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, Long> {
	@Query(value = """
	        select u.username as username, u.display_name as display_name,
	               u.id as user_id, u.auth_version as auth_version, u.status as user_status,
	               c.id as company_id, member.role as role
	        from users u
	        left join workspace_members member on member.user_id = u.id
	        left join company_profiles c on c.workspace_id = member.workspace_id
	        where lower(u.username) = lower(:username)
	        order by c.trade_name, c.id
	        limit 1
	        """, nativeQuery = true)
	Optional<RequestAuthorizationProjection> findFirstRequestAuthorization(@Param("username") String username);

	@Query(value = """
	        select u.username as username, u.display_name as display_name,
	               u.id as user_id, u.auth_version as auth_version, u.status as user_status,
	               c.id as company_id, member.role as role
	        from users u
	        join workspace_members member on member.user_id = u.id
	        join company_profiles c on c.workspace_id = member.workspace_id
	        where lower(u.username) = lower(:username) and c.id = :companyId
	        limit 1
	        """, nativeQuery = true)
	Optional<RequestAuthorizationProjection> findRequestAuthorization(@Param("username") String username,
	        @Param("companyId") Long companyId);

    @Query("""
            select u.username as username, u.displayName as displayName,
                   u.id as userId, u.authVersion as authVersion, u.status as userStatus,
                   c.id as id, c.id as companyId, c.tradeName as tradeName, c.gstNo as gstNo,
                   c.phone as phone, c.address as address, c.defaultBroker as defaultBroker,
                   c.defaultQuality as defaultQuality, c.logoContentType as logoContentType,
                   c.logoWidth as logoWidth, c.logoHeight as logoHeight, c.version as version,
                   c.createdAt as createdAt, c.updatedAt as updatedAt
            from UserAccount u
            left join WorkspaceMember member on member.user.id = u.id
            left join CompanyProfile c on c.workspace.id = member.workspace.id
            where lower(u.username) = lower(:username)
            order by c.tradeName asc, c.id asc
            """)
    List<BootstrapAuthorizationProjection> findBootstrapAuthorization(@Param("username") String username);

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
            select c.id as id, c.tradeName as tradeName, c.gstNo as gstNo, c.phone as phone,
                   c.address as address, c.defaultBroker as defaultBroker, c.defaultQuality as defaultQuality,
                   c.logoContentType as logoContentType, c.logoWidth as logoWidth, c.logoHeight as logoHeight,
                   c.version as version, c.createdAt as createdAt, c.updatedAt as updatedAt
            from CompanyProfile c
            where exists (
                select 1 from WorkspaceMember member
                where member.workspace.id = c.workspace.id
                  and lower(member.user.username) = lower(:username)
            )
            order by c.tradeName asc
            """)
    List<CompanyProfileSummary> findAccessibleSummariesByUsername(@Param("username") String username);

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
