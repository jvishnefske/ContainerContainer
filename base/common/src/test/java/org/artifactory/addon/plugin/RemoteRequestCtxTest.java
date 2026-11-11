package org.artifactory.addon.plugin;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemoteRequestCtxTest {
    @Test
    void setHeadersKeepsCaseInsensitiveLookup() {
        RemoteRequestCtx ctx = new RemoteRequestCtx();
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Jfrog-Origin", "value");

        ctx.setHeaders(headers);

        assertEquals("value", ctx.getHeaders().get("X-JFrog-Origin"));
    }
}
