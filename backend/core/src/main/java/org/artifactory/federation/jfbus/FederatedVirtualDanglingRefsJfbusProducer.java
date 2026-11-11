package org.artifactory.federation.jfbus;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.federation.DeferredDanglingRefPublisher;
import org.artifactory.jfbus.JfbusEventingService;
import org.artifactory.spring.ConditionalOnProperty;
import org.artifactory.spring.ContextCreationListener;
import org.jfrog.common.JsonUtils;
import org.jfrog.eventing.facade.TopicId;
import org.springframework.stereotype.Service;

import java.util.Map;

import static org.artifactory.jfbus.JfbusConstants.HEADER_TYPE;
import static org.artifactory.jfbus.JfbusConstants.HEADER_VIRTUAL_FEDERATED;
import static org.artifactory.jfbus.JfbusConstants.VIRTUAL_REPO_REF_REMOVAL_EVENT_TYPE;

/**
 * JFBus producer for federated virtual dangling-ref cleanup retries.
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "artifactory.virtual.repo.federation.enabled", havingValue = "true")
public class FederatedVirtualDanglingRefsJfbusProducer implements DeferredDanglingRefPublisher,
        ContextCreationListener {

    public static final TopicId DEFERRED_TOPIC =
            new TopicId("artifactory", "virtual-repo-ref-removal-deferred");

    private static final Map<String, String> DEFERRED_EVENT_HEADERS = Map.of(
            HEADER_TYPE, VIRTUAL_REPO_REF_REMOVAL_EVENT_TYPE,
            HEADER_VIRTUAL_FEDERATED, "true");

    private final JfbusEventingService jfbusEventingService;

    public FederatedVirtualDanglingRefsJfbusProducer(JfbusEventingService jfbusEventingService) {
        this.jfbusEventingService = jfbusEventingService;
    }

    @Override
    public void onContextCreated() {
        try {
            jfbusEventingService.createTopic(DEFERRED_TOPIC,
                    ConstantValues.virtualRepoRefRemovalJfbusTopicPartitions.getInt(), true);
        } catch (Exception e) {
            log.error("Could not create JFBus topic {}: {}", DEFERRED_TOPIC, e.getMessage(), e);
        }
    }

    @Override
    public boolean publishDanglingRefCleanupRetry(String virtualRepoKey, String childRepoKey) {
        if (!jfbusEventingService.isConfigured()) {
            log.info("JFBus is not configured; federated dangling ref deferred retry is not available for" +
                    " {} in {}", childRepoKey, virtualRepoKey);
            return true;
        }
        try {
            byte[] payload = serializePayload(virtualRepoKey, childRepoKey);
            String key = virtualRepoKey + "|" + childRepoKey;
            jfbusEventingService.sendEventBlocking(DEFERRED_TOPIC, key, payload, DEFERRED_EVENT_HEADERS);
            log.debug("Published federated dangling ref cleanup retry: {} in {}", childRepoKey, virtualRepoKey);
            return true;
        } catch (Exception e) {
            log.warn("Failed to publish federated dangling ref cleanup retry for {} in {}: {}",
                    childRepoKey, virtualRepoKey, e.getMessage());
            return false;
        }
    }

    private static byte[] serializePayload(String virtualRepoKey, String childRepoKey) {
        return JsonUtils.getInstance().valueToByteArray(
                new DeferredDanglingRefMessage(virtualRepoKey, childRepoKey));
    }
}
