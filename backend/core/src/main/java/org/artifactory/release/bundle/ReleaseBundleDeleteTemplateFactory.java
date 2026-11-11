package org.artifactory.release.bundle;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.storage.db.bundle.dao.ArtifactBundlesDao;
import org.jfrog.storage.DbProperties;
import org.springframework.context.annotation.Lazy;

import static org.jfrog.storage.DbType.DERBY;

/**
 * A release bundle delete-logic template toggle based on the configured DB type
 *
 * @author Uriah Levy
 */
@UtilityClass
@Slf4j
public class ReleaseBundleDeleteTemplateFactory {

    public ReleaseBundleDeleteTemplate getReleaseBundleDeleteTemplate(ArtifactBundlesDao bundlesDao,
            @Lazy InternalReleaseBundleService internalReleaseBundleService, DbProperties dbProperties) {
        if (DERBY.equals(dbProperties.getDbType())) {
            log.debug("Initializing Derby DB Release Bundle Delete template");
            return new DerbyReleaseBundleDeleteTemplate(bundlesDao, internalReleaseBundleService);
        }
        return new GeneralReleaseBundleDeleteTemplate(bundlesDao, internalReleaseBundleService);
    }

}
