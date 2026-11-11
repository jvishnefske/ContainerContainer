package org.artifactory.repo.migration.mappers;

import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.artifactory.descriptor.repo.ChecksumPolicyTypeDescriptor;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoChecksumPolicyTypeDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.SnapshotVersionBehaviorDescriptor;
import org.artifactory.descriptor.repo.TrashRepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.descriptor.repo.releasebundle.ReleaseBundlesRepoDescriptor;
import org.artifactory.repo.converter.RepositoriesConfigSubConverter;
import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.ResourceUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.repo.converter.RepositoryConfigBaseConverter.REPO_TYPE_CONFIG_KEY;
import static org.mockito.Mockito.mock;

public class RepoConfigMapperHolderTest extends ArtifactoryHomeBoundJUnitTest {

    @AfterEach
    void tearDown() {
        unbindArtifactoryHome();
    }

    @Test
    public void testMapWithHex() {
        HttpRepoDescriptor remoteRepoDescriptor = new HttpRepoDescriptor();
        remoteRepoDescriptor.setType(RepoType.Hex);
        remoteRepoDescriptor.setKey("remote1");
        RepoConfigMapperHolder repoConfigMapperHolder = new RepoConfigMapperHolder(Set.of());
        List<DbRepositoryConfig> dbRepos =
                repoConfigMapperHolder.map(RemoteRepoDescriptor.class, Map.of("repoKey", remoteRepoDescriptor));
        Assertions.assertEquals(1, dbRepos.size());
    }

    @ParameterizedTest
    @MethodSource("mappersRegistrationDataProvider")
    public void testMapWithRegisteredClass(Class<RepoDescriptor> repoDescriptorClass, RepoDescriptor repoDescriptor) {
        RepoConfigMapperHolder repoConfigMapperHolder = new RepoConfigMapperHolder(Set.of());
        List<DbRepositoryConfig> dbRepos =
                repoConfigMapperHolder.map(repoDescriptorClass, Map.of("repoKey", repoDescriptor));
        Assertions.assertEquals(1, dbRepos.size());
    }

    @Test
    public void testMapWithoutRegisteredClass() {
        RepoConfigMapperHolder repoConfigMapperHolder = new RepoConfigMapperHolder(Set.of());
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            repoConfigMapperHolder.map(TrashRepoDescriptor.class, Map.of("trash", mock(TrashRepoDescriptor.class)));
        }, "Repository class .* does not have mapper");
    }

    @Test
    public void testConvertMapOfFieldsToDbEntityWithoutSubConverters() throws IOException {
        LocalRepoDescriptor localRepoDescriptor = createLocalRepoDescriptor();
        RepoConfigMapperHolder repoConfigMapperHolder = new RepoConfigMapperHolder(Set.of());
        List<DbRepositoryConfig> dbRepositoryConfigs = repoConfigMapperHolder.map(LocalRepoDescriptor.class,
                Map.of(localRepoDescriptor.getKey(), localRepoDescriptor));
        Assertions.assertEquals(1, dbRepositoryConfigs.size());

        DbRepositoryConfig dbRepositoryConfig = dbRepositoryConfigs.get(0);
        String infoBlobAsString = new String(dbRepositoryConfig.getRepositoryInfoBlob());
        String expectedInfoBlobAsString = IOUtils.toString(
                ResourceUtils.getResource("/org/artifactory/repo/migration/local-repo-after-migration.json"),
                StandardCharsets.UTF_8);

        Assertions.assertEquals(JsonParser.parseString(expectedInfoBlobAsString),
                JsonParser.parseString(infoBlobAsString));
    }

    @Test
    public void testConvertMapOfFieldsToDbEntityWithSubConverters() throws IOException {
        LocalRepoDescriptor localRepoDescriptor = createLocalRepoDescriptor();
        localRepoDescriptor.setXrayNotAvailableMode(XrayNotAvailableMode.FAIL_OPEN);
        RepoConfigMapperHolder repoConfigMapperHolder =
                new RepoConfigMapperHolder(Set.of(new TestRepoTypeConfigRepositoriesConfigSubConverter()));
        List<DbRepositoryConfig> dbRepositoryConfigs = repoConfigMapperHolder.map(LocalRepoDescriptor.class,
                Map.of(localRepoDescriptor.getKey(), localRepoDescriptor));
        Assertions.assertEquals(1, dbRepositoryConfigs.size());

        DbRepositoryConfig dbRepositoryConfig = dbRepositoryConfigs.get(0);
        String infoBlobAsString = new String(dbRepositoryConfig.getRepositoryInfoBlob());
        String expectedInfoBlobAsString = IOUtils.toString(
                ResourceUtils.getResource("/org/artifactory/repo/migration/local-repo-after-converter.json"),
                StandardCharsets.UTF_8);

        Assertions.assertEquals(JsonParser.parseString(expectedInfoBlobAsString),
                JsonParser.parseString(infoBlobAsString));
    }

    private static Stream<Object[]> mappersRegistrationDataProvider() {
        LocalRepoDescriptor localRepoDescriptor = createLocalRepoDescriptor();

        VirtualRepoDescriptor virtualRepoDescriptor = new VirtualRepoDescriptor();
        virtualRepoDescriptor.setType(RepoType.NuGet);
        virtualRepoDescriptor.setKey("virtual11");

        HttpRepoDescriptor remoteRepoDescriptor = new HttpRepoDescriptor();
        remoteRepoDescriptor.setType(RepoType.Debian);
        remoteRepoDescriptor.setChecksumPolicyType(ChecksumPolicyTypeDescriptor.PASS_THRU);
        remoteRepoDescriptor.setKey("remote1");

        FederatedRepoDescriptor fedRepoDescriptor = new FederatedRepoDescriptor();
        fedRepoDescriptor.setType(RepoType.Vagrant);
        fedRepoDescriptor.setSnapshotVersionBehavior(SnapshotVersionBehaviorDescriptor.UNIQUE);
        fedRepoDescriptor.setChecksumPolicyType(LocalRepoChecksumPolicyTypeDescriptor.CLIENT);
        fedRepoDescriptor.setKey("fed1");

        ReleaseBundlesRepoDescriptor releaseBundlesRepoDescriptor = new ReleaseBundlesRepoDescriptor();
        releaseBundlesRepoDescriptor.setType(RepoType.SBT);
        releaseBundlesRepoDescriptor.setSnapshotVersionBehavior(SnapshotVersionBehaviorDescriptor.UNIQUE);
        releaseBundlesRepoDescriptor.setChecksumPolicyType(LocalRepoChecksumPolicyTypeDescriptor.CLIENT);
        releaseBundlesRepoDescriptor.setKey("bundle1");

        return Stream.of(
                new Object[] {LocalRepoDescriptor.class, localRepoDescriptor},
                new Object[] {RemoteRepoDescriptor.class, remoteRepoDescriptor},
                new Object[] {VirtualRepoDescriptor.class, virtualRepoDescriptor},
                new Object[] {FederatedRepoDescriptor.class, fedRepoDescriptor},
                new Object[] {ReleaseBundlesRepoDescriptor.class, releaseBundlesRepoDescriptor}
        );
    }

    private static LocalRepoDescriptor createLocalRepoDescriptor() {
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        localRepoDescriptor.setSnapshotVersionBehavior(SnapshotVersionBehaviorDescriptor.UNIQUE);
        localRepoDescriptor.setChecksumPolicyType(LocalRepoChecksumPolicyTypeDescriptor.CLIENT);
        localRepoDescriptor.setType(RepoType.Docker);
        localRepoDescriptor.setKey("local1");
        return localRepoDescriptor;
    }

    private class TestRepoTypeConfigRepositoriesConfigSubConverter implements RepositoriesConfigSubConverter {

        @Override
        public boolean isInterested(String key) {
            return key.equals("repoTypeConfig");
        }

        @Override
        public void convert(Map<String, Object> toConvert) {
            Map<String, Object> repoTypeConfig = (Map<String, Object>) toConvert.get(REPO_TYPE_CONFIG_KEY);
            repoTypeConfig.put("snapshotVersionBehavior", "TEST_VALUE_THAT_CONVERTER_UPDATED");
        }
    }
}