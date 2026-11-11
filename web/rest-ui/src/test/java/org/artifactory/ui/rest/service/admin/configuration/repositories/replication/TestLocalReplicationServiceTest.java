package org.artifactory.ui.rest.service.admin.configuration.repositories.replication;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.jfrog.sysconf.SysConfig;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.model.LocalConfig;
import org.artifactory.opentracing.ArtifactoryTracerConfiguration;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.repo.http.DenyListCacheHolderService;
import org.artifactory.repo.http.GuaveCacheConnectionManagersHolder;
import org.artifactory.repo.http.HttpConnectionPoolMonitorService;
import org.artifactory.repo.http.IdleConnectionMonitorService;
import org.artifactory.repo.http.IdleConnectionMonitorServiceImpl;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.jfrog.client.http.exception.DenylistHostException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import static com.jfrog.sysconf.SysConfigConstants.KEY_DENYLIST_ADDRESSES;
import static com.jfrog.sysconf.SysConfigConstants.KEY_DENYLIST_ENABLED;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestLocalReplicationServiceTest {

    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test(expectedExceptions = DenylistHostException.class)
    public void validateDeniedUrl() throws IOException {
        String host = "hosthost.com";
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        SysConfig sysConfig = mock(SysConfig.class);
        when(sysConfig.get(KEY_DENYLIST_ADDRESSES)).thenReturn(Optional.of("[" + host + "]"));
        when(sysConfig.getBoolean(KEY_DENYLIST_ENABLED, false)).thenReturn(true);
        when(artifactoryHome.getSysConfig()).thenReturn(sysConfig);

        ArtifactorySystemProperties artifactorySystemProperties = mock(ArtifactorySystemProperties.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getProperty(ConstantValues.artifactoryProductVersion)).
                thenReturn(ArtifactoryServiceVersion.getCurrent().getVersion());
        when(artifactorySystemProperties.getLongProperty(ConstantValues.httpClientMaxTotalConnections))
                .thenReturn(1L);
        when(artifactorySystemProperties.getLongProperty(ConstantValues.httpClientMaxConnectionsPerRoute))
                .thenReturn(1L);

        ArtifactoryHome.bind(artifactoryHome);
        ArtifactoryContextThreadBinder.bind(context);

        AddonsManager addonsManager = mock(AddonsManager.class);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);

        HaCommonAddon haCommonAddon = mock(HaCommonAddon.class);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);

        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        when(context.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(mock(CentralConfigDescriptor.class));

        ArtifactoryTracer artifactoryTracer = new ArtifactoryTracer(ArtifactoryTracerConfiguration.testTracer());
        HttpConnectionPoolMonitorService httpConnectionPoolMonitorService = new HttpConnectionPoolMonitorService();
        when(context.beanForType(ArtifactoryTracer.class)).thenReturn(artifactoryTracer);
        when(context.beanForType(HttpConnectionPoolMonitorService.class)).thenReturn(httpConnectionPoolMonitorService);

        IdleConnectionMonitorService idleConnectionMonitorService =
                new IdleConnectionMonitorServiceImpl(new GuaveCacheConnectionManagersHolder());
        when(context.beanForType(IdleConnectionMonitorService.class)).thenReturn(idleConnectionMonitorService);

        DenyListCacheHolderService denyListCacheHolderService = mock(DenyListCacheHolderService.class);
        when(context.beanForType(DenyListCacheHolderService.class)).thenReturn(denyListCacheHolderService);

        Cache<String, Boolean> denyListCache = CacheBuilder.newBuilder().build();
        denyListCache.put(host, true);
        when(denyListCacheHolderService.getDenyListCache()).thenReturn(denyListCache);

        LocalReplicationDescriptor descriptor = new LocalReplicationDescriptor();
        descriptor.setUrl("http://" + host + "/libs-releases-local");
        descriptor.setEnabled(true);
        RestResponse response = new ArtifactoryRestResponse();

        TestLocalReplicationService testLocalReplicationService = new TestLocalReplicationService();
        testLocalReplicationService.testValidReplicationLocation(descriptor, response);
    }

    @Test
    public void validateInvalidTargetUrl() throws RepoConfigException {
        String host = "hosthost.com";
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ArtifactoryHome.bind(artifactoryHome);
        ArtifactoryContextThreadBinder.bind(context);

        RestResponse response = new ArtifactoryRestResponse();
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        LocalRepositoryConfigModel configModel = mock(LocalRepositoryConfigModel.class);
        when(request.getImodel()).thenReturn(configModel);
        when(request.getPathParamByKey("repoKey")).thenReturn("repoKey");
        when(request.getQueryParamByKey("replicationUrl")).thenReturn("http://" + host + "/remote-cache");
        LocalConfig localConfig = mock(LocalConfig.class);
        when(configModel.toRepoConfig(any(), any(), any())).thenReturn(localConfig);
        LocalReplicationDescriptor replication = new LocalReplicationDescriptor();
        replication.setUrl("http://" + host + "/remote-cache");
        replication.setEnabled(true);
        when(configModel.getReplicationDescriptors(any(), any())).thenReturn(Set.of(replication));

        TestLocalReplicationService testLocalReplicationService = new TestLocalReplicationService();
        testLocalReplicationService.execute(request, response);
        assertEquals(response.getResponseCode(), SC_BAD_REQUEST);
        assertTrue(response.getEntity().toString()
                .contains("{\"error\":\"Replication to remote cache repositories is not allowed.\"}"));
    }
}