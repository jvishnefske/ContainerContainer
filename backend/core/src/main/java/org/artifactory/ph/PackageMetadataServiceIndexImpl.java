package org.artifactory.ph;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.jfrog.ph.acl.model.PackageArtifact;
import com.jfrog.ph.acl.service.PackageMetadataServiceIndexHandler;
import lombok.NonNull;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.md.Properties;
import org.artifactory.metadata.service.indexer.MetadataServiceIndexHandler;
import org.artifactory.model.fs.FileAdditionalInfo;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Shahaf Golan
 */
@Component
public class PackageMetadataServiceIndexImpl implements PackageMetadataServiceIndexHandler {

    private MetadataServiceIndexHandler metadataServiceIndexHandler;

    @NonNull
    static Properties mapToArtifactoryProperties(@NonNull Multimap<String, String> properties) {
        Properties artProperties = new PropertiesImpl();
        artProperties.putAll(properties);
        return artProperties;
    }

    @NonNull
    static FileInfoImpl mapToFileInfo(@NonNull PackageArtifact packageArtifact) {
        RepoPath repoPath = RepoPathFactory.create(packageArtifact.getRepoKey(), packageArtifact.getPath());
        FileInfoImpl fileInfo = new FileInfoImpl(repoPath, packageArtifact.getId());
        fileInfo.setSize(packageArtifact.getLength());
        FileAdditionalInfo fileAdditionalInfo = new FileAdditionalInfo();
        fileAdditionalInfo.addChecksumInfo(
                new ChecksumInfo(ChecksumType.md5, packageArtifact.getMd5(), packageArtifact.getMd5()));
        fileAdditionalInfo.addChecksumInfo(
                new ChecksumInfo(ChecksumType.sha1, packageArtifact.getSha1(), packageArtifact.getSha1()));
        fileAdditionalInfo.addChecksumInfo(
                new ChecksumInfo(ChecksumType.sha256, packageArtifact.getSha2(), packageArtifact.getSha2()));
        fileInfo.setAdditionalInfo(fileAdditionalInfo);
        fileInfo.setCreated(packageArtifact.getCreated());
        fileInfo.setModifiedBy(packageArtifact.getModifiedBy());
        fileInfo.setCreatedBy(packageArtifact.getCreatedBy());
        fileInfo.setLastModified(packageArtifact.getLastModified());
        return fileInfo;
    }

    @Override
    public void indexPackageAsync(PackageArtifact packageArtifact, Multimap<String, String> properties) {
        FileInfoImpl fileInfo = mapToFileInfo(packageArtifact);

        Multimap<String, String> multiMap = ArrayListMultimap.create(packageArtifact.getProperties());
        multiMap.putAll(properties);
        Properties artProperties = mapToArtifactoryProperties(multiMap);

        metadataServiceIndexHandler.indexPackageAsync(fileInfo, artProperties);
    }

    @Autowired
    public void setMetadataServiceIndexHandler(MetadataServiceIndexHandler metadataServiceIndexHandler) {
        this.metadataServiceIndexHandler = metadataServiceIndexHandler;
    }
}
