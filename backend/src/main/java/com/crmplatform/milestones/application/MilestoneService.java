package com.crmplatform.milestones.application;

import com.crmplatform.files.FileKind;
import com.crmplatform.files.Files;
import com.crmplatform.milestones.MilestoneOwner;
import com.crmplatform.milestones.MilestoneOwner.OwnerType;
import com.crmplatform.milestones.Milestones;
import com.crmplatform.milestones.domain.Milestone;
import com.crmplatform.milestones.domain.MilestoneRepositories.MilestoneRepository;
import com.crmplatform.milestones.domain.MilestoneRepositories.MilestoneTemplateRepository;
import com.crmplatform.milestones.domain.MilestoneStep;
import com.crmplatform.milestones.domain.MilestoneTemplate;
import com.crmplatform.milestones.domain.MilestoneTemplate.TemplateStep;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.events.AuditTrail;
import com.crmplatform.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entity milestones and milestone templates (FR-CRM-12/13, BR-040..044). */
@Service
public class MilestoneService implements Milestones {

    private static final int MAX_STEPS = 30;

    private final MilestoneRepository milestones;
    private final MilestoneTemplateRepository templates;
    private final Map<OwnerType, MilestoneOwner> owners = new EnumMap<>(OwnerType.class);
    private final Files files;
    private final AuditTrail audit;
    private final Clock clock;

    public MilestoneService(MilestoneRepository milestones, MilestoneTemplateRepository templates,
            List<MilestoneOwner> ownerVerifiers, Files files, AuditTrail audit, Clock clock) {
        this.milestones = milestones;
        this.templates = templates;
        ownerVerifiers.forEach(o -> owners.put(o.type(), o));
        this.files = files;
        this.audit = audit;
        this.clock = clock;
    }

    /** Throws 404 unless the owner record exists in the active workspace. */
    public void requireOwner(OwnerType type, UUID ownerId) {
        MilestoneOwner owner = owners.get(type);
        if (owner == null || !owner.exists(ownerId)) {
            throw ApiException.notFound(type.name().charAt(0) + type.name().substring(1).toLowerCase());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MilestoneView> find(OwnerType ownerType, UUID ownerId) {
        return milestones.findByOwnerTypeAndOwnerId(ownerType, ownerId).map(MilestoneService::view);
    }

    @Override
    @Transactional
    public MilestoneView set(OwnerType ownerType, UUID ownerId, MilestoneInput input) {
        List<MilestoneStep> steps = toSteps(input.steps(), ownerType == OwnerType.CONTACT);
        Milestone milestone = milestones.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .orElseGet(() -> new Milestone(ownerType, ownerId));
        List<UUID> images = steps.stream().map(MilestoneStep::getImageFileId).filter(Objects::nonNull).toList();
        milestone.define(input.name(), input.description(), input.categoryId(), steps);
        Milestone saved = milestones.save(milestone);
        files.attach(images, FileKind.IMAGE, "MILESTONE", saved.getId(), "steps.imageFileId");
        files.detachAllExcept("MILESTONE", saved.getId(), images);
        audit.action("SET_MILESTONE", ownerType.name(), ownerId, null, null, Map.of("name", saved.getName()));
        return view(saved);
    }

    @Override
    @Transactional
    public MilestoneView setFromTemplate(OwnerType ownerType, UUID ownerId, UUID templateId) {
        MilestoneTemplate template = templates.findById(templateId)
                .orElseThrow(() -> ApiException.referenceNotFound("milestoneTemplateId"));
        List<StepInput> steps = template.getSteps().stream().map(s -> new StepInput(s.order(), s.label(), s.icon(),
                s.backgroundColor(), s.textColor(), s.viewMode(), null, s.startDate(), s.endDate())).toList();
        return set(ownerType, ownerId, new MilestoneInput(template.getName(), template.getDescription(),
                template.getCategoryId(), steps));
    }

    @Override
    @Transactional
    public void clear(OwnerType ownerType, UUID ownerId) {
        milestones.findByOwnerTypeAndOwnerId(ownerType, ownerId).ifPresent(m -> {
            files.detachAllExcept("MILESTONE", m.getId(), List.of());
            m.markDeleted(Instant.now(clock), TenantContext.userId().orElse(null));
            audit.action("CLEAR_MILESTONE", ownerType.name(), ownerId, null, null, Map.of());
        });
    }

    @Transactional
    public MilestoneView complete(OwnerType ownerType, UUID ownerId, UUID stepId) {
        Milestone m = require(ownerType, ownerId);
        m.complete(stepId, TenantContext.userId().orElse(null), Instant.now(clock));
        audit.action("COMPLETE_STEP", ownerType.name(), ownerId, null, null, Map.of("stepId", stepId));
        return view(m);
    }

    @Transactional
    public MilestoneView reopen(OwnerType ownerType, UUID ownerId, UUID stepId) {
        Milestone m = require(ownerType, ownerId);
        m.reopen(stepId);
        return view(m);
    }

    @Override
    @Transactional
    public MilestoneView completeStepByOrder(OwnerType ownerType, UUID ownerId, int stepOrder) {
        Milestone m = require(ownerType, ownerId);
        m.completeByOrder(stepOrder, TenantContext.userId().orElse(null), Instant.now(clock));
        return view(m);
    }

    // ------------------------------------------------------------------ templates

    @Transactional(readOnly = true)
    public Page<MilestoneTemplate> listTemplates(String q, Pageable pageable) {
        return templates.search(q, pageable);
    }

    @Transactional(readOnly = true)
    public MilestoneTemplate template(UUID id) {
        return templates.findById(id).orElseThrow(() -> ApiException.notFound("Milestone template"));
    }

    @Transactional
    public MilestoneTemplate createTemplate(String name, String description, UUID categoryId, List<StepInput> steps) {
        return templates.save(new MilestoneTemplate(name, description, categoryId, toTemplateSteps(steps)));
    }

    @Transactional
    public MilestoneTemplate updateTemplate(UUID id, String name, String description, UUID categoryId,
            List<StepInput> steps) {
        MilestoneTemplate t = template(id);
        t.update(name, description, categoryId, toTemplateSteps(steps));
        return t;
    }

    @Transactional
    public void deleteTemplate(UUID id) {
        template(id).markDeleted(Instant.now(clock), TenantContext.userId().orElse(null));
    }

    // ------------------------------------------------------------------ helpers

    private Milestone require(OwnerType ownerType, UUID ownerId) {
        return milestones.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .orElseThrow(() -> ApiException.notFound("Milestone"));
    }

    private static List<MilestoneStep> toSteps(List<StepInput> inputs, boolean contactDefaults) {
        List<StepInput> source = inputs == null ? List.of() : inputs;
        if (source.isEmpty() && contactDefaults) {
            source = List.of(new StepInput(1, "Initial Contact", null, "#4A90E2", "#FFFFFF", "ICON", null, null, null),
                    new StepInput(2, "Contract Signed", null, "#4A90E2", "#FFFFFF", "ICON", null, null, null));
        }
        if (source.size() > MAX_STEPS) {
            throw ApiException.badRequest("VALIDATION_ERROR", "A milestone can have at most " + MAX_STEPS + " steps");
        }
        List<MilestoneStep> steps = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            StepInput s = source.get(i);
            if (s.label() == null || s.label().isBlank()) {
                throw ApiException.badRequest("VALIDATION_ERROR", "Step " + (i + 1) + " needs a label");
            }
            if (s.startDate() != null && s.endDate() != null && s.endDate().isBefore(s.startDate())) {
                throw ApiException.badRequest("VALIDATION_ERROR", "Step " + (i + 1) + " ends before it starts");
            }
            steps.add(new MilestoneStep(s.order() == null ? i + 1 : s.order(), s.label(), s.icon(),
                    s.backgroundColor(), s.textColor(), s.viewMode(), s.imageFileId(), s.startDate(), s.endDate()));
        }
        return steps;
    }

    private static List<TemplateStep> toTemplateSteps(List<StepInput> inputs) {
        if (inputs == null || inputs.size() < 2) {
            throw ApiException.badRequest("VALIDATION_ERROR", "A milestone template needs at least 2 steps");
        }
        List<MilestoneStep> validated = toSteps(inputs, false);
        if (validated.stream().map(MilestoneStep::getStepOrder).distinct().count() != validated.size()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Step orders must be unique within a milestone");
        }
        return validated.stream().map(s -> new TemplateStep(s.getStepOrder(), s.getLabel(), s.getIcon(),
                s.getBackgroundColor(), s.getTextColor(), s.getViewMode(), null, s.getStartDate(), s.getEndDate()))
                .toList();
    }

    static MilestoneView view(Milestone m) {
        List<StepView> steps = m.getSteps().stream().map(s -> new StepView(s.getId(), s.getStepOrder(), s.getLabel(),
                s.getIcon(), s.getBackgroundColor(), s.getTextColor(), s.getViewMode(), s.getImageFileId(),
                s.getStartDate(), s.getEndDate(), s.isCompleted(), s.getCompletedAt(), s.getCompletedBy())).toList();
        return new MilestoneView(m.getId(), m.getOwnerType().name(), m.getOwnerId(), m.getName(), m.getDescription(),
                m.getCategoryId(), m.getCurrentStep(), m.progress(), steps, m.getVersion());
    }
}
