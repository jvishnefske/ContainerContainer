package org.artifactory.metadata.service;

import com.google.common.collect.Maps;
import com.google.protobuf.util.JsonFormat;
import com.jfrog.artifactory.packageindexed.PackageIndexEventOuterClass;
import com.jfrog.artifactory.packageindexed.PackageIndexEventOuterClass.PackageIndexEvent;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.jfbus.JfbusEventingService;
import org.artifactory.jfbus.JfbusEventingService.JfbusOperationMode;
import org.artifactory.projects.ProjectsServiceImpl;
import org.artifactory.spring.ContextCreationListener;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.client.util.PathUtils;
import org.jfrog.eventing.facade.TopicId;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;

import static org.artifactory.jfbus.JfbusConstants.HEADER_DATA_SCHEMA;
import static org.artifactory.jfbus.JfbusConstants.HEADER_PACKAGE_TYPE;
import static org.artifactory.jfbus.JfbusConstants.HEADER_PROJECT;
import static org.artifactory.jfbus.JfbusConstants.HEADER_SUBJECT;
import static org.artifactory.jfbus.JfbusConstants.HEADER_TYPE;
import static org.artifactory.jfbus.JfbusConstants.PACKAGE_CREATED_EVENT_TYPE;
import static org.artifactory.jfbus.JfbusConstants.PACKAGE_DELETED_EVENT_TYPE;
import static org.jfrog.common.ExceptionLogUtils.debugOrWarn;

/**
 * Event service for package created/deleted events.
 *
 * @author IgorG
 */
@Slf4j
@Service
public class PackagesEventService implements ContextCreationListener {

    // Number of partitions can't be changed after topic creation, so set it high enough to handle load.
    private static final int PACKAGE_EVENTS_TOPIC_PARTITIONS = 15;
    private final TopicId pkgCreateTopicID = new TopicId("artifactory", "artifactory-package-indexed-events");

    private final JfbusEventingService jfbusEventingService;
    private final ProjectsServiceImpl projectsService;

    public PackagesEventService(JfbusEventingService jfbusEventingService,
            @Lazy ProjectsServiceImpl projectsService) {
        this.jfbusEventingService = jfbusEventingService;
        this.projectsService = projectsService;
    }

    @Override
    public void onContextCreated() {
        try {
            jfbusEventingService.createTopic(pkgCreateTopicID, PACKAGE_EVENTS_TOPIC_PARTITIONS, true);
        } catch (Exception e) {
            log.error("Could not create JFBus topic for package indexed events: {}", pkgCreateTopicID, e);
        }
    }

    /**
     * Gets the JFBus operation mode for the given repository type string.
     * Handles mapping of non-standard type strings to RepoType.
     */
    public static JfbusOperationMode getOperationMode() {
        return JfbusOperationMode.valueOf(ConstantValues.jfbusOperationMode.getString());
    }

    /**
     * Sends a package index event to JFBus.
     *
     * @param key               the message key - critical for message ordering
     * @param packageIndexEvent the package index event
     * @param headers           additional headers to include
     * @throws Exception        if sending fails
     */
    public void sendPackageIndexEvent(String key, @NonNull PackageIndexEvent packageIndexEvent,
            Map<String, String> headers) throws Exception {
        Map<String, String> allHeaders = Maps.newHashMap(headers);
        PackageIndexEvent finalEvent = enreachPackageIndexEvent(packageIndexEvent, allHeaders);
        populatePackageHeaders(allHeaders, finalEvent);

        printEventForTracing(finalEvent);

        jfbusEventingService.sendEvent(pkgCreateTopicID, key, finalEvent.toByteArray(), allHeaders);
    }

    public static void printEventForTracing(PackageIndexEventOuterClass.PackageIndexEvent event) {
        if (log.isTraceEnabled()) {
            try {
                log.trace(JsonFormat.printer().preservingProtoFieldNames().print(event));
            } catch (Exception e) {
                debugOrWarn(log, e.getMessage(), e);
            }
        }
    }

    private @NonNull PackageIndexEvent enreachPackageIndexEvent(PackageIndexEvent packageIndexEvent,
            Map<String, String> headers) {
        String repoKey = packageIndexEvent.getRepoName();
        if (StringUtils.isBlank(repoKey)) {
            log.error("Missing mandatory repoName field in PackageIndexEvent");
            return packageIndexEvent;
        }
        String projectKey = projectsService.getProjectKeyOfRepository(repoKey);
        ProjectResourceEntity resourceEntity = projectsService.getProjectRepositoryEntity(projectKey, repoKey);

        PackageIndexEvent.Builder enrichedEvent = packageIndexEvent.toBuilder();
        if (StringUtils.isNotBlank(projectKey)) {
            enrichedEvent.setProject(projectKey);
            headers.put(HEADER_PROJECT, projectKey);
        }
        if (resourceEntity != null && resourceEntity.getEnvironments() != null) {
            enrichedEvent.addAllStage(resourceEntity.getEnvironments());
        }
        return enrichedEvent.build();
    }

    /**
     * Populates package-specific headers from the PackageIndexEvent.
     */
    private void populatePackageHeaders(Map<String, String> headers, PackageIndexEvent packageIndexEvent) {
        String eventLeadFileName = PathUtils.getFileName(packageIndexEvent.getLeadFilePath());
        if (StringUtils.isNotBlank(eventLeadFileName)) {
            headers.put(HEADER_SUBJECT, eventLeadFileName);
        }
        String packageType = packageIndexEvent.getRepoPackageType();
        if (StringUtils.isNotBlank(packageType)) {
            headers.put(HEADER_PACKAGE_TYPE, packageType);
        }
        headers.putIfAbsent(HEADER_TYPE, packageIndexEvent.hasCreatePackageIndexEvent() ? PACKAGE_CREATED_EVENT_TYPE :
                PACKAGE_DELETED_EVENT_TYPE);
        headers.put(HEADER_DATA_SCHEMA, "https://jfrog.com/artifactory/schemas/package/metadatasync/v1");
    }
}
