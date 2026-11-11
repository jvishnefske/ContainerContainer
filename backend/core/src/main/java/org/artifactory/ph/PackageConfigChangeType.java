package org.artifactory.ph;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.config.CentralConfigDescriptor;

public enum PackageConfigChangeType {
    BASE_URL {
        @Override
        boolean isChanged(CentralConfigDescriptor newDescriptor, CentralConfigDescriptor oldDescriptor) {
            return oldDescriptor != null && ((oldDescriptor.getUrlBase() != null &&
                    !oldDescriptor.getUrlBase().equals(newDescriptor.getUrlBase())) ||
                    (oldDescriptor.getUrlBase() == null && StringUtils.isNotBlank(newDescriptor.getUrlBase())));
        }
    };

    abstract boolean isChanged(CentralConfigDescriptor newDescriptor, CentralConfigDescriptor oldDescriptor);
}
