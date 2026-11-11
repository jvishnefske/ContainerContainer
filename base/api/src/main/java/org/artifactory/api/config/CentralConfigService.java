/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.api.config;

import org.apache.commons.lang3.builder.DiffResult;
import org.artifactory.descriptor.DescriptorAware;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.jfrog.common.config.diff.DataDiff;
import org.jfrog.config.validation.ConfigValidation;
import org.joda.time.format.DateTimeFormatter;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * User: freds Date: Aug 3, 2008 Time: 6:22:39 PM
 */
public interface CentralConfigService extends DescriptorAware<CentralConfigDescriptor>, ImportableExportable {
    DateTimeFormatter getDateFormatter();

    /**
     * This method saves the new descriptor and reloads, if no arguments are passed, the validation
     * is expected to have happened before, otherwise the validation will happen before the save
     *
     * @param descriptor - The mutable descriptor to be saved
     * @param changed    - A list of arguments that need to be validated(descriptors, models etc.)
     */
    void saveEditedDescriptorAndReload(MutableCentralConfigDescriptor descriptor, Object... changed);

    /**
     * Reload configuration from storage
     *
     * @return {@code true} if succeeded reloading
     */
    void reloadConfiguration();

    void reloadConfigurationLazy();

    String getServerName();

    /**
     * Formats the given milliseconds date with the date format configured in the central config.
     *
     * @param date Date in milliseconds
     * @return Formatted date string
     */
    String format(long date);

    VersionInfo getServiceVersionInfo();

    VersionInfo getProductVersionInfo();

    /**
     * @return A mutable COPY of the central config descriptor.
     */
    MutableCentralConfigDescriptor getMutableDescriptor();

    String getConfigXml();

    void setConfigXml(String xmlConfig);

    /**
     * Saves passed configuration.
     *
     * @param xmlConfig    - configuration content.
     * @param isApiRequest - In case 'true', a more strict validations will be applied.
     */
    void setConfigXml(String xmlConfig, boolean isApiRequest);

    void setLogo(File logo) throws IOException;

    DiffResult findDiff(CentralConfigDescriptor oldDescriptor, CentralConfigDescriptor newDescriptor);

    /**
     * Merges the changes represented by the {@param diffs} into the central configuration descriptor and saves it.
     * Optionally, validates the configuration before saving.
     *
     * @param diffs          A collection of dataDiff to be merged.
     * @param shouldValidate Indicate whether to perform validation on the descriptor
     *                       configuration values before saving the changes. If true, validation is
     *                       performed; otherwise, it is skipped.
     * @see ConfigValidation
     */
    void mergeAndSaveNewData(Collection<DataDiff<?>> diffs, boolean shouldValidate);

    BaseUrlModel getPlatformBaseUrl();

    /**
     * @return the Artifactory System Configuration
     */
    ArtifactorySystemConfig getSystemConfiguration();

    Integer getListeningPort();

    boolean isContextCreated();

    /**
     * @return the Proxy Configuration for specified Service
     */
    ProxyDescriptor getServiceProxyByKey(String serviceKey);
}