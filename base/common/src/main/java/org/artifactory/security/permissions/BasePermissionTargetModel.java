package org.artifactory.security.permissions;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.artifactory.security.PermissionTarget.ANY_PATH;

/**
 * @author Omri Ziv
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
class BasePermissionTargetModel {

    // TODO - Properties representations not supported the same on Jersey REST And iTest.

    @JsonProperty("include-patterns")
    private List<String> includePatterns = Collections.singletonList(ANY_PATH);
    @JsonProperty("exclude-patterns")
    private List<String> excludePatterns = Collections.emptyList();
    private Set<String> repositories;

}
