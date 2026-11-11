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

package org.artifactory.ui.rest.model.artifacts.search.packagesearch.criteria;

import com.google.common.base.Strings;
import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.mime.DebianNaming;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.search.AqlUISearchModel;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.result.AqlUISearchDockerV1ResultManipulator;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.result.AqlUISearchDummyResultManipulator;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.result.AqlUISearchNpmResultManipulator;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.result.AqlUISearchResultManipulator;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.result.ConanPackageSearchResultMerger;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.result.PackageSearchResultMerger;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.strategy.AqlUIDockerV1ImageSearchStrategy;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.strategy.AqlUIDockerV2ImageDigestSearchStrategy;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.strategy.AqlUIDockerV2ImageSearchStrategy;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.strategy.AqlUIDockerV2TagPathSearchStrategy;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.strategy.AqlUINpmChecksumSearchStrategy;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.strategy.AqlUINpmKeywordsSearchStrategy;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.strategy.AqlUINpmNameSearchStrategy;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.strategy.AqlUINpmScopeSearchStrategy;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.strategy.AqlUIPropertySearchStrategy;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.strategy.AqlUISearchStrategy;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemActualSha1;
import static org.artifactory.ui.rest.service.artifacts.search.packagesearch.result.DummyPackageSearchResultMerger.DUMMY_MERGER;

/**
 * Contains all available criteria specific to each package type the search supports
 *
 * @author Dan Feldman
 */
public enum PackageSearchCriteria {
    //Keep packages ordered alphabetically please!

    bowerName(() -> new PackageSearchCriterion(PackageSearchType.bower, "bower.name",
            new AqlUISearchModel("bowerName", "Name", "name", "Bower Package Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("bower.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    bowerVersion(() -> new PackageSearchCriterion(PackageSearchType.bower, "bower.version",
            new AqlUISearchModel("bowerVersion", "Version", "version", "Bower Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("bower.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    chefName(() -> new PackageSearchCriterion(PackageSearchType.chef, "chef.name",
            new AqlUISearchModel("chefName", "Name", "name", "Chef Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("chef.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    chefVersion(() -> new PackageSearchCriterion(PackageSearchType.chef, "chef.version",
            new AqlUISearchModel("chefVersion", "Version", "version", "Chef Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("chef.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    chefCategory(() -> new PackageSearchCriterion(PackageSearchType.chef, "chef.category",
            new AqlUISearchModel("chefCategory", "Category", "category", "Chef Category", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("chef.category",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    chefPlatform(() -> new PackageSearchCriterion(PackageSearchType.chef, "chef.platform",
            new AqlUISearchModel("chefPlatform", "Platform", "platform", "Chef Platform", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("chef.platform",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    cocoapodsName(() -> new PackageSearchCriterion(PackageSearchType.cocoapods, "pods.name",
            new AqlUISearchModel("cocoapodsName", "Name", "name", "CocoaPods Package Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("pods.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    cocoapodsVersion(() -> new PackageSearchCriterion(PackageSearchType.cocoapods, "pods.version",
            new AqlUISearchModel("cocoapodsVersion", "Version", "version", "CocoaPods Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("pods.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    composerName(() -> new PackageSearchCriterion(PackageSearchType.composer, "composer.name",
            new AqlUISearchModel("composerName", "Name", "name", "Composer Package Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("composer.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    composerVersion(() -> new PackageSearchCriterion(PackageSearchType.composer, "composer.version",
            new AqlUISearchModel("composerVersion", "Version", "version", "Composer Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("composer.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    conanName(() -> new PackageSearchCriterion(PackageSearchType.conan, "conan.package.name",
            new AqlUISearchModel("conanName", "Name", "name", "Conan Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("conan.package.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    conanVersion(() -> new PackageSearchCriterion(PackageSearchType.conan, "conan.package.version",
            new AqlUISearchModel("conanVersion", "Version", "version", "Conan Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("conan.package.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    conanUser(() -> new PackageSearchCriterion(PackageSearchType.conan, "conan.package.user",
            new AqlUISearchModel("conanUser", "User", "user", "Conan User", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("conan.package.user",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    conanChannel(() -> new PackageSearchCriterion(PackageSearchType.conan, "conan.package.channel",
            new AqlUISearchModel("conanChannel", "Channel", "channel", "Conan Channel", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("conan.package.channel",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    conanOs(() -> new PackageSearchCriterion(PackageSearchType.conan, "conan.settings.os",
            new AqlUISearchModel("conanOs", "OS", "os", "Conan OS", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("conan.settings.os",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    conanArch(() -> new PackageSearchCriterion(PackageSearchType.conan, "conan.settings.arch",
            new AqlUISearchModel("conanArch", "Architecture", "arch", "Conan Architecture", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("conan.settings.arch",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    conanBuildType(() -> new PackageSearchCriterion(PackageSearchType.conan, "conan.settings.build_type",
            new AqlUISearchModel("conanBuildType", "Build Type", "buildType", "Conan Build Type", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("conan.settings.build_type",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    conanCompiler(() -> new PackageSearchCriterion(PackageSearchType.conan, "conan.settings.compiler",
            new AqlUISearchModel("conanCompiler", "Compiler", "compiler", "Conan Compiler", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("conan.settings.compiler",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    cranPackageName(() -> new PackageSearchCriterion(PackageSearchType.cran, "cran.name",
            new AqlUISearchModel("cranPackageName", "Name", "name", "Cran Package Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("cran.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    cranPackageVersion(() -> new PackageSearchCriterion(PackageSearchType.cran, "cran.version",
            new AqlUISearchModel("cranPackageVersion", "Version", "version", "Cran Package Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("cran.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    condaName(() -> new PackageSearchCriterion(PackageSearchType.conda, "conda.name",
            new AqlUISearchModel("condaName", "Name", "name", "Conda Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("conda.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    condaVersion(() -> new PackageSearchCriterion(PackageSearchType.conda, "conda.version",
            new AqlUISearchModel("condaVersion", "Version", "version", "Conda Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("conda.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    condaArch(() -> new PackageSearchCriterion(PackageSearchType.conda, "conda.arch",
            new AqlUISearchModel("condaArch", "Arch", "arch", "Conda Arch", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("conda.arch",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    condaPlatform(() -> new PackageSearchCriterion(PackageSearchType.conda, "conda.platform",
            new AqlUISearchModel("condaPlatform", "Platform", "platform", "Conda Platform", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("conda.platform",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    debianName(() -> new PackageSearchCriterion(PackageSearchType.debian, "deb.name",
            new AqlUISearchModel("debianName", "Name", "name", "Debian Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("deb.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),


    debianVersion(() -> new PackageSearchCriterion(PackageSearchType.debian, "deb.version",
            new AqlUISearchModel("debianVersion", "Version", "version", "Debian Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("deb.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    debianPriority(() -> new PackageSearchCriterion(PackageSearchType.debian, "deb.priority",
            new AqlUISearchModel("debianPriority", "Priority", "priority", "Debian Priority", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("deb.priority",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    debianMaintainer(() -> new PackageSearchCriterion(PackageSearchType.debian, "deb.maintainer",
            new AqlUISearchModel("debianMaintainer", "Maintainer", "maintainer", "Debian Maintainer", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("deb.maintainer",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    debianDistribution(() -> new PackageSearchCriterion(PackageSearchType.debian, DebianNaming.DISTRIBUTION_PROP,
            new AqlUISearchModel("debianDistribution", "Distribution", "dist", "Debian Distribution", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy(DebianNaming.DISTRIBUTION_PROP,
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    debianComponent(() -> new PackageSearchCriterion(PackageSearchType.debian, DebianNaming.COMPONENT_PROP,
            new AqlUISearchModel("debianComponent", "Component", "component", "Debian Component", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy(DebianNaming.COMPONENT_PROP,
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    debianArchitecture(() -> new PackageSearchCriterion(PackageSearchType.debian, DebianNaming.ARCHITECTURE_PROP,
            new AqlUISearchModel("debianArchitecture", "Architecture", "arch", "Debian Architecture", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy(DebianNaming.ARCHITECTURE_PROP,
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    debianSourceName(() -> new PackageSearchCriterion(PackageSearchType.debianSource, "dsc.name",
            new AqlUISearchModel("debianSourceName", "Name", "name", "Debian Source Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("dsc.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),


    debianSourceVersion(() -> new PackageSearchCriterion(PackageSearchType.debianSource, "dsc.version",
            new AqlUISearchModel("debianSourceVersion", "Version", "version", "Debian Source Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("dsc.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    debianSourceMaintainer(() -> new PackageSearchCriterion(PackageSearchType.debianSource, "dsc.maintainer",
            new AqlUISearchModel("debianSourceMaintainer", "Maintainer", "maintainer", "Debian Source Maintainer", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("dsc.maintainer",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    dockerV1Image(() -> new PackageSearchCriterion(PackageSearchType.dockerV1, "path",
            new AqlUISearchModel("dockerV1Image", "Image", "image", "Docker V1 Image", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.matches}),
            new AqlUIDockerV1ImageSearchStrategy(AqlPhysicalFieldEnum.itemPath,
                    new AqlDomainEnum[] {AqlDomainEnum.items}),
            new AqlUISearchDockerV1ResultManipulator())),

    dockerV1Tag(() -> new PackageSearchCriterion(PackageSearchType.dockerV1, "docker.tag.name",
            new AqlUISearchModel("dockerV1Tag", "Tag", "tag", "Docker V1 Tag", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("docker.tag.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    dockerV2Image(() -> new PackageSearchCriterion(PackageSearchType.dockerV2, "docker.repoName",
            new AqlUISearchModel("dockerV2Image", "Image", "image", "Docker V2 Image", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIDockerV2ImageSearchStrategy("docker.repoName",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    dockerV2TagPath(() -> new PackageSearchCriterion(PackageSearchType.dockerV2, "docker.tag.path",
            new AqlUISearchModel("dockerV2TagPath", "TagPath", "tagPath", "Docker V2 Tag", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIDockerV2TagPathSearchStrategy(AqlPhysicalFieldEnum.itemPath,
                    new AqlDomainEnum[] {AqlDomainEnum.items}),
            new AqlUISearchDummyResultManipulator())),

    dockerV2Tag(() -> new PackageSearchCriterion(PackageSearchType.dockerV2, "docker.manifest",
            new AqlUISearchModel("dockerV2Tag", "Tag", "tag", "Docker V2 Tag", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIDockerV2ImageSearchStrategy("docker.manifest",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    //IMPORTANT: remove after migration-enforcing major --> don't remove the logic that puts the prop from docker
    //without replacing this!
    dockerV2ImageDigest(() -> new PackageSearchCriterion(PackageSearchType.dockerV2, "sha256",
            new AqlUISearchModel("dockerV2ImageDigest", "Image Digest", "digest", "Docker V2 Image Digest", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIDockerV2ImageDigestSearchStrategy("sha256",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    ociImage(() -> new PackageSearchCriterion(PackageSearchType.oci, "docker.repoName",
            new AqlUISearchModel("ociImage", "Image", "image", "Oci Image", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIDockerV2ImageSearchStrategy("docker.repoName",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    ociTagPath(() -> new PackageSearchCriterion(PackageSearchType.oci, "docker.tag.path",
            new AqlUISearchModel("ociTagPath", "TagPath", "tagPath", "Oci Tag", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIDockerV2TagPathSearchStrategy(AqlPhysicalFieldEnum.itemPath,
                    new AqlDomainEnum[] {AqlDomainEnum.items}),
            new AqlUISearchDummyResultManipulator())),

    ociTag(() -> new PackageSearchCriterion(PackageSearchType.oci, "docker.manifest",
            new AqlUISearchModel("ociTag", "Tag", "tag", "Oci Tag", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIDockerV2ImageSearchStrategy("docker.manifest",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    ociImageDigest(() -> new PackageSearchCriterion(PackageSearchType.oci, "sha256",
            new AqlUISearchModel("ociImageDigest", "Image Digest", "digest", "Oci Image Digest", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIDockerV2ImageDigestSearchStrategy("sha256",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    helmOciImage(() -> new PackageSearchCriterion(PackageSearchType.helmoci, "docker.repoName",
            new AqlUISearchModel("helmOciImage", "Image", "image", "HelmOci Image", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIDockerV2ImageSearchStrategy("docker.repoName",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    helmOciTagPath(() -> new PackageSearchCriterion(PackageSearchType.helmoci, "docker.tag.path",
            new AqlUISearchModel("helmOciTagPath", "TagPath", "tagPath", "HelmOci Tag", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIDockerV2TagPathSearchStrategy(AqlPhysicalFieldEnum.itemPath,
                    new AqlDomainEnum[] {AqlDomainEnum.items}),
            new AqlUISearchDummyResultManipulator())),

    helmOciTag(() -> new PackageSearchCriterion(PackageSearchType.helmoci, "docker.manifest",
            new AqlUISearchModel("helmOciTag", "Tag", "tag", "HelmOci Tag", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIDockerV2ImageSearchStrategy("docker.manifest",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    helmOciImageDigest(() -> new PackageSearchCriterion(PackageSearchType.helmoci, "sha256",
            new AqlUISearchModel("helmOciImageDigest", "Image Digest", "digest", "HelmOci Image Digest", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIDockerV2ImageDigestSearchStrategy("sha256",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    gemName(() -> new PackageSearchCriterion(PackageSearchType.gems, "gem.name",
            new AqlUISearchModel("gemName", "Name", "name", "Gem Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("gem.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    gemVersion(() -> new PackageSearchCriterion(PackageSearchType.gems, "gem.version",
            new AqlUISearchModel("gemVersion", "Version", "version", "Gem Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("gem.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    chartName(() -> new PackageSearchCriterion(PackageSearchType.helm, "chart.name",
            new AqlUISearchModel("chartName", "Name", "name", "Helm Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("chart.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    chartVersion(() -> new PackageSearchCriterion(PackageSearchType.helm, "chart.version",
            new AqlUISearchModel("chartVersion", "Version", "version", "Helm Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("chart.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    chartAppVersion(() -> new PackageSearchCriterion(PackageSearchType.helm, "chart.appVersion",
            new AqlUISearchModel("chartAppVersion", "App Version", "appVersion", "Helm App Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("chart.appVersion",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    crateName(() -> new PackageSearchCriterion(PackageSearchType.cargo, "crate.name",
            new AqlUISearchModel("crateName", "Name", "name", "Crate Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("crate.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    crateVersion(() -> new PackageSearchCriterion(PackageSearchType.cargo, "crate.version",
            new AqlUISearchModel("crateVersion", "Version", "version", "Crate Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("crate.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    terraformModuleName(() -> new PackageSearchCriterion(PackageSearchType.terraformModule, "terraform.name",
            new AqlUISearchModel("terraformModuleName", "Name", "name", "Terraform Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("terraform.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    terraformModuleVersion(() -> new PackageSearchCriterion(PackageSearchType.terraformModule, "terraform.version",
            new AqlUISearchModel("terraformModuleVersion", "Version", "version", "Terraform Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("terraform.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    terraformModuleNamespace(() -> new PackageSearchCriterion(PackageSearchType.terraformModule, "terraform.namespace",
            new AqlUISearchModel("terraformModuleNamespace", "Namespace", "namespace", "Namespace", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("terraform.namespace",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    terraformModuleProvider(() -> new PackageSearchCriterion(PackageSearchType.terraformModule, "terraform.provider",
            new AqlUISearchModel("terraformModuleProvider", "Provider (System)", "provider", "Provider", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("terraform.provider",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    terraformModuleType(() -> new PackageSearchCriterion(PackageSearchType.terraformModule, "terraform.type",
            new AqlUISearchModel("terraformModuleType", "Terraform Type", "type", "Terraform Type", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("terraform.type",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    terraformProviderName(() -> new PackageSearchCriterion(PackageSearchType.terraformProvider, "terraform.name",
            new AqlUISearchModel("terraformProviderName", "Name", "name", "Terraform Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("terraform.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    terraformProviderVersion(() -> new PackageSearchCriterion(PackageSearchType.terraformProvider, "terraform.version",
            new AqlUISearchModel("terraformProviderVersion", "Version", "version", "Terraform Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("terraform.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    terraformProviderNamespace(() -> new PackageSearchCriterion(PackageSearchType.terraformProvider,
            "terraform.namespace",
            new AqlUISearchModel("terraformProviderNamespace", "Namespace", "namespace", "Namespace", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("terraform.namespace",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    terraformFlavor(() -> new PackageSearchCriterion(PackageSearchType.terraformProvider, "terraform.flavor",
            new AqlUISearchModel("terraformFlavor", "Flavor", "flavor", "Terraform Flavor", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("terraform.flavor",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    terraformProviderType(() -> new PackageSearchCriterion(PackageSearchType.terraformProvider, "terraform.type",
            new AqlUISearchModel("terraformProviderType", "Terraform Type", "type", "Terraform Type", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("terraform.type",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    alpineName(() -> new PackageSearchCriterion(PackageSearchType.alpine, "alpine.name",
            new AqlUISearchModel("alpineName", "Name", "name", "Alpine Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("alpine.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    alpineVersion(() -> new PackageSearchCriterion(PackageSearchType.alpine, "alpine.version",
            new AqlUISearchModel("alpineVersion", "Version", "version", "Alpine Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("alpine.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    alpineBranch(() -> new PackageSearchCriterion(PackageSearchType.alpine, "alpine.branch",
            new AqlUISearchModel("alpineBranch", "Branch", "branch", "Alpine Branch", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("alpine.branch",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    alpineRepository(() -> new PackageSearchCriterion(PackageSearchType.alpine, "alpine.repository",
            new AqlUISearchModel("alpineRepository", "Alpine Inner Repository", "repository", "Alpine Repository", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("alpine.repository",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    alpineArchitecture(() -> new PackageSearchCriterion(PackageSearchType.alpine, "alpine.architecture",
            new AqlUISearchModel("alpineArchitecture", "Architecture", "architecture", "Alpine Architecture", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("alpine.architecture",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    ansibleNamespace(() -> new PackageSearchCriterion(
            PackageSearchType.ansible, "ansible.namespace",
            new AqlUISearchModel("ansibleNamespace", "Namespace", "namespace", "Ansible Namespace", true,
                                 new AqlComparatorEnum[]{AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("ansible.namespace",
                                            new AqlDomainEnum[]{AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    ansibleName(() -> new PackageSearchCriterion(
            PackageSearchType.ansible, "ansible.name",
            new AqlUISearchModel("ansibleName", "Name", "name", "Ansible Name", true,
                                 new AqlComparatorEnum[]{AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("ansible.name",
                                            new AqlDomainEnum[]{AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    ansibleVersion(() -> new PackageSearchCriterion(
            PackageSearchType.ansible, "ansible.version",
            new AqlUISearchModel("ansibleVersion", "Version", "version", "Ansible Version", true,
                                 new AqlComparatorEnum[]{AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("ansible.version",
                                            new AqlDomainEnum[]{AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    ansibleTags(() -> new PackageSearchCriterion(
            PackageSearchType.ansible, "ansible.tags",
            new AqlUISearchModel("ansibleTags", "Tags", "tags", "Ansible Tags", false,
                                 new AqlComparatorEnum[]{AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUINpmKeywordsSearchStrategy("ansible.tags",
                                               new AqlDomainEnum[]{AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    goName(() -> new PackageSearchCriterion(PackageSearchType.go, "go.name",
            new AqlUISearchModel("goName", "Name", "name", "Go Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("go.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    goVersion(() -> new PackageSearchCriterion(PackageSearchType.go, "go.version",
            new AqlUISearchModel("goVersion", "Version", "version", "Go Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("go.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    nugetPackageId(() -> new PackageSearchCriterion(PackageSearchType.nuget, "nuget.id",
            new AqlUISearchModel("nugetPackageId", "ID", "name", "NuGet Package ID", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("nuget.id",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    nugetVersion(() -> new PackageSearchCriterion(PackageSearchType.nuget, "nuget.version",
            new AqlUISearchModel("nugetVersion", "Version", "version", "NuGet Package Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("nuget.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    npmName(() -> new PackageSearchCriterion(PackageSearchType.npm, "npm.name",
            new AqlUISearchModel("npmName", "Name", "name", "Npm Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUINpmNameSearchStrategy("npm.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    npmVersion(() -> new PackageSearchCriterion(PackageSearchType.npm, "npm.version",
            new AqlUISearchModel("npmVersion", "Version", "version", "Npm Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("npm.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    npmScope(() -> new PackageSearchCriterion(PackageSearchType.npm, "npm.name",
            new AqlUISearchModel("npmScope", "Scope", "scope", "Npm Scope", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUINpmScopeSearchStrategy("npm.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchNpmResultManipulator())),

    npmKeywords(() -> new PackageSearchCriterion(PackageSearchType.npm, "npm.keywords",
            new AqlUISearchModel("npmKeywords", "Keywords", "keywords", "Npm Keywords", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUINpmKeywordsSearchStrategy("npm.keywords",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchNpmResultManipulator())),

    npmChecksum(() -> new PackageSearchCriterion(PackageSearchType.npm, "npm.checksum",
            new AqlUISearchModel("npmChecksum", "Checksum", "checksum", "Npm Checksum", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUINpmChecksumSearchStrategy(itemActualSha1, new AqlDomainEnum[] {AqlDomainEnum.items}),
            new AqlUISearchNpmResultManipulator())),

    opkgName(() -> new PackageSearchCriterion(PackageSearchType.opkg, "opkg.name",
            new AqlUISearchModel("opkgName", "Name", "name", "Opkg Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("opkg.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    opkgVersion(() -> new PackageSearchCriterion(PackageSearchType.opkg, "opkg.version",
            new AqlUISearchModel("opkgVersion", "Version", "version", "Opkg Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("opkg.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    opkgArchitecture(() -> new PackageSearchCriterion(PackageSearchType.opkg, "opkg.architecture",
            new AqlUISearchModel("opkgArchitecture", "Architecture", "arch", "Opkg Architecture", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("opkg.architecture",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    opkgPriority(() -> new PackageSearchCriterion(PackageSearchType.opkg, "opkg.priority",
            new AqlUISearchModel("opkgPriority", "Priority", "priority", "Opkg Priority", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("opkg.priority",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    opkgMaintainer(() -> new PackageSearchCriterion(PackageSearchType.opkg, "opkg.maintainer",
            new AqlUISearchModel("opkgMaintainer", "Maintainer", "maintainer", "Opkg Maintainer", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("opkg.maintainer",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    pypiName(() -> new PackageSearchCriterion(PackageSearchType.pypi, "pypi.name",
            new AqlUISearchModel("pypiName", "Name", "name", "PyPi Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("pypi.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    pypiVersion(() -> new PackageSearchCriterion(PackageSearchType.pypi, "pypi.version",
            new AqlUISearchModel("pypiVersion", "Version", "version", "Pypi Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("pypi.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    puppetName(() -> new PackageSearchCriterion(PackageSearchType.puppet, "puppet.name",
            new AqlUISearchModel("puppetName", "Name", "name", "Puppet Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("puppet.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    puppetVersion(() -> new PackageSearchCriterion(PackageSearchType.puppet, "puppet.version",
            new AqlUISearchModel("puppetVersion", "Version", "version", "Puppet Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("puppet.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    rpmName(() -> new PackageSearchCriterion(PackageSearchType.rpm, "rpm.metadata.name",
            new AqlUISearchModel("rpmName", "Name", "name", "RPM Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("rpm.metadata.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    rpmVersion(() -> new PackageSearchCriterion(PackageSearchType.rpm, "rpm.metadata.version",
            new AqlUISearchModel("rpmVersion", "Version", "version", "RPM Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("rpm.metadata.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    rpmArchitecture(() -> new PackageSearchCriterion(PackageSearchType.rpm, "rpm.metadata.arch",
            new AqlUISearchModel("rpmArchitecture", "Architecture", "arch", "RPM Architecture", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("rpm.metadata.arch",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    swiftName(() -> new PackageSearchCriterion(PackageSearchType.swift, "swift.name",
            new AqlUISearchModel("swiftName", "Name", "name", "Swift Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("swift.name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    swiftVersion(() -> new PackageSearchCriterion(PackageSearchType.swift, "swift.version",
            new AqlUISearchModel("swiftVersion", "Version", "version", "Swift Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("swift.version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    vagrantName(() -> new PackageSearchCriterion(PackageSearchType.vagrant, "box_name",
            new AqlUISearchModel("vagrantName", "Box Name", "name", "Vagrant Box Name", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("box_name",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    vagrantVersion(() -> new PackageSearchCriterion(PackageSearchType.vagrant, "box_version",
            new AqlUISearchModel("vagrantVersion", "Box Version", "version", "Vagrant Box Version", true,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("box_version",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    vagrantProvider(() -> new PackageSearchCriterion(PackageSearchType.vagrant, "box_provider",
            new AqlUISearchModel("vagrantProvider", "Box Provider", "boxProvider", "Vagrant Box Provider", false,
                    new AqlComparatorEnum[] {AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("box_provider",
                    new AqlDomainEnum[] {AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    nixName(() -> new PackageSearchCriterion(
            PackageSearchType.nix, "nix.name",
            new AqlUISearchModel("nixName", "Name", "name", "Nix Name", true,
                                 new AqlComparatorEnum[]{AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("nix.name",
                                            new AqlDomainEnum[]{AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    nixVersion(() -> new PackageSearchCriterion(
            PackageSearchType.nix, "nix.version",
            new AqlUISearchModel("nixVersion", "Version", "version", "Nix Version", true,
                                 new AqlComparatorEnum[]{AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("nix.version",
                                            new AqlDomainEnum[]{AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    nixHash(() -> new PackageSearchCriterion(
            PackageSearchType.nix, "nix.store.path",
            new AqlUISearchModel("nixHash", "NarInfo Hash", "narinfohash", "Nix NarInfo Hash", true,
                                 new AqlComparatorEnum[]{AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("nix.store.path",
                                            new AqlDomainEnum[]{AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    nixSystem(() -> new PackageSearchCriterion(
            PackageSearchType.nix, "nix.system",
            new AqlUISearchModel("nixSystem", "System", "system", "Nix System", true,
                                 new AqlComparatorEnum[]{AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("nix.system",
                                            new AqlDomainEnum[]{AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    luaRocksName(() -> new PackageSearchCriterion(
            PackageSearchType.luarocks, "luarocks.name",
            new AqlUISearchModel("luaRocksName", "Name", "name", "LuaRocks Name", true,
                                 new AqlComparatorEnum[]{AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("luarocks.name",
                                            new AqlDomainEnum[]{AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    luaRocksVersion(() -> new PackageSearchCriterion(
            PackageSearchType.luarocks, "luarocks.version",
            new AqlUISearchModel("luaRocksVersion", "Version", "version", "LuaRocks Version", true,
                                 new AqlComparatorEnum[]{AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("luarocks.version",
                                            new AqlDomainEnum[]{AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    skillsName(() -> new PackageSearchCriterion(
            PackageSearchType.skills, "skill.name",
            new AqlUISearchModel("skillsName", "Name", "name", "Skills Name", true,
                                 new AqlComparatorEnum[]{AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("skill.name",
                                            new AqlDomainEnum[]{AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    skillsVersion(() -> new PackageSearchCriterion(
            PackageSearchType.skills, "skill.version",
            new AqlUISearchModel("skillsVersion", "Version", "version", "Skills Version", true,
                                 new AqlComparatorEnum[]{AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("skill.version",
                                            new AqlDomainEnum[]{AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    agentPluginsName(() -> new PackageSearchCriterion(
            PackageSearchType.agentplugins, "agentplugins.name",
            new AqlUISearchModel("agentPluginsName", "Name", "name", "Agent Plugins Name", true,
                                 new AqlComparatorEnum[]{AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("agentplugins.name",
                                            new AqlDomainEnum[]{AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    agentPluginsVersion(() -> new PackageSearchCriterion(
            PackageSearchType.agentplugins, "agentplugins.version",
            new AqlUISearchModel("agentPluginsVersion", "Version", "version", "Agent Plugins Version", true,
                                 new AqlComparatorEnum[]{AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("agentplugins.version",
                                            new AqlDomainEnum[]{AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    agentPackagesName(() -> new PackageSearchCriterion(
            PackageSearchType.agentpackages, "agentpackages.gitrepo",
            new AqlUISearchModel("agentPackagesName", "Name", "name", "Agent Packages Name", true,
                                 new AqlComparatorEnum[]{AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("agentpackages.gitrepo",
                                            new AqlDomainEnum[]{AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator())),

    agentPackagesVersion(() -> new PackageSearchCriterion(
            PackageSearchType.agentpackages, "agentpackages.version",
            new AqlUISearchModel("agentPackagesVersion", "Version", "version", "Agent Packages Version", true,
                                 new AqlComparatorEnum[]{AqlComparatorEnum.equals, AqlComparatorEnum.matches}),
            new AqlUIPropertySearchStrategy("agentpackages.version",
                                            new AqlDomainEnum[]{AqlDomainEnum.items, AqlDomainEnum.properties}),
            new AqlUISearchDummyResultManipulator()));

    private final PackageSearchCriteriaFactory criteriaFactory;

    public PackageSearchCriterion getCriterion() {
        return criteriaFactory.create();
    }

    PackageSearchCriteria(PackageSearchCriteriaFactory ruleFactory) {
        this.criteriaFactory = ruleFactory;
    }

    public static class PackageSearchCriterion {

        PackageSearchType type;
        String aqlName;
        AqlUISearchModel model;
        AqlUISearchStrategy strategy;
        AqlUISearchResultManipulator resultManipulator;

        PackageSearchCriterion(PackageSearchType type, String aqlName, AqlUISearchModel model,
                AqlUISearchStrategy strategy, AqlUISearchResultManipulator resultManipulator) {
            this.type = type;
            this.aqlName = aqlName;
            this.model = model;
            this.strategy = strategy;
            this.resultManipulator = resultManipulator;
        }

        public PackageSearchType getType() {
            return type;
        }

        public AqlUISearchModel getModel() {
            return model;
        }

        public AqlUISearchStrategy getStrategy() {
            return strategy;
        }

        public AqlUISearchResultManipulator getResultManipulator() {
            return resultManipulator;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PackageSearchCriterion packageSearchCriterion = (PackageSearchCriterion) o;
            if (type != null ? !type.equals(packageSearchCriterion.type) : packageSearchCriterion.type != null) {
                return false;
            }

            if (aqlName != null ? !aqlName.equals(packageSearchCriterion.aqlName) :
                    packageSearchCriterion.aqlName != null) {
                return false;
            }
            if (model != null ? !model.equals(packageSearchCriterion.model) : packageSearchCriterion.model != null) {
                return false;
            }
            if (strategy != null ? !strategy.equals(packageSearchCriterion.strategy) :
                    packageSearchCriterion.strategy != null) {
                return false;
            }
            return resultManipulator != null ? resultManipulator.equals(packageSearchCriterion.resultManipulator) :
                    packageSearchCriterion.resultManipulator == null;
        }
    }

    public static AqlUISearchStrategy getStrategyByFieldId(String id) {
        if (Strings.isNullOrEmpty(id)) {
            throw new IllegalArgumentException("Field ID cannot be null or empty");
        }
        return valueOf(id).getCriterion().strategy;
    }

    public static AqlUISearchStrategy getStrategyByTypeAndElementType(PackageSearchType type, String elementType) {
        for (PackageSearchCriteria packageSearchCriteria : PackageSearchCriteria.values()) {
            PackageSearchCriterion criterion = packageSearchCriteria.getCriterion();
            if (criterion.type.equals(type) && criterion.model.getDisplayName().equals(elementType)) {
                return criterion.strategy;
            }
        }
        return null;
    }

    /**
     * Returns the criteria that matches the AQL field name or the property key that {@param aqlName} references
     */
    public static PackageSearchCriterion getCriterionByAqlFieldOrPropName(String aqlName) {
        return Stream.of(values())
                .map(PackageSearchCriteria::getCriterion)
                .filter(criterion -> criterion.aqlName.equalsIgnoreCase(aqlName))
                .findAny()
                .orElse(null);
    }

    public static List<PackageSearchCriterion> getCriteriaByPackage(String packageType) {
        return Stream.of(values())
                .map(PackageSearchCriteria::getCriterion)
                .filter(searchCriterion -> searchCriterion.type.equals(PackageSearchType.getById(packageType)))
                .collect(Collectors.toList());
    }

    public static List<PackageSearchCriterion> getCriteriaByPackage(PackageSearchType packageType) {
        return Stream.of(values())
                .map(PackageSearchCriteria::getCriterion)
                .filter(searchCriterion -> searchCriterion.type.equals(packageType))
                .collect(Collectors.toList());
    }

    public static List<AqlUISearchResultManipulator> getResultManipulatorsByPackage(PackageSearchType packageType) {
        return getCriteriaByPackage(packageType)
                .stream()
                .map(PackageSearchCriterion::getResultManipulator)
                .collect(Collectors.toList());
    }

    public static PackageSearchType getPackageTypeByFieldId(String fieldId) {
        if (Strings.isNullOrEmpty(fieldId)) {
            return null;
        }
        try {
            return valueOf(fieldId).getCriterion().type;
        } catch (IllegalArgumentException iae) {
            //no such fieldId - return null
            return null;
        }
    }

    public static List<AqlUISearchStrategy> getStartegiesByPackageSearchType(PackageSearchType type) {
        return getCriteriaByPackage(type).stream()
                .map(PackageSearchCriterion::getStrategy)
                .collect(Collectors.toList());
    }

    //Keep packages ordered alphabetically please!
    public enum PackageSearchType {
        bower(RepoType.Bower, true, "bower", true, DUMMY_MERGER),
        cargo(RepoType.Cargo, true, "cargo", true, DUMMY_MERGER),
        chef(RepoType.Chef, true, "chef", true, DUMMY_MERGER),
        cocoapods(RepoType.CocoaPods, true, "cocoapods", true, DUMMY_MERGER),
        composer(RepoType.Composer, true, "composer", true, DUMMY_MERGER),
        conan(RepoType.Conan, false, "conan", false, new ConanPackageSearchResultMerger()),
        cran(RepoType.CRAN, false, "cran", true, DUMMY_MERGER),
        conda(RepoType.Conda, false, "conda", true, DUMMY_MERGER),
        debian(RepoType.Debian, false, "deb", true, DUMMY_MERGER),
        debianSource(RepoType.Debian, false, "deb", true, DUMMY_MERGER),
        dockerV1(RepoType.Docker, true, "docker", false, DUMMY_MERGER),
        dockerV2(RepoType.Docker, true, "docker", false, DUMMY_MERGER),
        oci(RepoType.OCI, true, "oci", false, DUMMY_MERGER),
        gavc(RepoType.Maven, true, "pom", true, DUMMY_MERGER),
        gems(RepoType.Gems, false, "ruby-gems", true, DUMMY_MERGER),
        go(RepoType.Go, false, "golang", true, DUMMY_MERGER),
        helm(RepoType.Helm, false, "helm", true, DUMMY_MERGER),
        helmoci(RepoType.HelmOCI, true, "helmoci", false, DUMMY_MERGER),


        bazelmodules(RepoType.BazelModules, false, "bazelmodules", true, DUMMY_MERGER),
        luarocks(RepoType.LuaRocks, true, "luarocks", true, DUMMY_MERGER),
        nix(RepoType.Nix, false, "nix", true, DUMMY_MERGER),
        agentplugins(RepoType.AgentPlugins, false, "agentplugins", true, DUMMY_MERGER),
        agentpackages(RepoType.AgentPackages, false, "agentpackages", true, DUMMY_MERGER),
        alpine(RepoType.Alpine, true, "alpine", true, DUMMY_MERGER),
        nuget(RepoType.NuGet, true, "nuget", true, DUMMY_MERGER),
        npm(RepoType.Npm, true, "npm", true, DUMMY_MERGER),
        opkg(RepoType.Opkg, false, "opkg", true, DUMMY_MERGER),
        pypi(RepoType.Pypi, false, "pypi", true, DUMMY_MERGER),
        puppet(RepoType.Puppet, false, "puppet", true, DUMMY_MERGER),
        pub(RepoType.Pub, true, "pub", true, DUMMY_MERGER),
        rpm(RepoType.YUM, true, "rpm", true, DUMMY_MERGER),
        releasebundles(RepoType.ReleaseBundles, true, "release-bundles", true, DUMMY_MERGER),
        swift(RepoType.Swift, true, "swift", true, DUMMY_MERGER),
        terraformModule(RepoType.Terraform, false, "terraform", true, DUMMY_MERGER),
        terraformProvider(RepoType.Terraform, false, "terraform", true, DUMMY_MERGER),
        terraformBackend(RepoType.TerraformBackend, false, "terraform-backend", true, DUMMY_MERGER),
        vagrant(RepoType.Vagrant, false, "vagrant", true, DUMMY_MERGER),
        huggingfaceml(RepoType.HuggingFaceML, false, "huggingfaceml", true, DUMMY_MERGER),
        machinelearning(RepoType.MachineLearning, false, "machinelearning", true, DUMMY_MERGER),
        ansible(RepoType.Ansible, false, "ansible", true, DUMMY_MERGER),
        hex(RepoType.Hex, false, "hex", true, DUMMY_MERGER),
        nimmodel(RepoType.NimModel, false, "nimmodel", true, DUMMY_MERGER),
        jem(RepoType.Jem, false, "jem", true, DUMMY_MERGER),
        aieditorextensions(RepoType.AIEditorExtensions, true, "aieditorextensions", true, DUMMY_MERGER),
        jetbrainsplugins(RepoType.JetBrainsPlugins, true, "jetbrainsplugins", true, DUMMY_MERGER),
        skills(RepoType.Skills, true, "skills", true, DUMMY_MERGER),

        ;
        /*, all(""),*/ /*gitlfs(RepoType.GitLfs, false, "git-lfs"),*/

        final boolean remoteCachesProps;
        final RepoType repoType;
        final String icon;
        final boolean downloadEnabled;
        final PackageSearchResultMerger resultMerger;

        PackageSearchType(RepoType repoType, boolean remoteCachesProps, String icon, boolean downloadEnabled,
                PackageSearchResultMerger resultMerger) {
            this.repoType = repoType;
            this.remoteCachesProps = remoteCachesProps;
            this.icon = icon;
            this.downloadEnabled = downloadEnabled;
            this.resultMerger = resultMerger;
        }

        public static PackageSearchType getById(String id) {
            for (PackageSearchType type : values()) {
                if (type.name().equalsIgnoreCase(id)) {
                    return type;
                }
            }
            throw new UnsupportedOperationException("Unsupported package");
        }

        public String getDisplayName() {
            if (this.equals(dockerV1)) {
                return "Docker V1";
            } else if (this.equals(dockerV2)) {
                return "Docker V2";
            } else if (this.equals(oci)) {
                return "OCI";
            } else if (this.equals(gavc)) {
                return "GAVC";
            } else if (this.equals(pypi)) {
                return "PyPI";
            } else if (this.equals(terraformModule)) {
                return "Terraform Module";
            } else if (this.equals(terraformProvider)) {
                return "Terraform Provider";
            } else if (this.equals(terraformBackend)) {
                return "Terraform Backend";
            } else if (this.equals(debianSource)) {
                return "Debian Source";
            }
            return repoType.getDisplayName();
        }

        public boolean isRemoteCachesProps() {
            return remoteCachesProps;
        }

        public boolean isDownloadEnabled() {
            return downloadEnabled;
        }

        public String getId() {
            return this.name();
        }

        public RepoType getRepoType() {
            return repoType;
        }

        public String getIcon() {
            return icon;
        }

        public PackageSearchResultMerger getResultMerger() {
            return resultMerger;
        }
    }
}
