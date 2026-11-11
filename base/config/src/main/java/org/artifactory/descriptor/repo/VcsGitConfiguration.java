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

package org.artifactory.descriptor.repo;

import org.artifactory.descriptor.Descriptor;
import org.artifactory.descriptor.repo.vcs.VcsGitProvider;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * @author Yoav Luft
 */
@XmlType(name = "VcsGitType", propOrder = {"provider", "downloadUrl"})
@GenerateDiffFunction
public class VcsGitConfiguration implements Descriptor {

    @XmlElement(name = "provider")
    private VcsGitProvider provider = VcsGitProvider.GITHUB;

    @XmlElement(name = "downloadUrl")
    @NotUnicode
    private String downloadUrl;

    public VcsGitConfiguration() {
    }

    public VcsGitConfiguration(VcsGitProvider provider) {
        this.provider = provider;
    }

    public VcsGitProvider getProvider() {
        return provider;
    }

    public void setProvider(VcsGitProvider provider) {
        this.provider = provider;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
}
