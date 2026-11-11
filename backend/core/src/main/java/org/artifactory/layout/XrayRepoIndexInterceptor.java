package org.artifactory.layout;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class XrayRepoIndexInterceptor implements RepoConfigChangeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(XrayRepoIndexInterceptor.class);

    AddonsManager addonsManager;

    @Autowired
    public XrayRepoIndexInterceptor(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Override
    public void onAfterSave(Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges) {
        log.debug("Xray index interceptor is running");
        XrayAddon xrayAddon = addonsManager.addonByType(XrayAddon.class);
        if (xrayAddon.isXrayEnabled()) {
            log.debug("Xray enabled, searching for diff");
            xrayAddon.reportReposIndexDiff(repoConfigChanges);
        }
    }
}
