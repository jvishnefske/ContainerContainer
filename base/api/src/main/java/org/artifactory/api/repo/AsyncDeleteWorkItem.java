package org.artifactory.api.repo;


import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.workqueue.WorkItem;

@Getter
@AllArgsConstructor
public class AsyncDeleteWorkItem extends WorkItem {

    private final boolean pruneEmptyFolders;
    private final DeleteContext deleteContext;

    @Nonnull
    @Override
    public String getUniqueKey() {
        return deleteContext.getRepoPath().toPath();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AsyncDeleteWorkItem that = (AsyncDeleteWorkItem) o;
        return deleteContext.getRepoPath().equals(that.deleteContext.getRepoPath());
    }

    @Override
    public int hashCode() {
        return deleteContext.getRepoPath().hashCode();
    }
}
