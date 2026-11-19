package com.crmplatform.crm.contacts.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ContactRepository extends JpaRepository<Contact, UUID>, JpaSpecificationExecutor<Contact> {

    @Query("select count(c) > 0 from Contact c where c.email = :email and (:excludeId is null or c.id <> :excludeId)")
    boolean emailTaken(String email, UUID excludeId);

    List<Contact> findByRepresentativeUserId(UUID userId);

    @Query("select c from Contact c join c.tagIds t where t = :tagId")
    List<Contact> findByTag(UUID tagId);

    List<Contact> findByStatusId(UUID statusId);
}
