package com.crmplatform.operations.jobs.application;

import java.util.Optional;
import java.util.UUID;

/** Response-time commitment that applies at a site (from active maintenance agreements). */
public interface SlaPolicy {

    Optional<Integer> responseHoursAt(UUID locationId);
}
