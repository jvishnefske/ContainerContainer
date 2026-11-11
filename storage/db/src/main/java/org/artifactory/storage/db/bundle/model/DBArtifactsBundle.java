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

package org.artifactory.storage.db.bundle.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.api.rest.distribution.bundle.models.ReleaseBundleModel;
import org.artifactory.bundle.BundleTransactionStatus;
import org.artifactory.bundle.BundleType;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import jakarta.annotation.Nonnull;
import java.util.regex.Pattern;

import static org.jfrog.common.ArgUtils.requireMatches;
import static org.jfrog.common.ArgUtils.requireNonBlank;
import static org.jfrog.common.ArgUtils.requireNonNull;

/**
 * @author Tomer Mayost
 */
@Data
@NoArgsConstructor
public class DBArtifactsBundle {
    long id;
    String name;
    String version;
    BundleTransactionStatus status;
    DateTime dateCreated;
    DateTime dateDistributed;
    String signature;
    BundleType type;
    String storingRepo;
    Boolean keep;
    String sourceServiceId;

    private static final Pattern namePattern = Pattern.compile("^[a-zA-Z\\d]+[a-zA-Z\\d\\-_:.]*$");
    private static final Pattern versionPattern = Pattern.compile("^[a-zA-Z\\d]+[a-zA-Z\\d\\-_:.+]*$");
    private static final String INVALID_NAME_PATTERN_MESSAGE =
            " can only contain letters, numbers and the following characters: .-_:";
    private static final String INVALID_VERSION_PATTERN_MESSAGE =
            " can only contain letters, numbers and the following characters: .-_:+";

    public static DBArtifactsBundle buildFrom(@Nonnull ReleaseBundleModel releaseBundleModel) {
        DBArtifactsBundle artifactsBundle = new DBArtifactsBundle();
        artifactsBundle.setName(releaseBundleModel.getName());
        artifactsBundle.setVersion(releaseBundleModel.getVersion());
        artifactsBundle.setDateCreated(parseDateCreated(releaseBundleModel.getCreated()));
        artifactsBundle.setStatus(releaseBundleModel.getStatus() == null ? BundleTransactionStatus.INPROGRESS :
                BundleTransactionStatus.valueOf(releaseBundleModel.getStatus()));
        artifactsBundle.setSignature(releaseBundleModel.getSignature());
        artifactsBundle.setType(releaseBundleModel.getType() == null ? BundleType.TARGET :
                BundleType.valueOf(releaseBundleModel.getType()));
        artifactsBundle.setStoringRepo(releaseBundleModel.getStoringRepo());
        artifactsBundle.setKeep(releaseBundleModel.getKeep());
        artifactsBundle.setSourceServiceId(releaseBundleModel.getSourceServiceId());
        artifactsBundle.validate();
        return artifactsBundle;
    }

    private static DateTime parseDateCreated(@Nonnull String date) {
        return getDateTimeFormatter().parseDateTime(date);
    }

    private static DateTimeFormatter getDateTimeFormatter() {
        return ISODateTimeFormat.dateTime().withZoneUTC();
    }

    public void validate() {
        validate(name, version, dateCreated, signature);
    }

    public void validate(String name, String version, DateTime dateCreated, String signature) {
        this.name = requireMatches(name, namePattern, "Bundle name" + INVALID_NAME_PATTERN_MESSAGE);
        this.version = requireMatches(version, versionPattern, "Bundle version" + INVALID_VERSION_PATTERN_MESSAGE);
        this.dateCreated = requireNonNull(dateCreated, "Date created must not be null");
        this.signature = requireNonBlank(signature, "Signature is mandatory");
    }
}
