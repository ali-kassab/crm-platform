package com.crmplatform.crm.refdata.application;

import com.crmplatform.crm.LabelCatalog;
import com.crmplatform.crm.LabelDeleted;
import com.crmplatform.crm.refdata.domain.Category;
import com.crmplatform.crm.refdata.domain.Label;
import com.crmplatform.crm.refdata.domain.Label.LabelValues;
import com.crmplatform.crm.refdata.domain.RefDataRepositories.CategoryRepository;
import com.crmplatform.crm.refdata.domain.RefDataRepositories.StatusRepository;
import com.crmplatform.crm.refdata.domain.RefDataRepositories.TagRepository;
import com.crmplatform.crm.refdata.domain.Status;
import com.crmplatform.crm.refdata.domain.Tag;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.events.AuditTrail;
import com.crmplatform.shared.spi.ProcedureDirectory;
import com.crmplatform.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Categories, statuses and tags (FR-CRM-01..03, BR-010..016). */
@Service
public class RefDataService implements LabelCatalog {

    private final CategoryRepository categories;
    private final StatusRepository statuses;
    private final TagRepository tags;
    private final ObjectProvider<ProcedureDirectory> procedures;
    private final ApplicationEventPublisher events;
    private final AuditTrail audit;
    private final Clock clock;

    public RefDataService(CategoryRepository categories, StatusRepository statuses, TagRepository tags,
            ObjectProvider<ProcedureDirectory> procedures, ApplicationEventPublisher events, AuditTrail audit,
            Clock clock) {
        this.categories = categories;
        this.statuses = statuses;
        this.tags = tags;
        this.procedures = procedures;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ categories

    @Transactional(readOnly = true)
    public Page<Category> listCategories(String q, Pageable pageable) {
        return categories.search(q, pageable);
    }

    @Transactional(readOnly = true)
    public Category category(UUID id) {
        return categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
    }

    @Transactional
    public Category createCategory(String name, String description) {
        if (categories.nameTaken(name.trim(), null)) {
            throw nameTaken("category");
        }
        Category category = categories.save(new Category(name, description));
        audit.created("CATEGORY", category.getId(), null, null, Map.of("name", category.getName()));
        return category;
    }

    @Transactional
    public Category updateCategory(UUID id, String name, String description) {
        Category category = category(id);
        if (categories.nameTaken(name.trim(), id)) {
            throw nameTaken("category");
        }
        Map<String, Object> before = Map.of("name", category.getName());
        category.update(name, description);
        audit.updated("CATEGORY", id, null, null, before, Map.of("name", category.getName()));
        return category;
    }

    @Transactional
    public void deleteCategory(UUID id) {
        Category category = category(id);
        if (statuses.existsByCategoryId(id) || tags.existsByCategoryId(id)) {
            throw ApiException.conflict("IN_USE", "This category is used by statuses or tags");
        }
        category.markDeleted(Instant.now(clock), TenantContext.userId().orElse(null));
        audit.deleted("CATEGORY", id, null, null);
    }

    // ------------------------------------------------------------------ statuses

    @Transactional(readOnly = true)
    public Page<Status> listStatuses(String q, UUID categoryId, Pageable pageable) {
        return statuses.search(q, categoryId, pageable);
    }

    @Transactional(readOnly = true)
    public Status statusEntity(UUID id) {
        return statuses.findById(id).orElseThrow(() -> ApiException.notFound("Status"));
    }

    @Transactional
    public Status createStatus(LabelValues values) {
        validateLabel(values, null, true);
        Status status = statuses.save(new Status(values));
        audit.created("STATUS", status.getId(), null, null, Map.of("name", status.getName()));
        return status;
    }

    @Transactional
    public Status updateStatus(UUID id, LabelValues values) {
        Status status = statusEntity(id);
        validateLabel(values, id, true);
        Map<String, Object> before = snapshot(status);
        status.update(values);
        audit.updated("STATUS", id, null, null, before, snapshot(status));
        return status;
    }

    @Transactional
    public void deleteStatus(UUID id) {
        Status status = statusEntity(id);
        status.markDeleted(Instant.now(clock), TenantContext.userId().orElse(null));
        events.publishEvent(new LabelDeleted(TenantContext.requireWorkspaceId(), id, false));
        audit.deleted("STATUS", id, null, null);
    }

    // ------------------------------------------------------------------ tags

    @Transactional(readOnly = true)
    public Page<Tag> listTags(String q, UUID categoryId, Pageable pageable) {
        return tags.search(q, categoryId, pageable);
    }

    @Transactional(readOnly = true)
    public Tag tagEntity(UUID id) {
        return tags.findById(id).orElseThrow(() -> ApiException.notFound("Tag"));
    }

    @Transactional
    public Tag createTag(LabelValues values) {
        validateLabel(values, null, false);
        Tag tag = tags.save(new Tag(values));
        audit.created("TAG", tag.getId(), null, null, Map.of("name", tag.getName()));
        return tag;
    }

    @Transactional
    public Tag updateTag(UUID id, LabelValues values) {
        Tag tag = tagEntity(id);
        validateLabel(values, id, false);
        Map<String, Object> before = snapshot(tag);
        tag.update(values);
        audit.updated("TAG", id, null, null, before, snapshot(tag));
        return tag;
    }

    /** Deleting a tag removes it from every entity and stops the runs it started (BR-015, EC-11). */
    @Transactional
    public void deleteTag(UUID id) {
        Tag tag = tagEntity(id);
        tag.markDeleted(Instant.now(clock), TenantContext.userId().orElse(null));
        events.publishEvent(new LabelDeleted(TenantContext.requireWorkspaceId(), id, true));
        audit.deleted("TAG", id, null, null);
    }


    // ------------------------------------------------------------------ LabelCatalog (public API)

    @Override
    @Transactional(readOnly = true)
    public void requireTags(Collection<UUID> tagIds, String field) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        Set<UUID> unique = new HashSet<>(tagIds);
        if (tags.findAllById(unique).size() != unique.size()) {
            throw ApiException.referenceNotFound(field);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requireStatus(UUID statusId, String field) {
        if (statusId != null && !statuses.existsById(statusId)) {
            throw ApiException.referenceNotFound(field);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, LabelSummary> tags(Collection<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Map.of();
        }
        return tags.findAllById(new HashSet<>(tagIds)).stream()
                .collect(Collectors.toMap(Tag::getId, RefDataService::summary, (a, b) -> a, LinkedHashMap::new));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LabelSummary> status(UUID statusId) {
        return statusId == null ? Optional.empty() : statuses.findById(statusId).map(RefDataService::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TriggerBinding> tagBinding(UUID tagId) {
        return tags.findById(tagId).map(t -> new TriggerBinding(t.getStartProcedureId(), t.getStopProcedureId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TriggerBinding> statusBinding(UUID statusId) {
        return statuses.findById(statusId).map(s -> new TriggerBinding(s.getStartProcedureId(), s.getStopProcedureId()));
    }

    @Override
    @Transactional
    public void unbindProcedure(UUID procedureId) {
        tags.boundTo(procedureId).forEach(t -> t.unbindProcedure(procedureId));
        statuses.boundTo(procedureId).forEach(s -> s.unbindProcedure(procedureId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isProcedureBound(UUID procedureId) {
        return !tags.boundTo(procedureId).isEmpty() || !statuses.boundTo(procedureId).isEmpty();
    }

    public Map<UUID, Category> categoriesById(Collection<UUID> ids) {
        return categories.findAllById(new HashSet<>(ids)).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    // ------------------------------------------------------------------ helpers

    private void validateLabel(LabelValues v, UUID selfId, boolean isStatus) {
        if (!categories.existsById(v.categoryId())) {
            throw ApiException.referenceNotFound("categoryId");
        }
        boolean taken = isStatus ? statuses.nameTaken(v.name().trim(), selfId) : tags.nameTaken(v.name().trim(), selfId);
        if (taken) {
            throw nameTaken(isStatus ? "status" : "tag");
        }
        if (v.startProcedureId() != null && v.startProcedureId().equals(v.stopProcedureId())) {
            throw ApiException.badRequest("SAME_START_STOP_PROCEDURE",
                    "The start and stop procedures must be different");
        }
        for (UUID procedureId : new UUID[] {v.startProcedureId(), v.stopProcedureId()}) {
            if (procedureId != null) {
                ProcedureDirectory directory = procedures.getIfAvailable();
                if (directory == null || !directory.exists(procedureId)) {
                    throw ApiException.referenceNotFound(procedureId.equals(v.startProcedureId())
                            ? "startProcedureId" : "stopProcedureId");
                }
            }
        }
        if (v.startProcedureId() != null) {
            List<Tag> boundTags = tags.boundToStart(v.startProcedureId(), isStatus ? null : selfId);
            List<Status> boundStatuses = statuses.boundToStart(v.startProcedureId(), isStatus ? selfId : null);
            if (!boundTags.isEmpty() || !boundStatuses.isEmpty()) {
                throw ApiException.conflict("PROCEDURE_ALREADY_BOUND",
                        "This procedure already starts from another tag or status");
            }
        }
    }

    private static Map<String, Object> snapshot(Label label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", label.getName());
        m.put("categoryId", label.getCategoryId());
        m.put("backgroundColor", label.getBackgroundColor());
        m.put("textColor", label.getTextColor());
        m.put("icon", label.getIcon());
        m.put("startProcedureId", label.getStartProcedureId());
        m.put("stopProcedureId", label.getStopProcedureId());
        return m;
    }

    private static LabelSummary summary(Label l) {
        return new LabelSummary(l.getId(), l.getName(), l.getBackgroundColor(), l.getTextColor(), l.getIcon());
    }

    private static ApiException nameTaken(String what) {
        return ApiException.conflict("NAME_TAKEN", "A " + what + " with this name already exists");
    }
}
