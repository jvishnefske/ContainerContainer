package org.artifactory.lifecycle.converter.postinit.v105;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.config.adapter.ArtifactoryConfigurationAdapter;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.converter.postinit.PostInitConverter;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.CompoundVersionDetails;

import java.io.File;

import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7490m001;

/**
 * Since we no longer encrypt the license file (See {@link ArtifactoryConfigurationAdapter#initSharedConfigs()}), we
 * need to modify the file after init so that it will be written to the configs table <b>decrypted</b>. In case other
 * node (not upgraded yet) is writing it back to the configs table encrypted, this will make sure the last node in
 * the cluster will write it decrypted after upgrade.
 *
 * @author Andrey Tashlitsky
 */
@Slf4j
public class UpdateLicenseFileLastModifiedConverter extends PostInitConverter {

    public UpdateLicenseFileLastModifiedConverter(ArtifactoryServiceVersion version) {
        super(version, version);
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        File licenseFile = ArtifactoryHome.get().getLicenseFile();
        if (licenseFile.exists() && licenseFile.canWrite()) {
            long now = System.currentTimeMillis();
            boolean success = licenseFile.setLastModified(now);
            if (success) {
                log.info("Successfully updated the license file 'last modified' to {}.", now);
            }
        }
    }

    @Override
    public boolean isInterested(CompoundVersionDetails source, CompoundVersionDetails target) {
        return source.getVersion().before(v7490m001.get()) && target.getVersion().afterOrEqual(v7490m001.get());
    }

    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) throws ConverterPreconditionException {
        // noop
    }
}
