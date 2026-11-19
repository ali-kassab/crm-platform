package com.crmplatform.crm;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Public API of contacts and locations for other modules (work, finance, automation). */
public interface Contacts {

    Optional<ContactSummary> find(UUID contactId);

    Map<UUID, ContactSummary> findAll(Collection<UUID> contactIds);

    Optional<LocationSummary> location(UUID locationId);

    Map<UUID, LocationSummary> locations(Collection<UUID> locationIds);

    // ---- mutations used by automation commands (fire tag/status events like user edits)

    void addTags(UUID contactId, Set<UUID> tagIds);

    void removeTags(UUID contactId, Set<UUID> tagIds);

    void setStatus(UUID contactId, UUID statusId);

    /** Clears the status only if it currently equals {@code statusId} (or unconditionally when null). */
    void clearStatus(UUID contactId, UUID statusId);

    /** @return false if the contact already has a representative */
    boolean assignRepresentative(UUID contactId, UUID userId);

    void changeType(UUID contactId, String type, Boolean active, UUID statusId);

    record ContactSummary(UUID id, String type, String fullName, String company, String email, String phone,
            UUID representativeUserId, UUID statusId, Map<String, Object> metadata, String city, String mobile) {
    }

    /** Includes what a technician needs to reach the site (address line, access notes, coordinates). */
    record LocationSummary(UUID id, UUID contactId, String name, String type, String city, String addressLine1,
            String accessInstructions, java.math.BigDecimal latitude, java.math.BigDecimal longitude) {
    }
}
