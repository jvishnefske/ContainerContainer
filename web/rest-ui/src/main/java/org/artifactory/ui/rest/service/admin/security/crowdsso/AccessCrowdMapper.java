package org.artifactory.ui.rest.service.admin.security.crowdsso;

import org.artifactory.mapper.ArtifactoryBaseMapper;
import org.artifactory.ui.rest.model.admin.security.crowdsso.CrowdIntegration;
import org.jfrog.access.client.model.CrowdSetting;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(uses = ArtifactoryBaseMapper.class, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AccessCrowdMapper {
    @Mapping(source = "enable", target = "enableIntegration")
    @Mapping(target = "noAutoUserCreation", expression = "java(crowdSetting.isAutoUserCreation())")
    CrowdIntegration map(CrowdSetting crowdSetting);

    @Mapping(source = "enableIntegration", target = "enable")
    @Mapping(target = "autoUserCreation", expression = "java(crowdIntegration.isNoAutoUserCreation())")
    CrowdSetting map(CrowdIntegration crowdIntegration);
}
