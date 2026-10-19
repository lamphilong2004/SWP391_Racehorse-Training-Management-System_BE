package com.racehorse.horse;

import com.racehorse.auth.*;
import com.racehorse.common.api.ErrorDetail;
import com.racehorse.common.error.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.SQLException;
import java.util.*;

@Service
public class HorseService {
    private final HorseRepository repository;private final CurrentActorProvider actors;
    public HorseService(HorseRepository repository,CurrentActorProvider actors){this.repository=repository;this.actors=actors;}
    private Long trainerScope() {
        ActorContext actor=actors.currentActor().orElseThrow(()->new BusinessException(ErrorCode.UNAUTHORIZED,"Authentication required."));
        if(actor.roles().stream().anyMatch(r->Set.of(RoleCode.CLUB_MANAGER,RoleCode.HEAD_TRAINER,RoleCode.VETERINARIAN,RoleCode.GROOM).contains(r)))return null;
        return actor.userId();
    }
    @Transactional
    public Map<String,Object> create(HorseIdentityRequest input) {
        long id;
        try{id=repository.insert(input.normalized());}
        catch(DataIntegrityViolationException error){
            Throwable cause=error;boolean duplicate=false;String field="horseCode";
            while(cause!=null){
                if(cause instanceof SQLException sql){
                    if(sql.getErrorCode()==2601||sql.getErrorCode()==2627||"23505".equals(sql.getSQLState()))duplicate=true;
                    if(sql.getMessage()!=null&&sql.getMessage().contains("registration_no"))field="registrationNo";
                }
                cause=cause.getCause();
            }
            if(!duplicate)throw error;
            throw new BusinessException(ErrorCode.CONFLICT,"Horse code or registration number already exists.",List.of(new ErrorDetail(field,"already exists")));
        }
        return detail(id);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> detail(long id) {
        Long trainer=trainerScope();
        Map<String,Object> horse=repository.find(id,trainer).orElseThrow(()->new BusinessException(
            trainer!=null&&repository.exists(id)?ErrorCode.FORBIDDEN:ErrorCode.NOT_FOUND,
            trainer!=null&&repository.exists(id)?"This horse is outside your assigned programs.":"Horse not found."));
        horse.put("currentOwnerships",repository.ownerships(id));return horse;
    }
    @Transactional(readOnly=true)
    public HorseRepository.Page list(String q,String lifecycle,String health,String availability,int page,int size) {
        return repository.list(q,lifecycle,health,availability,page,size,trainerScope());
    }
}
