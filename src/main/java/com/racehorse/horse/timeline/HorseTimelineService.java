package com.racehorse.horse.timeline;

import com.racehorse.auth.ActorContext;
import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.auth.RoleCode;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.horse.HorseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class HorseTimelineService {

    private final HorseService horses;
    private final TimelineRepository repository;
    private final CurrentActorProvider actors;

    public HorseTimelineService(HorseService horses, TimelineRepository repository,
                                CurrentActorProvider actors) {
        this.horses = horses;
        this.repository = repository;
        this.actors = actors;
    }

    @Transactional(readOnly = true)
    public TimelinePage getTimeline(long horseId, int page, int size) {
        horses.detail(horseId);
        ActorContext actor = actors.currentActor().orElseThrow(() ->
                new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required."));

        TimelineRepository.Result result = repository.page(horseId, page, size);
        var items = result.items().stream()
                .map(event -> mayViewTarget(actor, event) ? event : event.withoutTarget())
                .toList();
        return new TimelinePage(items, result.page(), result.size(), result.totalItems(), result.totalPages());
    }

    private static boolean mayViewTarget(ActorContext actor, TimelineEvent event) {
        return switch (event.eventType()) {
            case HORSE_ACTIVATED -> true;
            case STABLE_ASSIGNED, OWNERSHIP_CHANGED ->
                    hasAnyRole(actor, RoleCode.CLUB_MANAGER, RoleCode.HEAD_TRAINER);
            case VETERINARY_EXAMINATION, RESTRICTION_CHANGED ->
                    actor.roles().contains(RoleCode.VETERINARIAN);
            case PROGRAM_ACTIVATED, TRAINING_SESSION, INCIDENT_REPORTED -> false;
        };
    }

    private static boolean hasAnyRole(ActorContext actor, RoleCode... roles) {
        Set<RoleCode> allowed = Set.of(roles);
        return actor.roles().stream().anyMatch(allowed::contains);
    }
}
