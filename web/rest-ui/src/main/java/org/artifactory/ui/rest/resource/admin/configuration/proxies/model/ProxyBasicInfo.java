package org.artifactory.ui.rest.resource.admin.configuration.proxies.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.ui.rest.model.admin.configuration.proxy.Proxy;

import java.util.List;

@Slf4j
@Builder
public record ProxyBasicInfo(String key, boolean platformDefault) implements RestModel {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Proxy>> PROXY_LIST_TYPE = new TypeReference<>() {
    };

    public static List<ProxyBasicInfo> toBasicInfo(Response response) {
        if (response == null || response.getEntity() == null) {
            return List.of();
        }
        try {
            return MAPPER.readValue(response.getEntity().toString(), PROXY_LIST_TYPE)
                    .stream()
                    .map(proxy -> ProxyBasicInfo.builder()
                            .key(proxy.getKey())
                            .platformDefault(proxy.isPlatformDefault())
                            .build())
                    .toList();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse proxy response for basic info conversion", e);
            return List.of();
        }
    }
}
