package org.artifactory.logging.layout.cleanup;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.JsonNode;
import io.vavr.collection.Array;
import lombok.Builder;
import org.artifactory.logging.LoggingTestUtils;
import org.artifactory.logging.SaasLogNames;
import org.jfrog.common.JsonUtils;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CleanupAuditJsonLayoutTest {

    private final CleanupAuditJsonLayout cleanupAuditJsonLayout = new CleanupAuditJsonLayout();

    @Test
    void testCleanupAuditJsonLayout() throws JSONException {
        CleanedNode cleanedNode = CleanedNode.builder()
                .policyKey("policyKey")
                .packageName("pName")
                .packageVersion("pVersion")
                .repo("repo")
                .nodePath("/path/to/node")
                .nodeName("nodeName")
                .sha1("SHA1")
                .status(1)
                .message("message")
                .build();
        String log = cleanupAuditJsonLayout.doLayout(
                LoggingTestUtils.createEvent(Array.of(cleanedNode), CleanupAuditJsonLayoutTest.class, Level.INFO,
                        Map.of()));
        JsonNode jsonNode = JsonUtils.getInstance().readTree(log);
        assertEquals(jsonNode.get(SaasLogNames.CLEANUP_AUDIT_LOG.getLogKey()).asText(),
                SaasLogNames.CLEANUP_AUDIT_LOG.getLogName());
        JSONAssert.assertEquals("""
                  {
                  "policyKey": "policyKey",
                  "policyRunId": 0,
                  "packageName": "pName",
                  "packageVersion": "pVersion",
                  "lead": false,
                  "repo": "repo",
                  "nodePath": "/path/to/node",
                  "nodeName": "nodeName",
                  "binLength": 0,
                  "sha1": "SHA1",
                  "status": 1,
                  "message": "message"
                }
                """, jsonNode.get("cleaned_node").toString(), JSONCompareMode.STRICT);
    }

    @SuppressWarnings("UnnecessaryModifier")
    @Builder
    private static record CleanedNode(
            String policyKey,
            int policyRunId,
            String packageName,
            String packageVersion,
            boolean lead,
            String repo,
            String nodePath,
            String nodeName,
            long binLength,
            String sha1,
            Integer status,
            String message
    ) {
    }
}