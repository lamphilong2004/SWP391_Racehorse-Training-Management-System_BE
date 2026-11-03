package com.racehorse.stablecare;

import com.racehorse.auth.*;
import com.racehorse.common.error.BusinessException;
import com.racehorse.medical.incident.*;
import com.racehorse.notification.NotificationPublisher;
import com.racehorse.training.TrainingCalendar;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StableCareIncidentTest {
    @Test void assignedDueCareTaskCanReachMedicalButOtherGroomAndMismatchedHorseCannot(){
        var repo=mock(Core026Repository.class);var actors=mock(CurrentActorProvider.class);var clock=mock(TrainingCalendar.class);
        var service=new Core026Service(repo,actors,JsonMapper.builder().build(),mock(NotificationPublisher.class),clock);
        var day=LocalDate.of(2026,10,28);when(clock.today()).thenReturn(day);
        when(actors.currentActor()).thenReturn(Optional.of(new ActorContext(2,List.of(RoleCode.GROOM))));
        when(repo.careTaskHorse(10)).thenReturn(Optional.of(20L));
        var request=new IncidentApi.CreateRequest(20L,null,null,10L,"OTHER","LOW","SIMULATED observation",OffsetDateTime.now());
        assertThrows(BusinessException.class,()->service.create(request));
        verify(repo,never()).insertIncident(any(),anyLong());
        when(repo.assignedCareTask(10,2,day)).thenReturn(true);when(repo.insertIncident(request,2)).thenReturn(99L);
        when(repo.incident(99)).thenReturn(Optional.of(new IncidentApi.Incident(99,"INC99",20,2,null,null,null,10L,"OTHER","LOW","SIMULATED",OffsetDateTime.now(),OffsetDateTime.now(),"REPORTED")));
        assertEquals(99,service.create(request).id());
        var wrongHorse=new IncidentApi.CreateRequest(21L,null,null,10L,"OTHER","LOW","SIMULATED",OffsetDateTime.now());
        assertThrows(BusinessException.class,()->service.create(wrongHorse));
        when(actors.currentActor()).thenReturn(Optional.of(new ActorContext(3,List.of(RoleCode.GROOM))));
        assertThrows(BusinessException.class,()->service.create(request));
    }
}
