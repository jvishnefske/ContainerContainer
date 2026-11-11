package org.artifactory.storage.db.locks.service;

import jakarta.annotation.Nonnull;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.descriptor.addon.ha.lock.DbLockingMechanism;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.jfrog.common.config.diff.DataDiff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * This facade's purpose is to delegate to the proper DB locking mechanism depending on the following conditions:
 * On init, the facade selects the proper DB locking mechanism depending on the value of:
 * {@link org.artifactory.common.ConstantValues#dbLockingMechanism}.
 * if the value is: {@link org.artifactory.descriptor.addon.ha.lock.DbLockingMechanism#JFROG_TABLE_BASED } the standard
 * {@link JFrogTableBasedDbLocksService} implementation is selected.
 * Otherwise, if it is: {@link org.artifactory.descriptor.addon.ha.lock.DbLockingMechanism#NATIVE} then
 * {@link NativeDbLocksService} is selected.
 * Finally, if the value is: {@link org.artifactory.descriptor.addon.ha.lock.DbLockingMechanism#AUTO},
 * then {@link AutoDbLocksService} is selected.
 * </p>
 *
 * @see NativeDbLocksService
 * @see JFrogTableBasedDbLocksService
 * @see AutoDbLocksService
 *
 * @author Mor Merhav
 */
@Primary
@Service
@Reloadable(beanClass = DbLocksServiceFacade.class, initAfter = AccessService.class, listenOn = CentralConfigKey.none)
@Slf4j
public class DbLocksServiceFacade implements DbLocksService {

    @Delegate(excludes = ExcludedMethods.class)
    private DbLocksService dbLocksService;

    @Autowired
    public DbLocksServiceFacade(JFrogTableBasedDbLocksService tableBasedDbLocksService) {
        try {
            switch (DbLockingMechanism.get()) {
                case JFROG_TABLE_BASED -> {
                    log.info("Table based DB lock mechanism selected.");
                    dbLocksService = tableBasedDbLocksService;
                }
                case NATIVE -> {
                    dbLocksService = getNativeDbLocksService();
                    log.info("Native DB lock mechanism selected.");
                }
                case AUTO -> {
                    ArtifactoryServersCommonService artifactoryServersCommonService =
                            ContextHelper.get().beanForType(ArtifactoryServersCommonService.class);
                    CentralConfigService centralConfigService =
                            ContextHelper.get().beanForType(CentralConfigService.class);
                    dbLocksService = new AutoDbLocksService(tableBasedDbLocksService, getNativeDbLocksService(),
                            artifactoryServersCommonService, centralConfigService);
                    log.info("Automatic DB lock mechanism selected.");
                }
            }
        } catch (ClassNotFoundException e) {
            ArtifactoryDbProperties dbProperties = ArtifactoryHome.get().getDBProperties();
            log.info("No NativeDbLocksService implementation bean exists for DB type: '{}'. Falling back to table" +
                    " based mechanism.", dbProperties.getDbType());
            dbLocksService = tableBasedDbLocksService;
        } catch (Exception e) {
            log.error("Failed to initialize DB locks mechanism due to: {}. Falling back to table based mechanism.",
                    e.getMessage());
            dbLocksService = tableBasedDbLocksService;
        }
    }

    @Nonnull
    private NativeDbLocksService getNativeDbLocksService() throws ClassNotFoundException {
        try {
            return ContextHelper.get().beanForType(NativeDbLocksService.class);
        } catch (RuntimeException e) {
            log.debug("Failed to fetch NativeDbLocksService bean", e);
            throw new ClassNotFoundException();
        }
    }

    @SuppressWarnings("unused")
    private interface ExcludedMethods {
        void init();

        void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff);

        void destroy();

        boolean isInterested();

        void convert();
    }
}
