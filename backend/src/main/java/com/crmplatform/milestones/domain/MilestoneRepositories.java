package com.crmplatform.milestones.domain;

import com.crmplatform.milestones.MilestoneOwner.OwnerType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public final class MilestoneRepositories {

    private MilestoneRepositories() {
    }

    public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {
        Optional<Milestone> findByOwnerTypeAndOwnerId(OwnerType ownerType, UUID ownerId);
    }

    public interface MilestoneTemplateRepository extends JpaRepository<MilestoneTemplate, UUID> {
        @Query("select t from MilestoneTemplate t where (:q is null or lower(t.name) like :q escape '\\')")
        Page<MilestoneTemplate> search(String q, Pageable pageable);
    }
}
