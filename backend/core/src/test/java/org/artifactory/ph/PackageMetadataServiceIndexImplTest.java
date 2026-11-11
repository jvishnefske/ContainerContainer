package org.artifactory.ph;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.jfrog.ph.acl.model.PackageArtifact;
import org.artifactory.common.repo.PackageType;
import org.artifactory.fs.FileInfo;
import org.artifactory.md.Properties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testng.Assert;

import java.util.Set;

/**
 * @author Shahaf Golan
 */
class PackageMetadataServiceIndexImplTest {

    @Test
    void testMapToArtifactoryProperties() {
        SetMultimap<String, String> multiMap = HashMultimap.create();
        multiMap.put("key", "val1");
        multiMap.put("key", "val2");
        Properties properties = PackageMetadataServiceIndexImpl.mapToArtifactoryProperties(multiMap);
        Assertions.assertTrue(properties.containsKey("key"));
        Set<String> values = properties.get("key");
        Assert.assertNotNull(values);
        Assertions.assertTrue(values.contains("val1"));
        Assertions.assertTrue(values.contains("val2"));
    }

    @Test
    void testMapToFileInfo() {
        PackageArtifact packageArtifact =
                PackageArtifact.builder().file(true).packageType(PackageType.DOCKER).id(100)
                        .md5("aeb188a67bb0d85d92ff1bfb42d53a63")
                        .sha1("5dd1e432e8cc29a638af7b2c407c89a10a6182d0")
                        .sha2("3d426b0bfc361d6e8303f51459f17782b219dece42a1c7fe463b6014b189c86d")
                        .createdBy("eliran").created(1L).lastModified(2L).repoKey("repo")
                        .path("path/to/file.txt").modifiedBy("atar").length(1L).build();
        FileInfo fileInfo = PackageMetadataServiceIndexImpl.mapToFileInfo(packageArtifact);
        Assert.assertNotNull(fileInfo);
        Assertions.assertEquals(packageArtifact.getId(), fileInfo.getId());
        Assertions.assertEquals(packageArtifact.getMd5(), fileInfo.getMd5());
        Assertions.assertEquals(packageArtifact.getSha1(), fileInfo.getSha1());
        Assertions.assertEquals(packageArtifact.getSha2(), fileInfo.getSha2());
        Assertions.assertEquals(packageArtifact.getCreatedBy(), fileInfo.getCreatedBy());
        Assertions.assertEquals(packageArtifact.getCreated(), fileInfo.getCreated());
        Assertions.assertEquals(packageArtifact.getModifiedBy(), fileInfo.getModifiedBy());
        Assertions.assertEquals(packageArtifact.getLastModified(), fileInfo.getLastModified());
    }
}