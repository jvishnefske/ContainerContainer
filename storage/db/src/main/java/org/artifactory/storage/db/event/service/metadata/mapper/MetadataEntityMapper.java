package org.artifactory.storage.db.event.service.metadata.mapper;

import org.artifactory.mapper.ArtifactoryCentralMapperConfig;
import org.artifactory.storage.db.event.service.metadata.model.MutableArtifactMetadata;
import org.artifactory.storage.db.event.service.metadata.model.MutableMetadataEntityBOM;
import org.jfrog.metadata.client.model.MetadataFile;
import org.jfrog.metadata.client.model.MetadataPackage;
import org.jfrog.metadata.client.model.MetadataPackageImpl;
import org.jfrog.metadata.client.model.MetadataVersion;
import org.jfrog.metadata.client.rest.MetadataPackageProxyFactory;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

/**
 * One-way mapper from the {@link MutableMetadataEntityBOM} to the various MDS entity models.
 *
 * @author Uriah Levy
 */
@Mapper(config = ArtifactoryCentralMapperConfig.class, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        uses = MetadataEntityTranslator.class, componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MetadataEntityMapper {

    @ObjectFactory
    default MetadataPackage createMdsPackage(@Context MetadataPackage metadataPackage) {
        return MetadataPackageProxyFactory.create(metadataPackage);
    }

    // Full mapper
    @Mapping(target = "licenses", ignore = true)
    @Mapping(target = "stats", ignore = true)
    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "qualifiers", ignore = true)
    @Mapping(target = "userProperties", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "modified", ignore = true)
    MetadataPackageImpl metadataBomToMdsPackage(MutableMetadataEntityBOM metadataBom);

    // Update (patch) mapper. Not used - can be used to patch only set fields
    @Mapping(target = "licenses", ignore = true)
    @Mapping(target = "stats", ignore = true)
    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "qualifiers", ignore = true)
    @Mapping(target = "userProperties", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "modified", ignore = true)
    MetadataPackage metadataBomToMdsPackage(MutableMetadataEntityBOM metadataBom,
            @Context MetadataPackage metadataPackageProxy);

    @Mapping(source = "modified", target = "modified", qualifiedByName = {"ArtifactoryBaseMapper", "EpochToIso8601"})
    @Mapping(source = "licenses", target = "licenses", qualifiedByName = {"MetadataEntityTranslator",
            "LicensesTranslator"})
    @Mapping(source = "userProperties", target = "userProperties", qualifiedByName = {"MetadataEntityTranslator",
            "UserPropertiesTranslator"})
    @Mapping(source = "qualifiers", target = "qualifiers", qualifiedByName = {"MetadataEntityTranslator",
            "QualifiersTranslator"})
    @Mapping(source = "tags", target = "tags", qualifiedByName = {"MetadataEntityTranslator", "TagsTranslator"})
    @Mapping(source = "version", target = "name")
    MetadataVersion metadataBomToMdsVersion(MutableMetadataEntityBOM metadataBom);

    @Mapping(source = "artifactName", target = "name")
    @Mapping(source = "artifactMimeType", target = "mimeType")
    @Mapping(source = "artifactLength", target = "length")
    MetadataFile metadataBomToMdsFile(MutableArtifactMetadata mutableArtifactMetadata);
}
