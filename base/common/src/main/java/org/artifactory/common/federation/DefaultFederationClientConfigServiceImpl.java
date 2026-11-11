package org.artifactory.common.federation;

import jakarta.annotation.Nonnull;
import org.artifactory.repo.FederationMigrationStatus;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.fs.service.ConfigsServiceKey;
import org.springframework.stereotype.Service;

@Service
@Reloadable(beanClass = FederationClientConfigurationService.class)
public class DefaultFederationClientConfigServiceImpl implements FederationClientConfigurationService {

    @Override
    public boolean isRtfsEnabled() {
        return false;
    }

    @Override
    public boolean isRtfsBinariesTasksEnabled() {
        return false;
    }

    @Override
    public boolean shouldUseFederationBinaryProviderV2() {
        return false;
    }

    @Override
    public FederationMigrationStatus getMigrationStatus() {
        return null;
    }

    @Override
    public void updateConfigKey(ConfigsServiceKey key, boolean value) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void updateConfigKey(ConfigsServiceKey key, String value) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public <T> boolean updateConfigKey(ConfigsServiceKey key, T value) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void deleteConfigKey(ConfigsServiceKey key) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void setBinaryTasksUsingRtfsFlag(boolean enable) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public boolean getBooleanConfig(ConfigsServiceKey key) {
        return false;
    }

    @Override
    public <T> T getConfig(ConfigsServiceKey key, Class<T> type) {
        return null;
    }

    @Nonnull
    private String getUnsupportedMessage() {
        return "Unsupported operation: federation client is not supported";
    }
}
