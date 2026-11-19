package com.crmplatform.crm;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public read API over tags and statuses for other modules (validation, display, automation
 * trigger bindings).
 */
public interface LabelCatalog {

    /** Throws 400 REFERENCE_NOT_FOUND naming {@code field} if any id is not a tag of this workspace. */
    void requireTags(Collection<UUID> tagIds, String field);

    /** Throws 400 REFERENCE_NOT_FOUND if the status does not exist in this workspace. */
    void requireStatus(UUID statusId, String field);

    Map<UUID, LabelSummary> tags(Collection<UUID> tagIds);

    Optional<LabelSummary> status(UUID statusId);

    /** Start/stop procedure bound to a tag, if any. */
    Optional<TriggerBinding> tagBinding(UUID tagId);

    /** Start/stop procedure bound to a status, if any. */
    Optional<TriggerBinding> statusBinding(UUID statusId);

    /** Clears every reference to the procedure (force delete of a procedure). */
    void unbindProcedure(UUID procedureId);

    /** Whether any tag or status uses the procedure as start or stop procedure. */
    boolean isProcedureBound(UUID procedureId);

    record LabelSummary(UUID id, String name, String backgroundColor, String textColor, String icon) {
    }

    record TriggerBinding(UUID startProcedureId, UUID stopProcedureId) {
    }
}
