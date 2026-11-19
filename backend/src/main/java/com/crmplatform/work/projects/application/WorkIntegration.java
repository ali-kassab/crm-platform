package com.crmplatform.work.projects.application;

import com.crmplatform.crm.CrmReferences.NoteParents;
import com.crmplatform.crm.CrmReferences.ProjectUsage;
import com.crmplatform.crm.LabelDeleted;
import com.crmplatform.identity.WorkspaceCreated;
import com.crmplatform.milestones.MilestoneOwner;
import com.crmplatform.shared.tenant.TenantContext;
import com.crmplatform.work.WorkReferences.JobDirectory;
import com.crmplatform.work.boards.application.BoardService;
import com.crmplatform.work.boards.domain.BoardRepositories.TaskRepository;
import com.crmplatform.work.projects.domain.ProjectRepositories.ProjectRepository;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/** Hooks the work module provides to lower modules, and its reactions to their events. */
@Configuration(proxyBeanMethods = false)
class WorkIntegration {

    /** BR-028: contacts/locations with projects cannot be deleted. */
    @Bean
    ProjectUsage projectUsage(ProjectRepository projects) {
        return new ProjectUsage() {
            @Override
            public boolean contactHasProjects(UUID contactId) {
                return projects.existsByContactId(contactId);
            }

            @Override
            public boolean locationHasProjects(UUID locationId) {
                return projects.existsByLocationId(locationId);
            }
        };
    }

    /** BR-030: notes may belong to a project or job. */
    @Bean
    NoteParents noteParents(ProjectRepository projects, ObjectProvider<JobDirectory> jobs) {
        return new NoteParents() {
            @Override
            public boolean projectExists(UUID projectId) {
                return projects.existsById(projectId);
            }

            @Override
            public boolean jobExists(UUID jobId) {
                JobDirectory directory = jobs.getIfAvailable();
                return directory != null && directory.job(jobId).isPresent();
            }
        };
    }

    @Bean
    MilestoneOwner projectMilestoneOwner(ProjectRepository projects) {
        return new MilestoneOwner() {
            @Override
            public OwnerType type() {
                return OwnerType.PROJECT;
            }

            @Override
            public boolean exists(UUID id) {
                return projects.existsById(id);
            }
        };
    }

    @Bean
    MilestoneOwner taskMilestoneOwner(TaskRepository tasks) {
        return new MilestoneOwner() {
            @Override
            public OwnerType type() {
                return OwnerType.TASK;
            }

            @Override
            public boolean exists(UUID id) {
                return tasks.existsById(id);
            }
        };
    }

    @Component
    static class Handlers {

        private final BoardService boards;
        private final ProjectService projects;
        private final TransactionTemplate requiresNew;

        Handlers(BoardService boards, ProjectService projects, PlatformTransactionManager tm) {
            this.boards = boards;
            this.projects = projects;
            this.requiresNew = new TransactionTemplate(tm);
            this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }

        /** Seeds the workspace's default board template right after sign-up (AUTH_FLOWS F1). */
        @TransactionalEventListener
        void on(WorkspaceCreated event) {
            TenantContext.runAs(event.workspaceId(), event.ownerUserId(),
                    () -> requiresNew.executeWithoutResult(s -> boards.seedDefaultTemplate()));
        }

        @ApplicationModuleListener
        void on(LabelDeleted event) {
            if (event.tag()) {
                TenantContext.runAs(event.workspaceId(), null, () -> projects.detachTag(event.labelId()));
            }
        }
    }
}
