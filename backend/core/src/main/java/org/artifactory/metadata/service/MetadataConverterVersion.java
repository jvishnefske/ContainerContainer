package org.artifactory.metadata.service;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;

import java.util.Arrays;
import java.util.List;

/**
 * @author Uriah Levy
 */
@Getter
@Slf4j
public enum MetadataConverterVersion {

    v1(ArtifactoryServiceVersionProvider.v711m001.get(),
            new MetadataReindexConverter(Sets.newHashSet(RepoType.YUM, RepoType.Maven), true),
            new MetadataReindexConverter(
                    Sets.newHashSet(RepoType.Pypi, RepoType.Bower, RepoType.Chef, RepoType.CocoaPods,
                            RepoType.CRAN, RepoType.Gradle), false)),
    v2(ArtifactoryServiceVersionProvider.v730m001.get(),
            new MetadataReindexConverter(Sets.newHashSet(RepoType.Gradle, RepoType.SBT), true)),
    v3(ArtifactoryServiceVersionProvider.v7710.get(),
            new MetadataReindexConverter(Sets.newHashSet(RepoType.Conda), true)),
    v4(ArtifactoryServiceVersionProvider.v7910.get(),
       new MetadataReindexConverter(Sets.newHashSet(RepoType.P2), false));

    private final ArtifactoryServiceVersion version;
    private final MetadataConverter[] converters;

    MetadataConverterVersion(ArtifactoryServiceVersion version, MetadataConverter... converters) {
        this.version = version;
        this.converters = converters;
    }

    public static void convert(ArtifactoryServiceVersion fromVersion) {
        List<MetadataConverter> converters = Lists.newArrayList();
        for (MetadataConverterVersion metadataConverterVersion : MetadataConverterVersion.values()) {
            if (metadataConverterVersion.version.after(fromVersion)) {
                converters.addAll(Arrays.asList(metadataConverterVersion.getConverters()));
            }
        }

        if (converters.isEmpty()) {
            log.debug("No Metadata converters found between version {} and {}", fromVersion,
                    ArtifactoryServiceVersion.getCurrent());
        } else {
            log.info("Starting Metadata conversion from {}({}) to {}({})", fromVersion.getVersion(),
                    fromVersion.getRevision(),
                    ArtifactoryServiceVersion.getCurrent().getVersion(),
                    ArtifactoryServiceVersion.getCurrent().getRevision());
            for (MetadataConverter converter : converters) {
                if (converter.isInterested(fromVersion)) {
                    converter.convert();
                } else {
                    log.info("Not running Metadata Server converter '{}' (converter criteria not met)",
                            converter.getClass().getSimpleName());
                }
            }
            log.info("Finished Metadata conversion from {}({}) to {}({})", fromVersion.getVersion(),
                    fromVersion.getRevision(),
                    ArtifactoryServiceVersion.getCurrent().getVersion(),
                    ArtifactoryServiceVersion.getCurrent().getRevision());
        }
    }

}