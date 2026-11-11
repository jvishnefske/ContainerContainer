package org.artifactory.util;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.artifactory.api.jackson.JacksonReader;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetails;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.onboarding.DefaultRepoModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Alexei Vainshtein
 */
@UtilityClass
public class RepositoriesUtils {

    private static final Logger log = LoggerFactory.getLogger(RepositoriesUtils.class);
    private static final String DEFAULT_REPO_RESOURCE = "/templates/defaultRepository.json";
    private static final Map<String, DefaultRepoModel> reposTypesAndModelsMap = loadReposTypesAndModelsMap();

    /**
     * Adds a list of local, virtual or distribution repositories to the repository details list
     *
     * @param detailsList List that details should be appended to
     * @param reposToAdd  List of repositories to add details of
     * @param type        Type of repository which is being added
     */
    public static void addNonRemoteRepoConfigDetails(String contextUrl, List<RepoDetails> detailsList,
            List<? extends RepoConfig> reposToAdd,
            RepoDetailsType type, AuthorizationService authorizationService) {
        for (RepoConfig repoToAdd : reposToAdd) {
            String key = repoToAdd.getKey();
            if (authorizationService.userHasPermissionsOnRepositoryRoot(key)) {
                RepoDetails repoDetails = RepoDetails.builder()
                        .key(key)
                        .description(repoToAdd.getBaseConfig().getDescription())
                        .type(type)
                        .url(contextUrl + "/" + key)
                        .packageType(repoToAdd.getPackageType())
                        .build();
                detailsList.add(repoDetails);
            }
        }
    }

    @NonNull
    public String getRepoLayoutForType(@NonNull String repoType) {
        DefaultRepoModel repoModel = reposTypesAndModelsMap.get(repoType.toLowerCase());
        return repoModel != null ? repoModel.getLayout() : RepoLayoutUtils.SIMPLE_DEFAULT_NAME;
    }

    @NonNull
    public static Map<String, DefaultRepoModel> loadReposTypesAndModelsMap() {
        try (InputStream json = RepositoriesUtils.class.getResourceAsStream(DEFAULT_REPO_RESOURCE)) {
            return JacksonReader.streamAsValueTypeReference(json, new TypeReference<List<DefaultRepoModel>>() {
                    })
                    .stream()
                    .collect(Collectors.toMap(DefaultRepoModel::getRepoType, defaultRepoModel -> defaultRepoModel));
        } catch (IOException e) {
            String message = "Can't import default repository file";
            log.error(message, e);
            return new HashMap<>();
        }
    }
}
