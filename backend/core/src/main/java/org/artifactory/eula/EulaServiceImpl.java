package org.artifactory.eula;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.eula.EulaService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.eula.EulaDescriptor;
import org.artifactory.eula.exceptions.EulaException;
import org.artifactory.ui.rest.service.state.GlobalStateProviderService;
import org.joda.time.format.ISODateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

import static org.artifactory.addon.ArtifactoryRunningMode.JCR;

/**
 * @author Omri Ziv
 */
@Slf4j
@Component
public class EulaServiceImpl implements EulaService {

    private final CentralConfigService centralConfigService;
    private final AddonsManager addonsManager;
    private final GlobalStateProviderService globalStateProviderService;
    private static final String EULA_FILE = "jcr-eula.html";

    @Autowired
    public EulaServiceImpl(CentralConfigService centralConfigService, AddonsManager addonsManager,
            GlobalStateProviderService globalStateProviderService) {
        this.addonsManager = addonsManager;
        this.centralConfigService = centralConfigService;
        this.globalStateProviderService = globalStateProviderService;
    }

    @Override
    public boolean isRequired() {
        if (addonsManager.getArtifactoryRunningMode() == JCR) {
            CentralConfigDescriptor centralConfigDescriptor = centralConfigService.getDescriptor();
            EulaDescriptor eulaConfig = centralConfigDescriptor.getEulaConfig();
            if (eulaConfig == null) {
                return true;
            } else {
                return !eulaConfig.isAccepted();
            }
        }
        return false;
    }

    @Override
    public void accept() {
        MutableCentralConfigDescriptor mutableDescriptor = centralConfigService.getMutableDescriptor();
        EulaDescriptor eulaConfig = mutableDescriptor.getEulaConfig();
        if (eulaConfig == null || !eulaConfig.isAccepted()) {
            eulaConfig = new EulaDescriptor();
        }
        eulaConfig.setAccepted(true);
        eulaConfig.setAcceptDate(ISODateTimeFormat.dateTime().print(System.currentTimeMillis()));

        mutableDescriptor.setEulaConfig(eulaConfig);
        centralConfigService.saveEditedDescriptorAndReload(mutableDescriptor, eulaConfig);
        globalStateProviderService.invalidateCache();
    }

    @Override
    public byte[] getEulaFile() {
        try (InputStream eulaStream = fetchEulaStream()) {
            return IOUtils.toByteArray(eulaStream);
        } catch (IOException e) {
            log.error("Could not read Eula.");
            throw new EulaException("Could not read Eula.", e);
        }
    }

    private InputStream fetchEulaStream() {
        return this.getClass().getResourceAsStream("/META-INF/jcr/" + EULA_FILE);
    }

}
