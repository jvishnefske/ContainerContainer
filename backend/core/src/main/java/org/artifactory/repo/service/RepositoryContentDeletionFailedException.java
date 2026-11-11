package org.artifactory.repo.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.artifactory.api.common.BasicStatusHolder;

@Getter
@RequiredArgsConstructor
class RepositoryContentDeletionFailedException extends RuntimeException implements StatusException {

    private final transient BasicStatusHolder statusHolder;

    @Override
    public String getMessage() {
        return statusHolder.getStatusMsg();
    }
}