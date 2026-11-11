package org.artifactory.storage.db.locks.service.condition;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.addon.ha.lock.DbLockingMechanism;
import org.artifactory.storage.db.locks.service.annotation.NativeDbLocksType;
import org.jfrog.storage.DbType;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.type.AnnotatedTypeMetadata;

import static org.artifactory.descriptor.addon.ha.lock.DbLockingMechanism.AUTO;
import static org.artifactory.descriptor.addon.ha.lock.DbLockingMechanism.NATIVE;

/**
 * @author Mor Merhav
 */
@Slf4j
public class NativeDbLocksBeanCondition implements Condition {
    @Override
    public boolean matches(@Nonnull ConditionContext context, @Nonnull AnnotatedTypeMetadata metadata) {
        MergedAnnotation<NativeDbLocksType> annotation = metadata.getAnnotations().get(NativeDbLocksType.class);
        if (annotation.equals(MergedAnnotation.missing())) {
            log.warn(
                    "Class: {} was annotated with @Conditional(NativeDbLocksBeanCondition.class) but lacked " +
                            "@NativeDbLocksType with appropriate locking DB type. Skipping bean init.",
                    metadata.getAnnotations().get(Conditional.class).getSource());
            return false;
        }
        DbType lockingDbTypeFromAnnotation = annotation.getEnum("value", DbType.class);
        ArtifactoryDbProperties dbProperties = ArtifactoryHome.get().getDBProperties();
        DbType dbTypeFromSettings = dbProperties.getDbType();
        log.trace("DB type: {}", dbTypeFromSettings);

        return (dbTypeFromSettings == lockingDbTypeFromAnnotation) &&
                (DbLockingMechanism.get() == NATIVE || DbLockingMechanism.get() == AUTO);
    }
}
