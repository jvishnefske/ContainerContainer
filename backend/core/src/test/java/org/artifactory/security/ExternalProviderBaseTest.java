package org.artifactory.security;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class ExternalProviderBaseTest {

    private ExternalProviderBase externalProviderBase;

    @Mock
    private AddonsManager addonsManager;
    @Mock
    private XrayAddon xrayAddon;

    @BeforeEach
    void setUp() {
        openMocks(this);
        when(addonsManager.addonByType(XrayAddon.class)).thenReturn(xrayAddon);
        externalProviderBase = new ExternalProviderBase(addonsManager) {
        };
    }

    @Test
    void shouldNotAuthenticate() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("anonymous", "password");
        assertTrue(externalProviderBase.shouldNotAuthenticate(authentication));
    }

    @Test
    void shouldAuthenticateNoXray() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("xray", "password");
        assertFalse(externalProviderBase.shouldNotAuthenticate(authentication));
    }

    @Test
    void shouldAuthenticateNonXrayUser() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("nonAnonymous", "password");
        assertFalse(externalProviderBase.shouldNotAuthenticate(authentication));
    }
}
