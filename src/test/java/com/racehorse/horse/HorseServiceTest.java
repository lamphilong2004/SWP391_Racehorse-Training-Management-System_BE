package com.racehorse.horse;

import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.common.error.BusinessException;
import com.racehorse.horse.activation.HorseActivationService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HorseServiceTest {
    private final HorseRepository repository=mock(HorseRepository.class);
    private final HorseService service=new HorseService(
            repository, mock(CurrentActorProvider.class), mock(HorseActivationService.class));
    private final HorseIdentityRequest identity=new HorseIdentityRequest("TEST-01","Test Horse",null,null,null,null,null);

    @Test void wrapperSqlColumnListDoesNotMisidentifyDuplicateHorseCode(){
        when(repository.insert(any())).thenThrow(new DuplicateKeyException(
            "INSERT horse(horse_code,registration_no) VALUES (?,?)",
            new SQLException("Violation of UNIQUE constraint 'uq_horse__horse_code'.","23000",2627)));
        BusinessException result=assertThrows(BusinessException.class,()->service.create(identity));
        assertEquals("horseCode",result.details().getFirst().field());
        assertFalse(result.getMessage().contains("INSERT"));
    }
    @Test void duplicateRegistrationIdentifiesCanonicalIndex(){
        when(repository.insert(any())).thenThrow(new DuplicateKeyException(
            "INSERT horse(horse_code,registration_no) VALUES (?,?)",
            new SQLException("Cannot insert duplicate key with unique index 'ux_horse__registration_no'.","23000",2601)));
        BusinessException result=assertThrows(BusinessException.class,()->service.create(identity));
        assertEquals("registrationNo",result.details().getFirst().field());
    }
    @Test void unrelatedIntegrityFailureDoesNotMasqueradeAsDuplicateIdentity(){
        DataIntegrityViolationException original=new DataIntegrityViolationException("Constraint failure",new SQLException("CHECK failed","23000",547));
        when(repository.insert(any())).thenThrow(original);
        assertSame(original,assertThrows(DataIntegrityViolationException.class,()->service.create(identity)));
    }
}
