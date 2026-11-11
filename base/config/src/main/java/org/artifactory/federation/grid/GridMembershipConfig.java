package org.artifactory.federation.grid;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.jfrog.access.client.platformconfig.ConfigurationModel;

/**
 * JFConfig wire model for the {@code grid.topology.membership} key.
 * <p>
 * Fetched via {@code AccessService.getPlatformConfigClient().getConfig(...)}. Maps
 * snake_case JSON fields to camelCase Java properties.
 *
 * @author Tomer Ben Kereth
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GridMembershipConfig implements ConfigurationModel {
    private String role;
    private String gridKey;
    private String jpdId;
    private String primaryJpdId;
    private String primaryJpdUrl;
}
