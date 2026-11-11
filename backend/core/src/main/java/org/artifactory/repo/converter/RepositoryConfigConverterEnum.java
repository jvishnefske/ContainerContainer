package org.artifactory.repo.converter;

import com.google.common.collect.Lists;
import org.artifactory.repo.converter.v1.TerraformEnableBypassHeaderConverter;
import org.artifactory.repo.converter.v2.DownloadRedirectConfigPopulateConverter;
import org.artifactory.repo.converter.v3.ExampleRepoLayoutConverter;
import org.artifactory.repo.converter.v4.SkillsRepoLayoutConverter;
import org.artifactory.repo.converter.v5.AgentPluginsRepoLayoutConverter;
import org.artifactory.repo.converter.v6.AgentPackagesRepoLayoutConverter;

import java.util.Arrays;
import java.util.List;

/**
 * @author Tamar Rosen
 */
public enum RepositoryConfigConverterEnum {
    V1(1, new TerraformEnableBypassHeaderConverter()),
    V2(2, new DownloadRedirectConfigPopulateConverter()),
    V3(3, new ExampleRepoLayoutConverter()),
    V4(4, new SkillsRepoLayoutConverter()),
    V5(5, new AgentPluginsRepoLayoutConverter()),
    V6(6, new AgentPackagesRepoLayoutConverter());
    private long version;
    private final RepositoriesConverter[] repositoriesConverter;

    RepositoryConfigConverterEnum(long version, RepositoriesConverter... repositoriesConverters) {
        this.version = version;
        this.repositoriesConverter = repositoriesConverters;
    }

    public static List<RepositoriesConverter> getRelevantConverters(long fromVersion) {
        List<RepositoriesConverter> converters = Lists.newArrayList();
        for (RepositoryConfigConverterEnum converterVersion : RepositoryConfigConverterEnum.values()) {
            if (converterVersion.version > fromVersion) {
                converters.addAll(Arrays.asList(converterVersion.getRepositoriesConverter()));
            }
        }
        return converters;
    }

    public RepositoriesConverter[] getRepositoriesConverter() {
        return repositoriesConverter;
    }
}
