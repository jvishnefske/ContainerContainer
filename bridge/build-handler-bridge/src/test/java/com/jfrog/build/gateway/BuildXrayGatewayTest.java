package com.jfrog.build.gateway;

import com.jfrog.build.acl.model.BuildXrayEvent;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildXrayGatewayTest {

    private BuildXrayGateway buildXrayGateway;

    private XrayAddon xrayAddon;

    @BeforeEach
    void setUp() {
        xrayAddon = mock(XrayAddon.class);
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(XrayAddon.class)).thenReturn(xrayAddon);
        buildXrayGateway = new BuildXrayGateway(addonsManager);
    }

    @Test
    void runXrayInterceptor_createBuild_forwardsBuildStartedToAddon() {
        Long buildStarted = 1700000000000L;
        buildXrayGateway.runXrayInterceptor(
                BuildXrayEvent.CREATE_BUILD, "some-build-name", "1", buildStarted, "artifactory-build-info");
        verify(xrayAddon).callAddBuildInterceptor("some-build-name", "1", buildStarted, "artifactory-build-info");
    }

    @Test
    void runXrayInterceptor_deleteBuild_forwardsBuildStartedToAddon() {
        Long buildStarted = 42L;
        buildXrayGateway.runXrayInterceptor(
                BuildXrayEvent.DELETE_BUILD, "some-build-name", "2", buildStarted, "phoenix-build-info");
        verify(xrayAddon).callDeleteBuildInterceptor("some-build-name", "2", buildStarted, "phoenix-build-info");
    }
}
