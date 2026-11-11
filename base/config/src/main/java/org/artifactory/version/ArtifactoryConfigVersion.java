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

package org.artifactory.version;

import org.artifactory.common.repo.PackageType;
import org.artifactory.version.converter.NamespaceConverter;
import org.artifactory.version.converter.PackageArtifactoryRequestsCanRetrieveRemoteArtifactConverter;
import org.artifactory.version.converter.SnapshotUniqueVersionConverter;
import org.artifactory.version.converter.XmlConverter;
import org.artifactory.version.converter.v100.BackupToElementConverter;
import org.artifactory.version.converter.v100.RepositoriesKeysConverter;
import org.artifactory.version.converter.v110.SnapshotNonUniqueValueConverter;
import org.artifactory.version.converter.v120.AnonAccessNameConverter;
import org.artifactory.version.converter.v130.AnnonAccessUnderSecurityConverter;
import org.artifactory.version.converter.v130.BackupListConverter;
import org.artifactory.version.converter.v130.LdapSettings130Converter;
import org.artifactory.version.converter.v131.LdapAuthenticationPatternsConverter;
import org.artifactory.version.converter.v132.BackupKeyConverter;
import org.artifactory.version.converter.v132.LdapListConverter;
import org.artifactory.version.converter.v134.BackupExcludedVirtualRepoConverter;
import org.artifactory.version.converter.v135.ProxyNTHostConverter;
import org.artifactory.version.converter.v136.IndexerCronRemoverConverter;
import org.artifactory.version.converter.v136.RepositoryTypeConverter;
import org.artifactory.version.converter.v141.ProxyDefaultConverter;
import org.artifactory.version.converter.v1410.GcSystemPropertyConverter;
import org.artifactory.version.converter.v1412.IndexerCronExpPropertyConverter;
import org.artifactory.version.converter.v1414.ArchiveBrowsingConverter;
import org.artifactory.version.converter.v1414.AssumedOfflineConverter;
import org.artifactory.version.converter.v1414.CleanupConfigConverter;
import org.artifactory.version.converter.v142.RepoIncludeExcludePatternsConverter;
import org.artifactory.version.converter.v143.RemoteChecksumPolicyConverter;
import org.artifactory.version.converter.v144.MultiLdapXmlConverter;
import org.artifactory.version.converter.v144.ServerIdXmlConverter;
import org.artifactory.version.converter.v147.DefaultRepoLayoutConverter;
import org.artifactory.version.converter.v147.JfrogRemoteRepoUrlConverter;
import org.artifactory.version.converter.v147.UnusedArtifactCleanupSwitchConverter;
import org.artifactory.version.converter.v149.ReplicationElementNameConverter;
import org.artifactory.version.converter.v152.BlackDuckProxyConverter;
import org.artifactory.version.converter.v153.VirtualCacheCleanupConverter;
import org.artifactory.version.converter.v160.AddonsDefaultLayoutConverter;
import org.artifactory.version.converter.v160.MavenIndexerConverter;
import org.artifactory.version.converter.v160.SingleRepoTypeConverter;
import org.artifactory.version.converter.v160.SuppressConsitencyConverter;
import org.artifactory.version.converter.v162.FolderDownloadConfigConverter;
import org.artifactory.version.converter.v166.SourceDeletedDetectionConverter;
import org.artifactory.version.converter.v167.TrashcanConfigConverter;
import org.artifactory.version.converter.v167.UserLockConfigConverter;
import org.artifactory.version.converter.v168.PasswordPolicyConverter;
import org.artifactory.version.converter.v169.PasswordMaxAgeConverter;
import org.artifactory.version.converter.v171.SimpleLayoutConverter;
import org.artifactory.version.converter.v172.BlockMismatchingMimeTypesConverter;
import org.artifactory.version.converter.v175.DockerForceAuthRemovalConverter;
import org.artifactory.version.converter.v177.LdapPoisoningProtectionConverter;
import org.artifactory.version.converter.v178.SigningKeysConverter;
import org.artifactory.version.converter.v180.ExternalProvidersRemovalConverter;
import org.artifactory.version.converter.v180.XrayRepoConfigConverter;
import org.artifactory.version.converter.v181.ComposerDefaultLayoutConverter;
import org.artifactory.version.converter.v182.ConanDefaultLayoutConverter;
import org.artifactory.version.converter.v201.PuppetDefaultLayoutConverter;
import org.artifactory.version.converter.v204.AccessTokenSettingsRenameToAccessClientSettingsConverter;
import org.artifactory.version.converter.v204.EventBasedRemoteReplicationConverter;
import org.artifactory.version.converter.v205.YumEnableFilelistsIndexingForExistingLocalReposConverter;
import org.artifactory.version.converter.v207.FolderDownloadForAnonymousConfigConverter;
import org.artifactory.version.converter.v207.RemoveAccessAdminCredentialsConverter;
import org.artifactory.version.converter.v208.AddReplicationKey;
import org.artifactory.version.converter.v211.XrayMinBlockedSeverityAndBlockUnscannedConverter;
import org.artifactory.version.converter.v212.PyPIRegistryUrlConverter;
import org.artifactory.version.converter.v213.AllowCrowdUsersToAccessProfilePageConverter;
import org.artifactory.version.converter.v213.GoDefaultLayoutConverter;
import org.artifactory.version.converter.v214.NuGetV3Converter;
import org.artifactory.version.converter.v215.ConanDefaultDistributionRuleConverter;
import org.artifactory.version.converter.v215.ConanFixDefaultLayoutConverter;
import org.artifactory.version.converter.v218.AnonAccessToBuildsConverter;
import org.artifactory.version.converter.v218.BackupSettingConvert;
import org.artifactory.version.converter.v218.BuildDefaultLayoutConverter;
import org.artifactory.version.converter.v219.ConanV2DefaultLayoutConverter;
import org.artifactory.version.converter.v220.DownloadRedirectConverter;
import org.artifactory.version.converter.v221.PyPIRegistrySuffixConverter;
import org.artifactory.version.converter.v224.EnablePushingSchema1DockerConverter;
import org.artifactory.version.converter.v300.DefaultProxyToPlatformConverter;
import org.artifactory.version.converter.v300.FixGoDefaultLayoutConverter;
import org.artifactory.version.converter.v300.RemoveXrayProxyConfigConverter;
import org.artifactory.version.converter.v302.FixNpmDefaultLayoutConverter;
import org.artifactory.version.converter.v304.RemoveTaskAffinityConverter;
import org.artifactory.version.converter.v305.AddPagingSupportEnabledConverter;
import org.artifactory.version.converter.v309.AddVerifyAudienceRestrictionConverter;
import org.artifactory.version.converter.v310.PropertyDuplicationsConverter;
import org.artifactory.version.converter.v311.DockerRemoteRepositoryCacheRetrievalConverter;
import org.artifactory.version.converter.v3110.AddCargoDefaultLayoutConverter;
import org.artifactory.version.converter.v3111.RemoveKeyPairFromRemoteRepositoriesConverter;
import org.artifactory.version.converter.v3113.AddMetadataTimeout;
import org.artifactory.version.converter.v3116.ComposerLocalRepositoryEnableMetadataV1Converter;
import org.artifactory.version.converter.v3116.RepositoryXrayDataTtlConverter;
import org.artifactory.version.converter.v3119.RemoveArtifactLifecycleSettingsConverter;
import org.artifactory.version.converter.v312.AddBasicAuthenticationEnabledConverter;
import org.artifactory.version.converter.v312.FixConanDefaultLayoutConverter;
import org.artifactory.version.converter.v3120.ReplicationPathPrefixConverter;
import org.artifactory.version.converter.v3123.AddTerraformDefaultLayoutConverter;
import org.artifactory.version.converter.v3124.AddSwiftDefaultLayoutConverter;
import org.artifactory.version.converter.v3126.NugetSymbolServerUrlConverter;
import org.artifactory.version.converter.v3127.FixSwiftDefaultLayoutConverter;
import org.artifactory.version.converter.v313.DistributionReposToLocalConverter;
import org.artifactory.version.converter.v313.DockerLocalRepositoryRetentionPolicyConverter;
import org.artifactory.version.converter.v3132.AddArtifactsSettingsConverter;
import org.artifactory.version.converter.v3134.CopyPasswordExpirationPolicyToConfigServiceConverter;
import org.artifactory.version.converter.v3134.MoveUserLockPolicyAndAnonAccessSettingToConfigServiceConverter;
import org.artifactory.version.converter.v3134.RemoveAccessClientSettingConverter;
import org.artifactory.version.converter.v315.ArchiveIndexEnableConverter;
import org.artifactory.version.converter.v319.GpgToDefaultGpgKeyPairConverter;
import org.artifactory.version.converter.v322.MoveBasicAuthenticationEnabledToConfigServiceConverter;
import org.artifactory.version.converter.v322.MovePasswordEncryptionPolicyToConfigServiceConverter;
import org.artifactory.version.converter.v324.MigrateConfigDescriptorFooterFieldsConverter;
import org.artifactory.version.converter.v325.MigrateSsoHttpToConfigServiceConverter;
import org.artifactory.version.converter.v326.MoveLdapGroupSettingToConfigServiceConverter;
import org.artifactory.version.converter.v326.MoveLdapSettingToConfigServiceConverter;
import org.artifactory.version.converter.v327.MigrateCrowdSettingsToAccessServiceConverter;
import org.artifactory.version.converter.v329.XrayUnavailableConverter;
import org.artifactory.version.converter.v330.MigrateContantsToAccessConverter;
import org.artifactory.version.converter.v332.SetXrayTokenIdentificationToTrueConverter;
import org.artifactory.version.converter.v333.ItemsBrowserPagingSizeConvertor;
import org.artifactory.version.converter.v334.MoveSamlSettingToAccessConverter;
import org.artifactory.version.converter.v335.CalculateRepositoryAssociationsConvertor;
import org.artifactory.version.converter.v336.MigrateSamlEncryptionToAccessConverter;
import org.artifactory.version.converter.v337.UserLockConvertor;
import org.artifactory.version.converter.v3370.AddAnsibleDefaultLayoutConverter;
import org.artifactory.version.converter.v338.RemoveCleanupPoliciesSettingsConvertor;
import org.artifactory.version.converter.v339.RemoveCalculateRepositoryAssociationsConvertor;
import org.artifactory.version.converter.v345.ArchiveIndexDisableConverter;
import org.artifactory.version.converter.v347.MoveOAuthSettingsToAccessConverter;
import org.artifactory.version.converter.v349.AddNixDefaultLayoutConverter;
import org.artifactory.version.converter.v352.AddSkillsDefaultLayoutConverter;
import org.artifactory.version.converter.v353.AddAgentPluginsDefaultLayoutConverter;
import org.artifactory.version.converter.v353.AddAgentPackagesDefaultLayoutConverter;
import org.artifactory.version.converter.v355.AddLuaRocksDefaultLayoutConverter;
import org.artifactory.version.converter.v357.MigrateAgentResourcesToAgentPackagesConverter;
import org.artifactory.version.converter.v359.HuggingFaceXetEnabledConverter;
import org.artifactory.version.converter.v360.RemoveAgentResourcesDefaultLayoutConverter;
import org.artifactory.version.converter.v3637.RemoveArtifactsSettingsConvertor;
import org.artifactory.version.converter.v3638.AddSbtIvyDefaultLayoutConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * NOTE:
 * 1. Each version declares the converters that should run when moving forward *from* it, meaning for example that
 * v180 starts at 4.12.1 (denoted v4121) up to 4.13.2 and the converter that needs to run for it
 * is XrayRepoConfigConverter (which is declared in v171) --> when moving forward from it to v181
 * ComposerDefaultLayoutConverter will run.
 *
 * 2. If a converter has been cherry picked to/from a previous version, make sure to have the schema version which was
 * added to the older version - otherwise, the newer version would not recognize the incremented schema version from
 * the older version.
 *
 * @author freds
 * @author Yossi Shaul
 */
public enum ArtifactoryConfigVersion implements VersionWrapper {
    v100("http://artifactory.jfrog.org/xsd/1.0.0",
            "http://www.jfrog.org/xsd/artifactory-v1_0_0.xsd",
            ArtifactoryServiceVersionProvider.v122rc0.get(),
            new SnapshotUniqueVersionConverter(),
            new BackupToElementConverter(),
            new RepositoriesKeysConverter()),
    v110("http://artifactory.jfrog.org/xsd/1.1.0",
            "http://www.jfrog.org/xsd/artifactory-v1_1_0.xsd",
            ArtifactoryServiceVersionProvider.v125.get(),
            new SnapshotNonUniqueValueConverter()),
    v120("http://artifactory.jfrog.org/xsd/1.2.0",
            "http://www.jfrog.org/xsd/artifactory-v1_2_0.xsd",
            ArtifactoryServiceVersionProvider.v125u1.get(),
            new AnonAccessNameConverter()),
    v130("http://artifactory.jfrog.org/xsd/1.3.0",
            "http://www.jfrog.org/xsd/artifactory-v1_3_0.xsd",
            ArtifactoryServiceVersionProvider.v130beta1.get(),
            new BackupListConverter(), new AnnonAccessUnderSecurityConverter(),
            new LdapSettings130Converter()),
    v131("http://artifactory.jfrog.org/xsd/1.3.1",
            "http://www.jfrog.org/xsd/artifactory-v1_3_1.xsd",
            ArtifactoryServiceVersionProvider.v130beta3.get(),
            new LdapAuthenticationPatternsConverter()),
    v132("http://artifactory.jfrog.org/xsd/1.3.2",
            "http://www.jfrog.org/xsd/artifactory-v1_3_2.xsd",
            ArtifactoryServiceVersionProvider.v130beta4.get(),
            new BackupKeyConverter(), new LdapListConverter()),
    v133("http://artifactory.jfrog.org/xsd/1.3.3",
            "http://www.jfrog.org/xsd/artifactory-v1_3_3.xsd",
            ArtifactoryServiceVersionProvider.v130beta5.get()),
    v134("http://artifactory.jfrog.org/xsd/1.3.4",
            "http://www.jfrog.org/xsd/artifactory-v1_3_4.xsd",
            ArtifactoryServiceVersionProvider.v130rc1.get(),
            new BackupExcludedVirtualRepoConverter()),
    v135("http://artifactory.jfrog.org/xsd/1.3.5",
            "http://www.jfrog.org/xsd/artifactory-v1_3_5.xsd",
            ArtifactoryServiceVersionProvider.v130rc2.get(),
            new ProxyNTHostConverter()),
    v136("http://artifactory.jfrog.org/xsd/1.3.6",
            "http://www.jfrog.org/xsd/artifactory-v1_3_6.xsd",
            ArtifactoryServiceVersionProvider.v206.get(),
            new IndexerCronRemoverConverter(), new RepositoryTypeConverter()),
    v140("http://artifactory.jfrog.org/xsd/1.4.0",
            "http://www.jfrog.org/xsd/artifactory-v1_4_0.xsd",
            ArtifactoryServiceVersionProvider.v210.get(),
            new ProxyDefaultConverter()),
    v141("http://artifactory.jfrog.org/xsd/1.4.1",
            "http://www.jfrog.org/xsd/artifactory-v1_4_1.xsd",
            ArtifactoryServiceVersionProvider.v211.get(),
            new RepoIncludeExcludePatternsConverter()),
    v142("http://artifactory.jfrog.org/xsd/1.4.2",
            "http://www.jfrog.org/xsd/artifactory-v1_4_2.xsd",
            ArtifactoryServiceVersionProvider.v213.get(),
            new RemoteChecksumPolicyConverter()),
    v143("http://artifactory.jfrog.org/xsd/1.4.3",
            "http://www.jfrog.org/xsd/artifactory-v1_4_3.xsd",
            ArtifactoryServiceVersionProvider.v222.get(),
            new MultiLdapXmlConverter(), new ServerIdXmlConverter()),
    v144("http://artifactory.jfrog.org/xsd/1.4.4",
            "http://www.jfrog.org/xsd/artifactory-v1_4_4.xsd",
            ArtifactoryServiceVersionProvider.v224.get()),
    v145("http://artifactory.jfrog.org/xsd/1.4.5",
            "http://www.jfrog.org/xsd/artifactory-v1_4_5.xsd",
            ArtifactoryServiceVersionProvider.v230.get()),
    v146("http://artifactory.jfrog.org/xsd/1.4.6",
            "http://www.jfrog.org/xsd/artifactory-v1_4_6.xsd",
            ArtifactoryServiceVersionProvider.v231.get(),
            new JfrogRemoteRepoUrlConverter(), new DefaultRepoLayoutConverter(),
            new UnusedArtifactCleanupSwitchConverter()),
    v147("http://artifactory.jfrog.org/xsd/1.4.7",
            "http://www.jfrog.org/xsd/artifactory-v1_4_7.xsd",
            ArtifactoryServiceVersionProvider.v232.get()),
    v148("http://artifactory.jfrog.org/xsd/1.4.8",
            "http://www.jfrog.org/xsd/artifactory-v1_4_8.xsd",
            ArtifactoryServiceVersionProvider.v233.get(),
            new ReplicationElementNameConverter()),
    v149("http://artifactory.jfrog.org/xsd/1.4.9",
            "http://www.jfrog.org/xsd/artifactory-v1_4_9.xsd",
            ArtifactoryServiceVersionProvider.v234.get(),
            new GcSystemPropertyConverter()),
    v1410("http://artifactory.jfrog.org/xsd/1.4.10",
            "http://www.jfrog.org/xsd/artifactory-v1_4_10.xsd",
            ArtifactoryServiceVersionProvider.v240.get()),
    v1411("http://artifactory.jfrog.org/xsd/1.4.11",
            "http://www.jfrog.org/xsd/artifactory-v1_4_11.xsd",
            ArtifactoryServiceVersionProvider.v250.get(),
            new IndexerCronExpPropertyConverter()),
    v1412("http://artifactory.jfrog.org/xsd/1.4.12",
            "http://www.jfrog.org/xsd/artifactory-v1_4_12.xsd",
            ArtifactoryServiceVersionProvider.v251.get()),
    v1413("http://artifactory.jfrog.org/xsd/1.4.13",
            "http://www.jfrog.org/xsd/artifactory-v1_4_13.xsd",
            ArtifactoryServiceVersionProvider.v252.get(),
            new CleanupConfigConverter(), new AssumedOfflineConverter(),
            new ArchiveBrowsingConverter()),
    v1414("http://artifactory.jfrog.org/xsd/1.4.14",
            "http://www.jfrog.org/xsd/artifactory-v1_4_14.xsd",
            ArtifactoryServiceVersionProvider.v260.get()),
    v1415("http://artifactory.jfrog.org/xsd/1.4.15",
            "http://www.jfrog.org/xsd/artifactory-v1_4_15.xsd",
            ArtifactoryServiceVersionProvider.v262.get()),
    v1416("http://artifactory.jfrog.org/xsd/1.4.16",
            "http://www.jfrog.org/xsd/artifactory-v1_4_16.xsd",
            ArtifactoryServiceVersionProvider.v264.get()),
    v1417("http://artifactory.jfrog.org/xsd/1.4.17",
            "http://www.jfrog.org/xsd/artifactory-v1_4_17.xsd",
            ArtifactoryServiceVersionProvider.v265.get()),
    v1418("http://artifactory.jfrog.org/xsd/1.4.18",
            "http://www.jfrog.org/xsd/artifactory-v1_4_18.xsd",
            ArtifactoryServiceVersionProvider.v266.get()),
    v150("http://artifactory.jfrog.org/xsd/1.5.0",
            "http://www.jfrog.org/xsd/artifactory-v1_5_0.xsd",
            ArtifactoryServiceVersionProvider.v300.get()),
    v151("http://artifactory.jfrog.org/xsd/1.5.1",
            "http://www.jfrog.org/xsd/artifactory-v1_5_1.xsd",
            ArtifactoryServiceVersionProvider.v3021.get()),
    v152("http://artifactory.jfrog.org/xsd/1.5.2",
            "http://www.jfrog.org/xsd/artifactory-v1_5_2.xsd",
            ArtifactoryServiceVersionProvider.v304.get()
            , new BlackDuckProxyConverter()),
    v153("http://artifactory.jfrog.org/xsd/1.5.3",
            "http://www.jfrog.org/xsd/artifactory-v1_5_3.xsd",
            ArtifactoryServiceVersionProvider.v310.get(),
            new VirtualCacheCleanupConverter()),
    v154("http://artifactory.jfrog.org/xsd/1.5.4",
            "http://www.jfrog.org/xsd/artifactory-v1_5_4.xsd",
            ArtifactoryServiceVersionProvider.v320.get()),
    v155("http://artifactory.jfrog.org/xsd/1.5.5",
            "http://www.jfrog.org/xsd/artifactory-v1_5_5.xsd",
            ArtifactoryServiceVersionProvider.v330.get()),
    v156("http://artifactory.jfrog.org/xsd/1.5.6",
            "http://www.jfrog.org/xsd/artifactory-v1_5_6.xsd",
            ArtifactoryServiceVersionProvider.v340.get()),
    v157("http://artifactory.jfrog.org/xsd/1.5.7",
            "http://www.jfrog.org/xsd/artifactory-v1_5_7.xsd",
            ArtifactoryServiceVersionProvider.v342.get()),
    v158("http://artifactory.jfrog.org/xsd/1.5.8",
            "http://www.jfrog.org/xsd/artifactory-v1_5_8.xsd",
            ArtifactoryServiceVersionProvider.v350.get()),
    v159("http://artifactory.jfrog.org/xsd/1.5.9",
            "http://www.jfrog.org/xsd/artifactory-v1_5_9.xsd",
            ArtifactoryServiceVersionProvider.v351.get()),
    v1510("http://artifactory.jfrog.org/xsd/1.5.10",
            "http://www.jfrog.org/xsd/artifactory-v1_5_10.xsd",
            ArtifactoryServiceVersionProvider.v360.get()),
    v1511("http://artifactory.jfrog.org/xsd/1.5.11",
            "http://www.jfrog.org/xsd/artifactory-v1_5_11.xsd",
            ArtifactoryServiceVersionProvider.v370.get()),
    v1512("http://artifactory.jfrog.org/xsd/1.5.12",
            "http://www.jfrog.org/xsd/artifactory-v1_5_12.xsd",
            ArtifactoryServiceVersionProvider.v380.get()),
    v1513("http://artifactory.jfrog.org/xsd/1.5.13",
            "http://www.jfrog.org/xsd/artifactory-v1_5_13.xsd",
            ArtifactoryServiceVersionProvider.v390.get(), new AddonsDefaultLayoutConverter(),
            new SingleRepoTypeConverter(),
            new SuppressConsitencyConverter(), new MavenIndexerConverter()),
    v160("http://artifactory.jfrog.org/xsd/1.6.0",
            "http://www.jfrog.org/xsd/artifactory-v1_6_0.xsd",
            ArtifactoryServiceVersionProvider.v400.get()),
    v161("http://artifactory.jfrog.org/xsd/1.6.1",
            "http://www.jfrog.org/xsd/artifactory-v1_6_1.xsd",
            ArtifactoryServiceVersionProvider.v401.get(),
            new FolderDownloadConfigConverter()),
    v162("http://artifactory.jfrog.org/xsd/1.6.2",
            "http://www.jfrog.org/xsd/artifactory-v1_6_2.xsd",
            ArtifactoryServiceVersionProvider.v410.get()),
    v163("http://artifactory.jfrog.org/xsd/1.6.3",
            "http://www.jfrog.org/xsd/artifactory-v1_6_3.xsd",
            ArtifactoryServiceVersionProvider.v413.get()),
    v164("http://artifactory.jfrog.org/xsd/1.6.4",
            "http://www.jfrog.org/xsd/artifactory-v1_6_4.xsd",
            ArtifactoryServiceVersionProvider.v430.get()),
    v165("http://artifactory.jfrog.org/xsd/1.6.5",
            "http://www.jfrog.org/xsd/artifactory-v1_6_5.xsd",
            ArtifactoryServiceVersionProvider.v431.get(),
            new SourceDeletedDetectionConverter()),
    v166("http://artifactory.jfrog.org/xsd/1.6.6",
            "http://www.jfrog.org/xsd/artifactory-v1_6_6.xsd",
            ArtifactoryServiceVersionProvider.v433.get(),
            new UserLockConfigConverter(), new TrashcanConfigConverter()),
    v167("http://artifactory.jfrog.org/xsd/1.6.7",
            "http://www.jfrog.org/xsd/artifactory-v1_6_7.xsd",
            ArtifactoryServiceVersionProvider.v440.get(),
            new PasswordPolicyConverter()),
    v168("http://artifactory.jfrog.org/xsd/1.6.8",
            "http://www.jfrog.org/xsd/artifactory-v1_6_8.xsd",
            ArtifactoryServiceVersionProvider.v441.get(),
            new PasswordMaxAgeConverter()),
    v169("http://artifactory.jfrog.org/xsd/1.6.9",
            "http://www.jfrog.org/xsd/artifactory-v1_6_9.xsd",
            ArtifactoryServiceVersionProvider.v442.get()),
    v170("http://artifactory.jfrog.org/xsd/1.7.0",
            "http://www.jfrog.org/xsd/artifactory-v1_7_0.xsd",
            ArtifactoryServiceVersionProvider.v443.get(), new SimpleLayoutConverter()),
    v171("http://artifactory.jfrog.org/xsd/1.7.1",
            "http://www.jfrog.org/xsd/artifactory-v1_7_1.xsd",
            ArtifactoryServiceVersionProvider.v460.get(), new BlockMismatchingMimeTypesConverter()),
    v172("http://artifactory.jfrog.org/xsd/1.7.2",
            "http://www.jfrog.org/xsd/artifactory-v1_7_2.xsd",
            ArtifactoryServiceVersionProvider.v461.get()),
    v173("http://artifactory.jfrog.org/xsd/1.7.3",
            "http://www.jfrog.org/xsd/artifactory-v1_7_3.xsd",
            ArtifactoryServiceVersionProvider.v471.get()),
    v174("http://artifactory.jfrog.org/xsd/1.7.4",
            "http://www.jfrog.org/xsd/artifactory-v1_7_4.xsd",
            ArtifactoryServiceVersionProvider.v480.get(),
            new DockerForceAuthRemovalConverter()),
    v175("http://artifactory.jfrog.org/xsd/1.7.5",
            "http://www.jfrog.org/xsd/artifactory-v1_7_5.xsd",
            ArtifactoryServiceVersionProvider.v490.get()),
    v176("http://artifactory.jfrog.org/xsd/1.7.6",
            "http://www.jfrog.org/xsd/artifactory-v1_7_6.xsd",
            ArtifactoryServiceVersionProvider.v4100.get(),
            new LdapPoisoningProtectionConverter()),
    v177("http://artifactory.jfrog.org/xsd/1.7.7",
            "http://www.jfrog.org/xsd/artifactory-v1_7_7.xsd",
            ArtifactoryServiceVersionProvider.v4110.get(),
            new SigningKeysConverter()),
    v178("http://artifactory.jfrog.org/xsd/1.7.8",
            "http://www.jfrog.org/xsd/artifactory-v1_7_8.xsd",
            ArtifactoryServiceVersionProvider.v4111.get()),
    v179("http://artifactory.jfrog.org/xsd/1.7.9",
            "http://www.jfrog.org/xsd/artifactory-v1_7_9.xsd",
            ArtifactoryServiceVersionProvider.v4120.get(),
            new XrayRepoConfigConverter()),
    v180("http://artifactory.jfrog.org/xsd/1.8.0",
            "http://www.jfrog.org/xsd/artifactory-v1_8_0.xsd",
            ArtifactoryServiceVersionProvider.v4121.get(),
            new ComposerDefaultLayoutConverter()),
    v181("http://artifactory.jfrog.org/xsd/1.8.1",
            "http://www.jfrog.org/xsd/artifactory-v1_8_1.xsd",
            ArtifactoryServiceVersionProvider.v4140.get(),
            new ConanDefaultLayoutConverter()),
    v182("http://artifactory.jfrog.org/xsd/1.8.2",
            "http://www.jfrog.org/xsd/artifactory-v1_8_2.xsd",
            ArtifactoryServiceVersionProvider.v4150.get(),
            new ExternalProvidersRemovalConverter()),
    v200("http://artifactory.jfrog.org/xsd/2.0.0",
            "http://www.jfrog.org/xsd/artifactory-v2_0_0.xsd",
            ArtifactoryServiceVersionProvider.v500beta1.get(),
            new PuppetDefaultLayoutConverter()),
    v201("http://artifactory.jfrog.org/xsd/2.0.1",
            "http://www.jfrog.org/xsd/artifactory-v2_0_1.xsd",
            ArtifactoryServiceVersionProvider.v500rc6.get()),
    v202("http://artifactory.jfrog.org/xsd/2.0.2",
            "http://www.jfrog.org/xsd/artifactory-v2_0_2.xsd",
            ArtifactoryServiceVersionProvider.v521m003.get()),
    v203("http://artifactory.jfrog.org/xsd/2.0.3",
            "http://www.jfrog.org/xsd/artifactory-v2_0_3.xsd",
            ArtifactoryServiceVersionProvider.v522m001.get(),
            new AccessTokenSettingsRenameToAccessClientSettingsConverter()),
    v204("http://artifactory.jfrog.org/xsd/2.0.4",
            "http://www.jfrog.org/xsd/artifactory-v2_0_4.xsd",
            ArtifactoryServiceVersionProvider.v540m001.get(),
            new YumEnableFilelistsIndexingForExistingLocalReposConverter()),
    v205("http://artifactory.jfrog.org/xsd/2.0.5",
            "http://www.jfrog.org/xsd/artifactory-v2_0_5.xsd",
            ArtifactoryServiceVersionProvider.v540.get(),
            new EventBasedRemoteReplicationConverter()),
    v206("http://artifactory.jfrog.org/xsd/2.0.6",
            "http://www.jfrog.org/xsd/artifactory-v2_0_6.xsd",
            ArtifactoryServiceVersionProvider.v550m001.get()),
    v207("http://artifactory.jfrog.org/xsd/2.0.7",
            "http://www.jfrog.org/xsd/artifactory-v2_0_7.xsd",
            ArtifactoryServiceVersionProvider.v552m001.get(),
            new RemoveAccessAdminCredentialsConverter(),
            new FolderDownloadForAnonymousConfigConverter()),
    v208("http://artifactory.jfrog.org/xsd/2.0.8",
            "http://www.jfrog.org/xsd/artifactory-v2_0_8.xsd",
            ArtifactoryServiceVersionProvider.v560m001.get(),
            new AddReplicationKey()),
    v209("http://artifactory.jfrog.org/xsd/2.0.9",
            "http://www.jfrog.org/xsd/artifactory-v2_0_9.xsd",
            ArtifactoryServiceVersionProvider.v570m001.get()),
    v210("http://artifactory.jfrog.org/xsd/2.1.0",
            "http://www.jfrog.org/xsd/artifactory-v2_1_0.xsd",
            ArtifactoryServiceVersionProvider.v580.get(),
            new XrayMinBlockedSeverityAndBlockUnscannedConverter()),
    v211("http://artifactory.jfrog.org/xsd/2.1.1",
            "http://www.jfrog.org/xsd/artifactory-v2_1_1.xsd",
            ArtifactoryServiceVersionProvider.v5100m009.get(),
            new PyPIRegistryUrlConverter()),
    v212("http://artifactory.jfrog.org/xsd/2.1.2",
            "http://www.jfrog.org/xsd/artifactory-v2_1_2.xsd",
            ArtifactoryServiceVersionProvider.v5103.get(),
            new AllowCrowdUsersToAccessProfilePageConverter(),
            new GoDefaultLayoutConverter()),
    v213("http://artifactory.jfrog.org/xsd/2.1.3",
            "http://www.jfrog.org/xsd/artifactory-v2_1_3.xsd",
            ArtifactoryServiceVersionProvider.v5110.get(),
            new NuGetV3Converter()),
    v214("http://artifactory.jfrog.org/xsd/2.1.4",
            "http://www.jfrog.org/xsd/artifactory-v2_1_4.xsd",
            ArtifactoryServiceVersionProvider.v600m023.get(),
            new ConanFixDefaultLayoutConverter(), new ConanDefaultDistributionRuleConverter()),
    v215("http://artifactory.jfrog.org/xsd/2.1.5",
            "http://www.jfrog.org/xsd/artifactory-v2_1_5.xsd",
            ArtifactoryServiceVersionProvider.v610m002.get()),
    v216("http://artifactory.jfrog.org/xsd/2.1.6",
            "http://www.jfrog.org/xsd/artifactory-v2_1_6.xsd",
            ArtifactoryServiceVersionProvider.v640m007.get()),
    v217("http://artifactory.jfrog.org/xsd/2.1.7",
            "http://www.jfrog.org/xsd/artifactory-v2_1_7.xsd",
            ArtifactoryServiceVersionProvider.v658.get(),
            new BackupSettingConvert(), new AnonAccessToBuildsConverter(), new BuildDefaultLayoutConverter()),
    v218("http://artifactory.jfrog.org/xsd/2.1.8",
            "http://www.jfrog.org/xsd/artifactory-v2_1_8.xsd",
            ArtifactoryServiceVersionProvider.v660m001.get()),
    v219("http://artifactory.jfrog.org/xsd/2.1.9",
            "http://www.jfrog.org/xsd/artifactory-v2_1_9.xsd",
            ArtifactoryServiceVersionProvider.v665.get(),
            new DownloadRedirectConverter(), new ConanV2DefaultLayoutConverter()),
    v220("http://artifactory.jfrog.org/xsd/2.2.0",
            "http://www.jfrog.org/xsd/artifactory-v2_2_0.xsd",
            ArtifactoryServiceVersionProvider.v690m001.get(), new PyPIRegistrySuffixConverter()),
    v221("http://artifactory.jfrog.org/xsd/2.2.1",
            "http://www.jfrog.org/xsd/artifactory-v2_2_1.xsd",
            ArtifactoryServiceVersionProvider.v6103.get()),
    v222("http://artifactory.jfrog.org/xsd/2.2.2",
            "http://www.jfrog.org/xsd/artifactory-v2_2_2.xsd",
            ArtifactoryServiceVersionProvider.v6110m001.get()),
    v223("http://artifactory.jfrog.org/xsd/2.2.3",
            "http://www.jfrog.org/xsd/artifactory-v2_2_3.xsd",
            ArtifactoryServiceVersionProvider.v6120.get(),
            new EnablePushingSchema1DockerConverter()),
    v224("http://artifactory.jfrog.org/xsd/2.2.4",
            "http://www.jfrog.org/xsd/artifactory-v2_2_4.xsd",
            ArtifactoryServiceVersionProvider.v6150.get(),
            new FixGoDefaultLayoutConverter()),
    v225("http://artifactory.jfrog.org/xsd/2.2.5",
            "http://www.jfrog.org/xsd/artifactory-v2_2_5.xsd",
            ArtifactoryServiceVersionProvider.v6190.get()),
    v226("http://artifactory.jfrog.org/xsd/2.2.6",
            "http://www.jfrog.org/xsd/artifactory-v2_2_6.xsd",
            ArtifactoryServiceVersionProvider.v6210m007.get()),
    v227("http://artifactory.jfrog.org/xsd/2.2.7",
            "http://www.jfrog.org/xsd/artifactory-v2_2_7.xsd",
            ArtifactoryServiceVersionProvider.v6230m001.get()),
    v228("http://artifactory.jfrog.org/xsd/2.2.8",
            "http://www.jfrog.org/xsd/artifactory-v2_2_8.xsd",
            ArtifactoryServiceVersionProvider.v6230m002.get()),
    v229("http://artifactory.jfrog.org/xsd/2.2.9",
            "http://www.jfrog.org/xsd/artifactory-v2_2_9.xsd",
            ArtifactoryServiceVersionProvider.v6231m009.get()),
    v230("http://artifactory.jfrog.org/xsd/2.3.0",
            "http://www.jfrog.org/xsd/artifactory-v2_3_0.xsd",
            ArtifactoryServiceVersionProvider.v6234m011.get()),
    v231("http://artifactory.jfrog.org/xsd/2.3.1",
            "http://www.jfrog.org/xsd/artifactory-v2_3_1.xsd",
            ArtifactoryServiceVersionProvider.v62312.get()),
    v232("http://artifactory.jfrog.org/xsd/2.3.2",
            "http://www.jfrog.org/xsd/artifactory-v2_3_2.xsd",
            ArtifactoryServiceVersionProvider.v62320m003.get()),
    v233("http://artifactory.jfrog.org/xsd/2.3.3",
            "http://www.jfrog.org/xsd/artifactory-v2_3_3.xsd",
            ArtifactoryServiceVersionProvider.v62324m009.get()),
    v300("http://artifactory.jfrog.org/xsd/3.0.0",
            "http://www.jfrog.org/xsd/artifactory-v3_0_0.xsd",
            ArtifactoryServiceVersionProvider.v700m001.get(), new RemoveXrayProxyConfigConverter(),
            new DefaultProxyToPlatformConverter()),
    v301("http://artifactory.jfrog.org/xsd/3.0.1",
            "http://www.jfrog.org/xsd/artifactory-v3_0_1.xsd",
            ArtifactoryServiceVersionProvider.v700m420.get()),
    v302("http://artifactory.jfrog.org/xsd/3.0.2",
            "http://www.jfrog.org/xsd/artifactory-v3_0_2.xsd",
            ArtifactoryServiceVersionProvider.v740m081.get(), new FixNpmDefaultLayoutConverter()),
    v303("http://artifactory.jfrog.org/xsd/3.0.3",
            "http://www.jfrog.org/xsd/artifactory-v3_0_3.xsd",
            ArtifactoryServiceVersionProvider.v750m129.get(), new RemoveTaskAffinityConverter()),
    v304("http://artifactory.jfrog.org/xsd/3.0.4",
            "http://www.jfrog.org/xsd/artifactory-v3_0_4.xsd",
            ArtifactoryServiceVersionProvider.v760m001.get(), new AddPagingSupportEnabledConverter()),
    v305("http://artifactory.jfrog.org/xsd/3.0.5",
            "http://www.jfrog.org/xsd/artifactory-v3_0_5.xsd",
            ArtifactoryServiceVersionProvider.v770m038.get()),
    v306("http://artifactory.jfrog.org/xsd/3.0.6",
            "http://www.jfrog.org/xsd/artifactory-v3_0_6.xsd",
            ArtifactoryServiceVersionProvider.v780m001.get()),
    v307("http://artifactory.jfrog.org/xsd/3.0.7",
            "http://www.jfrog.org/xsd/artifactory-v3_0_7.xsd",
            ArtifactoryServiceVersionProvider.v780m002.get()),
    v308("http://artifactory.jfrog.org/xsd/3.0.8",
            "http://www.jfrog.org/xsd/artifactory-v3_0_8.xsd",
            ArtifactoryServiceVersionProvider.v780m073.get()),
    v309("http://artifactory.jfrog.org/xsd/3.0.9",
            "http://www.jfrog.org/xsd/artifactory-v3_0_9.xsd",
            ArtifactoryServiceVersionProvider.v790m001.get(), new AddVerifyAudienceRestrictionConverter()),
    v310("http://artifactory.jfrog.org/xsd/3.1.0",
            "http://www.jfrog.org/xsd/artifactory-v3_1_0.xsd",
            ArtifactoryServiceVersionProvider.v7100m001.get(), new PropertyDuplicationsConverter()),
    v311("http://artifactory.jfrog.org/xsd/3.1.1",
            "http://www.jfrog.org/xsd/artifactory-v3_1_1.xsd",
            ArtifactoryServiceVersionProvider.v7100m023.get(), new DockerRemoteRepositoryCacheRetrievalConverter()),
    v312("http://artifactory.jfrog.org/xsd/3.1.2",
            "http://www.jfrog.org/xsd/artifactory-v3_1_2.xsd",
            ArtifactoryServiceVersionProvider.v7103m019.get(), new AddBasicAuthenticationEnabledConverter()),
    v313("http://artifactory.jfrog.org/xsd/3.1.3",
            "http://www.jfrog.org/xsd/artifactory-v3_1_3.xsd",
            ArtifactoryServiceVersionProvider.v7120m001.get(), new DockerLocalRepositoryRetentionPolicyConverter()),
    v314("http://artifactory.jfrog.org/xsd/3.1.4",
            "http://www.jfrog.org/xsd/artifactory-v3_1_4.xsd",
            ArtifactoryServiceVersionProvider.v7120m002.get(), new ArchiveIndexEnableConverter(),
            new FixConanDefaultLayoutConverter()),
    v315("http://artifactory.jfrog.org/xsd/3.1.5",
            "http://www.jfrog.org/xsd/artifactory-v3_1_5.xsd",
            ArtifactoryServiceVersionProvider.v7130m001.get()),
    v316("http://artifactory.jfrog.org/xsd/3.1.6",
            "http://www.jfrog.org/xsd/artifactory-v3_1_6.xsd",
            ArtifactoryServiceVersionProvider.v7130m012.get()),
    v317("http://artifactory.jfrog.org/xsd/3.1.7",
            "http://www.jfrog.org/xsd/artifactory-v3_1_7.xsd",
            ArtifactoryServiceVersionProvider.v7140m001.get()),
    v318("http://artifactory.jfrog.org/xsd/3.1.8",
            "http://www.jfrog.org/xsd/artifactory-v3_1_8.xsd",
            ArtifactoryServiceVersionProvider.v7160m001.get(), new GpgToDefaultGpgKeyPairConverter()),
    v319("http://artifactory.jfrog.org/xsd/3.1.9",
            "http://www.jfrog.org/xsd/artifactory-v3_1_9.xsd",
            ArtifactoryServiceVersionProvider.v7170m040.get(), new AddCargoDefaultLayoutConverter()),
    v3110("http://artifactory.jfrog.org/xsd/3.1.10",
            "http://www.jfrog.org/xsd/artifactory-v3_1_10.xsd",
            ArtifactoryServiceVersionProvider.v7173m001.get(), new RemoveKeyPairFromRemoteRepositoriesConverter()),
    v3111("http://artifactory.jfrog.org/xsd/3.1.11",
            "http://www.jfrog.org/xsd/artifactory-v3_1_11.xsd",
            ArtifactoryServiceVersionProvider.v7177m001.get()),
    v3112("http://artifactory.jfrog.org/xsd/3.1.12",
            "http://www.jfrog.org/xsd/artifactory-v3_1_12.xsd",
            ArtifactoryServiceVersionProvider.v7190m001.get(), new AddMetadataTimeout()),
    v3113("http://artifactory.jfrog.org/xsd/3.1.13",
            "http://www.jfrog.org/xsd/artifactory-v3_1_13.xsd",
            ArtifactoryServiceVersionProvider.v7200m017.get()),
    v3114("http://artifactory.jfrog.org/xsd/3.1.14",
            "http://www.jfrog.org/xsd/artifactory-v3_1_14.xsd",
            ArtifactoryServiceVersionProvider.v7210m001.get(), new DistributionReposToLocalConverter()),
    v3115("http://artifactory.jfrog.org/xsd/3.1.15",
            "http://www.jfrog.org/xsd/artifactory-v3_1_15.xsd",
            ArtifactoryServiceVersionProvider.v7211m001.get(), new ComposerLocalRepositoryEnableMetadataV1Converter(),
            new RepositoryXrayDataTtlConverter()),
    v31151("http://artifactory.jfrog.org/xsd/3.1.15.1",
            "http://www.jfrog.org/xsd/artifactory-v3_1_15_1.xsd",
            ArtifactoryServiceVersionProvider.v72113m002.get()),
    v3116("http://artifactory.jfrog.org/xsd/3.1.16",
            "http://www.jfrog.org/xsd/artifactory-v3_1_16.xsd",
            ArtifactoryServiceVersionProvider.v7240m001.get()),
    v3117("http://artifactory.jfrog.org/xsd/3.1.17",
            "http://www.jfrog.org/xsd/artifactory-v3_1_17.xsd",
            ArtifactoryServiceVersionProvider.v7250m001.get()),
    v3118("http://artifactory.jfrog.org/xsd/3.1.18",
            "http://www.jfrog.org/xsd/artifactory-v3_1_18.xsd",
            ArtifactoryServiceVersionProvider.v7250m053.get()),
    v3119("http://artifactory.jfrog.org/xsd/3.1.19",
            "http://www.jfrog.org/xsd/artifactory-v3_1_19.xsd",
            ArtifactoryServiceVersionProvider.v7253m001.get(), new RemoveArtifactLifecycleSettingsConverter()),
    v3120("http://artifactory.jfrog.org/xsd/3.1.20",
            "http://www.jfrog.org/xsd/artifactory-v3_1_20.xsd",
            ArtifactoryServiceVersionProvider.v727.get()),
    v3121("http://artifactory.jfrog.org/xsd/3.1.21",
            "http://www.jfrog.org/xsd/artifactory-v3_1_21.xsd",
            ArtifactoryServiceVersionProvider.v7273.get(), new ReplicationPathPrefixConverter()),
    v3122("http://artifactory.jfrog.org/xsd/3.1.22",
            "http://www.jfrog.org/xsd/artifactory-v3_1_22.xsd",
            ArtifactoryServiceVersionProvider.v7274.get(), new AddTerraformDefaultLayoutConverter()),
    v3123("http://artifactory.jfrog.org/xsd/3.1.23",
            "http://www.jfrog.org/xsd/artifactory-v3_1_23.xsd",
            ArtifactoryServiceVersionProvider.v729.get()),
    v3124("http://artifactory.jfrog.org/xsd/3.1.24",
            "http://www.jfrog.org/xsd/artifactory-v3_1_24.xsd",
            ArtifactoryServiceVersionProvider.v7291.get(), new AddSwiftDefaultLayoutConverter()),
    v3125("http://artifactory.jfrog.org/xsd/3.1.25",
            "http://www.jfrog.org/xsd/artifactory-v3_1_25.xsd",
            ArtifactoryServiceVersionProvider.v7301.get(), new NugetSymbolServerUrlConverter()),
    v3126("http://artifactory.jfrog.org/xsd/3.1.26",
            "http://www.jfrog.org/xsd/artifactory-v3_1_26.xsd",
            ArtifactoryServiceVersionProvider.v7330.get(), new FixSwiftDefaultLayoutConverter()),
    v3127("http://artifactory.jfrog.org/xsd/3.1.27",
            "http://www.jfrog.org/xsd/artifactory-v3_1_27.xsd",
            ArtifactoryServiceVersionProvider.v7350.get()),
    v3128("http://artifactory.jfrog.org/xsd/3.1.28",
            "http://www.jfrog.org/xsd/artifactory-v3_1_28.xsd",
            ArtifactoryServiceVersionProvider.v7377.get()), // change to latest
    v3129("http://artifactory.jfrog.org/xsd/3.1.29",
            "http://www.jfrog.org/xsd/artifactory-v3_1_29.xsd",
            ArtifactoryServiceVersionProvider.v7380.get()),
    v3130("http://artifactory.jfrog.org/xsd/3.1.30",
            "http://www.jfrog.org/xsd/artifactory-v3_1_30.xsd",
            ArtifactoryServiceVersionProvider.v7400.get()),
    v3131("http://artifactory.jfrog.org/xsd/3.1.31",
            "http://www.jfrog.org/xsd/artifactory-v3_1_31.xsd",
            ArtifactoryServiceVersionProvider.v7410.get()),
    v3132("http://artifactory.jfrog.org/xsd/3.1.32",
            "http://www.jfrog.org/xsd/artifactory-v3_1_32.xsd",
            ArtifactoryServiceVersionProvider.v7411.get(), new AddArtifactsSettingsConverter(),
            new PackageArtifactoryRequestsCanRetrieveRemoteArtifactConverter(PackageType.HELM)),
    v3133("http://artifactory.jfrog.org/xsd/3.1.33",
            "http://www.jfrog.org/xsd/artifactory-v3_1_33.xsd",
            ArtifactoryServiceVersionProvider.v7420m001.get()),
    v3134("http://artifactory.jfrog.org/xsd/3.1.34",
            "http://www.jfrog.org/xsd/artifactory-v3_1_34.xsd",
            ArtifactoryServiceVersionProvider.v7440.get(),
            new RemoveAccessClientSettingConverter(),
            new MoveUserLockPolicyAndAnonAccessSettingToConfigServiceConverter(),
            new CopyPasswordExpirationPolicyToConfigServiceConverter()),
    v3135("http://artifactory.jfrog.org/xsd/3.1.35",
            "http://www.jfrog.org/xsd/artifactory-v3_1_35.xsd",
            ArtifactoryServiceVersionProvider.v7441m001.get()),
    v3136("http://artifactory.jfrog.org/xsd/3.1.36",
            "http://www.jfrog.org/xsd/artifactory-v3_1_36.xsd",
            ArtifactoryServiceVersionProvider.v7442.get(),
            new RemoveArtifactsSettingsConvertor()),
    v3137("http://artifactory.jfrog.org/xsd/3.1.37",
            "http://www.jfrog.org/xsd/artifactory-v3_1_37.xsd",
            ArtifactoryServiceVersionProvider.v7460m001.get()),
    v321("http://artifactory.jfrog.org/xsd/3.2.1",
            "http://www.jfrog.org/xsd/artifactory-v3_2_1.xsd",
            ArtifactoryServiceVersionProvider.v7480.get()),
    v322("http://artifactory.jfrog.org/xsd/3.2.2",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_2_2.xsd",
            ArtifactoryServiceVersionProvider.v7481.get(),
            new MovePasswordEncryptionPolicyToConfigServiceConverter(),
            new MoveBasicAuthenticationEnabledToConfigServiceConverter()),
    V323("http://artifactory.jfrog.org/xsd/3.2.3",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_2_3.xsd",
            ArtifactoryServiceVersionProvider.v7482.get(),
            new MigrateConfigDescriptorFooterFieldsConverter()),
    v324("http://artifactory.jfrog.org/xsd/3.2.4",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_2_4.xsd",
            ArtifactoryServiceVersionProvider.v7490m001.get(),
            new MigrateSsoHttpToConfigServiceConverter()
    ),
    v325("http://artifactory.jfrog.org/xsd/3.2.5",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_2_5.xsd",
            ArtifactoryServiceVersionProvider.v7521m001.get()),
    v326("http://artifactory.jfrog.org/xsd/3.2.6",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_2_6.xsd",
            ArtifactoryServiceVersionProvider.v7530.get(),
            new MoveLdapSettingToConfigServiceConverter(),
            new MoveLdapGroupSettingToConfigServiceConverter()),
    v327("http://artifactory.jfrog.org/xsd/3.2.7",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_2_7.xsd",
            ArtifactoryServiceVersionProvider.v7570.get(),
            new MigrateCrowdSettingsToAccessServiceConverter()),
    v328("http://artifactory.jfrog.org/xsd/3.2.8",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_2_8.xsd",
            ArtifactoryServiceVersionProvider.v7580.get(),
            new XrayUnavailableConverter()),
    v329("http://artifactory.jfrog.org/xsd/3.2.9",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_2_9.xsd",
            ArtifactoryServiceVersionProvider.v7670.get()),
    v3210("http://artifactory.jfrog.org/xsd/3.2.10",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_2_10.xsd",
            ArtifactoryServiceVersionProvider.v7680.get(),
            new MigrateContantsToAccessConverter()),
    v330("http://artifactory.jfrog.org/xsd/3.3.0",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_3_0.xsd",
            ArtifactoryServiceVersionProvider.v7720.get()),
    v331("http://artifactory.jfrog.org/xsd/3.3.1",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_3_1.xsd",
            ArtifactoryServiceVersionProvider.v7730.get()),
    v332("http://artifactory.jfrog.org/xsd/3.3.2",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_3_2.xsd",
            ArtifactoryServiceVersionProvider.v7740.get(),
            new SetXrayTokenIdentificationToTrueConverter(),
            new ItemsBrowserPagingSizeConvertor()),
    v333("http://artifactory.jfrog.org/xsd/3.3.3",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_3_3.xsd",
            ArtifactoryServiceVersionProvider.v7760.get(),
            new MoveSamlSettingToAccessConverter()),
    v334("http://artifactory.jfrog.org/xsd/3.3.4",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_3_4.xsd",
            ArtifactoryServiceVersionProvider.v7770.get(), new CalculateRepositoryAssociationsConvertor()),
    v335("http://artifactory.jfrog.org/xsd/3.3.5",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_3_5.xsd",
            ArtifactoryServiceVersionProvider.v7830.get(),
            new MigrateSamlEncryptionToAccessConverter()),
    v336("http://artifactory.jfrog.org/xsd/3.3.6",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_3_6.xsd",
            ArtifactoryServiceVersionProvider.v7850.get(), new UserLockConvertor(),
            new AddAnsibleDefaultLayoutConverter()),
    v337("http://artifactory.jfrog.org/xsd/3.3.7",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_3_7.xsd",
            ArtifactoryServiceVersionProvider.v7860.get(), new RemoveCleanupPoliciesSettingsConvertor()),
    v338("http://artifactory.jfrog.org/xsd/3.3.8",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_3_8.xsd",
            ArtifactoryServiceVersionProvider.v7870.get(), new RemoveCalculateRepositoryAssociationsConvertor()),
    v339("http://artifactory.jfrog.org/xsd/3.3.9",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_3_9.xsd",
            ArtifactoryServiceVersionProvider.v7880.get()),
    v340("http://artifactory.jfrog.org/xsd/3.4.0",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_4_0.xsd",
            ArtifactoryServiceVersionProvider.v7910.get()),
    v341("http://artifactory.jfrog.org/xsd/3.4.1",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_4_1.xsd",
            ArtifactoryServiceVersionProvider.v71030m020.get()),
    v342("http://artifactory.jfrog.org/xsd/3.4.2",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_4_2.xsd",
            ArtifactoryServiceVersionProvider.v71030.get()),
    v343("http://artifactory.jfrog.org/xsd/3.4.3",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_4_3.xsd",
            ArtifactoryServiceVersionProvider.v71060.get()),
    v344("http://artifactory.jfrog.org/xsd/3.4.4",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_4_4.xsd",
            ArtifactoryServiceVersionProvider.v71070.get(), new ArchiveIndexDisableConverter(),
            new AddSbtIvyDefaultLayoutConverter()),
    v345("http://artifactory.jfrog.org/xsd/3.4.5",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_4_5.xsd",
            ArtifactoryServiceVersionProvider.v71080.get()),
    v346("http://artifactory.jfrog.org/xsd/3.4.6",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_4_6.xsd",
            ArtifactoryServiceVersionProvider.v71200.get(), new MoveOAuthSettingsToAccessConverter()),
    v347("http://artifactory.jfrog.org/xsd/3.4.7",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_4_7.xsd",
            ArtifactoryServiceVersionProvider.v71230.get()),
    v348("http://artifactory.jfrog.org/xsd/3.4.8",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_4_8.xsd",
            ArtifactoryServiceVersionProvider.v71240.get(), new AddNixDefaultLayoutConverter()),
    v349("http://artifactory.jfrog.org/xsd/3.4.9",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_4_9.xsd",
            ArtifactoryServiceVersionProvider.v71310.get()),
    v350("http://artifactory.jfrog.org/xsd/3.5.0",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_5_0.xsd",
            ArtifactoryServiceVersionProvider.v71371.get(), new AddNixDefaultLayoutConverter()),
    v351("http://artifactory.jfrog.org/xsd/3.5.1",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_5_1.xsd",
            ArtifactoryServiceVersionProvider.v71450.get(), new AddSkillsDefaultLayoutConverter()),
    v352("http://artifactory.jfrog.org/xsd/3.5.2",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_5_2.xsd",
            ArtifactoryServiceVersionProvider.v71460.get(), new AddAgentPluginsDefaultLayoutConverter()),
    v353("http://artifactory.jfrog.org/xsd/3.5.3",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_5_3.xsd",
            ArtifactoryServiceVersionProvider.v71540.get(), new AddAgentPackagesDefaultLayoutConverter()),
    v354("http://artifactory.jfrog.org/xsd/3.5.4",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_5_4.xsd",
            ArtifactoryServiceVersionProvider.v71550.get(), new AddLuaRocksDefaultLayoutConverter()),
    v355("http://artifactory.jfrog.org/xsd/3.5.5",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_5_5.xsd",
            ArtifactoryServiceVersionProvider.v71560.get(), new AddAgentPackagesDefaultLayoutConverter()),
    v356("http://artifactory.jfrog.org/xsd/3.5.6",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_5_6.xsd",
            ArtifactoryServiceVersionProvider.v71570.get()),
    v357("http://artifactory.jfrog.org/xsd/3.5.7",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_5_7.xsd",
            ArtifactoryServiceVersionProvider.v71580.get(), new MigrateAgentResourcesToAgentPackagesConverter()),
    v358("http://artifactory.jfrog.org/xsd/3.5.8",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_5_8.xsd",
            ArtifactoryServiceVersionProvider.v71590.get(), new AddAgentPackagesDefaultLayoutConverter()),
    v359("http://artifactory.jfrog.org/xsd/3.5.9",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_5_9.xsd",
            ArtifactoryServiceVersionProvider.v71600.get(), new HuggingFaceXetEnabledConverter()),
    v360("http://artifactory.jfrog.org/xsd/3.5.10",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_5_10.xsd",
            ArtifactoryServiceVersionProvider.v71610.get(), new RemoveAgentResourcesDefaultLayoutConverter()),
    v361("http://artifactory.jfrog.org/xsd/3.5.11",
            Constants.RELEASES_JFROG_IO_ARTIFACTORY +
                    "static-release-virtual/xsd/artifactory/artifactory-v3_5_11.xsd",
            ArtifactoryServiceVersionProvider.v71620.get())
    ;


    private final String xsdUri;
    private final String xsdLocation;
    private ArtifactoryServiceVersion version;
    private final XmlConverter[] converters;

    /**
     * @param version    The artifactory version this config version was first used
     * @param converters A list of converters to use to move from <b>this</b> config version to the <b>next</b> config
     *                   version
     */
    ArtifactoryConfigVersion(String xsdUri, String xsdLocation, ArtifactoryServiceVersion version,
            XmlConverter... converters) {
        this.xsdUri = xsdUri;
        this.xsdLocation = xsdLocation;
        this.version = version;
        this.converters = converters;
    }

    public boolean isLast() {
        return this.ordinal() == last().ordinal();
    }

    public String convert(String in, boolean isApiRequest) {
        // First create the list of converters to apply
        List<XmlConverter> localConverters = new ArrayList<>();

        // First thing to do is to change the namespace and schema location
        localConverters.add(new NamespaceConverter());

        // All converters of versions above me needs to be executed in sequence
        ArtifactoryConfigVersion[] versions = ArtifactoryConfigVersion.values();
        for (ArtifactoryConfigVersion aVersion : versions) {
            if (aVersion.ordinal() >= ordinal() && aVersion.getConverters() != null) {
                localConverters.addAll(Arrays.asList(aVersion.getConverters()));
            }
        }
        return isApiRequest ?
                XmlConverterUtils.convertStrict(localConverters, in) :
                XmlConverterUtils.convert(localConverters, in);
    }

    public String getXsdUri() {
        return xsdUri;
    }

    public String getXsdLocation() {
        return xsdLocation;
    }

    public XmlConverter[] getConverters() {
        return converters;
    }

    @Override
    public ArtifactoryServiceVersion getVersion() {
        return version;
    }

    public static ArtifactoryConfigVersion getConfigVersion(String configXml) {
        // Find correct version by schema URI
        ArtifactoryConfigVersion[] configVersions = values();
        for (ArtifactoryConfigVersion configVersion : configVersions) {
            if (configXml.contains("\"" + configVersion.getXsdUri() + "\"")) {
                return configVersion;
            }
        }
        return null;
    }

    public static ArtifactoryConfigVersion last() {
        return ArtifactoryConfigVersion.values()[ArtifactoryConfigVersion.values().length - 1];
    }

    private static class Constants {
        public static final String RELEASES_JFROG_IO_ARTIFACTORY = "https://releases.jfrog.io/artifactory/";
    }
}
