package org.artifactory.storage.db.locks.service;

import lombok.experimental.Delegate;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.locks.service.annotation.NativeDbLocksType;
import org.artifactory.storage.db.locks.service.condition.NativeDbLocksBeanCondition;
import org.jfrog.storage.DbType;
import org.jfrog.storage.concurrency.LockProvider;
import org.jfrog.storage.concurrency.NativeDbDatasourceConfig;
import org.jfrog.storage.concurrency.NativeDbLocksProvider;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import static org.artifactory.common.ConstantValues.dbLockNativeInitialAcquireSleepTimeMillis;
import static org.artifactory.common.ConstantValues.dbLockNativeMaxAcquireSleepTimeMillis;
import static org.artifactory.common.ConstantValues.dbLockNativePoolSize;

@Component
@NativeDbLocksType(DbType.POSTGRESQL)
@Conditional(NativeDbLocksBeanCondition.class)
public class ArtifactoryNativeDbLocksProvider implements LockProvider {

    @Delegate
    private final NativeDbLocksProvider nativeDbLocksProvider;

    public ArtifactoryNativeDbLocksProvider() {
        this.nativeDbLocksProvider = new NativeDbLocksProvider(new NativeDbDatasourceConfig(
                        ArtifactoryHome.get().getDBProperties(),
                        dbLockNativeInitialAcquireSleepTimeMillis.getLong(),
                        dbLockNativeMaxAcquireSleepTimeMillis.getLong(),
                        dbLockNativePoolSize.getInt()));
    }
}