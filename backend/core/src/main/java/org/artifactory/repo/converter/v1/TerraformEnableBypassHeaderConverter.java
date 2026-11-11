package org.artifactory.repo.converter.v1;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.converter.RepositoriesConverter;

import java.util.Map;

import static org.artifactory.util.PackageTypeConfigFields.TERRAFORM_REGISTRY_URL;

/**
 *  Converter for terraform remote repositories.
 *  If the registry url points to terraform registry it will bypass head requests.
 *  For more info see  <a href="https://jfrog-int.atlassian.net/browse/RTDEV-29611">RTDEV-29611</a>
 *  @author Yuval Ohayon
 */
public class TerraformEnableBypassHeaderConverter implements RepositoriesConverter {

    public static final String REPO_TYPE_CONFIG_KEY = "repoTypeConfig";
    public static final String TYPE_KEY = "type";
    public static final String PACKAGE_TYPE_KEY = "packageType";
    public static final String PACKAGE_TYPE_CONFIG_KEY = "packageTypeConfig";
    public static final String TERRAFORM_REGISTRY_URL_KEY = "terraformRegistryUrl";
    public static final String BYPASS_HEAD_REQUESTS_KEY = "bypassHeadRequests";

    @Override
    public void convert(Map<String, Object> repositoryConfigMap) {
        String type = (String) repositoryConfigMap.get(TYPE_KEY);
        String packageType = (String) repositoryConfigMap.get(PACKAGE_TYPE_KEY);
        Map<String, Object> packageTypeConfig = deserializeMap(repositoryConfigMap.get(PACKAGE_TYPE_CONFIG_KEY));
        String terraformRegistryUrl = (String) packageTypeConfig.get(TERRAFORM_REGISTRY_URL_KEY);

        if (RepoType.Terraform.getType().equals(packageType) && RepoDetailsType.REMOTE.getConfigName().equals(type) &&
                StringUtils.isNotBlank(terraformRegistryUrl) &&
                terraformRegistryUrl.startsWith(TERRAFORM_REGISTRY_URL.getDefaultValue())) {
            Map<String, Object> repoTypeConfig = deserializeMap(repositoryConfigMap.get(REPO_TYPE_CONFIG_KEY));
            repoTypeConfig.put(BYPASS_HEAD_REQUESTS_KEY, true);
            repositoryConfigMap.put(REPO_TYPE_CONFIG_KEY, repoTypeConfig);
        }
    }
}