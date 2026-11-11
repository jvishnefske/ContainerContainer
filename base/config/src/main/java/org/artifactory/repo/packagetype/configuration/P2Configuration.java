package org.artifactory.repo.packagetype.configuration;

import lombok.Data;
import org.jfrog.common.config.validation.NotUnicode;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Tamar Rosen
 */
@Data
public class P2Configuration {
    private long modelVersion;
    private List<@NotUnicode String> urls = new ArrayList<>();
    private String p2OriginalUrl;
    private boolean forceP2Authentication;
}
