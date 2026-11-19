package com.crmplatform.crm.notes.application;

import com.crmplatform.crm.CrmReferences.NoteParents;
import com.crmplatform.crm.contacts.domain.ContactRepository;
import com.crmplatform.crm.notes.domain.Note;
import com.crmplatform.crm.notes.domain.NoteRepository;
import com.crmplatform.files.FileKind;
import com.crmplatform.files.Files;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.events.AuditTrail;
import com.crmplatform.shared.security.AuthenticatedUser;
import com.crmplatform.shared.security.CurrentUser;
import com.crmplatform.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Notes (FR-CRM-11, BR-030..032, AZ-01). */
@Service
public class NoteService {

    private final NoteRepository notes;
    private final ContactRepository contacts;
    private final ObjectProvider<NoteParents> parents;
    private final Files files;
    private final AuditTrail audit;
    private final Clock clock;

    public NoteService(NoteRepository notes, ContactRepository contacts, ObjectProvider<NoteParents> parents,
            Files files, AuditTrail audit, Clock clock) {
        this.notes = notes;
        this.contacts = contacts;
        this.parents = parents;
        this.files = files;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Note> list(UUID contactId, UUID projectId, UUID jobId, Pageable pageable) {
        if (Stream.of(contactId, projectId, jobId).noneMatch(Objects::nonNull)) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Filter by contactId, projectId or jobId");
        }
        return notes.findByParent(contactId, projectId, jobId, pageable);
    }

    @Transactional(readOnly = true)
    public Note get(UUID id) {
        return notes.findById(id).orElseThrow(() -> ApiException.notFound("Note"));
    }

    @Transactional
    public Note create(UUID contactId, UUID projectId, UUID jobId, String title, String body, UUID documentFileId) {
        long parentCount = Stream.of(contactId, projectId, jobId).filter(Objects::nonNull).count();
        if (parentCount != 1) {
            throw ApiException.badRequest("NOTE_PARENT_REQUIRED",
                    "A note must belong to exactly one contact, project or job");
        }
        NoteParents resolver = parents.getIfAvailable();
        boolean exists = contactId != null ? contacts.existsById(contactId)
                : resolver != null && (projectId != null ? resolver.projectExists(projectId) : resolver.jobExists(jobId));
        if (!exists) {
            throw ApiException.referenceNotFound(contactId != null ? "contactId" : projectId != null ? "projectId" : "jobId");
        }
        Note note = notes.save(new Note(contactId, projectId, jobId, title, body));
        attachDocument(note, documentFileId);
        audit.created("NOTE", note.getId(), projectId, jobId, Map.of("title", note.getTitle()));
        return note;
    }

    @Transactional
    public Note update(UUID id, String title, String body, boolean documentSent, UUID documentFileId) {
        Note note = get(id);
        if (!Objects.equals(note.getCreatedBy(), CurrentUser.id())) {
            throw ApiException.forbidden("Only the author can edit this note");
        }
        Map<String, Object> before = Map.of("title", note.getTitle(), "body", note.getBody());
        note.edit(title == null ? note.getTitle() : title, body == null ? note.getBody() : body);
        if (documentSent && !Objects.equals(documentFileId, note.getDocumentFileId())) {
            UUID previous = note.getDocumentFileId();
            attachDocument(note, documentFileId);
            if (previous != null) {
                files.delete(previous); // BR-032
            }
        }
        audit.updated("NOTE", id, note.getProjectId(), note.getJobId(), before,
                Map.of("title", note.getTitle(), "body", note.getBody()));
        return note;
    }

    @Transactional
    public void delete(UUID id) {
        Note note = get(id);
        AuthenticatedUser user = CurrentUser.require();
        if (!Objects.equals(note.getCreatedBy(), user.userId()) && !user.isAdminOrOwner()) {
            throw ApiException.forbidden("Only the author or an admin can delete this note");
        }
        note.markDeleted(Instant.now(clock), user.userId());
        if (note.getDocumentFileId() != null) {
            files.delete(note.getDocumentFileId());
        }
        audit.deleted("NOTE", id, note.getProjectId(), note.getJobId());
    }

    private void attachDocument(Note note, UUID documentFileId) {
        files.attach(documentFileId == null ? List.of() : List.of(documentFileId), FileKind.DOCUMENT, "NOTE",
                note.getId(), "documentFileId");
        note.setDocumentFileId(documentFileId);
        TenantContext.requireWorkspaceId();
    }
}
