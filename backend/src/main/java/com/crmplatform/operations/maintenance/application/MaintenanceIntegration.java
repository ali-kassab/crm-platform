package com.crmplatform.operations.maintenance.application;

import com.crmplatform.crm.CrmReferences.ProjectUsage;
import com.crmplatform.identity.WorkspaceMembers;
import com.crmplatform.operations.equipment.domain.EquipmentRepository;
import com.crmplatform.operations.jobs.application.SlaPolicy;
import com.crmplatform.operations.maintenance.domain.MaintenanceRepositories.MaintenancePlanRepository;
import com.crmplatform.operations.warranties.application.WarrantyService;
import com.crmplatform.shared.tenant.TenantWork;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** SLA policy, delete guards and the daily maintenance run (generation + reminders). */
@Configuration(proxyBeanMethods = false)
public class MaintenanceIntegration {

    /** Unplanned jobs at a site under an active agreement get its response time. */
    @Bean
    SlaPolicy agreementSlaPolicy(MaintenancePlanRepository plans) {
        return plans::shortestResponseHoursAt;
    }

    /** Customers and sites with registered equipment or maintenance plans cannot be deleted. */
    @Bean
    ProjectUsage siteAssetsUsage(EquipmentRepository equipment, MaintenancePlanRepository plans) {
        return new ProjectUsage() {
            @Override
            public boolean contactHasProjects(UUID contactId) {
                return equipment.existsByContactId(contactId) || plans.existsByContactId(contactId);
            }

            @Override
            public boolean locationHasProjects(UUID locationId) {
                return equipment.existsByLocationId(locationId) || plans.existsByLocationId(locationId);
            }
        };
    }

    /**
     * Daily, single-instance (ShedLock). Each item runs in its own workspace transaction, so one
     * failure never blocks the others and every step is idempotent when repeated.
     */
    @Component
    public static class MaintenanceDaily {

        private static final Logger log = LoggerFactory.getLogger(MaintenanceDaily.class);

        private final MaintenanceService maintenance;
        private final WarrantyService warranties;
        private final WorkspaceMembers members;
        private final TenantWork tenantWork;

        MaintenanceDaily(MaintenanceService maintenance, WarrantyService warranties, WorkspaceMembers members,
                TenantWork tenantWork) {
            this.maintenance = maintenance;
            this.warranties = warranties;
            this.members = members;
            this.tenantWork = tenantWork;
        }

        @Scheduled(cron = "${app.operations.maintenance-cron:0 15 0 * * *}")
        @SchedulerLock(name = "maintenance-daily", lockAtMostFor = "PT30M")
        void scheduled() {
            Summary s = runOnce();
            if (s.total() > 0) {
                log.info("Maintenance run: {}", s);
            }
        }

        /** One full pass; also called by tests. */
        public Summary runOnce() {
            int generated = each(maintenance::plansDue, (ws, id) -> maintenance.generateDue(id));
            int overdue = each(maintenance::occurrencesOverdue, (ws, id) -> maintenance.remindOverdue(id) ? 1 : 0);
            int warrantyReminders = each(warranties::dueForReminder,
                    (ws, id) -> warranties.remind(id, members.managers()) ? 1 : 0);
            int renewals = each(maintenance::plansEnding, (ws, id) -> maintenance.remindEnding(id) ? 1 : 0);
            int ended = tenantWork.callAsSystem(maintenance::endExpired);
            return new Summary(generated, overdue, warrantyReminders, renewals, ended);
        }

        private int each(Supplier<List<Object[]>> rows, BiFunction<UUID, UUID, Integer> work) {
            int count = 0;
            for (Object[] row : tenantWork.callAsSystem(rows)) {
                UUID id = uuid(row[0]);
                UUID workspace = uuid(row[1]);
                try {
                    count += tenantWork.call(workspace, null, () -> work.apply(workspace, id));
                } catch (RuntimeException e) {
                    log.warn("Maintenance step failed for {} in workspace {}", id, workspace, e);
                }
            }
            return count;
        }

        private static UUID uuid(Object value) {
            return value instanceof UUID u ? u : UUID.fromString(String.valueOf(value));
        }
    }

    public record Summary(int jobsGenerated, int overdueReminders, int warrantyReminders, int renewalReminders,
            int plansEnded) {
        int total() {
            return jobsGenerated + overdueReminders + warrantyReminders + renewalReminders + plansEnded;
        }
    }
}
