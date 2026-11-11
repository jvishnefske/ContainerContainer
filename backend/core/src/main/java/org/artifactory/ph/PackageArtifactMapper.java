package org.artifactory.ph;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.jfrog.ph.acl.model.PackageArtifact;
import com.jfrog.ph.acl.model.PackageArtifact.PackageArtifactBuilder;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.artifactory.addon.nuget.NuGetProperties;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl;
import org.artifactory.aql.result.rows.FileInfoWithStatisticsItemRow;
import org.artifactory.aql.util.AqlUtils;
import org.artifactory.common.repo.PackageType;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.md.Properties;
import org.artifactory.md.PropertiesInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.resource.RepoResourceInfo;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.fs.MutableVfsFile;
import org.jfrog.metadata.client.model.MetadataFile;
import org.jfrog.metadata.client.model.MetadataPackageImpl;
import org.jfrog.metadata.client.model.MetadataVersion;
import org.jfrog.metadata.client.model.MetadataVersionTagEntity;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PackageArtifactMapper {

    public static PackageArtifact artifact(VfsItem<?> item, PackageType packageType) {
        PackageArtifactBuilder artifact = PackageArtifact.builder();
        fillArtifact(artifact, item, packageType);
        return artifact.build();
    }

    public static PackageArtifact artifact(RepoResource repoResource, PackageType packageType) {
        PackageArtifactBuilder artifact = PackageArtifact.builder();
        fillArtifact(artifact, repoResource, packageType);
        return artifact.build();
    }

    public static PackageArtifact artifact(PackageArtifact source, RepoPath targetRepoPath, PackageType packageType) {
        PackageArtifactBuilder artifact = PackageArtifact.builder();
        fillArtifact(source, artifact, targetRepoPath, packageType);
        return artifact.build();
    }

    public static PackageArtifact artifact(FileInfo fileInfo) {
        PackageArtifactBuilder artifact = PackageArtifact.builder();
        fillArtifact(artifact, fileInfo);
        return artifact.build();
    }

    public static PackageArtifact artifact(ItemInfo itemInfo) {
        PackageArtifactBuilder artifact = PackageArtifact.builder();
        fillArtifact(artifact, itemInfo);
        return artifact.build();
    }

    public static PackageArtifact artifact(FileInfoWithStatisticsItemRow row) {
        final PackageArtifact.PackageArtifactBuilder builder = PackageArtifact.builder();
        final HashMultimap<String, String> properties = HashMultimap.create();
        builder.properties(properties);
        builder.file(true);
        builder.created(row.getCreated());
        builder.id(row.getItemId());
        builder.path(row.getRepoPath().getPath());
        builder.repoKey(row.getRepo());
        builder.name(row.getName());
        builder.md5(row.getActualMd5());
        builder.sha1(row.getActualSha1());
        builder.sha2(row.getSha2());
        builder.lastUpdated(row.toFileInfo().getLastUpdated());
        builder.lastModified(row.getModified());
        builder.createdBy(row.getCreatedBy());
        builder.modifiedBy(row.getModifiedBy());
        builder.length(row.getSize());
        builder.downloadCount(row.getStatDownloaded());
        return builder.build();
    }

    public static PackageArtifact artifact(AqlBaseFullRowImpl row) {
        final PackageArtifact.PackageArtifactBuilder builder = PackageArtifact.builder();
        final HashMultimap<String, String> properties = HashMultimap.create();
        builder.properties(properties);
        builder.file(true);
        builder.created(row.getCreated().getTime());
        builder.id(row.getNodeId());
        builder.path(AqlUtils.fromAql(row).getPath());
        builder.repoKey(row.getRepo());
        builder.name(row.getName());
        builder.md5(row.getActualMd5());
        builder.sha1(row.getActualSha1());
        builder.sha2(row.getSha2());
        builder.lastUpdated(row.getUpdated().getTime());
        builder.lastModified(row.getModified().getTime());
        builder.createdBy(row.getCreatedBy());
        builder.modifiedBy(row.getModifiedBy());
        builder.length(row.getSize());
        if (row.containsKey(AqlPhysicalFieldEnum.statDownloads.getName())) {
            builder.downloadCount(row.getDownloads());
        }
        return builder.build();
    }

    public static List<PackageArtifact> artifact(MetadataPackageImpl metadataPackage, String repoKey,
            PackageType pkgType) {
        String description = metadataPackage.getDescription();
        return metadataPackage.getVersions()
                .stream()
                .map(version -> extractFromMetadataRepos(repoKey, pkgType, version, description))
                .flatMap(List::stream)
                .toList();
    }

    private static void fillArtifact(PackageArtifactBuilder artifact, VfsItem<?> item,
            PackageType packageType) {
        artifact.file(item.isFile());
        artifact.created(item.getCreated());
        artifact.id(item.getId());
        artifact.path(item.getPath());
        artifact.repoKey(item.getRepoKey());
        artifact.name(item.getName());
        artifact.packageType(packageType);
        if (item.isFile()) {
            VfsFile<?> file = (VfsFile<?>) item;
            artifact.md5(file.getMd5());
            artifact.sha2(file.getSha2());
            artifact.sha1(file.getSha1());
            artifact.length(file.length());
            artifact.lastUpdated(file.getInfo().getLastUpdated());
            artifact.lastModified(file.getInfo().getLastModified());
            artifact.createdBy(file.getInfo().getCreatedBy());
            artifact.modifiedBy(file.getInfo().getModifiedBy());
        }
        artifact.properties(lazyProperties(item));
        artifact.originalProperties(getOriginalProps(item));
    }

    private static void fillArtifact(PackageArtifactBuilder artifact, RepoResource repoResource,
            PackageType packageType) {
        RepoResourceInfo info = repoResource.getInfo();
        artifact.path(info.getRepoPath().getPath());
        artifact.repoKey(info.getRepoPath().getRepoKey());
        artifact.sha1(info.getSha1());
        artifact.file(info.getRepoPath().isFile());
        artifact.sha2(info.getSha2());
        artifact.md5(info.getMd5());
        artifact.lastModified(info.getLastModified());
        artifact.length(info.getSize());
        artifact.name(info.getRepoPath().getName());
        artifact.packageType(packageType);
    }

    private static void fillArtifact(PackageArtifact source, PackageArtifactBuilder target,
            RepoPath targetRepoPath,
            PackageType packageType) {
        target.created(source.getCreated());
        target.md5(source.getMd5());
        target.file(source.isFile());
        target.length(source.getLength());
        target.sha1(source.getSha1());
        target.sha2(source.getSha2());
        target.lastModified(source.getLastModified());
        target.properties(source.getProperties());
        target.path(targetRepoPath.getPath());
        target.repoKey(targetRepoPath.getRepoKey());
        target.name(targetRepoPath.getName());
        target.packageType(packageType);
    }

    private static void fillArtifact(PackageArtifactBuilder artifact, FileInfo fileInfo) {
        artifact.file(true);
        artifact.created(fileInfo.getCreated());
        artifact.id(fileInfo.getId());
        artifact.path(fileInfo.getRelPath());
        artifact.repoKey(fileInfo.getRepoKey());
        artifact.name(fileInfo.getName());
        artifact.md5(fileInfo.getMd5());
        artifact.sha2(fileInfo.getSha2());
        artifact.sha1(fileInfo.getSha1());
        artifact.lastUpdated(fileInfo.getLastUpdated());
        artifact.lastModified(fileInfo.getLastModified());
        artifact.createdBy(fileInfo.getCreatedBy());
        artifact.modifiedBy(fileInfo.getModifiedBy());
        artifact.length(fileInfo.getSize());
    }

    private static void fillArtifact(PackageArtifactBuilder artifact, ItemInfo itemInfo) {
        if (!itemInfo.isFolder()) {
            fillArtifact(artifact, (FileInfo) itemInfo);
        } else {
            artifact.file(false);
            artifact.created(itemInfo.getCreated());
            artifact.id(itemInfo.getId());
            artifact.path(itemInfo.getRelPath());
            artifact.repoKey(itemInfo.getRepoKey());
            artifact.name(itemInfo.getName());
            artifact.lastUpdated(itemInfo.getLastUpdated());
            artifact.lastModified(itemInfo.getLastModified());
            artifact.createdBy(itemInfo.getCreatedBy());
            artifact.modifiedBy(itemInfo.getModifiedBy());
        }
    }

    /**
     * Get original properties of the given item, or empty result if not exists
     * The originalProperties are the last snapshot of the artifact properties before property changes, and each
     * package can decide if they are needed or not.
     *
     * @see PackageStorageInterceptorImpl#markOriginalPropertiesIfNeeded(VfsItem)
     */
    private static SetMultimap<String, String> getOriginalProps(VfsItem<?> item) {
        if (item instanceof MutableVfsFile mutableVfsFile) {
            if (mutableVfsFile.isOriginalPropertiesAvailable()) {
                PropertiesInfo propertiesInfo = mutableVfsFile.getOriginalProperties();
                ImmutableSetMultimap.Builder<String, String> props = ImmutableSetMultimap.builder();
                for (String key : propertiesInfo.keySet()) {
                    Set<String> values = propertiesInfo.get(key);
                    if (values != null) {
                        for (String value : values) {
                            props.put(key, value);
                        }
                    }
                }
                return props.build();
            }
        }
        return ImmutableSetMultimap.of();
    }


    @SuppressWarnings("unchecked")
    private static SetMultimap<String, String> lazyProperties(VfsItem<?> vfsItem) {
        return
                (SetMultimap<String, String>) Proxy.newProxyInstance(PackageArtifactMapper.class.getClassLoader(),
                        new Class[] {SetMultimap.class}, new LazyProperties(vfsItem));
    }

    private static class LazyProperties implements InvocationHandler {

        private Supplier<SetMultimap<String, String>> propertiesSupplier;

        private LazyProperties(VfsItem<?> vfsItem) {
            propertiesSupplier = () -> {
                Properties vfsItemProperties = vfsItem.getProperties();
                ImmutableSetMultimap.Builder<String, String> props = ImmutableSetMultimap.builder();
                for (String key : vfsItemProperties.keys()) {
                    Set<String> values = vfsItemProperties.get(key);
                    if (values != null) {
                        for (String value : values) {
                            props.put(key, value);
                        }
                    }
                }
                SetMultimap<String, String> properties = props.build();
                propertiesSupplier = () -> properties;
                return properties;
            };
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(this.propertiesSupplier.get(), args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }
    }

    private static List<PackageArtifact> extractFromMetadataRepos(String repoKey, PackageType pkgType,
            MetadataVersion version, String description) {
        String name = version.getPkgid();
        SetMultimap<String, String> properties = getProperties(pkgType, version, name, description);
        return version.getRepos()
                .stream()
                .filter(repo -> repo.getName().equals(repoKey))
                .map(repo -> PackageArtifact.builder()
                        .repoKey(repoKey)
                        .path(repo.getLeadFilePath())
                        .sha2(getSha256(version))
                        .name(name)
                        .downloadCount(repo.getDownloadCount() == null ? 0 : repo.getDownloadCount())
                        .properties(properties)
                        .build())
                .toList();
    }

    private static SetMultimap<String, String> getProperties(PackageType pkgType, MetadataVersion version,
            String pkgName, String description) {
        SetMultimap<String, String> properties = HashMultimap.create();
        version.getUserProperties().forEach(
                property -> properties.put(property.getName(), String.join(",", property.getValues())));
        setRepoSpecificProperties(version, properties, pkgType, pkgName, description);
        return properties;
    }

    private static void setRepoSpecificProperties(MetadataVersion version, SetMultimap<String, String> properties,
            PackageType pkgType, String pkgName, String description) {
        if (PackageType.NUGET.equals(pkgType)) {
            properties.put(NuGetProperties.Tags.nodePropertyName(), getTags(version));
            properties.put(NuGetProperties.Digest.nodePropertyName(), getSha256(version));
            properties.put(NuGetProperties.Version.nodePropertyName(), version.getName());
            properties.put(NuGetProperties.Id.nodePropertyName(), pkgName);
            properties.put(NuGetProperties.Description.nodePropertyName(), description);
            properties.put(NuGetProperties.Summary.nodePropertyName(), description);
        }
    }

    private static String getTags(MetadataVersion version) {
        Set<MetadataVersionTagEntity> tags = version.getTags();
        if (tags == null) {return "";}
        return tags.stream()
                .map(MetadataVersionTagEntity::getValue)
                .collect(Collectors.joining(","));
    }

    private static String getSha256(MetadataVersion version) {
        return version.getFiles()
                .stream()
                .map(MetadataFile::getSha256)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
