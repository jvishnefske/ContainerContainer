package org.artifactory.ui.rest.model.artifacts.search.syntax;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author Igor Usenko
 */
public record Query(
        String text,
        String repo,
        @JsonAlias("path") String folder,
        String sha,
        boolean autoComplete,
        List<String> permittedRepos
) {

    public Query(String text, String repo, String path, String sha) {
        this(text, repo, path, sha, false, List.of());
    }

    public Query(String text, String repo, String path, String sha, boolean autoComplete) {
        this(text, repo, path, sha, autoComplete, List.of());
    }

    public boolean isRepoFixed() {
        return isNotBlank(text) && isNotBlank(repo);
    }

    public boolean hasTextRepoAndFolder() {
        return isNotBlank(repo()) && isNotBlank(folder()) && isNotBlank(text());
    }

    public static Query create(Query query, boolean autoComplete) {
        return new Query(query.text(), query.repo(), query.folder(), query.sha(), autoComplete, query.permittedRepos());
    }

    public static Query copyWithDifferentRepo(Query query, String repo) {
        return new Query(query.text(), repo, query.folder(), query.sha(), query.autoComplete(), query.permittedRepos());
    }

    public static Query adjustQueryToNonAdminUser(Query query, boolean autoComplete, List<String> permittedRepos) {
        return new Query(query.text(), query.repo(), query.folder(), query.sha(), autoComplete, permittedRepos);
    }
}
