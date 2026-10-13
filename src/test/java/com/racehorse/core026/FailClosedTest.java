package com.racehorse.core026;

import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.core026.Contracts.*;
import com.racehorse.core026.IntegrationPorts.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class FailClosedTest {
    @Test
    void missingRealAuthenticationAdapterFailsClosed() {
        var beans = new DefaultListableBeanFactory();
        var access = new AccessPolicy(beans.getBeanProvider(ActorResolver.class),
                beans.getBeanProvider(GroomWorkScope.class));
        assertEquals(ErrorCode.UNAUTHORIZED, assertThrows(BusinessException.class, access::actor).code());
    }

    @Test
    void missingGroomAssignmentAdapterFailsClosed() {
        var beans = new DefaultListableBeanFactory();
        var access = new AccessPolicy(beans.getBeanProvider(ActorResolver.class),
                beans.getBeanProvider(GroomWorkScope.class));
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(BusinessException.class, () -> access.groom(new Actor(1, Set.of(Role.GROOM)))).code());
    }
}
