package org.artifactory.ui.rest.service.admin.configuration.repositories;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.service.validation.RepoKeyLengthLimitsHelper;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Oleksandr Legenkiy
 */
class RepoKeyLengthLimitsServiceTest {

    private RepoKeyLengthLimitsService service;
    private RepoKeyLengthLimitsHelper helperMock;

    @BeforeEach
    void setUp() {
        helperMock = mock(RepoKeyLengthLimitsHelper.class);
        service = new RepoKeyLengthLimitsService(helperMock);
    }

    @ParameterizedTest
    @MethodSource("provideTestExecuteMethodArguments")
    void testExecute(String repoType, String repoPackageType, int hardLimitMock, Integer softLimitMock,
            boolean expectModelToBeSet, int expRespCode, String expErrorMsg) {
        var requestMock = mock(ArtifactoryRestRequest.class);
        when(requestMock.getQueryParamByKey("repoType")).thenReturn(repoType);
        when(requestMock.getQueryParamByKey("repoPackageType")).thenReturn(repoPackageType);
        var repoTypeEnum = RepoDetailsType.fromString(repoType);
        var repoPackageTypeEnum = RepoType.fromString(repoPackageType);
        if (repoTypeEnum != null && repoPackageTypeEnum != null) {
            when(helperMock.hardLimit(repoTypeEnum, repoPackageTypeEnum)).thenReturn(hardLimitMock);
            when(helperMock.softLimit(repoPackageTypeEnum)).thenReturn(softLimitMock);
        }
        var responseMock = mock(ArtifactoryRestResponse.class);
        doReturn(responseMock).when(responseMock).error(anyString());
        doReturn(responseMock).when(responseMock).responseCode(anyInt());
        service.execute(requestMock, responseMock);
        if (expRespCode != 200) {
            verify(responseMock).responseCode(expRespCode);
            verify(responseMock).error(expErrorMsg);
        }
        if (expectModelToBeSet) {
            verify(responseMock).iModel(new RepoKeyLengthLimits(hardLimitMock, softLimitMock));
        }
    }

    private static Stream<Arguments> provideTestExecuteMethodArguments() {
        return Stream.of(
                Arguments.of("local", "docker", 1, 2, true, 200, null),
                Arguments.of("remote", "helmOci", 3, 4, true, 200, null),
                Arguments.of("virtual", "docker", 5, 6, true, 200, null),
                Arguments.of("federated", "docker", 7, 8, true, 200, null),
                Arguments.of("distribution", "docker", 9, 10, true, 200, null),
                Arguments.of("", "", 1, 2, false, 400, "Query params 'repoType' and 'repoPackageType' are required"),
                Arguments.of("local", "", 1, 2, false, 400,
                        "Query params 'repoType' and 'repoPackageType' are required"),
                Arguments.of("", "docker", 1, 2, false, 400,
                        "Query params 'repoType' and 'repoPackageType' are required"),
                Arguments.of("remote1", "docker", 1, 2, false, 400, "Invalid 'repoType' param value"),
                Arguments.of("remote", "docker1", 1, 2, false, 400, "Invalid 'repoPackageType' param value")
        );
    }
}