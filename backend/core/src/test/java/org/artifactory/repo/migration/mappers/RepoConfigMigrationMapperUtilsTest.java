package org.artifactory.repo.migration.mappers;

import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.artifactory.util.PackageTypeConfigFields.ENCRYPT_STATES;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class RepoConfigMigrationMapperUtilsTest {

    @ParameterizedTest
    @ValueSource(booleans = {true,false})
    void testBuildLocalPackageTypeConfig(boolean isEncrypt)
    {
        LocalRepoDescriptor localRepoDescriptor = spy(LocalRepoDescriptor.class);
        localRepoDescriptor.setType(RepoType.TerraformBackend);
        when(localRepoDescriptor.isEncryptStates()).thenReturn(isEncrypt);
        Map<String,String> res = RepoConfigMigrationMapperUtils.buildLocalPackageTypeConfig(localRepoDescriptor);
        Assertions.assertEquals(String.valueOf(isEncrypt),res.get(ENCRYPT_STATES.getKey()));
    }

}