package com.crmplatform.identity.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    @Query(value = "select count(*) > 0 from workspaces where slug = :slug", nativeQuery = true)
    boolean slugTaken(String slug);
}
