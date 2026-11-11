package org.artifactory.ui.rest.service.admin.security.signingkeys;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.common.keys.KeyPairService;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.admin.security.signingkey.SignKey;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.jfrog.security.crypto.PlainTextEncryptionWrapper;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import jakarta.ws.rs.core.Response;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * @author Volodymyr Shulga
 */
public class VerifySigningKeyServiceTest {
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private HaCommonAddon haCommonAddon;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private ArtifactoryHome artifactoryHomeMock;
    @Mock
    private KeyPairService keyPairService;
    @Mock
    private KeyPairDescriptor keyPairDescriptor;

    private final EncryptionWrapper encryptionWrapper = new PlainTextEncryptionWrapper();
    private final String PASSPHRASE_NON_ENCRYPTED = "passphrase_non_encrypted";
    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        when(haCommonAddon.getHostId()).thenReturn("4");
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(context.beanForType(KeyPairService.class)).thenReturn(keyPairService);
        ArtifactoryContextThreadBinder.bind(context);
        ArtifactoryHome.bind(artifactoryHomeMock);
        when(artifactoryHomeMock.getArtifactoryEncryptionWrapper()).thenReturn(encryptionWrapper);
        Mockito.reset(keyPairService);
    }

    @AfterMethod
    public void cleanup() throws Exception {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void shouldSucceedForUnencryptedPassphrase() {
        when(keyPairService.getKeyPairByName(any()))
                .thenReturn(keyPairDescriptor);

        when(keyPairDescriptor.getPrivateKey()).thenReturn("key1");
        when(keyPairDescriptor.getPublicKey()).thenReturn("key2");
        when(keyPairService.verifyKeyPairByName(any(), eq(PASSPHRASE_NON_ENCRYPTED))).thenReturn(true);

        final SignKey signKey = new SignKey();
        signKey.setPublicKeyInstalled(true);
        signKey.setPrivateKeyInstalled(true);

        signKey.setPassPhrase(PASSPHRASE_NON_ENCRYPTED);

        final ArtifactoryRestResponse<String> response = new ArtifactoryRestResponse<>();
        VerifySigningKeyService signingKeyService = new VerifySigningKeyService(addonsManager, keyPairService);
        signingKeyService.execute(signKey, response);
        assertEquals(response.getResponseCode(), Response.Status.OK.getStatusCode());
        assertNull(response.getFeedbackMsg().getError());
        assertEquals(response.getFeedbackMsg().getInfo(), "Successfully verified keys and passphrase");
    }

    @Test
    public void shouldFailForNotSetPublicKey() {
        when(keyPairService.getKeyPairByName(any()))
                .thenReturn(keyPairDescriptor);
        when(keyPairDescriptor.getPrivateKey()).thenReturn("key1");
        when(keyPairDescriptor.getPublicKey()).thenReturn(StringUtils.EMPTY);

        final SignKey signKey = new SignKey();
        signKey.setPassPhrase(PASSPHRASE_NON_ENCRYPTED);

        final ArtifactoryRestResponse<String> response = new ArtifactoryRestResponse<>();
        VerifySigningKeyService signingKeyService = new VerifySigningKeyService(addonsManager, keyPairService);
        signingKeyService.execute(signKey, response);
        assertEquals(response.getResponseCode(), Response.Status.OK.getStatusCode());
        assertNull(response.getFeedbackMsg().getInfo());
        assertEquals(response.getFeedbackMsg().getError(), "No public key installed");
    }

    @Test
    public void shouldFailForNotSetPrivateKey() {
        when(keyPairService.getKeyPairByName(any()))
                .thenReturn(keyPairDescriptor);
        when(keyPairDescriptor.getPrivateKey()).thenReturn(StringUtils.EMPTY);
        when(keyPairDescriptor.getPublicKey()).thenReturn("key2");

        final SignKey signKey = new SignKey();
        signKey.setPassPhrase(PASSPHRASE_NON_ENCRYPTED);

        final ArtifactoryRestResponse<String> response = new ArtifactoryRestResponse<>();
        VerifySigningKeyService signingKeyService = new VerifySigningKeyService(addonsManager, keyPairService);
        signingKeyService.execute(signKey, response);
        assertEquals(response.getResponseCode(), Response.Status.OK.getStatusCode());
        assertNull(response.getFeedbackMsg().getInfo());
        assertEquals(response.getFeedbackMsg().getError(), "No private key installed");
    }
}