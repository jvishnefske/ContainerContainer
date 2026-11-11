package org.artifactory.metadata.service.provider;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.result.AqlEagerResult;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.aql.util.AqlUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.StatsInfo;
import org.artifactory.leadartifact.detector.LeadArtifactDetector;
import org.artifactory.md.Properties;
import org.artifactory.metadata.service.MetadataEntityFacade;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.storage.db.event.service.metadata.model.MutableArtifactMetadata;
import org.artifactory.storage.db.event.service.metadata.model.MutableMetadataEntityBOM;
import org.jfrog.client.util.PathUtils;
import org.jfrog.metadata.client.model.MetadataVersionRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.artifactory.util.application.ApplicationServiceConfigurationUtil.APPLICATION_SERVICE_ID_PROPERTY_NAME;

/**
 * @author Uriah Levy
 * Copies data into the {@link MutableMetadataEntityBOM}, either from the {@link Properties}, or some other data source
 */
public abstract class AbstractMetadataProvider implements MetadataProvider, LeadArtifactDetector {
    private static final Logger log = LoggerFactory.getLogger(AbstractMetadataProvider.class);

    static final String COLON = ":";
    static final String ARTIFACTORY_LICENSES = "artifactory.licenses";
    static final String PACKAGE_ID_SEPARATOR = COLON + "//";
    private static final int MAX_PROPERTY_LENGTH = 4000;
    static final int MAX_PKGID_VERSION_LENGTH = 255;

    private final RepoType type;
    TypeSpecificMetadataTemplate typeSpecificMetadataTemplate;

    AbstractMetadataProvider(RepoType type) {
        this.type = type;
    }

    /**
     * Copy data from the {@link Properties} into the metadata BOM
     *
     * @param metadataBOM - the {@link MutableMetadataEntityBOM} that will hold the metadata
     * @param fileInfo    - the {@link FileInfo} object that containing file related metadata
     * @param properties  - the {@link Properties} key-value store containing package metadata
     */
    @Override
    public void supplement(MutableMetadataEntityBOM metadataBOM, FileInfo fileInfo, Properties properties,
            @Nullable StatsInfo stats) {
        allResolvers()
                .forEach(resolver -> resolver.apply(metadataBOM, properties, fileInfo, stats));
    }

    @Override
    public Set<String> getLeadArtifactExtensions() {
        return typeSpecificMetadataTemplate.getLeadFileExtensions();
    }

    @Override
    public RepoType getRepoType() {
        return type;
    }

    /**
     * Determines if an artifact is the one file that signifies the "anchor" of a version.
     * That would be a descriptor of a version, or the binary itself if no descriptor exists for the package type.
     */
    @Override
    public boolean isLeadArtifact(RepoPath repoPath) {
        String extension = PathUtils.getExtension(repoPath.getPath());
        String tarExtension = PathUtils.getTarFamilyExtension(repoPath.getPath());
        return (StringUtils.isNotBlank(tarExtension) && getLeadArtifactExtensions().contains(tarExtension)) ||
                (StringUtils.isNotBlank(extension) && getLeadArtifactExtensions().contains(extension));
    }

    @Override
    public String getPkgid(RepoPath repoPath, Properties properties) {
        String pkgidSuffix = typeSpecificMetadataTemplate.getPkgidSuffix(repoPath, properties);
        if (StringUtils.isBlank(pkgidSuffix)) {
            log.debug("No pkgid suffix resolved for path '{}'", repoPath);
            return null;
        }
        return getRepoType().getPkgidPrefix() + PACKAGE_ID_SEPARATOR + pkgidSuffix;
    }


    protected String getType(@Nonnull Properties properties) {
        return type.getType();
    }

    void setTypeSpecificMetadataTemplate(TypeSpecificMetadataTemplate typeSpecificMetadataTemplate) {
        this.typeSpecificMetadataTemplate = typeSpecificMetadataTemplate;
    }

    String getPkgIdSuffixFromLayout(ModuleInfo moduleInfo, RepoPath repoPath) {
        if (moduleInfo.isValid()) {
            return moduleInfo.getOrganization().replace("/", ".") + COLON + moduleInfo.getModule();
        }
        log.debug("Module Info invalid for path {}", repoPath);
        return null;
    }

    MetadataResolver userPropsResolver() {
        return (MutableMetadataEntityBOM bom, Properties properties, FileInfo fileInfo, StatsInfo statsInfo) ->
                properties
                        .keySet()
                        .stream()
                        .filter(key -> !typeSpecificMetadataTemplate.getExcludedUserProperties().contains(key))
                        .filter(key -> !key.equals(APPLICATION_SERVICE_ID_PROPERTY_NAME))
                        .filter(key -> !key.equals(typeSpecificMetadataTemplate.getLicenseKey()))
                        .filter(key -> !key.startsWith(ARTIFACTORY_LICENSES))
                        .filter(key -> !Objects.isNull(properties.get(key)))
                        .forEach(key -> {
                            Set<String> values = properties.get(key);
                            if (values != null) {
                                Set<String> sanitizedValues = values.stream()
                                        .filter(StringUtils::isNotBlank)
                                        .collect(Collectors.toSet());
                                if (!sanitizedValues.isEmpty()) {
                                    bom.getUserProperties().put(key, sanitizedValues);
                                }
                            }
                        });
    }

    MetadataResolver tagsResolver() {
        return (MutableMetadataEntityBOM bom, Properties properties, FileInfo fileInfo, StatsInfo statsInfo) ->
                typeSpecificMetadataTemplate.getTagKey().ifPresent(tagKey -> {
                    String rawTagsValue = valueForKey(properties, tagKey);
                    if (StringUtils.isNotBlank(rawTagsValue)) {
                        Set<String> tags = setByTemplateFunction(rawTagsValue,
                                typeSpecificMetadataTemplate.getTagValueResolver());
                        bom.setTags(tags.stream().map(tag -> trimToSize(tag, MAX_PROPERTY_LENGTH))
                                .collect(Collectors.toSet()));
                    }
                });
    }

    MetadataResolver qualifiersResolver() {
        return (MutableMetadataEntityBOM bom, Properties properties, FileInfo fileInfo, StatsInfo statsInfo) ->
                typeSpecificMetadataTemplate
                        .getQualifierKeysToMdsKeys()
                        .keySet()
                        .forEach(qualifierKey -> {
                            String rawValue = valueForKey(properties, qualifierKey);
                            if (StringUtils.isNotBlank(rawValue)) {
                                String value = valueByTemplateFunction(rawValue,
                                        typeSpecificMetadataTemplate.getQualifierValueResolver());
                                if (StringUtils.isNotBlank(value)) {
                                    bom.getQualifiers()
                                            .put(typeSpecificMetadataTemplate.getQualifierKeysToMdsKeys()
                                                            .get(qualifierKey),
                                                    value);
                                }
                            }
                        });
    }

    MetadataResolver pkgIdResolver() {
        return (MutableMetadataEntityBOM bom, Properties properties, FileInfo fileInfo, StatsInfo statsInfo) ->
                resolvePkgId(bom, properties, fileInfo);
    }

    protected void resolvePkgId(MutableMetadataEntityBOM bom, Properties properties, FileInfo fileInfo) {
        String pkgidSuffix = typeSpecificMetadataTemplate.getPkgidSuffix(fileInfo.getRepoPath(), properties);
        if (StringUtils.isNotBlank(pkgidSuffix)) {
            bom.setPkgid(trimToSize(getRepoType().getPkgidPrefix() + PACKAGE_ID_SEPARATOR + pkgidSuffix,
                    MAX_PKGID_VERSION_LENGTH));
        } else {
            log.debug("No pkgid resolved for path '{}'", fileInfo.getRepoPath());
        }
    }

    MetadataResolver packageDescriptionResolver() {
        return (MutableMetadataEntityBOM bom, Properties properties, FileInfo fileInfo, StatsInfo statsInfo) -> {
            Optional<String> descriptionKey = typeSpecificMetadataTemplate.getDescriptionKey();
            if (descriptionKey.isPresent()) {
                String description = valueForKey(properties, descriptionKey.get());
                if (StringUtils.isNotBlank(description) &&
                        typeSpecificMetadataTemplate.acceptsDescription(description)) {
                    bom.setDescription(description);
                }
            }
        };
    }

    MetadataResolver versionResolver() {
        return (MutableMetadataEntityBOM bom, Properties properties, FileInfo fileInfo, StatsInfo statsInfo) -> {
            String version = typeSpecificMetadataTemplate.getVersion(fileInfo.getRepoPath(), properties);
            if (StringUtils.isNotBlank(version)) {
                bom.setVersion(trimToSize(version, MAX_PKGID_VERSION_LENGTH));
            } else {
                log.debug("No version resolved for path '{}'", fileInfo.getRepoPath());
            }
        };
    }

    String valueForKey(Properties properties, String key) {
        String value = properties.getFirst(key);
        if (StringUtils.isBlank(value)) {
            if (log.isDebugEnabled()) {
                log.debug("Artifact Metadata provider has no metadata for property '{}'", key);
            }
            return "";
        }
        return trimValueIfNeeded(key, value);
    }

    String trimValueIfNeeded(@Nonnull String key, @Nonnull String value) {
        if (shouldTrimByKey(key)) {
            return trimToSize(value, MAX_PROPERTY_LENGTH);
        }
        return value;
    }

    MetadataResolver nameResolver() {
        return (MutableMetadataEntityBOM bom, Properties properties, FileInfo fileInfo, StatsInfo statsInfo) -> {
            String name = typeSpecificMetadataTemplate.getName(properties, fileInfo.getRepoPath());
            if (StringUtils.isNotBlank(name)) {
                bom.setName(name);
            }
        };
    }

    Optional<AqlApiItem> getLeadArtifactSiblingsQuery(@Nonnull RepoPath leadArtifactRepoPath) {
        if (leadArtifactRepoPath.getParent() == null) {
            // Root not supported.
            return Optional.empty();
        }
        return Optional.ofNullable(AqlUtils.getAllDirectChildrenOfParentQuery(leadArtifactRepoPath));
    }

    void setVersionRepo(MutableMetadataEntityBOM bom, FileInfo leadArtifactFileInfo,
            StatsInfo stats) {
        RepositoryService repositoryService = ContextHelper.get().beanForType(RepositoryService.class);
        RepoConfig config = repositoryService.getRepoConfigByKey(leadArtifactFileInfo.getRepoKey());
        boolean isLocal = config instanceof LocalConfig && !config.isCache();
        String repoKey = MetadataEntityFacade.resolveRepoKey(leadArtifactFileInfo.getRepoKey(), config);
        MetadataVersionRepo metadataVersionRepo = new MetadataVersionRepo(repoKey,
                isLocal ? "local" : "remote", leadArtifactFileInfo.getRepoPath().getPath(),
                stats != null ? stats.getDownloadCount() : null);
        bom.setRepos(Collections.singletonList(metadataVersionRepo));
    }

    MetadataResolver fileInfoResolver() {
        return (MutableMetadataEntityBOM bom, Properties properties, FileInfo leadArtifactFileInfo, StatsInfo stats) ->
        {
            // Artifact metadata
            setArtifactMetadata(bom, leadArtifactFileInfo);
            // Version creation date
            bom.setModified(leadArtifactFileInfo.getLastModified());
            // Version Repo
            setVersionRepo(bom, leadArtifactFileInfo, stats);
        };
    }

    List<MutableArtifactMetadata> findVersionFiles(FileInfo leadArtifactFileInfo) {
        Optional<AqlApiItem> versionFilesQuery = buildVersionFilesQuery(leadArtifactFileInfo.getRepoPath());
        if (versionFilesQuery.isPresent()) {
            AqlApiItem query = versionFilesQuery.get();
            query.limit(ConstantValues.mdsMaxFilesToWritePerVersion.getInt());
            AqlService aqlService = ContextHelper.get().beanForType(AqlService.class);
            AqlEagerResult<AqlItem> aqlResult = aqlService.executeQueryEager(query);
            if (!aqlResult.getResults().isEmpty()) {
                return aqlResult.getResults().stream()
                        .filter(item -> !item.getName().equals(leadArtifactFileInfo.getName()))
                        .map(item ->  new MutableArtifactMetadata(item.getName(), null, item.getSize(),
                                item.getActualMd5(), item.getActualSha1(), item.getSha2(), false, item.getPath()))
                        .collect(Collectors.toList());
            }
        }
        log.debug("Version files query is disabled, this package type doesn't support version files collection." +
                " The repository layout may not be supported or this version contains only a lead artifact");
        return new ArrayList<>();
    }

    private boolean shouldTrimByKey(@Nonnull String key) {
        return typeSpecificMetadataTemplate.getDescriptionKey().map(descKey -> !descKey.equals(key)).orElse(true) &&
                typeSpecificMetadataTemplate.getTagKey().map(tagKey -> !tagKey.equals(key)).orElse(true);
    }

    String trimToSize(@Nonnull String value, int size) {
        return value.length() > size ? value.substring(0, size) : value;
    }

    private MetadataResolver typeResolver() {
        return (MutableMetadataEntityBOM bom, Properties properties, FileInfo fileInfo, StatsInfo stats) -> bom
                .setPackageType(getType(properties).toLowerCase());
    }

    private MetadataResolver licenseResolver() {
        return (MutableMetadataEntityBOM bom, Properties properties, FileInfo fileInfo, StatsInfo statsInfo) -> {
            Set<String> licenses = collectionForKey(properties, typeSpecificMetadataTemplate.getLicenseKey());
            if (CollectionUtils.isNotEmpty(licenses)) {
                bom.setLicenses(new ArrayList<>(licenses));
            }
        };
    }

    private MetadataResolver statsResolver() {
        return (MutableMetadataEntityBOM bom, Properties properties, FileInfo fileInfo, StatsInfo stats) -> {
            if (stats != null) {
                bom.setDownloadCount(stats.getDownloadCount());
            }
        };
    }

    private MetadataResolver issuesResolver() {
        return (MutableMetadataEntityBOM bom, Properties properties, FileInfo fileInfo, StatsInfo stats) -> {
            Optional<String> issuesKey = typeSpecificMetadataTemplate.getIssuesKey();
            if (issuesKey.isPresent()) {
                String issuesUrl = valueForKey(properties, issuesKey.get());
                if (StringUtils.isNotBlank(issuesUrl)) {
                    bom.setIssuesUrl(issuesUrl);
                }
            }
        };
    }

    MetadataResolver architectureResolver() {
        return (MutableMetadataEntityBOM bom, Properties properties, FileInfo fileInfo, StatsInfo stats) -> {
            Optional<String> architectureKey = typeSpecificMetadataTemplate.getArchitectureKey();
            if (architectureKey.isPresent()) {
                String architectureValue = valueForKey(properties, architectureKey.get());
                if (StringUtils.isNotBlank(architectureValue)) {
                    bom.getArtifactMetadata().stream().filter(MutableArtifactMetadata::isLead).findFirst().ifPresent(
                            mutableArtifactMetadata -> mutableArtifactMetadata.setArch(architectureValue)
                    );
                }
            }
        };
    }

    MetadataResolver distributionResolver() {
        return (MutableMetadataEntityBOM bom, Properties properties, FileInfo fileInfo, StatsInfo stats) -> {
            Optional<String> distributionKey = typeSpecificMetadataTemplate.getDistributionKey();
            if (distributionKey.isPresent()) {
                String distributionValue = valueForKey(properties, distributionKey.get());
                if (StringUtils.isNotBlank(distributionValue)) {
                    bom.getArtifactMetadata().stream().filter(MutableArtifactMetadata::isLead).findFirst().ifPresent(
                            mutableArtifactMetadata -> mutableArtifactMetadata.setDist(distributionValue)
                    );
                }
            }
        };
    }

    private Set<MetadataResolver> allResolvers() {
        return Set.of(
                nameResolver(),
                versionResolver(),
                licenseResolver(),
                pkgIdResolver(),
                userPropsResolver(),
                qualifiersResolver(),
                tagsResolver(),
                packageDescriptionResolver(),
                fileInfoResolver(),
                typeResolver(),
                issuesResolver(),
                statsResolver(),
                distributionResolver(),
                architectureResolver());
    }

    private void setArtifactMetadata(MutableMetadataEntityBOM bom, FileInfo leadArtifactFileInfo) {
        List<MutableArtifactMetadata> versionFiles = findVersionFiles(leadArtifactFileInfo);
        MutableArtifactMetadata leadArtifact = new MutableArtifactMetadata(
                leadArtifactFileInfo.getName(), leadArtifactFileInfo.getMimeType(), leadArtifactFileInfo.getSize(),
                leadArtifactFileInfo.getMd5(), leadArtifactFileInfo.getSha1(), leadArtifactFileInfo.getSha2(),
                true, leadArtifactFileInfo.getRelPath());
        versionFiles.add(leadArtifact);
        bom.setArtifactMetadata(versionFiles);
    }

    private Set<String> collectionForKey(Properties properties, String key) {
        Set<String> collection = properties.get(key);
        if (CollectionUtils.isEmpty(collection)) {
            if (log.isDebugEnabled()) {
                log.debug("Artifact Metadata provider has no metadata for property '{}'", key);
            }
            return Collections.emptySet();
        }
        return collection;
    }

    private String valueByTemplateFunction(String value, Function<String, String> function) {
        if (StringUtils.isBlank(value)) {
            if (log.isDebugEnabled()) {
                log.debug("Skipping extraction of value by function from an empty value");
            }
            return "";
        }
        return function.apply(value);
    }

    private Set<String> setByTemplateFunction(String value, Function<String, Set<String>> function) {
        if (StringUtils.isBlank(value)) {
            if (log.isDebugEnabled()) {
                log.debug("Skipping extraction of value by function from an empty value");
            }
            return new HashSet<>();
        }
        return function.apply(value);
    }

    /***
     * Check if the lead artifact matches the expected repository layouts
     * @param repositoryService
     * @param leadArtifactRepoPath
     * @param expectedLayouts
     * @return
     */
    protected boolean isLeadArtifactMatchRepoLayouts(InternalRepositoryService repositoryService,
            RepoPath leadArtifactRepoPath, @Nonnull Set<String> expectedLayouts ) {
        RepoConfig repoConfig = repositoryService.repoConfigByKey(leadArtifactRepoPath.getRepoKey());
        String repoLayoutName = repoConfig != null ? repoConfig.getBaseConfig().getRepoLayoutRef() : null;
        return repoLayoutName != null && expectedLayouts.contains(repoLayoutName);
    }

    /***
     * Check if the lead artifact matches the expected repository layout
     * @param repositoryService
     * @param leadArtifactRepoPath
     * @param expectedLayout
     * @return
     */
    protected boolean isLeadArtifactMatchRepoLayout(InternalRepositoryService repositoryService,
            RepoPath leadArtifactRepoPath, @Nonnull String expectedLayout ) {
        return isLeadArtifactMatchRepoLayouts(repositoryService, leadArtifactRepoPath, Set.of(expectedLayout));
    }
}
