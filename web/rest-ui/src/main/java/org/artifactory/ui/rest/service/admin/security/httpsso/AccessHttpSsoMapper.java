package org.artifactory.ui.rest.service.admin.security.httpsso;

import org.artifactory.mapper.ArtifactoryBaseMapper;
import org.artifactory.ui.rest.model.admin.security.httpsso.HttpSso;
import org.jfrog.access.client.model.HttpSsoSetting;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(uses = ArtifactoryBaseMapper.class, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AccessHttpSsoMapper {

    @Mapping(target = "autoCreateUser", expression = "java(httpSso.isNoAutoUserCreation())")
    HttpSsoSetting map(HttpSso httpSso);

    @Mapping(target = "noAutoUserCreation", expression = "java(httpSsoSetting.isAutoCreateUser())")
    HttpSso map(HttpSsoSetting httpSsoSetting);
}
