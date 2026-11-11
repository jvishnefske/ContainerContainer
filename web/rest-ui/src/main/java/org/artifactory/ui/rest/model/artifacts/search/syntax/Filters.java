package org.artifactory.ui.rest.model.artifacts.search.syntax;

import org.artifactory.common.repo.PackageType;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType;

import java.util.Set;

public record Filters(
        Set<Type> itemTypes,
        Set<RepositoryType> repositoryTypes,
        Set<PackageType> packageTypes,
        DateRange lastModified,
        DateRange created
) {
    private static final Filters EMPTY = new Filters(null, null, null, null, null);

    public static Filters orEmpty(Filters filters) {
        return filters != null ? filters : EMPTY;
    }
}
