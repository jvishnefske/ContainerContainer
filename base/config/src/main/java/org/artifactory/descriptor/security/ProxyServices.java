package org.artifactory.descriptor.security;

import com.google.common.collect.Sets;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jfrog.access.common.ServiceType;

import java.util.Set;

/**
 * @author Yuval Reches
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class ProxyServices {

    /**
     * Service type for Platform Federation. Not yet defined in {@link ServiceType}
     * (org.jfrog.access:access-common-api), so it is declared here as a literal until it is added upstream.
     */
    private static final String PLATFORM_FEDERATION = "jfpfed";

    /**
     * Service type for Grid. Not yet defined in {@link ServiceType} (org.jfrog.access:access-common-api),
     * so it is declared here as a literal until it is added upstream.
     */
    private static final String GRID = "jfgrd";

    /**
     * The valid services to be populated inside a {@link org.artifactory.descriptor.repo.ProxyDescriptor#services}
     */
    public static final Set<String> VALID_PROXY_SERVICES = Sets
            .newHashSet(ServiceType.ARTIFACTORY, ServiceType.XRAY, ServiceType.DISTRIBUTION,
                    ServiceType.MISSION_CONTROL, ServiceType.CATALOG, PLATFORM_FEDERATION, GRID);
}
