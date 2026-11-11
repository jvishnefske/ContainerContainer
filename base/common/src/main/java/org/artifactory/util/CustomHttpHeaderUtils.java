package org.artifactory.util;

import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpRequestBase;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.crypto.CryptoService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.repo.remote.CustomHttpHeader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Applies configured custom HTTP headers to outgoing remote repository requests.
 * Sensitive values are decrypted via {@link ContextHelper#get()} and {@link CryptoService#decryptStoredValue}.
 * <p>
 * Header name/value rules (RFC 7230 token, blocklists, CRLF, etc.) are enforced at persist time by
 * {@link CustomHttpHeaderValidator} (REST create/update, {@code RepoConfigCustomHttpHeadersChangeValidator}, and
 * migration mapping). This class only attaches already-validated config to the HTTP client.
 * Sensitive values are omitted (not sent) when the stored or decrypted value is blank (REST “clear” uses an empty
 * string), when the context is missing, when decrypt fails, or when the value still looks encrypted afterward; those
 * cases are logged at DEBUG to avoid flooding logs on every outbound request.
 */
@Slf4j
@UtilityClass
public final class CustomHttpHeaderUtils {

    /**
     * Returns a new list of {@link CustomHttpHeader} instances (copy semantics for assignment into repo config).
     */
    public static List<CustomHttpHeader> normalizedCopy(@Nullable List<CustomHttpHeader> headers) {
        if (headers == null || headers.isEmpty()) {
            return new ArrayList<>();
        }
        return headers.stream()
                .filter(Objects::nonNull)
                .map(h -> new CustomHttpHeader(h.getName(), h.getValue(), h.isSensitive()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static void addCustomHttpHeadersToRequest(HttpRequestBase method, String repoKey,
            List<CustomHttpHeader> customHttpHeaders) {
        if (!ConstantValues.remoteRepoCustomHttpHeadersEnabled.getBoolean()) {
            return;
        }
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(repoKey, "repoKey");
        Objects.requireNonNull(customHttpHeaders, "customHttpHeaders");
        for (CustomHttpHeader customHeader : customHttpHeaders) {
            addSingleCustomHttpHeaderToRequest(method, repoKey, customHeader);
        }
    }

    private static void addSingleCustomHttpHeaderToRequest(HttpRequestBase method, String repoKey,
            CustomHttpHeader customHeader) {
        if (customHeader == null) {
            log.debug("Skipping null custom HTTP header for repo {}", repoKey);
            return;
        }

        String headerName = customHeader.getName();
        if (headerName == null) {
            log.debug("Skipping custom HTTP header with null name for repo {}", repoKey);
            return;
        }

        String headerValue = resolveCustomHttpHeaderValue(repoKey, customHeader, headerName);
        if (headerValue == null) {
            return;
        }
        method.setHeader(headerName, headerValue);
    }

    private static String resolveCustomHttpHeaderValue(String repoKey, CustomHttpHeader customHeader,
            String headerName) {
        String headerValue = customHeader.getValue();
        if (headerValue == null) {
            log.debug("Skipping custom HTTP header '{}' with null value for repo {}", headerName, repoKey);
            return null;
        }
        if (customHeader.isSensitive()) {
            if (StringUtils.isBlank(headerValue)) {
                log.debug("Skipping sensitive custom HTTP header '{}' for repo {}: empty stored value (cleared)",
                        headerName, repoKey);
                return null;
            }
            headerValue = decryptSensitiveCustomHeaderValue(repoKey, headerName, headerValue);
            if (headerValue == null) {
                return null;
            }
            if (StringUtils.isBlank(headerValue)) {
                log.debug("Skipping sensitive custom HTTP header '{}' for repo {}: blank value after decrypt",
                        headerName, repoKey);
                return null;
            }
        }
        return headerValue;
    }

    private static String decryptSensitiveCustomHeaderValue(String repoKey, String headerName, String headerValue) {
        var ctx = ContextHelper.get();
        if (ctx == null) {
            log.debug("Omitting sensitive custom HTTP header '{}' for repo {}: no application context for decrypt",
                    headerName, repoKey);
            return null;
        }
        try {
            String decrypted = ctx.beanForType(CryptoService.class).decryptStoredValue(headerValue);
            if (StringUtils.isNotBlank(decrypted) && stillEncrypted(decrypted)) {
                log.debug("Omitting sensitive custom HTTP header '{}' for repo {}: value still appears encrypted "
                        + "after decrypt", headerName, repoKey);
                return null;
            }
            return decrypted;
        } catch (RuntimeException e) {
            log.debug("Omitting sensitive custom HTTP header '{}' for repo {} after CryptoService decrypt failure: {}",
                    headerName, repoKey, e.getMessage(), e);
            return null;
        }
    }

    private static boolean stillEncrypted(String value) {
        return CryptoHelper.isArtifactoryKeyEncrypted(value) || CryptoHelper.isMasterKeyEncrypted(value);
    }
}
