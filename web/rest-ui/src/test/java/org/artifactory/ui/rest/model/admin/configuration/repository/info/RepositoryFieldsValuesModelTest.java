package org.artifactory.ui.rest.model.admin.configuration.repository.info;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.webstart.ArtifactWebstartAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.artifactory.descriptor.repo.KeyPairDescriptor.ECDSA_TYPE;
import static org.artifactory.descriptor.repo.KeyPairDescriptor.ED25519_TYPE;
import static org.artifactory.descriptor.repo.KeyPairDescriptor.GPG_TYPE;
import static org.artifactory.descriptor.repo.KeyPairDescriptor.RSA_TYPE;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Author: Ivan Perepelytsya
 * Date: 6/3/20.
 */
public class RepositoryFieldsValuesModelTest {

    private AutoCloseable closeable;

    @Mock
    private ArtifactoryApplicationContext artifactoryContext;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private ArtifactWebstartAddon artifactWebstartAddon;
    @Mock
    private CentralConfigDescriptor descriptor;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private ArtifactoryHome artifactoryHome;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;
    @Mock
    private AolService aolService;
    @Mock
    private ArtifactoryBinaryService binaryService;

    @BeforeEach
    void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);

        ArtifactoryHome.bind(artifactoryHome);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);

        when(addonsManager.addonByType(ArtifactWebstartAddon.class)).thenReturn(artifactWebstartAddon);
        when(artifactWebstartAddon.getKeyPairNames()).thenReturn(List.of());
        when(ContextHelper.get().beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(ContextHelper.get().beanForType(CentralConfigService.class)).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(descriptor);
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);
        when(aolService.skipDockerDns()).thenReturn(false);
    }

    @AfterEach
    void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void testCdn() {
        when(binaryService.isCdnAllowed()).thenReturn(true);
        when(descriptor.getProxies()).thenReturn(List.of());
        final RepositoryFieldsValuesModel model =
                new RepositoryFieldsValuesModel(descriptor, binaryService, List.of("mvn", "npm", "nuget"),
                        List.of("Docker", "mvn"));
        final List<String> allowedRedirects = model.getCdnRedirectAllowedRepos();

        assertEquals(allowedRedirects.size(), 3);
        assertEquals(allowedRedirects.get(0), "mvn");
        assertEquals(allowedRedirects.get(1), "npm");
        assertEquals(allowedRedirects.get(2), "nuget");

        final List<String> allowedCdnRedirects = model.getForcedCdnRedirectRepos();
        assertEquals(allowedCdnRedirects.size(), 2);
        assertEquals(allowedCdnRedirects.get(0), "Docker");
        assertEquals(allowedCdnRedirects.get(1), "mvn");

        assertTrue(model.isCdnAllowedPerRepo());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testDockerV1Enabled(boolean skipDns) {
        when(descriptor.getProxies()).thenReturn(List.of());
        when(aolService.skipDockerDns()).thenReturn(skipDns);

        final RepositoryFieldsValuesModel model =
                new RepositoryFieldsValuesModel(descriptor, binaryService, List.of(), List.of());
        assertEquals(!skipDns, model.isDockerV1Enabled());
    }

    @Test
    void testKeyPairsFilteredByType() {
        when(descriptor.getProxies()).thenReturn(List.of());
        when(descriptor.getKeyPairs()).thenReturn(List.of(
                keyPair("rsa1", RSA_TYPE),
                keyPair("gpg1", GPG_TYPE),
                keyPair("ecdsa1", ECDSA_TYPE),
                keyPair("ed1", ED25519_TYPE),
                keyPair("ed2", ED25519_TYPE)
        ));

        final RepositoryFieldsValuesModel model =
                new RepositoryFieldsValuesModel(descriptor, binaryService, List.of(), List.of());

        assertEquals(model.getKeyPairs(), List.of("rsa1"));
        assertEquals(model.getGpgKeyPairs(), List.of("gpg1"));
        assertEquals(model.getEcdsaKeyPairs(), List.of("ecdsa1"));
        assertEquals(model.getEd25519KeyPairs(), List.of("ed1", "ed2"));
    }

    @Test
    void testKeyPairsAllListsEmptyWhenNoKeyPairsConfigured() {
        when(descriptor.getProxies()).thenReturn(List.of());
        when(descriptor.getKeyPairs()).thenReturn(List.of());

        final RepositoryFieldsValuesModel model =
                new RepositoryFieldsValuesModel(descriptor, binaryService, List.of(), List.of());

        assertTrue(model.getKeyPairs().isEmpty());
        assertTrue(model.getGpgKeyPairs().isEmpty());
        assertTrue(model.getEcdsaKeyPairs().isEmpty());
        assertTrue(model.getEd25519KeyPairs().isEmpty());
    }

    private static KeyPairDescriptor keyPair(String name, String type) {
        KeyPairDescriptor kp = new KeyPairDescriptor();
        kp.setKey(name);
        kp.setKeyType(type);
        return kp;
    }
}