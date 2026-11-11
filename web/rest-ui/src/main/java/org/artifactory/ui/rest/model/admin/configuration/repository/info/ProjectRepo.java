package org.artifactory.ui.rest.model.admin.configuration.repository.info;

import org.jfrog.access.client.project.ProjectResourceEntity;

public record ProjectRepo(String repoKey, String projectKey,
                          ProjectResourceEntity resourceEntity) {}

