package org.artifactory.unifiedevent;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.addon.replication.ReplicationTargetInfo;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.unifiedevent.api.request.ArtifactResourceContext;
import org.artifactory.unifiedevent.builder.EventEnvelopeBuilder;
import org.artifactory.unifiedevent.builder.artifact.ArtifactDataBuilder;
import org.artifactory.unifiedevent.builder.artifact.ArtifactEventDataBuilder;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.mapper.ArtifactDataMapper;
import org.artifactory.unifiedevent.mapper.DispatcherResponseMapper;
import org.artifactory.unifiedevent.mapper.RepoConfigEventEnvelopeMapper;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.model.artifact.ArtifactData;
import org.artifactory.unifiedevent.model.artifact.ArtifactEventData;
import org.artifactory.unifiedevent.model.repo.RepoConfigEventData;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class UnifiedEventServiceImpl implements UnifiedEventService {

    private final EventDispatcherHandler eventDispatcherHandler;
    private final UnifiedEventContextProvider contextProvider;
    private final ArtifactDataMapper artifactDataMapper;
    private final RepoConfigEventEnvelopeMapper repoConfigEventEnvelopeMapper;

    @Override
    public DispatcherResponse sendArtifactEvent(SupportedUnifiedEvents supportedUnifiedEvent,
            ArtifactoryRequest request, RepoPath actualRepoPath) {
        ArtifactData artifactData =
                ArtifactDataBuilder.buildDataForArtifactAction(request, contextProvider);
        ArtifactEventData artifactEventData =
                ArtifactEventDataBuilder.buildForArtifactEvent(artifactData, request, actualRepoPath,
                        contextProvider.getUserContext());
        EventEnvelope<ArtifactEventData> event = EventEnvelopeBuilder.populateEventEnvelopeData(artifactEventData,
                contextProvider.getSubjectForArtifactData(artifactData), contextProvider,
                supportedUnifiedEvent);

        return dispatchUnifiedEvent(event);
    }

    @Override
    public DispatcherResponse sendRemoteArtifactEvent(SupportedUnifiedEvents supportedUnifiedEvent,
            ArtifactResourceContext resourceContext) {
        EventEnvelope<ArtifactEventData> eventData =
                artifactDataMapper.toArtifactEventEnvelopeV1(supportedUnifiedEvent, resourceContext);
        return dispatchUnifiedEvent(eventData);
    }

    @Override
    public DispatcherResponse sendStorageEvent(SupportedUnifiedEvents supportedUnifiedEvent,
            VfsItem<? extends ItemInfo> info, DeleteContext ctx, RepoPath targetRepoPath) {
        ArtifactData artifactData =
                ArtifactDataBuilder.buildDataFormItemInfo(info, contextProvider);
        ArtifactEventData artifactEventData =
                ArtifactEventDataBuilder.buildForStorageEvent(artifactData, ctx, contextProvider.getUserContext(),
                        targetRepoPath);
        EventEnvelope<ArtifactEventData> event = EventEnvelopeBuilder.populateEventEnvelopeData(artifactEventData,
                contextProvider.getSubjectForArtifactData(artifactData), contextProvider, supportedUnifiedEvent);

        return dispatchUnifiedEvent(event);
    }

    @Override
    public DispatcherResponse sendPropertyEvent(SupportedUnifiedEvents eventType, VfsItem<? extends ItemInfo> info,
            String propertyName, List<String> propertyValues) {
        ArtifactData artifactData = ArtifactDataBuilder.buildDataFormItemInfo(info, contextProvider);
        ArtifactEventData artifactEventData = ArtifactEventDataBuilder.buildForPropertyEvent(artifactData,
                propertyName, propertyValues, contextProvider.getUserContext());
        EventEnvelope<ArtifactEventData> event = EventEnvelopeBuilder.populateEventEnvelopeData(artifactEventData,
                contextProvider.getSubjectForArtifactData(artifactData), contextProvider, eventType);

        return dispatchUnifiedEvent(event);
    }

    @Override
    public DispatcherResponse sendReplicationEvent(SupportedUnifiedEvents eventType, RepoPath repoPath,
            ReplicationTargetInfo targetInfo) {
        ArtifactData artifactData =
                ArtifactDataBuilder.buildDataForReplicationInfo(repoPath, contextProvider);
        ArtifactEventData artifactEventData =
                ArtifactEventDataBuilder.buildForReplicationEvent(artifactData, targetInfo,
                        contextProvider.getUserContext());
        EventEnvelope<ArtifactEventData> event = EventEnvelopeBuilder.populateEventEnvelopeData(artifactEventData,
                contextProvider.getSubjectForArtifactData(artifactData), contextProvider, eventType);

        return dispatchUnifiedEvent(event);
    }

    @Override
    public DispatcherResponse sendRepoConfigEvent(SupportedUnifiedEvents eventType, RepoConfigEventData eventData,
            String subject) {
        EventEnvelope<RepoConfigEventData> event =
                repoConfigEventEnvelopeMapper.toRepoConfigEventEnvelopeV1(eventType, eventData, subject);
        return dispatchUnifiedEvent(event);
    }

    private <T> DispatcherResponse dispatchUnifiedEvent(EventEnvelope<T> event) {
        List<DispatcherResponse> responses = eventDispatcherHandler.dispatchEvent(event);
        DispatcherResponse response = DispatcherResponseMapper.toConsolidatedDispatcherResponse(responses);
        log.debug("Consolidated dispatcher response for event: {} from the dispatchers: {}", event.type(),
                response);
        if (!response.shouldProceed()) {
            throw new UnifiedEventException(HttpStatus.SC_CONFLICT, false, response.message());
        }
        return response;
    }
}
