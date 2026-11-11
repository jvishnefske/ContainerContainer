package org.artifactory.util;

import org.apache.http.client.methods.HttpGet;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.crypto.CryptoService;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.remote.CustomHttpHeader;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CustomHttpHeaderUtils}.
 */
class CustomHttpHeaderUtilsTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO = "remote-generic";

    @Test
    void normalizedCopyReturnsEmptyForNullOrEmpty() {
        assertTrue(CustomHttpHeaderUtils.normalizedCopy(null).isEmpty());
        assertTrue(CustomHttpHeaderUtils.normalizedCopy(List.of()).isEmpty());
    }

    @Test
    void normalizedCopyFiltersNullsAndCopies() {
        List<CustomHttpHeader> in = new ArrayList<>();
        in.add(new CustomHttpHeader("A", "1", false));
        in.add(null);
        in.add(new CustomHttpHeader("B", "2", true));

        List<CustomHttpHeader> out = CustomHttpHeaderUtils.normalizedCopy(in);

        assertEquals(2, out.size());
        assertEquals("a", out.get(0).getName());
        assertEquals("1", out.get(0).getValue());
        assertEquals("b", out.get(1).getName());
        assertEquals("2", out.get(1).getValue());
        assertTrue(out.get(1).isSensitive());
    }

    @Test
    void addCustomHttpHeadersToRequestNoOpWhenFeatureDisabled() {
        setStringSystemProperty(ConstantValues.remoteRepoCustomHttpHeadersEnabled, "false");
        HttpGet request = new HttpGet("http://example.com");
        CustomHttpHeaderUtils.addCustomHttpHeadersToRequest(request, REPO,
                List.of(new CustomHttpHeader("X-A", "v", false)));
        assertNull(request.getFirstHeader("X-A"));
    }

    @Test
    void addCustomHttpHeadersToRequestSetsNonSensitiveHeader() {
        setStringSystemProperty(ConstantValues.remoteRepoCustomHttpHeadersEnabled, "true");
        HttpGet request = new HttpGet("http://example.com");
        CustomHttpHeaderUtils.addCustomHttpHeadersToRequest(request, REPO,
                List.of(new CustomHttpHeader("X-Token", "plain-value", false)));
        assertEquals("plain-value", request.getFirstHeader("X-Token").getValue());
    }

    @Test
    void addCustomHttpHeadersToRequestSkipsNullHeaderNullNameNullValue() {
        setStringSystemProperty(ConstantValues.remoteRepoCustomHttpHeadersEnabled, "true");
        HttpGet request = new HttpGet("http://example.com");
        List<CustomHttpHeader> headers = new ArrayList<>();
        headers.add(null);
        CustomHttpHeader nullName = mock(CustomHttpHeader.class);
        when(nullName.getName()).thenReturn(null);
        when(nullName.getValue()).thenReturn("v");
        when(nullName.isSensitive()).thenReturn(false);
        headers.add(nullName);
        headers.add(new CustomHttpHeader("X-Ok", "ok", false));
        CustomHttpHeader nullValue = mock(CustomHttpHeader.class);
        when(nullValue.getName()).thenReturn("x-null-val");
        when(nullValue.getValue()).thenReturn(null);
        when(nullValue.isSensitive()).thenReturn(false);
        headers.add(nullValue);

        CustomHttpHeaderUtils.addCustomHttpHeadersToRequest(request, REPO, headers);

        assertEquals("ok", request.getFirstHeader("x-ok").getValue());
        assertNull(request.getFirstHeader("x-null-val"));
    }

    @Test
    void addCustomHttpHeadersToRequestDecryptsSensitiveViaCryptoService() {
        setStringSystemProperty(ConstantValues.remoteRepoCustomHttpHeadersEnabled, "true");
        ArtifactoryContext ctx = mock(ArtifactoryContext.class);
        CryptoService crypto = mock(CryptoService.class);
        when(ctx.beanForType(CryptoService.class)).thenReturn(crypto);
        when(crypto.decryptStoredValue("cipher")).thenReturn("clear-text");

        HttpGet request = new HttpGet("http://example.com");
        try (MockedStatic<ContextHelper> ch = mockStatic(ContextHelper.class)) {
            ch.when(ContextHelper::get).thenReturn(ctx);
            CustomHttpHeaderUtils.addCustomHttpHeadersToRequest(request, REPO,
                    List.of(new CustomHttpHeader("Authorization", "cipher", true)));
        }

        assertEquals("clear-text", request.getFirstHeader("Authorization").getValue());
        verify(crypto).decryptStoredValue(eq("cipher"));
    }

    @Test
    void addCustomHttpHeadersToRequestOmitsSensitiveWhenNoContext() {
        setStringSystemProperty(ConstantValues.remoteRepoCustomHttpHeadersEnabled, "true");
        HttpGet request = new HttpGet("http://example.com");
        try (MockedStatic<ContextHelper> ch = mockStatic(ContextHelper.class)) {
            ch.when(ContextHelper::get).thenReturn(null);
            CustomHttpHeaderUtils.addCustomHttpHeadersToRequest(request, REPO,
                    List.of(new CustomHttpHeader("Authorization", "cipher", true)));
        }
        assertNull(request.getFirstHeader("Authorization"));
    }

    @Test
    void addCustomHttpHeadersToRequestOmitsSensitiveWhenDecryptThrows() {
        setStringSystemProperty(ConstantValues.remoteRepoCustomHttpHeadersEnabled, "true");
        ArtifactoryContext ctx = mock(ArtifactoryContext.class);
        CryptoService crypto = mock(CryptoService.class);
        when(ctx.beanForType(CryptoService.class)).thenReturn(crypto);
        when(crypto.decryptStoredValue("cipher")).thenThrow(new IllegalStateException("boom"));

        HttpGet request = new HttpGet("http://example.com");
        try (MockedStatic<ContextHelper> ch = mockStatic(ContextHelper.class)) {
            ch.when(ContextHelper::get).thenReturn(ctx);
            CustomHttpHeaderUtils.addCustomHttpHeadersToRequest(request, REPO,
                    List.of(new CustomHttpHeader("Authorization", "cipher", true)));
        }
        assertNull(request.getFirstHeader("Authorization"));
    }

    @Test
    void addCustomHttpHeadersToRequestOmitsHeaderWhenSensitiveStoredValueEmptyCleared() {
        setStringSystemProperty(ConstantValues.remoteRepoCustomHttpHeadersEnabled, "true");
        HttpGet request = new HttpGet("http://example.com");
        CustomHttpHeaderUtils.addCustomHttpHeadersToRequest(request, REPO,
                List.of(new CustomHttpHeader("X-Bearer-Token", "", true)));
        assertNull(request.getFirstHeader("X-Bearer-Token"));
    }

    @Test
    void addCustomHttpHeadersToRequestOmitsHeaderWhenSensitiveDecryptsToEmpty() {
        setStringSystemProperty(ConstantValues.remoteRepoCustomHttpHeadersEnabled, "true");
        ArtifactoryContext ctx = mock(ArtifactoryContext.class);
        CryptoService crypto = mock(CryptoService.class);
        when(ctx.beanForType(CryptoService.class)).thenReturn(crypto);
        when(crypto.decryptStoredValue("cipher")).thenReturn("");

        HttpGet request = new HttpGet("http://example.com");
        try (MockedStatic<ContextHelper> ch = mockStatic(ContextHelper.class)) {
            ch.when(ContextHelper::get).thenReturn(ctx);
            CustomHttpHeaderUtils.addCustomHttpHeadersToRequest(request, REPO,
                    List.of(new CustomHttpHeader("Authorization", "cipher", true)));
        }
        assertNull(request.getFirstHeader("Authorization"));
    }

    @Test
    void addCustomHttpHeadersToRequestOmitsSensitiveWhenStillEncryptedAfterDecrypt() {
        setStringSystemProperty(ConstantValues.remoteRepoCustomHttpHeadersEnabled, "true");
        String stillEncrypted = "JE2f7DvoFNZ77qa4udE4LxVi8zsZ3q9tEzk5kaYJLgoLJYwdPm";
        ArtifactoryContext ctx = mock(ArtifactoryContext.class);
        CryptoService crypto = mock(CryptoService.class);
        when(ctx.beanForType(CryptoService.class)).thenReturn(crypto);
        when(crypto.decryptStoredValue("anything")).thenReturn(stillEncrypted);

        HttpGet request = new HttpGet("http://example.com");
        try (MockedStatic<ContextHelper> ch = mockStatic(ContextHelper.class)) {
            ch.when(ContextHelper::get).thenReturn(ctx);
            CustomHttpHeaderUtils.addCustomHttpHeadersToRequest(request, REPO,
                    List.of(new CustomHttpHeader("Authorization", "anything", true)));
        }
        assertNull(request.getFirstHeader("Authorization"));
    }

    @Test
    void addCustomHttpHeadersToRequestRequiresNonNullArguments() {
        setStringSystemProperty(ConstantValues.remoteRepoCustomHttpHeadersEnabled, "true");
        HttpGet request = new HttpGet("http://example.com");
        assertThrows(NullPointerException.class,
                () -> CustomHttpHeaderUtils.addCustomHttpHeadersToRequest(null, REPO, List.of()));
        assertThrows(NullPointerException.class,
                () -> CustomHttpHeaderUtils.addCustomHttpHeadersToRequest(request, null, List.of()));
        assertThrows(NullPointerException.class,
                () -> CustomHttpHeaderUtils.addCustomHttpHeadersToRequest(request, REPO, null));
    }
}
