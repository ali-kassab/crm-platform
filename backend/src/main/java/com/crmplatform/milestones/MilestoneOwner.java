package com.crmplatform.milestones;

import java.util.UUID;

/**
 * Implemented by each module that owns milestone-bearing records, so this module can verify the
 * owner exists in the active workspace without depending on those modules.
 */
public interface MilestoneOwner {

    OwnerType type();

    boolean exists(UUID ownerId);

    enum OwnerType {
        CONTACT("contacts", "crm:read", "crm:write"),
        LOCATION("locations", "crm:read", "crm:write"),
        PROJECT("projects", "work:read", "work:write"),
        JOB("jobs", "ops:read", "ops:write"),
        TASK("tasks", "work:read", "work:write");

        private final String path;
        private final String readPermission;
        private final String writePermission;

        OwnerType(String path, String readPermission, String writePermission) {
            this.path = path;
            this.readPermission = readPermission;
            this.writePermission = writePermission;
        }

        public String path() {
            return path;
        }

        public String readPermission() {
            return readPermission;
        }

        public String writePermission() {
            return writePermission;
        }

        public static OwnerType fromPath(String path) {
            for (OwnerType t : values()) {
                if (t.path.equals(path)) {
                    return t;
                }
            }
            return null;
        }
    }
}
