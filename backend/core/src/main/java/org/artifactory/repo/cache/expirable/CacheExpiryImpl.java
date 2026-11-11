/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.repo.cache.expirable;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.db.DbService;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.artifactory.util.application.ApplicationServiceConfigurationUtil.APPLICATION_SERVICE_ID_PROPERTY_NAME;

/**
 * Aggregates and polls all the cache expirable components when needing to determine if a path can expire
 *
 * @author Noam Y. Tenne
 */
@Slf4j
@Primary
@Service
@Reloadable(beanClass = CacheExpiry.class, initAfter = DbService.class, listenOn = CentralConfigKey.none)
public class CacheExpiryImpl implements CacheExpiry, ApplicationContextAware {
    private ArtifactoryApplicationContext context;

    private final Map<RepoType, Set<CacheExpiryChecker>> expirable = new EnumMap<>(RepoType.class);

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        context = ((ArtifactoryApplicationContext) applicationContext);
    }

    @Override
    public void init() {
        Set<CacheExpiryChecker> allExpiryChecker =
                new HashSet<>(context.beansForType(CacheExpiryChecker.class).values());
        allExpiryChecker.forEach(this::addCheckToType);
        log.debug("Loaded expirables: {}", expirable);
    }

    private void addCheckToType(CacheExpiryChecker checker) {
        RepoType[] types = checker.getTypes();
        if (types != null) {
            for (RepoType repoType : types) {
                expirable.computeIfAbsent(repoType, r -> new HashSet<>()).add(checker);
            }
        }
    }

    @Override
    public boolean isExpirable(RepoType repoType, String repoKey, @Nonnull String path) {
        return check(path, repoType, checker -> checker.isExpirable(repoType, repoKey, path),
                () -> String.format("Repo %s of type (%s) isExpirable match for path %s ",
                        repoKey, repoType, path));
    }

    @Override
    public boolean isMetadata(RepoType repoType, String repoKey, @Nonnull String path) {
        return check(path, repoType, checker -> checker.isMetadata(repoType, repoKey, path),
                () -> String.format("Repo %s of type (%s) isMetadata match for path %s ",
                        repoKey, repoType, path));
    }

    @Override
    public String getMetadataArtifactType(@Nonnull RepoType repoType, @Nonnull String repoKey, @Nonnull String path) {
        Set<CacheExpiryChecker> checkers = this.expirable.get(repoType);
        if (checkers != null) {
            for (MetaDataChecker checker : checkers) {
                String metadataArtifactType = checker.getMetadataArtifactType(repoType, repoKey, path);
                if (metadataArtifactType != null) {
                    return metadataArtifactType;
                }
            }
        }
        return null;
    }

    @Override
    public boolean isLocalGenerated(@Nonnull RepoType repoType, @Nonnull String repoKey, @Nonnull String path) {
        return check(path, repoType, checker -> checker.isLocalGenerated(repoType, repoKey, path),
                () -> String.format("Repo %s of type (%s) isLocalGenerated match for path %s",
                        repoKey, repoType, path));
    }

    @Override
    public boolean shouldExcludePropertyFromMirroring(@Nonnull RepoType repoType, @Nonnull String propertyKey) {
        return isApplicationServiceProperty(propertyKey) || check(propertyKey, repoType,
                checker -> checker.shouldExcludePropertyFromMirroring(repoType, propertyKey),
                () -> String.format("Property %s:%s should be excluded from mirroring",
                        repoType, propertyKey));
    }

    @Override
    public boolean shouldExcludePropertyFromMirroring(@Nonnull String repoKey, @Nonnull String propertyKey) {
        RepoConfig repoConfig = context.getRepositoryService().repoConfigByKey(repoKey);
        return repoConfig == null || shouldExcludePropertyFromMirroring(repoConfig.getPackageType(), propertyKey);
    }

    @Override
    public Set<String> excludePropertyValueFromMirroring(@Nonnull RepoType repoType,
            @Nonnull String propertyKey,
            Set<String> values) {
        Set<String> filteredValues = new HashSet<>(values);
        if (StringUtils.isNotBlank(propertyKey)) {
            Set<CacheExpiryChecker> expirables = expirable.get(repoType);
            if (expirables != null) {
                for (CacheExpiryChecker cacheExpirable : expirables) {
                    log.debug("cacheExpirable: {}, values: {} and propertyKey: {} ",
                            cacheExpirable.getClass().getSimpleName(), values, propertyKey);
                    log.debug("filteredValues before filtering: {}", filteredValues);
                    filteredValues =
                            cacheExpirable.excludePropertyValueFromMirroring(propertyKey, filteredValues,
                                    repoType);
                    log.debug("filteredValues after filtering: {}", filteredValues);
                }
            }
        }
        return filteredValues;
    }

    private boolean check(String value, RepoType repoType, Predicate<CacheExpiryChecker> checkFunction,
            Supplier<String> msg) {
        if (StringUtils.isNotBlank(value)) {
            Set<CacheExpiryChecker> expirables = expirable.get(repoType);
            if (expirables != null) {
                for (CacheExpiryChecker cacheExpirable : expirables) {
                    if (checkFunction.test(cacheExpirable)) {
                        if (log.isTraceEnabled()) {
                            log.trace("{} {}", cacheExpirable.getClass().getSimpleName(), msg.get());
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check if the property is an application service property and should be excluded from mirroring.
     * This logic is relevant to all repo types.
     * @param propertyKey The key of the property to check.
     */
    private boolean isApplicationServiceProperty(@Nonnull String propertyKey) {
        boolean isApplicationServiceProperty = APPLICATION_SERVICE_ID_PROPERTY_NAME.equals(propertyKey);
        if (isApplicationServiceProperty) {
            log.debug("Property {} is an application service property and should be exclude from mirroring",
                    propertyKey);
        }
        return isApplicationServiceProperty;
    }
}
