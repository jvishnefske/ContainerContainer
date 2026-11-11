package com.jfrog.build.gateway;

import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.acl.model.BuildRepoPath;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.fs.VfsItemNotFoundException;
import org.artifactory.storage.fs.service.FileService;

import java.util.Optional;

import static java.util.Optional.ofNullable;
import static org.artifactory.checksum.ChecksumType.md5;
import static org.artifactory.checksum.ChecksumType.sha1;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;
import static org.artifactory.exception.factory.ExceptionFactory.internalServerException;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@RequiredArgsConstructor
public class BuildFileGateway {

    private final FileService fileService;

    @NonNull
    public BuildFileInfo getFileInfo(@NonNull String rawRepoPath) {
        RepoPath repoPath = RepoPathFactory.create(rawRepoPath);
        try {
            ItemInfo itemInfo = fileService.loadItem(repoPath);
            if (itemInfo instanceof FileInfo fileInfo) {
                return createBuildFileInfo(fileInfo);
            }
            throw badRequestException("Found a directory instead of a file: %s", rawRepoPath);
        } catch (VfsItemNotFoundException e) {
            throw notFoundException("Not found repository path: %s", e, rawRepoPath);
        } catch (Exception e) {
            throw internalServerException("Failed to load the repository path: %s", e, rawRepoPath);
        }
    }

    @NonNull
    private BuildFileInfo createBuildFileInfo(@NonNull FileInfo fileInfo) {
        BuildRepoPath buildRepoPath = new BuildRepoPath(fileInfo.getRepoKey(), fileInfo.getRelPath());
        Optional<ChecksumsInfo> checksums = ofNullable(fileInfo.getChecksumsInfo());
        return new BuildFileInfo(
                fileInfo.getId(),
                buildRepoPath,
                fileInfo.getCreatedBy(),
                fileInfo.getModifiedBy(),
                checksums.map(c -> c.getChecksumInfo(md5).getOriginal()).orElse(null),
                checksums.map(c -> c.getChecksumInfo(md5).getActual()).orElse(null),
                checksums.map(c -> c.getChecksumInfo(sha1).getOriginal()).orElse(null),
                checksums.map(c -> c.getChecksumInfo(sha1).getActual()).orElse(null),
                fileInfo.getSha2(),
                fileInfo.getSize(),
                fileInfo.getCreated(),
                fileInfo.getLastModified(),
                fileInfo.getLastUpdated(),
                null
        );
    }
}
