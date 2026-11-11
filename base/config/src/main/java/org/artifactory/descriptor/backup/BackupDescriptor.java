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

package org.artifactory.descriptor.backup;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlID;
import jakarta.xml.bind.annotation.XmlType;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.descriptor.TaskDescriptor;
import org.jfrog.common.config.diff.DiffAtomic;
import org.jfrog.common.config.diff.DiffIgnore;
import org.jfrog.common.config.diff.DiffKey;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Fred Simon
 */
@XmlType(name = "BackupType", propOrder = {"key", "enabled", "dir", "cronExp", "retentionPeriodHours", "createArchive",
        "excludedRepositories", "sendMailOnError", "excludeNewRepositories", "precalculate", "exportMissionControl"},
        namespace = Descriptor.NS)
@GenerateDiffFunction
public class BackupDescriptor implements TaskDescriptor {

    private static final long serialVersionUID = 1L;

    static final int DEFAULT_RETENTION_PERIOD_HOURS = 168;//7 days

    public static final String BACKUP_PATH_NOT_IN_ALLOWLIST =
            "Backup destination is not within the configured allowed backup paths";

    @XmlID
    @XmlElement(required = true)
    @DiffKey
    @NotUnicode
    private String key;

    @XmlElement(defaultValue = "true")
    private boolean enabled = true;

    @XmlElement(required = true)
    @NotUnicode
    private String cronExp;

    @DiffIgnore
    private File dir;

    @XmlElement(defaultValue = DEFAULT_RETENTION_PERIOD_HOURS + "")
    private int retentionPeriodHours = DEFAULT_RETENTION_PERIOD_HOURS;

    @XmlElement(defaultValue = "false")
    private boolean createArchive;

    @XmlElement(defaultValue = "false")
    private boolean precalculate;

    @XmlElementWrapper(name = "excludedRepositories")
    @XmlElement(name = "repositoryRef", required = false)
    @DiffAtomic
    private List<@NotUnicode String> excludedRepositories = new ArrayList<>();

    @XmlElement(defaultValue = "true")
    private boolean sendMailOnError = true;

    @XmlElement(defaultValue = "false")
    private boolean excludeNewRepositories = false;

    @XmlElement(defaultValue = "false")
    private boolean exportMissionControl = false;

    public boolean isExportMissionControl() {
        return exportMissionControl;
    }

    public void setExportMissionControl(boolean exportMissionControl) {
        this.exportMissionControl = exportMissionControl;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public File getDir() {
        return dir;
    }

    public void setDir(File dir) {
        this.dir = dir;
    }

    public String getCronExp() {
        return cronExp;
    }

    public void setCronExp(String cronExp) {
        this.cronExp = cronExp;
    }

    public int getRetentionPeriodHours() {
        return retentionPeriodHours;
    }

    public void setRetentionPeriodHours(int retentionPeriodHours) {
        this.retentionPeriodHours = retentionPeriodHours;
    }

    public boolean isCreateArchive() {
        return createArchive;
    }

    public void setCreateArchive(boolean createArchive) {
        this.createArchive = createArchive;
    }

    @SuppressWarnings({"unchecked"})
    public List<String> getExcludedRepositories() {
        return excludedRepositories;
    }

    public void setExcludedRepositories(List<String> excludedRepositories) {
        this.excludedRepositories = Optional.ofNullable(excludedRepositories).orElseGet(ArrayList::new);
    }

    public boolean removeExcludedRepository(String realRepoKey) {
        return excludedRepositories.remove(realRepoKey);
    }

    public boolean addExcludedRepository(String realRepoKey) {
        return excludeNewRepositories && excludedRepositories.add(realRepoKey);
    }

    public boolean isIncremental() {
        return (retentionPeriodHours <= 0) && !isCreateArchive();
    }

    public boolean isSendMailOnError() {
        return sendMailOnError;
    }

    public void setSendMailOnError(boolean sendMailOnError) {
        this.sendMailOnError = sendMailOnError;
    }

    public boolean isExcludeNewRepositories() {
        return excludeNewRepositories;
    }

    public void setExcludeNewRepositories(boolean excludeNewRepositories) {
        this.excludeNewRepositories = excludeNewRepositories;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BackupDescriptor)) {
            return false;
        }

        BackupDescriptor that = (BackupDescriptor) o;

        return key != null ? key.equals(that.key) : that.key == null;

    }

    @Override
    public int hashCode() {
        return key != null ? key.hashCode() : 0;
    }

    @Override
    public boolean sameTaskDefinition(TaskDescriptor otherDescriptor) {
        if (otherDescriptor == null || !(otherDescriptor instanceof BackupDescriptor)) {
            throw new IllegalArgumentException("Cannot compare backup descriptor " + this + " with " + otherDescriptor);
        }
        BackupDescriptor backupDesc = (BackupDescriptor) otherDescriptor;
        return backupDesc.enabled == this.enabled &&
                StringUtils.equals(backupDesc.key, this.key) &&
                StringUtils.equals(backupDesc.cronExp, this.cronExp);
    }

    public boolean isPrecalculate() {
        return precalculate;
    }

    public void setPrecalculate(boolean precalculate) {
        this.precalculate = precalculate;
    }
}