/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.ui.rest.service.admin.configuration.repositories;

import org.apache.http.HttpStatus;
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypes;
import org.artifactory.addon.plugin.pkgtype.impl.PluginPackageTypeRegistryImpl;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.CreateRepoConfigHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.UpdateRepoConfigHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** R2 in the UI REST layer: unknown plugin package types are refused with 400 naming the type. */
class PluginPackageTypeRepoValidationTest extends ArtifactoryHomeBoundJUnitTest {

    private final PluginPackageTypeRegistryImpl registry = new PluginPackageTypeRegistryImpl();
    private RepoConfigValidator validator;

    @BeforeEach
    void setUp() {
        validator = new RepoConfigValidator();
        validator.setPluginPackageTypeRegistry(registry);
    }

    private static RepoConfig genericConfig(String key, String pluginType) {
        Map<String, String> packageTypeConfig = new HashMap<>();
        packageTypeConfig.put(PluginPackageTypes.CONFIG_KEY, pluginType);
        RepoConfig config = mock(RepoConfig.class);
        when(config.getKey()).thenReturn(key);
        when(config.getPackageType()).thenReturn(RepoType.Generic);
        when(config.getPackageTypeConfig()).thenReturn(packageTypeConfig);
        return config;
    }

    @SuppressWarnings("unchecked")
    private static ArtifactoryRestResponse<Object> response() {
        ArtifactoryRestResponse<Object> response = mock(ArtifactoryRestResponse.class);
        when(response.responseCode(anyInt())).thenReturn(response);
        when(response.error(any())).thenReturn(response);
        return response;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArtifactoryRestRequest request(RepositoryConfigModel model) {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getImodel()).thenReturn(model);
        return request;
    }

    private static RepositoryConfigModel<?, ?, ?> model(String repoKey) {
        RepositoryConfigModel<?, ?, ?> model = mock(RepositoryConfigModel.class);
        GeneralRepositoryConfigModel general = mock(GeneralRepositoryConfigModel.class);
        when(general.getRepoKey()).thenReturn(repoKey);
        when(model.getGeneral()).thenReturn(general);
        return model;
    }

    @Test
    void validatorRejectsUnknownTypeWith400NamingIt() {
        RepoConfigException e = assertThrows(RepoConfigException.class,
                () -> validator.validatePluginPackageType(genericConfig("r", "nosuch"), null));
        assertEquals(HttpStatus.SC_BAD_REQUEST, e.getStatusCode());
        assertTrue(e.getMessage().contains("'nosuch'"), e.getMessage());
    }

    @Test
    void validatorAcceptsRegisteredType() {
        registry.replacePluginTypes("p", List.of(PluginPackageType.builder("simple", "p").build()));
        assertDoesNotThrow(() -> validator.validatePluginPackageType(genericConfig("r", "simple"), null));
    }

    @Test
    void validatorAcceptsKeepingTypeOfRemovedPlugin() {
        assertDoesNotThrow(() -> validator.validatePluginPackageType(genericConfig("r", "gone"),
                genericConfig("r", "gone")));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void createWithUnknownTypeReturns400AndStoresNothing() throws Exception {
        RepoConfigValidator validatorSpy = spy(validator);
        doNothing().when(validatorSpy).validateEnvironmentsForRepoType(any());
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        ProjectsService projectsService = mock(ProjectsService.class);
        CreateRepositoryConfigService service = spy(new CreateRepositoryConfigService(
                mock(CentralConfigService.class), validatorSpy, mock(CreateRepoConfigHelper.class), projectsService,
                mock(RepositoryService.class), repositoryConfigService));
        doReturn(genericConfig("r", "nosuch")).when(service).createRepoConfig(any(), any(), any(), any(),
                anyBoolean());
        ArtifactoryRestResponse response = response();

        service.execute(request(model("r")), response);

        verify(response).responseCode(HttpStatus.SC_BAD_REQUEST);
        verify(response).error(contains("'nosuch'"));
        verify(repositoryConfigService, never()).upsertRepoConfig(any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void updateRejectedByBackendValidatorReturns400() throws Exception {
        UpdateRepoConfigHelper updater = mock(UpdateRepoConfigHelper.class);
        ProjectsService projectsService = mock(ProjectsService.class);
        RepoConfigValidator validatorMock = mock(RepoConfigValidator.class);
        UpdateRepositoryConfigService service = new UpdateRepositoryConfigService(updater, projectsService,
                validatorMock);
        RepositoryConfigModel<?, ?, ?> model = model("r");
        doThrow(new ConfigurationException("Unknown plugin package type 'nosuch'", HttpStatus.SC_BAD_REQUEST))
                .when(model).updateRepo(updater, false);
        ArtifactoryRestResponse response = response();

        service.execute(request(model), response);

        verify(response).responseCode(HttpStatus.SC_BAD_REQUEST);
        verify(response).error(contains("'nosuch'"));
    }
}
