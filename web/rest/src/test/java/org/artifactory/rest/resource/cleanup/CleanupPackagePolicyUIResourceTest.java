package org.artifactory.rest.resource.cleanup;

import org.artifactory.rest.common.service.RestService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.resource.cleanup.dto.PackageCleanupPolicyDto;
import org.artifactory.rest.services.cleanup.CleanupPackagePolicyServiceFactory;
import org.artifactory.rest.services.cleanup.CreateCleanupPackagePolicyService;
import org.artifactory.rest.services.cleanup.DeleteCleanupPackagePolicyService;
import org.artifactory.rest.services.cleanup.GetAllCleanupPackagePolicyService;
import org.artifactory.rest.services.cleanup.GetCleanupPackagePolicyService;
import org.artifactory.rest.services.cleanup.UpdateCleanupPackagePolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Oleksandr Legenkiy
 */
class CleanupPackagePolicyUIResourceTest {

    private CleanupPackageUIResourceTestSpy resourceSpy;
    private CleanupPackagePolicyServiceFactory factoryMock;

    @BeforeEach
    void setup() {
        factoryMock = mock(CleanupPackagePolicyServiceFactory.class);
        resourceSpy = spy(new CleanupPackageUIResourceTestSpy(factoryMock));
        doReturn(null).when(resourceSpy).runService(any());
        doReturn(null).when(resourceSpy).runService(any(), any());
    }

    @Test
    void testGetAllPackagePolicies() {
        var service = mock(GetAllCleanupPackagePolicyService.class);
        when(factoryMock.getAllCleanupPackagePolicyService()).thenReturn(service);
        resourceSpy.getPackageCleanupPolicies();
        verify(resourceSpy).runService(service);
    }

    @Test
    void testGetPackagePolicy() {
        var service = mock(GetCleanupPackagePolicyService.class);
        when(factoryMock.getCleanupPackagePolicyService()).thenReturn(service);
        resourceSpy.getPackageCleanupPolicy("key");
        verify(resourceSpy).runService(service, "key");
    }

    @Test
    void testCreatePackagePolicy() {
        var service = mock(CreateCleanupPackagePolicyService.class);
        when(factoryMock.createCleanupPackagePolicyService()).thenReturn(service);
        var policy = PackageCleanupPolicyDto.builder().key("key").build();
        resourceSpy.createPackageCleanupPolicy("key", policy);
        verify(resourceSpy).runService(service, policy);
    }

    @Test
    void testCreatePackagePolicyValidatesKey() {
        var policy = PackageCleanupPolicyDto.builder().key("key").build();
        BadRequestException badRequestException =
                assertThrows(BadRequestException.class, () -> resourceSpy.createPackageCleanupPolicy("key1", policy));
        assertEquals("Incorrect policy key value", badRequestException.getMessage());
    }

    @Test
    void testUpdatePackagePolicy() {
        var service = mock(UpdateCleanupPackagePolicyService.class);
        when(factoryMock.updateCleanupPackagePolicyService()).thenReturn(service);
        var policy = PackageCleanupPolicyDto.builder().key("key").build();
        resourceSpy.updatePackageCleanupPolicy("key", policy);
        verify(resourceSpy).runService(service, policy);
    }

    @Test
    void testUpdatePackagePolicyValidatesKey() {
        var policy = PackageCleanupPolicyDto.builder().key("key").build();
        BadRequestException badRequestException =
                assertThrows(BadRequestException.class, () -> resourceSpy.updatePackageCleanupPolicy("key1", policy));
        assertEquals("Incorrect policy key value", badRequestException.getMessage());
    }

    @Test
    void testDeletePackagePolicy() {
        var service = mock(DeleteCleanupPackagePolicyService.class);
        when(factoryMock.deleteCleanupPackagePolicyService()).thenReturn(service);
        resourceSpy.deletePackageCleanupPolicy("key");
        verify(resourceSpy).runService(service, "key");
    }

    /**
     * This is needed to make 'runService' methods accessible from the test class and to mock them
     */
    static class CleanupPackageUIResourceTestSpy extends CleanupPackagePolicyUIResource {

        public CleanupPackageUIResourceTestSpy(CleanupPackagePolicyServiceFactory factory) {
            super(factory);
        }

        @Override
        protected <Y> Response runService(RestService<Y> service, Y model) {
            return super.runService(service, model);
        }

        @Override
        protected <Y> Response runService(RestService<Y> service) {
            return super.runService(service);
        }
    }
}