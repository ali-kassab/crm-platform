package com.crmplatform.files.application;

import com.crmplatform.shared.tenant.TenantContext;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Hourly cleanup of uploads that were never attached (FR-FIL-04). Single-instance via ShedLock. */
@Component
class OrphanFileCleanup {

    private static final Logger log = LoggerFactory.getLogger(OrphanFileCleanup.class);

    private final FileService files;

    OrphanFileCleanup(FileService files) {
        this.files = files;
    }

    @Scheduled(cron = "${app.files.orphan-cleanup-cron:0 17 * * * *}")
    @SchedulerLock(name = "files-orphan-cleanup", lockAtMostFor = "PT10M")
    void run() {
        int removed = TenantContext.runAs(TenantContext.SYSTEM, null, files::purgeOrphans);
        if (removed > 0) {
            log.info("Removed {} orphaned uploads", removed);
        }
    }
}
