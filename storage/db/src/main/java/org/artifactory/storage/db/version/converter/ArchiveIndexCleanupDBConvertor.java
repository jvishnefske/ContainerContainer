package org.artifactory.storage.db.version.converter;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.jfrog.storage.DbType;

import java.sql.Connection;
import java.util.function.BiPredicate;

/**
 * @author Evgeny Kurtser
 */
@Slf4j
public class ArchiveIndexCleanupDBConvertor extends ScriptFileDbConverter {

    @SafeVarargs
    public ArchiveIndexCleanupDBConvertor(@Nonnull String file, BiPredicate<Connection, DbType>... conditions) {
        super(file, conditions);
    }

    @Override
    public DbConverterResult convert(ChangeLogContext context) {
        DbConverterResult result;
        boolean upgradeAllowed = isUpgradeAllowed();
        if (upgradeAllowed) {
            result = super.convert(context);
        } else {
            result = new DbConverterResult();
            result.setStatus(ConverterApplyStatus.SUSPENDED);
            log.info("Converter `{}` is done with status `{}`.", context.getDbVersion().version(), result.getStatus());
        }
        return result;
    }

    /**
     * Check if the upgrade is allowed for this converter.
     * The converter should be called only if the previous version is 7.8.0 or newer.
     */
    private boolean isUpgradeAllowed() {
        boolean oldVersionFound = false;
        try {
            long v7800Revision = ArtifactoryServiceVersionProvider.v7800.get().getRevision();
            ArtifactoryServersCommonService serversService =
                    ContextHelper.get().beanForType(ArtifactoryServersCommonService.class);
            oldVersionFound = serversService.getAllArtifactoryServers().stream()
                    .anyMatch(server -> server.getArtifactoryRevision() < v7800Revision);

            if(oldVersionFound) {
                log.info("This converter won't run on RT version older than 7.88.0. Skipping this converter.");
            }
        } catch (Exception e) {
            log.error("Cannot retrieve previous version data due to {}. Skipping this converter.", e.getMessage(), e);
        }
        return !oldVersionFound;
    }
}