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

package org.artifactory.descriptor.security;

import org.artifactory.descriptor.Descriptor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import java.util.Objects;
import java.util.Optional;

/**
 * The password policy related settings.
 *
 * @author Yossi Shaul
 */
@XmlType(name = "PasswordSettingsType", namespace = Descriptor.NS)
@JsonIgnoreProperties(value = {"encryptionEnabled", "encryptionRequired"})
@GenerateDiffFunction
public class PasswordSettings implements Descriptor {

    @XmlElement(name = "expirationPolicy", required = false)
    private PasswordExpirationPolicy expirationPolicy = new PasswordExpirationPolicy();

    @XmlElement(name = "resetPolicy", required = false)
    private PasswordResetPolicy resetPolicy = new PasswordResetPolicy();

    public PasswordExpirationPolicy getExpirationPolicy() {
        return expirationPolicy;
    }

    public void setExpirationPolicy(PasswordExpirationPolicy expirationPolicy) {
        this.expirationPolicy = Optional.ofNullable(expirationPolicy).orElseGet(PasswordExpirationPolicy::new);
    }

    public PasswordResetPolicy getResetPolicy() {
        return resetPolicy;
    }

    public void setResetPolicy(PasswordResetPolicy resetPolicy) {
        this.resetPolicy = Optional.ofNullable(resetPolicy).orElseGet(PasswordResetPolicy::new);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PasswordSettings that = (PasswordSettings) o;
        return expirationPolicy.equals(that.expirationPolicy) && resetPolicy.equals(that.resetPolicy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expirationPolicy, resetPolicy);
    }

    @Override
    public String toString() {
        return "expirationPolicy: " +
                expirationPolicy +
                "resetPolicy: " +
                resetPolicy;
    }
}