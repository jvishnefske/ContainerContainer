package org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator;

import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.common.ConstantValues.remoteRepoCreateValidationEnabledPackages;
import static org.artifactory.common.ConstantValues.remoteRepoUpdateValidationEnabledPackages;

/**
 * @author arkojd
 */
@Component
public class RemoteRepoPackageValidator {

    private final Map<RepoType,RemoteRepoPackageSpecificValidator> packageTypeToValidatorMap;

    public RemoteRepoPackageValidator() {
        Collection<RemoteRepoPackageSpecificValidator> remoteRepoPackageSpecificValidators =
                ContextHelper.get().beansForType(RemoteRepoPackageSpecificValidator.class).values();
        packageTypeToValidatorMap = new EnumMap<>(RepoType.class);
        for (RemoteRepoPackageSpecificValidator validator : remoteRepoPackageSpecificValidators) {
            packageTypeToValidatorMap.put(validator.getRepoType(), validator);
        }
    }

    public void validate(RemoteRepoConfig remoteRepoConfig, MutableCentralConfigDescriptor configDescriptor,
            RemoteRepositoryConfigModel model) throws RepoConfigException {
        RemoteRepoPackageSpecificValidator validator =
                packageTypeToValidatorMap.get(remoteRepoConfig.getPackageType());
        validator.validate(remoteRepoConfig, configDescriptor, model);
    }

    @Nonnull
    public List<String> getRemoteRepoCreateValidationEnabledPackages() {
        return Arrays.stream(remoteRepoCreateValidationEnabledPackages.getString().split(","))
                .map(StringUtils::trim)
                .filter(StringUtils::isNotBlank)
                .map(String::toLowerCase)
                .toList();
    }

    @Nonnull
    public List<String> getRemoteRepoUpdateValidationEnabledPackages() {
        return Arrays.stream(remoteRepoUpdateValidationEnabledPackages.getString().split(","))
                .map(StringUtils::trim)
                .filter(StringUtils::isNotBlank)
                .map(String::toLowerCase)
                .toList();
    }

}
