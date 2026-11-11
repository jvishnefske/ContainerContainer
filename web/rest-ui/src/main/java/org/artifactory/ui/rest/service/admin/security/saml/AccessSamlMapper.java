package org.artifactory.ui.rest.service.admin.security.saml;

import org.artifactory.mapper.ArtifactoryBaseMapper;
import org.artifactory.ui.rest.model.admin.security.saml.Saml;
import org.jfrog.access.client.model.SamlSetting;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(uses = ArtifactoryBaseMapper.class, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AccessSamlMapper {
    @Mapping(target = "noAutoUserCreation", expression = "java(samlSetting.isAutoUserCreation())")
    Saml map(SamlSetting samlSetting);

    @Mapping(target = "autoUserCreation", expression = "java(saml.isNoAutoUserCreation())")
    SamlSetting map(Saml saml);
}
