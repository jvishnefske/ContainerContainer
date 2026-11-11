package org.artifactory.security.token.model;

import org.artifactory.api.security.access.GenericTokenSpec;
import org.artifactory.api.security.access.TokenSpec;
import org.artifactory.api.security.access.UserTokenSpec;
import org.artifactory.common.ConstantValues;
import org.artifactory.security.token.exception.TokenRequestException;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.artifactory.security.token.TokenResponseErrorCode.InvalidRequest;

public class TokenUtils {

    @Nonnull
    public static String requireUsername(String username) {
        if (isBlank(username)) {
            throw new TokenRequestException(InvalidRequest, "username is required");
        }
        return username;
    }

    @Nonnull
    public static List<String> audienceToList(@Nullable String audience) {
        if (isBlank(audience)) {
            return emptyList();
        }
        return asList(audience.split(" "));
    }

    public static long expiresInOrDefault(Long expiresIn) {
        return expiresInOrDefault(expiresIn, ConstantValues.accessTokenExpiresInDefault.getLong());
    }

    public static long expiresInOrDefault(Long expiresIn, long expiresInDefault) {
        if (expiresIn == null) {
            return expiresInDefault;
        }

        if (expiresIn < 0) {
            throw new TokenRequestException(InvalidRequest, "Invalid expires_in value: " + expiresIn);
        }

        return expiresIn;
    }


    public static List<String> createScopeFromPath(List<String> repoPath) {
        return repoPath.stream()
                .map(path -> "path:" + path + ":read")
                .collect(Collectors.toList());
    }

    public static TokenSpec<? extends TokenSpec> createTokenSpec(String username) {
        if (GenericTokenSpec.accepts(username)) {
            return GenericTokenSpec.create(username);
        }
        return UserTokenSpec.create(username);
    }


    /**
     * Check that the given token spec is supported for creating tokens by Artifactory.
     * This check is done here, in the resource, and not in the service to limit the possibilities from the REST API,
     * but keep the flexibility inside the system.
     *
     * @param tokenSpec the token spec to check
     */
    public static void assertSupportedTokenSpecForCreate(TokenSpec tokenSpec, String serviceIdFormattedName) {
        if (!tokenSpec.isRefreshable() || (tokenSpec.getExpiresIn() != null && tokenSpec.getExpiresIn() <= 0)) {
            //Only refreshable tokens with expiry can be used in other artifactory instances/clusters
            if (!targetAudienceIsOnlyThisArtifactoryService(tokenSpec, serviceIdFormattedName)) {
                throw new TokenRequestException(InvalidRequest,
                        "Only refreshable tokens with expiry can have custom audience");
            }
        }
    }

    private static boolean targetAudienceIsOnlyThisArtifactoryService(TokenSpec tokenSpec,
            String serviceIdFormattedName) {
        List<String> audience = tokenSpec.getAudience();
        return audience.isEmpty() ||
                audience.equals(singletonList(serviceIdFormattedName));
    }
}
