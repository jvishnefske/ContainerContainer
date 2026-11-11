package org.artifactory.repo.validator.virtual;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.exception.InvalidOperationException;
import org.artifactory.model.VirtualRepoConfig;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * Validates the package-specific configurations of virtual repositories.
 * @author Einas Ashkar
 */
@Component
public class VirtualRepoPackageValidator {
    private final Map<RepoType, VirtualRepoPackageSpecificValidator> packageTypeToValidatorMap;

    public VirtualRepoPackageValidator() {
        Collection<VirtualRepoPackageSpecificValidator> virtualRepoPackageSpecificValidators =
                ContextHelper.get().beansForType(VirtualRepoPackageSpecificValidator.class).values();
        packageTypeToValidatorMap = new EnumMap<>(RepoType.class);
        for (VirtualRepoPackageSpecificValidator validator : virtualRepoPackageSpecificValidators) {
            packageTypeToValidatorMap.put(validator.getRepoType(), validator);
        }
    }

    public void validate(VirtualRepoConfig virtualRepoConfig)
            throws InvalidOperationException {
        VirtualRepoPackageSpecificValidator validator =
                packageTypeToValidatorMap.get(virtualRepoConfig.getPackageType());
        if (validator == null) {
            return;
        }
        validator.validate(virtualRepoConfig);
    }
}
