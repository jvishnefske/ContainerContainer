package org.artifactory.http;

import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.artifactory.util.TenantIdProvider;

/**
 * Adds the {@code X-Jfrog-Src-Tenant-Id} header to outbound requests, resolved via {@link TenantIdProvider}.
 * Attached to a client only when it serves a smart remote repository, so the smart-repo check is done once
 * at client-build time rather than per request.
 */
public class SrcTenantIdHeaderInterceptor implements HttpRequestInterceptor {

    @Override
    public void process(HttpRequest request, HttpContext context) {
        TenantIdProvider.getTenantId()
                .ifPresent(tenantId -> request.setHeader(TenantIdProvider.X_JFROG_SRC_TENANT_ID, tenantId));
    }
}
