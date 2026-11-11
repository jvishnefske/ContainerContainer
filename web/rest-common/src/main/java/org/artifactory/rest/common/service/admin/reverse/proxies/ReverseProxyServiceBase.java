package org.artifactory.rest.common.service.admin.reverse.proxies;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.service.ConfigValidationService;

/**
 * @author Igor Usenko
 */
class ReverseProxyServiceBase {

    private final ConfigValidationService configValidationService;
    private final CentralConfigService centralConfigService;

    public ReverseProxyServiceBase(CentralConfigService centralConfigService,
            ConfigValidationService configValidationService) {
        this.centralConfigService = centralConfigService;
        this.configValidationService = configValidationService;
    }

    protected MutableCentralConfigDescriptor getDescriptor() {
        return centralConfigService.getMutableDescriptor();
    }

    protected void saveAndReload(MutableCentralConfigDescriptor config, ReverseProxyDescriptor proxy) {
        validate(proxy);
        centralConfigService.saveEditedDescriptorAndReload(config, proxy);
    }

    private void validate(ReverseProxyDescriptor proxy) {
        try {
            configValidationService.validate(proxy);
        } catch (IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

}
