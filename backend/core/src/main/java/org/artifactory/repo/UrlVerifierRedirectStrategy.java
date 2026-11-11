/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.repo;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.protocol.HttpContext;
import org.artifactory.common.ConstantValues;
import org.artifactory.request.UrlVerifier;

import java.net.URI;

/**
 *  @author Eli Fishman
 *
 * A {@link RedirectStrategy} decorator that validates every redirect {@code Location}
 * target through {@link UrlVerifier#isRedirectTargetBlocked(String, String)} before the redirect is followed.
 * <p>
 * It applies only the minimal SSRF <em>security floor</em> — loopback and known JFrog internal hosts —
 * deliberately leaving private/site-local range blocking to the existing, customer-tunable redirect denylist
 * inside {@code JFrogDefaultRedirectStrategy}. This keeps the blast radius small: legitimate internal-mirror
 * redirects on private IPs keep working, while the reported SSRF targets (loopback, cloud-metadata via the
 * underlying denylist, {@code jfrog-argocd-internal.jfrog.io}, {@code jpms.jfrog.com}) are closed.
 * <p>
 * It closes the hole independently of the underlying strategy: it wraps both {@code DefaultRedirectStrategy}
 * (used when {@code ignoreRedirectStrategy} falls back on on-prem) and {@code JFrogDefaultRedirectStrategy}, so
 * the floor is enforced on every deployment. Because the check runs on the {@code Location} regardless of HTTP
 * method, it also closes the HEAD-200/GET-301 split-brain (a redirector that answers 200 to HEAD probes but 301
 * to GET body fetches).
 * <p>
 * Behaviour is governed by two flags, mirroring the outbound guard:
 * <ul>
 *   <li>{@link ConstantValues#httpRepoRedirectSsrfGuardEnabled} — when {@code true}, a blocked Location stops
 *       the redirect from being followed (fail closed).</li>
 *   <li>{@link ConstantValues#httpRepoRedirectSsrfGuardShadowMode} — when the guard is disabled but shadow mode
 *       is on (default), the Location is still evaluated and a WARN is logged for anything that would be
 *       blocked, without changing behaviour.</li>
 * </ul>
 */
@Slf4j
public class UrlVerifierRedirectStrategy implements RedirectStrategy {

    private final RedirectStrategy delegate;
    private final UrlVerifier urlVerifier;
    private final String repoKey;

    public UrlVerifierRedirectStrategy(@Nonnull RedirectStrategy delegate, @Nonnull UrlVerifier urlVerifier,
            @Nonnull String repoKey) {
        this.delegate = delegate;
        this.urlVerifier = urlVerifier;
        this.repoKey = repoKey;
    }

    @Override
    public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context)
            throws ProtocolException {
        boolean delegateRedirected = delegate.isRedirected(request, response, context);
        if (!delegateRedirected) {
            return false;
        }
        boolean guardEnabled = ConstantValues.httpRepoRedirectSsrfGuardEnabled.getBoolean();
        boolean shadowMode = ConstantValues.httpRepoRedirectSsrfGuardShadowMode.getBoolean();
        if (!guardEnabled && !shadowMode) {
            return true;
        }
        String location = extractCheckableLocation(response);
        if (location == null) {
            return true;
        }
        if (!urlVerifier.isRedirectTargetBlocked(location, repoKey)) {
            return true;
        }
        if (guardEnabled) {
            log.warn("Blocking redirect for repo '{}' — SSRF guard rejected redirect target: {}", repoKey, location);
            return false;
        }
        log.warn("[redirect SSRF-guard shadow] Would have blocked redirect for repo '{}' — target: {}",
                repoKey, location);
        return true;
    }

    @Override
    public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context)
            throws ProtocolException {
        return delegate.getRedirect(request, response, context);
    }

    @VisibleForTesting
    RedirectStrategy getDelegate() {
        return delegate;
    }

    /**
     * Returns the {@code Location} header value when it names a host of its own, which is the SSRF-relevant case: an
     * absolute url, or a protocol-relative {@code //host/path}. The latter states no scheme, so it is not an absolute
     * URI, but {@link org.apache.http.impl.client.DefaultRedirectStrategy} resolves a non-absolute Location against
     * the request url, and resolving a network-path reference keeps the authority it states - so the client connects
     * to whatever host the redirect named. Checking only absolute urls followed those unchecked.
     * <p>
     * A Location stating no authority is still not re-checked: it resolves to the current host, which was validated
     * before the request that is now redirecting was made.
     */
    private String extractCheckableLocation(HttpResponse response) {
        Header locationHeader = response.getFirstHeader(HttpHeaders.LOCATION);
        if (locationHeader == null || StringUtils.isBlank(locationHeader.getValue())) {
            return null;
        }
        String location = locationHeader.getValue();
        try {
            URI uri = new URI(location);
            return uri.isAbsolute() || uri.getRawAuthority() != null ? location : null;
        } catch (Exception e) {
            log.debug("Could not parse redirect Location '{}' for repo '{}'", location, repoKey, e);
            return null;
        }
    }
}
