package com.jfrog.build.acl.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jfrog.build.api.Build;

/**
 * @author BarakH
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class BuildWrapper {

    private Build build;
    private String buildRepo;

    public boolean isBuildExist() {
        return build != null && buildRepo != null;
    }

    public String getName() {
        return build.getName();
    }

    public String getNumber() {
        return build.getNumber();
    }

    public String getStarted() {
        return build.getStarted();
    }
}
