package org.artifactory.repo.service.validation;

import com.google.common.collect.ImmutableList;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.converter.ConverterManager;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.model.ArtifactoryServerRole;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.version.CompoundVersionDetails;
import org.artifactory.version.VersionProvider;
import org.jfrog.common.ClockUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class HaArtifactoryVersionAlignmentValidatorImplTest {
    @Mock
    private ArtifactoryContext context;
    @Mock
    private ConverterManager converterManager;
    @Mock
    private ArtifactoryServersCommonService serversService;
    @Mock
    private HaArtifactoryVersionAlignmentValidator haArtifactoryVersionAlignmentValidator;
    private AutoCloseable mockitoAutoCloseable;


    @BeforeEach
    void setUp() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.getConverterManager()).thenReturn(converterManager);
        haArtifactoryVersionAlignmentValidator = new HaArtifactoryVersionAlignmentValidatorImpl(serversService);
        ReflectionTestUtils.setField(haArtifactoryVersionAlignmentValidator, "isHaEnabled", true);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mockitoAutoCloseable.close();
    }

    @Test
    void testInTheMiddleOfConversions() {
        when(converterManager.isConverting()).thenReturn(true);
        haArtifactoryVersionAlignmentValidator.validate();
    }

    @Test
    void testDifferentVersions() {
        when(converterManager.isConverting()).thenReturn(false);
        VersionProvider versionProvider = Mockito.mock(VersionProvider.class);
        CompoundVersionDetails currentVersion = Mockito.mock(CompoundVersionDetails.class);

        when(context.getVersionProvider()).thenReturn(versionProvider);
        when(versionProvider.getRunningServiceVersion()).thenReturn(currentVersion);
        when(currentVersion.getVersionName()).thenReturn("7.0.0");

        ArtifactoryServer otherMemberWithDifferentVersion = new ArtifactoryServer("node-2",
                ClockUtils.epochMillis(), "http://node-2.foo.bar", 12345, ArtifactoryServerState.RUNNING,
                ArtifactoryServerRole.MEMBER, ClockUtils.epochMillis(), "10.0.0", 1000900L, 1L,
                ArtifactoryRunningMode.HA, "abcde");

        when(serversService.getOtherRunningHaMembers()).thenReturn(ImmutableList.of(otherMemberWithDifferentVersion));
        assertThrows(ArtifactoryVersionException.class, () -> haArtifactoryVersionAlignmentValidator.validate());
        when(currentVersion.getVersionName()).thenReturn("10.0.0");
        haArtifactoryVersionAlignmentValidator.validate();
    }

}