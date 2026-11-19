package com.crmplatform.work.flowcharts;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FlowchartRepository extends JpaRepository<Flowchart, UUID> {

    @Query("select f from Flowchart f where (:projectId is null or f.projectId = :projectId) "
            + "and (:favorite is null or f.favorite = :favorite) and (:ownerId is null or f.ownerUserId = :ownerId) "
            + "and (:q is null or lower(f.title) like :q escape '\\')")
    Page<Flowchart> search(UUID projectId, Boolean favorite, UUID ownerId, String q, Pageable pageable);

    List<Flowchart> findByProjectId(UUID projectId);
}
