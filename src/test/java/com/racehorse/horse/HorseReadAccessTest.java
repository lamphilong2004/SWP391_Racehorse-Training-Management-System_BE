package com.racehorse.horse;

import com.racehorse.auth.*;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.horse.activation.HorseActivationService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HorseReadAccessTest {
    final HorseRepository repository=mock(HorseRepository.class);
    final CurrentActorProvider actors=mock(CurrentActorProvider.class);
    final HorseService service=new HorseService(repository,actors,mock(HorseActivationService.class));
    void actor(RoleCode role){when(actors.currentActor()).thenReturn(Optional.of(new ActorContext(12,List.of(role))));}
    @Test void unrestrictedStaffUseOnlyTheLightweightReadGate(){
        for(RoleCode role:List.of(RoleCode.CLUB_MANAGER,RoleCode.HEAD_TRAINER,RoleCode.VETERINARIAN,RoleCode.GROOM)){
            actor(role);when(repository.canRead(7,null)).thenReturn(true);service.assertCanRead(7);
        }
        verify(repository,times(4)).canRead(7,null);verifyNoMoreInteractions(repository);
    }
    @Test void trainerIsStillScopedToTheirAssignment(){
        actor(RoleCode.TRAINER);when(repository.canRead(7,12L)).thenReturn(true);service.assertCanRead(7);
        verify(repository).canRead(7,12L);verifyNoMoreInteractions(repository);
    }
    @Test void outOfScopeTrainerRemainsForbidden(){
        actor(RoleCode.TRAINER);when(repository.exists(7)).thenReturn(true);
        BusinessException e=assertThrows(BusinessException.class,()->service.assertCanRead(7));
        assertEquals(ErrorCode.FORBIDDEN,e.code());
    }
    @Test void missingHorseRemainsNotFound(){
        actor(RoleCode.CLUB_MANAGER);
        BusinessException e=assertThrows(BusinessException.class,()->service.assertCanRead(7));
        assertEquals(ErrorCode.NOT_FOUND,e.code());
    }
    @Test void anonymousDoesNotQueryHorseOrPhotoData(){
        when(actors.currentActor()).thenReturn(Optional.empty());
        BusinessException e=assertThrows(BusinessException.class,()->service.assertCanRead(7));
        assertEquals(ErrorCode.UNAUTHORIZED,e.code());verifyNoInteractions(repository);
    }
}
