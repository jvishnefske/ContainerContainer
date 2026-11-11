package org.artifactory.repo.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.artifactory.api.common.RepoRemovalStatusHolder;

@Getter
@RequiredArgsConstructor
class RepositoryRemovalFailedException extends RuntimeException implements StatusException {

    private final transient RepoRemovalStatusHolder statusHolder;

    @Override
    public String getMessage() {
        return statusHolder.getStatusMsg();
    }
}