package org.artifactory.repo.converter.v1;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerraformEnableBypassHeaderConverterTest {

    @ParameterizedTest
    @MethodSource("repoTypeMatrix")
    void testConvert(RepoType packageType, boolean expected) {
        TerraformEnableBypassHeaderConverter converter = new TerraformEnableBypassHeaderConverter();
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote-repo", packageType);
        Map<String, Object> repositoryConfigMap = converter.deserializeMap(remoteRepoConfig);
        converter.convert(repositoryConfigMap);
        Map<String, Object> repoTypeConfig = converter.deserializeMap(
                repositoryConfigMap.get(TerraformEnableBypassHeaderConverter.REPO_TYPE_CONFIG_KEY));
        boolean bypassHeadRequests =
                (boolean) repoTypeConfig.get(TerraformEnableBypassHeaderConverter.BYPASS_HEAD_REQUESTS_KEY);
        assertEquals(expected, bypassHeadRequests);
    }

    static Stream<Arguments> repoTypeMatrix() {
        return Stream.of(Arguments.of(RepoType.Terraform, true), Arguments.of(RepoType.Alpine, false),
                Arguments.of(RepoType.Bower, false));
    }
}