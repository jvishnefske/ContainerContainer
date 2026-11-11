package org.artifactory.repo.interceptor.repocud;

import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.crypto.CryptoService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoMetadata;
import org.artifactory.repo.interceptor.RepoEventTriggerSourceResolver;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.repo.RepoConfigEventData;
import org.artifactory.util.RepoConfigDiffUtil;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.artifactory.security.AuthenticationHelper.getAuthentication;

/**
 * Builds the canonical {@link RepoConfigEventData} payload for repo CUD events. Centralizes the TR1 shape
 * (actor context, repository metadata, resolved {@code triggerSource}) and the event-specific payload fields (masked
 * config snapshot for create, status code for partial delete). The same payload is published to both the Worker
 * outbound and the JFBus dispatchers via {@link org.artifactory.unifiedevent.UnifiedEventService}, so masking happens
 * once here, before the event envelope is built.
 */
@Slf4j
@Component
public class RepoConfigEventDataBuilder {

    private final SecurityService securityService;
    private final RepoEventTriggerSourceResolver triggerSourceResolver;
    private final CryptoService cryptoService;

    public RepoConfigEventDataBuilder(SecurityService securityService,
            RepoEventTriggerSourceResolver triggerSourceResolver, CryptoService cryptoService) {
        this.securityService = securityService;
        this.triggerSourceResolver = triggerSourceResolver;
        this.cryptoService = cryptoService;
    }

    /**
     * Builds the canonical payload for the given change and event type. CREATE events (BEFORE and AFTER) carry a
     * masked config snapshot; UPDATE events (BEFORE and AFTER) carry a field-level {@code changeAttributes} diff;
     * a non-null {@code statusCode} on AFTER_REPO_DELETE is recorded (e.g. 207 for partial content deletion).
     */
    public RepoConfigEventData build(@NonNull RepoConfigChange change, @NonNull SupportedUnifiedEvents eventType,
            @Nullable Integer statusCode, @Nullable RepoMetadata resolvedMetadata) {
        RepoMetadata metadata = resolvedMetadata == null ? RepoMetadata.empty() : resolvedMetadata;
        RepoConfigEventData.RepoConfigEventDataBuilder builder =
                baseData(change, triggerSourceResolver.resolve(), metadata);
        if (eventType == SupportedUnifiedEvents.BEFORE_REPO_CREATE
                || eventType == SupportedUnifiedEvents.AFTER_REPO_CREATE) {
            builder.config(maskedConfig(change));
        }
        if (eventType == SupportedUnifiedEvents.BEFORE_REPO_UPDATE
                || eventType == SupportedUnifiedEvents.AFTER_REPO_UPDATE) {
            builder.changeAttributes(changeAttributes(change));
        }
        if (eventType == SupportedUnifiedEvents.AFTER_REPO_DELETE && statusCode != null) {
            builder.statusCode(statusCode);
        }
        return builder.build();
    }

    private List<RepoConfigEventData.ChangeAttribute> changeAttributes(RepoConfigChange change) {
        return RepoConfigDiffUtil.maskedDiff(change.getOldRepoConfig(), change.getNewRepoConfig()).stream()
                .map(diff -> new RepoConfigEventData.ChangeAttribute(
                        diff.getAttributeName(), diff.getOldValue(), diff.getNewValue()))
                .toList();
    }

    private RepoConfigEventData.RepoConfigEventDataBuilder baseData(@NonNull RepoConfigChange change,
            @Nullable String triggerSource, @NonNull RepoMetadata resolvedMetadata) {
        return builderWithUserContext(change, resolvedMetadata, userContextWithTriggerSource(triggerSource));
    }

    private static RepoConfigEventData.RepoConfigEventDataBuilder builderWithUserContext(
            RepoConfigChange change, RepoMetadata resolvedMetadata, RepoConfigEventData.UserContext actor) {
        return RepoConfigEventData.builder()
                .context(new RepoConfigEventData.RepositoryEventContext(actor.id()))
                .metadata(buildMetadata(change, resolvedMetadata))
                .userContext(actor);
    }

    @Nullable
    private Object maskedConfig(RepoConfigChange change) {
        return maskOne(repoConfig(change));
    }

    @Nullable
    private RepoConfig maskOne(@Nullable RepoConfig config) {
        if (config == null) {
            return null;
        }
        List<RepoConfig> masked = cryptoService.mask(List.of(config));
        if (masked.isEmpty()) {
            log.warn("CryptoService.mask() returned empty for repo '{}'; skipping config from event payload"
                    + " (fail-closed)", config.getKey());
            return null;
        }
        return masked.getFirst();
    }

    private static RepoConfigEventData.RepositoryMetadata buildMetadata(RepoConfigChange change,
            RepoMetadata resolvedMetadata) {
        RepoConfig repoConfig = repoConfig(change);
        return new RepoConfigEventData.RepositoryMetadata(
                change.getRepoKey(),
                repoTypeName(change),
                packageTypeName(change),
                repoConfig == null ? null : repoConfig.isFederated(),
                resolvedMetadata.project(),
                resolvedMetadata.hasSharedProjects(),
                resolvedMetadata.hasReplication(),
                resolvedMetadata.environments());
    }

    private RepoConfigEventData.UserContext userContextWithTriggerSource(@Nullable String triggerSource) {
        Authentication authentication = getAuthentication();
        String name = securityService.currentUsername();
        boolean token = false;
        String realm = securityService.getAuthenticatedRealm();
        if (authentication instanceof AccessTokenAuthentication accessToken) {
            name = accessToken.getAccessToken().getSubject();
            token = true;
        }
        return new RepoConfigEventData.UserContext(name, token, realm, triggerSource);
    }

    private static String repoTypeName(RepoConfigChange change) {
        RepoDetailsType repoType = change.getRepoType();
        if (repoType == null) {
            return null;
        }
        return repoType.typeNameLowercase().replace(' ', '_');
    }

    private static String packageTypeName(RepoConfigChange change) {
        org.artifactory.descriptor.repo.RepoType packageType = change.getPackageType();
        return packageType == null ? null : packageType.getType();
    }

    @Nullable
    static RepoConfig repoConfig(RepoConfigChange change) {
        return change.getNewRepoConfig() == null ? change.getOldRepoConfig() : change.getNewRepoConfig();
    }
}
