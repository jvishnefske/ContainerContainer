package org.artifactory.repo.service.flexible;

import jakarta.annotation.Nonnull;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.flexible.context.MoveCopyContext;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class CalculateTargetMoveCopyPathTest {

    @Nonnull
    private MoveCopyContext context(String sourcePath, String targetPath) {
        RepoPath repoPath = RepoPathFactory.create("repo1", sourcePath);
        return new MoveCopyContext(repoPath, "repo2", targetPath);
    }

    public void testOptions() {
        MoveCopyContext ctx = context("a/b/c/", "/");
        RepoPath filePath = RepoPathFactory.create("repo1", "a/b/c/e/d.txt");
        RepoPath targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/c/e/d.txt");
        ctx.setCopyContent(true);
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/e/d.txt");

        ctx = context("a/b/c/", "/");
        filePath = RepoPathFactory.create("repo1", "a/b/c/z/e/d.txt");
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/c/z/e/d.txt");
        ctx.setCopyContent(true);
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/z/e/d.txt");

        ctx = context("a/b/c", "/g");
        filePath = RepoPathFactory.create("repo1", "a/b/c/e/d.txt");
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/g/e/d.txt");
        ctx.setCopyContent(true);
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/g/e/d.txt"); // acts the same

        ctx = context("a/b/c/", "a");
        filePath = RepoPathFactory.create("repo1", "a/b/c/e/d.txt");
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/e/d.txt");
        ctx.setCopyContent(true);
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/e/d.txt"); // acts the same

        ctx = context("a/b/c/", "a");
        filePath = RepoPathFactory.create("repo1", "a/b/e/d.txt");
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/e/d.txt");
        ctx.setCopyContent(true);
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/e/d.txt"); // acts the same

        ctx = context("a/b/c/d.txt", "a");
        filePath = RepoPathFactory.create("repo1", "a/b/c/d.txt");
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/");
        ctx.setCopyContent(true);
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/"); // acts the same

        ctx = context("a/b/c/d.txt", "a/b");
        filePath = RepoPathFactory.create("repo1", "a/b/c/d.txt");
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/b/");
        ctx.setCopyContent(true);
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/b/"); // acts the same

        ctx = context("a/b/c/d.txt", "a/");
        filePath = RepoPathFactory.create("repo1", "a/b/c/d.txt");
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/d.txt");
        ctx.setCopyContent(true);
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/");

        ctx = context("a/b/c/d.txt", "a");
        filePath = RepoPathFactory.create("repo1", "a/b/c/d.txt");
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/");
        ctx.setCopyContent(true);
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/"); // acts the same

        ctx = context("a/b/c/d.txt", "/");
        filePath = RepoPathFactory.create("repo1", "a/b/c/d.txt");
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/d.txt");
        ctx.setCopyContent(true);
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/"); // copy file to folder

        ctx = context("a/b/c", "/");
        filePath = RepoPathFactory.create("repo1", "a/b/c/d.txt");
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/c/d.txt");
        ctx.setCopyContent(true);
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/d.txt");
        // acts as copy the content to root (explicit folder)


        ctx = context("a/b/c/d.txt", "/");
        filePath = RepoPathFactory.create("repo1", "a/b/c/d.txt");
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/d.txt");
        ctx.setCopyContent(true);
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/"); // acts as copy file to folder

        ctx = context("a/b/c", "/");
        filePath = RepoPathFactory.create("repo1", "a/b/c/d.txt");
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/c/d.txt");
        ctx.setCopyContent(true);
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/d.txt"); // copy content only

        ctx = context("/", "/");
        filePath = RepoPathFactory.create("repo1", "a/b/c/d.txt");
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/b/c/d.txt");
        ctx.setCopyContent(true);
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/b/c/d.txt"); // acts the same

        ctx = context("/", "/");
        filePath = RepoPathFactory.create("repo1", "a/b/c");
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/b/c");
        ctx.setCopyContent(true);
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/b/c"); // acts the same

        ctx = context("/", "/");
        filePath = RepoPathFactory.create("repo1", "a/b");
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/b");
        ctx.setCopyContent(true);
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/b"); // acts the same

        ctx = context("a", "x/y");
        filePath = RepoPathFactory.create("repo1", "a/b/c");
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/x/y/b/c");
        ctx.setCopyContent(true);
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/x/y/b/c"); // acts the same

        ctx = context("a", "a/b");
        filePath = RepoPathFactory.create("repo1", "a/b");
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/b/b");
        ctx.setCopyContent(true);
        targetRepoPath = BulkProvider.getTargetPath(ctx, filePath);
        Assert.assertEquals(targetRepoPath.toPath(), "repo2/a/b/b"); // acts the same
    }
}
