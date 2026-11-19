package com.crmplatform.operations.visits.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitReportRepository extends JpaRepository<VisitReport, UUID> {

    Optional<VisitReport> findByVisitId(UUID visitId);
}
