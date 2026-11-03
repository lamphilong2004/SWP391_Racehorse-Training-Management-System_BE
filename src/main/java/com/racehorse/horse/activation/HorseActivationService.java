package com.racehorse.horse.activation;

import com.racehorse.auth.ActorContext;
import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.common.audit.AuditRepository;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class HorseActivationService {

    private final HorseActivationRepository repository;
    private final AuditRepository auditRepository;
    private final CurrentActorProvider actors;

    public HorseActivationService(
            HorseActivationRepository repository,
            AuditRepository auditRepository,
            CurrentActorProvider actors) {
        this.repository = repository;
        this.auditRepository = auditRepository;
        this.actors = actors;
    }

    @Transactional(readOnly = true)
    public ActivationPage listPending(int page, int size) {
        long totalItems = repository.countDraftHorses();
        List<ActivationStatus> items = repository.findDraftHorseIds(page, size)
                .stream()
                .map(this::statusForExistingHorse)
                .toList();
        return new ActivationPage(items, page, size, totalItems,
                totalItems / size + (totalItems % size == 0 ? 0 : 1));
    }

    @Transactional(readOnly = true)
    public ActivationStatus getStatus(long horseId) {
        return statusForExistingHorse(horseId);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public boolean reevaluate(long horseId) {
        HorseActivationRepository.HorseFacts facts = repository.findFacts(horseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Horse not found."));
        if (!"DRAFT".equals(facts.lifecycle()) || !facts.allMandatoryConditionsSatisfied()) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return repository.activateDraft(horseId, now) == 1;
    }

    @Transactional
    public void recordReadinessReview(long horseId) {
        if (repository.findFacts(horseId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Horse not found.");
        }
        ActorContext actor = actors.currentActor()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required."));
        auditRepository.recordReadinessReview(horseId, actor);
    }

    private ActivationStatus statusForExistingHorse(long horseId) {
        HorseActivationRepository.HorseFacts facts = repository.findFacts(horseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Horse not found."));
        boolean reviewed = repository.hasReadinessReview(horseId);
        return new ActivationStatus(
                facts.horseId(),
                facts.horseCode(),
                facts.name(),
                facts.lifecycle(),
                facts.activatedAt(),
                checklist(facts, reviewed));
    }

    private static List<ActivationChecklistItem> checklist(
            HorseActivationRepository.HorseFacts facts,
            boolean readinessReviewed) {
        List<ActivationChecklistItem> items = new ArrayList<>(6);
        items.add(mandatory("IDENTITY", "Horse identity", facts.identityValid(),
                "Horse identity is valid.", "Horse identity is incomplete."));
        items.add(mandatory("PEDIGREE", "Pedigree", facts.pedigreeValid(),
                "Pedigree is valid.", "Pedigree is incomplete."));
        items.add(mandatory("OWNER", "Owner", facts.ownerValid(),
                "A valid owner is linked to the horse.", "A valid owner is not linked to the horse."));
        items.add(mandatory("FREE_STABLE_BOX", "Free stable box", facts.freeStableBox(),
                "A free stable box is assigned to the horse.", "A free stable box is not assigned to the horse."));
        items.add(mandatory("VETERINARIAN_BASELINE", "Veterinarian baseline", facts.veterinarianBaseline(),
                "A valid Veterinarian baseline is recorded.",
                "A valid Veterinarian baseline is not recorded."));
        items.add(new ActivationChecklistItem(
                "HEAD_TRAINER_READINESS_REVIEW",
                "Head Trainer readiness review",
                "Recommended review; this item is non-blocking and does not gate activation.",
                "RECOMMENDED",
                readinessReviewed ? "REVIEWED" : "NOT_REVIEWED"));
        return items;
    }

    private static ActivationChecklistItem mandatory(
            String id,
            String label,
            boolean satisfied,
            String satisfiedDescription,
            String pendingDescription) {
        return new ActivationChecklistItem(
                id,
                label,
                satisfied ? satisfiedDescription : pendingDescription,
                "MANDATORY",
                satisfied ? "SATISFIED" : "PENDING");
    }
}
