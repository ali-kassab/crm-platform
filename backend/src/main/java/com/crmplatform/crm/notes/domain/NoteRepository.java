package com.crmplatform.crm.notes.domain;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    @Query("select n from Note n where (:contactId is null or n.contactId = :contactId) "
            + "and (:projectId is null or n.projectId = :projectId) and (:jobId is null or n.jobId = :jobId)")
    Page<Note> findByParent(UUID contactId, UUID projectId, UUID jobId, Pageable pageable);
}
