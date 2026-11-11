package org.artifactory.util;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;

import javax.annotation.Nullable;

import static org.artifactory.api.rest.constant.RestConstants.PATH_API;

@UtilityClass
public class PackageTypeUtil {

    private static final String DELIMITER = "/";
    private static final String PATH_API_WITH_DELIMITER = DELIMITER + PATH_API + DELIMITER;
    private static final String REMOTE = "remote";

    @Nullable
    public String extractPackageType(String requestUrl) {
        String packageType = StringUtils.substringBetween(requestUrl, PATH_API_WITH_DELIMITER, DELIMITER);

        // Only package-based APIs are supported
        RepoType repoType = RepoType.fromTypeWithNullable(packageType);

        if (repoType == null) {
            return null;
        }

        // Terraform is a special case, it has a nested type
        if (repoType.equals(RepoType.Terraform)) {
            String nestedType =
                    StringUtils.substringBetween(requestUrl, RepoType.Terraform.getType() + DELIMITER, DELIMITER);
            if (REMOTE.equals(nestedType)) {
                return RepoType.TerraformBackend.getType();
            }
        }
        return repoType.getType();
    }
}
