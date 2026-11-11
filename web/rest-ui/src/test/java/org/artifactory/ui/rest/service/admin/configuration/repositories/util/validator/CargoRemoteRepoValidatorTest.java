package org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator;

import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertSame;
/**
 * @author arkojd
 */
class CargoRemoteRepoValidatorTest {

    private CargoRemoteRepoValidator cargoRemoteRepoValidator;

    @BeforeEach
    void setUp() {
        cargoRemoteRepoValidator = new CargoRemoteRepoValidator();
    }

    @Test
    void validateToThrowException() {
        RemoteRepoConfig remoteRepoConfig = Mockito.mock(RemoteRepoConfig.class);
        Mockito.when(remoteRepoConfig.getPackageType()).thenReturn(RepoType.Cargo);
        MutableCentralConfigDescriptor descriptor = Mockito.mock(MutableCentralConfigDescriptor.class);
        Mockito.when(descriptor.getUrlBase()).thenReturn("");
        assertThrowsExactly(RepoConfigException.class,
                () -> cargoRemoteRepoValidator.validate(remoteRepoConfig, descriptor, null));
    }

    @Test
    void validateToNotThrowException() {
        RemoteRepoConfig remoteRepoConfig = Mockito.mock(RemoteRepoConfig.class);
        Mockito.when(remoteRepoConfig.getPackageType()).thenReturn(RepoType.Cargo);
        MutableCentralConfigDescriptor descriptor = Mockito.mock(MutableCentralConfigDescriptor.class);
        Mockito.when(descriptor.getUrlBase()).thenReturn("dummy");
        assertDoesNotThrow(() -> cargoRemoteRepoValidator.validate(remoteRepoConfig, descriptor, null));
    }

    @Test
    void validateToNotThrowExceptionForOtherRepoType() {
        RemoteRepoConfig remoteRepoConfig = Mockito.mock(RemoteRepoConfig.class);
        Mockito.when(remoteRepoConfig.getPackageType()).thenReturn(RepoType.Generic);
        MutableCentralConfigDescriptor descriptor = Mockito.mock(MutableCentralConfigDescriptor.class);
        Mockito.when(descriptor.getUrlBase()).thenReturn("");
        assertDoesNotThrow(() -> cargoRemoteRepoValidator.validate(remoteRepoConfig, descriptor, null));
    }

    @Test
    void getRepoType() {
        assertSame(cargoRemoteRepoValidator.getRepoType(), RepoType.Cargo);
    }
}