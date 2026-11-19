package com.crmplatform.crm;

import java.util.UUID;

/** A tag or status was deleted; owners detach it from their records (BR-015, EC-11). */
public record LabelDeleted(UUID workspaceId, UUID labelId, boolean tag) {
}
