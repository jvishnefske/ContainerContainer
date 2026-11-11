package org.artifactory.unifiedevent.mapper;

import org.artifactory.unifiedevent.UnifiedEventContextProvider;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.model.repo.RepoConfigEventData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class RepoConfigEventEnvelopeMapper {

    @Autowired
    protected UnifiedEventContextProvider contextProvider;

    @Mapping(target = "specVersion", constant = "1.0")
    @Mapping(target = "id", expression = "java(contextProvider.generateEventId())")
    @Mapping(target = "type", source = "eventType")
    @Mapping(target = "source", expression = "java(contextProvider.buildSourceHeader())")
    @Mapping(target = "time", expression = "java(contextProvider.getCurrentTimestamp())")
    @Mapping(target = "subject", source = "subject")
    @Mapping(target = "dataContentType", constant = "application/json")
    @Mapping(target = "dataSchema", expression = "java(contextProvider.getDataSchema(eventType))")
    @Mapping(target = "eventHeaders", expression = "java(contextProvider.getEventHeaders())")
    @Mapping(target = "data", source = "eventData")
    public abstract EventEnvelope<RepoConfigEventData> toRepoConfigEventEnvelopeV1(
            SupportedUnifiedEvents eventType,
            RepoConfigEventData eventData,
            String subject);
}
