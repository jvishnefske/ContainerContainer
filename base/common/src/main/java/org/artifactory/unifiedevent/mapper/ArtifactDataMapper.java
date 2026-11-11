package org.artifactory.unifiedevent.mapper;

import org.artifactory.common.resource.BaseResource;
import org.artifactory.fs.RepoResource;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.unifiedevent.UnifiedEventContextProvider;
import org.artifactory.unifiedevent.api.request.ArtifactResourceContext;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.model.artifact.ArtifactData;
import org.artifactory.unifiedevent.model.artifact.ArtifactEventData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Optional;

@Mapper(componentModel = "spring")
public abstract class ArtifactDataMapper {

    @Autowired
    protected UnifiedEventContextProvider contextProvider;

    @Mapping(target = "specVersion", constant = "1.0")
    @Mapping(target = "id", expression = "java(contextProvider.generateEventId())")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "source", expression = "java(contextProvider.buildSourceHeader())")
    @Mapping(target = "time", expression = "java(contextProvider.getCurrentTimestamp())")
    @Mapping(target = "subject", expression = "java(getSubject(resourceContext))")
    @Mapping(target = "dataContentType", constant = "application/json")
    @Mapping(target = "data", source = "resourceContext")
    public abstract EventEnvelope<ArtifactEventData> toArtifactEventEnvelopeV1(
            SupportedUnifiedEvents type,
            ArtifactResourceContext resourceContext);

    @Mapping(target = "artifact", source = "resourceContext")
    @Mapping(target = "context", expression = "java(contextProvider.getUserContext())")
    @Mapping(target = "request", expression = "java(safeGetRequest(resourceContext))")
    protected abstract ArtifactEventData toArtifactEventData(ArtifactResourceContext resourceContext);

    @Mapping(target = "repoKey", expression = "java(getRepoKey(resourceContext))")
    @Mapping(target = "repoType", expression = "java(contextProvider.getRepoType(getRepoKey(resourceContext)))")
    @Mapping(target = "path", expression = "java(getRepoPath(resourceContext))")
    @Mapping(target = "created", expression = "java(safeGetCreated(resourceContext))")
    @Mapping(target = "lastModified", expression = "java(safeGetLastModified(resourceContext))")
    @Mapping(target = "md5", expression = "java(safeGetMd5(resourceContext))")
    @Mapping(target = "sha1", expression = "java(safeGetSha1(resourceContext))")
    @Mapping(target = "sha256", expression = "java(safeGetSha256(resourceContext))")
    @Mapping(target = "properties", expression = "java(safeGetProperties(resourceContext))")
    protected abstract ArtifactData toArtifactData(ArtifactResourceContext resourceContext);

    // --- Null-Safe Helper Methods ---

    protected String getSubject(ArtifactResourceContext ctx) {
        return Optional.ofNullable(ctx)
                .map(ArtifactResourceContext::repoResource)
                .map(RepoResource::getRepoPath)
                .map(RepoPath::toPrettyPath)
                .orElse(null);
    }

    protected String getRepoKey(ArtifactResourceContext ctx) {
        return Optional.ofNullable(ctx)
                .map(ArtifactResourceContext::repoResource)
                .map(BaseResource::getRepoKey)
                .orElse(null);
    }

    protected String getRepoPath(ArtifactResourceContext ctx) {
        return Optional.ofNullable(ctx)
                .map(ArtifactResourceContext::repoResource)
                .map(RepoResource::getRepoPath)
                .map(RepoPath::getPath)
                .orElse(null);
    }

    protected ArtifactoryRequest safeGetRequest(ArtifactResourceContext ctx) {
        return (ctx != null) ? (ArtifactoryRequest) ctx.artifactoryRequest() : null;
    }

    protected Long safeGetCreated(ArtifactResourceContext ctx) {
        return (ctx != null && ctx.repoResource() != null && ctx.repoResource().getInfo() != null)
                ? ctx.repoResource().getInfo().getCreated() : null;
    }

    protected Long safeGetLastModified(ArtifactResourceContext ctx) {
        return (ctx != null && ctx.repoResource() != null && ctx.repoResource().getInfo() != null)
                ? ctx.repoResource().getInfo().getLastModified() : null;
    }

    protected String safeGetMd5(ArtifactResourceContext ctx) {
        return (ctx != null && ctx.repoResource() != null && ctx.repoResource().getInfo() != null)
                ? ctx.repoResource().getInfo().getMd5() : null;
    }

    protected String safeGetSha1(ArtifactResourceContext ctx) {
        return (ctx != null && ctx.repoResource() != null && ctx.repoResource().getInfo() != null)
                ? ctx.repoResource().getInfo().getSha1() : null;
    }

    protected String safeGetSha256(ArtifactResourceContext ctx) {
        return (ctx != null && ctx.repoResource() != null && ctx.repoResource().getInfo() != null)
                ? ctx.repoResource().getInfo().getSha2() : null;
    }

    protected java.util.Map<String, java.util.Collection<String>> safeGetProperties(ArtifactResourceContext ctx) {
        return (ctx != null && ctx.properties() != null) ? ctx.properties().asMap() : java.util.Collections.emptyMap();
    }
}