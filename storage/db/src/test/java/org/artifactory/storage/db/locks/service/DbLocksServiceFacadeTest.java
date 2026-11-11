package org.artifactory.storage.db.locks.service;

import jakarta.annotation.Nullable;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

class DbLocksServiceFacadeTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private JFrogTableBasedDbLocksService tableBasedDbLocksService;
    @Mock
    private NativeDbLocksService nativeDbLocksService;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private ArtifactoryServersCommonService artifactoryServersCommonService;
    private DbLocksServiceFacade dbLocksServiceFacade;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        when(artifactoryContext.beanForType(NativeDbLocksService.class)).thenReturn(nativeDbLocksService);
        when(artifactoryContext.beanForType(ArtifactoryServersCommonService.class)).thenReturn(
                artifactoryServersCommonService);

        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testConstructorTableBaseSelectedByDefault() {
        dbLocksServiceFacade = new DbLocksServiceFacade(tableBasedDbLocksService);

        DbLocksService dbLocksService = extractDbLocksServiceField();
        assertNotNull(dbLocksService);
        assertEquals(AutoDbLocksService.class, dbLocksService.getClass());
    }

    @Test
    void testConstructorNativeSelected() {
        setStringSystemProperty(ConstantValues.dbLockingMechanism, "native");

        dbLocksServiceFacade = new DbLocksServiceFacade(tableBasedDbLocksService);

        DbLocksService dbLocksService = extractDbLocksServiceField();
        assertNotNull(dbLocksService);
        assertEquals(nativeDbLocksService, dbLocksService);
    }

    @Test
    void testConstructorAutoSelected() {
        setStringSystemProperty(ConstantValues.dbLockingMechanism, "auto");

        dbLocksServiceFacade = new DbLocksServiceFacade(tableBasedDbLocksService);

        DbLocksService dbLocksService = extractDbLocksServiceField();
        assertNotNull(dbLocksService);
        assertEquals(AutoDbLocksService.class, dbLocksService.getClass());
    }

    @Test
    void testConstructorAutoThrowsExceptionTableBasedSelectedByFallback() {
        setStringSystemProperty(ConstantValues.dbLockingMechanism, "auto");
        when(artifactoryContext.beanForType(ArtifactoryServersCommonService.class)).thenThrow(new RuntimeException());

        dbLocksServiceFacade = new DbLocksServiceFacade(tableBasedDbLocksService);

        DbLocksService dbLocksService = extractDbLocksServiceField();
        assertNotNull(dbLocksService);
        assertEquals(tableBasedDbLocksService, dbLocksService);
    }

    @Test
    void testConstructorNoNativeImplementationTableBasedSelectedByFallback() {
        setStringSystemProperty(ConstantValues.dbLockingMechanism, "native");
        when(artifactoryContext.beanForType(NativeDbLocksService.class)).thenThrow(new RuntimeException());

        dbLocksServiceFacade = new DbLocksServiceFacade(tableBasedDbLocksService);

        DbLocksService dbLocksService = extractDbLocksServiceField();
        assertNotNull(dbLocksService);
        assertEquals(tableBasedDbLocksService, dbLocksService);
    }

    @Nullable
    private DbLocksService extractDbLocksServiceField() {
        return (DbLocksService) ReflectionTestUtils.getField(dbLocksServiceFacade, "dbLocksService");
    }
}