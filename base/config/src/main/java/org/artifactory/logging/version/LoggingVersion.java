/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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

package org.artifactory.logging.version;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.environment.converter.local.version.v7.V700PreInitReplaceLogbackConverter;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.XmlConverterUtils;
import org.artifactory.version.converter.XmlConverter;
import org.artifactory.version.v18.LogbackTrafficLogV2Converter;
import org.artifactory.version.v19.LogbackMetricLogNewAppenderConverter;
import org.artifactory.version.v20.LogbackMetricLogAppenderOrderConverter;
import org.artifactory.version.v21.LogbackCompressLatestConverter;
import org.artifactory.version.v215.LogbackMetricsLogsConverter;
import org.artifactory.version.v22.LogbackSizeAndIntervalPolicyConverter;
import org.artifactory.version.v23.LogbackRemoveStartArtifactoryProWithServicesLogger;
import org.artifactory.version.v24.LogbackOutboundRequestLogConverter;
import org.artifactory.version.v25.LogbackUsageConsumptionLogConverter;
import org.artifactory.version.v26.LogbackFeatureAdoptionLogConverter;
import org.artifactory.version.v27.LogbackFeatureAdoptionAppenderIntervalConverter;
import org.artifactory.version.v28.ConsoleLogsConverter;
import org.artifactory.version.v29.TargetBundleRetentionLogConverter;
import org.artifactory.version.v30.ConsoleLogsFilterConverter;
import org.artifactory.version.v31.LogbackFederationMetricsLogConverter;
import org.artifactory.version.v32.MavenIndexerLogConverter;
import org.artifactory.version.v33.LogbackConsumptionUsageLogConverter;
import org.artifactory.version.v34.BundleHandlerLogConverter;
import org.artifactory.version.v35.ShadowLogsConverter;
import org.artifactory.version.v36.ShadowLogsConverterFix;
import org.artifactory.version.v37.RequestOutTriggeringPolicyConverter;
import org.artifactory.version.v38.FederationLogAppenderConverter;
import org.artifactory.version.v39.LogThrottleFilterConverter;
import org.artifactory.version.v40.DistributionHandlerLogConverter;
import org.artifactory.version.v41.LogbackCleanupAuditLogConverter;
import org.artifactory.version.v42.TrafficLogDisableCompressionConverter;
import org.artifactory.version.v43.LogbackConsumptionUsageLogFolderConverter;
import org.artifactory.version.v44.LogbackConsumptionUsageLogIntervalConverter;
import org.artifactory.version.v45.LogbackStorageOutboundRequestLogConverter;
import org.artifactory.version.v46.LogbackTrafficLogV3Converter;
import org.artifactory.version.v47.LogThrottleExceptionGlobalLimitConverter;
import org.artifactory.version.v48.BuildHandlerLogConverter;
import org.artifactory.version.v49.LogbackErrorCatalogAppenderConverter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.artifactory.version.ArtifactoryServiceVersionProvider.v700m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7110m033;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v71110;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7113m003;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v71200;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v71240;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v71380;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7150m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v71590;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7171m005;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7192m014;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7200m020;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7250m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v731m007;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v73710m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7420m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7490m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7520m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7598;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7600;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7690;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7700;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v770m079;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7730;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7760;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7770;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v777m006;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7790;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7790m009;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7820;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7830;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7880;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7890;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7900;

/**
 * Keeps track of the logging configuration versions
 * All converters before Artifactory 7 have been deleted, since we backup and replace the old backup file
 *
 * @author Noam Y. Tenne
 * @see V700PreInitReplaceLogbackConverter
 */
@Slf4j
public enum LoggingVersion {

    v16(v700m001.get()),
    v17(v770m079.get(), new LogbackMetricsLogsConverter()),
    v18(v777m006.get(), new LogbackTrafficLogV2Converter()),
    v19(v7110m033.get(), new LogbackMetricLogNewAppenderConverter()),
    v20(v7113m003.get(), new LogbackMetricLogAppenderOrderConverter()),
    v21(v7150m001.get(), new LogbackCompressLatestConverter()),
    v22(v7171m005.get(), new LogbackSizeAndIntervalPolicyConverter()),
    v23(v7192m014.get(), new LogbackRemoveStartArtifactoryProWithServicesLogger()),
    v24(v7200m020.get(), new LogbackOutboundRequestLogConverter()),
    v25(v7250m001.get(), new LogbackUsageConsumptionLogConverter()),
    v26(v731m007.get(), new LogbackFeatureAdoptionLogConverter()),
    v27(v73710m001.get(), new LogbackFeatureAdoptionAppenderIntervalConverter()),
    v28(v7420m001.get(), new ConsoleLogsConverter()),
    v29(v7490m001.get(), new TargetBundleRetentionLogConverter()),
    v30(v7520m001.get(), new ConsoleLogsFilterConverter()),
    v31(v7598.get(), new LogbackFederationMetricsLogConverter()),
    v32(v7600.get(), new MavenIndexerLogConverter()),
    v33(v7690.get(), new LogbackConsumptionUsageLogConverter()),
    v34(v7700.get(), new BundleHandlerLogConverter()),
    v35(v7730.get(), new ShadowLogsConverter()),
    v36(v7760.get(), new ShadowLogsConverterFix()),
    v37(v7770.get(), new RequestOutTriggeringPolicyConverter()),
    v38(v7790.get(), new FederationLogAppenderConverter()),
    v39(v7790m009.get(), new LogThrottleFilterConverter()),
    v40(v7820.get(), new DistributionHandlerLogConverter()),
    v41(v7830.get(), new LogbackCleanupAuditLogConverter()),
    v42(v7880.get(), new TrafficLogDisableCompressionConverter()),
    v43(v7890.get(), new LogbackConsumptionUsageLogFolderConverter()),
    v44(v7900.get(), new LogbackConsumptionUsageLogIntervalConverter()),
    v45(v71110.get(), new LogbackStorageOutboundRequestLogConverter()),
    v46(v71200.get(), new LogbackTrafficLogV3Converter()),
    v47(v71240.get(), new LogThrottleExceptionGlobalLimitConverter()),
    v48(v71380.get(), new BuildHandlerLogConverter()),
    v49(v71590.get(), new LogbackErrorCatalogAppenderConverter());

    private final ArtifactoryServiceVersion version;
    private final XmlConverter[] xmlConverters;

    /**
     * Main constructor
     *
     * @param from          Start version
     * @param xmlConverters XML converters required for the specified range
     */
    LoggingVersion(ArtifactoryServiceVersion from, XmlConverter... xmlConverters) {
        this.version = from;
        this.xmlConverters = xmlConverters;
    }

    XmlConverter[] getXmlConverters() {
        return xmlConverters;
    }

    /**
     * Run the needed conversions
     *
     * @param srcEtcDir the directory in which resides the logback file to convert
     */
    public static void convert(ArtifactoryServiceVersion version, File srcEtcDir, File targetEtcDir)
            throws IOException {
        // First create the list of converters to apply
        List<XmlConverter> converters = LoggingVersion.getEffectedXmlConverters(version);
        if (!converters.isEmpty()) {
            File logbackConfigFile = new File(srcEtcDir, ArtifactoryHome.LOGBACK_CONFIG_FILE_NAME);
            if (!logbackConfigFile.exists()) {
                log.info("Skipping logger converters as logback file doesn't exist.");
                return;
            }
            try {
                String result = XmlConverterUtils.convert(converters,
                        FileUtils.readFileToString(logbackConfigFile, StandardCharsets.UTF_8));
                backupAndSaveLogback(result, targetEtcDir);
            } catch (IOException e) {
                log.error("Error occurred while converting logback config for conversion: {}.", e.getMessage());
                log.debug("Error occurred while converting logback config for conversion", e);
                throw e;
            }
        }
    }

    /**
     * Creates a backup of the existing logback configuration file and proceeds to save post-conversion content
     *
     * @param result Conversion result
     * @param etcDir directory to which to save the conversion result
     */
    public static void backupAndSaveLogback(String result, File etcDir) throws IOException {
        File logbackConfigFile = new File(etcDir, ArtifactoryHome.LOGBACK_CONFIG_FILE_NAME);
        if (logbackConfigFile.exists()) {
            File originalBackup = new File(etcDir, "logback.original.xml");
            FileUtils.copyFile(logbackConfigFile, originalBackup);
        }
        FileUtils.writeStringToFile(logbackConfigFile, result, StandardCharsets.UTF_8);
    }

    public static void convert(ArtifactoryServiceVersion from, File path)
            throws IOException {
        boolean foundConversion = false;
        // All converters of versions above me needs to be executed in sequence
        LoggingVersion[] versions = LoggingVersion.values();
        for (LoggingVersion logingVersion : versions) {
            if (logingVersion.version.after(from)) {
                foundConversion = true;
                convert(from, path, path);
            }
        }
        // Write to log only if conversion has been executed
        if (foundConversion) {
            log.info("Ending database conversion from {}", from);
        }
    }

    public static List<XmlConverter> getEffectedXmlConverters(ArtifactoryServiceVersion importedVersion) {
        List<XmlConverter> converters = Lists.newArrayList();
        for (LoggingVersion loggingVersion : values()) {
            if (loggingVersion.version.afterOrEqual(importedVersion) && loggingVersion.xmlConverters != null) {
                converters.addAll(Lists.newArrayList(loggingVersion.xmlConverters));
            }
        }
        return converters;
    }
}
