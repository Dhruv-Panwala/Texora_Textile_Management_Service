package com.example.TextileManagement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.TextileManagement.entities.Workspace;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    Optional<Workspace> findBySlug(String slug);

    @Query("""
            select w from Workspace w
            where exists (
                select 1 from WorkspaceMember member
                where member.workspace.id = w.id
                  and lower(member.user.username) = lower(:username)
            )
            order by w.name asc
            """)
    List<Workspace> findAllAccessibleByUsername(@Param("username") String username);
}
