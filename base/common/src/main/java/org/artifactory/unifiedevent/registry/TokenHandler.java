package org.artifactory.unifiedevent.registry;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.security.access.AccessService;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.token.TokenRequest;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.jfrog.access.common.ServiceType.ARTIFACTORY;

@AllArgsConstructor
@Component
@Slf4j
public class TokenHandler {
    private static final Long TOKEN_ACCESS_EXPIRATION_SEC = 60L;
    private final Map<Consumer, String> consumerTokenCache = new ConcurrentHashMap<>();
    protected AccessService accessService;


    public String getToken(Consumer consumer) {
        String token = consumerTokenCache.get(consumer);
        if (token == null || isExpired(token)) {
            token = generateToken(consumer);
            consumerTokenCache.put(consumer, token);
        }
        return token;
    }

    public String generateToken(Consumer consumer) {
        AccessClient accessClient = accessService.getAccessClient();
        if (accessClient == null) {
            throw new AccessClientException("Access Client is null, may not be initiated");
        }
        String serviceId = accessService.getArtifactoryServiceId().getFormattedName();
        log.debug("Generating token for service: {}, consumer: {}", serviceId, consumer.name());
        // TODO: The properties of the token should b stored with consumer class
        TokenRequest tokenRequest =
                TokenRequest.builder()
                        .audience(ARTIFACTORY + "@*")
                        .scopes("applied-permissions/admin")
                        .issuer(serviceId)
                        .subject(serviceId)
                        .expiresIn(TOKEN_ACCESS_EXPIRATION_SEC)
                        .build();
        return accessClient.token().create(tokenRequest).getTokenValue();
    }

    boolean isExpired(String token) {
        log.trace(token);
        // Implement logic to check if the token is expired
        // This could involve checking the token's expiration time or other criteria
        return true;
    }

}