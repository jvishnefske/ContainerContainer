package org.artifactory.repo.db;

import org.artifactory.md.Properties;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.resource.FileResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.artifactory.repo.db.DbStoringRepoMixin.ORIGIN_REMOTE_PATH_PROP_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class DbStoringRepoMixinOriginRemotePathTest {

    private DbStoringRepoMixin mixin;
    private FileResource fileResource;
    private Properties properties;

    @BeforeEach
    void setUp() {
        mixin = new DbStoringRepoMixin(null, null, null);

        RepoPathImpl repoPath = new RepoPathImpl("a", "b");
        FileInfoImpl fileInfo = new FileInfoImpl(repoPath);
        fileResource = new FileResource(fileInfo);
        fileResource.setOriginRemotePath("not-changed");

        properties = new PropertiesImpl();
    }

    @Test
    void whenNotFound() {
        FileResource spy = spy(fileResource);
        when(spy.isFound()).thenReturn(false);

        mixin.setOriginRemotePath(false, spy, properties);

        assertEquals("not-changed", spy.getOriginRemotePath());
    }

    @Test
    void whenPropertiesIsNull() {
        mixin.setOriginRemotePath(false, fileResource, null);

        assertEquals("not-changed", fileResource.getOriginRemotePath());
    }

    @Test
    void whenPropertyExists() {
        properties.put(ORIGIN_REMOTE_PATH_PROP_KEY, "changed");

        mixin.setOriginRemotePath(false, fileResource, properties);

        assertEquals("changed", fileResource.getOriginRemotePath());
    }

    @Test
    void whenPropertyEmpty() {
        properties.put(ORIGIN_REMOTE_PATH_PROP_KEY, "");

        mixin.setOriginRemotePath(false, fileResource, properties);

        assertEquals("not-changed", fileResource.getOriginRemotePath());
    }

    @Test
    void whenLocalNotCachedRepo() {
        properties.put(ORIGIN_REMOTE_PATH_PROP_KEY, "");

        mixin.setOriginRemotePath(true, fileResource, properties);

        assertEquals("local", fileResource.getOriginRemotePath());
    }

}
