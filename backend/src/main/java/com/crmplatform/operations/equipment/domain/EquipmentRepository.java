package com.crmplatform.operations.equipment.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface EquipmentRepository extends JpaRepository<Equipment, UUID>, JpaSpecificationExecutor<Equipment> {

    @Query("select count(e) > 0 from Equipment e where lower(e.serialNumber) = lower(:serial) and e.id <> :exceptId")
    boolean serialTaken(String serial, UUID exceptId);

    List<Equipment> findByIdIn(Collection<UUID> ids);

    boolean existsByContactId(UUID contactId);

    boolean existsByLocationId(UUID locationId);
}
