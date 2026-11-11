package org.artifactory.repo;

import lombok.Getter;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.repo.virtual.VirtualRepo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class represents a structure for storing nested repositories.
 * It holds a reference to a parent virtual repository and a map of real repositories to their paths.
 * Each path is a list of lists of virtual repositories.
 * <p>
 * The class provides methods to add real and virtual repositories to the structure.
 * When adding a real repository, a route (list of virtual repositories) is also added.
 * When adding a virtual repository, the nested repositories of the virtual repository are also added.
 *
 * @author Hussein Alshqerat
 */
@Getter
public class NestedRealRepositoriesWithRoutes {
    private final VirtualRepo parent;
    private final Map<RealRepo<?, ?>, List<List<VirtualRepo>>> paths = new HashMap<>();

    public NestedRealRepositoriesWithRoutes(VirtualRepo parent) {
        this.parent = parent;
    }

    /**
     * Resolve nested repositories for a given virtual repository and path.
     * The method resolves the nested repositories and their paths recursively.
     * It uses a cache to store intermediate results and avoid cyclic dependencies.
     * The method returns a structure that holds the nested repositories and their paths.
     * The paths are lists of lists of virtual repositories.
     * <p>
     * @param parent    the virtual repository to resolve
     * @param forPath   the path to resolve
     * @param inProgress    a set of virtual repositories that are in progress
     * @param cache    a cache to store intermediate results
     * @return  a structure that holds the nested repositories and their paths
     */
    public static NestedRealRepositoriesWithRoutes resolveNestedRepositories(VirtualRepo parent, String forPath,
            Set<VirtualRepo> inProgress, Map<String, NestedRealRepositoriesWithRoutes> cache) {
        if (parent == null) {
            throw new ItemNotFoundRuntimeException("Parent virtual repository cannot be null");
        }

        // Check for cyclic dependencies
        if (!inProgress.add(parent)) {
            return null;
        }
        // Validate the path pattern
        if (!parent.isPathPatternValid(InternalRepoPathFactory.create(parent.getKey(), forPath + "/"), forPath)) {
            return null;
        }
        // Initialize the result object and add it to the cache
        NestedRealRepositoriesWithRoutes result = new NestedRealRepositoriesWithRoutes(parent);
        cache.put(parent.getKey(), result);
        // Temporary cache to store intermediate results of nested resolutions
        Map<String, NestedRealRepositoriesWithRoutes> tempCache = new HashMap<>();
        // Process child repositories
        for (Repo child : getRepoChildren(parent)) {
            // Directly add real repositories
            if (child.isReal()) {
                result.addRealRepo((RealRepo) child, new ArrayList<>());
            } else {
                // Recursively resolve virtual repositories
                VirtualRepo virtualChild = (VirtualRepo) child;
                NestedRealRepositoriesWithRoutes nested = tempCache.computeIfAbsent(child.getKey(),
                        x -> resolveNestedRepositories(virtualChild, forPath, inProgress, cache));
                result.addVirtualRepo(virtualChild, nested);
            }
        }
        // Merge the temporary cache into the main cache
        cache.putAll(tempCache);
        return result;
    }

    private static Set<Repo> getRepoChildren(VirtualRepo virtualRepo) {
        List<LocalRepo> locals = virtualRepo.getLocalRepositories();
        List<RemoteRepo> remotes = virtualRepo.getRemoteRepositories();
        List<VirtualRepo> virtuals = virtualRepo.getVirtualRepositories();
        Set<Repo> childrens = new HashSet<>();
        childrens.addAll(locals);
        childrens.addAll(remotes);
        childrens.addAll(virtuals);
        return childrens;
    }

    public void addRealRepo(RealRepo<?, ?> repo, List<VirtualRepo> route) {
        this.paths.computeIfAbsent(repo, x -> new ArrayList<>()).add(route);
    }

    public void addVirtualRepo(VirtualRepo virtualChild, NestedRealRepositoriesWithRoutes nested) {
        if (nested == null || nested.getPaths().isEmpty()) {
            return;
        }
        for (Map.Entry<RealRepo<?, ?>, List<List<VirtualRepo>>> entry : nested.paths.entrySet()) {
            RealRepo<?, ?> child = entry.getKey();
            for (List<VirtualRepo> childRoute : entry.getValue()) {
                List<VirtualRepo> newRoute = new ArrayList<>();
                newRoute.add(virtualChild);
                newRoute.addAll(childRoute);
                this.paths.computeIfAbsent(child, a -> new ArrayList<>()).add(newRoute);
            }
        }
    }

    public List<List<VirtualRepo>> getRoutesByRepoKey(String repoKey) {
        // Todo add new hashmap to store the routes by repo key
        return paths.keySet().stream().filter(repo -> repo.getKey().equals(repoKey))
                .map(paths::get).findFirst().orElse(new ArrayList<>());
    }

    public List<String> getLocalReposKeys() {
        return paths.keySet().stream().filter(Repo::isLocal).map(Repo::getKey).toList();
    }

    public List<String> getRemoteReposKeys() {
        return paths.keySet().stream().filter(realRepo -> !realRepo.isLocal() && realRepo.isReal()).map(Repo::getKey)
                .toList();
    }
}