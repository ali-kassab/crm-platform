package com.crmplatform.crm.locations.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    List<Location> findByContactId(UUID contactId);

    @Query("select l from Location l where (:contactId is null or l.contactId = :contactId) "
            + "and (:type is null or l.type = :type) "
            + "and (:city is null or lower(l.address.city) = :city) "
            + "and (:q is null or app_search_norm(l.name) like :q escape '\\')")
    Page<Location> search(UUID contactId, Location.Type type, String city, String q, Pageable pageable);
}
