package org.artifactory.rest.common.aop;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.entitlement.Entitlement;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import jakarta.ws.rs.core.Response;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntitlementAdviceTest {

    private EntitlementAdvice entitlementAdvice;

    @Mock
    private ArtifactoryApplicationContext applicationContext;
    @Mock
    private MethodInvocation methodInvocation;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private Response response;

    private AutoCloseable mocks;

    @BeforeEach
    public void onStart() throws NoSuchMethodException {
        mocks = MockitoAnnotations.openMocks(this);
        when(applicationContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        entitlementAdvice = new EntitlementAdvice();
        ArtifactoryContextThreadBinder.bind(applicationContext);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testAdviceInvocationWhenEntitled() throws Throwable {
        when(methodInvocation.getThis()).thenReturn(new DummyEntitledClass());
        when(addonsManager.isEntitled(EntitlementType.LDAP))
                .thenReturn(true);
        when(methodInvocation.proceed()).thenReturn(response);
        entitlementAdvice.invoke(methodInvocation);
        verify(methodInvocation).proceed();
    }

    @Test
    void testAdviceInvocationWhenNotEntitled() throws Throwable {
        when(methodInvocation.getThis()).thenReturn(new DummyEntitledClass());
        when(addonsManager.isEntitled(EntitlementType.LDAP))
                .thenReturn(false);
        when(methodInvocation.proceed()).thenReturn(response);
        Object actual = entitlementAdvice.invoke(methodInvocation);
        verify(methodInvocation, never()).proceed();
        Assertions.assertEquals(HttpStatus.SC_FORBIDDEN, ((Response) actual).getStatus());
    }

    @AfterEach
    public void cleanUp() {
        Mockito.reset(applicationContext);
        ArtifactoryContextThreadBinder.unbind();
    }

    @Entitlement(EntitlementType.LDAP)
    private static final class DummyEntitledClass {
    }
}
