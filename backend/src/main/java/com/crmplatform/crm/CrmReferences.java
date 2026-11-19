package com.crmplatform.crm;

import java.util.UUID;

/**
 * Hooks implemented by higher modules so crm can enforce cross-module rules without depending on
 * them (BR-028 delete guards, BR-030 note parents).
 */
public final class CrmReferences {

    private CrmReferences() {
    }

    /** Reports whether a contact or location is still referenced by active projects. */
    public interface ProjectUsage {

        boolean contactHasProjects(UUID contactId);

        boolean locationHasProjects(UUID locationId);
    }

    /** Verifies that a project or job exists in the active workspace (note parents). */
    public interface NoteParents {

        boolean projectExists(UUID projectId);

        boolean jobExists(UUID jobId);
    }
}
