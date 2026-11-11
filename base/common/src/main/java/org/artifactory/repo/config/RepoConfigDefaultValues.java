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

package org.artifactory.repo.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.ChecksumPolicyType;
import org.artifactory.repo.local.LocalRepoChecksumPolicyType;
import org.artifactory.repo.packagetype.DockerApiVersion;
import org.artifactory.repo.packagetype.PomCleanupPolicy;
import org.artifactory.repo.packagetype.SnapshotVersionBehavior;
import org.artifactory.repo.packagetype.configuration.VcsGitConfiguration;
import org.artifactory.repo.remote.ContentSynchronisation;
import org.artifactory.repo.vcs.VcsGitProvider;
import org.artifactory.repo.vcs.VcsType;
import org.artifactory.util.AolUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.common.ConstantValues.debianDefaultArchitectures;
import static org.artifactory.descriptor.repo.XrayRepoConfigDescriptor.DEFAULT_DATA_TTL;

/**
 * @author Dan Feldman
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class RepoConfigDefaultValues {

    //local basic
    public static final String DEFAULT_INCLUDES_PATTERN = "**/*";
    public static final String DEFAULT_REPO_LAYOUT = "simple-default";

    //local advanced
    public static final boolean DEFAULT_BLACKED_OUT = false;
    public static final boolean DEFAULT_ALLOW_CONTENT_BROWSING = false;
    public static final boolean DEFAULT_PRIORITY_RELEASE = false;

    //remote basic
    public static final boolean DEFAULT_OFFLINE = false;
    public static final ContentSynchronisation DEFAULT_DELEGATION_CONTEXT = null;

    //remote advanced
    public static final boolean DEFAULT_HARD_FAIL = false;
    public static final boolean DEFAULT_STORE_ARTIFACTS_LOCALLY = true;
    public static final boolean DEFAULT_SYNC_PROPERTIES = false;
    public static final boolean DEFAULT_SHARE_CONFIG = false;
    public static final boolean DEFAULT_BLOCK_MISMATCHING_MIME_TYPES = true;
    public static final boolean DEFAULT_BYPASS_HEAD_REQUESTS = false;
    public static final boolean DEFAULT_DISABLE_URL_NORMALIZATION = false;

    public static final boolean DEFAULT_LIST_REMOTE_ITEMS_SUPPORTED_TYPE = true;
    public static final boolean DEFAULT_LIST_REMOTE_ITEMS_UNSUPPORTED_TYPE = false;
    public static final boolean DEFAULT_ENABLE_TOKEN_AUTHENTICATION = false;

    //network
    public static final int DEFAULT_SOCKET_TIMEOUT = 15000;
    public static final boolean DEFAULT_LENIENENT_HOST_AUTH = false;
    public static final boolean DEFAULT_COOKIE_MANAGEMENT = false;
    public static final boolean DEFAULT_DISABLE_PROXY = false;

    //cache
    public static final int DEFAULT_KEEP_UNUSED_ARTIFACTS = 0;
    public static final long DEFAULT_RETRIEVAL_CACHE_PERIOD = 7200;
    public static final long DEFAULT_ASSUMED_OFFLINE = 300;
    public static final long DEFAULT_MISSED_RETRIEVAL_PERIOD = 1800;
    public static final long DEFAULT_METADATA_RETRIEVAL_TIMEOUT_SECS = 60;
    public static final long MIN_METADATA_CACHE_PERIOD_SECS = 30;

    //replication
    public static final boolean DEFAULT_LOCAL_REPLICATION_ENABLED = true;
    public static final boolean DEFAULT_REMOTE_REPLICATION_ENABLED = false;
    public static final boolean DEFAULT_EVENT_REPLICATION = false;
    public static final boolean DEFAULT_REPLICATION_SYNC_DELETES = false;
    public static final boolean DEFAULT_REPLICATION_SYNC_STATISTICS = false;
    public static final boolean DEFAULT_REPLICATION_CHECK_BINARY_EXISTENCE_IN_FILESTORE = false;

    //virtual
    public static final boolean DEFAULT_VIRTUAL_CAN_RETRIEVE_FROM_REMOTE = false;
    public static final long DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD = 7200;
    public static final boolean DEFAULT_VIRTUAL_HIDE_UNAUTHORIZED_RESOURCES = false;
    public static final boolean DEFAULT_VIRTUAL_USE_NAMESPACES = false;

    //xray
    public static final boolean DEFAULT_XRAY_INDEX = false;
    public static final int DEFAULT_XRAY_DATA_TTL = DEFAULT_DATA_TTL;

    //download redirect
    public static final boolean DEFAULT_DOWNLOAD_REDIRECT = false;
    public static final boolean DEFAULT_CDN_REDIRECT = false;

    //external dependency
    public static final String EXTERNAL_DEPENDENCIES_PATTERN_ALL = "**";
    public static final String PATTERN_GITHUB = "**/github.com/**";
    public static final String DEFAULT_EXTERNAL_DEPENDENCIES_PATTERN = EXTERNAL_DEPENDENCIES_PATTERN_ALL;

    //bower
    public static final String DEFAULT_BOWER_REGISTRY = "https://registry.bower.io";

    //Cargo
    public static final String DEFAULT_CARGO_REGISTRY = "https://index.crates.io/";

    // Nix
    public static final String DEFAULT_NIX_REGISTRY_URL = "https://cache.nixos.org";

    // BazelModules
    public static final String DEFAULT_BAZELMODULES_REGISTRY_URL = "https://bcr.bazel.build/";

    //CocoaPods
    public static final String DEFAULT_PODS_SPECS_REPO = "https://github.com/CocoaPods/Specs";
    public static final String DEFAULT_PODS_CDN_URL = "https://cdn.cocoapods.org";
    public static final List<String> DEFAULT_PODS_EXTERNAL_DEPENDENCIES_PATTERNS = List.of(PATTERN_GITHUB);

    //Swift
    public static final List<String> DEFAULT_SWIFT_EXTERNAL_DEPENDENCIES_PATTERNS = List.of(PATTERN_GITHUB);

    //Composer
    public static final String DEFAULT_COMPOSER_REGISTRY = "https://repo.packagist.org";
    public static final boolean ENABLE_COMPOSER_METADATA_V1_INDEXING = false;

    //debian
    public static final boolean DEFAULT_DEB_TRIVIAL_LAYOUT = false;
    public static final boolean DEFAULT_DEB_DDEB_SUPPORT = false;
    public static final String DEFAULT_DEB_ARCHITECTURES = debianDefaultArchitectures.getString();

    //docker
    public static final DockerApiVersion DEFAULT_DOCKER_API_VER = DockerApiVersion.V2;
    public static final boolean DEFAULT_TOKEN_AUTH = true;
    public static final int DEFAULT_MAX_UNIQUE_TAGS = 0;
    public static final boolean DEFAULT_DOCKER_BLOCK_PUSHING_SCHEMA1 = true;
    public static final boolean DEFAULT_DOCKER_VIRTUAL_RESOLVE_TAGS_BY_TIMESTAMP = false;
    public static final boolean DEFAULT_DOCKER_VIRTUAL_CACHE_FOREIGN_LAYERS = false;
    public static final long DEFAULT_DOCKER_REMOTE_RETRIEVAL_CACHE_PERIOD = 21600L;
    public static final int DEFAULT_DOCKER_TAG_RETENTION = 1;

    //maven / gradle / ivy / sbt
    public static final int DEFAULT_MAX_UNIQUE_SNAPSHOTS = 0;
    public static final boolean DEFAULT_HANDLE_RELEASES = true;
    public static final boolean DEFAULT_HANDLE_SNAPSHOTS = true;
    public static final boolean DEFAULT_SUPPRESS_POM_CHECKS = true;
    public static final boolean DEFAULT_SUPPRESS_POM_CHECKS_MAVEN = false;
    public static final SnapshotVersionBehavior DEFAULT_SNAPSHOT_BEHAVIOR = SnapshotVersionBehavior.UNIQUE;
    public static final LocalRepoChecksumPolicyType DEFAULT_CHECKSUM_POLICY = LocalRepoChecksumPolicyType.CLIENT;
    public static final boolean DEFAULT_EAGERLY_FETCH_JARS = false;
    public static final boolean DEFAULT_EAGERLY_FETCH_SOURCES = false;
    public static final ChecksumPolicyType DEFAULT_REMOTE_CHECKSUM_POLICY = ChecksumPolicyType.GEN_IF_ABSENT;
    public static final boolean DEFAULT_REJECT_INVALID_JARS = false;
    public static final PomCleanupPolicy DEFAULT_POM_CLEANUP_POLICY = PomCleanupPolicy.discard_active_reference;
    public static final boolean DEFAULT_FORCE_MAVEN_AUTH = false;

    //nuget
    public static final String DEFAULT_NUGET_FEED_PATH = "api/v2";
    public static final String DEFAULT_NUGET_DOWNLOAD_PATH = "api/v2/package";
    public static final boolean DEFAULT_FORCE_NUGET_AUTH = false;
    public static final boolean DEFAULT_NUGET_NORMALIZED_VERSION = false;
    public static final String DEFAULT_NUGET_V3_URL = "https://api.nuget.org/v3/index.json";
    public static final String DEFAULT_NUGET_SYMBOL_SERVER_URL = "https://symbols.nuget.org/download/symbols";

    //P2
    public static final boolean DEFAULT_FORCE_P2_AUTH = false;

    //conan
    public static final boolean DEFAULT_FORCE_CONAN_AUTH = false;

    // PyPI
    public static final String DEFAULT_PYPI_REGISTRY = "https://pypi.org";
    public static final String DEFAULT_PYPI_SUFFIX = "simple";

    //vcs
    public static final VcsType DEFAULT_VCS_TYPE = VcsType.GIT;
    public static final VcsGitProvider DEFAULT_GIT_PROVIDER = VcsGitProvider.GITHUB;
    public static final VcsGitConfiguration DEFAULT_VCS_GIT_CONFIG = new VcsGitConfiguration();

    //yum
    public static final int DEFAULT_YUM_METADATA_DEPTH = 0;
    public static final String DEFAULT_YUM_GROUPFILE_NAME = "groups.xml";
    public static final boolean DEFAULT_YUM_AUTO_CALCULATE = true;
    public static final boolean DEFAULT_ENABLE_FILELIST_INDEXING = false;

    //go
    public static final List<String> DEFAULT_GO_METADATA_URLS = List.of(
            PATTERN_GITHUB,
            "**/bitbucket.org/**",
            "**/gopkg.in/**",
            "**/golang.org/**",
            "**/k8s.io/**");
    public static final long DEFAULT_GO_REMOTE_METADATA_MISSED_RETRIEVAL_CACHE_PERIOD = 600;
    public static final long DEFAULT_SIGNED_URL_TTL = 90;

    //terraform
    public static final String DEFAULT_TERRAFORM_REGISTRY_URL = "https://registry.terraform.io";
    public static final String DEFAULT_TERRAFORM_PROVIDERS_URL = "https://releases.hashicorp.com";
    public static final String DEFAULT_TERRAFORM_LOCAL_TYPE = "module";

    //pub
    public static final String DEFAULT_PUB_REGISTRY_URL = "https://pub.dartlang.org";

    //ml model
    public static final String DEFAULT_HUGGING_FACE_ML_REGISTRY_URL = "https://huggingface.co";

    //Ansible
    public static final String DEFAULT_ANSIBLE_REGISTRY_URL = "https://galaxy.ansible.com";

    // AI-Editor Extensions
    public static final String DEFAULT_VSCODE_REGISTRY_URL =
            "https://marketplace.visualstudio.com/_apis/public/gallery";
    public static final String DEFAULT_AI_EDITOR_EXTENSIONS_EXTERNAL_DEPENDENCIES_PATTERNS = "**/**vsassets.io/**";

    // Jetbrains
    public static final String DEFAULT_JETBRAINS_PLUGINS_REGISTRY_URL = "https://plugins.jetbrains.com";

    // LuaRocks
    public static final String DEFAULT_LUAROCKS_REGISTRY_URL = "https://luarocks.org";

    //Hex
    public static final String DEFAULT_HEX_REGISTRY_URL = "https://repo.hex.pm";

    public static final String DEFAULT_HEX_REGISTRY_PUBLIC_KEY = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApqREcFDt5vV21JVe2QNB
            Edvzk6w36aNFhVGWN5toNJRjRJ6m4hIuG4KaXtDWVLjnvct6MYMfqhC79HAGwyF+
            IqR6Q6a5bbFSsImgBJwz1oadoVKD6ZNetAuCIK84cjMrEFRkELtEIPNHblCzUkkM
            3rS9+DPlnfG8hBvGi6tvQIuZmXGCxF/73hU0/MyGhbmEjIKRtG6b0sJYKelRLTPW
            XgK7s5pESgiwf2YC/2MGDXjAJfpfCd0RpLdvd4eRiXtVlE9qO9bND94E7PgQ/xqZ
            J1i2xWFndWa6nfFnRxZmCStCOZWYYPlaxr+FZceFbpMwzTNs4g3d4tLNUcbKAIH4
            0wIDAQAB
            -----END PUBLIC KEY-----
            """;

    //NimModel
    public static final String DEFAULT_NIMMODEL_REGISTRY_URL = "https://api.ngc.nvidia.com";


    // example repository key
    public static final String EXAMPLE_REPO_KEY = "example-repo-local";
    public static final String POLICY_REPORTS_REPO_KEY = "jfrog-policy-reports-local";

    public static final Set<String> DEFAULT_URLS_LIST = Arrays.stream(DefaultUrl.values())
            .map(DefaultUrl::getUrl)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toSet());

    // default remote registry urls
    public enum DefaultUrl {
        BAZELMODULES(RepoType.BazelModules,
                List.of(RemoteRegistry.ofDefault(DEFAULT_BAZELMODULES_REGISTRY_URL, "BazelModules Repository"))),
        NIX(RepoType.Nix,
                List.of(RemoteRegistry.ofDefault(DEFAULT_NIX_REGISTRY_URL, "Nix Repository"))),
        AGENTPLUGINS(RepoType.AgentPlugins, List.of()),
        ALPINE(RepoType.Alpine, List.of(
                RemoteRegistry.ofDefault("http://dl-cdn.alpinelinux.org/alpine", "Alpine Linux Org"))),
        BOWER(RepoType.Bower, List.of(RemoteRegistry.ofDefault(Constants.GITHUB_URL, Constants.GITHUB_NAME))),
        COCOAPODS(RepoType.CocoaPods, List.of(RemoteRegistry.ofDefault(Constants.GITHUB_URL, Constants.GITHUB_NAME))),
        COMPOSER(RepoType.Composer, List.of(RemoteRegistry.ofDefault(Constants.GITHUB_URL, Constants.GITHUB_NAME))),
        CHEF(RepoType.Chef, List.of(RemoteRegistry.ofDefault("https://supermarket.chef.io", "Chef Supermarket"))),
        CARGO(RepoType.Cargo, List.of(
                RemoteRegistry.ofDefault("https://index.crates.io/", "crates.io"),
                RemoteRegistry.of(Constants.GITHUB_URL + "rust-lang/crates.io-index", "crates.io git"))),
        CONDA(RepoType.Conda,
                List.of(RemoteRegistry.ofDefault("https://repo.anaconda.com/pkgs/main", "Anaconda Repo"))),
        CONAN(RepoType.Conan, List.of(RemoteRegistry.ofDefault("https://center.conan.io/", "Conan Center"))),
        CRAN(RepoType.CRAN, List.of(
                RemoteRegistry.ofDefault("https://cran.r-project.org/", "CRAN Package Registry"))),
        DEBIAN(RepoType.Debian, List.of(
                RemoteRegistry.ofDefault("http://archive.ubuntu.com/ubuntu/", "Ubuntu Archive"),
                RemoteRegistry.of("https://deb.debian.org/debian/", "Debian Archive"))),
        DOCKER(RepoType.Docker, List.of(
                RemoteRegistry.ofDefault("https://registry-1.docker.io/", "Docker Hub"),
                RemoteRegistry.of("https://mcr.microsoft.com/", "Microsoft Artifact Registry"),
                RemoteRegistry.of("https://quay.io/", "RED HAT"))),
        OCI(RepoType.OCI, List.of(
                RemoteRegistry.ofDefault("https://registry-1.docker.io/", "Docker Hub"),
                RemoteRegistry.of("https://mcr.microsoft.com/", "Microsoft Artifact Registry"),
                RemoteRegistry.of("https://quay.io/", "RED HAT"))),
        HELMOCI(RepoType.HelmOCI, List.of(
                RemoteRegistry.ofDefault("https://registry-1.docker.io/", "Docker Hub"),
                RemoteRegistry.of("https://mcr.microsoft.com/", "Microsoft Artifact Registry"),
                RemoteRegistry.of("https://quay.io/", "RED HAT"))),
        GEMS(RepoType.Gems, List.of(RemoteRegistry.ofDefault("https://rubygems.org/", "RubyGems.org"))),
        GENERIC(RepoType.Generic, List.of()),
        GITLFS(RepoType.GitLfs, List.of()),
        GO(RepoType.Go, List.of(RemoteRegistry.ofDefault("https://proxy.golang.org/", "Go module proxy"))),
        GRADLE(RepoType.Gradle, List.of(RemoteRegistry.ofDefault(Constants.MAVEN_URL, Constants.MAVEN_NAME))),
        HELM(RepoType.Helm, List.of()),
        IVY(RepoType.Ivy, List.of(RemoteRegistry.ofDefault(Constants.MAVEN_URL, Constants.MAVEN_NAME))),
        MAVEN(RepoType.Maven, List.of(RemoteRegistry.ofDefault(Constants.MAVEN_URL, Constants.MAVEN_NAME))),
        NPM(RepoType.Npm, List.of(RemoteRegistry.ofDefault("https://registry.npmjs.org", "npm Registry"))),
        NUGET(RepoType.NuGet, List.of(RemoteRegistry.ofDefault("https://www.nuget.org/", "NuGet Gallery"))),
        OPKG(RepoType.Opkg, List.of(
                RemoteRegistry.ofDefault("https://downloads.openwrt.org/chaos_calmer/15.05.1/", "openwrt.org"))),
        P2(RepoType.P2, List.of(RemoteRegistry.ofDefault(Constants.MAVEN_URL, Constants.MAVEN_NAME))),
        PUPPET(RepoType.Puppet, List.of(RemoteRegistry.ofDefault("https://forgeapi.puppetlabs.com/", "Puppet Forge"))),
        PYPI(RepoType.Pypi,
                List.of(RemoteRegistry.ofDefault("https://files.pythonhosted.org", "Python Package Index"))),
        RPM(RepoType.YUM, List.of(RemoteRegistry.ofDefault("http://mirror.centos.org/centos/", "CentOS Mirror"))),
        SBT(RepoType.SBT, List.of(RemoteRegistry.ofDefault(Constants.MAVEN_URL, Constants.MAVEN_NAME))),
        VAGRANT(RepoType.Vagrant, List.of()),
        VCS(RepoType.VCS, List.of(RemoteRegistry.ofDefault(Constants.GITHUB_URL, Constants.GITHUB_NAME))),
        PUB(RepoType.Pub, List.of(RemoteRegistry.ofDefault(DEFAULT_PUB_REGISTRY_URL, "pub.dev"))),
        SWIFT(RepoType.Swift, List.of()),
        TERRAFORM(RepoType.Terraform, List.of(RemoteRegistry.ofDefault(Constants.GITHUB_URL, Constants.GITHUB_NAME))),
        HUGGINGFACEML(RepoType.HuggingFaceML,
                List.of(RemoteRegistry.ofDefault(DEFAULT_HUGGING_FACE_ML_REGISTRY_URL, "HuggingFace Hub"))),
        ANSIBLE(RepoType.Ansible, List.of(RemoteRegistry.ofDefault(DEFAULT_ANSIBLE_REGISTRY_URL, "Ansible Galaxy"))),
        HEX(RepoType.Hex,
                List.of(RemoteRegistry.ofDefault(DEFAULT_HEX_REGISTRY_URL, "Hex Package Manager Repository"))),
        NIMMODEL(RepoType.NimModel,
                List.of(RemoteRegistry.ofDefault(DEFAULT_NIMMODEL_REGISTRY_URL,
                        "NimModel Package Manager Repository"))),
        AIEDITOREXTENSIONS(RepoType.AIEditorExtensions,
                List.of(RemoteRegistry.ofDefault(DEFAULT_VSCODE_REGISTRY_URL, "AIEditorExtensions Repository"))),
        JETBRAINSPLUGINS(RepoType.JetBrainsPlugins,
                List.of(RemoteRegistry.ofDefault(DEFAULT_JETBRAINS_PLUGINS_REGISTRY_URL,
                        "JetBrains Plugins Repository"))),
        LUAROCKS(RepoType.LuaRocks,
                List.of(RemoteRegistry.ofDefault(DEFAULT_LUAROCKS_REGISTRY_URL, "LuaRocks Repository")));

        private final RepoType type;
        @Getter
        private final List<RemoteRegistry> remoteRegistries;
        private String url;
        private String aolUrl;

        DefaultUrl(RepoType type, List<RemoteRegistry> remoteRegistries) {
            this.type = type;
            if (remoteRegistries.isEmpty()) {
                this.url = StringUtils.EMPTY;
                this.aolUrl = null;
            }
            for (RemoteRegistry remoteRegistry : remoteRegistries) {
                switch (remoteRegistry.getDefaultState()) {
                    case ALL, ONPREM -> this.url = remoteRegistry.getUrl();
                    case AOL -> this.aolUrl = remoteRegistry.getUrl();
                    default -> {
                        // nothing
                    }
                }
            }
            this.remoteRegistries = remoteRegistries.stream()
                    .filter(item -> StringUtils.isNotEmpty(item.getUrl()))
                    .toList();
        }

        RepoType getType() {
            return type;
        }

        public String getUrl() {
            if (StringUtils.isNotBlank(this.aolUrl) && AolUtils.isAol()) {
                return this.aolUrl;
            }
            return this.url;
        }

        public static String urlByType(RepoType type) {
            return Arrays.stream(values())
                    .filter(defaultUrl -> defaultUrl.type.equals(type))
                    .findAny()
                    .map(DefaultUrl::getUrl).orElse(StringUtils.EMPTY);
        }

        public static List<RemoteRegistry> urlListByType(RepoType type) {
            return Arrays.stream(values())
                    .filter(defaultUrl -> defaultUrl.type.equals(type))
                    .findAny()
                    .map(DefaultUrl::getRemoteRegistries).orElse(List.of());
        }

        private static class Constants {
            static final String GITHUB_URL = "https://github.com/";
            static final String MAVEN_URL = "https://repo1.maven.org/maven2/";
            static final String GITHUB_NAME = "GitHub";
            static final String MAVEN_NAME = "Central - Maven Repository";
        }
    }
}