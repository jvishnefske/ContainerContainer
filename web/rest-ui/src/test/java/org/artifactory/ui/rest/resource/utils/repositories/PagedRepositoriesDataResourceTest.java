package org.artifactory.ui.rest.resource.utils.repositories;

import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.ui.rest.service.utils.UtilsServiceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import jakarta.ws.rs.core.Response;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PagedRepositoriesDataResourceTest {

    @Mock
    private UtilsServiceFactory utilsFactory;
    @Mock
    private SecurityService securityService;

    @Spy
    private PagedRepositoriesDataResource resource;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        resource.setUtilsFactory(utilsFactory);
        resource.setSecurityService(securityService);
        doReturn(mock(Response.class)).when(resource).runService(any());
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testSimpleUser() {
        setAuthorisationService(false, false);

        assertThrows(AuthorizationRestException.class, () -> {
            resource.getPagedRepoData();
        });
    }

    @ParameterizedTest
    @MethodSource("userBooleanProvider")
    void testAdminAndUserWithManageResourcesPermission(boolean isAdmin, boolean isResourceManager) {
        setAuthorisationService(isAdmin, isResourceManager);

        resource.getPagedRepoData();

        verify(resource, times(1)).runService(any());
    }

    private void setAuthorisationService(boolean isAdmin, boolean isResourceManager) {
        when(securityService.isAdmin()).thenReturn(isAdmin);
        when(securityService.isResourceManager()).thenReturn(isResourceManager);
    }

    private static Stream<Arguments> userBooleanProvider() {
        return Stream.of(
                Arguments.of(true, false),
                Arguments.of(false, true),
                Arguments.of(true, true)
        );
    }
}
