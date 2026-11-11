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

package org.artifactory.converter.postinit;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.postinit.v103.GoCacheRemoteRepositoryConverter;
import org.artifactory.converter.postinit.v103.GoVirtualRepositoryWhitelistConverter;
import org.artifactory.lifecycle.converter.postinit.v100.DockerInvalidImageLocationConverter;
import org.artifactory.lifecycle.converter.postinit.v101.ConanRepoPathConverter;
import org.artifactory.lifecycle.converter.postinit.v102.RemoteRepoBypassHeadSystemPropsConverter;
import org.artifactory.lifecycle.converter.postinit.v104.UsageRepositoryCleanupConverter;
import org.artifactory.lifecycle.converter.postinit.v105.NpmDeprecateConverter;
import org.artifactory.lifecycle.converter.postinit.v105.UpdateLicenseFileLastModifiedConverter;
import org.artifactory.lifecycle.converter.postinit.v107.FederatedLocalMemberPostInitConverter;
import org.artifactory.lifecycle.converter.postinit.v108.UpdatePlatformConfigPropertiesConverter;
import org.artifactory.lifecycle.converter.postinit.v109.SshPublicKeyNameConverter;
import org.artifactory.lifecycle.converter.postinit.v110.DeleteSshPublicOldNameConverter;
import org.artifactory.lifecycle.converter.postinit.v111.DisableListingRemoteFolderItemsPostInitConverter;
import org.artifactory.lifecycle.converter.postinit.v112.RestoreObservabilityReposToDefaultConfigConverter;
import org.artifactory.lifecycle.converter.postinit.v113.FrogMLRepoConverter;
import org.artifactory.lifecycle.converter.postinit.v114.FederatedRemoteMembersConverter;
import org.artifactory.lifecycle.converter.postinit.v115.HuggingFaceEmptyRepoLayoutConverter;
import org.artifactory.lifecycle.converter.postinit.v116.CreateMissingRepoRootPathsPostInitConverter;
import org.artifactory.lifecycle.converter.postinit.v118.NugetChocolateyCacheRetrievalPostInitConverter;
import org.artifactory.lifecycle.converter.postinit.v119.FederatedBlobEnrichmentPostInitConverter;
import org.artifactory.version.CompoundVersionDetails;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.artifactory.version.ArtifactoryServiceVersionProvider.v4110;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v4121;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v4150;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v560m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v6100;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v640m007;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v71040;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v71050;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v71110;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v71200;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v71371;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v71390;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v71480;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v71500;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v71600;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7385m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7490m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7520m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7590m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7940;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7990;

/**
 * Converters that will run post-init (but after all conversions)
 *
 * @author Nadav Yogev
 */
public enum PostInitVersions {
    v100(new DockerInvalidImageLocationConverter(v4110.get(), v4121.get())),
    v101(new ConanRepoPathConverter(v4150.get(), v560m001.get())),
    //This converter is not range-bound, it responds to any version above 6.4.0 (including)
    v102(new RemoteRepoBypassHeadSystemPropsConverter(v640m007.get(), v640m007.get())),
    v103(new GoCacheRemoteRepositoryConverter(v6100.get()), new GoVirtualRepositoryWhitelistConverter(v6100.get())),
    v104(new UsageRepositoryCleanupConverter(v7385m001.get(), v7385m001.get())),
    v105(new UpdateLicenseFileLastModifiedConverter(v7490m001.get()), new NpmDeprecateConverter(v7490m001.get())),
    v107(new FederatedLocalMemberPostInitConverter(v7520m001.get(), v7520m001.get())),
    v108(new UpdatePlatformConfigPropertiesConverter(v7590m001.get(), v7590m001.get())),
    v109(new SshPublicKeyNameConverter(v7590m001.get(), v7590m001.get())),
    v110(new DeleteSshPublicOldNameConverter(v7590m001.get(), v7590m001.get())),
    v111(new DisableListingRemoteFolderItemsPostInitConverter(v7940.get(), v7940.get())),
    v112(new RestoreObservabilityReposToDefaultConfigConverter(v7990.get(), v7990.get())),
    v113(new FrogMLRepoConverter(v71040.get())),
    v114(new FederatedRemoteMembersConverter(v71050.get())),
    v115(new HuggingFaceEmptyRepoLayoutConverter(v71110.get())),
    v116(new CreateMissingRepoRootPathsPostInitConverter(v71390.get())),
    v117(new FederatedLocalMemberPostInitConverter(v71371.get(), v71480.get())),
    v118(new NugetChocolateyCacheRetrievalPostInitConverter(v71200.get(), v71500.get())),
    v119(new FederatedBlobEnrichmentPostInitConverter(v71600.get())),
    ;

    private final PostInitConverter[] converters;

    /**
     * @param converters A list of converters to use to move from <b>this</b> config version to the <b>next</b> config
     *                   version
     */
    PostInitVersions(PostInitConverter... converters) {
        this.converters = converters;
    }

    public static PostInitVersions getCurrent() {
        PostInitVersions[] versions = PostInitVersions.values();
        return versions[versions.length - 1];
    }

    public void convert(CompoundVersionDetails from, CompoundVersionDetails until) {
        Arrays.stream(PostInitVersions.values())
                .flatMap(version -> Stream.of(version.getConverters()))
                .filter(converter -> converter.isInterested(from, until))
                .forEach(converter -> converter.convert(from, until));
    }

    /**
     * Assert that all the post init converters that should run have all their preconditions met before running them
     */
    public void assertPreConditions(ArtifactoryHome home, CompoundVersionDetails from, CompoundVersionDetails until) {
        Arrays.stream(PostInitVersions.values())
                .flatMap(version -> Stream.of(version.getConverters()))
                .filter(converter -> converter.isInterested(from, until))
                .forEach(converter -> converter.assertConversionPrecondition(home, from, until));
    }

    public PostInitConverter[] getConverters() {
        return converters;
    }
}
