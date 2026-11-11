package org.artifactory.ui.rest.service.jfevent;

import lombok.NonNull;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.jfevent.JfEventAddon;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Yevdo Abramov
 * Created on 24/05/2020
 */
@Component
public class GetDomainsListJfEventService implements RestService {

    private final AddonsManager addonsManager;

    @Autowired
    public GetDomainsListJfEventService(@NonNull AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Override
    public void execute(@NonNull ArtifactoryRestRequest request, @NonNull RestResponse response) {
        JfEventAddon jfEventAddon = addonsManager.addonByType(JfEventAddon.class);
        response.iModel(jfEventAddon.getDomainsList());
    }
}
