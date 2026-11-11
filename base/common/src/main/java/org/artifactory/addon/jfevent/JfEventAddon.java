package org.artifactory.addon.jfevent;

import lombok.NonNull;
import org.artifactory.common.addon.Addon;

import java.util.Collections;
import java.util.List;

/**
 * @author Yevdo Abramov
 * Created on 22/04/2020
 */
public interface JfEventAddon extends Addon {

    /**
     * Get the supported domains (repository/build/docker etc) by artifactory as part of the jf-events services
     *
     * @return the list of domains info
     */
    @NonNull
    default List<JfEventDomainInfo> getDomainsList() {
        return Collections.emptyList();
    }
}
