package org.artifactory.storage.checksum.replication;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.storage.fs.service.ConfigsService;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static org.artifactory.storage.fs.service.ConfigsServiceKey.STORAGE_CHECKSUM_REPLICATION;

/**
 * @author Avishay Halpren
 */
@Component
@AllArgsConstructor
@Slf4j
public class StorageChecksumReplicationService {

    public static final int MAX_DAYS_TO_ALLOW = 30;
    private static final int MILLIS_IN_A_DAY = 24 * 60 * 60 * 1000;

    private final ConfigsService configsService;
    private final LoadingCache<String, StorageChecksumReplicationConfig> configCache = CacheBuilder.newBuilder()
            .initialCapacity(1)
            .maximumSize(1)
            .expireAfterWrite(10, TimeUnit.MINUTES) //for the sake of rest ui "auth/screen/globalState"
            .build(new CacheLoader<>() {
                @Override
                public StorageChecksumReplicationConfig load(@Nonnull String source) {
                    StorageChecksumReplicationConfig config = getConfig();
                    if (config == null) {
                        //so it will be stored in cache (instead of null) - empty still means notAllowed
                        config = new StorageChecksumReplicationConfig();
                    }
                    return config;
                }
            });

    @Nullable
    public StorageChecksumReplicationConfig getConfig() {
        return configsService.getConfig(STORAGE_CHECKSUM_REPLICATION, StorageChecksumReplicationConfig.class);
    }

    @NonNull
    public StorageChecksumReplicationConfig getConfigWithCache() {
        return configCache.getUnchecked("key"); //"key" is not relevant
    }

    /**
     * used by REST API
     */
    public StorageChecksumReplicationModel getModel() {
        StorageChecksumReplicationConfig config = getConfig();
        if (config != null) {
            StorageChecksumReplicationModel model = new StorageChecksumReplicationModel();
            model.setCheckBinaryExistenceAllowed(config.isAllowed());
            model.setTrustUntil(config.getTrustUntilMsg());
            return model;
        }
        return null;
    }

    /**
     * used by REST API
     */
    public void updateModel(StorageChecksumReplicationModel model) {
        try {
            long now = System.currentTimeMillis();
            StorageChecksumReplicationConfig checksumReplicationConfig = new StorageChecksumReplicationConfig();
            Boolean allowed = model.getCheckBinaryExistenceAllowed();
            if (allowed == null) {
                throw new BadRequestException("checkBinaryExistenceAllowed must be provided");
            }
            Integer daysToTrust = model.getDaysToTrust();
            if (!allowed && daysToTrust != null) {
                throw new BadRequestException(
                        "daysToTrust should not be used when checkBinaryExistenceAllowed is false");
            }
            if (daysToTrust != null && (daysToTrust > MAX_DAYS_TO_ALLOW || daysToTrust < 0)) {
                throw new BadRequestException("daysToTrust must be between 0 to " + MAX_DAYS_TO_ALLOW);
            }
            checksumReplicationConfig.setAllowed(allowed);
            if (daysToTrust != null) {
                checksumReplicationConfig.setTrustUntil(now + ((long) daysToTrust * MILLIS_IN_A_DAY));
            }
            configsService.addOrUpdateConfig(STORAGE_CHECKSUM_REPLICATION, checksumReplicationConfig, now);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed updating {}", model, e);
            //not throwing the same error/message - so the internal message will not reach the client
            throw new RuntimeException("Error update configuration");
        }
    }

}
