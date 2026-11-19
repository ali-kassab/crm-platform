package com.crmplatform.operations.jobs.application;

import com.crmplatform.crm.CrmReferences.ProjectUsage;
import com.crmplatform.crm.LabelDeleted;
import com.crmplatform.operations.visits.application.VisitService;
import com.crmplatform.milestones.MilestoneOwner;
import com.crmplatform.operations.jobs.domain.JobRepository;
import com.crmplatform.operations.warranties.application.WarrantyService;
import com.crmplatform.shared.events.DomainEvents.JobDeleted;
import com.crmplatform.shared.events.DomainEvents.ProjectDeleted;
import com.crmplatform.shared.tenant.TenantWork;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Hooks the operations module provides to lower modules, and its reactions to their events. */
@Configuration(proxyBeanMethods = false)
class OperationsIntegration {

    @Bean
    MilestoneOwner jobMilestoneOwner(JobRepository jobs) {
        return new MilestoneOwner() {
            @Override
            public OwnerType type() {
                return OwnerType.JOB;
            }

            @Override
            public boolean exists(UUID id) {
                return jobs.existsById(id);
            }
        };
    }

    /** Customers and sites with active jobs (including standalone ones) cannot be deleted. */
    @Bean
    ProjectUsage jobUsage(JobRepository jobs) {
        return new ProjectUsage() {
            @Override
            public boolean contactHasProjects(UUID contactId) {
                return jobs.existsByContactId(contactId);
            }

            @Override
            public boolean locationHasProjects(UUID locationId) {
                return jobs.existsByLocationId(locationId);
            }
        };
    }

    /** Hourly: SCHEDULED visits whose window passed without a start become MISSED (idempotent). */
    @Component
    static class MissedVisits {

        private static final Logger log = LoggerFactory.getLogger(MissedVisits.class);

        private final VisitService visits;
        private final TenantWork tenantWork;

        MissedVisits(VisitService visits, TenantWork tenantWork) {
            this.visits = visits;
            this.tenantWork = tenantWork;
        }

        @Scheduled(cron = "${app.operations.missed-visits-cron:0 10 * * * *}")
        @SchedulerLock(name = "missed-visits", lockAtMostFor = "PT10M")
        void run() {
            int missed = tenantWork.callAsSystem(visits::markMissed);
            if (missed > 0) {
                log.info("Marked {} visits as missed", missed);
            }
        }
    }

    @Component
    static class Handlers {

        private final JobService jobs;
        private final WarrantyService warranties;
        private final TenantWork tenantWork;

        Handlers(JobService jobs, WarrantyService warranties, TenantWork tenantWork) {
            this.jobs = jobs;
            this.warranties = warranties;
            this.tenantWork = tenantWork;
        }

        /** BR-056: a project's jobs and warranties go with it, in the same transaction. */
        @EventListener
        void on(ProjectDeleted event) {
            jobs.deleteAllOfProject(event.projectId());
            warranties.deleteAllOfProject(event.projectId());
        }

        @EventListener
        void on(JobDeleted event) {
            warranties.unlinkJob(event.jobId());
        }

        @TransactionalEventListener
        void on(LabelDeleted event) {
            if (event.tag()) {
                tenantWork.run(event.workspaceId(), null, () -> {
                    jobs.detachTag(event.labelId());
                    warranties.detachTag(event.labelId());
                });
            }
        }
    }

    /** Daily warranty expiry across all workspaces (FR-OPS-07). Single-instance via ShedLock. */
    @Component
    static class WarrantyExpiry {

        private static final Logger log = LoggerFactory.getLogger(WarrantyExpiry.class);

        private final WarrantyService warranties;
        private final TenantWork tenantWork;

        WarrantyExpiry(WarrantyService warranties, TenantWork tenantWork) {
            this.warranties = warranties;
            this.tenantWork = tenantWork;
        }

        @Scheduled(cron = "${app.operations.warranty-expiry-cron:0 5 0 * * *}")
        @SchedulerLock(name = "warranty-expiry", lockAtMostFor = "PT10M")
        void run() {
            int expired = tenantWork.callAsSystem(warranties::expireEnded);
            if (expired > 0) {
                log.info("Marked {} warranties as expired", expired);
            }
        }
    }
}
