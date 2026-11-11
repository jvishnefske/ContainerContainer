package org.artifactory.ui.rest.service.admin.security.keys;

import lombok.NonNull;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.keys.KeysAddon;
import org.artifactory.api.jackson.JacksonWriter;
import org.artifactory.model.keys.TrustedKeyImpl;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.admin.security.keys.TrustedKeyIModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@SuppressWarnings({"unchecked", "rawtypes"})
class CreateTrustedKeyServiceTest {

    private static final String KEY = "{key}";
    private static final String TYPE = "{type}";
    private static final String ALIAS = "{alias}";

    private CreateTrustedKeyService keyService;

    @BeforeEach
    public void setUp() {
        KeysAddon addon = mock(KeysAddon.class);
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(KeysAddon.class)).thenReturn(addon);
        when(addon.createTrustedKey(KEY, TYPE, ALIAS)).thenReturn(buildTestTrustedKey());
        keyService = new CreateTrustedKeyService(addonsManager);
    }

    @Test
    void execute_Success() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = mock(RestResponse.class);
        when(request.getImodel()).thenReturn(buildTestTrustedKeyInput());
        keyService.execute(request, response);
        verify(response).iModel("""
                {
                    "public_key": "{key}",
                    "kid": "{kid}",
                    "type": "{type}",
                    "alias": "{alias}",
                    "fingerprint": "{fingerprint}",
                    "issuedBy": "{issuedBy}",
                    "issued": 1640000000,
                    "expiry": 0
                }""".replaceAll("[\s\n]", ""));
        verify(response).responseCode(CREATED.value());
    }

    @Test
    void execute_InternalServerError() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = mock(RestResponse.class);
        when(request.getImodel()).thenReturn(buildTestTrustedKeyInput());
        try (MockedStatic<JacksonWriter> mockedJacksonWriter = mockStatic(JacksonWriter.class)) {
            mockedJacksonWriter.when(() -> JacksonWriter.serialize(any())).thenThrow(new IOException());
            keyService.execute(request, response);
            verify(response).error("Error occurred while creating trusted key");
            verify(response).responseCode(INTERNAL_SERVER_ERROR.value());
        }
    }

    @NonNull
    private static TrustedKeyIModel buildTestTrustedKeyInput() {
        return TrustedKeyIModel.builder()
                .type(TYPE)
                .alias(ALIAS)
                .trustedKey(KEY)
                .build();
    }

    @NonNull
    private static TrustedKeyImpl buildTestTrustedKey() {
        return TrustedKeyImpl.builder()
                .kid("{kid}")
                .type(TYPE)
                .alias(ALIAS)
                .trustedKey(KEY)
                .issuedBy("{issuedBy}")
                .issued(1640000000L)
                .fingerprint("{fingerprint}")
                .expiry(0L)
                .build();
    }
}