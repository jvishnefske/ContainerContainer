package org.artifactory.repo.packagetype;

import lombok.Data;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Tamar Rosen
 */
@Data
public class ExternalDependenciesConfig {
    private boolean enabled = false;
    @Nullable
    private List<@NotUnicode String> patterns = new ArrayList<>();
    private String remoteRepoRef;
}
