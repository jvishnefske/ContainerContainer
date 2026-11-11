package org.artifactory.ui.rest.service.artifacts.search.syntax;

import lombok.experimental.UtilityClass;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Filters;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Query;
import org.artifactory.ui.rest.model.artifacts.search.syntax.State;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Type;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.ui.rest.model.artifacts.search.syntax.Type.FILE;
import static org.artifactory.ui.rest.model.artifacts.search.syntax.Type.FOLDER;
import static org.artifactory.ui.rest.model.artifacts.search.syntax.Type.REPO;

/**
 * @author Igor Usenko
 */
@UtilityClass
class SyntaxSearchUtils {

    static boolean shouldSearch(Type currentType, Set<Type> types, State state) {
        return types.contains(currentType) && (state == null || state.type().equals(currentType));
    }

    static State getRepoSearchState(List<Repository> repositories) {
        if (repositories.isEmpty()) {
            return null;
        }
        Repository last = repositories.getLast();
        return new State(REPO, last.repo(), last.modified());
    }

    static State getPathSearchState(List<Node> paths) {
        return getSearchState(FOLDER, paths);
    }

    static State getFileSearchState(List<Node> paths) {
        return getSearchState(FILE, paths);
    }

    static State getSearchState(Type type, List<Node> paths) {
        if (paths.isEmpty()) {
            return null;
        }
        Node last = paths.getLast();
        return new State(type, String.valueOf(last.nodeId()), last.modified());
    }

    static Set<Type> getSearchTypes(Query searchQuery, Filters filters) {
        if (filters != null && filters.itemTypes() != null && !filters.itemTypes().isEmpty()) {
            Set<Type> types = filters.itemTypes().stream()
                    .filter(type -> searchQuery.repo() == null || type != REPO)
                    .collect(Collectors.toSet());

            // SHA only applies to files
            if (isNotNull(searchQuery.sha())) {
                types.remove(FOLDER);
                types.remove(REPO);
            }
            return types;
        }
        Set<Type> types = getSearchTypes(searchQuery);
        if (types.isEmpty()) {
            throw new IllegalArgumentException("Unable to determine search types from query");
        }
        return types;
    }

    private static Set<Type> getSearchTypes(Query searchQuery) {
        Set<Type> types = new HashSet<>();

        if (isNull(searchQuery.text())) {
            if (isNotNull(searchQuery.sha())) {
                types.add(FILE);
            } else if (isNotNull(searchQuery.folder())) {
                types.add(FOLDER);
            } else if (isNotNull(searchQuery.repo())) {
                types.add(REPO);
            }
        } else {
            if (isNotNull(searchQuery.sha())) {
                types.add(FILE);
            } else if (isNotNull(searchQuery.repo()) || isNotNull(searchQuery.folder())) {
                types.add(FOLDER);
                types.add(FILE);
            } else {
                types.add(REPO);
                types.add(FOLDER);
                types.add(FILE);
            }
        }
        return types;
    }

    static String getDownloadUrl(String contextUrl, String repo, String path, String name) {
        String fullPath = getFullPath(repo, path, name);
        return contextUrl + "/" + fullPath;
    }

    static String getFullPath(String repo, String path, String name) {
        String corrected = ".".equals(path) ? "" : path;
        if (corrected.isEmpty() && name.isEmpty()) {
            return repo;
        }
        return repo + "/" + corrected + (corrected.isEmpty() ? "" : "/") + name;
    }

    static boolean isNull(String string) {
        return string == null;
    }

    static boolean isNotNull(String string) {
        return string != null;
    }

}
