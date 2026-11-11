package org.artifactory.ui.rest.service.admin.security.crowdsso;

import org.artifactory.ui.rest.model.admin.security.crowdsso.CrowdIntegration;
import org.jfrog.access.client.model.CrowdSetting;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AccessCrowdMapperTest {

    AccessCrowdMapper mapper = Mappers.getMapper(AccessCrowdMapper.class);

    @Test
    void mapToAccessModel() {
        CrowdIntegration crowdIntegration = CrowdIntegration.builder()
                .enableIntegration(true)
                .applicationName("test")
                .noAutoUserCreation(true)
                .serverUrl("http://localhost:8095/crowd")
                .password("password")
                .useDefaultProxy(true)
                .customCookieTokenKey("custom")
                .directAuthentication(true)
                .overrideAllGroupsUponLogin(true)
                .allowUserToAccessProfile(true)
                .sessionValidationInterval(10)
                .build();
        CrowdSetting expected = CrowdSetting
                .builder()
                .enable(true)
                .applicationName("test")
                .autoUserCreation(true)
                .serverUrl("http://localhost:8095/crowd")
                .password("password")
                .useDefaultProxy(true)
                .customCookieTokenKey("custom")
                .directAuthentication(true)
                .overrideAllGroupsUponLogin(true)
                .allowUserToAccessProfile(true)
                .sessionValidationInterval(10)
                .build();

        CrowdSetting crowdSetting = mapper.map(crowdIntegration);
        assertEquals(expected, crowdSetting);
    }


    @Test
    void mapToRestModel() {
        CrowdIntegration expected = CrowdIntegration.builder()
                .enableIntegration(true)
                .applicationName("test")
                .noAutoUserCreation(true)
                .serverUrl("http://localhost:8095/crowd")
                .password("password")
                .useDefaultProxy(true)
                .customCookieTokenKey("custom")
                .directAuthentication(true)
                .overrideAllGroupsUponLogin(true)
                .allowUserToAccessProfile(true)
                .sessionValidationInterval(10)
                .build();
        CrowdSetting crowdSetting = CrowdSetting
                .builder()
                .enable(true)
                .applicationName("test")
                .autoUserCreation(true)
                .serverUrl("http://localhost:8095/crowd")
                .password("password")
                .useDefaultProxy(true)
                .customCookieTokenKey("custom")
                .directAuthentication(true)
                .overrideAllGroupsUponLogin(true)
                .allowUserToAccessProfile(true)
                .sessionValidationInterval(10)
                .build();

        CrowdIntegration crowdIntegration = mapper.map(crowdSetting);
        assertEquals(expected, crowdIntegration);
    }

    @Test
    void mapEmpty() {
        CrowdIntegration crowdIntegration = mapper.map(CrowdSetting.builder().build());
        assertEquals(CrowdIntegration.builder().build(), crowdIntegration);
    }
}
