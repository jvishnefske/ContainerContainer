package org.artifactory.logging.layout.request;

import ch.qos.logback.classic.Level;
import io.vavr.collection.Array;
import org.artifactory.logging.LoggingTestUtils;
import org.artifactory.logging.SaasLogNames;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.jfrog.storage.binstore.ifc.model.BinaryRequestOutKeys.KEY_DATE_TIME;
import static org.jfrog.storage.binstore.ifc.model.BinaryRequestOutKeys.KEY_DURATION;
import static org.jfrog.storage.binstore.ifc.model.BinaryRequestOutKeys.KEY_LENGTH;
import static org.jfrog.storage.binstore.ifc.model.BinaryRequestOutKeys.KEY_METHOD;
import static org.jfrog.storage.binstore.ifc.model.BinaryRequestOutKeys.KEY_REPO;
import static org.jfrog.storage.binstore.ifc.model.BinaryRequestOutKeys.KEY_SHA1;
import static org.jfrog.storage.binstore.ifc.model.BinaryRequestOutKeys.KEY_STATUS;
import static org.jfrog.storage.binstore.ifc.model.BinaryRequestOutKeys.KEY_URL;

/**
 * @author Idan Dadon
 */
public class StorageOutboundRequestJsonLayoutTest {
    StorageOutboundRequestJsonLayout storageOutboundRequestJsonLayout = new StorageOutboundRequestJsonLayout();

    @Test
    void testDoLayout() {
        String storageRequestOutLog = storageOutboundRequestJsonLayout.doLayout(
                LoggingTestUtils.createEvent(Array.of(), StorageOutboundRequestJsonLayout.class, Level.INFO,
                        createMdc()));
        Assertions.assertTrue(storageRequestOutLog.contains(
                "\"" + SaasLogNames.STORAGE_OUTBOUND_REQUEST_LOG.getLogKey() + "\":\"" +
                        SaasLogNames.STORAGE_OUTBOUND_REQUEST_LOG.getLogName() + "\""));
        Assertions.assertTrue(storageRequestOutLog.contains("\"traceid\":\"traceid\""));
        Assertions.assertTrue(storageRequestOutLog.contains("\"remote_repository\":\"remote-repo\""));
        Assertions.assertTrue(storageRequestOutLog.contains("\"path\":\"http://localhost:8081/artifactory\""));
        Assertions.assertTrue(storageRequestOutLog.contains("\"user\":\"internal\""));
        Assertions.assertTrue(storageRequestOutLog.contains("\"method\":\"HEAD\""));
        Assertions.assertTrue(storageRequestOutLog.contains("\"request\":{"));
        Assertions.assertTrue(storageRequestOutLog.contains("\"response\":{"));
        Assertions.assertTrue(storageRequestOutLog.contains("\"datetime\":"));
        Assertions.assertTrue(storageRequestOutLog.contains("\"length\":100"));
        Assertions.assertTrue(storageRequestOutLog.contains("\"duration\":420"));
    }

    private Map<String, String> createMdc() {
        Map<String, String> mdc = new HashMap<>();
        mdc.put("uber-trace-id", "traceid");
        mdc.put(KEY_REPO.getKeyName(), "remote-repo");
        mdc.put(KEY_METHOD.getKeyName(), "HEAD");
        mdc.put(KEY_URL.getKeyName(), "http://localhost:8081/artifactory");
        mdc.put(KEY_SHA1.getKeyName(), "sha1");
        mdc.put(KEY_DATE_TIME.getKeyName(), "2025-03-17T13:26:06.640Z");
        mdc.put(KEY_LENGTH.getKeyName(), "100");
        mdc.put(KEY_STATUS.getKeyName(), "200");
        mdc.put(KEY_DURATION.getKeyName(), "420");

        return mdc;
    }
}
